// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <cstring>
#include <memory>
#include <vector>

// Fake device only. Tests include the production Player/JNI translation unit unchanged.
namespace oboe {
enum class Result { OK = 0, ErrorDisconnected = -899, ErrorInternal = -896 };
enum class DataCallbackResult { Continue, Stop };
enum class Direction { Output };
enum class PerformanceMode { None };
enum class SharingMode { Shared };
enum class AudioFormat { Float };
enum class ChannelCount { Stereo };
enum class Usage { Media };
enum class ContentType { Music };
enum class SampleRateConversionQuality { Medium };
enum class StreamState { Open, Started, Stopped, Disconnected, Closed };
inline const char *convertToText(Result r) { return r == Result::OK ? "OK" : "fake device error"; }

class AudioStream;
class AudioStreamDataCallback {
public:
    virtual ~AudioStreamDataCallback() = default;
    virtual DataCallbackResult onAudioReady(AudioStream *, void *, int32_t) = 0;
};
class AudioStreamErrorCallback {
public:
    virtual ~AudioStreamErrorCallback() = default;
    virtual bool onError(AudioStream *, Result) { return false; }
    virtual void onErrorBeforeClose(AudioStream *, Result) {}
    virtual void onErrorAfterClose(AudioStream *, Result) {}
};

class AudioStream {
public:
    AudioStreamDataCallback *data = nullptr;
    std::shared_ptr<AudioStreamErrorCallback> error;
    StreamState state = StreamState::Open;
    Result startResult = Result::OK;
    int sampleRate = 48000;
    int stops = 0, closes = 0, starts = 0;
    int getSampleRate() const { return sampleRate; }
    StreamState getState() const { return state; }
    Result requestStart() {
        ++starts;
        if (startResult == Result::OK) state = StreamState::Started;
        return startResult;
    }
    Result stop() { ++stops; state = StreamState::Stopped; return Result::OK; }
    Result close() { ++closes; state = StreamState::Closed; return Result::OK; }
    void fail(Result r = Result::ErrorDisconnected) {
        state = StreamState::Disconnected;
        // Oboe uses its default close path when no custom callback takes ownership.
        if (!error || !error->onError(this, r)) {
            stop();
            if (error) error->onErrorBeforeClose(this, r);
            close();
            if (error) error->onErrorAfterClose(this, r);
        }
    }
    std::vector<float> render(int32_t frames = 8) {
        std::vector<float> out(frames * 2, -1.0f);
        if (state == StreamState::Started && data->onAudioReady(this, out.data(), frames) == DataCallbackResult::Stop)
            state = StreamState::Stopped;
        return out;
    }
};

struct TestDevice {
    static inline std::vector<std::shared_ptr<AudioStream>> streams;
    static inline Result openResult = Result::OK;
    static inline Result startResult = Result::OK;
    static inline int rate = 48000;
    static void reset() { streams.clear(); openResult = startResult = Result::OK; rate = 48000; }
};
class AudioStreamBuilder {
    AudioStreamDataCallback *data_ = nullptr;
    std::shared_ptr<AudioStreamErrorCallback> error_;
    int rate_ = 0;
public:
    AudioStreamBuilder *setDirection(Direction) { return this; }
    AudioStreamBuilder *setPerformanceMode(PerformanceMode) { return this; }
    AudioStreamBuilder *setSharingMode(SharingMode) { return this; }
    AudioStreamBuilder *setFormat(AudioFormat) { return this; }
    AudioStreamBuilder *setChannelCount(ChannelCount) { return this; }
    AudioStreamBuilder *setUsage(Usage) { return this; }
    AudioStreamBuilder *setContentType(ContentType) { return this; }
    AudioStreamBuilder *setDataCallback(AudioStreamDataCallback *cb) { data_ = cb; return this; }
    AudioStreamBuilder *setErrorCallback(std::shared_ptr<AudioStreamErrorCallback> cb) { error_ = std::move(cb); return this; }
    AudioStreamBuilder *setSampleRate(int rate) { rate_ = rate; return this; }
    AudioStreamBuilder *setSampleRateConversionQuality(SampleRateConversionQuality) { return this; }
    Result openStream(std::shared_ptr<AudioStream> &stream) {
        if (TestDevice::openResult != Result::OK) return TestDevice::openResult;
        stream = std::make_shared<AudioStream>();
        stream->data = data_;
        stream->error = error_;
        stream->sampleRate = rate_ > 0 ? rate_ : TestDevice::rate;
        stream->startResult = TestDevice::startResult;
        TestDevice::streams.push_back(stream);
        return Result::OK;
    }
};
} // namespace oboe
