// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { IDBFactory } from '../web/node_modules/fake-indexeddb/build/esm/index.js';
import { catalogue, playlists, played, settings, bookmarks, forget } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';
import { searchCatalogueTracks } from '../web/src/library-search-store.js';

globalThis.indexedDB = new IDBFactory();
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
async function seed(source, paths, accepts = () => true) {
  await catalogue.clear(`${source}:`);
  const parsed = archive.toRecords(paths.map((path) => `1\t${path}`).join('\n'), source, accepts);
  await catalogue.putAll([...parsed.records, { key: `${source}:meta`, tracks: parsed.tracks, total: parsed.total,
    complete: true, at: 123, platformCounts: parsed.platformCounts, formats: parsed.formats }]);
}
await test('production query ANDs words across title/author and folds accents without changing stable URL', async () => {
  await seed('modland', ['Protracker/4-Mat/space_ninja.mod', 'Protracker/4-Mat/different.mod', 'SNDH/Michał/dźwięk.sndh']);
  const found = await searchCatalogueTracks('4-mat space', [], [], 2000);
  assert.equal(found.total, 1); assert.equal(found.rows[0].url, archive.urlFor('Protracker', '4-Mat', 'space_ninja.mod', 'modland'));
  const unicode = await searchCatalogueTracks('michal dzwiek', [], [], 2000);
  assert.equal(unicode.rows.length, 1); assert.equal(unicode.rows[0].name, 'dźwięk.sndh');
});
await test('blank and one-letter queries are valid and explicitly chosen archives exclude others', async () => {
  await seed('asma', ['Composers/Rogers/Quest_For_Cool.sap']);
  assert.equal((await searchCatalogueTracks('', [], [], 2000)).total, 4);
  assert.equal((await searchCatalogueTracks('s', ['asma'], [], 2000)).rows.length, 1);
  assert.ok((await searchCatalogueTracks('', ['asma'], [], 2000)).rows.every((row) => row.url.startsWith('https://asma.atari.org/')));
  assert.equal((await searchCatalogueTracks('', ['amp'], [], 2000)).rows.length, 0);
});
await test('platform scope uses whole-archive ASMA and native directory/filename classification', async () => {
  assert.equal((await searchCatalogueTracks('', [], ['atari-8bit'], 2000)).total, 1);
  assert.deepEqual((await searchCatalogueTracks('', [], ['amiga'], 2000)).rows.map((row) => row.name), ['different.mod', 'space_ninja.mod']);
});
await test('unplayable retained rows do not enter Search and special symbols stay literal', async () => {
  await seed('modland', ['Protracker/Author/a%_!.mod', 'Protracker/Author/ordinary.mod', 'Custom/Author/no.cust'], (file) => !file.endsWith('.cust'));
  const found = await searchCatalogueTracks('%_!', ['modland'], [], 2000);
  assert.equal(found.total, 1); assert.equal(found.rows[0].name, 'a%_!.mod');
  assert.equal((await searchCatalogueTracks('', ['modland'], [], 2000)).total, 2);
});
await test('global title cap counts every match and returns the true first 2000 across archives without bulk reads', async () => {
  await seed('modland', Array.from({ length: 2407 }, (_, i) => `Protracker/Author/${String(2406 - i).padStart(4, '0')}.mod`));
  await seed('asma', ['Composers/Author/0000.sap']);
  const getAll = IDBObjectStore.prototype.getAll;
  IDBObjectStore.prototype.getAll = () => { throw new Error('Unbounded bulk read'); };
  try {
    const found = await searchCatalogueTracks('', [], [], 2000);
    assert.equal(found.total, 2408); assert.equal(found.rows.length, 2000);
    assert.deepEqual(found.rows.slice(0, 3).map((row) => row.name), ['0000.mod', '0000.sap', '0001.mod']);
    assert.equal(found.rows.at(-1).name, '1998.mod');
  } finally { IDBObjectStore.prototype.getAll = getAll; }
});
await test('metadata and all chosen title cursors share one readonly transaction', async () => {
  const transaction = IDBDatabase.prototype.transaction, seen = [];
  IDBDatabase.prototype.transaction = function (...args) { const result = transaction.apply(this, args); seen.push(result); return result; };
  try {
    const found = await searchCatalogueTracks('', [], [], 2000);
    assert.equal(seen.length, 1); assert.equal(seen[0].mode, 'readonly'); assert.equal(found.held.length, 2);
  } finally { IDBDatabase.prototype.transaction = transaction; }
});
await test('cancelled cursor and invalid shard reject; a later query recovers without deleting data', async () => {
  const controller = new AbortController(), next = IDBCursor.prototype.continue; let visited = 0;
  IDBCursor.prototype.continue = function (...args) { if (++visited === 1) { controller.abort(); return; } return next.apply(this, args); };
  try { await assert.rejects(searchCatalogueTracks('', [], [], 2000, controller.signal), { name: 'AbortError' }); }
  finally { IDBCursor.prototype.continue = next; }
  await catalogue.putAll([{ key: 'modland:titles:bad', entries: null }]);
  await assert.rejects(searchCatalogueTracks('', ['modland'], [], 2000), /Unreadable catalogue title shard/);
  assert.equal((await catalogue.get('modland:meta')).tracks, 2407);
  await seed('modland', ['Protracker/Author/recovered.mod']);
  assert.equal((await searchCatalogueTracks('', ['modland'], [], 2000)).rows[0].name, 'recovered.mod');
});
await test('query leaves playlists History preferences bookmarks and retained indexes unchanged', async () => {
  await playlists.save({ id: 'saved', name: 'Saved', tracks: [{ url: 'kept', name: 'Kept' }] });
  await played.record({ url: 'played', name: 'Played' }); await settings.set('existing', 'value');
  const before = { playlist: await playlists.get('saved'), history: await played.recent(), bookmarks: await bookmarks.list(), meta: await catalogue.get('modland:meta') };
  await searchCatalogueTracks('recovered', [], [], 2000);
  assert.deepEqual({ playlist: await playlists.get('saved'), history: await played.recent(), bookmarks: await bookmarks.list(), meta: await catalogue.get('modland:meta') }, before);
  assert.equal(await settings.get('existing'), 'value');
});
forget();
console.log(`${checks} library search storage checks passed.`);
