// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { Worker } from 'node:worker_threads';
import { webcrypto } from 'node:crypto';
import { LoudnessAnalyser } from '../web/src/loudness-analyser.js';
import { LoudnessPreparation } from '../web/src/loudness-preparation.js';
import { createLoudnessAnalyser } from '../web/src/loudness-worker.js';
import { nativeLoudnessProfile } from '../web/src/loudness-profiles.js';
import { stereoFixture, openFixture, renderPcm } from './fixtures/stereo.mjs';

let checks = 0;
async function test(name, run) {
  try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
}
// Buffer's backing allocation can have a nonzero offset; preserve exactly the published file.
const wasm = fs.readFileSync('web/vendor/engine.wasm');
const binary = wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength);
let started = 0, terminated = 0;
const createWorker = () => {
  const worker = new Worker(new URL('./fixtures/loudness-worker-node.mjs', import.meta.url)); started++;
  const browser = { postMessage: (message, transfer) => worker.postMessage(message, transfer),
    terminate() { terminated++; void worker.terminate(); } };
  worker.on('message', (data) => browser.onmessage?.({ data }));
  worker.on('error', (error) => browser.onerror?.(error));
  worker.on('messageerror', (error) => browser.onmessageerror?.(error));
  return browser;
};
const bytes = () => {
  const data = stereoFixture('correlated');
  data[1084 + 8 * 16 + 2] |= 0x0d;
  return data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength);
};
const request = () => ({ bytes: bytes(), name: 'worker.mod', subsong: 0, width: 100 });
await test('the production worker handler analyses in a real separate thread and reuses its engine', async () => {
  const analyser = new LoudnessAnalyser({ wasmBinary: binary, createWorker });
  try {
    const input = request(), first = await analyser.analyseOwned(input);
    assert.equal(input.bytes.byteLength, 0); assert(nativeLoudnessProfile(first)); assert.equal(first[4], 0);
    assert.equal(started, 1); assert.equal(binary.byteLength, wasm.length);
    const second = await analyser.analyseOwned({ ...request(), width: 50 });
    assert(nativeLoudnessProfile(second)); assert.equal(started, 1);
    assert.equal(first[3], 1); assert.equal(second[3], 1);
    assert(Math.abs(first[1] - second[1]) < .00001);
  } finally { analyser.close(); }
});
await test('actual prefix work is optional on decoder refusal and recovers for another file', async () => {
  const analyser = new LoudnessAnalyser({ wasmBinary: binary, createWorker });
  try {
    assert.equal(await analyser.analyseOwned({ ...request(), bytes: Uint8Array.of(1, 2, 3).buffer }), null);
    assert(nativeLoudnessProfile(await analyser.analyseOwned(request())));
  } finally { analyser.close(); }
});
await test('cancellation terminates an actual worker and a fresh thread can prepare the next track', async () => {
  const analyser = new LoudnessAnalyser({ wasmBinary: binary, createWorker }), controller = new AbortController();
  try {
    const pending = analyser.analyseOwned(request(), { signal: controller.signal });
    const rejected = assert.rejects(pending, { name: 'AbortError' }); controller.abort(); await rejected;
    assert(nativeLoudnessProfile(await analyser.analyseOwned(request())));
  } finally { analyser.close(); }
});
await test('production preparation and actual worker preserve caller bytes and reuse complete facts', async () => {
  const analyser = new LoudnessAnalyser({ wasmBinary: binary, createWorker }), records = new Map();
  const preparation = new LoudnessPreparation({ analyser, crypto: webcrypto, revision: () => 'thread-check', cache: {
    read: async (key) => records.get(key) ?? null, write: async (key, profile) => records.set(key, profile) } });
  try {
    const input = request(), before = Buffer.from(input.bytes).toString('hex'), count = started;
    const first = await preparation.prepare(input); assert(first.profile?.complete);
    assert.equal(Buffer.from(input.bytes).toString('hex'), before); assert.equal(started, count + 1);
    assert.deepEqual(await preparation.prepare(input), first); assert.equal(started, count + 1);
    assert.equal(records.size, 1);
  } finally { analyser.close(); }
});
await test('independent native analysis never advances an audible decoder', async () => {
  const Engine = (await import('../web/vendor/engine.mjs')).default;
  const engine = await Engine({ wasmBinary: binary }), data = stereoFixture('correlated');
  const handle = openFixture(engine, data);
  const analyser = new LoudnessAnalyser({ wasmBinary: binary, createWorker });
  try {
    renderPcm(engine, handle, 44100, 2048); const position = engine._pt_position(handle);
    assert(nativeLoudnessProfile(await analyser.analyseOwned(request())));
    assert.equal(engine._pt_position(handle), position);
    assert(renderPcm(engine, handle, 44100, 2048).some((value) => Math.abs(value) > .001));
  } finally { analyser.close(); engine._pt_close(handle); }
});
await test('a failed worker engine initialization is retryable through the actual module factory', async () => {
  const Engine = (await import('../web/vendor/engine.mjs')).default; let attempts = 0;
  const analyse = createLoudnessAnalyser((options) => { if (!attempts++) throw new Error('first init refused'); return Engine(options); });
  await assert.rejects(analyse({ ...request(), wasmBinary: binary }), /first init/);
  assert(nativeLoudnessProfile(await analyse({ ...request(), wasmBinary: binary }))); assert.equal(attempts, 2);
});
assert.equal(started, terminated); assert(checks > 0);
console.log(`${checks} real-thread/native-WASM analysis cases passed (no browser audio device)`);
