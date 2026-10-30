// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { SavedPlaces, savedPlaceId, saveablePlace, savedPlaceReason } from '../web/src/saved-places.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
const key = { catalogueId: 'modland', format: 'Protracker', author: 'Artist' };
const place = { key, catalogueName: 'Modland', savedAt: 1 };
const destination = { tracks: [{ url: 'a', name: 'A' }], meta: { tracks: 1 } };
class Store {
  rows = new Map(); fail = false; saveGate = null;
  async save(row) { await this.saveGate?.promise; if (this.fail) throw Error('failure'); const id = savedPlaceId(row.key); if (this.rows.has(id)) return false; this.rows.set(id, row); return true; }
  async list() { return [...this.rows.values()]; }
  async remove(key) { if (this.fail) throw Error('failure'); this.rows.delete(savedPlaceId(key)); }
}
const make = (options = {}) => {
  const store = new Store(), notices = [], opened = [], removed = [];
  const controller = new SavedPlaces({ store, notice: (notice) => notices.push(notice),
    availability: (places) => places.map((place) => ({ place, availability: 'available' })),
    resolve: () => destination, show: (key, contents) => opened.push({ key, contents }), isShowingList: () => true,
    removed: (undo) => removed.push(undo), ...options });
  return { store, notices, opened, removed, controller };
};
await test('save list duplicate and removal never resolve or navigate', async () => {
  const state = make({ resolve: () => assert.fail('Must not resolve') });
  await state.controller.refresh(); assert.equal(state.controller.state.loading, false);
  await state.controller.save(place); await state.controller.save({ ...place, savedAt: 9 });
  assert.deepEqual(state.controller.state.rows.map((row) => row.place), [place]);
  await state.controller.remove(key); assert.equal(state.controller.state.rows.length, 0);
  assert.deepEqual(state.notices, ['saved', 'already-saved']); assert.equal(state.opened.length, 0);
  state.store.fail = true; await state.controller.save(place); assert.equal(state.notices.at(-1), 'failed'); assert.equal(state.controller.state.writing, false);
});
await test('cancelled noncancellable location read cannot navigate after Back', async () => {
  const gate = deferred(), state = make({ resolve: async () => { await gate.promise; return destination; } });
  const opening = state.controller.open(key); state.controller.cancelOpening(); gate.resolve(); await opening;
  assert.deepEqual(state.opened, []); assert.equal(state.controller.state.opening, null);
});
await test('list removal offers one-shot Undo restoring the original record after navigation', async () => {
  let inList = true; const state = make({ isShowingList: () => inList }); await state.store.save(place);
  await state.controller.remove(key); const undo = state.removed[0]; assert.equal(typeof undo, 'function');
  inList = false; await undo(); assert.deepEqual(state.store.rows.get(savedPlaceId(key)), place);
  await state.controller.remove(key); assert.equal(state.removed.at(-1), null); await undo(); assert.equal(state.store.rows.size, 0);
});
await test('each Undo owns its removal and never overwrites a re-saved bookmark', async () => {
  const state = make(), other = { ...place, key: { ...key, format: 'Other' }, savedAt: 2 };
  await state.store.save(place); await state.store.save(other); await state.controller.remove(key); await state.controller.remove(other.key);
  const again = { ...place, savedAt: 99 }; await state.controller.save(again);
  await state.removed[0](); await state.removed[1]();
  assert.deepEqual(state.store.rows.get(savedPlaceId(key)), again); assert.deepEqual(state.store.rows.get(savedPlaceId(other.key)), other);
});
await test('Undo queues behind a busy write instead of dropping restoration', async () => {
  const state = make(); await state.store.save(place); await state.controller.remove(key);
  const other = { ...place, key: { ...key, author: 'Other' } }; const gate = state.store.saveGate = deferred();
  const save = state.controller.save(other), undo = state.removed[0](); assert.equal(state.controller.state.writing, true);
  gate.resolve(); await Promise.all([save, undo]); assert.equal(state.store.rows.size, 2); assert.equal(state.controller.state.writing, false);
});
await test('failed removal preserves its record and offers no Undo', async () => {
  const state = make(); await state.store.save(place); state.store.fail = true;
  await state.controller.remove(key); assert.deepEqual(state.store.rows.get(savedPlaceId(key)), place);
  assert.deepEqual(state.notices, ['failed']); assert.equal(state.removed.length, 0); assert.equal(state.controller.state.writing, false);
});
await test('missing group updates availability without deleting or navigating', async () => {
  const state = make({ resolve: () => ({ reason: 'group-missing' }) }); await state.store.save(place); await state.controller.refresh();
  await state.controller.open(key); assert.equal(state.controller.state.rows[0].availability, 'group-missing');
  assert.deepEqual(state.store.rows.get(savedPlaceId(key)), place); assert.deepEqual(state.notices, ['unavailable']); assert.deepEqual(state.opened, []);
});
await test('opening uses resolved current contents only while the Saved list is showing', async () => {
  let inList = true; const state = make({ isShowingList: () => inList });
  await state.controller.open(key); assert.deepEqual(state.opened, [{ key, contents: destination }]);
  inList = false; await state.controller.open(key); assert.equal(state.opened.length, 1);
});
await test('save action exists only for supported exact author/game paths including empty author', () => {
  assert.deepEqual(saveablePlace(['modland', 'Protracker', ''], ['modland', 'asma']), { ...key, author: '' });
  for (const path of [[], ['modland', 'Protracker'], ['search'], ['history'], ['modarchive', 'Group', 'Author'], ['removed', 'Protracker', 'Artist']]) assert.equal(saveablePlace(path, ['modland', 'asma']), null);
});
await test('exact tuple identity distinguishes delimiters case whitespace and catalogue', () => {
  const keys = [key, { ...key, author: 'artist' }, { ...key, author: ' Artist' }, { ...key, catalogueId: 'asma' },
    { ...key, format: 'Protracker/A', author: 'B' }, { ...key, format: 'Protracker', author: 'A/B' }];
  assert.equal(new Set(keys.map(savedPlaceId)).size, keys.length);
});
await test('source availability uses all indexed rows rather than playable rows alone', () => {
  assert.equal(savedPlaceReason(key, { tracks: 0, total: 12 }, true), null);
  assert.equal(savedPlaceReason(key, { tracks: 0, total: 0 }, true), 'index-missing');
  assert.equal(savedPlaceReason(key, { tracks: 1 }, false), 'catalogue-unavailable');
});
await test('a newer Open owns navigation and busy state even when the older resolve ignores abort', async () => {
  const old = deferred(), fresh = deferred();
  const otherKey = { ...key, author: 'Other' };
  const state = make({ resolve: (placeKey) => placeKey.author === 'Artist' ? old.promise : fresh.promise });
  const a = state.controller.open(key), b = state.controller.open(otherKey);
  old.resolve(destination); await a; assert.equal(state.controller.state.opening.author, 'Other'); assert.equal(state.opened.length, 0);
  fresh.resolve(destination); await b; assert.deepEqual(state.opened.map((row) => row.key), [otherKey]); assert.equal(state.controller.state.opening, null);
});
await test('save and Undo keep immutable snapshots of their original keys and metadata', async () => {
  const state = make(), mutable = { ...place, key: { ...key } };
  const saving = state.controller.save(mutable); mutable.key.author = 'Changed'; mutable.savedAt = 99; await saving;
  assert.deepEqual(state.controller.state.rows[0].place, place);
  assert.throws(() => { state.controller.state.rows[0].place.key.author = 'Other'; }, TypeError);
  await state.controller.remove(key); await state.removed[0]();
  assert.deepEqual(state.store.rows.get(savedPlaceId(key)), place);
});
console.log(`${checks} Saved places domain cases passed.`);
