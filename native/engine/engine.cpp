/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

// The native side of the player. Decoding and audio output both live here, and JNI carries only
// control and metadata: pushing PCM across the boundary every few milliseconds would put the JNI
// cost on the one thread that must never be late.
//
// This is currently a single backend (libopenmpt). The shape -- open / describe / start / stop --
// is the shape the backend registry will expose, so adding sc68 means adding a backend behind this
// interface rather than changing it.

#include <jni.h>
#include <oboe/Oboe.h>
#include <libopenmpt/libopenmpt.hpp>

extern "C" {
#include <sc68/sc68.h>
#include <sc68/file68_rsc.h>
#include <asap.h>
#include <gme.h>
}

#include <sidplayfp/sidplayfp.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/SidConfig.h>
#include <sidlite.h>

#include <android/log.h>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <sstream>
#include <algorithm>
#include <cstdlib>
#include <string>
#include <vector>

#define LOG_TAG "protracktor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

/**
 * One decoder, seen the same way whatever it is underneath.
 *
 * Everything the player needs to know is asked, not assumed -- especially [canSeek]. libopenmpt can
 * move to a position; sc68 emulates a 68000 and cannot, because there is no way back except running
 * the machine again from the start. A UI that offers a control the backend cannot honour is a UI
 * that lies (docs/ARCHITECTURE.md §5).
 */
class Backend {
public:
    virtual ~Backend() = default;

    /** Renders interleaved stereo floats. Returns frames produced; fewer than asked means the end. */
    virtual std::size_t render(int sampleRate, std::size_t frames, float *out) = 0;

    virtual bool canSeek() const = 0;
    virtual void seek(double seconds) = 0;
    virtual void rewind() = 0;
    virtual double positionSeconds() const = 0;
    virtual double durationSeconds() const = 0;
    virtual std::string describe() const = 0;

    /** 0 means "whatever the device prefers". Non-zero backends are resampled by Oboe if need be. */
    virtual int preferredSampleRate() const { return 0; }
};

class OpenmptBackend : public Backend {
public:
    explicit OpenmptBackend(const std::vector<char> &bytes)
        : module_(std::make_unique<openmpt::module>(bytes.data(), bytes.size())) {}

    std::size_t render(int sampleRate, std::size_t frames, float *out) override {
        return module_->read_interleaved_stereo(sampleRate, frames, out);
    }

    bool canSeek() const override { return true; }
    void seek(double seconds) override { module_->set_position_seconds(seconds); }
    void rewind() override { module_->set_position_seconds(0.0); }
    double positionSeconds() const override { return module_->get_position_seconds(); }
    double durationSeconds() const override { return module_->get_duration_seconds(); }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << module_->get_metadata("title") << '\n'
          << "format\t" << module_->get_metadata("type_long") << '\n'
          << "tracker\t" << module_->get_metadata("tracker") << '\n'
          << "artist\t" << module_->get_metadata("artist") << '\n'
          << "channels\t" << module_->get_num_channels() << '\n'
          << "patterns\t" << module_->get_num_patterns() << '\n'
          << "instruments\t" << module_->get_num_instruments() << '\n'
          << "samples\t" << module_->get_num_samples() << '\n'
          << "subsongs\t" << module_->get_num_subsongs() << '\n'
          << "seekable\t1" << '\n'
          << "message\t" << module_->get_metadata("message_raw");
        return o.str();
    }

private:
    std::unique_ptr<openmpt::module> module_;
};

/**
 * Atari ST, through a 68000 emulator.
 *
 * An SNDH file is machine code, not note data: the music is a program that drives the YM2149. That
 * is why this backend is a whole emulated computer and why it cannot seek -- the only way to reach
 * a position is to run the machine there.
 */
// sc68 declares its allocator as taking unsigned int, which is not malloc's signature on a 64-bit
// target. Adapters rather than a cast: a cast here would compile and then pass a truncated size.
void *sc68Alloc(unsigned int bytes) { return std::malloc(bytes); }
void sc68Free(void *pointer) { std::free(pointer); }

class Sc68Backend : public Backend {
public:
    /**
     * Where sc68's replay binaries were unpacked. Set once from Kotlin before anything is opened.
     *
     * sc68 does not carry these routines inside the tunes: SNDH and `.sc68` both reference small
     * 68000 binaries that live on disk. Without them a file loads and then plays silence, which is
     * indistinguishable from a broken decoder and cost a day to diagnose on 2.2.1.
     */
    static std::string &sharedDataPath() {
        static std::string path;
        return path;
    }

