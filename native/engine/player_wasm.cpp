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
// thread and an audio thread, and a worklet has neither. A handle is a backend.
//
// `docs/PLAN_WEB.md` §13 S1.

#include "engine.h"

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

}  // namespace

extern "C" {

/** Opens `bytes`, using `name` where a format is told apart by its extension. Null on failure. */
EMSCRIPTEN_KEEPALIVE
Handle *pt_open(const unsigned char *bytes, int length, const char *name) {
    lastError.clear();
    return guarded<Handle *>([&]() -> Handle * {
        std::vector<char> copy(bytes, bytes + length);
        std::string error;
        auto backend = protracktor::openBackend(std::move(copy), name ? name : "", error);
        if (!backend) {
            lastError = error.empty() ? "no decoder claimed the file" : error;
            return nullptr;
        }
        auto handle = std::make_unique<Handle>(Handle{std::move(backend), ""});
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
    if (!handle) return 0;
    // Silence rather than an exception: fewer frames than asked means "the tune ended", which the
    // worklet already knows how to answer, and a decoder that throws mid-tune ends it here.
    return guarded<int>([&] {
        return static_cast<int>(handle->backend->render(sampleRate, static_cast<std::size_t>(frames), out));
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
    if (h) guardedVoid([&] { h->backend->seek(seconds); });
}
EMSCRIPTEN_KEEPALIVE void pt_rewind(Handle *h) { if (h) guardedVoid([&] { h->backend->rewind(); }); }
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
