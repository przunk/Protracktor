// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Android's half of the engine: the Oboe callback, and the JNI surface above it.
//
// **Everything Android-specific in the native code is in this file**, which is the point of it
// existing. `engine.cpp` next door decodes and knows nothing about phones; this drives it from an
// audio callback and exposes fifteen functions to Kotlin. `docs/ARCHITECTURE.md` §4 is the rule
// this arrangement comes from -- PCM never crosses the boundary -- and `docs/OPEN_QUESTIONS.md` Q9
// is the argument about whether the boundary should be a process instead.

#include "engine.h"
#include "log.h"

#include <jni.h>
#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

namespace {

using protracktor::Backend;
using protracktor::openBackend;

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
        const int preferred = backend_->preferredSampleRate();
        if (preferred > 0) {
            builder.setSampleRate(preferred)
                ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
        }

        const oboe::Result result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(result));
            stream_.reset();
            return false;
        }
        // **The one assumption in this chain nobody has ever measured.** The comment above says
        // "Oboe resamples if need be", and if it ever does not, `getSampleRate()` comes back as the
        // device's rate, the callback hands that number to a backend that ignores it, and 44,100
        // samples play at 48,000 -- 8.8% fast, about a semitone and a half sharp. That is not a
        // hypothetical: it is exactly the defect the web build shipped with until 2026-09-08,
        // found by the owner saying a SID "sounded quicker than I remember".
        //
        // He said the same thing about two SPCs on 2026-09-09. A log line is what turns "I think
        // it sounds fast" into a fact, and it costs nothing on a path that runs once per track.
        if (preferred > 0 && stream_->getSampleRate() != preferred) {
            LOGE("sample rate: asked Oboe for %d, got %d -- the tune will play %.1f%% fast",
                 preferred, stream_->getSampleRate(),
                 (static_cast<double>(stream_->getSampleRate()) / preferred - 1.0) * 100.0);
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
    return env->NewStringUTF(protracktor::backendsFingerprint().c_str());
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSetDataPath(JNIEnv *env, jclass, jstring path) {
    const char *chars = env->GetStringUTFChars(path, nullptr);
    protracktor::setSharedDataPath(chars ? chars : "");
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
