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

std::unique_ptr<Backend> openBackend(std::vector<char> bytes) {
    lastOpenError().clear();
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
Java_com_przunk_protracktor_engine_NativeEngine_nativeOpen(JNIEnv *env, jclass, jbyteArray data) {
    const jsize length = env->GetArrayLength(data);
    std::vector<char> bytes(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

    // No backend recognising the bytes is reported as a handle of 0. The caller says so to the user
    // rather than failing silently.
    auto backend = openBackend(std::move(bytes));
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
