// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Android's half of the engine: the Oboe callback, and the JNI surface above it.
//
// Everything Android-specific in the native code is here. `engine.cpp` decodes and knows nothing
// about phones; this drives it from an audio callback and exposes sixteen functions to Kotlin.
// PCM never crosses the boundary (`docs/ARCHITECTURE.md` §4); whether it should be a separate
// process instead is `docs/OPEN_QUESTIONS.md` Q9.

#include "engine.h"
#include "log.h"

#include <jni.h>
#include <oboe/Oboe.h>

#include <atomic>
#include <exception>
#include <mutex>
#include <cstdint>
#include <memory>
#include <cstdio>
#include <string>
#include <utility>
#include <vector>

namespace {

using protracktor::Backend;
using protracktor::openBackend;

/**
 * Nothing thrown by a decoder may leave this file (`docs/STATUS.md` C42, C55).
 *
 * An exception reaching the JVM through a JNI frame, or reaching Oboe's real-time thread, is
 * `std::terminate`: the process ends with no message. Decoders are allowed to throw on malformed
 * files, which a public archive has plenty of.
 *
 * `openBackend` catches what *choosing* a backend throws; this covers everything after -- describe,
 * render, seek, subsong, length. The fallback is what each call already means by failure, so Kotlin
 * needs no new vocabulary: no handle, `false`, an empty string, zero. The reason goes to logcat.
 */
template <class T, class Work>
T guarded(const char *what, Work &&work, T fallback) {
    try {
        return work();
    } catch (const std::exception &e) {
        LOGE("%s threw and was contained: %s", what, e.what());
        return fallback;
    } catch (...) {
        LOGE("%s threw something that is not an exception and was contained", what);
        return fallback;
    }
}

template <class Work>
void guardedVoid(const char *what, Work &&work) {
    guarded<int>(what, [&] { work(); return 0; }, 0);
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
        publishSubsongs();
        publishDescribe();
    }

    /**
     * How many tunes are in the file, from a value the backend published rather than by asking it.
     *
     * Asking the backend here would read it from the control thread while the audio callback is
     * reading it too, which libopenmpt's header forbids (`docs/review-round-8.md` R2). The count
     * cannot change for the life of a file, so it is published once in the constructor.
     */
    int subsongCount() const { return subsongs_.load(std::memory_order_acquire); }

    /**
     * Switches tune.
     *
     * While the stream is running the switch is handed to the audio callback rather than performed
     * here: that callback is the only thread which touches the backend, and a decoder cannot be
     * changed underneath a read in progress.
     *
     * A finished tune has no callback left to hand it to: when a backend runs out, `onAudioReady`
     * returns `Stop` and Oboe never enters it again, though `stream_` is still there. A request
     * stored then would sit forever. So when nothing is running, the switch happens on this thread
     * and the stream is started again.
     */
    void requestSubsong(int index) {
        // **Under the lock, on this thread, running or not.** Switching tune is the same unbounded
        // work as a seek -- several backends reach tune five by running through four -- and it was
        // handed to the callback for the same reason and with the same consequence. The callback
        // plays silence while this holds the lock, which is a gap of a buffer or two where the old
        // way risked a stalled stream and a frozen app.
        const bool wasRunning = running();
        {
            const std::lock_guard<std::mutex> held(decoderGuard_);
            if (!backend_->selectSubsong(index)) return;
            finished_.store(false, std::memory_order_release);
            publishPosition();
            publishDuration();
            publishDescribe();
        }
        // A finished tune has no callback left to hand anything to, so the stream needs starting
        // for the new tune to be heard.
        if (!wasRunning) start();
    }

    /**
     * Whether a callback is still being called.
     *
     * Not `stream_ != nullptr`: the stream outlives the last callback. Oboe is told to stop from
     * inside the callback when a tune ends, and the object stays until somebody closes it.
     */
    bool running() const { return stream_ != nullptr && !isFinished(); }

