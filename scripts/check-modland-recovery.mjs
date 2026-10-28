// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { legacyUnknownAuthorUrl } from '../web/src/catalogue-urls.js';
import { downloadMusic, MUSIC_HEADERS_TIMEOUT, MUSIC_READ_TIMEOUT } from '../web/src/music-download.js';
import { RemoteMusic } from '../web/src/remote-music.js';
import { musicCache, forget } from '../web/src/store.js';

const lines = fs.readFileSync('docs/rules/modland-legacy-cases.tsv', 'utf8').trim().split('\n');
const count = Number(lines.find((line) => line.startsWith('# cases: ')).slice('# cases: '.length));
const rows = lines.filter((line) => line && !line.startsWith('#'));
assert.equal(rows.shift(), 'case\turl\texpect');
const cases = rows.map((line) => { const [name, url, expected] = line.split('\t'); return { name, url, expected: expected === '-' ? null : expected }; });
assert(count > 0); assert.equal(cases.length, count);
const old = cases[0].url, replacement = cases[0].expected, payload = new Uint8Array([0, 1, 2, 3]);
const tick = () => new Promise((done) => setImmediate(done));
const until = async (predicate) => {
  for (let i = 0; i < 100; i++) { if (await predicate()) return; await tick(); }
  throw new Error('The production boundary was not reached');
};
async function clock(run) {
  const set = globalThis.setTimeout, clear = globalThis.clearTimeout;
  let now = 0, serial = 0; const timers = new Map();
  globalThis.setTimeout = (fn, delay) => { const id = ++serial; timers.set(id, { fn, at: now + delay }); return id; };
  globalThis.clearTimeout = (id) => timers.delete(id);
  const advance = async (delta) => {
    now += delta;
    for (const [id, timer] of [...timers]) if (timer.at <= now) { timers.delete(id); timer.fn(); }
    await tick();
  };
  try { await run({ advance, timers }); } finally { globalThis.setTimeout = set; globalThis.clearTimeout = clear; }
}
let checks = 0;
async function test(name, run) {
  try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name);
}

