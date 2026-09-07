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
/* HivelyTracker. `types.h` is a generic name and its typedefs -- TEXT, BOOL, CONST, TRUE, FALSE --
 * are unqualified Amiga ones, so it goes last in this block, after the four headers that would
 * otherwise have to live with them. */
#include <types.h>
#include <replay.h>
/* `hvl_play_irq` is the sequencer half of `hvl_DecodeFrame` and upstream does not declare it in
 * `replay.h`, though it exports it. `HivelyBackend` uses it to measure a tune's length without
 * mixing a note -- 1ms instead of 30ms, and the same answer. Declared here rather than patched into
 * the vendored header because the vendored tree is fetched, not committed. */
void hvl_play_irq(struct hvl_tune *ht);
}

// ZXTune. C++ with its own namespaces, so outside the `extern "C"` block above.
//
// `include/types.h` first, and **the path is not decoration**. It defines `uint_t` and `int_t`,
// which every other ZXTune header uses and none of them includes -- their own sources get it from a
// compiler-forced include, so the omission is invisible inside their build and immediate outside
// it. And a plain `<types.h>` finds *HivelyTracker's*, because that library ships a file of the
// same name and its include directory is on the path too. Two vendored libraries with a generically
// named header is not a problem until it is: the error arrives sixty lines later, in a third
// library's header, saying a type does not exist.
#include <include/types.h>

#include <binary/container_factories.h>
#include <formats/chiptune/aym/ascsoundmaster.h>
#include <formats/chiptune/aym/protracker2.h>
#include <formats/chiptune/aym/protracker3.h>
#include <formats/chiptune/aym/soundtracker.h>
#include <formats/chiptune/aym/soundtrackerpro.h>
#include <formats/chiptune/aym/sqtracker.h>
#include <module/holder.h>
#include <module/information.h>
#include <module/players/aym/ascsoundmaster.h>
#include <module/players/aym/fasttracker.h>
#include <module/players/aym/globaltracker.h>
#include <module/players/aym/prosoundmaker.h>
#include <module/players/aym/aym_base.h>
#include <module/players/aym/protracker1.h>
#include <module/players/aym/protracker2.h>
#include <module/players/aym/protracker3.h>
#include <module/players/aym/soundtracker.h>
#include <module/players/aym/soundtrackerpro.h>
#include <module/players/aym/sqtracker.h>
#include <module/renderer.h>
#include <parameters/container.h>
#include <sound/chunk.h>

#include <sidplayfp/sidplayfp.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/SidConfig.h>
#include <sidlite.h>

#include <android/log.h>
#include <atomic>
#include <mutex>
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

    /** How many tunes are inside. One for a format that holds one. */
    virtual int subsongCount() const { return 1; }

    /**
     * Plays subsong [index], counted from **zero**.
     *
     * Zero-based at this boundary whatever the library underneath does -- sc68 numbers its tracks
     * from one, and letting that leak upwards would put an off-by-one in every caller instead of in
     * one conversion here. Returns whether the switch happened.
     */
    virtual bool selectSubsong(int index) { return index == 0; }

    /**
     * Which subsong is playing, zero-based.
     *
     * Almost always zero, and `GmeBackend` is why this exists at all: HES and KSS files routinely
     * hold nothing at track 0, so it opens at the first track with sound in it and the rest of the
     * app has to be told where that was.
     */
    virtual int currentSubsong() const { return 0; }
};

class OpenmptBackend : public Backend {
public:
    explicit OpenmptBackend(const std::vector<char> &bytes)
        : module_(std::make_unique<openmpt::module>(bytes.data(), bytes.size())) {}

    std::size_t render(int sampleRate, std::size_t frames, float *out) override {
        return module_->read_interleaved_stereo(sampleRate, frames, out);
    }

    int subsongCount() const override { return static_cast<int>(module_->get_num_subsongs()); }

    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        // Throws for an out-of-range subsong, which the check above prevents -- but a corrupted
        // module can still surprise it, and an exception crossing JNI would take the app down.
        try {
            module_->select_subsong(index);
            return true;
        } catch (const std::exception &e) {
            LOGE("libopenmpt refused subsong %d: %s", index, e.what());
            return false;
        }
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
          // Present in IT and MPTM and rare elsewhere, and it was never sent across at all --
          // libopenmpt knew the date of every module that carried one and nobody asked.
          << "date\t" << module_->get_metadata("date") << '\n'
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
    static void setSharedDataPath(const std::string &path) {
        std::lock_guard<std::mutex> held(sharedDataMutex());
        sharedDataPath() = path;
    }

