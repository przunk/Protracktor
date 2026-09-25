// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Share as audio, the page's side (`docs/BACKLOG.md` A62): a tune rendered by an engine of its own
// and encoded to AAC, off the page's thread and away from the worklet, so what plays goes on
// playing. A module worker: the page starts it on the first share and keeps it.
//
// `exportTune` takes the engine and the encoder as arguments, so `check-engine.mjs` can run the
// whole way on Node with a stand-in encoder -- Node has no WebCodecs. What only a browser can
// check is `webCodecsEncoder`, and it is the part that stays unchecked here.

import EngineModule from '../vendor/engine.mjs';
import { fadeGain, shareAudioPlan, SHARE_AUDIO_FADE_SECONDS } from './rules.js';
import { muxM4a } from './m4a.js';

/** What a tune is rendered at when its decoder has no preference. */
export const EXPORT_RATE = 44100;
const BLOCK = 4096;

/** The encoder the page uses, and the question whether this browser has one. */
export const AAC_CONFIG = { codec: 'mp4a.40.2', numberOfChannels: 2, bitrate: 128000 };

/**
 * Renders [request] and hands it to an encoder made by [makeEncoder]`(sampleRate)`, which answers
 * `encode(interleaved, frames)` and `finish() -> { frames, config }`. Returns the `.m4a` bytes, and
 * what was decided, for the checks.
 */
export async function exportTune(engine, makeEncoder, { bytes, name, subsong = null, knownLengths = [], limitMinutes }) {
  const e = engine;
  const data = new Uint8Array(bytes);
  const buf = e._malloc(data.length);
  e.HEAPU8.set(data, buf);
  const nameBytes = new TextEncoder().encode(`${name ?? ''}\0`);
  const namePtr = e._malloc(nameBytes.length);
  e.HEAPU8.set(nameBytes, namePtr);
  const handle = e._pt_open(buf, data.length, namePtr);
  e._free(buf);
  e._free(namePtr);
  if (!handle) throw new Error(e.UTF8ToString(e._pt_last_error()) || 'no decoder took the file');

  let scratch = 0;
  try {
    if (subsong != null && subsong !== e._pt_current_subsong(handle)) e._pt_select_subsong(handle, subsong);
    const current = e._pt_current_subsong(handle);
    // The same order the player asks in: the decoder, then the database.
    const own = e._pt_duration(handle);
    const known = own > 0 ? own : (knownLengths[current] > 0 ? knownLengths[current] : 0);
    const plan = shareAudioPlan(known, limitMinutes);

    // A decoder with a fixed clock gives its own rate whatever it is asked for; asked for another it
    // would come out sharp, as it did on the page once (`app.js`, the 44.1 kHz context).
    const preferred = e._pt_preferred_rate(handle);
    const rate = preferred >= 8000 && preferred <= 96000 ? preferred : EXPORT_RATE;
    const total = Math.round(plan.seconds * rate);
    const fadeFrames = plan.fade ? Math.round(SHARE_AUDIO_FADE_SECONDS * rate) : 0;

    const encoder = await makeEncoder(rate);
    scratch = e._malloc(BLOCK * 2 * 4);
    let done = 0;
    while (done < total) {
      const want = Math.min(BLOCK, total - done);
      const got = e._pt_render(handle, rate, want, scratch);
      if (got <= 0) break;
      const block = new Float32Array(e.HEAPF32.buffer, scratch, got * 2).slice();
      if (fadeFrames > 0 && done + got > total - fadeFrames) {
        for (let i = 0; i < got; i++) {
          const gain = fadeGain(done + i, total, fadeFrames);
          block[2 * i] *= gain;
          block[2 * i + 1] *= gain;
        }
      }
      await encoder.encode(block, got);
      done += got;
      if (got < want) break;
    }
    if (done === 0) throw new Error('the decoder gave no sound');
    const { frames, config } = await encoder.finish();
    return { bytes: muxM4a({ sampleRate: rate, config, frames }), rate, rendered: done, plan, subsong: current };
  } finally {
    if (scratch) e._free(scratch);
    e._pt_close(handle);
  }
}

/** The browser's AAC encoder, wrapped to what `exportTune` asks of one. */
export function webCodecsEncoder(sampleRate) {
  const frames = [];
  let config = null;
  let failure = null;
  let timestamp = 0;
  const encoder = new AudioEncoder({
    output: (chunk, meta) => {
      const description = meta?.decoderConfig?.description;
      if (description && !config) {
        config = description instanceof ArrayBuffer
          ? new Uint8Array(description.slice(0))
          : new Uint8Array(description.buffer.slice(description.byteOffset, description.byteOffset + description.byteLength));
      }
      const frame = new Uint8Array(chunk.byteLength);
      chunk.copyTo(frame);
      frames.push(frame);
    },
    error: (error) => { failure = error; },
  });
  encoder.configure({ ...AAC_CONFIG, sampleRate });
  return {
    async encode(interleaved, count) {
      if (failure) throw failure;
      const audio = new AudioData({
        format: 'f32', sampleRate, numberOfFrames: count, numberOfChannels: 2,
        timestamp: Math.round((timestamp * 1e6) / sampleRate), data: interleaved,
      });
      encoder.encode(audio);
      audio.close();
      timestamp += count;
      // Rendering is far faster than encoding; without a pause the whole tune would queue up.
      while (encoder.encodeQueueSize > 8) await new Promise((resolve) => setTimeout(resolve, 0));
    },
    async finish() {
      await encoder.flush();
      encoder.close();
      if (failure) throw failure;
      if (!config) throw new Error('the encoder gave no AAC configuration');
      return { frames, config };
    },
  };
}

// The worker itself: only when this file runs as one, never when a check imports it.
if (typeof WorkerGlobalScope !== 'undefined' && globalThis instanceof WorkerGlobalScope) {
  let engine = null;
  globalThis.onmessage = async ({ data }) => {
    try {
      engine ??= await EngineModule();
      const { bytes } = await exportTune(engine, webCodecsEncoder, data);
      globalThis.postMessage({ ok: true, bytes }, [bytes.buffer]);
    } catch (error) {
      globalThis.postMessage({ ok: false, reason: String(error?.message ?? error) });
    }
  };
}