    /**
     * A cheap pre-filter, not a verdict.
     *
     * 2.2.1 had `api68_verify_mem`, and it was already distrusted here: it returns -1 for an
     * ICE-packed SNDH that then loads and plays perfectly, and gating on it is what once stopped
     * every SNDH in the owner's library from opening. 3.x has no equivalent, which costs nothing --
     * the magic checks were doing the work. The real answer still comes from whether the load
     * succeeds.
     */
    static bool worthTrying(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        if (!std::memcmp(bytes.data(), "ICE!", 4)) return true;   // packed; sc68 unpacks it itself
        if (!std::memcmp(bytes.data(), "SC68", 4)) return true;

        // SNDH files carry the tag a few bytes in, after a branch instruction.
        const std::size_t window = std::min<std::size_t>(bytes.size() - 4, 256);
        for (std::size_t i = 0; i < window; ++i) {
            if (!std::memcmp(bytes.data() + i, "SNDH", 4)) return true;
        }
        return false;
    }

    explicit Sc68Backend(const std::vector<char> &bytes) {
        ensureLibraryReady();

        sc68_create_t create;
        std::memset(&create, 0, sizeof(create));
        create.sampling_rate = kSampleRate;

        sc68_ = sc68_create(&create);
        if (!sc68_) throw std::runtime_error("sc68 refused to create a player");

        if (sc68_load_mem(sc68_, bytes.data(), static_cast<int>(bytes.size())) < 0) {
            sc68_destroy(sc68_);
            sc68_ = nullptr;
            throw std::runtime_error("sc68 could not load this file");
        }
        if (sc68_play(sc68_, 1, SC68_DEF_LOOP) < 0) {
            sc68_destroy(sc68_);
            sc68_ = nullptr;
            throw std::runtime_error("sc68 loaded the file but refused to play it");
        }
        sc68_music_info(sc68_, &info_, SC68_CUR_TRACK, nullptr);
    }

    ~Sc68Backend() override {
        if (sc68_) {
            sc68_stop(sc68_);
            sc68_destroy(sc68_);
        }
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        if (ended_) return 0;

        // sc68 produces interleaved 16-bit stereo; Oboe is running in float. Converting here keeps
        // the whole conversion on the audio thread and out of the rest of the engine.
        if (scratch_.size() < frames * 2) scratch_.resize(frames * 2);

        int count = static_cast<int>(frames);
        const int code = sc68_process(sc68_, scratch_.data(), &count);

        // `SC68_ERROR` is ~0 -- every bit set -- so testing it with `&` is true of any non-zero
        // status, including the perfectly ordinary SC68_IDLE|SC68_CHANGE returned on the first
        // pass. It is a failure only when it IS the value.
        if (code == SC68_ERROR) {
            ended_ = true;
            return 0;
        }
        if (code & SC68_END) ended_ = true;
        if (count <= 0) return ended_ ? 0 : 0;

        const std::size_t produced = static_cast<std::size_t>(count);
        for (std::size_t i = 0; i < produced * 2; ++i) {
            out[i] = static_cast<float>(scratch_[i]) / 32768.0f;
        }
        rendered_ += produced;
        return produced;
    }

    bool canSeek() const override { return false; }
    void seek(double) override {}