    /** A copy, taken under the lock. Callers must not hold a reference into it. */
    static std::string sharedDataPathCopy() {
        std::lock_guard<std::mutex> held(sharedDataMutex());
        return sharedDataPath();
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
        if (sc68_play(sc68_, current_.load(std::memory_order_relaxed), SC68_DEF_LOOP) < 0) {
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

    /**
     * Fills the buffer, asking sc68 as many times as that takes.
     *
     * **A single call is not enough, and getting this wrong ends every track.** `sc68_process`
     * returns `SC68_IDLE|SC68_CHANGE` and **zero frames** on its first call for a freshly played
     * tune -- the machine has not been run yet -- and a short render means end-of-tune to the
     * player: `onAudioReady` silences the rest of the buffer and stops the stream. Returning that
     * first zero stopped every SNDH and `.sc68` file on the first audio callback
     * (`docs/review.md` R1).
     *
     * Measured: three of three real files give `code=3 frames=0` on pass 0 and full buffers after.
     */
    std::size_t render(int, std::size_t frames, float *out) override {
        if (ended_) return 0;

        // sc68 produces interleaved 16-bit stereo; Oboe is running in float. Converting here keeps
        // the whole conversion on the audio thread and out of the rest of the engine.
        if (scratch_.size() < frames * 2) scratch_.resize(frames * 2);

        std::size_t produced = 0;
        int idlePasses = 0;

        while (produced < frames) {
            int count = static_cast<int>(frames - produced);
            const int code = sc68_process(sc68_, scratch_.data(), &count);

            // `SC68_ERROR` is ~0 -- every bit set -- so testing it with `&` is true of any non-zero
            // status, including the ordinary SC68_IDLE|SC68_CHANGE of the first pass. It is a
            // failure only when it IS the value.
            if (code == SC68_ERROR) {
                ended_ = true;
                break;
            }

            if (count > 0) {
                const std::size_t got = static_cast<std::size_t>(count);
                // `produced * 2` because both are interleaved stereo: frame n is samples 2n and
                // 2n+1 in each.
                for (std::size_t i = 0; i < got * 2; ++i) {
                    out[produced * 2 + i] = static_cast<float>(scratch_[i]) / 32768.0f;
                }
                produced += got;
                idlePasses = 0;
            } else if (++idlePasses > kMaxIdlePasses) {
                // A bound, because this runs on the audio thread. A decoder that never produces
                // anything must cost one silent buffer, not a locked-up device.
                break;
            }

            if (code & SC68_END) {
                ended_ = true;
                break;
            }
        }

        rendered_ += produced;
        return produced;
    }

    bool canSeek() const override { return false; }
    void seek(double) override {}

    /**
     * Back to the start of **what is playing**, which used to mean track 1 whatever was playing.
     *
     * `docs/STATUS.md` C13: repeat-one goes through here, so on a multi-tune SNDH sitting on
     * subsong five it repeated subsong one. It did repeat -- just not the thing the user was
     * listening to. ASAP replays `song_`, game-music-emu replays `track_`, libsidplayfp reloads
     * with its selected song; sc68 was the one that forgot.
     */
    void rewind() override {
        sc68_stop(sc68_);
        sc68_play(sc68_, current_.load(std::memory_order_acquire), SC68_DEF_LOOP);
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
        const std::lock_guard<std::mutex> held(infoGuard_);
        return static_cast<double>(info_.trk.time_ms) / 1000.0;
    }

    std::string describe() const override {
        const std::lock_guard<std::mutex> held(infoGuard_);
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

    int subsongCount() const override {
        const std::lock_guard<std::mutex> held(infoGuard_);
        return info_.tracks > 0 ? info_.tracks : 1;
    }

    /** sc68 counts its tracks from one; the interface counts from zero. Converted here, once. */
    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        if (sc68_play(sc68_, index + 1, SC68_DEF_LOOP) < 0) return false;
        // Remembered so `rewind` can come back to it. Everything that replays this tune without
        // choosing a subsong -- repeat-one, and replaying a finished track -- goes through there.
        current_.store(index + 1, std::memory_order_release);
        {
            const std::lock_guard<std::mutex> held(infoGuard_);
            sc68_music_info(sc68_, &info_, SC68_CUR_TRACK, nullptr);
        }
        rendered_ = 0;
        ended_ = false;
        return true;
    }

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
            const std::string share = sharedDataPathCopy();
            if (!share.empty()) rsc68_set_share(share.c_str());
            return true;
        }();
        if (!ready) throw std::runtime_error("sc68 refused to initialise");
    }

    static constexpr int kSampleRate = 44100;

    /**
     * How many empty passes to tolerate before giving up on a buffer.
     *
     * One is normal -- the first call after `sc68_play` always produces nothing. More than a
     * handful means the decoder is not going to produce anything, and the audio thread is the
     * wrong place to find out slowly.
     */
    static constexpr int kMaxIdlePasses = 8;

    /**
     * Where the replay binaries are, and the lock that makes reading it safe.
     *
     * Written once from JNI during start-up and read when a file is opened. Nothing ordered the two
     * until now, and a library scan can reach an open while start-up is still running -- a torn
     * read gives an empty path, and sc68 with no replay path loads files and plays silence, which
     * is indistinguishable from a broken decoder and cost a day to diagnose once already
     * (`docs/review.md` R5).
     */
    static std::string &sharedDataPath() {
        static std::string path;
        return path;
    }

    static std::mutex &sharedDataMutex() {
        static std::mutex guard;
        return guard;
    }

    sc68_t *sc68_ = nullptr;

    /**
     * What sc68 says about the tune, and the lock that makes reading it legal.
     *
     * **`selectSubsong` runs on the audio thread.** A pending subsong is applied inside the audio
     * callback (`Player::render`) precisely so a switch cannot land under a read in progress -- and
     * that is also what puts this struct on two threads, because `selectSubsong` refills it while
     * `describe()`, `durationSeconds()` and `subsongCount()` read it whenever the UI asks what is
     * playing. Filling a struct on one thread and reading it on another is a race whatever the
     * hardware does about it (`docs/review.md` R6, and round 6's R1 and R2).
     *
     * **A lock on the audio thread is deliberate here and is not the usual mistake.** It is taken
     * once per *subsong change* -- never per buffer -- and the section it holds is one library call
     * that fills a struct already in memory. The alternative was publishing half a dozen scalars
     * and a handful of strings as atomics, which is more machinery guarding the same thing less
     * clearly.
     */
    mutable std::mutex infoGuard_;
    sc68_music_info_t info_{};

