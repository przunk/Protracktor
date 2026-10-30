// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { catalogue } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';
import { savedPlaceRows, resolveSavedPlace } from '../web/src/saved-places-catalogue.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
const key = { catalogueId: 'modland', format: 'Protracker', author: 'Artist' };
const place = (key) => ({ key, catalogueName: 'Modland', savedAt: 1 });
const seed = async (text = '1\tProtracker/Artist/a.mod\n1\tProtracker/Other/b.mod', playable = () => true) => {
  const built = archive.toRecords(text, 'modland', playable);
  await catalogue.replacePrefix('modland:', [...built.records, { key: 'modland:meta', tracks: built.tracks, total: built.total, complete: true }]);
};
await test('availability reads local group summaries once per format without reading tracks or network', async () => {
  await seed(); const get = catalogue.get, fetch = globalThis.fetch, keys = [];
  catalogue.get = (key) => { keys.push(key); return get(key); };
  globalThis.fetch = () => assert.fail('Bookmark availability cannot fetch');
  try {
    const rows = await savedPlaceRows([place(key), place({ ...key, author: 'Other' })]);
    assert.deepEqual(rows.map((row) => row.availability), ['available', 'available']);
  } finally { catalogue.get = get; globalThis.fetch = fetch; }
  assert.equal(keys.filter((key) => key.endsWith(':meta')).length, 1);
  assert.equal(keys.filter((key) => key.includes(':authors:')).length, 1); assert.equal(keys.some((key) => key.includes(':tracks:')), false);
});
await test('exact-case missing groups differ from unsupported catalogues and missing indexes', async () => {
  const rows = await savedPlaceRows([place({ ...key, author: 'artist' }), place({ ...key, catalogueId: 'removed' }), place({ ...key, catalogueId: 'asma' })]);
  assert.deepEqual(rows.map((row) => row.availability), ['group-missing', 'catalogue-unavailable', 'index-missing']);
});
await test('an indexed archive with zero playable tracks reports group absence rather than a missing index', async () => {
  await seed('1\tProtracker/Artist/a.mod', () => false);
  assert.equal((await savedPlaceRows([place(key)]))[0].availability, 'group-missing');
});
await test('Open always resolves current contents and never a frozen saved track list', async () => {
  await seed(); assert.equal((await resolveSavedPlace(key)).tracks[0].name, 'a.mod');
  await seed('1\tProtracker/Artist/new.mod'); assert.equal((await resolveSavedPlace(key)).tracks[0].name, 'new.mod');
  await catalogue.clear('modland:'); assert.equal((await resolveSavedPlace(key)).reason, 'index-missing');
});
await test('a late track read retries against a newer complete index before returning a destination', async () => {
  await seed(); const get = catalogue.get, entered = deferred(), release = deferred(); let held = false;
  catalogue.get = async (name) => { const value = await get(name); if (name.includes(':tracks:') && !held) { held = true; entered.resolve(); await release.promise; } return value; };
  try {
    const opening = resolveSavedPlace(key); await entered.promise; await seed('1\tProtracker/Artist/replaced.mod'); release.resolve();
    assert.equal((await opening).tracks[0].name, 'replaced.mod');
  } finally { release.resolve(); catalogue.get = get; }
});
await test('cancelling a delayed metadata read stops before reading folder contents', async () => {
  const get = catalogue.get, entered = deferred(), release = deferred(), abort = new AbortController(); let tracks = 0;
  catalogue.get = async (name) => { if (name.includes(':tracks:')) tracks++; const value = await get(name); if (name.endsWith(':meta')) { entered.resolve(); await release.promise; } return value; };
  try {
    const opening = resolveSavedPlace(key, abort.signal); await entered.promise; abort.abort(); release.resolve();
    await assert.rejects(opening, { name: 'AbortError' }); assert.equal(tracks, 0);
  } finally { release.resolve(); catalogue.get = get; }
});
await test('storage failures are errors rather than invented unavailable folder results', async () => {
  const get = catalogue.get; catalogue.get = async () => { throw Error('Read denied'); };
  try { await assert.rejects(resolveSavedPlace(key), /Read denied/); await assert.rejects(savedPlaceRows([place(key)]), /Read denied/); }
  finally { catalogue.get = get; }
});
await test('continuously replaced indexes cannot publish a mixed destination', async () => {
  const get = catalogue.get;
  catalogue.get = async (name) => { const value = await get(name); if (name.endsWith(':meta')) await catalogue.putAll([{ ...value, key: name, changed: Math.random() }]); return value; };
  try { await assert.rejects(resolveSavedPlace(key), /catalogue changed/); } finally { catalogue.get = get; }
});
console.log(`${checks} Saved-place catalogue/ownership cases passed.`);