    void rewind() override {
        sc68_stop(sc68_);
        sc68_play(sc68_, 1, SC68_DEF_LOOP);
        rendered_ = 0;
        ended_ = false;
    }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / static_cast<double>(kSampleRate);
    }

    /**
     * How long the tune is, which 2.2.1 could not answer for SNDH at all.
     *
     * 3.x ships a database of known SNDH files with their durations, so most of these now have a
     * real length rather than none.
     */
    double durationSeconds() const override {
        return static_cast<double>(info_.trk.time_ms) / 1000.0;
    }

    std::string describe() const override {
        const auto text = [](const char *value) { return value ? value : ""; };
        std::ostringstream o;
        o << "title\t" << text(info_.title) << '\n'
          << "format\tAtari ST (sc68)" << '\n'
          << "tracker\t" << text(info_.replay) << '\n'
          << "artist\t" << text(info_.artist) << '\n'
          << "hardware\t" << text(info_.trk.hw) << '\n'
          << "year\t" << text(info_.year) << '\n'
          << "subsongs\t" << info_.tracks << '\n'
          << "seekable\t0";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    /**
     * `sc68_init` is process-wide and must happen exactly once, before any player is created.
     *
     * The replay path is applied here too: 3.x dropped `shared_path` from its init struct and
     * `rsc68_set_share` replaces it, which has to be called after the library is up.
     */
    static void ensureLibraryReady() {
        static const bool ready = [] {
            sc68_init_t init;
            std::memset(&init, 0, sizeof(init));
            // Do not read or write a config file. There is nowhere sensible for one on Android, and
            // a player that remembers settings nobody set is a player that behaves differently on
            // its second run for no visible reason.
            init.flags.no_load_config = 1;
            init.flags.no_save_config = 1;
            if (sc68_init(&init) < 0) return false;
            if (!sharedDataPath().empty()) rsc68_set_share(sharedDataPath().c_str());
            return true;
        }();
        if (!ready) throw std::runtime_error("sc68 refused to initialise");
    }

    static constexpr int kSampleRate = 44100;

    sc68_t *sc68_ = nullptr;
    sc68_music_info_t info_{};
    std::vector<short> scratch_;
    std::size_t rendered_ = 0;
    bool ended_ = false;
};

/**
 * Why the last open failed, in words.
 *
 * "Not a format we can play yet" is true of a file no backend claims and false of one a backend
 * claimed and then choked on -- and the owner cannot tell those apart from the outside. Neither
 * could I: it took a host probe to find that sc68 2.2.1 loads some SNDH files and fails validation
 * on others. Saying which happened costs one string.
 */
std::string &lastOpenError() {
    static std::string reason;
    return reason;
}

/**
 * Atari 8-bit, through a 6502 and a POKEY.
 *
 * Twelve of twelve random SAP files from Modland played on the first try, which is not a sentence
 * that could be written about the Atari ST backend. ASAP also seeks, which few emulator backends do.
 *
 * It needs the **filename**: several of the fourteen formats it handles are told apart by extension
 * rather than by a header, and one of them (`.fc`) collides with an Amiga format libopenmpt claims.
 * That is why the whole engine now carries a name alongside the bytes.
 */
class AsapBackend : public Backend {
public:
    static bool claimsName(const std::string &name) {
        return ASAPInfo_IsOurFile(name.c_str());
    }

    AsapBackend(const std::vector<char> &bytes, const std::string &name)
        : asap_(ASAP_New()) {
        if (!asap_) throw std::runtime_error("ASAP would not initialise");

        ASAP_SetSampleRate(asap_, kSampleRate);
        if (!ASAP_Load(asap_, name.c_str(),
                       reinterpret_cast<const uint8_t *>(bytes.data()),
                       static_cast<int>(bytes.size()))) {
            ASAP_Delete(asap_);
            asap_ = nullptr;
            throw std::runtime_error("ASAP could not load this file");
        }

        info_ = ASAP_GetInfo(asap_);
        song_ = ASAPInfo_GetDefaultSong(info_);
        durationMs_ = ASAPInfo_GetDuration(info_, song_);

        if (!ASAP_PlaySong(asap_, song_, durationMs_)) {
            ASAP_Delete(asap_);
            asap_ = nullptr;
            throw std::runtime_error("ASAP loaded this file but would not start it");
        }
    }

    ~AsapBackend() override {
        if (asap_) ASAP_Delete(asap_);
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        if (ended_) return 0;

        const int channels = ASAPInfo_GetChannels(info_);
        const std::size_t wanted = frames * static_cast<std::size_t>(channels);
        if (scratch_.size() < wanted) scratch_.resize(wanted);

        const int bytes = ASAP_Generate(
            asap_, reinterpret_cast<uint8_t *>(scratch_.data()),
            static_cast<int>(wanted * sizeof(short)), ASAPSampleFormat_S16_L_E);

        if (bytes <= 0) {
            ended_ = true;
            return 0;
        }

        const std::size_t produced = static_cast<std::size_t>(bytes) / sizeof(short) / channels;
        for (std::size_t i = 0; i < produced; ++i) {
            // Mono is duplicated rather than left in one ear. Half these tunes are single-POKEY and
            // the owner's phone puts its second channel through the screen vibrator, where it would
            // be inaudible.
            const float left = static_cast<float>(scratch_[i * channels]) / 32768.0f;
            const float right = channels > 1
                ? static_cast<float>(scratch_[i * channels + 1]) / 32768.0f
                : left;
            out[i * 2] = left;
            out[i * 2 + 1] = right;
        }
        if (produced < frames) ended_ = true;
        return produced;
    }