    /**
     * Which track is playing, in sc68's own one-based numbering.
     *
     * Atomic for the reason its neighbours below are: written by the audio thread in
     * `selectSubsong`, read by the control thread in `rewind`. `restart()` stops the stream before
     * rewinding, so in practice the write is already visible -- which is exactly the argument round
     * 5 declined to rely on.
     *
     * One rather than zero because that is what `sc68_play` is given before any subsong is chosen.
     */
    std::atomic<int> current_{1};
    std::vector<short> scratch_;
    // Written by render() on the audio thread and read from elsewhere by positionSeconds(), which
    // is a race by the language's rules however benign it looks on ARM (`docs/review.md` R6). The
    // default sequentially-consistent ordering is more than a counter needs and costs one fence per
    // buffer, which is nothing next to emulating a 68000 -- so it is left alone rather than tuned.
    std::atomic<std::size_t> rendered_{0};
    std::atomic<bool> ended_{false};
};


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

    int subsongCount() const override { return ASAPInfo_GetSongs(info_); }

    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        song_ = index;
        // ASAP wants the duration of the song being started; -1 lets it play to its own end.
        durationMs_ = ASAPInfo_GetDuration(info_, song_);
        if (!ASAP_PlaySong(asap_, song_, durationMs_)) return false;
        ended_ = false;
        return true;
    }

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

        gme_track_info(emu_, &info_, track_);
        if (const gme_err_t err = gme_start_track(emu_, track_)) {
            const std::string message = std::string("game-music-emu could not start it: ") + err;
            if (info_) gme_free_info(info_);
            gme_delete(emu_);
            emu_ = nullptr;
            throw std::runtime_error(message);
        }

        // Without a fade the last buffer stops dead. GME applies one relative to the track length
        // it reports, which for files that do not state a length is its own two-and-a-half minutes.
        if (info_ && info_->play_length > 0) gme_set_fade(emu_, info_->play_length);

        openAtSomethingAudible();
    }

    /**
     * Start where the music is, not where the file starts.
     *
     * **HES and KSS routinely put nothing at track 0.** Measured 2026-09-04 across forty files from
     * Modland: every one of twenty HES files had music, and seven of them were silent at track 0 —
     * so a third of the format appeared broken while the music sat one track along. KSS is the same
     * shape. These are sound *banks* as much as albums; track 0 is often an empty slot or an effect.
     *
     * So when the first track renders nothing, look for one that does — entered **only** when
     * track 0 was silent, so a file that starts with music never pays for this.
     *
     * **Bounded by time rather than by a track count**, which is the second version of this. The
     * first stopped after twelve tracks, and the owner immediately found `aleste 2.kss`: 256
     * tracks, 82 of them audible, and **the first is number 47**. Twelve was a guess dressed as a
     * limit. A wall-clock budget makes no guess about how fast the phone is — a quick device
     * searches further, a slow one stops sooner and behaves as it did before — and it is the
     * quantity that actually matters, since what is being protected is the wait before sound.
     *
     * It does not touch `gme_track_count`, which reports a flat 256 for KSS and HES whatever the
     * file holds. That number is wrong — `aleste 2.kss` really has 82 tunes, not 256 — and finding
     * the truth means rendering every one of them, which is not something to do while somebody
     * waits. Recorded in `docs/PLAN_FORMATS.md` §2 rather than guessed at here.
     */
    void openAtSomethingAudible() {
        if (!emu_ || audible(kProbeFrames)) {
            gme_start_track(emu_, track_);
            return;
        }

        const auto deadline = std::chrono::steady_clock::now() + kSearchBudget;
        const int limit = gme_track_count(emu_);
        for (int candidate = 1; candidate < limit; ++candidate) {
            if (std::chrono::steady_clock::now() > deadline) break;
            if (gme_start_track(emu_, candidate)) continue;
            if (!audible(kProbeFrames)) continue;

            track_ = candidate;
            if (info_) { gme_free_info(info_); info_ = nullptr; }
            gme_track_info(emu_, &info_, track_);
            if (info_ && info_->play_length > 0) gme_set_fade(emu_, info_->play_length);
            gme_start_track(emu_, track_);
            return;
        }

        // Nothing audible anywhere we looked. Back to the beginning: a silent file is still that
        // file, and starting it somewhere arbitrary would be worse than starting it where it says.
        gme_start_track(emu_, track_);
    }

    /** Renders a moment and says whether any of it was above silence. Consumes what it renders. */
    bool audible(std::size_t frames) {
        const std::size_t samples = frames * 2;
        if (scratch_.size() < samples) scratch_.resize(samples);
        std::size_t done = 0;
        while (done < samples) {
            const std::size_t chunk = std::min<std::size_t>(2048, samples - done);
            if (gme_track_ended(emu_)) break;
            if (gme_play(emu_, static_cast<int>(chunk), scratch_.data())) break;
            for (std::size_t i = 0; i < chunk; ++i) {
                if (scratch_[i] != 0) return true;
            }
            done += chunk;
        }
        return false;
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
    void rewind() override { gme_start_track(emu_, track_); }

    int subsongCount() const override { return emu_ ? gme_track_count(emu_) : 1; }

    /** Which track is playing. Not always zero: see `openAtSomethingAudible`. */
    int currentSubsong() const override { return track_; }

    bool selectSubsong(int index) override {
        if (!emu_ || index < 0 || index >= subsongCount()) return false;
        if (const gme_err_t err = gme_start_track(emu_, index)) {
            LOGE("gme refused track %d: %s", index, err);
            return false;
        }
        track_ = index;
        // The info is per track: a GBS names each tune and gives each its own length, and a stale
        // struct would show the first one's title against the third one's audio.
        //
        // **Freed first.** `gme_track_info` allocates and its header says so -- "Must be freed
        // after use" -- and this overwrote the pointer, so every subsong change leaked a struct and
        // its strings. Small, and a 256-track HES walked through under "play all" leaks it 256
        // times. The destructor only ever freed the last one.
        if (info_) { gme_free_info(info_); info_ = nullptr; }
        gme_track_info(emu_, &info_, track_);

        // **And the fade has to move with it**, because in this library the fade is what ends the
        // track: "Once fade ends track_ended() returns true". It was set once in the constructor
        // from track 0's length, so every later tune in a GBS faded at the first tune's time --
        // early for the long ones, and for a file whose first track states no length, not at all.
        if (info_ && info_->play_length > 0) gme_set_fade(emu_, info_->play_length);
        return true;
    }
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
          // Where this file actually opened. Zero for nearly everything; not for a HES whose first
          // track is an empty slot (`openAtSomethingAudible`), and the playlist row and the subsong
          // strip both have to agree with the audio.
          << "subsong\t" << track_ << '\n'
          << "seekable\t1" << '\n'
          << "message\t" << (info_ ? field(info_->comment) : "");
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;
    /** A fifth of a second is plenty to tell music from an empty slot, and cheap to throw away. */
    static constexpr std::size_t kProbeFrames = kSampleRate / 5;
    /**
     * How long the search may take, measured on the clock the listener is also watching.
     *
     * 300 ms is the number because it is roughly where a delay stops reading as "loading" and
     * starts reading as "broken", and because the alternative it replaced -- a fixed twelve tracks
     * -- missed a file whose first tune is number 47. On a phone that emulates at fifty times real
     * time this reaches well past that; on a slow one it gives up early, which is the behaviour it
     * had before and no worse.
     */
    static constexpr std::chrono::milliseconds kSearchBudget{300};
    // Subsong selection is a UI feature that does not exist yet; some of these files hold hundreds.
    int track_ = 0;

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

    int subsongCount() const override { return info_ ? static_cast<int>(info_->songs()) : 1; }

    /**
     * libsidplayfp selects on the *tune* and the engine has to be handed it again.
     *
     * Its songs are numbered from one, like sc68's; the interface counts from zero. And unlike the
     * others this cannot simply be told to jump -- the tune is reselected and the whole machine
     * reloaded, which is why the mixer is initialised again as well. Forgetting that is a segfault,
     * and it is the same one that cost a day when this backend was written.
     */
    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        tune_.selectSong(static_cast<unsigned int>(index + 1));
        if (!engine_.load(&tune_)) return false;
        engine_.initMixer(false);
        info_ = tune_.getInfo();
        spare_.clear();
        spareRead_ = 0;
        rendered_ = 0;
        return true;
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

    // Both written by render() on the audio thread and read by positionSeconds() from elsewhere.
    // Same reasoning as Sc68Backend: a race by the language's rules, and the default ordering costs
    // a fence per buffer against the price of emulating a 6502 (`docs/review.md` R6).
    std::atomic<std::uint64_t> rendered_{0};
    std::atomic<int> rate_{kSampleRate};

    SidTune tune_;
    SIDLiteBuilder builder_;
    sidplayfp engine_;
    const SidTuneInfo *info_ = nullptr;
    std::vector<short> scratch_;
    std::vector<short> spare_;
    std::size_t spareRead_ = 0;
};

