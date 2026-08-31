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

#include <android/log.h>
#include <atomic>
#include <cstring>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#define LOG_TAG "protracktor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

class Player : public oboe::AudioStreamDataCallback {
public:
    explicit Player(std::vector<char> bytes)
        : module_(std::make_unique<openmpt::module>(bytes.data(), bytes.size())) {}

    // Not locked, and deliberately so. Oboe's stop() blocks until an in-flight callback returns, and
    // every control path below stops the stream before touching the module -- so the callback is the
    // only reader while it runs, and never concurrent with a writer. A mutex here would be a lock on
    // the audio thread bought for nothing.
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override {
        auto *out = static_cast<float *>(audioData);

        // A seek requested from another thread is applied HERE rather than there. libopenmpt's
        // module is not safe to move under a read in progress, and the audio callback is the only
        // thread that reads it, so handing the request over and letting the callback act on it
        // removes the race without stopping the stream and clicking.
        const double seekTo = pendingSeek_.exchange(NO_SEEK, std::memory_order_acq_rel);
        if (seekTo >= 0.0) {
            module_->set_position_seconds(seekTo);
            finished_.store(false, std::memory_order_release);
        }

        const std::size_t rendered = module_->read_interleaved_stereo(
            stream->getSampleRate(), static_cast<std::size_t>(numFrames), out);

        if (rendered < static_cast<std::size_t>(numFrames)) {
            // End of the module. Silence the remainder rather than leaving whatever the buffer held,
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
     * Asks for a new position. Applied by the audio callback on its next pass, or immediately when
     * nothing is playing and there is no callback to hand it to.
     */
    void seek(double seconds) {
        const double target = seconds < 0.0 ? 0.0 : seconds;
        if (stream_) {
            pendingSeek_.store(target, std::memory_order_release);
        } else {
            module_->set_position_seconds(target);
            finished_.store(false, std::memory_order_release);
        }
    }

    /** Back to the beginning and playing again. Used for repeat-one and for replaying a finished track. */
    bool restart() {
        stop();
        module_->set_position_seconds(0.0);
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

    double positionSeconds() const { return module_->get_position_seconds(); }
    double durationSeconds() const { return module_->get_duration_seconds(); }

    std::string describe() const {
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
          << "duration\t" << module_->get_duration_seconds() << '\n'
          << "message\t" << module_->get_metadata("message_raw");
        return o.str();
    }

private:
    std::unique_ptr<openmpt::module> module_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> finished_{false};

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

    // libopenmpt reports an unrecognised or corrupt module by throwing. A handle of 0 is how that
    // reaches Kotlin; the caller is expected to say so rather than fail silently.
    try {
        return reinterpret_cast<jlong>(new Player(std::move(bytes)));
    } catch (const std::exception &e) {
        LOGE("open failed: %s", e.what());
        return 0;
    }
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
