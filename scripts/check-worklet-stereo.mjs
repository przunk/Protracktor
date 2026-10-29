// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { stereoFixture, openFixture, renderPcm } from './fixtures/stereo.mjs';

const EngineModule = (await import(path.resolve('web/vendor/engine.mjs'))).default;
const source = fs.readFileSync('web/src/processor.js', 'utf8').replace("import EngineModule from '../vendor/engine.mjs';", '');
let count = 0;
async function test(name, run) { await run(); count++; console.log(`✓ ${name}`); }
async function processor({ width, delayed = false } = {}) {
  let Processor, release;
  const replies = [], ready = new Promise((resolve) => { release = resolve; });
  const sandbox = {
    EngineModule: async (options) => { const engine = await EngineModule(options); if (delayed) await ready; return engine; },
    AudioWorkletProcessor: class { constructor() { this.port = { postMessage: (message) => replies.push(structuredClone(message)) }; } },
    registerProcessor: (_name, type) => { Processor = type; },
    sampleRate: 44100, performance, crypto: globalThis.crypto,
  };
  vm.runInNewContext(source, sandbox);
  const value = new Processor({ processorOptions: { wasmBinary: fs.readFileSync('web/vendor/engine.wasm'), stereoWidth: width } });
  return { value, replies, release, send: (message) => value.port.onmessage({ data: message }),
    async opened(id) {
      for (let n = 0; n < 1000; n++) {
        if (replies.some((message) => message.type === 'opened' && message.requestId === id)) return;
        if (replies.some((message) => message.type === 'failed')) throw new Error(JSON.stringify(replies));
        await new Promise((resolve) => setTimeout(resolve, 10));
      }
      throw new Error('The actual worklet did not open the stereo fixture');
    } };
}
function callback(processor, frames = 2048) {
  const output = [new Float32Array(frames), new Float32Array(frames)];
  assert.equal(processor.process([], [output]), true);
  const pcm = new Float32Array(frames * 2);
  for (let n = 0; n < frames; n++) { pcm[n * 2] = output[0][n]; pcm[n * 2 + 1] = output[1][n]; }
  return pcm;
}
function matrix(raw, pcm, width, start = 0) {
  assert.equal(pcm.length, raw.length); assert(raw.some((value) => Math.abs(value) > .001));
  const cross = (1 - width / 100) / 2, keep = 1 - cross, f = Math.fround;
  for (let n = start * 2; n < pcm.length; n += 2) {
    const left = width === 100 ? raw[n] : f(f(keep * raw[n]) + f(cross * raw[n + 1]));
    const right = width === 100 ? raw[n + 1] : f(f(keep * raw[n + 1]) + f(cross * raw[n]));
    assert(Math.abs(pcm[n] - left) < 1e-7 && Math.abs(pcm[n + 1] - right) < 1e-7, `wrong matrix at frame${n / 2}`);
  }
}
const bytes = stereoFixture('left');

for (const width of [100, 50, 0]) await test(`the actual worklet renders its initial ${width}% selection before any default-width burst`, async () => {
  const p = await processor({ width });
  p.send({ type: 'open', requestId: 71, name: 'stereo.mod', bytes }); await p.opened(71);
  const h = openFixture(p.value.engine, bytes);
  try { matrix(renderPcm(p.value.engine, h, 44100, 2048), callback(p.value), width); }
  finally { p.value.engine._pt_close(h); p.send({ type: 'close' }); }
});

await test('compilation-time mode changes are drained in order before first PCM', async () => {
  const p = await processor({ delayed: true });
  p.send({ type: 'open', requestId: 72, name: 'stereo.mod', bytes });
  p.send({ type: 'stereo', percent: 50 }); p.send({ type: 'stereo', percent: 0 });
  p.release(); await p.opened(72);
  const h = openFixture(p.value.engine, bytes);
  try { matrix(renderPcm(p.value.engine, h, 44100, 2048), callback(p.value), 0); }
  finally { p.value.engine._pt_close(h); p.send({ type: 'close' }); }
});

await test('a paused live change cannot advance the real decoder and resumes through the selected mix', async () => {
  const p = await processor({ width: 0 });
  p.send({ type: 'open', requestId: 73, name: 'stereo.mod', bytes }); await p.opened(73);
  const e = p.value.engine, h = openFixture(e, bytes);
  try {
    matrix(renderPcm(e, h, 44100, 2048), callback(p.value), 0);
    p.send({ type: 'pause' }); const before = e._pt_position(p.value.handle);
    p.send({ type: 'stereo', percent: 50 });
    assert(callback(p.value).every((sample) => sample === 0));
    assert.equal(p.value.playing, false); assert.equal(e._pt_position(p.value.handle), before);
    p.send({ type: 'play' });
    matrix(renderPcm(e, h, 44100, 2048), callback(p.value), 50, 512);
    assert.equal(p.value.requestId, 73);
  } finally { e._pt_close(h); p.send({ type: 'close' }); }
});

await test('closing a file retains the global mode for replacement, seek and subsong messages', async () => {
  const p = await processor({ width: 50 });
  p.send({ type: 'open', requestId: 74, name: 'stereo.mod', bytes }); await p.opened(74);
  callback(p.value); p.send({ type: 'close' }); p.send({ type: 'stereo', percent: 0 });
  p.send({ type: 'open', requestId: 75, name: 'stereo.mod', bytes }); await p.opened(75);
  const e = p.value.engine, h = openFixture(e, bytes);
  try {
    matrix(renderPcm(e, h, 44100, 2048), callback(p.value), 0);
    p.send({ type: 'subsong', index: 0 }); p.send({ type: 'seek', seconds: .1 });
    e._pt_select_subsong(h, 0); e._pt_seek(h, .1);
    matrix(renderPcm(e, h, 44100, 2048), callback(p.value), 0);
    p.send({ type: 'rewind' }); e._pt_rewind(h);
    matrix(renderPcm(e, h, 44100, 2048), callback(p.value), 0);
    assert(p.replies.some((reply) => reply.type === 'subsong' && reply.requestId === 75));
  } finally { e._pt_close(h); p.send({ type: 'close' }); }
});

assert(count > 0); console.log(`${count} real-worklet/WASM stereo message and PCM cases passed (no audio device)`);