/**
 * AHX and HVL, through HivelyTracker's standalone replayer.
 *
 * The Amiga synth trackers: no samples in the file, a handful of waveforms and a per-instrument
 * program that bends them. libopenmpt has **no loader for either** -- measured in
 * `docs/PLAN_FORMATS.md` §0b, 0/12 and 0/12 -- which is why `.ahx` and `.hvl` were taken out of
 * `SupportedFormats` on 2026-09-04 and why they are back now. 1,433 Modland files.
 *
 * Measured on the host before integration (§6): **80 of 80 sampled files loaded from a buffer, were
 * audible and reached a song end.** The first backend here to come back clean on both halves.
 *
 * Two things make it unlike the other five:
 *
 * **It renders a PAL frame at a time.** `hvl_DecodeFrame` fills exactly one 1/50s buffer and picks
 * that size itself; everything else here renders however many frames it is asked for. So this is
 * the one backend that carries a ring buffer, and `framesPerCall_` is computed the way
 * `hvl_DecodeFrame` computes it -- `rate/50/multiplier*multiplier`, which is 880 rather than 882
 * when the speed multiplier is four. Assuming 882 would emit two stale samples every frame.
 *
 * **It knows how long the tune is, cheaply.** `hvl_play_irq` is the sequencer without the mixer, so
 * running a tune to its end costs about a millisecond instead of thirty. The length is therefore
 * measured at load, unconditionally, and `.ahx` and `.hvl` get a real duration and a working seek
 * bar without `SongLengths` knowing anything about them. Verified on the host: sequencer-only and
 * full-render lengths agreed on every file tried.
 */
