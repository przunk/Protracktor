// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { downloadMusic, MUSIC_HEADERS_TIMEOUT, MUSIC_READ_TIMEOUT } from '../web/src/music-download.js';
const tick = () => new Promise((done) => setImmediate(done));
async function clock(run) {
  const set = globalThis.setTimeout, clear = globalThis.clearTimeout;
  let now = 0, next = 0; const timers = new Map();
  globalThis.setTimeout = (fn, delay) => { const id = ++next; timers.set(id, { fn, at: now + delay }); return id; };
  globalThis.clearTimeout = (id) => timers.delete(id);
  const advance = async (delta) => {
    now += delta;
    for (const [id, timer] of [...timers]) if (timer.at <= now) { timers.delete(id); timer.fn(); }
    await tick();
  };
  try { await run({ advance, timers }); } finally { globalThis.setTimeout = set; globalThis.clearTimeout = clear; }
}
let checks = 0;
async function test(name, run) { await run(); checks++; console.log(`✓ ${name}`); }
await test('the real platform Response stream returns exact bytes and retires its deadlines', async () => clock(async ({ timers }) => {
  let signal;
  const bytes = await downloadMusic(async (url, options) => { signal = options.signal; return new Response(new Uint8Array([1, 2, 3])); }, 'fixture');
  assert.deepEqual([...new Uint8Array(bytes)], [1, 2, 3]); await tick(); assert.equal(timers.size, 0); assert.equal(signal.aborted, false);
}));
await test('a stalled header uses the combined native connect and response allowance', async () => clock(async ({ advance }) => {
  let signal, completed = false;
  const result = downloadMusic((url, options) => { signal = options.signal; return new Promise(() => {}); }, 'fixture');
  const rejected = assert.rejects(result, { name: 'TimeoutError' }).then(() => { completed = true; });
  await tick(); await advance(MUSIC_HEADERS_TIMEOUT - 1); assert.equal(completed, false);
  await advance(1); await rejected; assert.equal(signal.aborted, true);
}));
await test('a progressing real stream renews the native inactivity allowance for every read', async () => clock(async ({ advance, timers }) => {
  let producer;
  const stream = new ReadableStream({ start(controller) { producer = controller; } });
  const result = downloadMusic(async () => new Response(stream), 'fixture');
  await tick(); await advance(MUSIC_READ_TIMEOUT - 1); producer.enqueue(new Uint8Array([4])); await tick();
  await advance(MUSIC_READ_TIMEOUT - 1); producer.enqueue(new Uint8Array([5])); await tick(); producer.close();
  assert.deepEqual([...new Uint8Array(await result)], [4, 5]); await tick(); assert.equal(timers.size, 0);
}));
await test('a stalled real body aborts its transfer and never returns the partial bytes', async () => clock(async ({ advance }) => {
  let signal, cancelled = false;
  const stream = new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array([9])); }, cancel() { cancelled = true; } });
  const result = downloadMusic(async (url, options) => { signal = options.signal; return new Response(stream); }, 'fixture');
  const rejected = assert.rejects(result, { name: 'TimeoutError' }); await tick();
  await advance(MUSIC_READ_TIMEOUT); await rejected; assert.equal(signal.aborted, true); assert.equal(cancelled, true);
}));
await test('HTTP refusal never consumes or returns a response body', async () => {
  let consumed = false;
  await assert.rejects(downloadMusic(async () => ({ ok: false, status: 404, arrayBuffer() { consumed = true; } }), 'fixture'), /HTTP 404/);
  assert.equal(consumed, false);
});
assert.equal(checks, 5); assert.equal(MUSIC_HEADERS_TIMEOUT, 45000); assert.equal(MUSIC_READ_TIMEOUT, 30000);
console.log('5 production music-transfer cases passed with real Response streams and controlled timers');
