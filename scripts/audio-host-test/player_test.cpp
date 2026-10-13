// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

#include <iostream>
#include <stdexcept>
#include <functional>
#include "../../native/engine/player_oboe.cpp"

namespace protracktor {
std::string describeOf(const Backend &backend) { return backend.describe(); }
}

namespace {
void require(bool value, const char *why) { if (!value) throw std::runtime_error(why); }
struct Decoder : Backend {
    double position = 12.0;
    int renders = 0, rewinds = 0, started = 0, subsong = 2;
    bool ended = false;
    std::size_t render(int, std::size_t frames, float *out) override {
        ++renders;
        if (ended) return 0;
        for (std::size_t i = 0; i < frames * 2; ++i) out[i] = 1.0f;
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
    std::cout << total << " audio-host checks, " << failed << " failed\n";
    return total == 0 || failed > 0 ? 1 : 0;
}
