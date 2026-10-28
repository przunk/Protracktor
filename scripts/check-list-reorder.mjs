// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PlayOrder, PlaybackContext, PlaybackWorkspace } from '../web/src/playback-context.js';
import { moveItems, moveEditorTrack } from '../web/src/list-reorder.js';
let checks = 0;
const test = (name, run) => { try { run(); } catch (error) { console.error('✗ ' + name); throw error; }
  checks++; console.log('✓ ' + name); };
const tracks = ['a', 'b', 'c', 'd'].map((name) => Object.freeze({ url: name, name }));
const workspace = (order = new PlayOrder({ tracks }).startAt(1)) => new PlaybackWorkspace({
  editor: { id: 'a', name: 'Draft', order, dirty: false, revision: 7 },
  context: new PlaybackContext({ source: { kind: 'playlist', id: 'a' }, order }) });
const rows = fs.readFileSync('docs/rules/playlist-reorder-cases.tsv', 'utf8').split('\n').filter((line) => line && !line.startsWith('#'));
assert.equal(rows.shift(), 'case\tcount\tat\tfrom\tto\trepeat\torder\tcurrent\tnext\tprevious'); assert.ok(rows.length);
for (const line of rows) test('shared native reorder: ' + line.split('\t')[0], () => {
  const [name, count, at, from, to, repeat, expected, current, next, previous] = line.split('\t');
  let order = new PlayOrder({ tracks: Array.from({ length: Number(count) }, (_, i) => ({ url: String(i) })), repeat });
  if (at !== '-') order = order.startAt(Number(at));
  const before = workspace(order), after = moveEditorTrack(before, Number(from), Number(to)), actual = after.editor.order;
  assert.equal(actual.tracks.map((row) => row.url).join() || '-', expected, name);
  for (const [value, wanted] of [[actual.currentIndex, current], [actual.next().currentIndex, next], [actual.previous().currentIndex, previous]])
    assert.equal(value == null ? '-' : String(value), wanted, name);
  assert.equal(actual.current, order.current); assert.equal(before.editor.order, order);
});
test('moving a visible B draft cannot edit or restart playing A', () => {
  const a = workspace(), b = a.selectPlaylist({ id: 'b', name: 'B', tracks });
  const after = moveEditorTrack(b, 0, 3);
  assert.equal(after.context, b.context); assert.equal(after.context, a.context);
  assert.equal(after.editor.dirty, true); assert.equal(after.editor.revision, b.editor.revision + 1);
  assert.deepEqual(after.editor.order.tracks, [tracks[1], tracks[2], tracks[3], tracks[0]]);
});
test('occurrence IDs, omitted rows, route, held bytes and actual history survive an active draft move', () => {
  const held = { url: 'held', data: new Uint8Array([1, 2, 3]).buffer };
  const order = new PlayOrder({ tracks: [held, ...tracks] }).startAt(0).next().next().previous();
  const before = workspace(order), context = before.context.copy({ omitted: [2], route: { path: ['here'] } });
  const after = moveEditorTrack(before.copy({ context }), 0, 4);
  assert.equal(after.context.current, context.current); assert.equal(after.context.currentEntryId, context.currentEntryId);
  assert.deepEqual(after.context.entryIds, [1, 2, 3, 4, 0]); assert.deepEqual(after.context.omitted, [2]);
  assert.equal(after.context.route, context.route); assert.equal(after.context.order.tracks[4].data, held.data);
  assert.deepEqual(after.editor.order.history, order.history.map((index) => index === 0 ? 4 : index - 1));
  assert.equal(after.editor.order.cursor, order.cursor); assert.equal(before.editor.dirty, false);
});
test('duplicate URLs and even identical object references keep their exact playing occurrence', () => {
  const duplicate = tracks[0], order = new PlayOrder({ tracks: [duplicate, tracks[1], duplicate, tracks[2]] }).startAt(2);
  const before = workspace(order), after = moveEditorTrack(before, 0, 3);
  assert.equal(after.editor.order.currentIndex, 1); assert.equal(after.context.currentEntryId, 2);
  assert.deepEqual(after.context.entryIds, [1, 2, 3, 0]); assert.equal(after.editor.order.current, duplicate);
});
test('shuffle re-deals from the current occurrence, retains played history and visits every remaining occurrence', () => {
  for (let seed = 1; seed <= 20; seed++) {
    let n = seed; const random = () => ((n = n * 16807 % 2147483647) - 1) / 2147483646;
    const order = new PlayOrder({ tracks, shuffle: true, random }).startAt(2).next().next().previous();
    const before = workspace(order), after = moveEditorTrack(before, 0, 3), moved = after.editor.order;
    assert.equal(moved.current, order.current); assert.equal(moved.indices[0], moved.currentIndex);
    assert.deepEqual(moved.history.map((index) => moved.tracks[index]), order.history.map((index) => order.tracks[index]));
    assert.equal(new Set(moved.indices).size, tracks.length); assert.equal(moved.cursor, order.cursor);
  }
});
test('invalid, fractional and same-slot moves are identity no-ops without dirtying a draft', () => {
  const before = workspace();
  for (const [from, to] of [[-1, 1], [0, 9], [NaN, 1], [1, 1], [0, 1.5]]) assert.equal(moveEditorTrack(before, from, to), before);
  assert.equal(moveItems(tracks, 0, 0), tracks);
});
assert.ok(checks > 0); console.log(`${checks} playlist-reorder domain checks passed`);