class HivelyBackend : public Backend {
public:
    /** Both of the replayer's own magics. AHX is "THX" -- the format is older than its tracker. */
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        const auto *b = reinterpret_cast<const unsigned char *>(bytes.data());
        return (b[0] == 'T' && b[1] == 'H' && b[2] == 'X' && b[3] < 3) ||
               (b[0] == 'H' && b[1] == 'V' && b[2] == 'L' && b[3] < 2);
    }

    explicit HivelyBackend(const std::vector<char> &bytes) {
        static std::once_flag once;
        std::call_once(once, [] { hvl_InitReplayer(); });

        // The loader walks the file computing sizes and **never checks against its length**: a
        // truncated file sends it reading past the end, and `strncpy(ht_Name, &buf[offset], 128)`
        // does the same with an offset taken straight from the header. Rather than restate its
        // data-dependent walk here -- a second copy of a parser is a copy that goes stale -- the
        // bytes are handed over inside a padded, zeroed allocation big enough that the walk cannot
        // leave it.
        //
        // The bound is arithmetic, not a guess. Every count it reads is a byte or a twelve-bit
        // field: at most 4,095 positions of 67 channels x 2, 256 tracks x 255 rows x 5 bytes, and
        // 64 instruments of 22 + 255 x 5. That is under 960 KB, so a mebibyte of zeros past the end
        // is provably past the worst the header can ask for -- and zeros terminate both walks
        // early, because a zero row is not 0x3f and a zero instrument has no program.
        std::vector<unsigned char> padded(bytes.size() + kLoaderSlack, 0);
        std::memcpy(padded.data(), bytes.data(), bytes.size());

        ahx_ = static_cast<unsigned char>(bytes[0]) == 'T';

        // freeit = 0: `padded` stays ours and is dropped on the way out of this constructor. The
        // tune is one allocation of its own by then.
        ht_ = hvl_reset(padded.data(), static_cast<uint32>(bytes.size()), kStereoSeparation,
                        static_cast<uint32>(kSampleRate), 0);
        if (!ht_) throw std::runtime_error("HivelyTracker could not load this file");

        const uint32 multiplier = ht_->ht_SpeedMultiplier ? ht_->ht_SpeedMultiplier : 1;
        framesPerCall_ = static_cast<std::size_t>(kSampleRate / 50 / multiplier) * multiplier;
        frame_.resize(framesPerCall_ * 2);

        duration_ = measureSeconds(0);
        startSubsong(0);
    }

    ~HivelyBackend() override {
        if (ht_) hvl_FreeTune(ht_);
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        std::size_t written = 0;
        while (written < frames) {
            if (framePos_ == framesPerCall_) {
                // A tune that has reached its end is not decoded again: the replayer would happily
                // keep going round, and a short render is what tells the engine the file is over.
                if (ht_->ht_SongEndReached) break;
                hvl_DecodeFrame(ht_, reinterpret_cast<int8 *>(frame_.data()),
                                reinterpret_cast<int8 *>(frame_.data()) + 2, 4);
                framePos_ = 0;
            }
            const std::size_t take = std::min(frames - written, framesPerCall_ - framePos_);
            for (std::size_t i = 0; i < take; ++i) {
                out[(written + i) * 2] = frame_[(framePos_ + i) * 2] / 32768.0f;
                out[(written + i) * 2 + 1] = frame_[(framePos_ + i) * 2 + 1] / 32768.0f;
            }
            framePos_ += take;
            written += take;
        }
        rendered_ += written;
        return written;
    }

    bool canSeek() const override { return true; }

    /**
     * Seeks by starting again and decoding forward, because the replayer has no other way in.
     *
     * Decoding rather than sequencing alone: the sequencer gives the right position but the wrong
     * voice state, and the difference is audible as a wrong note at the seek point. Full decoding
     * costs about 30ms per minute skipped on a desktop, which is what a seek can afford and a load
     * cannot -- the length measurement is the other way round for exactly that reason.
     */
    void seek(double seconds) override {
        const auto target = static_cast<std::size_t>(std::max(0.0, seconds) * kSampleRate);
        startSubsong(subsong_);
        while (rendered_ + framesPerCall_ <= target && !ht_->ht_SongEndReached) {
            hvl_DecodeFrame(ht_, reinterpret_cast<int8 *>(frame_.data()),
                            reinterpret_cast<int8 *>(frame_.data()) + 2, 4);
            rendered_ += framesPerCall_;
        }
        framePos_ = framesPerCall_;
    }

    void rewind() override { startSubsong(subsong_); }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / kSampleRate;
    }

    double durationSeconds() const override { return duration_; }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << title() << '\n'
          << "format\t" << (ahx_ ? "Amiga AHX (HivelyTracker)" : "Amiga HVL (HivelyTracker)") << '\n'
          << "channels\t" << ht_->ht_Channels << '\n'
          << "subsongs\t" << subsongCount() << '\n'
          << "seekable\t1";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

    // `ht_SubsongNr` is how many *extra* subsongs there are -- `hvl_InitSubsong` accepts indices up
    // to and including it -- so a file with none reports zero and holds one tune.
    int subsongCount() const override { return static_cast<int>(ht_->ht_SubsongNr) + 1; }

    int currentSubsong() const override { return subsong_; }

    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        duration_ = measureSeconds(index);
        if (!startSubsong(index)) return false;
        return true;
    }

private:
    // 44100 divides by 50, which the replayer's frame size assumes without saying so.
    static constexpr int kSampleRate = 44100;

    // 4 is upstream's "Paula" setting: full stereo separation, the Amiga's own. It only affects AHX;
    // HVL files carry their own panning.
    static constexpr uint32 kStereoSeparation = 4;

    // Derived in the constructor's comment. A mebibyte, once per file loaded.
    static constexpr std::size_t kLoaderSlack = 1024 * 1024;

    bool startSubsong(int index) {
        if (!hvl_InitSubsong(ht_, static_cast<uint32>(index))) return false;
        ht_->ht_SongEndReached = 0;
        subsong_ = index;
        rendered_ = 0;
        framePos_ = framesPerCall_;      // nothing buffered; the next render decodes
        return true;
    }

    /**
     * How long a subsong runs, by running the sequencer without the mixer.
     *
     * Bounded at ten minutes, which is what `hvl2wav` uses, because a tune that never sets
     * `ht_SongEndReached` would otherwise spin here. A zero return reads as "unknown" upstream and
     * disables the scrubber rather than offering a wrong one.
     */
    double measureSeconds(int index) {
        if (!hvl_InitSubsong(ht_, static_cast<uint32>(index))) return 0.0;
        ht_->ht_SongEndReached = 0;
        const uint32 multiplier = ht_->ht_SpeedMultiplier ? ht_->ht_SpeedMultiplier : 1;
        long ticks = 0;
        const long limit = 600 * 50;
        for (; ticks < limit && !ht_->ht_SongEndReached; ++ticks)
            for (uint32 i = 0; i < multiplier; ++i) hvl_play_irq(ht_);
        return ticks >= limit ? 0.0 : static_cast<double>(ticks) / 50.0;
    }

    /** `ht_Name` is filled by a 128-byte `strncpy` that need not have terminated it. */
    std::string title() const {
        const char *name = ht_->ht_Name;
        const std::size_t length = static_cast<std::size_t>(
            std::find(name, name + sizeof(ht_->ht_Name), '\0') - name);
        std::string out(name, length);
        std::replace_if(out.begin(), out.end(),
                        [](char c) { return static_cast<unsigned char>(c) < 0x20; }, ' ');
        return out;
    }

    struct hvl_tune *ht_ = nullptr;
    bool ahx_ = false;
    std::size_t framesPerCall_ = 0;
    std::size_t framePos_ = 0;
    std::vector<short> frame_;
    std::size_t rendered_ = 0;
    double duration_ = 0.0;
    int subsong_ = 0;
};

