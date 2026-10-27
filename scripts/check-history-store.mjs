// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { IDBFactory } from '../web/node_modules/fake-indexeddb/build/esm/index.js';
let checks = 0, moduleId = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const requestResult = (request) => new Promise((resolve, reject) => { request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); });
const fresh = async () => { globalThis.indexedDB = new IDBFactory(); return import(`../web/src/store.js?history-test=${++moduleId}`); };
const oldRow = { url: 'a', name: 'Actual title', file: 'a.mod', meta: 'Author', replayable: true, playedAt: 123, playCount: 7 };
async function seed(version, rows = [oldRow]) {
  const request = indexedDB.open('protracktor', version);
  request.onupgradeneeded = () => {
    const db = request.result;
    for (const [name, key] of [['played', 'url'], ['playlists', 'id'], ['settings', 'key'], ['catalogue', 'key']]) db.createObjectStore(name, { keyPath: key });
    if (version >= 4) request.transaction.objectStore('played').createIndex('playedAt', 'playedAt');
  };
  const db = await requestResult(request);
  await new Promise((resolve, reject) => {
    const transaction = db.transaction(['played', 'playlists', 'settings', 'catalogue'], 'readwrite');
    for (const row of rows) transaction.objectStore('played').put(row);
    transaction.objectStore('playlists').put({ id: 'kept', name: 'Playlist', tracks: [{ url: 'a' }] });
    transaction.objectStore('settings').put({ key: 'test', value: 'Setting' });
    transaction.objectStore('catalogue').put({ key: 'modland:meta', tracks: 42 });
    transaction.oncomplete = resolve; transaction.onabort = () => reject(transaction.error);
  });
  return db;
}
for (const version of [3, 4]) await test(`v${version} upgrade seeds old History once without changing user data`, async () => {
  const store = await fresh(), db = await seed(version); db.close();
  assert.deepEqual(await store.played.recent(), [{ ...oldRow, listened: true }]);
  assert.equal((await store.playlists.get('kept')).name, 'Playlist');
  assert.equal(await store.settings.get('test'), 'Setting'); assert.equal((await store.catalogue.get('modland:meta')).tracks, 42);
  const upgraded = await requestResult(indexedDB.open('protracktor'));
  assert.equal(upgraded.version, 5); assert.equal(upgraded.objectStoreNames.contains('bookmarks'), true); upgraded.close(); store.forget();
});
await test('empty old History stays empty and new rows do not inherit migration marks', async () => {
  const store = await fresh(), db = await seed(4, []); db.close(); assert.equal(await store.played.count(), 0);
  await store.played.record({ url: 'new', name: 'New', listened: true });
  assert.equal((await store.played.recent())[0].listened, false); store.forget();
});
await test('reopening v5 neither reseeds new rows nor restores cleared legacy rows', async () => {
  const store = await fresh(), db = await seed(4); db.close(); await store.played.clear();
  await store.played.record({ url: 'new', name: 'New' }); store.forget();
  const reopened = await import(`../web/src/store.js?history-test=${++moduleId}`);
  assert.equal(await reopened.played.count(), 1); assert.equal((await reopened.played.recent())[0].listened, false); reopened.forget();
});
await test('qualification preserves recency count and metadata and never creates a missing row', async () => {
  const store = await fresh(); await store.played.record(oldRow); const before = (await store.played.recent())[0];
  await store.played.qualify({ trackId: 'a', historyGeneration: 0 });
  assert.deepEqual((await store.played.recent())[0], { ...before, listened: true });
  await store.played.qualify({ trackId: 'missing', historyGeneration: 0 }); assert.equal(await store.played.count(), 1); store.forget();
});
await test('replay preserves earned marks while incrementing one row and improving metadata', async () => {
  const store = await fresh(); await store.played.record(oldRow); await store.played.qualify({ trackId: 'a', historyGeneration: 0 });
  await store.played.record({ ...oldRow, name: 'Better', listened: false });
  const row = (await store.played.recent())[0]; assert.equal(row.playCount, 2); assert.equal(row.listened, true); assert.equal(row.name, 'Better'); store.forget();
});
await test('Clear synchronously retires the ticket captured before a delayed open', async () => {
  const store = await fresh(), ticket = store.played.generation;
  await store.played.record(oldRow); const clear = store.played.clear();
  assert.equal(store.played.revision.clearing, true);
  const old = store.played.record({ url: 'late-old' }, ticket), newPlay = store.played.record({ url: 'new' });
  await Promise.all([clear, old, newPlay]); assert.deepEqual((await store.played.recent()).map((row) => row.url), ['new']); store.forget();
});
await test('presentation reads only requested IDs in batches of at most 500', async () => {
  const store = await fresh(); await store.played.record(oldRow);
  await store.played.record({ url: 'filename', name: 'file.mod', file: 'file.mod' });
  const get = IDBObjectStore.prototype.get; let reads = 0; const perTransaction = new Map();
  IDBObjectStore.prototype.get = function (...args) { if (this.name === 'played') { reads++; perTransaction.set(this.transaction, (perTransaction.get(this.transaction) ?? 0) + 1); } return get.apply(this, args); };
  let shown;
  try { shown = await store.played.presentationAmong(['a', 'filename', ...Array.from({ length: 1000 }, (_, i) => `missing${i}`)]); }
  finally { IDBObjectStore.prototype.get = get; }
  assert.deepEqual(shown.get('a'), { name: 'Actual title', listened: false }); assert.deepEqual(shown.get('filename'), { name: '', listened: false });
  assert.equal(shown.size, 2); assert.equal(reads, 1002); assert.equal(Math.max(...perTransaction.values()), 500);
  const abort = new AbortController(); abort.abort(); await assert.rejects(store.played.presentationAmong(['a'], abort.signal), { name: 'AbortError' }); store.forget();
});
await test('a blocked old tab can close and the production open path retries successfully', async () => {
  const store = await fresh(), db = await seed(4);
  await assert.rejects(store.played.count(), /another tab/); db.close();
  assert.equal(await store.played.count(), 1); assert.equal((await store.played.recent())[0].listened, true); store.forget();
});
await test('a denied open can retry without poisoning the storage singleton', async () => {
  const store = await fresh(), factory = indexedDB, original = factory.open;
  factory.open = () => { throw new DOMException('Denied test', 'SecurityError'); };
  await assert.rejects(store.played.count(), { name: 'SecurityError' }); factory.open = original;
  assert.equal(await store.played.count(), 0); store.forget();
});
await test('a newer schema is refused without deleting its History or playlists', async () => {
  const store = await fresh(), db = await seed(6); db.close();
  await assert.rejects(store.played.count(), { name: 'VersionError' });
  const retained = await requestResult(indexedDB.open('protracktor', 6));
  const transaction = retained.transaction(['played', 'playlists']);
  assert.deepEqual(await requestResult(transaction.objectStore('played').get('a')), oldRow);
  const next = retained.transaction('playlists'); assert.equal((await requestResult(next.objectStore('playlists').get('kept'))).name, 'Playlist'); retained.close(); store.forget();
});
await test('an interrupted migration rolls back both marks and schema and can be retried', async () => {
  const store = await fresh(), db = await seed(4); db.close(); const update = IDBCursor.prototype.update;
  IDBCursor.prototype.update = function (...args) { const request = update.apply(this, args); this.source.transaction.abort(); return request; };
  try { await assert.rejects(store.played.count(), { name: 'AbortError' }); } finally { IDBCursor.prototype.update = update; }
  const retained = await requestResult(indexedDB.open('protracktor', 4));
  assert.equal(retained.objectStoreNames.contains('bookmarks'), false);
  assert.deepEqual(await requestResult(retained.transaction('played').objectStore('played').get('a')), oldRow); retained.close();
  assert.equal((await store.played.recent())[0].listened, true); store.forget();
});
await test('a failed replay preserves the old row and the next record can recover', async () => {
  const store = await fresh(); await store.played.record(oldRow); const before = (await store.played.recent())[0];
  const errors = [], off = store.played.onFailure((error) => errors.push(error.name));
  const put = IDBObjectStore.prototype.put;
  IDBObjectStore.prototype.put = function (...args) { if (this.name === 'played') throw new DOMException('Quota test', 'QuotaExceededError'); return put.apply(this, args); };
  try { assert.equal(await store.played.record({ ...oldRow, name: 'Lost' }), false); }
  finally { IDBObjectStore.prototype.put = put; off(); }
  assert.deepEqual((await store.played.recent())[0], before); assert.deepEqual(errors, ['QuotaExceededError']);
  await store.played.record(oldRow); assert.equal((await store.played.recent())[0].playCount, 2); store.forget();
});
await test('two storage owners increment replay counts without read/write races', async () => {
  const store = await fresh();
  const other = await import(`../web/src/store.js?history-test=${++moduleId}`);
  await Promise.all([store.played.record(oldRow), other.played.record(oldRow), store.played.record(oldRow), other.played.record(oldRow)]);
  assert.equal((await store.played.recent())[0].playCount, 4); store.forget(); other.forget();
});
await test('learned titles persist before qualification across reopen and clear only with History', async () => {
  const store = await fresh();
  await store.played.record({ url: 'decoded', name: ' Decoded ', file: 'a.mod' });
  await store.played.record({ url: 'fallback', name: 'fallback.mod', file: 'fallback.mod' });
  await store.played.record({ url: 'blank', name: '  ', file: 'blank.mod' });
  const before = await store.played.recent(); store.forget();
  const reopened = await import(`../web/src/store.js?history-test=${++moduleId}`);
  assert.deepEqual(await reopened.played.presentationAmong(['decoded', 'fallback', 'blank', 'absent']),
    new Map([['decoded', { name: 'Decoded', listened: false }], ['fallback', { name: '', listened: false }], ['blank', { name: '', listened: false }]]));
  assert.deepEqual(await reopened.played.recent(), before);
  await reopened.played.clear(); assert.equal((await reopened.played.presentationAmong(['decoded', 'fallback', 'blank'])).size, 0); reopened.forget();
});
console.log(`${checks} History storage/migration cases passed.`);
