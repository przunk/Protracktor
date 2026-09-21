// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/* Drives `UadeBackend` through the real `openBackend`, on the host.
 *
 * **The probe measured UADE; this measures our code around it.** `probe_uade.c` asks libuade
 * directly, so a 12/12 there says nothing about the scratch directory, the render loop, subsong
 * numbering or TFMX's companion -- all of which are ours, and all of which would otherwise be
 * tried for the first time on a phone, where "nothing plays" arrives with no reason attached.
 *
 * `engine.cpp` is compiled unchanged. Only the host differs from Android: this links the same
 * backends with the host compiler, the way the web build links them with Emscripten.
 *
 *   uade-drive play <file> [<companion>...]   one tune, every question below
 *   uade-drive pair <file> <file>             two at once: one emulator process each
 *
 * Paths come from the environment, as they come from Kotlin on the phone:
 *   UADE_CORE_FILE, UADE_BASE_DIR, UADE_SCRATCH_DIR
 *
 * Prints `VERDICT ok ...` or `VERDICT fail <reason> ...` last, so a reader can take one line.
 */
#include "engine.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

using protracktor::Backend;
using protracktor::Companion;

namespace {

constexpr int kRate = 44100;
constexpr std::size_t kFrames = 1024;

std::vector<char> slurp(const char *path) {
    std::ifstream file(path, std::ios::binary);
    return std::vector<char>(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
}

std::string leaf(const std::string &path) {
    const auto slash = path.find_last_of('/');
    return slash == std::string::npos ? path : path.substr(slash + 1);
}

/** Entries under the scratch root. Every backend removes its own, so after a close this is zero. */
int scratchEntries(const char *root) {
    int count = 0;
    if (DIR *dir = opendir(root)) {
        while (dirent *entry = readdir(dir)) {
            if (std::strcmp(entry->d_name, ".") && std::strcmp(entry->d_name, "..")) ++count;
        }
        closedir(dir);
    }
    return count;
}

struct Rendered {
    std::size_t firstFrames = 0;
    std::size_t totalFrames = 0;
    float peak = 0.0f;
};

Rendered render(Backend &backend, double seconds) {
    Rendered r;
    std::vector<float> buffer(kFrames * 2);
    const std::size_t target = static_cast<std::size_t>(seconds * kRate);
    bool first = true;
    while (r.totalFrames < target) {
        const std::size_t got = backend.render(kRate, kFrames, buffer.data());
        if (first) { r.firstFrames = got; first = false; }
        for (std::size_t i = 0; i < got * 2; ++i) r.peak = std::max(r.peak, std::fabs(buffer[i]));
        r.totalFrames += got;
        if (got < kFrames) break;
    }
    return r;
}

std::unique_ptr<Backend> open(const std::string &path, const std::vector<std::string> &companionPaths,
                              std::string &error) {
    std::vector<Companion> companions;
    for (const auto &c : companionPaths) companions.push_back(Companion{leaf(c), slurp(c.c_str())});
    return protracktor::openBackend(slurp(path.c_str()), leaf(path), error, std::move(companions));
}

int play(const std::string &path, const std::vector<std::string> &companions, const char *scratch) {
    std::string error;
    auto backend = open(path, companions, error);
    if (!backend) {
        std::printf("VERDICT fail refused \"%s\"\n", error.c_str());
        return 1;
    }
    const std::string described = backend->describe();
    const bool isUade = described.find("UADE") != std::string::npos ||
                        described.find("(") != std::string::npos;
    const int subsongs = backend->subsongCount();
    const int current = backend->currentSubsong();
    const int whileOpen = scratchEntries(scratch);

    const Rendered first = render(*backend, 10.0);
    // The subsong must not have moved on inside the stream: that is the player's decision.
    const bool stayed = backend->currentSubsong() == current;
    const double position = backend->positionSeconds();
    const double duration = backend->durationSeconds();

    // The last subsong, when there is more than one: the zero-based index has to land where UADE
    // numbers from, which is usually one.
    bool subsongOk = true;
    if (subsongs > 1) {
        subsongOk = backend->selectSubsong(subsongs - 1) &&
                    backend->currentSubsong() == subsongs - 1 &&
                    render(*backend, 1.0).firstFrames == kFrames;
    }
    backend->rewind();
    const bool rewindOk = render(*backend, 1.0).firstFrames == kFrames;

    backend.reset();
    const int afterClose = scratchEntries(scratch);

    const char *problem =
        first.firstFrames != kFrames ? "short-first-buffer"
        : first.peak == 0.0f ? "silent"
        : whileOpen != 1 ? "scratch-not-created"
        : afterClose != 0 ? "scratch-left-behind"
        : !stayed ? "subsong-advanced-by-itself"
        : std::fabs(position - static_cast<double>(first.totalFrames) / kRate) > 0.5 ? "position"
        : !subsongOk ? "subsong"
        : !rewindOk ? "rewind"
        : nullptr;
    std::printf("VERDICT %s%s backend=\"%s\" first=%zu peak=%.3f rendered=%.1fs position=%.1fs "
                "duration=%.1fs subsongs=%d current=%d uade=%s\n",
                problem ? "fail " : "ok", problem ? problem : "", described.c_str(),
                first.firstFrames, first.peak, static_cast<double>(first.totalFrames) / kRate,
                position, duration, subsongs, current, isUade ? "yes" : "no");
    return problem ? 1 : 0;
}

int pair(const std::string &a, const std::string &b, const char *scratch) {
    std::string errorA, errorB;
    auto first = open(a, {}, errorA);
    auto second = open(b, {}, errorB);
    if (!first || !second) {
        std::printf("VERDICT fail refused \"%s\" \"%s\"\n", errorA.c_str(), errorB.c_str());
        return 1;
    }
    const int bothOpen = scratchEntries(scratch);
    // Interleaved, the way playback and a folder scan interleave on the phone.
    std::vector<float> buffer(kFrames * 2);
    std::size_t full = 0;
    float peakA = 0, peakB = 0;
    for (int i = 0; i < 100; ++i) {
        if (first->render(kRate, kFrames, buffer.data()) == kFrames) ++full;
        for (float s : buffer) peakA = std::max(peakA, std::fabs(s));
        if (second->render(kRate, kFrames, buffer.data()) == kFrames) ++full;
        for (float s : buffer) peakB = std::max(peakB, std::fabs(s));
    }
    first.reset();
    second.reset();
    const int afterClose = scratchEntries(scratch);
    const bool ok = full == 200 && peakA > 0 && peakB > 0 && bothOpen == 2 && afterClose == 0;
    std::printf("VERDICT %s full=%zu/200 peaks=%.3f,%.3f scratch-open=%d scratch-after=%d\n",
                ok ? "ok" : "fail pair", full, peakA, peakB, bothOpen, afterClose);
    return ok ? 0 : 1;
}

}  // namespace

int main(int argc, char **argv) {
    const char *core = std::getenv("UADE_CORE_FILE");
    const char *base = std::getenv("UADE_BASE_DIR");
    const char *scratch = std::getenv("UADE_SCRATCH_DIR");
    if (argc < 3 || !core || !base || !scratch) {
        std::fprintf(stderr, "usage: UADE_CORE_FILE=… UADE_BASE_DIR=… UADE_SCRATCH_DIR=… "
                             "uade-drive play <file> [companion…] | pair <a> <b>\n");
        return 2;
    }
    protracktor::setUadePaths(core, base, scratch);
    const std::string mode = argv[1];
    if (mode == "play") {
        return play(argv[2], std::vector<std::string>(argv + 3, argv + argc), scratch);
    }
    if (mode == "pair" && argc == 4) return pair(argv[2], argv[3], scratch);
    return 2;
}