    bool canSeek() const override { return true; }

    void seek(double seconds) override {
        ASAP_Seek(asap_, static_cast<int>(seconds * 1000.0));
        ended_ = false;
    }

    void rewind() override {
        ASAP_PlaySong(asap_, song_, durationMs_);
        ended_ = false;
    }

    double positionSeconds() const override { return ASAP_GetPosition(asap_) / 1000.0; }

    // A negative duration means the file does not say, which is common. Zero reads as "unknown" to
    // the rest of the app and disables the scrubber rather than offering a meaningless one.
    double durationSeconds() const override {
        return durationMs_ > 0 ? durationMs_ / 1000.0 : 0.0;
    }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << (ASAPInfo_GetTitle(info_) ? ASAPInfo_GetTitle(info_) : "") << '\n'
          << "format\tAtari 8-bit (ASAP)" << '\n'
          << "artist\t" << (ASAPInfo_GetAuthor(info_) ? ASAPInfo_GetAuthor(info_) : "") << '\n'
          << "date\t" << (ASAPInfo_GetDate(info_) ? ASAPInfo_GetDate(info_) : "") << '\n'
          << "channels\t" << ASAPInfo_GetChannels(info_) << '\n'
          << "subsongs\t" << ASAPInfo_GetSongs(info_) << '\n'
          << "seekable\t1";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;

    ASAP *asap_ = nullptr;
    const ASAPInfo *info_ = nullptr;
    int song_ = 0;
    int durationMs_ = -1;
    std::vector<short> scratch_;
    bool ended_ = false;
};

/**
 * The consoles, through game-music-emu: NES, SNES, Game Boy, Sega, PC Engine, ZX Spectrum, MSX.
 *
 * One library for seven machines, and the only one here that identifies files **by content** --
 * `gme_identify_header` reads the bytes rather than trusting a name. That makes it the safest
 * backend to ask early, and it is why nothing had to be added to the filename plumbing for it.
 *
 * Verified on the host before integration: NSF 4/4, SPC 4/4, GBS 3/3, VGM 3/3.
 */
class GmeBackend : public Backend {
public:
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        const char *type = gme_identify_header(bytes.data());
        return type && *type;
    }

    explicit GmeBackend(const std::vector<char> &bytes) {
        if (const gme_err_t err = gme_open_data(bytes.data(), static_cast<long>(bytes.size()),
                                                &emu_, kSampleRate)) {
            throw std::runtime_error(std::string("game-music-emu refused it: ") + err);
        }
        if (!emu_) throw std::runtime_error("game-music-emu returned nothing");

        gme_track_info(emu_, &info_, kTrack);
        if (const gme_err_t err = gme_start_track(emu_, kTrack)) {
            const std::string message = std::string("game-music-emu could not start it: ") + err;
            if (info_) gme_free_info(info_);
            gme_delete(emu_);
            emu_ = nullptr;
            throw std::runtime_error(message);
        }

        // Without a fade the last buffer stops dead. GME applies one relative to the track length
        // it reports, which for files that do not state a length is its own two-and-a-half minutes.
        if (info_ && info_->play_length > 0) gme_set_fade(emu_, info_->play_length);
    }

    ~GmeBackend() override {
        if (info_) gme_free_info(info_);
        if (emu_) gme_delete(emu_);
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        if (!emu_ || gme_track_ended(emu_)) return 0;

        const std::size_t samples = frames * 2;  // gme counts samples, not frames
        if (scratch_.size() < samples) scratch_.resize(samples);

        if (gme_play(emu_, static_cast<int>(samples), scratch_.data())) return 0;

        for (std::size_t i = 0; i < samples; ++i) {
            out[i] = static_cast<float>(scratch_[i]) / 32768.0f;
        }
        return frames;
    }

    bool canSeek() const override { return true; }
    void seek(double seconds) override { gme_seek(emu_, static_cast<int>(seconds * 1000.0)); }
    void rewind() override { gme_start_track(emu_, kTrack); }
    double positionSeconds() const override { return gme_tell(emu_) / 1000.0; }

    double durationSeconds() const override {
        return (info_ && info_->play_length > 0) ? info_->play_length / 1000.0 : 0.0;
    }

    std::string describe() const override {
        const auto field = [](const char *value) { return value && *value ? value : ""; };
        std::ostringstream o;
        o << "title\t" << (info_ ? field(info_->song) : "") << '\n'
          << "format\t" << (info_ ? field(info_->system) : "") << '\n'
          << "artist\t" << (info_ ? field(info_->author) : "") << '\n'
          << "game\t" << (info_ ? field(info_->game) : "") << '\n'
          << "copyright\t" << (info_ ? field(info_->copyright) : "") << '\n'
          << "dumper\t" << (info_ ? field(info_->dumper) : "") << '\n'
          << "subsongs\t" << (emu_ ? gme_track_count(emu_) : 0) << '\n'
          << "seekable\t1" << '\n'
          << "message\t" << (info_ ? field(info_->comment) : "");
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;
    // Subsong selection is a UI feature that does not exist yet; some of these files hold hundreds.
    static constexpr int kTrack = 0;

    Music_Emu *emu_ = nullptr;
    gme_info_t *info_ = nullptr;
    std::vector<short> scratch_;
};

