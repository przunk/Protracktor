// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import EngineModule from '../web/vendor/engine.mjs';
import { stereoFixture, openFixture, renderPcm } from './fixtures/stereo.mjs';

const bytes = stereoFixture('correlated'), known = { seconds: 20, lufs: -6, peak: .5, complete: true };
const until = async (predicate) => {
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) { if (predicate()) return; await new Promise((done) => setTimeout(done, 5)); }
  throw new Error('The actual worklet did not reach its expected decoder boundary');
};
async function create() {
  let Processor; const replies = [];
  vm.runInNewContext(fs.readFileSync('web/src/processor.js', 'utf8').replace("import EngineModule from '../vendor/engine.mjs';", ''), {
    EngineModule, performance, sampleRate: 44100,
    AudioWorkletProcessor: class { constructor() { this.port = { postMessage: (row) => replies.push(structuredClone(row)) }; } },
    registerProcessor: (_name, type) => { Processor = type; },
  });
  const value = new Processor({ processorOptions: { wasmBinary: fs.readFileSync('web/vendor/engine.wasm'), normalizationEnabled: true, stereoWidth: 100 } });
  const send = (data) => value.port.onmessage({ data });
  send({ type: 'open', requestId: 1, bytes, name: 'loudness.mod' });
  await until(() => replies.some((row) => row.type === 'decoded'));
  const prepared = (profile = known, overrides = {}) => send({ type: 'prepared', requestId: 1, current: 0, width: 100, profile, ...overrides });
  const callback = (frames = 2048) => {
    const output = [new Float32Array(frames), new Float32Array(frames)];
    assert.equal(value.process([], [output]), true);
    const pcm = new Float32Array(frames * 2);
    for (let n = 0; n < frames; n++) { pcm[n * 2] = output[0][n]; pcm[n * 2 + 1] = output[1][n]; }
    return pcm;
  };
  return { value, replies, send, prepared, callback };
}
const scaled = (actual, raw, gain, first = 0) => {
  assert.equal(actual.length, raw.length); assert(raw.some((n) => Math.abs(n) > .001));
  for (let n = first; n < raw.length; n++) assert(Math.abs(actual[n] - raw[n] * gain) < 1e-7, `unexpected PCM gain at${n}`);
};
let checks = 0;
async function test(name, run) { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); }
await test('the real callback waits for matching request and actual subsong before first PCM', async () => {
  const p = await create(), e = p.value.engine;
  try {
    for (const overrides of [{ requestId: 0 }, { current: 1 }]) {
      p.prepared(known, overrides); assert(p.value.awaitingPreparation);
      assert(p.callback().every((n) => n === 0)); assert.equal(e._pt_position(p.value.handle), 0);
    }
    assert(!p.replies.some((row) => row.type === 'opened'));
    const raw = openFixture(e, bytes);
    try { p.prepared(); scaled(p.callback(), renderPcm(e, raw, 44100, 2048), Math.fround(10 ** (-12 / 20))); }
    finally { e._pt_close(raw); }
  } finally { p.send({ type: 'close' }); }
});
await test('a paused startup cannot be resumed by a late preparation reply', async () => {
  const p = await create(), e = p.value.engine;
  try {
    p.send({ type: 'pause' }); p.prepared();
    assert.equal(p.replies.find((row) => row.type === 'opened').playing, false);
    assert(p.callback().every((n) => n === 0)); assert.equal(e._pt_position(p.value.handle), 0);
    p.send({ type: 'play' }); assert(p.callback().some((n) => Math.abs(n) > .001));
  } finally { p.send({ type: 'close' }); }
});
await test('a stale-width startup profile uses live fallback while the selected file still opens', async () => {
  const p = await create(), e = p.value.engine, raw = openFixture(e, bytes);
  try { p.prepared(known, { width: 50 }); scaled(p.callback(), renderPcm(e, raw, 44100, 2048), .5); }
  finally { e._pt_close(raw); p.send({ type: 'close' }); }
});
await test('late profile packets for another request subsong or width cannot change the real gain', async () => {
  const p = await create(), e = p.value.engine, raw = openFixture(e, bytes);
  try {
    p.prepared();
    for (const overrides of [{ requestId: 0 }, { subsong: 1 }, { width: 50 }]) {
      p.send({ type: 'normalization-profile', requestId: 1, subsong: 0, width: 100,
        profile: { seconds: 20, lufs: -45, peak: .01, complete: true }, ...overrides });
      scaled(p.callback(), renderPcm(e, raw, 44100, 2048), Math.fround(10 ** (-12 / 20)));
    }
  } finally { e._pt_close(raw); p.send({ type: 'close' }); }
});
await test('a valid but late cache packet cannot re-enable normalization after Off', async () => {
  const p = await create(), e = p.value.engine, raw = openFixture(e, bytes);
  try {
    p.prepared(); p.callback(); renderPcm(e, raw, 44100, 2048);
    p.send({ type: 'normalization', enabled: false });
    p.send({ type: 'normalization-profile', requestId: 1, subsong: 0, width: 100,
      profile: { seconds: 20, lufs: -45, peak: .01, complete: true } });
    scaled(p.callback(8192), renderPcm(e, raw, 44100, 8192), 1, 7000);
    assert.equal(p.value.normalizationEnabled, false);
  } finally { e._pt_close(raw); p.send({ type: 'close' }); }
});
await test('a successful worklet subsong transition drops the old trim before its first PCM', async () => {
  const p = await create(), e = p.value.engine, raw = openFixture(e, bytes);
  try {
    p.prepared(); p.callback(); p.send({ type: 'subsong', index: 0 });
    scaled(p.callback(), renderPcm(e, raw, 44100, 2048), .5);
    assert(p.replies.some((row) => row.type === 'normalization-context' && row.subsong === 0 && row.width === 100));
  } finally { e._pt_close(raw); p.send({ type: 'close' }); }
});
await test('closing emits the old profile and old acknowledgement before adopting a new request', async () => {
  const p = await create();
  try {
    p.prepared(); p.callback(); const before = p.replies.length;
    p.send({ type: 'open', requestId: 2, bytes, name: 'next.mod' });
    assert.deepEqual(p.replies.slice(before).map((row) => [row.type, row.requestId]), [['loudness', 1], ['closed', 1], ['decoded', 2]]);
    assert(p.replies[before].values[0] > 0);
  } finally { p.send({ type: 'close' }); }
});
await test('only natural uninterrupted worklet EOF emits a complete profile before its final position', async () => {
  for (const seek of [false, true]) {
    const p = await create();
    try {
      p.prepared(); p.callback(); if (seek) p.send({ type: 'seek', seconds: .2 });
      for (let n = 0; n < 200 && !p.value.ended; n++) p.callback(4096);
      assert(p.value.ended);
      const end = p.replies.findIndex((row) => row.type === 'ended');
      assert.equal(p.replies[end - 1].type, 'loudness'); assert.equal(p.replies[end - 1].reason, 'end');
      assert.equal(p.replies[end - 1].values[3], seek ? 0 : 1);
      const count = p.replies.length; p.callback(); assert.equal(p.replies.length, count);
    } finally { p.send({ type: 'close' }); }
  }
});
assert(checks > 0); console.log(`${checks} real-worklet/native-WASM normalization boundary cases passed`);
