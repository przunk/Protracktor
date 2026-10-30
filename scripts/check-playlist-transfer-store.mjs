// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { playlists } from '../web/src/store.js';
let checks = 0, notifications = 0;
const unsubscribe = playlists.subscribe(() => notifications++);
const test = async (name, run) => { try { await run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
try {
  await test('an imported identity commits all references atomically and notifies only after commit', async () => {
    const first = { id: 'transfer-storage', name: 'Original', tracks: [{ url: 'https://example.org/a.mod', name: 'A' }] };
    await playlists.create(first);
    assert.equal(notifications, 1); const stored = await playlists.get(first.id);
    assert.equal(stored.name, first.name); assert.deepEqual(stored.tracks, first.tracks); assert.ok(stored.updatedAt > 0);
  });
  await test('a duplicate create rejects with the real request error and cannot replace any existing fields', async () => {
    const before = await playlists.get('transfer-storage'), count = notifications;
    await assert.rejects(playlists.create({ id: before.id, name: 'Overwritten', tracks: [] }), (error) => error?.name === 'ConstraintError');
    assert.deepEqual(await playlists.get(before.id), before); assert.equal(notifications, count);
  });
  await test('an invalid imported identity rejects without committing or notifying and a later import still works', async () => {
    const before = notifications;
    await assert.rejects(playlists.create({ name: 'Missing identity', tracks: [] }), (error) => error?.name === 'DataError');
    assert.equal(notifications, before);
    await playlists.create({ id: 'transfer-recovery', name: 'Recovery', tracks: [] });
    assert.equal((await playlists.get('transfer-recovery')).name, 'Recovery'); assert.equal(notifications, before + 1);
  });
} finally { unsubscribe(); }
assert.ok(checks > 0); console.log(`${checks} production playlist-creation storage checks passed`);