/**
 * Commodore 64, through libsidplayfp.
 *
 * The largest single body of music left after trackers: roughly 72,000 files in Modland alone.
 *
 * **No Commodore ROMs are supplied**, and thirty random Modland SIDs all played without them, none
 * of them needing BASIC. Whether to ship or source ROMs at all is the owner's call and is not made
 * here; the measurement is in `docs/PLAN_FORMATS.md` so the question has a number attached.
 *
 * The emulation is SIDLite rather than ReSIDfp: 3.x split ReSIDfp into a separate library, and
 * SIDLite ships inside this one.
 */
class SidBackend : public Backend {
public:
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 4) return false;
        return !std::memcmp(bytes.data(), "PSID", 4) || !std::memcmp(bytes.data(), "RSID", 4);
    }

    explicit SidBackend(const std::vector<char> &bytes)
        : tune_(reinterpret_cast<const uint_least8_t *>(bytes.data()),
                static_cast<uint_least32_t>(bytes.size())),
          builder_("sidlite") {
        if (!tune_.getStatus()) {
            throw std::runtime_error(std::string("not a SID file: ") + tune_.statusString());
        }
        tune_.selectSong(0);
        info_ = tune_.getInfo();

        SidConfig cfg = engine_.config();
        cfg.frequency = kSampleRate;
        cfg.sidEmulation = &builder_;
        if (!engine_.config(cfg)) {
            throw std::runtime_error(std::string("libsidplayfp refused the configuration: ") + engine_.error());
        }
        if (!engine_.load(&tune_)) {
            throw std::runtime_error(std::string("libsidplayfp could not load it: ") + engine_.error());
        }

        // Without this, mix() dereferences a mixer that does not exist yet. Nothing in the header
        // says so; it cost a segfault and a read of player.cpp to find.
        engine_.initMixer(false);
    }

    std::size_t render(int sampleRate, std::size_t frames, float *out) override {
        if (sampleRate > 0) rate_ = sampleRate;
        std::size_t produced = 0;

        while (produced < frames) {
            if (spare_.empty()) {
                // play() runs the machine for a while and reports how many samples are waiting;
                // mix() takes them out. The two are separate in 3.x.
                const int waiting = engine_.play(kCyclesPerPass);
                if (waiting <= 0) break;

                const unsigned int want = std::min<unsigned int>(waiting, kScratchSamples);
                scratch_.resize(kScratchSamples);
                const unsigned int got = engine_.mix(scratch_.data(), want);
                if (got == 0) break;
                spare_.assign(scratch_.begin(), scratch_.begin() + got);
                spareRead_ = 0;
            }

            // Mono, duplicated into both channels. A SID is one chip and putting it in one ear
            // would be a choice nobody made.
            const short sample = spare_[spareRead_++];
            out[produced * 2] = static_cast<float>(sample) / 32768.0f;
            out[produced * 2 + 1] = out[produced * 2];
            ++produced;
            if (spareRead_ >= spare_.size()) spare_.clear();
        }
        rendered_ += produced;
        return produced;
    }

    // libsidplayfp has no seek: the only way to a position is to run the machine there.
    bool canSeek() const override { return false; }
    void seek(double) override {}

    void rewind() override {
        spare_.clear();
        spareRead_ = 0;
        rendered_ = 0;
        engine_.load(&tune_);
        engine_.initMixer(false);
    }

    // Counted, because there is nothing to ask. The other backends know where they are in a song;
    // libsidplayfp is running a program and has no notion of a position at all. Frames handed to
    // the output device is the same quantity by another route, and it is the one the listener is
    // actually hearing.
    double positionSeconds() const override {
        return static_cast<double>(rendered_) / static_cast<double>(rate_);
    }

    // SID files carry no length. HVSC's Songlengths database is what supplies one, and that is a
    // catalogue feature rather than a backend one.
    double durationSeconds() const override { return 0.0; }

    std::string describe() const override {
        const auto field = [this](unsigned int i) -> const char * {
            return (info_ && i < info_->numberOfInfoStrings() && info_->infoString(i))
                ? info_->infoString(i) : "";
        };
        std::ostringstream o;
        o << "title\t" << field(0) << '\n'
          << "format\tCommodore 64 (SID)" << '\n'
          << "artist\t" << field(1) << '\n'
          << "copyright\t" << field(2) << '\n'
          << "channels\t" << (info_ ? info_->sidChips() : 1) << '\n'
          << "subsongs\t" << (info_ ? info_->songs() : 1) << '\n'
          << "seekable\t0";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;
    static constexpr unsigned int kCyclesPerPass = 20000;
    static constexpr unsigned int kScratchSamples = 8192;

    std::uint64_t rendered_ = 0;
    int rate_ = kSampleRate;

    SidTune tune_;
    SIDLiteBuilder builder_;
    sidplayfp engine_;
    const SidTuneInfo *info_ = nullptr;
    std::vector<short> scratch_;
    std::vector<short> spare_;
    std::size_t spareRead_ = 0;
};

