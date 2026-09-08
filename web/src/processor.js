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
    EngineModule({ wasmBinary: options.processorOptions.wasmBinary }).then((engine) => {
      this.engine = engine;
      this.port.postMessage({ type: 'ready', backends: engine.UTF8ToString(engine._pt_backends()) });
    });

    this.port.onmessage = (event) => this.onMessage(event.data);
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
        const nameBytes = new TextEncoder().encode(message.name + '\0');
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
          canSeek: e._pt_can_seek(this.handle) === 1,
        });
        break;
      }
      case 'play': this.playing = true; break;
      case 'pause': this.playing = false; break;
      case 'seek': if (this.handle) e._pt_seek(this.handle, message.seconds); break;
      case 'subsong': if (this.handle) { e._pt_select_subsong(this.handle, message.index); this.ended = false; } break;
      case 'close': this.close(); break;
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
