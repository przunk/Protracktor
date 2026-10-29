// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// The web's half of the engine: a flat C API, for an AudioWorklet and for a probe.
//
// **The counterpart of `player_oboe.cpp`, and deliberately smaller.** On Android the engine owns
// the audio device, because Oboe hands it a real-time callback and `docs/ARCHITECTURE.md` §4 says
// PCM must not cross a boundary. In a browser the worklet *is* that callback and it lives in
// JavaScript, so this side does not own a device at all: it decodes into a buffer when asked, and
// `process()` asks. Same rule, fewer parts -- there is no thread here to be late.
//
// No `Player` class either, for the same reason: `Player` exists to mediate between a control
// thread and an audio thread, and a worklet has neither. Each handle owns its backend and DSP.
//
// `docs/PLAN_WEB.md` §13 S1.

#include "engine.h"
#include "stereo_mix.h"
#include "loudness_analysis.h"

#include <emscripten/emscripten.h>

#include <cstring>
#include <memory>
#include <string>
#include <vector>

namespace {

/**
 * One open file.
 *
 * The error is kept beside the backend because the C API has nowhere else to put it: a caller gets
 * a null handle and then asks what happened, which is the same shape `nativeOpen` uses on Android
 * through a one-element array (`docs/BACKLOG.md` A26 dislikes that one; this one is a struct).
 */
struct Handle {
    std::unique_ptr<protracktor::Backend> backend;
    std::string describe;
    protracktor::StereoMixer stereoMixer;
    int stereoWidth = 100;
    protracktor::VolumeNormalizer normalizer;
    protracktor::LoudnessProfile normalizationProfile;
    int normalizationWidth = 100;
    int processedWidth = 100;
    bool normalizationEnabled = false;
    bool normalizationDirty = false;
    bool continuousMeasurement = true;
    bool finished = false;
};

std::string lastError;

/**
 * Nothing thrown here may reach JavaScript (`docs/STATUS.md` C42).
 *
 * An exception crossing this boundary is not an error the page can report: Emscripten turns it into
 * `uncaught exception` out of `___cxa_throw`, the worklet is left mid-message, and every tune after
 * it fails too until the tab is reloaded.
 *
 * `openBackend` catches what *choosing* a backend throws; this covers everything after -- describe,
 * render, seek, length.
 */
void rememberFailure(const char *what) {
    lastError = (what && *what) ? what : "the decoder failed without saying why";
}

template <class T, class Work>
T guarded(Work &&work, T fallback) {
    try {
        return work();
    } catch (const std::exception &e) {
        rememberFailure(e.what());
        return fallback;
    } catch (...) {
        rememberFailure(nullptr);
        return fallback;
    }
}

template <class Work>
void guardedVoid(Work &&work) {
    try {
        work();
    } catch (const std::exception &e) {
        rememberFailure(e.what());
    } catch (...) {
        rememberFailure(nullptr);
    }
}

int mixWidth(int percent) { return percent == 0 || percent == 50 ? percent : 100; }

std::unique_ptr<protracktor::Backend> backendFromBytes(const unsigned char *bytes, int length,
                                                     const char *name) {
    if (!bytes || length <= 0) { lastError = "the file contains no bytes"; return nullptr; }
    std::vector<char> copy(bytes, bytes + length);
    std::string error;
    auto backend = protracktor::openBackend(std::move(copy), name ? name : "", error);
    if (!backend) lastError = error.empty() ? "no decoder claimed the file" : error;
    return backend;
}

}  // namespace

