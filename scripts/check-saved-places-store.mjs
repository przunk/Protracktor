// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { bookmarks, played, playlists, settings, catalogue, forget } from '../web/src/store.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const a = { key: { catalogueId: 'modland', format: 'Protracker', author: "O'Neil / A+B" }, catalogueName: 'Modland', savedAt: 1 };
const b = { ...a, key: { ...a.key, format: 'Fasttracker' }, savedAt: 2 };
const c = { ...a, key: { ...a.key, catalogueId: 'asma' }, savedAt: 3 };
const d = { ...a, key: { ...a.key, author: "o'neil / A+B" }, savedAt: 4 };
await test('exact composite identity and duplicate save preserve original metadata and time', async () => {
  for (const place of [a, b, c, d]) assert.equal(await bookmarks.save(place), true);
  assert.equal(await bookmarks.save({ ...a, catalogueName: 'Changed', savedAt: 100 }), false);
  assert.deepEqual(await bookmarks.list(), [d, c, b, a]);
});
await test('History and catalogue deletion/rebuild preserve all saved places and user documents', async () => {
  await playlists.save({ id: 'kept', name: 'Kept', tracks: [{ url: 'a' }] }); await settings.set('kept', true);
  await played.record({ url: 'a', name: 'A' }); await catalogue.putAll([{ key: 'modland:meta', tracks: 3 }]);
  await played.clear(); await catalogue.clear('modland:'); await catalogue.putAll([{ key: 'modland:meta', tracks: 4 }]);
  assert.deepEqual(await bookmarks.list(), [d, c, b, a]); assert.equal((await playlists.get('kept')).name, 'Kept'); assert.equal(await settings.get('kept'), true);
});
await test('reopening the production database retains exact bookmarks and order', async () => {
  forget(); const reopened = await import('../web/src/store.js?bookmarks-reopen');
  assert.deepEqual(await reopened.bookmarks.list(), [d, c, b, a]); reopened.forget();
});
await test('removal affects only the exact complete tuple', async () => {
  await bookmarks.remove(a.key); assert.deepEqual(await bookmarks.list(), [d, c, b]);
  assert.equal(await bookmarks.save(a), true);
});
await test('equal timestamps use native binary catalogue format and author tie order', async () => {
  const prefix = { catalogueName: 'Modland', savedAt: 20 };
  const names = ['😀', '\uE000', 'b', 'A', 'a'];
  for (const author of names) await bookmarks.save({ ...prefix, key: { catalogueId: 'modland', format: 'Tie', author } });
  assert.deepEqual((await bookmarks.list()).filter((place) => place.savedAt === 20).map((place) => place.key.author), ['A', 'a', 'b', '\uE000', '😀']);
});
await test('delimiter-like folder components empty author and whitespace remain distinct', async () => {
  const keys = [
    { catalogueId: 'modland', format: 'A/B', author: 'C' },
    { catalogueId: 'modland', format: 'A', author: 'B/C' },
    { catalogueId: 'modland', format: 'A', author: '' },
    { catalogueId: 'modland', format: 'A', author: ' ' },
  ];
  for (const key of keys) assert.equal(await bookmarks.save({ key, catalogueName: 'Modland', savedAt: 30 }), true);
  assert.equal((await bookmarks.list()).filter((place) => place.savedAt === 30).length, 4);
});
await test('quota failure rolls back a save without deleting existing bookmarks', async () => {
  const before = await bookmarks.list(), put = IDBObjectStore.prototype.put;
  IDBObjectStore.prototype.put = function (...args) { if (this.name === 'bookmarks') throw new DOMException('Quota test', 'QuotaExceededError'); return put.apply(this, args); };
  try { await assert.rejects(bookmarks.save({ ...a, key: { ...a.key, author: 'new' } }), { name: 'QuotaExceededError' }); }
  finally { IDBObjectStore.prototype.put = put; }
  assert.deepEqual(await bookmarks.list(), before);
});
await test('a failed remove leaves the saved record available for a later retry', async () => {
  const before = await bookmarks.list(), remove = IDBObjectStore.prototype.delete;
  IDBObjectStore.prototype.delete = function (...args) { if (this.name === 'bookmarks') throw new DOMException('Denied test', 'InvalidStateError'); return remove.apply(this, args); };
  try { await assert.rejects(bookmarks.remove(a.key), { name: 'InvalidStateError' }); }
  finally { IDBObjectStore.prototype.delete = remove; }
  assert.deepEqual(await bookmarks.list(), before); await bookmarks.remove(a.key);
  assert.equal((await bookmarks.list()).some((place) => JSON.stringify(place.key) === JSON.stringify(a.key)), false);
});
await test('simultaneous duplicate saves from two owners insert only once', async () => {
  const other = await import('../web/src/store.js?bookmarks-other');
  const place = { ...a, key: { ...a.key, author: 'concurrent' }, savedAt: 40 };
  assert.deepEqual((await Promise.all([bookmarks.save(place), other.bookmarks.save(place)])).sort(), [false, true]);
  assert.equal((await bookmarks.list()).filter((entry) => entry.key.author === 'concurrent').length, 1); other.forget(); forget();
});
console.log(`${checks} Saved places storage cases passed.`);
