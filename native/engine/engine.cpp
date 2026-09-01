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
#include <api68/api68.h>
#include <asap.h>
#include <gme.h>
}

#include <android/log.h>
#include <atomic>
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
    /** Where sc68's replay binaries were unpacked. Set once from Kotlin before anything is opened. */
    static std::string &sharedDataPath() {
        static std::string path;
        return path;
    }

    /**
     * A cheap pre-filter, not a verdict.
     *
     * api68_verify_mem is NOT usable for this: it returns -1 for an ICE-packed SNDH that
     * api68_load_mem then loads and plays perfectly. Gating on it is what stopped every SNDH in the
     * owner's library from opening. So this only asks "is it worth handing to sc68", and the real
     * answer comes from whether the load succeeds.
     */
    static bool worthTrying(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        if (api68_verify_mem(bytes.data(), static_cast<int>(bytes.size())) >= 0) return true;
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
        api68_init_t init;
        std::memset(&init, 0, sizeof(init));
        init.alloc = sc68Alloc;
        init.free = sc68Free;
        init.sampling_rate = kSampleRate;
        // Without this, SNDH loads and then refuses to play: sc68 wraps these tunes in a small
        // replay routine of its own (sndh_ice.bin and friends) that lives on disk, not in the file.
        init.shared_path = sharedDataPath().empty() ? nullptr : sharedDataPath().c_str();

        api_ = api68_init(&init);
        if (!api_) throw std::runtime_error("sc68 refused to initialise");

        if (api68_load_mem(api_, bytes.data(), static_cast<int>(bytes.size())) < 0) {
            api68_shutdown(api_);
            api_ = nullptr;
            throw std::runtime_error("sc68 could not load this file");
        }
        api68_play(api_, 1);
        api68_music_info(api_, &info_, 1, nullptr);
    }

    ~Sc68Backend() override {
        if (api_) {
            api68_stop(api_);
            api68_shutdown(api_);
        }
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        if (ended_) return 0;

        // sc68 produces interleaved 16-bit stereo; Oboe is running in float. Converting here keeps
        // the whole conversion on the audio thread and out of the rest of the engine.
        if (scratch_.size() < frames * 2) scratch_.resize(frames * 2);

        const int code = api68_process(api_, scratch_.data(), static_cast<int>(frames));
        if (code & API68_END) ended_ = true;

        for (std::size_t i = 0; i < frames * 2; ++i) {
            out[i] = static_cast<float>(scratch_[i]) / 32768.0f;
        }
        rendered_ += frames;
        return ended_ ? 0 : frames;
    }

    bool canSeek() const override { return false; }
    void seek(double) override {}

    void rewind() override {
        api68_stop(api_);
        api68_play(api_, 1);
        rendered_ = 0;
        ended_ = false;
    }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / static_cast<double>(kSampleRate);
    }

    double durationSeconds() const override {
        return static_cast<double>(info_.time_ms) / 1000.0;
    }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << (info_.title ? info_.title : "") << '\n'
          << "format\tAtari ST (sc68)" << '\n'
          << "tracker\t" << (info_.replay ? info_.replay : "") << '\n'
          << "artist\t" << (info_.author ? info_.author : "") << '\n'
          << "composer\t" << (info_.composer ? info_.composer : "") << '\n'
          << "hardware\t" << (info_.hwname ? info_.hwname : "") << '\n'
          << "subsongs\t" << info_.tracks << '\n'
          << "seekable\t0";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;

    api68_t *api_ = nullptr;
    api68_music_info_t info_{};
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