extern "C" {

/** Opens `bytes`, using `name` where a format is told apart by its extension. Null on failure. */
EMSCRIPTEN_KEEPALIVE
Handle *pt_open(const unsigned char *bytes, int length, const char *name) {
    lastError.clear();
    return guarded<Handle *>([&]() -> Handle * {
        auto backend = backendFromBytes(bytes, length, name);
        if (!backend) return nullptr;
        auto handle = std::make_unique<Handle>();
        handle->backend = std::move(backend);
        // **Inside the guard**, because this is the call that ran after the file was already open,
        // and a backend that opened a file it cannot fully read throws here rather than above.
        handle->describe = protracktor::describeOf(*handle->backend);
        return handle.release();
    }, nullptr);
}

EMSCRIPTEN_KEEPALIVE const char *pt_last_error() { return lastError.c_str(); }

EMSCRIPTEN_KEEPALIVE void pt_close(Handle *handle) { guardedVoid([&] { delete handle; }); }

/**
 * Renders interleaved stereo floats and returns the frames produced.
 *
 * Fewer than asked means the tune ended, which is the same contract `Backend::render` has and the
 * same one `onAudioReady` reads on Android. Interleaved rather than planar because that is what the
 * backends produce; a worklet's `process()` wants planar and de-interleaves on the JavaScript side,
 * where the copy is one loop and costs nothing measurable against decoding.
 */
EMSCRIPTEN_KEEPALIVE
int pt_render(Handle *handle, int sampleRate, int frames, float *out) {
    if (!handle || sampleRate <= 0 || frames <= 0 || !out) return 0;
    // Silence rather than an exception: fewer frames than asked means "the tune ended", which the
    // worklet already knows how to answer, and a decoder that throws mid-tune ends it here.
    return guarded<int>([&] {
        const auto rendered = handle->backend->render(sampleRate, static_cast<std::size_t>(frames), out);
        const bool mixChanged = handle->processedWidth != handle->stereoWidth;
        if (mixChanged) {
            if (handle->normalizer.hasMeasurement()) handle->continuousMeasurement = false;
            handle->normalizer.resetMeasurement();
            handle->processedWidth = handle->stereoWidth;
        }
        if (mixChanged || handle->normalizationDirty) {
            const bool prepared = handle->normalizationProfile.usable() &&
                handle->normalizationWidth == handle->stereoWidth;
            handle->normalizer.configureGain(handle->normalizationEnabled,
                protracktor::VolumeNormalizer::gainFor(prepared ? handle->normalizationProfile :
                    protracktor::LoudnessProfile{}), prepared);
            if (!handle->normalizationEnabled) handle->continuousMeasurement = false;
            handle->normalizationDirty = false;
        }
        handle->stereoMixer.process(out, rendered, handle->stereoWidth, sampleRate);
        handle->normalizer.process(out, rendered, sampleRate);
        handle->finished = rendered < static_cast<std::size_t>(frames);
        return static_cast<int>(rendered);
    }, 0);
}

/** Per-handle playback width; a fresh export/description handle retains Original. */
EMSCRIPTEN_KEEPALIVE
void pt_set_stereo_width(Handle *handle, int percent) {
    if (handle) handle->stereoWidth = mixWidth(percent);
}

/** Controls carry their profile width; a stale profile cannot configure another mix. */
EMSCRIPTEN_KEEPALIVE
void pt_set_normalization(Handle *handle, int enabled, double seconds, double lufs,
                          double peak, int complete, int width) {
    if (!handle) return;
    handle->normalizationEnabled = enabled != 0;
    handle->normalizationProfile = {seconds, lufs, peak, complete == 1};
    handle->normalizationWidth = mixWidth(width);
    handle->normalizationDirty = true;
}

/** Numeric snapshots avoid allocating strings or touching another decoder in the callback. */
EMSCRIPTEN_KEEPALIVE
int pt_loudness_profile(Handle *handle, double *out) {
    if (!handle || !out) return 0;
    return guarded<int>([&] {
        if (handle->normalizationDirty || handle->processedWidth != handle->stereoWidth) return 0;
        const auto profile = handle->normalizer.profile(handle->continuousMeasurement && handle->finished);
        out[0] = profile.seconds; out[1] = profile.lufs; out[2] = profile.peak;
        out[3] = profile.complete ? 1.0 : 0.0;
        return 1;
    }, 0);
}

/** Worker/control only: its own backend and the exact native prefix budget; never startedPlaying. */
EMSCRIPTEN_KEEPALIVE
int pt_measure_loudness(const unsigned char *bytes, int length, const char *name,
                        int subsong, int width, double *out) {
    if (!out) return 0;
    lastError.clear();
    return guarded<int>([&] {
        auto backend = backendFromBytes(bytes, length, name);
        if (!backend) return 0;
        const int selected = subsong < 0 ? backend->currentSubsong() : subsong;
        const auto profile = protracktor::analyseLoudness(*backend, selected, mixWidth(width));
        out[0] = profile.seconds; out[1] = profile.lufs; out[2] = profile.peak;
        out[3] = profile.complete ? 1.0 : 0.0; out[4] = selected;
        return 1;
    }, 0);
}

/**
 * The rate this decoder wants to be asked for, or 0 for "whatever you like".
 *
 * **Not advice.** Six of the backends emulate a machine with a fixed clock and produce 44,100
 * samples a second whatever they are asked for; handing those to a 48 kHz output as though they
 * were 48 kHz plays everything 8.8% fast and about a semitone and a half sharp. Android has always
 * asked this and told Oboe (`player_oboe.cpp`), which resamples; a browser has no resampler in that
 * path, so the page must open its AudioContext at this rate instead.
 */
EMSCRIPTEN_KEEPALIVE
int pt_preferred_rate(Handle *h) {
    return h ? guarded<int>([&] { return h->backend->preferredSampleRate(); }, 0) : 0;
}

EMSCRIPTEN_KEEPALIVE int pt_can_seek(Handle *h) {
    return h ? guarded<int>([&] { return h->backend->canSeek() ? 1 : 0; }, 0) : 0;
}
EMSCRIPTEN_KEEPALIVE void pt_seek(Handle *h, double seconds) {
    if (h) guardedVoid([&] {
        if (!h->backend->canSeek()) return;
        h->backend->seek(seconds); h->continuousMeasurement = false; h->finished = false;
    });
}
EMSCRIPTEN_KEEPALIVE void pt_rewind(Handle *h) {
    if (h) guardedVoid([&] { h->backend->rewind(); h->continuousMeasurement = false; h->finished = false; });
}
EMSCRIPTEN_KEEPALIVE double pt_position(Handle *h) {
    return h ? guarded<double>([&] { return h->backend->positionSeconds(); }, 0.0) : 0.0;
}
EMSCRIPTEN_KEEPALIVE double pt_duration(Handle *h) {
    return h ? guarded<double>([&] { return h->backend->durationSeconds(); }, 0.0) : 0.0;
}
EMSCRIPTEN_KEEPALIVE const char *pt_describe(Handle *h) { return h ? h->describe.c_str() : ""; }
EMSCRIPTEN_KEEPALIVE int pt_subsong_count(Handle *h) {
    return h ? guarded<int>([&] { return h->backend->subsongCount(); }, 1) : 0;
}
EMSCRIPTEN_KEEPALIVE int pt_current_subsong(Handle *h) {
    return h ? guarded<int>([&] { return h->backend->currentSubsong(); }, 0) : 0;
}

EMSCRIPTEN_KEEPALIVE
int pt_select_subsong(Handle *h, int index) {
    if (!h) return 0;
    return guarded<int>([&] {
        if (!h->backend->selectSubsong(index)) return 0;
        h->normalizer.reset(); h->continuousMeasurement = true; h->finished = false;
        h->normalizationProfile = {}; h->normalizationWidth = h->stereoWidth;
        h->normalizationDirty = true;
        h->describe = protracktor::describeOf(*h->backend);
        return 1;
    }, 0);
}

/** Which decoders this build carries. The same string the Android build reports. */
EMSCRIPTEN_KEEPALIVE
const char *pt_backends() {
    static std::string cached;
    cached = guarded<std::string>([] { return protracktor::backendsFingerprint(); }, std::string());
    return cached.c_str();
}

}  // extern "C"
