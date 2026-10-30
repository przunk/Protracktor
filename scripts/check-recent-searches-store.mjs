// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
await import('../web/node_modules/fake-indexeddb/auto/index.mjs');
import { settings, playlists, played, forget } from '../web/src/store.js';
import { RecentSearchHistory } from '../web/src/recent-searches.js';

let checks = 0;
const test = async (name, run) => { await run(); checks++; console.log('✓ ' + name); };
const history = () => new RecentSearchHistory({ read: () => settings.get('recentSearches', []),
  write: (queries) => settings.set('recentSearches', queries) });
await test('new settings key leaves existing playlists and other preferences untouched', async () => {
  await settings.set('randomScope', 'atari8'); await playlists.save({ id: 'kept', name: 'Kept', tracks: [{ url: 'https://example.test/a.mod' }] });
  const recent = history(); await recent.record('Żółw'); await recent.record('second');
  assert.deepEqual(await settings.get('recentSearches'), ['second', 'Żółw']);
  assert.equal(await settings.get('randomScope'), 'atari8'); assert.equal((await playlists.get('kept')).tracks.length, 1);
});
await test('reopening the real IndexedDB store restores exact retained spelling and order', async () => {
  forget(); const recent = history(); await recent.ready;
  assert.deepEqual(recent.queries, ['second', 'Żółw']); await recent.record(' SECOND ');
  forget(); const restored = history(); await restored.ready; assert.deepEqual(restored.queries, ['SECOND', 'Żółw']);
});
await test('Clear History does not clear recent search preferences', async () => {
  await played.clear(); assert.deepEqual(await settings.get('recentSearches'), ['SECOND', 'Żółw']);
});
await test('corrupt typed preference values have an empty safe presentation without deleting data', async () => {
  await settings.set('recentSearches', { retained: 'not an array' }); const recent = history(); await recent.ready;
  assert.deepEqual(recent.queries, []); assert.deepEqual(await settings.get('recentSearches'), { retained: 'not an array' });
  await recent.record('new'); assert.deepEqual(await settings.get('recentSearches'), ['new']);
});
forget(); console.log(`${checks} recent-search IndexedDB cases passed.`);
