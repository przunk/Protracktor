// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { settings, played, playlists, musicCache, forget } from '../web/src/store.js';
import { LoudnessProfiles, LOUDNESS_CACHE_KEY } from '../web/src/loudness-cache.js';
import { loudnessRecord } from '../web/src/loudness-profiles.js';

let checks = 0;
async function test(name, run) {
  try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
}
const key = (n) => n.toString(16).padStart(64, '0');
const profile = Object.freeze({ seconds: 20, lufs: -23, peak: .4, complete: false });
const cache = new LoudnessProfiles();
await test('two connections atomically retain simultaneous independent measurements', async () => {
  const other = await import('../web/src/store.js?loudness-second-connection');
  try {
    const second = new LoudnessProfiles(other.settings);
    await Promise.all(Array.from({ length: 30 }, (_, i) => (i % 2 ? second : cache).write(key(i), profile)));
    for (let n = 0; n < 30; n++) assert.deepEqual(await second.read(key(n)), profile);
  } finally { other.forget(); }
});
await test('late concurrent partial observations cannot replace a complete profile', async () => {
  const complete = { ...profile, seconds: 2, complete: true };
  await Promise.all([cache.write(key(1), complete), cache.write(key(1), { ...profile, seconds: 100 })]);
  assert.deepEqual(await cache.read(key(1)), complete);
  assert.equal(await cache.write(key(1), profile), false);
});
await test('a restarted reader sees the same CRC-checked facts', async () => {
  forget(); assert.deepEqual(await new LoudnessProfiles().read(key(0)), profile);
});
await test('History and fetched-music Clear preserve statistics and unrelated user data', async () => {
  await settings.set('loudness-unrelated', { value: 'preserve' });
  await playlists.save({ id: 'loudness-user-playlist', name: 'Keep my music', tracks: [{ url: 'https://example.test/a.mod' }], index: 0 });
  const before = await settings.get(LOUDNESS_CACHE_KEY);
  await played.clear(); await musicCache.clear();
  assert.deepEqual(await settings.get(LOUDNESS_CACHE_KEY), before);
  assert.deepEqual(await settings.get('loudness-unrelated'), { value: 'preserve' });
  assert.equal((await playlists.get('loudness-user-playlist')).tracks[0].url, 'https://example.test/a.mod');
  assert.deepEqual(await cache.read(key(0)), profile);
});
await test('finite-looking corrupt payloads miss and a fresh observation repairs only derived facts', async () => {
  const record = loudnessRecord(key(70), profile);
  await settings.set(LOUDNESS_CACHE_KEY, [{ ...record, payload: record.payload.slice(0, 20) + 'ff' + record.payload.slice(22) }]);
  assert.equal(await cache.read(key(70)), null);
  assert.equal(await cache.write(key(71), profile), true);
  assert.deepEqual((await settings.get(LOUDNESS_CACHE_KEY)).map((row) => row.key), [key(71)]);
});
await test('a malformed bank is a miss and can be replaced without a database reset', async () => {
  await settings.set(LOUDNESS_CACHE_KEY, 'not a measurement bank');
  assert.equal(await cache.read(key(71)), null);
  assert.equal(await cache.write(key(71), profile), true);
  assert.deepEqual(await cache.read(key(71)), profile);
});
await test('atomic reducer exceptions and asynchronous reducers preserve the old value', async () => {
  await settings.set('atomic-sentinel', { kept: true });
  await assert.rejects(settings.update('atomic-sentinel', () => { throw new Error('refused'); }), /refused/);
  await assert.rejects(settings.update('atomic-sentinel', async () => 'wrong'), /synchronous/);
  assert.deepEqual(await settings.get('atomic-sentinel'), { kept: true });
  assert.equal(await settings.update('atomic-sentinel', () => undefined), false);
});
await test('the production atomic path prevents read-modify-write losses', async () => {
  await settings.set('atomic-counter', 0);
  await Promise.all(Array.from({ length: 40 }, () => settings.update('atomic-counter', (value) => value + 1)));
  assert.equal(await settings.get('atomic-counter'), 40);
});
await test('quota refusal retains the prior complete bank and future writes can recover', async () => {
  const before = await settings.get(LOUDNESS_CACHE_KEY), put = IDBObjectStore.prototype.put;
  IDBObjectStore.prototype.put = function (row, ...args) {
    if (this.name === 'settings' && row.key === LOUDNESS_CACHE_KEY) throw new DOMException('quota', 'QuotaExceededError');
    return put.call(this, row, ...args);
  };
  try { assert.equal(await cache.write(key(72), profile), false); }
  finally { IDBObjectStore.prototype.put = put; }
  assert.deepEqual(await settings.get(LOUDNESS_CACHE_KEY), before);
  assert.equal(await cache.write(key(72), profile), true);
});
await test('invalid identity/profile and refused cache access remain optional misses', async () => {
  const refusal = new LoudnessProfiles({ get: async () => { throw new Error('read'); }, update: async () => { throw new Error('write'); } });
  assert.equal(await refusal.read(key(1)), null); assert.equal(await refusal.write(key(1), profile), false);
  const before = await settings.get(LOUDNESS_CACHE_KEY);
  assert.equal(await cache.write('../escape', profile), false);
  assert.equal(await cache.write(key(1), { ...profile, lufs: NaN }), false);
  assert.equal(await cache.read('../escape'), null); assert.deepEqual(await settings.get(LOUDNESS_CACHE_KEY), before);
});
await test('the production store retains only512 successful measurements without inferior promotion', async () => {
  await settings.set(LOUDNESS_CACHE_KEY, []);
  for (let n = 0; n < 512; n++) await cache.write(key(n), profile);
  assert.equal(await cache.write(key(0), { ...profile, seconds: 2 }), false);
  assert.deepEqual(await cache.read(key(0)), profile);
  await cache.write(key(512), profile);
  assert.equal(await cache.read(key(0)), null); assert.deepEqual(await cache.read(key(1)), profile);
  assert.equal((await settings.get(LOUDNESS_CACHE_KEY)).length, 512);
});
forget();
assert(checks > 0); console.log(`${checks} production loudness cache/atomic-storage cases passed`);
