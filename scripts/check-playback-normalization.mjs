// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { PlaybackNormalization } from '../web/src/playback-normalization.js';
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
  throw new Error('The expected normalization ownership boundary was not reached');
};
const values = [20, -23, .4, 0, 0];
const profile = Object.freeze({ seconds: 20, lufs: -23, peak: .4, complete: false });
const source = () => Uint8Array.of(1, 2, 3).buffer;
const wasm = Uint8Array.of(4, 5, 6).buffer;
const digest = [...new Uint8Array(await webcrypto.subtle.digest('SHA-256', wasm))].map((v) => v.toString(16).padStart(2, '0')).join('');
const base = (name = 'a.mod') => loudnessContentKey(source(), name, [], digest, webcrypto);
function harness({ enabled = true, width = 100, crypto = webcrypto, measure = async () => values, read, autoOpen = true } = {}) {
  const sent = [], records = new Map(), calls = [], writes = [];
  const cache = { read: read ?? (async (key) => records.get(key) ?? null),
    write: async (key, value) => { writes.push({ key, value }); records.set(key, value); } };
  const player = new PlaybackNormalization({ enabled, width, crypto, cache,
    send: (message) => {
      sent.push(structuredClone(message));
      if (autoOpen && message.type === 'prepared') queueMicrotask(() => player.opened(message.requestId));
    },
    createAnalyser: () => ({ close() {}, analyseOwned: async (request, options) => { calls.push(request); return measure(request, options); } }) });
  player.setEngine(wasm);
  return { player, sent, records, calls, writes };
}
await test('initial decoder selection is prepared before publishing matching first-PCM facts', async () => {
  const h = harness({ measure: async (request) => { assert.equal(request.subsong, 7); return [...values.slice(0, 4), 7]; } });
  const data = source(); h.player.register(1, data, 'a.mod');
  assert.equal(h.sent.length, 0); await h.player.decoded({ requestId: 1, current: 7 });
  assert.equal(data.byteLength, 3);
  assert.deepEqual(h.sent, [{ type: 'prepared', requestId: 1, current: 7, width: 100, profile }]);
  await h.player.decoded({ requestId: 1, current: 7 }); assert.equal(h.calls.length, 1);
});
await test('Off still completes the same decoder handshake without starting analysis or writing statistics', async () => {
  const h = harness({ enabled: false }); h.player.register(1, source(), 'a.mod');
  await h.player.decoded({ requestId: 1, current: 0 });
  assert.deepEqual(h.sent, [{ type: 'prepared', requestId: 1, current: 0, width: 100, profile: null }]);
  assert.equal(h.calls.length, 0); assert.equal(h.writes.length, 0);
});
await test('the source snapshot survives the real transfer that detaches main-thread playback bytes', async () => {
  const h = harness({ measure: async ({ bytes }) => {
    assert.deepEqual([...new Uint8Array(bytes)], [1, 2, 3]); return values;
  } }), data = source();
  h.player.register(1, data, 'a.mod'); structuredClone(data, { transfer: [data] });
  assert.equal(data.byteLength, 0); await h.player.decoded({ requestId: 1, current: 0 });
  assert.deepEqual(h.sent.at(-1).profile, profile); assert.equal(h.calls.length, 1);
});
await test('Stop cancels a private startup measurement and cannot publish or cache its late result', async () => {
  const barrier = deferred(), h = harness({ measure: () => barrier.promise }), controller = new AbortController();
  h.player.register(1, source(), 'a.mod', { signal: controller.signal });
  const preparing = h.player.decoded({ requestId: 1, current: 0 }); await until(() => h.calls.length === 1);
  controller.abort(); barrier.resolve(values); await preparing;
  assert.equal(h.sent.length, 0); assert.equal(h.writes.length, 0);
});
await test('replacing a loading source rejects its late completion and prepares only the new selection', async () => {
  const barrier = deferred(); let n = 0;
  const h = harness({ measure: async () => ++n === 1 ? barrier.promise : values });
  h.player.register(1, source(), 'old.mod'); const old = h.player.decoded({ requestId: 1, current: 0 });
  await until(() => h.calls.length === 1); h.player.register(2, source(), 'new.mod');
  const next = h.player.decoded({ requestId: 2, current: 0 }); barrier.resolve(values); await Promise.all([old, next]);
  assert.deepEqual(h.sent.map((row) => row.requestId), [2]);
  assert.equal(h.writes.length, 1); assert.equal(h.writes[0].key, await loudnessVariantKey(await base('new.mod'), 0, 100, webcrypto));
});
await test('a live Off during prefix preparation cancels optional work but completes the playable file', async () => {
  const h = harness({ measure: async (_request, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true });
  }) });
  h.player.register(1, source(), 'a.mod'); const pending = h.player.decoded({ requestId: 1, current: 0 });
  await until(() => h.calls.length === 1); h.player.setEnabled(false); await pending;
  assert.deepEqual(h.sent, [{ type: 'normalization', enabled: false },
    { type: 'prepared', requestId: 1, current: 0, width: 100, profile: null }]);
  assert.equal(h.writes.length, 0);
});
await test('a loading stereo change cannot apply an old-width profile and does not restart analysis', async () => {
  const barrier = deferred(), h = harness({ measure: () => barrier.promise });
  h.player.register(1, source(), 'a.mod'); const pending = h.player.decoded({ requestId: 1, current: 0 });
  await until(() => h.calls.length === 1); h.player.setWidth(0); barrier.resolve(values); await pending;
  assert.deepEqual(h.sent, [{ type: 'stereo', percent: 0 },
    { type: 'prepared', requestId: 1, current: 0, width: 100, profile: null }]);
  assert.equal(h.calls.length, 1);
});
await test('a late cache read cannot re-enable normalization after Off', async () => {
  let delay = false; const barrier = deferred(), h = harness({ read: async () => delay ? barrier.promise : null });
  h.player.register(1, source(), 'a.mod'); await h.player.decoded({ requestId: 1, current: 0 });
  delay = true; const refresh = h.player.context({ requestId: 1, subsong: 0, width: 100 });
  h.player.setEnabled(false); barrier.resolve(profile); await refresh;
  assert(!h.sent.some((row) => row.type === 'normalization-profile'));
});
await test('repeated subsong identities still reject an older selection read', async () => {
  const barriers = [deferred(), deferred(), deferred()]; let reads = 0, delay = false;
  const h = harness({ read: async () => delay ? barriers[reads++].promise : null });
  h.player.register(1, source(), 'a.mod'); await h.player.decoded({ requestId: 1, current: 0 }); delay = true;
  const one = h.player.context({ requestId: 1, subsong: 0, width: 100 }); await until(() => reads === 1);
  const two = h.player.context({ requestId: 1, subsong: 1, width: 100 }); await until(() => reads === 2);
  const three = h.player.context({ requestId: 1, subsong: 0, width: 100 }); await until(() => reads === 3);
  const complete = { ...profile, complete: true };
  barriers[2].resolve(complete); await three; barriers[0].resolve(profile); barriers[1].resolve(profile); await Promise.all([one, two]);
  assert.deepEqual(h.sent.filter((row) => row.type === 'normalization-profile'), [
    { type: 'normalization-profile', requestId: 1, subsong: 0, width: 100, profile: complete }]);
});
await test('a closing old decoder saves its own content facts without configuring the new source', async () => {
  const h = harness(); h.player.register(1, source(), 'old.mod'); await h.player.decoded({ requestId: 1, current: 0 });
  h.player.register(2, source(), 'new.mod'); await h.player.decoded({ requestId: 2, current: 0 });
  const before = h.sent.length;
  const saving = h.player.measured({ requestId: 1, subsong: 0, width: 100, values: [7, -20, .3, 1], reason: 'close' });
  h.player.closed(1); await saving;
  assert.equal(h.writes.at(-1).key, await loudnessVariantKey(await base('old.mod'), 0, 100, webcrypto));
  assert.equal(h.writes.at(-1).value.complete, true); assert.equal(h.sent.length, before);
  await h.player.context({ requestId: 2, subsong: 0, width: 100 });
  assert.equal(h.sent.at(-1).requestId, 2);
});
await test('progress snapshots do not continually rewrite derived disk statistics', async () => {
  const h = harness(); h.player.register(1, source(), 'a.mod'); await h.player.decoded({ requestId: 1, current: 0 });
  const before = h.writes.length;
  await h.player.measured({ requestId: 1, subsong: 0, width: 100, values, reason: 'progress' });
  assert.equal(h.writes.length, before);
  await h.player.measured({ requestId: 1, subsong: 0, width: 100, values: [30, -23, .4, 0], reason: 'toggle' });
  assert.equal(h.writes.length, before + 1);
});
await test('read-ahead warms actual and -1 identities only when enabled, without consuming caller bytes', async () => {
  const h = harness({ measure: async () => [...values.slice(0, 4), 7] }), data = source();
  await h.player.warm({ bytes: data, name: 'a.mod' }); assert.equal(data.byteLength, 3);
  h.player.register(1, data, 'a.mod'); await h.player.decoded({ requestId: 1, current: 7 });
  assert.equal(h.calls.length, 1); assert.equal(h.sent.at(-1).profile.complete, false);
  h.player.setEnabled(false); await h.player.warm({ bytes: source(), name: 'other.mod' });
  assert.equal(h.calls.length, 1);
});
await test('unavailable engine hashing keeps live preparation useful without claiming reusable cache identity', async () => {
  const h = harness({ crypto: {} }); h.player.register(1, source(), 'a.mod');
  await h.player.decoded({ requestId: 1, current: 0 }); assert.deepEqual(h.sent.at(-1).profile, profile);
  assert.equal(h.writes.length, 0);
});
await test('missing optional engine preparation still releases the first-PCM handshake', async () => {
  const sent = [], player = new PlaybackNormalization({ send: (row) => sent.push(row), enabled: true });
  player.register(1, source(), 'a.mod'); await player.decoded({ requestId: 1, current: 0 });
  assert.deepEqual(sent, [{ type: 'prepared', requestId: 1, current: 0, width: 100, profile: null }]);
});
await test('Stop also retires preparation already sent but never acknowledged as opened', async () => {
  const h = harness({ autoOpen: false }); h.player.register(1, source(), 'a.mod');
  await h.player.decoded({ requestId: 1, current: 0 }); h.player.cancelPending();
  const before = h.sent.length; h.player.opened(1);
  await h.player.context({ requestId: 1, subsong: 0, width: 100 });
  assert.equal(h.sent.length, before);
  const writes = h.writes.length;
  await h.player.measured({ requestId: 1, subsong: 0, width: 100, values, reason: 'close' });
  assert.equal(h.writes.length, writes);
});
assert(checks > 0); console.log(`${checks} playback normalization identity/race/preparation cases passed`);
