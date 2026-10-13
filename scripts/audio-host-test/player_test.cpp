// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

#include <iostream>
#include <stdexcept>
#include <functional>
#include <limits>
#include "../../native/engine/player_oboe.cpp"

namespace protracktor {
std::string describeOf(const Backend &backend) { return backend.describe(); }
}

namespace {
void require(bool value, const char *why) { if (!value) throw std::runtime_error(why); }
struct Decoder : Backend {
    double position = 12.0;
    int renders = 0, rewinds = 0, started = 0, subsong = 2, lastRate = 0;
    bool ended = false;
    float left = 1.0f, right = 1.0f;
    std::size_t frameLimit = std::numeric_limits<std::size_t>::max();
    std::size_t render(int sampleRate, std::size_t frames, float *out) override {
        ++renders;
        lastRate = sampleRate;
        if (ended) return 0;
        frames = std::min(frames, frameLimit);
        for (std::size_t i = 0; i < frames; ++i) {
            out[i * 2] = left;
            out[i * 2 + 1] = right;
        }
        position += 0.25;
        return frames;
    }
    bool canSeek() const override { return true; }
    void seek(double seconds) override { position = seconds; }
    void rewind() override { ++rewinds; position = 0.0; }
    double positionSeconds() const override { return position; }
    double durationSeconds() const override { return 100.0; }
    std::string describe() const override { return "test backend"; }
    int subsongCount() const override { return 4; }
    int currentSubsong() const override { return subsong; }
    bool selectSubsong(int value) override { subsong = value; return true; }
    void startedPlaying() override { ++started; }
};
struct Fixture {
    Decoder *decoder;
    Player player;
    Fixture() : Fixture(std::make_unique<Decoder>()) {}
    explicit Fixture(std::unique_ptr<Decoder> backend) : decoder(backend.get()), player(std::move(backend)) {}
    std::shared_ptr<oboe::AudioStream> stream() { return oboe::TestDevice::streams.back(); }
};
}