/**
 * The ZX Spectrum AY trackers, through ZXTune: PT3, PT2, STC, ASC, SQT, STP and their relatives.
 *
 * The largest platform in Modland this app could not play -- 23,891 files, of which 58 opened,
 * because `.ay` happens to be a name game-music-emu also uses. These are trackers driving an
 * AY-3-8912, which is why they belong here and the 95,000 `*SF` files do not: those are console
 * emulators (`docs/PLAN_FORMATS.md` §7).
 *
 * Measured on the host before integration: **72 of 72 sampled files loaded from a buffer and were
 * audible, and every one stated its own length** -- median 149s. So these arrive with a duration
 * and a working seek bar without `SongLengths` knowing anything about them.
 *
 * Three things make it unlike the other backends:
 *
 * **Two shapes of factory.** ProTracker3 hands back a `Module::Factory`, which produces a holder
 * directly; every other AY plugin hands back an `AYM::Factory`, which produces a *chiptune* that
 * `AYM::CreateHolder` then wraps. ZXTune's own plugin layer papers over this and drags in its whole
 * plugin registry to do it, so this does it by hand.
 *
 * **The renderer needs the module's own properties, not fresh ones.** The AY renderer reads its
 * frequency table from parameters -- ZXTune's source says "frequency table is mandatory!!!" and
 * throws without it -- and the table is something the *file* chose, put there while loading. Handing
 * it an empty container throws at the first render rather than at construction, which reads as a
 * decoder that cannot decode.
 *
 * **Render returns chunks of its own size**, like HivelyTracker and unlike everything else, so this
 * carries a ring buffer.
 */
class ZxTuneBackend : public Backend {
public:
    /**
     * Whether any of ZXTune's AY decoders will take these bytes.
     *
     * There is no cheap magic to test: `.stc` and `.pt2` have no signature worth the name, and
     * ZXTune's decoders check structure instead. So recognition *is* loading, and the answer is
     * whether the constructor throws -- which is why this backend is asked late, after everything
     * that can identify a file by a header.
     */
    static bool worthTrying(const std::vector<char> &bytes, const std::string &name) {
        if (bytes.size() < 64) return false;
        const auto dot = name.find_last_of('.');
        if (dot == std::string::npos) return false;
        std::string extension = name.substr(dot + 1);
        for (auto &c : extension) c = static_cast<char>(std::tolower(c));
        return extension == "pt3" || extension == "pt2" || extension == "pt1" ||
               extension == "stc" || extension == "st1" || extension == "st3" ||
               extension == "asc" || extension == "as0" || extension == "sqt" ||
               extension == "stp" || extension == "psm" || extension == "ftc" ||
               extension == "gtr";
    }