await test('the shared native URL cases preserve exact escaping and reject ambiguous routes', async () => {
  for (const row of cases) assert.equal(legacyUnknownAuthorUrl(row.url), row.expected, row.name);
  // Java URLDecoder replaces malformed UTF-8. A browser declines that ambiguous raw input.
  assert.equal(legacyUnknownAuthorUrl(old.replace('conflict', '%FF')), null);
  assert.equal(legacyUnknownAuthorUrl(null), null);
});
await test('a successful original response never asks for the moved path', async () => {
  const requests = [];
  const bytes = await downloadMusic(async (url) => { requests.push(url); return new Response(payload); }, old);
  assert.deepEqual(new Uint8Array(bytes), payload); assert.deepEqual(requests, [old]);
});
await test('only 404 permits recovery and refused bodies are cancelled without being consumed', async () => {
  for (const status of [401, 403, 410, 429, 500, 503]) {
    const requests = []; let discarded = false;
    await assert.rejects(downloadMusic(async (url) => {
      requests.push(url); return new Response(new ReadableStream({ cancel() { discarded = true; } }), { status });
    }, old), new RegExp('HTTP ' + status));
    assert.deepEqual(requests, [old]); assert.equal(discarded, true);
  }
});
await test('a 404 tries one replacement with exact bytes and discards the original error body', async () => {
  const requests = []; let discarded = false;
  const stream = new ReadableStream({ cancel() { discarded = true; } });
  const bytes = await downloadMusic(async (url) => { requests.push(url); return url === old ? new Response(stream, { status: 404 }) : new Response(payload); }, old);
  assert.deepEqual(requests, [old, replacement]); assert.equal(discarded, true); assert.deepEqual(new Uint8Array(bytes), payload);
});
await test('a missing replacement is tried once and repeated failures are not negative-cached', async () => {
  await musicCache.clear(); const requests = [];
  const music = new RemoteMusic({ cache: musicCache, fetch: async (url) => { requests.push(url); return new Response(null, { status: 404 }); } });
  await music.ready;
  for (let i = 0; i < 2; i++) await assert.rejects(music.bytes(old), /HTTP 404/);
  assert.deepEqual(requests, [old, replacement, old, replacement]); assert.deepEqual(await musicCache.snapshot(), []);
});
await test('unrelated or ambiguous addresses retain the original HTTP refusal', async () => {
  for (const row of cases.filter((row) => row.expected === null)) {
    const requests = [];
    await assert.rejects(downloadMusic(async (url) => { requests.push(url); return new Response(null, { status: 404 }); }, row.url), /HTTP 404/);
    assert.deepEqual(requests, [row.url], row.name);
  }
});
await test('transport failures at either address stay transport failures without another attempt', async () => {
  for (const at of [old, replacement]) {
    const requests = [];
    await assert.rejects(downloadMusic(async (url) => {
      requests.push(url); if (url === at) throw new TypeError('controlled connection failure');
      return new Response(null, { status: 404 });
    }, old), /controlled connection failure/);
    assert.deepEqual(requests, at === old ? [old] : [old, replacement]);
  }
});
await test('both attempts have an independent full header allowance', async () => clock(async ({ advance, timers }) => {
  const requests = []; let first, second;
  const job = downloadMusic((url) => { requests.push(url); return new Promise((resolve) => { if (url === old) first = resolve; else second = resolve; }); }, old);
  await tick(); await advance(MUSIC_HEADERS_TIMEOUT - 1); first(new Response(null, { status: 404 })); await tick();
  assert.deepEqual(requests, [old, replacement]); await advance(MUSIC_HEADERS_TIMEOUT - 1); second(new Response(payload));
  assert.deepEqual(new Uint8Array(await job), payload); await tick(); assert.equal(timers.size, 0);
}));
await test('cancelling the whole operation interrupts replacement headers and cleans deadlines', async () => clock(async ({ timers }) => {
  const controller = new AbortController(), requests = []; let transfer;
  const job = downloadMusic(async (url, options) => {
    requests.push(url); transfer = options.signal; if (url === old) return new Response(null, { status: 404 });
    return new Promise(() => {});
  }, old, { signal: controller.signal });
  const rejected = assert.rejects(job, { name: 'AbortError' }); await until(() => requests.length === 2); controller.abort(); await rejected;
  assert.equal(transfer.aborted, true); assert.equal(timers.size, 0); assert.deepEqual(requests, [old, replacement]);
}));
await test('replacement body inactivity never returns or stores a partial recovery', async () => clock(async ({ advance }) => {
  let discarded = false;
  const stream = new ReadableStream({ start(controller) { controller.enqueue(payload); }, cancel() { discarded = true; } });
  const job = downloadMusic(async (url) => url === old ? new Response(null, { status: 404 }) : new Response(stream), old);
  const rejected = assert.rejects(job, { name: 'TimeoutError' }); await tick(); await advance(MUSIC_READ_TIMEOUT); await rejected;
  assert.equal(discarded, true);
}));
await test('all four reported native files retain only their original disk identity and reopen offline', async () => {
  for (const row of cases.slice(0, 4)) {
    await musicCache.clear(); const requests = [];
    const music = new RemoteMusic({ cache: musicCache, fetch: async (url) => { requests.push(url); return new Response(payload, { status: url === row.url ? 404 : 200 }); } });
    await music.ready; assert.deepEqual(new Uint8Array(await music.bytes(row.url)), payload);
    assert.deepEqual(requests, [row.url, row.expected]); assert.equal(await musicCache.read(row.expected), null);
    assert.deepEqual((await musicCache.snapshot()).map((entry) => entry.url), [row.url]);
    const reopened = await import('../web/src/store.js?modland-recovered-offline');
    try {
      let attempted = false;
      const other = new RemoteMusic({ cache: reopened.musicCache, fetch: async () => { attempted = true; throw new TypeError('offline'); } });
      await other.ready; assert.deepEqual(new Uint8Array(await other.bytes(row.url)), payload); assert.equal(attempted, false);
    } finally { reopened.forget(); }
  }
});
await test('a concurrent recovery is shared and a cancelled waiter cannot abort another listener', async () => {
  await musicCache.clear(); const requests = []; let release;
  const music = new RemoteMusic({ cache: musicCache, fetch: async (url) => {
    requests.push(url); if (url === old) return new Response(null, { status: 404 });
    return new Promise((resolve) => { release = () => resolve(new Response(payload)); });
  } });
  await music.ready;
  const controller = new AbortController(), first = music.bytes(old, { signal: controller.signal }), second = music.bytes(old), third = music.bytes(old);
  const rejected = assert.rejects(first, { name: 'AbortError' }); await until(() => release); controller.abort(); await rejected; release();
  const [a, b] = await Promise.all([second, third]); assert.notEqual(a, b); structuredClone(a, { transfer: [a] });
  assert.deepEqual(new Uint8Array(b), payload); assert.deepEqual(requests, [old, replacement]);
  assert.deepEqual(new Uint8Array(await music.bytes(old)), payload); assert.deepEqual(requests, [old, replacement]);
});
await test('Clear during recovery prevents a late alternate response from restoring disk state', async () => {
  await musicCache.clear(); let release;
  const music = new RemoteMusic({ cache: musicCache, fetch: async (url) => {
    if (url === old) return new Response(null, { status: 404 }); return new Promise((resolve) => { release = () => resolve(new Response(payload)); });
  } });
  await music.ready; const job = music.bytes(old); await until(() => release); await music.clear(); release();
  assert.deepEqual(new Uint8Array(await job), payload); assert.deepEqual(await musicCache.snapshot(), []);
});

assert.equal(checks, 13); forget();
console.log(`${checks} production recovery cases passed; ${count} shared native URL cases; host IndexedDB and real Response streams`);