int main() {
    int total = 0, failed = 0;
    auto check = [&](const char *name, const std::function<void()> &run) {
        oboe::TestDevice::reset();
        ++total;
        try { run(); std::cout << "✓ " << name << '\n'; }
        catch (const std::exception &e) { ++failed; std::cerr << "✗ " << name << ": " << e.what() << '\n'; }
    };
    check("normal start and render use the production Player", [] {
        Fixture f;
        require(f.player.start(), "initial start failed");
        f.stream()->render();
        require(f.player.positionSeconds() == 12.25, "position did not come from render");
        require(f.player.running(), "healthy stream not running");
    });
    check("the production builder registers a shared error callback", [] {
        Fixture f;
        f.player.start();
        require(f.stream()->error != nullptr, "no application error callback registered");
    });
    check("disconnection is not reported as a running stream or end of track", [] {
        Fixture f;
        f.player.start();
        f.stream()->fail();
        require(!f.player.running(), "stream existence was mistaken for callback activity");
        require(!f.player.isFinished(), "route error became a track ending");
    });
    check("explicit start replaces a disconnected stream without rewinding the decoder", [] {
        Fixture f;
        f.player.start();
        auto old = f.stream();
        old->render();
        old->fail();
        require(f.player.start(), "resume failed");
        require(oboe::TestDevice::streams.size() == 2, "start reused a dead stream");
        require(f.stream() != old, "stream identity unchanged");
        require(f.player.positionSeconds() == 12.25 && f.decoder->rewinds == 0, "decoder position reset");
        require(f.decoder->subsong == 2, "subsong changed");
    });
    check("healthy polling does not reopen audio or restart decoder work", [] {
        Fixture f;
        require(f.player.recoverOutput() == 0, "unstarted track recovered");
        f.player.start();
        require(f.player.recoverOutput() == 0, "healthy output recovered");
        require(oboe::TestDevice::streams.size() == 1 && f.decoder->started == 1, "healthy stream replaced");
    });
    check("the error thread only signals and the control poll performs stop close and reopen", [] {
        Fixture f;
        f.player.start();
        auto old = f.stream();
        old->fail();
        require(old->closes == 0 && old->stops == 0, "error callback raced control-thread close");
        require(oboe::TestDevice::streams.size() == 1, "error callback opened a stream");
        require(f.player.recoverOutput() == 1, "poll did not recover");
        require(old->closes == 1 && old->stops == 1, "old stream not closed exactly once");
        require(oboe::TestDevice::streams.size() == 2, "replacement stream missing");
    });
    check("JNI recovery preserves position subsong description duration and ducking gain", [] {
        Fixture f;
        f.player.start();
        f.player.setGain(0.25f);
        f.stream()->render();
        const auto text = f.player.describe();
        f.stream()->fail();
        const auto handle = reinterpret_cast<jlong>(&f.player);
        require(Java_com_przunk_protracktor_engine_NativeEngine_nativeRecoverOutput(nullptr, nullptr, handle) == 1,
                "JNI did not expose recovery");
        require(f.player.positionSeconds() == 12.25 && f.decoder->rewinds == 0, "position reset");
        require(f.decoder->subsong == 2 && f.player.describe() == text && f.player.durationSeconds() == 100,
                "track identity changed");
        require(f.decoder->started == 1, "recovery restarted decoder background work");
        auto samples = f.stream()->render();
        require(samples.front() == 0.25f && f.player.positionSeconds() == 12.5, "render/gain did not survive");
    });
    check("a disconnected state can be recovered before the delayed error callback runs", [] {
        Fixture f;
        f.player.start();
        auto old = f.stream();
        old->state = oboe::StreamState::Disconnected;
        require(f.player.recoverOutput() == 1, "state-only disconnection missed");
        old->fail();
        require(f.player.recoverOutput() == 0 && oboe::TestDevice::streams.size() == 2,
                "late old error damaged replacement");
    });
    check("manual pause after disconnection prevents automatic resume", [] {
        Fixture f;
        f.player.start();
        auto old = f.stream();
        old->fail();
        f.player.stop();
        old->fail();
        require(f.player.recoverOutput() == 0 && !f.player.running(), "pause was overridden");
        require(oboe::TestDevice::streams.size() == 1, "paused stream reopened");
    });
    check("late errors from a replaced stream cannot stop or reopen the current stream", [] {
        Fixture f;
        f.player.start();
        auto old = f.stream();
        old->fail();
        f.player.recoverOutput();
        old->error->onError(old.get(), oboe::Result::ErrorInternal);
        require(f.player.recoverOutput() == 0 && f.player.running(), "stale stream error accepted");
        require(oboe::TestDevice::streams.size() == 2, "stale error reopened current stream");
    });
    check("a shared callback can finish after Player destruction without retaining a Player pointer", [] {
        std::shared_ptr<oboe::AudioStream> old;
        {
            Fixture f;
            f.player.start();
            old = f.stream();
        }
        require(old->closes == 1, "Player destruction did not close output");
        require(old->error->onError(old.get(), oboe::Result::ErrorDisconnected), "error handling delegated back to Oboe");
        require(oboe::TestDevice::streams.size() == 1, "deleted Player resumed");
    });
    check("a natural ending is not output disconnection and never restarts the same tune", [] {
        Fixture f;
        f.player.start();
        f.decoder->ended = true;
        f.stream()->render();
        require(f.player.isFinished(), "natural ending lost");
        f.stream()->fail();
        require(f.player.recoverOutput() == 0, "finished tune recovered");
        require(oboe::TestDevice::streams.size() == 1, "finished tune reopened");
    });
    check("non route errors close output and fail instead of advancing or automatically reopening", [] {
        Fixture f;
        f.player.start();
        auto old = f.stream();
        old->fail(oboe::Result::ErrorInternal);
        require(f.player.recoverOutput() == -1, "unrecoverable error hidden");
        require(old->closes == 1 && !f.player.isFinished(), "error did not close or became track ending");
        require(f.player.recoverOutput() == 0 && oboe::TestDevice::streams.size() == 1, "background retry continued");
    });
    check("failed reopen reports failure once and explicit Play can retry at the same position", [] {
        Fixture f;
        f.player.start();
        f.stream()->render();
        f.stream()->fail();
        oboe::TestDevice::openResult = oboe::Result::ErrorInternal;
        require(f.player.recoverOutput() == -1 && !f.player.running(), "open failure concealed");
        require(f.player.recoverOutput() == 0 && !f.player.isFinished(), "failure retried or ended track");
        oboe::TestDevice::openResult = oboe::Result::OK;
        require(f.player.start(), "manual retry failed");
        require(f.player.positionSeconds() == 12.25 && f.decoder->rewinds == 0, "retry reset position");
    });
    check("failed requestStart closes the replacement without leaving a phantom running stream", [] {
        Fixture f;
        f.player.start();
        f.stream()->fail();
        oboe::TestDevice::startResult = oboe::Result::ErrorInternal;
        require(f.player.recoverOutput() == -1 && !f.player.running(), "requestStart failure hidden");
        require(f.stream()->closes == 1 && f.player.recoverOutput() == 0, "failed replacement retained");
    });
    check("repeated route changes each use a fresh output and the newly granted device rate", [] {
        Fixture f;
        f.player.start();
        for (int rate : {44100, 48000, 32000}) {
            f.stream()->fail();
            oboe::TestDevice::rate = rate;
            require(f.player.recoverOutput() == 1, "subsequent route not recovered");
            f.stream()->render();
            require(f.decoder->lastRate == rate, "old output rate reused");
        }
        require(f.decoder->rewinds == 0 && f.decoder->started == 1, "route changes reset decoder");
    });
    check("a seek made during disconnection is retained rather than restoring the old published position", [] {
        Fixture f;
        f.player.start();
        f.stream()->fail();
        f.player.seek(70.0);
        require(f.player.recoverOutput() == 1 && f.player.positionSeconds() == 70.0, "seek overwritten by recovery");
        require(f.decoder->position == 70.0, "published position hid a decoder rewind");
        f.stream()->render();
        require(f.player.positionSeconds() == 70.25, "render resumed from a different seek position");
    });
    check("a subsong selection during disconnection starts only the requested subsong", [] {
        Fixture f;
        f.player.start();
        f.stream()->fail();
        f.player.requestSubsong(1);
        require(f.decoder->subsong == 1 && f.player.running(), "subsong stranded on dead output");
        require(f.player.recoverOutput() == 0 && oboe::TestDevice::streams.size() == 2, "subsong restarted twice");
    });
    check("Original is the default and preserves stereo samples including signed zero", [] {
        Fixture f;
        f.decoder->left = -0.0f;
        f.decoder->right = -0.625f;
        f.player.start();
        const auto samples = f.stream()->render();
        require(std::signbit(samples[0]) && samples[0] == 0.0f && samples[1] == -0.625f,
                "Original altered the source PCM");
    });
    check("Narrow through JNI retains half the original stereo width", [] {
        Fixture f;
        f.decoder->right = 0.0f;
        Java_com_przunk_protracktor_engine_NativeEngine_nativeSetStereoWidth(
            nullptr, nullptr, reinterpret_cast<jlong>(&f.player), 50);
        f.player.start();
        const auto samples = f.stream()->render();
        require(samples[0] == 0.75f && samples[1] == 0.25f, "Narrow did not mix 75/25");
    });
    check("Mono sends a left-only signal equally to both ears", [] {
        Fixture f;
        f.decoder->right = 0.0f;
        f.player.setStereoWidth(0);
        f.player.start();
        const auto samples = f.stream()->render();
        require(samples[0] == 0.5f && samples[1] == 0.5f, "Mono did not average both channels");
    });
    check("Narrow and Mono also retain a right-only signal", [] {
        for (int width : {50, 0}) {
            Fixture f;
            f.decoder->left = 0.0f;
            f.player.setStereoWidth(width);
            f.player.start();
            const auto samples = f.stream()->render();
            require(samples[0] == (width == 50 ? 0.25f : 0.5f) &&
                    samples[1] == (width == 50 ? 0.75f : 0.5f), "right channel dropped or swapped");
        }
    });
    check("already mono source stays unchanged and mixing does not amplify full-scale signals", [] {
        for (int width : {100, 50, 0}) {
            Fixture f;
            f.player.setStereoWidth(width);
            f.player.start();
            const auto samples = f.stream()->render();
            require(samples[0] == 1.0f && samples[1] == 1.0f, "correlated channels amplified or attenuated");
        }
    });
    check("Mono averages opposite-phase channels instead of boosting or taking one side", [] {
        Fixture f;
        f.decoder->right = -1.0f;
        f.player.setStereoWidth(0);
        f.player.start();
        const auto samples = f.stream()->render();
        require(samples[0] == 0.0f && samples[1] == 0.0f, "Mono has incorrect opposite-phase behaviour");
    });
    check("changing mode while playing settles smoothly without opening or rewinding a track", [] {
        Fixture f;
        f.decoder->right = 0.0f;
        f.player.start();
        f.stream()->render();
        float previousLeft = 1.0f;
        for (int width : {50, 0, 100}) {
            f.player.setStereoWidth(width);
            const auto samples = f.stream()->render(480);
            const float expectedLeft = 0.5f + width / 200.0f;
            require(std::abs(samples[0] - previousLeft) < 0.003f, "first changed sample made an abrupt step");
            require(std::abs(samples[samples.size() - 2] - expectedLeft) < 0.00001f,
                    "new mode did not settle within 10ms");
            for (std::size_t i = 2; i < samples.size(); i += 2) {
                require(std::abs(samples[i] - samples[i - 2]) < 0.003f, "mode change made an abrupt step");
                require(samples[i] >= 0.0f && samples[i] <= 1.0f, "mix increased peak");
            }
            previousLeft = expectedLeft;
        }
        require(oboe::TestDevice::streams.size() == 1 && f.decoder->rewinds == 0 && f.decoder->started == 1,
                "mode change restarted playback");
        require(f.decoder->subsong == 2 && f.player.positionSeconds() == 13.0, "mode change changed tune or position");
    });
    check("the selected mix and ducking gain survive Bluetooth output recovery", [] {
        Fixture f;
        f.decoder->right = 0.0f;
        f.player.setStereoWidth(50);
        f.player.setGain(0.25f);
        f.player.start();
        f.stream()->render();
        f.stream()->fail();
        require(f.player.recoverOutput() == 1, "output did not recover");
        const auto samples = f.stream()->render();
        require(samples[0] == 0.1875f && samples[1] == 0.0625f, "mix or gain lost after recovery");
    });
    check("changing a paused mix does not resume or create output", [] {
        Fixture f;
        f.decoder->right = 0.0f;
        f.player.start();
        f.player.stop();
        f.player.setStereoWidth(0);
        require(!f.player.running() && oboe::TestDevice::streams.size() == 1, "mode selection resumed playback");
        f.player.start();
        const auto samples = f.stream()->render(480);
        require(samples[samples.size() - 2] == 0.5f && samples.back() == 0.5f, "resume ignored selected mix");
    });
    check("the final short buffer is mixed and its unrendered tail remains silent", [] {
        Fixture f;
        f.decoder->right = 0.0f;
        f.decoder->frameLimit = 2;
        f.player.setStereoWidth(0);
        f.player.start();
        const auto samples = f.stream()->render(8);
        require(samples[0] == 0.5f && samples[1] == 0.5f && samples[2] == 0.5f && samples[3] == 0.5f,
                "final frames not mixed");
        for (std::size_t i = 4; i < samples.size(); ++i) require(samples[i] == 0.0f, "end tail not silent");
        require(f.player.isFinished(), "mix hid EOF");
    });
    check("an unsupported JNI width safely falls back to Original", [] {
        for (int width : {-1, 25, 200}) {
            Fixture f;
            f.decoder->left = -0.5f;
            f.decoder->right = 0.75f;
            f.player.setStereoWidth(width);
            f.player.start();
            const auto samples = f.stream()->render();
            require(samples[0] == -0.5f && samples[1] == 0.75f, "unknown mode did not preserve source");
        }
    });
    check("mix transitions use the granted sample rate and also smooth a second target before settling", [] {
        for (int rate : {32000, 44100, 96000}) {
            Fixture f;
            oboe::TestDevice::rate = rate;
            f.decoder->right = 0.0f;
            f.player.start();
            f.stream()->render();
            f.player.setStereoWidth(0);
            const auto first = f.stream()->render(rate / 200); // half the transition time
            require(first[first.size() - 2] > 0.5f && first[first.size() - 2] < 1.0f,
                    "transition jumped instead of using the device rate");
            f.player.setStereoWidth(100);
            const auto back = f.stream()->render(rate / 100 + 1);
            require(std::abs(back[0] - first[first.size() - 2]) < 0.003f, "mid-ramp retarget jumped");
            require(back[back.size() - 2] == 1.0f && back.back() == 0.0f, "Original did not settle exactly");
        }
    });
    std::cout << total << " audio-host checks, " << failed << " failed\n";
    return total == 0 || failed > 0 ? 1 : 0;
}
