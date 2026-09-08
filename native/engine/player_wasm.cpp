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

}  // namespace

extern "C" {

/** Opens `bytes`, using `name` where a format is told apart by its extension. Null on failure. */
EMSCRIPTEN_KEEPALIVE
Handle *pt_open(const unsigned char *bytes, int length, const char *name) {
    lastError.clear();
    std::vector<char> copy(bytes, bytes + length);
    std::string error;
    auto backend = protracktor::openBackend(std::move(copy), name ? name : "", error);
    if (!backend) {
        lastError = error.empty() ? "no decoder claimed the file" : error;
        return nullptr;
    }
    auto *handle = new Handle{std::move(backend), ""};
    handle->describe = handle->backend->describe();
    return handle;
}

EMSCRIPTEN_KEEPALIVE const char *pt_last_error() { return lastError.c_str(); }

EMSCRIPTEN_KEEPALIVE void pt_close(Handle *handle) { delete handle; }

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
    return static_cast<int>(handle->backend->render(sampleRate, static_cast<std::size_t>(frames), out));
}

EMSCRIPTEN_KEEPALIVE int pt_can_seek(Handle *h) { return h && h->backend->canSeek() ? 1 : 0; }
EMSCRIPTEN_KEEPALIVE void pt_seek(Handle *h, double seconds) { if (h) h->backend->seek(seconds); }
EMSCRIPTEN_KEEPALIVE void pt_rewind(Handle *h) { if (h) h->backend->rewind(); }
EMSCRIPTEN_KEEPALIVE double pt_position(Handle *h) { return h ? h->backend->positionSeconds() : 0.0; }
EMSCRIPTEN_KEEPALIVE double pt_duration(Handle *h) { return h ? h->backend->durationSeconds() : 0.0; }
EMSCRIPTEN_KEEPALIVE const char *pt_describe(Handle *h) { return h ? h->describe.c_str() : ""; }
EMSCRIPTEN_KEEPALIVE int pt_subsong_count(Handle *h) { return h ? h->backend->subsongCount() : 0; }
EMSCRIPTEN_KEEPALIVE int pt_current_subsong(Handle *h) { return h ? h->backend->currentSubsong() : 0; }

EMSCRIPTEN_KEEPALIVE
int pt_select_subsong(Handle *h, int index) {
    if (!h || !h->backend->selectSubsong(index)) return 0;
    h->describe = h->backend->describe();
    return 1;
}

/** Which decoders this build carries. The same string the Android build reports. */
EMSCRIPTEN_KEEPALIVE
const char *pt_backends() {
    static std::string cached;
    cached = protracktor::backendsFingerprint();
    return cached.c_str();
}

}  // extern "C"
