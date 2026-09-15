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
 * **Nothing thrown by a decoder may leave this file** (`docs/STATUS.md` C42, C55).
 *
 * The counterpart of `player_wasm.cpp`'s guard, and the more serious of the two. On the web an
 * escaping exception wedges a worklet and the owner reloads the tab; here there is nothing above
 * to catch it -- an exception reaching the JVM through a JNI frame, or reaching Oboe's real-time
 * thread, is `std::terminate` and the process is gone mid-tune with no message.
 *
 * `openBackend` already catches what *choosing* a backend throws. Everything after it was
 * unguarded: describing a file, rendering, seeking, switching subsong, asking a length. A decoder
 * is allowed to throw on a malformed file -- half of ASMA and Modland is malformed somewhere -- and
 * this is where that stops being the app's problem.
 *
 * The fallback is what the call already means by failure, so Kotlin needs no new vocabulary: no
 * handle, `false`, an empty string, zero. The reason goes to logcat, which is where the other
 * refusals in this file already go.
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
     * **`docs/review-round-8.md` R2, and it is `docs/review.md` R6 with one case missed.** This used
     * to call straight through, and Kotlin calls it three lines after `start()` -- so
     * `get_num_subsongs()` and `gme_track_count()` ran on an object the audio callback was reading,
     * which is the exact thing libopenmpt's header forbids. The count cannot change for the life of
     * a file, so publishing it once in the constructor is the whole fix.
     */
    int subsongCount() const { return subsongs_.load(std::memory_order_acquire); }

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
        // A finished tune has no callback left to hand anything to: `onAudioReady` returned `Stop`
        // and Oboe will not enter it again, so the stream needs starting for the new tune to be
        // heard. Every subsong of a finished file otherwise "played" instantly and in silence,
        // which is what the owner saw before this was understood.
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
     * This used to hand a seek to the callback through an atomic, on the reasoning that the
     * callback is the only thread touching the decoder so the race disappears. The race did
     * disappear. What replaced it was worse: `Backend::seek` is **unbounded work** for every
     * emulator here -- a SID, an SC68, a GME and libopenmpt all reach a position by running
     * forward to it -- so seeking near the end of a five-minute tune meant emulating five minutes
     * of a 6502 inside a callback with a few milliseconds to answer in. The stream starves, goes
     * silent and stops advancing, and the next `close()` blocks waiting for that callback to
     * return, which on `Dispatchers.Main.immediate` is a frozen app. The owner hit exactly that
     * twice on 2026-09-10, both times by dragging the seek bar while a tune was still loading.
     *
     * So the work moved off this thread and a `try_lock` guards what is left. Failing to take the
     * lock is not an error and never blocks: it means somebody is seeking, and a buffer of silence
     * is the correct thing to play while they are. `try_lock` on an uncontended mutex is an atomic
     * compare-and-swap, which is what the old design was paying anyway.
     */
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override {
        auto *out = static_cast<float *>(audioData);

        std::unique_lock<std::mutex> held(decoderGuard_, std::try_to_lock);
        if (!held.owns_lock()) {
            // Somebody has the decoder. Silence for this buffer, and the stream stays alive --
            // which is the whole point, because a stream that stalls is what wedged the app.
            std::memset(out, 0, static_cast<std::size_t>(numFrames) * 2 * sizeof(float));
            return oboe::DataCallbackResult::Continue;
        }

        // **The one guard that cannot be put at the JNI boundary.** This is Oboe's real-time
        // thread, not a call from Kotlin: an exception thrown here unwinds into Oboe's C callback
        // and ends the process, and no `try` around `nativeStart` can see it. A decoder that throws
        // partway through a malformed file gets treated as a decoder that ran out -- the buffer is
        // silenced and the stream stops, which is what the tail of this function already does for a
        // tune that ends. Kotlin is polling `finished_`, so it moves to the next tune on its own.
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
     * It is also what makes a subsong's own title reach the screen: a GBS names each of its tunes
     * and the phone was showing the first one's for all of them (R4).
     */
    void publishDescribe() {
        // `describe()` is where the reported crash actually came from (`docs/STATUS.md` C42): it
        // walks a decoder's instrument and sample tables, which on a truncated file is the first
        // place a length read past the end turns into a throw. An unreadable description costs the
        // owner a line of metadata; it used to cost the process.
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
        // **The one assumption in this chain nobody has ever measured.** The comment above says
        // "Oboe resamples if need be", and if it ever does not, `getSampleRate()` comes back as the
        // device's rate, the callback hands that number to a backend that ignores it, and 44,100
        // samples play at 48,000 -- 8.8% fast, about a semitone and a half sharp. That is not a
        // hypothetical: it is exactly the defect the web build shipped with until 2026-09-08,
        // found by the owner saying a SID "sounded quicker than I remember".
        //
        // He said the same thing about two SPCs on 2026-09-09. A log line is what turns "I think
        // it sounds fast" into a fact, and it costs nothing on a path that runs once per track.
        // **Kept as a string as well as logged, because logcat is not a channel the owner can
        // reach.** The tag here is `protracktor` and the Kotlin side's is `Protracktor`, so
        // filtering on the obvious one shows everything except this — which is what happened when
        // he went looking. The app asks for this note straight after starting and says it out loud
        // once, which is a diagnosis a person can read on the device that has the problem.
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

    // The reason is a local and goes back with this call, not into a global for somebody to
    // collect afterwards. It used to be one process-wide std::string; that was fine while one
    // thread opened files at a time, and became a data race the moment library scanning was made
    // concurrent with playback -- ThreadSanitizer confirmed it (`docs/review.md` R2). Two threads
    // clearing and assigning one std::string is undefined behaviour, not merely a mixed-up message.
    std::string error;

    // **The one guarded call that has somewhere to put the reason.** Everywhere else in this file a
    // contained throw leaves only a logcat line, because the function it happened in returns a
    // number. This one already carries a sentence back for the owner to read, so a throw gets
    // written into it rather than swallowed.
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
    // A player nobody can ask is a player that has finished: saying so gets the owner the next
    // tune, where `JNI_FALSE` would leave a dead one on screen forever.
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