    explicit ZxTuneBackend(const std::vector<char> &bytes) {
        const auto data = Binary::CreateContainer(
            Binary::View(bytes.data(), bytes.size()));

        namespace FC = Formats::Chiptune;
        // Ordered by how much of Modland each is worth, so the common case is decided first.
        tryFull("PT3", Module::ProTracker3::CreateFactory(FC::ProTracker3::CreateDecoder()), data);
        tryFull("PT3", Module::ProTracker3::CreateFactory(FC::ProTracker3::VortexTracker2::CreateDecoder()), data);
        tryAym("PT2", Module::ProTracker2::CreateFactory(), data);
        tryAym("STC", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateCompiledDecoder()), data);
        tryAym("ST1", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateUncompiledDecoder()), data);
        tryAym("ST3", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver3::CreateDecoder()), data);
        tryAym("ASC", Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver1::CreateDecoder()), data);
        tryAym("AS0", Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver0::CreateDecoder()), data);
        tryAym("STP", Module::SoundTrackerPro::CreateFactory(FC::SoundTrackerPro::CreateCompiledModulesDecoder()), data);
        tryAym("SQT", Module::SQTracker::CreateFactory(), data);
        // These three were claimed by `worthTrying` and by `SupportedFormats` before they were
        // implemented here, so `.psm`, `.ftc` and `.gtr` opened a file, refused it, and the app
        // said "is a format Protracktor cannot play yet" -- which is the message for a decoder that
        // does not exist, and was true only because this list was three lines short.
        tryAym("PT1", Module::ProTracker1::CreateFactory(), data);
        tryAym("PSM", Module::ProSoundMaker::CreateFactory(), data);
        tryAym("FTC", Module::FastTracker::CreateFactory(), data);
        tryAym("GTR", Module::GlobalTracker::CreateFactory(), data);

        if (!holder_) throw std::runtime_error("ZXTune did not recognise this file");

        durationSeconds_ = holder_->GetModuleInformation().Duration.CastTo<Time::Second>().Get();
        start();
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        std::size_t written = 0;
        while (written < frames) {
            if (chunkPos_ == chunk_.size()) {
                if (ended_) break;
                chunk_ = renderer_->Render();
                chunkPos_ = 0;
                if (chunk_.empty()) { ended_ = true; break; }
            }
            const std::size_t take = std::min(frames - written, chunk_.size() - chunkPos_);
            for (std::size_t i = 0; i < take; ++i) {
                const auto &sample = chunk_[chunkPos_ + i];
                out[(written + i) * 2] = sample.Left() / 32768.0f;
                out[(written + i) * 2 + 1] = sample.Right() / 32768.0f;
            }
            chunkPos_ += take;
            written += take;
        }
        rendered_ += written;
        return written;
    }

    bool canSeek() const override { return durationSeconds_ > 0; }

    void seek(double seconds) override {
        renderer_->SetPosition(
            Time::AtMillisecond() + Time::Milliseconds(static_cast<uint_t>(seconds * 1000.0)));
        rendered_ = static_cast<std::size_t>(std::max(0.0, seconds) * kSampleRate);
        chunk_ = Sound::Chunk();
        chunkPos_ = 0;
        ended_ = false;
    }

    void rewind() override { start(); }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / kSampleRate;
    }

    double durationSeconds() const override { return durationSeconds_; }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << property("Title") << '\n'
          << "format\t" << "ZX Spectrum " << format_ << " (ZXTune)" << '\n'
          << "artist\t" << property("Author") << '\n'
          << "tracker\t" << property("Program") << '\n'
          << "channels\t3" << '\n'
          << "seekable\t" << (canSeek() ? 1 : 0) << '\n'
          << "message\t" << property("Comment");
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;

    /** A plugin that produces a holder itself. ProTracker3 is the only one of these. */
    void tryFull(const char *name, Module::Factory::Ptr factory, const Binary::Container::Ptr &data) {
        if (holder_ || !factory) return;
        try {
            auto props = Parameters::Container::Create();
            if (auto found = factory->CreateModule(*props, *data, props)) {
                holder_ = std::move(found);
                format_ = name;
            }
        } catch (...) {
            // A decoder that half-recognises a truncated file throws. That means "not this one".
        }
    }

    /** A plugin that produces a chiptune, which `AYM::CreateHolder` turns into a holder. */
    void tryAym(const char *name, Module::AYM::Factory::Ptr factory, const Binary::Container::Ptr &data) {
        if (holder_ || !factory) return;
        try {
            auto props = Parameters::Container::Create();
            if (auto chiptune = factory->CreateChiptune(*data, props)) {
                holder_ = Module::AYM::CreateHolder(std::move(chiptune));
                format_ = name;
            }
        } catch (...) {
        }
    }

    void start() {
        renderer_ = holder_->CreateRenderer(kSampleRate, holder_->GetModuleProperties());
        chunk_ = Sound::Chunk();
        chunkPos_ = 0;
        rendered_ = 0;
        ended_ = false;
    }

    /** One of the module's own string properties, or empty. */
    std::string property(const char *key) const {
        const auto value = holder_->GetModuleProperties()->FindString(key);
        return value ? std::string(*value) : std::string();
    }

    Module::Holder::Ptr holder_;
    Module::Renderer::Ptr renderer_;
    Sound::Chunk chunk_;
    std::size_t chunkPos_ = 0;
    std::size_t rendered_ = 0;
    double durationSeconds_ = 0.0;
    bool ended_ = false;
    std::string format_ = "AY";
};

std::unique_ptr<Backend> openBackend(std::vector<char> bytes, const std::string &name,
                                     std::string &error) {
    error.clear();

    // ASAP first when the name is one of its fourteen: several of its formats are told apart by
    // extension rather than by any header, so nothing else can make that call.
    if (AsapBackend::claimsName(name)) {
        try {
            return std::make_unique<AsapBackend>(bytes, name);
        } catch (const std::exception &e) {
            LOGE("ASAP claimed the name but refused: %s", e.what());
            error = std::string("ASAP refused it: ") + e.what();
        }
    }

    // SID first among the content-identified ones: its magic is four unambiguous bytes at offset
    // zero, which is as certain as identification gets.
    if (SidBackend::recognises(bytes)) {
        try {
            return std::make_unique<SidBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("libsidplayfp refused it: %s", e.what());
            error = e.what();
        }
    }

    // HivelyTracker next. "THX" and "HVL" plus a version byte at offset zero are as unambiguous as
    // SID's, and nothing else here loads either format, so a match cannot be a stray claim.
    if (HivelyBackend::recognises(bytes)) {
        try {
            return std::make_unique<HivelyBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("HivelyTracker recognised the header but refused: %s", e.what());
            error = e.what();
        }
    }

    // game-music-emu next, because it is the only backend that identifies by content rather than by
    // name or by trying: a header check that reads the bytes cannot claim something that is not its.
    if (GmeBackend::recognises(bytes)) {
        try {
            return std::make_unique<GmeBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("gme recognised the header but refused: %s", e.what());
            error = e.what();
        }
    }

    // ZXTune next, and by name rather than by content: `.stc` and `.pt2` carry no signature worth
    // testing, so ZXTune's decoders check structure and recognition *is* loading. That makes it a
    // backend to ask late -- after everything that can identify a file from a header -- and only
    // about names no other backend claims.
    if (ZxTuneBackend::worthTrying(bytes, name)) {
        try {
            return std::make_unique<ZxTuneBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("ZXTune refused it: %s", e.what());
            error = e.what();
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
            error = std::string("sc68 recognised this file but refused it: ") + e.what();
        }
    }
    try {
        return std::make_unique<OpenmptBackend>(bytes);
    } catch (const std::exception &e) {
        LOGE("libopenmpt refused: %s", e.what());
        if (error.empty()) {
            error = std::string("no backend recognised it: ") + e.what();
        }
    }
    return nullptr;
}

class Player : public oboe::AudioStreamDataCallback {
public:
    /**
     * Takes the backend, and reads its numbers once while it is safe to.
     *
     * Nothing is rendering yet, so this is the one place the control thread may ask the backend
     * anything at all. Afterwards the audio thread publishes and everybody else reads.
     */
    explicit Player(std::unique_ptr<Backend> backend) : backend_(std::move(backend)) {
        publishPosition();
        publishDuration();
    }

    int subsongCount() const { return backend_->subsongCount(); }

