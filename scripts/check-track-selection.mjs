// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { TrackSelection } from '../web/src/track-selection.js';
let checks = 0;
const test = (name, run) => { try { run(); } catch (error) { console.error('✗ ' + name); throw error; } checks++; console.log('✓ ' + name); };
const tracks = [0, 1, 2].map((i) => Object.freeze({ url: `https://modland.com/pub/modules/Protracker/Selection/${i}.mod`, name: `Selection ${i}` }));
const fixture = () => { const model = new TrackSelection(); model.replace('history', tracks); return model; };
test('native long-press add is idempotent; row/checkbox taps toggle without changing tracks', () => {
  const model = fixture(); assert.equal(model.start('history', tracks[2].url), true); model.start('history', tracks[2].url);
  assert.equal(model.count, 1); model.toggle('history', tracks[0].url); assert.equal(model.count, 2);
  model.toggle('history', tracks[2].url); assert.equal(model.has(tracks[2].url), false); assert.deepEqual(model.tracks, tracks);
});
test('native batch actions capture source order instead of the order of selecting rows and clear afterwards', () => {
  const model = fixture(); model.start('history', tracks[2].url); model.toggle('history', tracks[0].url);
  const captured = model.take(); assert.deepEqual(captured, [tracks[0], tracks[2]]); assert.equal(model.active, false);
  assert.ok(Object.isFrozen(captured) && captured.every(Object.isFrozen));
  model.replace('other', [...tracks].reverse()); assert.deepEqual(captured, [tracks[0], tracks[2]]);
});
test('native Select all/none includes only the current source and does not become a persistent mode', () => {
  const model = fixture(); model.start('history', tracks[1].url); model.toggleAll(); assert.equal(model.count, 3); assert.equal(model.all, true);
  model.toggleAll(); assert.equal(model.count, 0); assert.equal(model.active, false); assert.equal(model.all, false);
});
test('native same-owner membership changes intersect IDs, retain surviving selection and use new source order', () => {
  const model = fixture(); model.start('history', tracks[2].url); model.toggle('history', tracks[0].url);
  model.replace('history', [tracks[2], tracks[0], { url: 'new', name: 'New' }]);
  assert.deepEqual(model.capture(), [tracks[2], tracks[0]]); assert.equal(model.all, false);
  model.replace('history', tracks.slice(0, 2)); assert.deepEqual(model.capture(), [tracks[0]]);
  model.replace('history', []); assert.equal(model.active, false);
});
test('native route/query changes retire selection and an obsolete row cannot toggle a newer owner', () => {
  const model = fixture(); model.start('history', tracks[0].url); model.replace('search-new-query', tracks);
  assert.equal(model.active, false); assert.equal(model.start('history', tracks[0].url), false);
  assert.equal(model.toggle('history', tracks[0].url), false); assert.equal(model.start('search-new-query', 'unknown'), false);
});
test('capture preserves held-buffer ownership without transferring bytes or mutating source references', () => {
  const data = new Uint8Array([1, 2, 3]).buffer, track = { url: 'held', name: 'Held', data }, model = new TrackSelection();
  model.replace('folder', [track]); model.start('folder', 'held'); const snapshot = model.capture();
  assert.equal(snapshot[0].data, data); assert.equal(data.byteLength, 3); assert.equal(Object.isFrozen(track), false);
  model.clear(); assert.equal(snapshot[0].data.byteLength, 3);
});
assert.ok(checks > 0); console.log(`${checks} native-derived selection model checks passed`);