std::unique_ptr<Backend> openBackend(std::vector<char> bytes, const std::string &name) {
    lastOpenError().clear();

    // ASAP first when the name is one of its fourteen: several of its formats are told apart by
    // extension rather than by any header, so nothing else can make that call.
    if (AsapBackend::claimsName(name)) {
        try {
            return std::make_unique<AsapBackend>(bytes, name);
        } catch (const std::exception &e) {
            LOGE("ASAP claimed the name but refused: %s", e.what());
            lastOpenError() = std::string("ASAP refused it: ") + e.what();
        }
    }

    // SID first among the content-identified ones: its magic is four unambiguous bytes at offset
    // zero, which is as certain as identification gets.
    if (SidBackend::recognises(bytes)) {
        try {
            return std::make_unique<SidBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("libsidplayfp refused it: %s", e.what());
            lastOpenError() = e.what();
        }
    }

    // game-music-emu next, because it is the only backend that identifies by content rather than by
    // name or by trying: a header check that reads the bytes cannot claim something that is not its.
    if (GmeBackend::recognises(bytes)) {
        try {
            return std::make_unique<GmeBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("gme recognised the header but refused: %s", e.what());
            lastOpenError() = e.what();
        }
    }

    // sc68 asked first. Its answer is the load succeeding, not a verify -- see worthTrying. If it
    // refuses, we fall through to libopenmpt, whose format net is wide enough that letting it go
    // first would risk a stray claim on something sc68 should have had.
    if (Sc68Backend::worthTrying(bytes)) {
        try {
            return std::make_unique<Sc68Backend>(bytes);
        } catch (const std::exception &e) {
            LOGE("sc68 recognised but refused: %s", e.what());
            lastOpenError() = std::string("sc68 recognised this file but refused it: ") + e.what();
        }
    }
    try {
        return std::make_unique<OpenmptBackend>(bytes);
    } catch (const std::exception &e) {
        LOGE("libopenmpt refused: %s", e.what());
        if (lastOpenError().empty()) {
            lastOpenError() = std::string("no backend recognised it: ") + e.what();
        }
    }
    return nullptr;
}

