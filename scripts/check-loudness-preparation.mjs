// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { LoudnessAnalyser, LOUDNESS_WORKER_TIMEOUT_MS } from '../web/src/loudness-analyser.js';
import { LoudnessPreparation } from '../web/src/loudness-preparation.js';
import { loudnessContentKey, loudnessVariantKey } from '../web/src/loudness-profiles.js';

let checks = 0;
async function test(name, run) {
  try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
const until = async (predicate) => {
  const deadline = Date.now() + 5000;
  while (Date.now() < deadline) { if (predicate()) return; await new Promise((done) => setTimeout(done, 1)); }
  throw new Error('The expected ownership boundary was not reached');
};
const values = [20, -23, .4, 0, 0];
const profile = { seconds: 20, lufs: -23, peak: .4, complete: false };
const source = () => ({ bytes: Uint8Array.of(1, 2, 3).buffer, name: 'a.mod', subsong: 0, width: 100 });
function workerHarness(options = {}) {
  const workers = [], timers = new Map(), wasmBinary = Uint8Array.of(4, 5).buffer;
  let nextTimer = 0;
  const analyser = new LoudnessAnalyser({ wasmBinary,
    createWorker: () => {
      const worker = { requests: [], terminated: 0,
        postMessage(data, transfer) { this.requests.push(structuredClone(data, { transfer })); },
        terminate() { this.terminated++; } };
      workers.push(worker); return worker;
    },
    setTimer: (callback, delay) => { assert.equal(delay, LOUDNESS_WORKER_TIMEOUT_MS); timers.set(++nextTimer, callback); return nextTimer; },
    clearTimer: (timer) => timers.delete(timer), ...options });
  const reply = (worker, result = values, id = worker.requests.at(-1).id) => worker.onmessage({ data: { id, values: result } });
  return { analyser, workers, timers, wasmBinary, reply };
}
await test('the worker owns transferred input and a private WASM copy; an idle worker is reused', async () => {
  const h = workerHarness(), input = source(), first = h.analyser.analyseOwned(input);
  assert.equal(input.bytes.byteLength, 0); assert.equal(h.wasmBinary.byteLength, 2);
  assert.equal(h.workers[0].requests[0].wasmBinary.byteLength, 2);
  h.reply(h.workers[0]); assert.deepEqual(await first, values); assert.equal(h.timers.size, 0);
  const second = h.analyser.analyseOwned(source()); assert.equal(h.workers.length, 1);
  assert.equal(h.workers[0].requests[1].wasmBinary, undefined);
  h.reply(h.workers[0]); await second; h.analyser.close(); assert.equal(h.workers[0].terminated, 1);
});
await test('cancelling the current job rejects it and destroys its decoder worker', async () => {
  const h = workerHarness(), controller = new AbortController();
  const pending = h.analyser.analyseOwned(source(), { signal: controller.signal });
  const rejected = assert.rejects(pending, { name: 'AbortError' });
  controller.abort(); await rejected; assert.equal(h.workers[0].terminated, 1);
  assert.equal(h.analyser.running, false); assert.equal(h.timers.size, 0);
  const next = h.analyser.analyseOwned(source());
  h.reply(h.workers[0]); assert.equal(h.analyser.running, true);
  h.reply(h.workers[1]); assert.deepEqual(await next, values); h.analyser.close();
});
await test('stale worker IDs cannot settle a newer job and concurrent analysis is rejected', async () => {
  const h = workerHarness(), pending = h.analyser.analyseOwned(source());
  assert.throws(() => h.analyser.analyseOwned(source()), /already running/);
  h.reply(h.workers[0], values, -1); assert.equal(h.analyser.running, true);
  h.reply(h.workers[0]); await pending; h.analyser.close();
});
await test('a pre-cancelled job creates no worker and does not consume input', async () => {
  const h = workerHarness(), controller = new AbortController(), input = source(); controller.abort();
  await assert.rejects(h.analyser.analyseOwned(input, { signal: controller.signal }), { name: 'AbortError' });
  assert.equal(h.workers.length, 0); assert.equal(input.bytes.byteLength, 3);
});
await test('a finite worker safety deadline disposes optional work and falls back', async () => {
  const h = workerHarness(), pending = h.analyser.analyseOwned(source());
  [...h.timers.values()][0](); assert.equal(await pending, null);
  assert.equal(h.workers[0].terminated, 1); assert.equal(h.analyser.running, false);
  assert.equal(h.timers.size, 0);
});
await test('an immediately expired deadline never posts to a disposed worker', async () => {
  const h = workerHarness({ setTimer: (callback) => { callback(); return 1; } });
  assert.equal(await h.analyser.analyseOwned(source()), null);
  assert.equal(h.workers[0].requests.length, 0); assert.equal(h.workers[0].terminated, 1);
});
for (const createWorker of [() => { throw new Error('unsupported'); }, () => null, () => ({})]) {
  await test('unsupported or invalid worker creation safely falls back', async () => {
    const h = workerHarness({ createWorker });
    assert.equal(await h.analyser.analyseOwned(source()), null); assert.equal(h.analyser.running, false);
  });
}
await test('failed transfer and message deserialization free the worker and allow retry', async () => {
  const h = workerHarness(), input = source();
  const first = h.analyser.analyseOwned(input); h.workers[0].onmessageerror({ preventDefault() {} });
  assert.equal(await first, null); assert.equal(h.workers[0].terminated, 1);
  const second = h.analyser.analyseOwned(source()); h.reply(h.workers[1]); await second;
  h.workers[1].postMessage = () => { throw new Error('transfer refused'); };
  assert.equal(await h.analyser.analyseOwned(source()), null); assert.equal(h.workers[1].terminated, 1);
});
await test('an idle worker load error disposes it without poisoning future jobs', async () => {
  const h = workerHarness(), first = h.analyser.analyseOwned(source()); h.reply(h.workers[0]); await first;
  let prevented = false; h.workers[0].onerror({ preventDefault() { prevented = true; } });
  assert(prevented); assert.equal(h.workers[0].terminated, 1);
  const next = h.analyser.analyseOwned(source()); assert.equal(h.workers.length, 2);
  h.reply(h.workers[1]); await next; h.analyser.close();
});
await test('close cancels an active job without waiting for its worker', async () => {
  const h = workerHarness(), pending = h.analyser.analyseOwned(source());
  const rejected = assert.rejects(pending, { name: 'AbortError' }); h.analyser.close(); await rejected;
  assert.equal(h.workers[0].terminated, 1); assert.equal(h.analyser.running, false);
});

function preparationHarness({ measure = async () => values, cacheRead, crypto = webcrypto } = {}) {
  const records = new Map(), calls = [], writes = [];
  const cache = { read: cacheRead ?? (async (key) => records.get(key) ?? null),
    write: async (key, value) => { records.set(key, value); writes.push(key); } };
  const preparation = new LoudnessPreparation({ cache, crypto, revision: () => 'decoder', analyser: {
    analyseOwned: async (request, options) => { calls.push(request); return measure(request, options); } } });
  return { preparation, records, calls, writes };
}
await test('the preparation snapshot protects cache and caller bytes across asynchronous hashing', async () => {
  const original = source(), before = original.bytes.slice(0), h = preparationHarness({ measure: async ({ bytes }) => {
    assert.deepEqual([...new Uint8Array(bytes)], [1, 2, 3]); structuredClone(bytes, { transfer: [bytes] }); return values;
  } });
  const task = h.preparation.prepare(original); new Uint8Array(original.bytes)[0] = 99;
  const prepared = await task;
  assert.equal(prepared.base, await loudnessContentKey(before, original.name, [], 'decoder', webcrypto));
  assert.equal(original.bytes.byteLength, 3); assert.equal(new Uint8Array(original.bytes)[0], 99);
});
await test('an actual-index cache hit skips decoding and returns a reusable source identity', async () => {
  const h = preparationHarness(), first = await h.preparation.prepare(source());
  assert.deepEqual(first.profile, profile);
  assert.deepEqual(await h.preparation.prepare(source()), first); assert.equal(h.calls.length, 1);
});
await test('warming stores both actual nonzero subsong and the Native -1 alias', async () => {
  const h = preparationHarness({ measure: async () => [...values.slice(0, 4), 7] });
  const prepared = await h.preparation.prepare({ ...source(), subsong: -1 });
  assert.deepEqual(prepared.profile, profile);
  for (const index of [7, -1]) assert(h.records.has(await loudnessVariantKey(prepared.base, index, 100, webcrypto)));
  await h.preparation.prepare({ ...source(), subsong: 7 }); await h.preparation.prepare({ ...source(), subsong: -1 });
  assert.equal(h.calls.length, 1);
});
await test('a valid measurement for another actual subsong is not applied to this selection', async () => {
  const h = preparationHarness({ measure: async () => [...values.slice(0, 4), 7] });
  const result = await h.preparation.prepare(source()); assert.equal(result.profile, null);
  assert(h.records.has(await loudnessVariantKey(result.base, 7, 100, webcrypto)));
});
await test('invalid short silent or malformed worker measurements never become cached gain facts', async () => {
  for (const result of [null, [], [.2, -20, .2, 1, 0], [20, -100, 0, 1, 0], [NaN, -23, .4, 0, 0]]) {
    const h = preparationHarness({ measure: async () => result });
    assert.equal((await h.preparation.prepare(source())).profile, null); assert.equal(h.writes.length, 0);
  }
});
await test('missing actual-index facts cannot configure a specifically selected subsong', async () => {
  for (const actual of [undefined, NaN, -1, 1.5, 65536]) {
    const h = preparationHarness({ measure: async () => [...values.slice(0, 4), actual] });
    assert.equal((await h.preparation.prepare(source())).profile, null); assert.equal(h.writes.length, 0);
  }
});
await test('unavailable hashing disables persistence but keeps an independent usable measurement', async () => {
  const h = preparationHarness({ crypto: {} }), result = await h.preparation.prepare(source());
  assert.equal(result.base, null); assert.deepEqual(result.profile, profile); assert.equal(h.writes.length, 0);
});
await test('optional cache read and worker failures fall back without rejecting playback preparation', async () => {
  const h = preparationHarness({ cacheRead: async () => { throw new Error('read refused'); }, measure: async () => { throw new Error('worker unavailable'); } });
  const result = await h.preparation.prepare(source()); assert.equal(result.profile, null); assert.equal(h.calls.length, 1);
});
await test('Off computes identity without starting a prefix decoder or holding the analysis queue', async () => {
  const barrier = deferred(), h = preparationHarness({ measure: () => barrier.promise });
  const first = h.preparation.prepare(source()); await until(() => h.calls.length === 1);
  const off = await h.preparation.prepare(source(), { analyse: false });
  assert(off.base); assert.equal(off.profile, null); assert.equal(h.calls.length, 1);
  barrier.resolve(values); await first;
});
await test('only one prefix decoder runs at a time and waiting work rechecks the improved cache', async () => {
  const barrier = deferred(); let active = 0, maximum = 0;
  const h = preparationHarness({ measure: async () => { maximum = Math.max(maximum, ++active); await barrier.promise; active--; return values; } });
  const first = h.preparation.prepare(source()), second = h.preparation.prepare(source());
  await until(() => h.calls.length === 1); barrier.resolve(); await Promise.all([first, second]);
  assert.equal(maximum, 1); assert.equal(h.calls.length, 1);
});
await test('cancelling queued work settles it before an older decode finishes', async () => {
  const barrier = deferred(), h = preparationHarness({ measure: () => barrier.promise });
  const first = h.preparation.prepare(source()); await until(() => h.calls.length === 1);
  const controller = new AbortController(), next = h.preparation.prepare({ ...source(), name: 'next.mod' }, { signal: controller.signal });
  const rejected = assert.rejects(next, { name: 'AbortError' });
  await new Promise((done) => setImmediate(done)); controller.abort(); await rejected;
  assert.equal(h.calls.length, 1); barrier.resolve(values); await first;
});
await test('cancellation after analysis cannot apply or cache the late result', async () => {
  const barrier = deferred(), h = preparationHarness({ measure: () => barrier.promise }), controller = new AbortController();
  const pending = h.preparation.prepare(source(), { signal: controller.signal });
  const rejected = assert.rejects(pending, { name: 'AbortError' });
  await until(() => h.calls.length === 1); controller.abort(); barrier.resolve(values); await rejected;
  const fresh = await h.preparation.prepare({ ...source(), name: 'fresh.mod' });
  assert.equal(h.writes.length, 1); assert.deepEqual(fresh.profile, profile);
});
await test('a failed prefix does not poison the serial queue for the next track', async () => {
  let calls = 0; const h = preparationHarness({ measure: async () => { if (!calls++) throw new Error('decoder refused'); return values; } });
  assert.equal((await h.preparation.prepare(source())).profile, null);
  assert.deepEqual((await h.preparation.prepare(source())).profile, profile); assert.equal(h.calls.length, 2);
});
assert(checks > 0); console.log(`${checks} loudness worker ownership/preparation cases passed`);
