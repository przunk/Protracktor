// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The audio thread. **The decoder lives in here**, which is the whole architecture in one sentence.
//
// `docs/ARCHITECTURE.md` §4 settled this for Android: rendering and output both live in native
// code because pushing PCM across a boundary "crosses it every few milliseconds on the one thread
// that must never be late". A browser poses the same question and takes the same answer -- the wasm
// module is instantiated *inside* the worklet, and the main thread sends it control and file bytes
// and nothing else.
//
// It needs no `SharedArrayBuffer` and therefore no cross-origin isolation: nothing is shared,
// because nothing is on the other side (`docs/PLAN_WEB.md` §5).

import EngineModule from '../vendor/engine.mjs';

/**
 * The globals an `AudioWorkletGlobalScope` does not have, and the engine's runtime expects.
 *
 * **This scope is deliberately minimal** -- it exists to run one function on the audio thread, so
 * it has `sampleRate`, `currentTime` and `currentFrame` and almost nothing else. No `fetch`, no
 * `URL`, no `XMLHttpRequest`, no `TextEncoder`, no `performance`, no `crypto`. Emscripten's runtime
 * reaches for two of those unconditionally, so they are provided here rather than rebuilt away:
 * the alternative is patching generated glue after every build.
 *
 * Read out of the generated file rather than guessed at: `performance` and `setTimeout` are used
 * unguarded, `TextDecoder`, `window` and `navigator` are guarded by `typeof`, and `setTimeout` is
 * only reached through `Module.setStatus`, which nothing here sets.
 */
if (typeof performance === 'undefined') {
  const origin = Date.now();
  // `_emscripten_get_now`, which is what `std::chrono::steady_clock` becomes -- and `GmeBackend`
  // budgets its search for an audible track against a 300 ms deadline, so this has to advance
  // honestly rather than return a constant.
  globalThis.performance = { now: () => Date.now() - origin };
}
if (typeof crypto === 'undefined') {
  // `getentropy`. Nothing in a music decoder needs unpredictable bytes, and a worklet has no
  // `crypto` to give them; this is here so a lazy call cannot abort the runtime.
  globalThis.crypto = {
    getRandomValues(view) {
      for (let i = 0; i < view.length; i++) view[i] = (Math.random() * 256) | 0;
      return view;
    },
  };
}

/**
 * UTF-8 with a terminating zero, by hand.
 *
 * `TextEncoder` does not exist in an `AudioWorkletGlobalScope`. Filenames here are mostly ASCII and
 * occasionally not -- Modland has plenty of accented author names -- so this is a real encoder
 * rather than a `charCodeAt` loop that would corrupt them.
 */
function utf8(text) {
  const bytes = [];
  for (const character of text) {
    let code = character.codePointAt(0);
    if (code < 0x80) bytes.push(code);
    else if (code < 0x800) bytes.push(0xc0 | (code >> 6), 0x80 | (code & 63));
    else if (code < 0x10000) bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 63), 0x80 | (code & 63));
    else bytes.push(0xf0 | (code >> 18), 0x80 | ((code >> 12) & 63), 0x80 | ((code >> 6) & 63), 0x80 | (code & 63));
  }
  bytes.push(0);
  return Uint8Array.from(bytes);
}

class ProtracktorProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    this.engine = null;
    this.handle = 0;
    this.scratch = 0;
    this.scratchFrames = 0;
    this.ended = false;
    this.playing = false;
    this.reported = -1;

    // **The wasm binary arrives as bytes rather than being fetched here.** `fetch` does not exist
    // in an AudioWorkletGlobalScope, so a build that downloads its own `.wasm` cannot start; the
    // alternative -- base64 inside the JavaScript -- costs a third more bytes and loses the
    // browser's ability to cache the binary separately.
    // **Messages that arrive before the engine does are kept, not dropped.** Compiling 2.6 MB of
    // wasm takes a moment, and the first thing the page does is fetch a track and post it -- which
    // beat the engine every time, was thrown away by the `if (!e) return` below, and left the page
    // saying "fetching…" for ever with nothing wrong anywhere the eye could reach.
    this.pending = [];

    // `locateFile` is not an optimisation here, it is what stops the module ever touching `URL`.
    // Emscripten's `findWasmBinary()` runs whether or not a binary was handed in, and its fallback
    // is `new URL("engine.wasm", import.meta.url)` -- and **an AudioWorkletGlobalScope has no
    // `URL`**. It is a deliberately minimal scope: no `fetch`, no `URL`, no `XMLHttpRequest`, and
    // no `TextEncoder` either, which is why the name below is encoded by hand. Setting `locateFile`
    // takes the other branch and the constructor is never reached.
    EngineModule({
      wasmBinary: options.processorOptions.wasmBinary,
      locateFile: (path) => path,
    }).then((engine) => {
      this.engine = engine;
      this.port.postMessage({ type: 'ready', backends: engine.UTF8ToString(engine._pt_backends()) });
      const waiting = this.pending;
      this.pending = null;
      for (const message of waiting) this.onMessage(message);
    }).catch((error) => {
      this.port.postMessage({ type: 'failed', reason: `the engine did not load: ${error}` });
    });

    this.port.onmessage = (event) => {
      if (this.pending) this.pending.push(event.data);
      else this.onMessage(event.data);
    };
  }

  onMessage(message) {
    const e = this.engine;
    if (!e) return;
    switch (message.type) {
      case 'open': {
        this.close();
        const bytes = new Uint8Array(message.bytes);
        const buf = e._malloc(bytes.length);
        e.HEAPU8.set(bytes, buf);
        const nameBytes = utf8(message.name);
        const namePtr = e._malloc(nameBytes.length);
        e.HEAPU8.set(nameBytes, namePtr);
        this.handle = e._pt_open(buf, bytes.length, namePtr);
        e._free(buf);
        e._free(namePtr);
        if (!this.handle) {
          this.port.postMessage({ type: 'failed', reason: e.UTF8ToString(e._pt_last_error()) });
          return;
        }
        this.ended = false;
        this.playing = true;
        this.port.postMessage({
          type: 'opened',
          describe: e.UTF8ToString(e._pt_describe(this.handle)),
          duration: e._pt_duration(this.handle),
          subsongs: e._pt_subsong_count(this.handle),
          // **Not always zero.** A HES or KSS file often has nothing at track 0, so `GmeBackend`
          // opens at the first track with sound in it and says so here. Assuming zero would leave
          // the chips pointing at a tune that is not the one playing.
          current: e._pt_current_subsong(this.handle),
          preferredRate: e._pt_preferred_rate(this.handle),
          rate: sampleRate,
          canSeek: e._pt_can_seek(this.handle) === 1,
        });
        break;
      }
      case 'play': this.playing = true; break;
      case 'pause': this.playing = false; break;
      case 'seek': if (this.handle) e._pt_seek(this.handle, message.seconds); break;
      case 'subsong':
        if (this.handle) {
          e._pt_select_subsong(this.handle, message.index);
          this.ended = false;
          this.playing = true;
          // **Answered, because a subsong is a different tune.** Its length is its own -- a GBS
          // gives each track one -- and so is its title. Without this the page kept showing the
          // first tune's duration against the third one's audio, which is the same defect
          // `selectSubsong` fixed on the phone.
          this.port.postMessage({
            type: 'subsong',
            index: e._pt_current_subsong(this.handle),
            duration: e._pt_duration(this.handle),
            describe: e.UTF8ToString(e._pt_describe(this.handle)),
          });
        }
        break;
      // Play, on a track that has already ended. `pt_rewind` is what the phone's transport does
      // too; re-opening the file would work and would spend a fetch on bytes we are holding.
      case 'rewind':
        if (this.handle) {
          e._pt_rewind(this.handle);
          this.ended = false;
          this.playing = true;
          this.reported = -1;
          this.port.postMessage({ type: 'position', seconds: 0 });
        }
        break;
      case 'close': this.close(); break;
      /**
       * Describe a file **without playing it**, for a row that is not the current one.
       *
       * A second handle, opened and closed inside this message: `this.handle` is what the audio
       * callback reads and must not be touched. The cost is opening a decoder on the audio thread,
       * which is real -- the median Modland module is 20 KB and opens in well under a buffer, but a
       * 70 MB one would not, and that is the case to watch if this ever glitches.
       */
      case 'describe': {
        const bytes = new Uint8Array(message.bytes);
        const buf = e._malloc(bytes.length);
        e.HEAPU8.set(bytes, buf);
        const nameBytes = utf8(message.name || '');
        const namePtr = e._malloc(nameBytes.length);
        e.HEAPU8.set(nameBytes, namePtr);
        const handle = e._pt_open(buf, bytes.length, namePtr);
        this.port.postMessage({
          type: 'described',
          id: message.id,
          ok: !!handle,
          describe: handle ? e.UTF8ToString(e._pt_describe(handle)) : '',
          duration: handle ? e._pt_duration(handle) : 0,
          subsongs: handle ? e._pt_subsong_count(handle) : 0,
          reason: handle ? '' : e.UTF8ToString(e._pt_last_error()),
        });
        if (handle) e._pt_close(handle);
        e._free(buf);
        e._free(namePtr);
        break;
      }
    }
  }

  close() {
    if (this.handle && this.engine) this.engine._pt_close(this.handle);
    this.handle = 0;
    this.playing = false;
  }

  process(_inputs, outputs) {
    const out = outputs[0];
    const frames = out[0].length;
    const e = this.engine;

    if (!e || !this.handle || !this.playing || this.ended) return true;

    if (this.scratchFrames < frames) {
      if (this.scratch) e._free(this.scratch);
      this.scratch = e._malloc(frames * 2 * 4);
      this.scratchFrames = frames;
    }

    const rendered = e._pt_render(this.handle, sampleRate, frames, this.scratch);
    // Interleaved from the backends, planar for the worklet. One loop, and it is not where the time
    // goes: the slowest decoder in the set runs at 23x realtime and this is a copy.
    const view = new Float32Array(e.HEAPF32.buffer, this.scratch, rendered * 2);
    const left = out[0];
    const right = out.length > 1 ? out[1] : out[0];
    for (let i = 0; i < rendered; i++) {
      left[i] = view[i * 2];
      right[i] = view[i * 2 + 1];
    }
    for (let i = rendered; i < frames; i++) { left[i] = 0; right[i] = 0; }

    if (rendered < frames) {
      this.ended = true;
      this.port.postMessage({ type: 'ended' });
      return true;
    }

    // Position is posted about five times a second rather than every 128 frames: 375 messages a
    // second would be a message queue, not a progress bar.
    const position = e._pt_position(this.handle);
    if (Math.abs(position - this.reported) > 0.2) {
      this.reported = position;
      this.port.postMessage({ type: 'position', seconds: position });
    }
    return true;
  }
}

registerProcessor('protracktor', ProtracktorProcessor);