class Player : public oboe::AudioStreamDataCallback {
public:
    explicit Player(std::unique_ptr<Backend> backend) : backend_(std::move(backend)) {}

    // Not locked, and deliberately so. Oboe's stop() blocks until an in-flight callback returns, and
    // every control path below stops the stream before touching the backend -- so the callback is
    // the only reader while it runs, and never concurrent with a writer. A mutex here would be a
    // lock on the audio thread bought for nothing.
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override {
        auto *out = static_cast<float *>(audioData);

        // A seek requested from another thread is applied HERE rather than there. A decoder cannot
        // be moved under a read in progress, and the audio callback is the only thread that reads
        // it, so handing the request over and letting the callback act on it removes the race
        // without stopping the stream and clicking.
        const double seekTo = pendingSeek_.exchange(NO_SEEK, std::memory_order_acq_rel);
        if (seekTo >= 0.0 && backend_->canSeek()) {
            backend_->seek(seekTo);
            finished_.store(false, std::memory_order_release);
        }

        const std::size_t rendered =
            backend_->render(stream->getSampleRate(), static_cast<std::size_t>(numFrames), out);

        // Ducking is applied here rather than by stopping the stream. A notification arriving should
        // lower the music for a moment, not end it -- and the only place a gain can be applied
        // without a gap is the buffer on its way out.
        const float gain = gain_.load(std::memory_order_relaxed);
        if (gain != 1.0f) {
            for (std::size_t i = 0; i < rendered * 2; ++i) out[i] *= gain;
        }

        if (rendered < static_cast<std::size_t>(numFrames)) {
            // End of the tune. Silence the remainder rather than leaving whatever the buffer held,
            // then ask Oboe to stop -- a player that runs off the end into noise is worse than one
            // that stops.
            std::memset(out + rendered * 2, 0, (numFrames - rendered) * 2 * sizeof(float));

            // A flag rather than a callback into Java. Attaching a JNI environment from the audio
            // callback means allocation and possible blocking on the one thread that must never be
            // late; Kotlin is polling this side anyway to drive the progress bar, so it costs
            // nothing to let it notice there too.
            finished_.store(true, std::memory_order_release);
            return oboe::DataCallbackResult::Stop;
        }
        return oboe::DataCallbackResult::Continue;
    }

    bool isFinished() const { return finished_.load(std::memory_order_acquire); }

    /** 1.0 is untouched. Used for ducking under a transient interruption. */
    void setGain(float gain) { gain_.store(gain, std::memory_order_relaxed); }

    /**
     * Asks for a new position. Applied by the audio callback on its next pass, or immediately when
     * nothing is playing and there is no callback to hand it to. Ignored by backends that cannot
     * seek; the UI is told so through the metadata rather than finding out by being disobeyed.
     */
    void seek(double seconds) {
        if (!backend_->canSeek()) return;
        const double target = seconds < 0.0 ? 0.0 : seconds;
        if (stream_) {
            pendingSeek_.store(target, std::memory_order_release);
        } else {
            backend_->seek(target);
            finished_.store(false, std::memory_order_release);
        }
    }

    /** Back to the beginning and playing. For repeat-one, and for replaying a finished tune. */
    bool restart() {
        stop();
        backend_->rewind();
        finished_.store(false, std::memory_order_release);
        return start();
    }

    bool start() {
        if (stream_) return true;
        finished_.store(false, std::memory_order_release);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::None)  // music playback, not low latency
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setDataCallback(this);

        // Backends that synthesise at a fixed rate say so, and Oboe resamples if the device runs at
        // something else. Asking a 68000 emulator to run at 48000 because the phone prefers it would
        // change the music, not the format it arrives in.
        if (const int preferred = backend_->preferredSampleRate(); preferred > 0) {
            builder.setSampleRate(preferred)
                ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
        }

        const oboe::Result result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(result));
            stream_.reset();
            return false;
        }
        if (const oboe::Result r = stream_->requestStart(); r != oboe::Result::OK) {
            LOGE("requestStart failed: %s", oboe::convertToText(r));
            stream_->close();
            stream_.reset();
            return false;
        }
        return true;
    }

    void stop() {
        if (!stream_) return;
        stream_->stop();  // blocks until any in-flight callback has returned
        stream_->close();
        stream_.reset();
    }

    ~Player() override { stop(); }

    double positionSeconds() const { return backend_->positionSeconds(); }
    double durationSeconds() const { return backend_->durationSeconds(); }
    std::string describe() const { return backend_->describe(); }