    /**
     * Switches tune.
     *
     * While the stream is running the switch is handed to the audio callback rather than performed
     * here: that callback is the only thread which touches the backend, and a decoder cannot be
     * changed underneath a read in progress.
     *
     * **But a finished tune has no callback left to hand it to.** When a backend runs out,
     * `onAudioReady` returns `Stop` and Oboe calls it no more -- `stream_` is still there, it is
     * simply never entered again. A request stored then would sit forever, `finished_` would stay
     * true, and the poll on the Kotlin side would ask for the next tune again and again: every
     * subsong of the file "played" instantly and in silence, which is exactly what the owner saw.
     * So when nothing is running, the switch happens here and the stream is started again.
     */
    void requestSubsong(int index) {
        if (running()) {
            pendingSubsong_.store(index, std::memory_order_release);
            return;
        }
        stop();
        if (backend_->selectSubsong(index)) {
            finished_.store(false, std::memory_order_release);
            // Stopped, so this thread owns the backend and may ask it directly.
            publishPosition();
            publishDuration();
            start();
        }
    }

    /**
     * Whether a callback is still being called.
     *
     * Not `stream_ != nullptr`: the stream outlives the last callback. Oboe is told to stop from
     * inside the callback when a tune ends, and the object stays until somebody closes it.
     */
    bool running() const { return stream_ != nullptr && !isFinished(); }

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
        const int subsong = pendingSubsong_.exchange(-1, std::memory_order_acq_rel);
        if (subsong >= 0 && backend_->selectSubsong(subsong)) {
            finished_.store(false, std::memory_order_release);
            // A different tune is a different length, and this is the thread allowed to ask.
            publishDuration();
        }

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

        // Published here rather than asked for later: the poll runs on another thread and these
        // libraries are not safe to touch from two at once.
        publishPosition();

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

    /** Reads the backend's own numbers. **Audio thread only**, or at open before it starts. */
    void publishPosition() { position_.store(backend_->positionSeconds(), std::memory_order_release); }
    void publishDuration() { duration_.store(backend_->durationSeconds(), std::memory_order_release); }

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
        // `running()`, not `stream_`: seeking a tune that has just ended would otherwise store a
        // request for a callback that will never run again. Same trap as the subsong switch above.
        if (running()) {
            pendingSeek_.store(target, std::memory_order_release);
        } else {
            backend_->seek(target);
            finished_.store(false, std::memory_order_release);
            publishPosition();
        }
    }

    /** Back to the beginning and playing. For repeat-one, and for replaying a finished tune. */
    bool restart() {
        stop();
        backend_->rewind();
        finished_.store(false, std::memory_order_release);
        publishPosition();
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

    /**
     * Where the tune is, read from a value the audio thread publishes.
     *
     * **Not by asking the backend.** libopenmpt says in its own header that "individual libopenmpt
     * objects are not thread-safe" and that a given object must be touched "from a single thread at
     * a time"; game-music-emu's `gme_tell` reads emulator state that `gme_play` is mutating. Both
     * were being asked from the polling coroutine, every tick, while the audio callback rendered --
     * an unsynchronised concurrent access to a library that says not to, on the two backends that
     * carry most of the library.
     *
     * sc68 and libsidplayfp never had this problem because they count frames into an atomic
     * (`docs/review.md` R6). This gives the other two the same shape rather than giving them a lock:
     * the audio thread already has the value and publishing it costs one store per buffer, while a
     * mutex would put the polling thread in a position to delay the one thread that must never be
     * late.
     */
    double positionSeconds() const { return position_.load(std::memory_order_acquire); }

    /**
     * How long it is, captured when it can change rather than polled.
     *
     * Read on the audio thread at the only two moments it can differ -- when the tune is opened and
     * when a subsong switch is applied -- because `openmpt::module::get_duration_seconds` is not a
     * cheap accessor and has no business running once per buffer.
     */
    double durationSeconds() const { return duration_.load(std::memory_order_acquire); }
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
    /** Published by the audio thread so the poll never touches a backend that is rendering. */
    std::atomic<double> position_{0.0};
    std::atomic<double> duration_{0.0};
    /** -1 means nothing pending. Applied by the audio callback, like a seek. */
    std::atomic<int> pendingSubsong_{-1};
};

Player *asPlayer(jlong handle) { return reinterpret_cast<Player *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeOpen(JNIEnv *env, jclass, jbyteArray data,
                                                          jstring fileName,
                                                          jobjectArray errorOut) {
    const jsize length = env->GetArrayLength(data);
    std::vector<char> bytes(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

    const char *nameChars = env->GetStringUTFChars(fileName, nullptr);
    const std::string name = nameChars ? nameChars : "";
    env->ReleaseStringUTFChars(fileName, nameChars);

    // The reason is a local and goes back with this call, not into a global for somebody to
    // collect afterwards. It used to be one process-wide std::string; that was fine while one
    // thread opened files at a time, and became a data race the moment library scanning was made
    // concurrent with playback -- ThreadSanitizer confirmed it (`docs/review.md` R2). Two threads
    // clearing and assigning one std::string is undefined behaviour, not merely a mixed-up message.
    std::string error;

    // No backend recognising the bytes is reported as a handle of 0. The caller says so to the user
    // rather than failing silently.
    auto backend = openBackend(std::move(bytes), name, error);

    if (errorOut && env->GetArrayLength(errorOut) > 0) {
        jstring text = env->NewStringUTF(error.c_str());
        env->SetObjectArrayElement(errorOut, 0, text);
        env->DeleteLocalRef(text);
    }

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

JNIEXPORT jint JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSubsongCount(JNIEnv *, jclass, jlong handle) {
    return asPlayer(handle)->subsongCount();
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSelectSubsong(JNIEnv *, jclass, jlong handle,
                                                                   jint index) {
    asPlayer(handle)->requestSubsong(index);
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSeek(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    asPlayer(handle)->seek(seconds);
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
    Sc68Backend::setSharedDataPath(chars ? chars : "");
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