    /**
     * **The decoder is locked, and the audio thread never waits for it.**
     *
     * `Backend::seek` is unbounded work for every emulator here (`docs/ARCHITECTURE.md` §5), so
     * it cannot happen on a thread with milliseconds to answer in. Seeking is done by its caller
     * under the lock; this takes the lock only if it is free.
     *
     * Failing to take it is not an error and never blocks: somebody is seeking, and a buffer of
     * silence is the right thing to play meanwhile. `try_lock` on an uncontended mutex is one
     * atomic compare-and-swap.
     */
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override {
        auto *out = static_cast<float *>(audioData);

        std::unique_lock<std::mutex> held(decoderGuard_, std::try_to_lock);
        if (!held.owns_lock()) {
            // Somebody has the decoder. Silence for this buffer, and the stream stays alive: a
            // stalled stream cannot be restarted from here and wedges the player.
            std::memset(out, 0, static_cast<std::size_t>(numFrames) * 2 * sizeof(float));
            return oboe::DataCallbackResult::Continue;
        }

        // The one guard that cannot sit at the JNI boundary: this is Oboe's real-time thread, and
        // an exception here unwinds into Oboe's C callback and ends the process. A throw is treated
        // as a decoder that ran out -- the tail of this function silences the buffer and stops the
        // stream, and Kotlin's poll moves to the next tune.
        const std::size_t rendered = guarded<std::size_t>(
            "render", [&] {
                return backend_->render(stream->getSampleRate(),
                                        static_cast<std::size_t>(numFrames), out);
            },
            0);

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

    /**
     * Reads the backend's own numbers. **Audio thread only**, or at open before it starts.
     *
     * Guarded at the definition rather than at each of the seven call sites, one of which is inside
     * the audio callback where a throw would end the process. Keeping the last published value is
     * the right fallback: a number that has stopped moving shows as a stalled progress bar, and the
     * tune ends on `render` returning nothing a buffer later anyway.
     */
    void publishPosition() {
        guardedVoid("positionSeconds",
                    [&] { position_.store(backend_->positionSeconds(), std::memory_order_release); });
    }
    void publishDuration() {
        guardedVoid("durationSeconds",
                    [&] { duration_.store(backend_->durationSeconds(), std::memory_order_release); });
    }
    void publishSubsongs() {
        guardedVoid("subsongCount",
                    [&] { subsongs_.store(backend_->subsongCount(), std::memory_order_release); });
    }

    /**
     * Takes the backend's description and keeps it, so nobody else has to ask the backend.
     *
     * **A mutex, and it is taken on the audio thread** -- but only when a tune actually changes,
     * never per buffer. The alternative was leaving `describe()` calling through and relying on
     * every caller to ask before the stream starts, which is what convention was doing and what
     * `docs/review-round-8.md` R3 is about. A lock held for one string copy, a few times a
     * listening session, is a smaller price than a rule nobody can see from the call site.
     *
     * It is also what gets a subsong's own title to the screen: a GBS names each of its tunes
     * (`docs/review-round-8.md` R4).
     */
    void publishDescribe() {
        // `describe()` walks a decoder's instrument and sample tables, which on a truncated file
        // is the first place a length read past the end becomes a throw (`docs/STATUS.md` C42). An
        // unreadable description costs a line of metadata, not the process.
        std::string text = guarded<std::string>("describe", [&] { return backend_->describe(); },
                                                std::string());
        const std::lock_guard<std::mutex> held(describeGuard_);
        describe_ = std::move(text);
    }

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
        // **On this thread, holding the lock**, however long it takes. The callback finds the lock
        // taken and plays silence meanwhile; nothing waits on anything that has a deadline. The
        // caller must not be the main thread -- `NativeEngine.Track.seekTo` says so and keeps to it.
        const std::lock_guard<std::mutex> held(decoderGuard_);
        backend_->seek(target);
        finished_.store(false, std::memory_order_release);
        publishPosition();
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
        // If Oboe does not resample, `getSampleRate()` is the device's rate, the callback hands
        // that number to a backend that ignores it, and 44,100 samples play at 48,000 -- 8.8%
        // fast, about a semitone and a half sharp (`docs/STATUS.md` C26).
        //
        // Kept as a string as well as logged, because logcat is not a channel a listener can
        // reach: this file's tag is `protracktor` and Kotlin's is `Protracktor`, so filtering on
        // one hides the other. The app asks for the note after starting and shows it once.
        rateNote_.clear();
        if (preferred > 0 && stream_->getSampleRate() != preferred) {
            const double fast =
                (static_cast<double>(stream_->getSampleRate()) / preferred - 1.0) * 100.0;
            char text[160];
            std::snprintf(text, sizeof(text),
                          "Audio: asked for %d Hz, got %d — playing %.1f%% fast",
                          preferred, stream_->getSampleRate(), fast);
            rateNote_ = text;
            LOGE("%s", text);
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
    /** The description as it was last published. Never reaches the backend; see `publishDescribe`. */
    std::string describe() const {
        const std::lock_guard<std::mutex> held(describeGuard_);
        return describe_;
    }

    /**
     * Empty unless Oboe opened the stream at a rate the backend did not ask for.
     *
     * Written once by `start()` and read by the app immediately afterwards, both on the caller's
     * thread — the audio callback never touches it, so it needs no atomic.
     */
    const std::string &rateNote() const { return rateNote_; }

private:
    std::unique_ptr<Backend> backend_;
    std::string rateNote_;
    /** Fixed for the life of a file, so published once and read by anybody. */
    std::atomic<int> subsongs_{1};
    mutable std::mutex describeGuard_;
    std::string describe_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> finished_{false};
    std::atomic<float> gain_{1.0f};

    /**
     * Held by whoever is touching the decoder. **The audio callback only ever tries.**
     *
     * The two requests that used to be queued for the callback -- a seek and a subsong switch --
     * are done by their caller under this instead, because both are unbounded and the callback is
     * not allowed to be. Nothing with a deadline ever blocks on it.
     */
    mutable std::mutex decoderGuard_;
    /** Published by the audio thread so the poll never touches a backend that is rendering. */
    std::atomic<double> position_{0.0};
    std::atomic<double> duration_{0.0};
};

Player *asPlayer(jlong handle) { return reinterpret_cast<Player *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeOpen(JNIEnv *env, jclass, jbyteArray data,
                                                          jstring fileName,
                                                          jobjectArray errorOut) {
    const jsize length = env->GetArrayLength(data);

    const char *nameChars = env->GetStringUTFChars(fileName, nullptr);
    const std::string name = nameChars ? nameChars : "";
    env->ReleaseStringUTFChars(fileName, nameChars);

    // A local, and it goes back with this call. A process-wide string here is a data race as soon
    // as scanning runs concurrently with playback, and two threads assigning one `std::string` is
    // undefined behaviour rather than merely a mixed-up message (`docs/review.md` R2).
    std::string error;

    // The one guarded call with somewhere to put the reason: it already carries a sentence back,
    // so a throw is written into it rather than left in logcat alone.
    //
    // `openBackend` catches each backend's own refusal, so what reaches here is what it does not:
    // `std::bad_alloc` from reading a file too big for the heap, and anything thrown that is not a
    // `std::exception` at all.
    std::unique_ptr<Backend> backend;
    try {
        // Copied out of the Java array here rather than above, so that a file too large to fit in
        // memory refuses with a sentence instead of ending the process.
        std::vector<char> bytes(static_cast<std::size_t>(length));
        env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

        // No backend recognising the bytes is reported as a handle of 0. The caller says so to the
        // user rather than failing silently.
        backend = openBackend(std::move(bytes), name, error);
    } catch (const std::exception &e) {
        LOGE("opening %s threw and was contained: %s", name.c_str(), e.what());
        error = std::string("the decoder failed while opening it: ") + e.what();
    } catch (...) {
        LOGE("opening %s threw something that is not an exception", name.c_str());
        error = "the decoder failed while opening it";
    }

    if (errorOut && env->GetArrayLength(errorOut) > 0) {
        jstring text = env->NewStringUTF(error.c_str());
        env->SetObjectArrayElement(errorOut, 0, text);
        env->DeleteLocalRef(text);
    }

    if (!backend) return 0;
    return guarded<jlong>(
        "starting the player", [&] { return reinterpret_cast<jlong>(new Player(std::move(backend))); },
        0);
}

/**
 * What Oboe actually gave us, when it is not what was asked for.
 *
 * A string rather than two numbers, because the caller does nothing with it but show it. Empty is
 * the normal answer and means the audio path is what it claims to be.
 */
JNIEXPORT jstring JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSampleRateNote(JNIEnv *env, jclass,
                                                                    jlong handle) {
    return guarded<jstring>(
        "rateNote", [&] { return env->NewStringUTF(asPlayer(handle)->rateNote().c_str()); },
        env->NewStringUTF(""));
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeClose(JNIEnv *, jclass, jlong handle) {
    // A decoder that throws while being torn down still has to be let go of: `delete` runs the
    // destructors either way, and containing the throw is all that is left to do.
    guardedVoid("close", [&] { delete asPlayer(handle); });
}

JNIEXPORT jboolean JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeStart(JNIEnv *, jclass, jlong handle) {
    return guarded<jboolean>("start", [&] { return asPlayer(handle)->start() ? JNI_TRUE : JNI_FALSE; },
                             JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeStop(JNIEnv *, jclass, jlong handle) {
    guardedVoid("stop", [&] { asPlayer(handle)->stop(); });
}

JNIEXPORT jboolean JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeIsFinished(JNIEnv *, jclass, jlong handle) {
    // A player nobody can ask is a player that has finished: saying so moves to the next tune,
    // where `JNI_FALSE` would leave a dead one on screen forever.
    return guarded<jboolean>(
        "isFinished", [&] { return asPlayer(handle)->isFinished() ? JNI_TRUE : JNI_FALSE; },
        JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeRestart(JNIEnv *, jclass, jlong handle) {
    return guarded<jboolean>(
        "restart", [&] { return asPlayer(handle)->restart() ? JNI_TRUE : JNI_FALSE; }, JNI_FALSE);
}

JNIEXPORT jint JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSubsongCount(JNIEnv *, jclass, jlong handle) {
    return guarded<jint>("subsongCount", [&] { return asPlayer(handle)->subsongCount(); }, 0);
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSelectSubsong(JNIEnv *, jclass, jlong handle,
                                                                   jint index) {
    guardedVoid("selectSubsong", [&] { asPlayer(handle)->requestSubsong(index); });
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSeek(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    guardedVoid("seek", [&] { asPlayer(handle)->seek(seconds); });
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
    return guarded<jstring>(
        "backendsFingerprint",
        [&] { return env->NewStringUTF(protracktor::backendsFingerprint().c_str()); },
        env->NewStringUTF(""));
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSetDataPath(JNIEnv *env, jclass, jstring path) {
    const char *chars = env->GetStringUTFChars(path, nullptr);
    guardedVoid("setDataPath", [&] { protracktor::setSharedDataPath(chars ? chars : ""); });
    env->ReleaseStringUTFChars(path, chars);
}

JNIEXPORT void JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeSetGain(JNIEnv *, jclass, jlong handle, jfloat gain) {
    guardedVoid("setGain", [&] { asPlayer(handle)->setGain(gain); });
}

JNIEXPORT jstring JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeDescribe(JNIEnv *env, jclass, jlong handle) {
    return guarded<jstring>(
        "describe", [&] { return env->NewStringUTF(asPlayer(handle)->describe().c_str()); },
        env->NewStringUTF(""));
}

JNIEXPORT jdouble JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativePositionSeconds(JNIEnv *, jclass, jlong handle) {
    return guarded<jdouble>("positionSeconds", [&] { return asPlayer(handle)->positionSeconds(); }, 0.0);
}

JNIEXPORT jdouble JNICALL
Java_com_przunk_protracktor_engine_NativeEngine_nativeDurationSeconds(JNIEnv *, jclass, jlong handle) {
    return guarded<jdouble>("durationSeconds", [&] { return asPlayer(handle)->durationSeconds(); }, 0.0);
}

}  // extern "C"