private:
    std::unique_ptr<Backend> backend_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> finished_{false};
    std::atomic<float> gain_{1.0f};

    // -1 means "nothing requested". A sentinel rather than a second flag: one atomic exchange in
    // the callback both reads the request and clears it.
    static constexpr double NO_SEEK = -1.0;
    std::atomic<double> pendingSeek_{NO_SEEK};
};

Player *asPlayer(jlong handle) { return reinterpret_cast<Player *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeOpen(JNIEnv *env, jclass, jbyteArray data,
                                                          jstring fileName) {
    const jsize length = env->GetArrayLength(data);
    std::vector<char> bytes(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

    const char *nameChars = env->GetStringUTFChars(fileName, nullptr);
    const std::string name = nameChars ? nameChars : "";
    env->ReleaseStringUTFChars(fileName, nameChars);

    // No backend recognising the bytes is reported as a handle of 0. The caller says so to the user
    // rather than failing silently.
    auto backend = openBackend(std::move(bytes), name);
    if (!backend) return 0;
    return reinterpret_cast<jlong>(new Player(std::move(backend)));
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeClose(JNIEnv *, jclass, jlong handle) {
    delete asPlayer(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeStart(JNIEnv *, jclass, jlong handle) {
    return asPlayer(handle)->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeStop(JNIEnv *, jclass, jlong handle) {
    asPlayer(handle)->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeIsFinished(JNIEnv *, jclass, jlong handle) {
    return asPlayer(handle)->isFinished() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeRestart(JNIEnv *, jclass, jlong handle) {
    return asPlayer(handle)->restart() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSeek(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    asPlayer(handle)->seek(seconds);
}

JNIEXPORT jstring JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeLastOpenError(JNIEnv *env, jclass) {
    return env->NewStringUTF(lastOpenError().c_str());
}

/**
 * Which decoders this build has, and at which versions.
 *
 * The local library index records a verdict per file -- what it is, whether anything can play it --
 * and those verdicts are only true of the decoder set that produced them. Replacing sc68 2.2.1 with
 * 3.0.0b took `.sndh` from 14 of 30 to 30 of 30: every "cannot play this" the old set wrote down
 * became wrong on the same day. Storing this string beside each row is what lets the index notice.
 *
 * Read from the libraries themselves where they will say, so a dependency bump cannot leave a
 * hand-written string behind.
 */
JNIEXPORT jstring JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeBackendsFingerprint(JNIEnv *env, jclass) {
    std::ostringstream o;
    o << "openmpt:" << openmpt::string::get("library_version")
      << ";sc68:" << sc68_versionstr()
      << ";asap:" << ASAPInfo_VERSION
      // game-music-emu publishes a packed integer rather than a string.
      << ";gme:" << ((GME_VERSION >> 16) & 0xff) << '.'
                 << ((GME_VERSION >> 8) & 0xff) << '.'
                 << (GME_VERSION & 0xff)
      << ";sidplayfp:" << LIBSIDPLAYFP_VERSION_MAJ << '.'
                       << LIBSIDPLAYFP_VERSION_MIN << '.'
                       << LIBSIDPLAYFP_VERSION_LEV;
    return env->NewStringUTF(o.str().c_str());
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSetDataPath(JNIEnv *env, jclass, jstring path) {
    const char *chars = env->GetStringUTFChars(path, nullptr);
    Sc68Backend::sharedDataPath() = chars ? chars : "";
    env->ReleaseStringUTFChars(path, chars);
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSetGain(JNIEnv *, jclass, jlong handle, jfloat gain) {
    asPlayer(handle)->setGain(gain);
}

JNIEXPORT jstring JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeDescribe(JNIEnv *env, jclass, jlong handle) {
    return env->NewStringUTF(asPlayer(handle)->describe().c_str());
}

JNIEXPORT jdouble JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativePositionSeconds(JNIEnv *, jclass, jlong handle) {
    return asPlayer(handle)->positionSeconds();
}

JNIEXPORT jdouble JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeDurationSeconds(JNIEnv *, jclass, jlong handle) {
    return asPlayer(handle)->durationSeconds();
}

}  // extern "C"
