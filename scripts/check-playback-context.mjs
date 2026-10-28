// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PlayOrder, PlaybackContext, PlaybackWorkspace } from '../web/src/playback-context.js';

let checks = 0;
const test = (name, run) => {
  run();
  checks++;
  console.log(`✓ ${name}`);
};
const tracks = ['a', 'b', 'c'].map((id) => Object.freeze({ url: id, name: id.toUpperCase() }));
const random = () => 0.41;
const order = (values = tracks) => new PlayOrder({ tracks: values, random });
const context = (source = { kind: 'playlist', id: 'A' }, index = 0) =>
  new PlaybackContext({ source, order: order().startAt(index) });

const cases = fs.readFileSync(new URL('../docs/rules/playback-context-cases.tsv', import.meta.url), 'utf8')
  .split('\n').filter((line) => line.trim() && !line.startsWith('#'));
assert.equal(cases.shift(), 'case\tsource\tat\trepeat\taction\tstep\tcurrent');
assert.ok(cases.length > 0, 'No shared transport cases executed');
for (const line of cases) {
  const [name, kind, at, repeat, action, expectedStep, expectedCurrent] = line.split('\t');
  test(`Android/WEB shared transport: ${name}`, () => {
    const source = kind === 'playlist' ? { kind, id: 'A' } : kind === 'collection'
      ? { kind, origin: 'search', query: 'test' } : kind === 'random' ? { kind, session: 7 } : { kind };
    const sequence = order().withRepeat(repeat);
    const state = new PlaybackContext({ source, order: at === '-' ? sequence : sequence.startAt(Number(at)) });
    assert.ok(['next', 'previous', 'ended'].includes(action));
    const result = state[action]();
    assert.equal(result.kind, expectedStep);
    assert.equal(result.context?.order.currentIndex ?? null, expectedCurrent === '-' ? null : Number(expectedCurrent));
    if (result.context) assert.deepEqual(result.context.source, source);
  });
}

// Ported from ActiveQueueTest: finite playlist and captured-collection semantics.
for (const source of [{ kind: 'playlist', id: 'A' }, { kind: 'collection', origin: 'search', query: 'test' }]) {
  test(`${source.kind}: Next and Previous retain source identity`, () => {
    const first = context(source);
    const next = first.next();
    assert.equal(next.kind, 'play');
    assert.equal(next.context.current, tracks[1]);
    assert.deepEqual(next.context.source, source);
    assert.equal(next.context.previous().context.current, tracks[0]);
    assert.equal(first.select(2).ended().kind, 'stop');
    assert.equal(first.select(2).next().kind, 'unchanged');
    assert.equal(first.current, tracks[0]);
  });
}

// ActiveQueueTest: repeat-one is completion policy, never an explicit-Next trap.
for (const source of [{ kind: 'playlist', id: 'A' }, { kind: 'collection', origin: 'history' },
  { kind: 'random', session: 8 }, { kind: 'external' }]) {
  test(`${source.kind}: Repeat One replays only at completion`, () => {
    const q = context(source).withOrder(order().withRepeat('one').startAt(0));
    assert.equal(q.ended().kind, 'replay');
    assert.notEqual(q.next().kind, 'replay');
    if (source.kind === 'playlist' || source.kind === 'collection') assert.equal(q.next().context.current, tracks[1]);
  });
}

test('repeat-all wraps a collection without falling back to a playlist', () => {
  const q = context({ kind: 'collection', origin: 'folder', path: 'artist' }, 2);
  const next = q.withOrder(q.order.withRepeat('all')).ended();
  assert.equal(next.context.current, tracks[0]);
  assert.deepEqual(next.context.source, q.source);
});

test('Random asks its provider instead of traversing a dormant playlist', () => {
  const q = context({ kind: 'random', session: 7 });
  assert.equal(q.next().kind, 'random-next');
  assert.equal(q.ended().kind, 'random-next');
  assert.equal(q.previous().kind, 'unchanged');
  assert.equal(q.select(1).previous().kind, 'random-previous');
  assert.equal(q.hasNext, true);
});

test('external and empty sources have no finite neighbours', () => {
  for (const kind of ['external', 'none']) {
    const q = context({ kind });
    assert.equal(q.hasNext, false);
    assert.equal(q.hasPrevious, false);
    assert.equal(q.next().kind, 'unchanged');
    assert.equal(q.previous().kind, 'unchanged');
    assert.equal(q.ended().kind, 'stop');
  }
});

test('source identity is independent of the selected editor and track URL', () => {
  assert.equal(context().belongsTo('A'), true);
  assert.equal(context().belongsTo('B'), false);
  assert.equal(context({ kind: 'collection', origin: 'history' }).belongsTo('A'), false);
  assert.equal(context({ kind: 'collection', origin: 'history' }).fromHistory, true);
  assert.equal(context().fromHistory, false);
});

test('jumping inside a shuffled displayed order does not redeal it', () => {
  const q = order().withShuffle(true).startAt(2);
  const jumped = q.jumpTo(1);
  assert.deepEqual(jumped.indices, q.indices);
  assert.equal(jumped.current, tracks[1]);
  assert.equal(q.current, tracks[2]);
});

test('hand selection under shuffle starts a new traversal at the selected track', () => {
  const q = order().withShuffle(true).startAt(2).next().startAt(1);
  assert.equal(q.currentIndex, 1);
  assert.equal(q.indices[0], 1);
  assert.equal(q.hasPrevious, false);
  assert.equal(new Set(q.indices).size, 3);
});

test('shuffle Previous then Next returns through actual history', () => {
  const q = order().withShuffle(true).startAt(0).next().next();
  assert.equal(q.previous().next().current, q.current);
  assert.equal(q.previous().previous().current, tracks[0]);
});

test('unshuffled Previous and Next follow visible neighbours, not an old forward branch', () => {
  const q = order().startAt(2).startAt(0).next();
  assert.equal(q.current, tracks[1]);
  assert.equal(q.previous().current, tracks[0]);
  assert.equal(q.withRepeat('all').startAt(0).previous().current, tracks[2]);
});

test('replacing tracks remaps retained history by identity', () => {
  const q = order().withShuffle(true).startAt(0).jumpTo(2);
  const edited = q.withTracks([tracks[2], tracks[0]]);
  assert.equal(edited.current, tracks[2]);
  assert.equal(edited.previous().current, tracks[0]);
  assert.equal(q.currentIndex, 2);
});

test('learned titles preserve shuffle permutation and selection', () => {
  const q = order().withShuffle(true).startAt(1);
  const titled = q.withTracks(tracks.map((t) => ({ ...t, name: `Learned ${t.name}` })));
  assert.deepEqual(titled.indices, q.indices);
  assert.equal(titled.currentIndex, q.currentIndex);
  assert.equal(titled.current.name, 'Learned B');
});

test('empty and idle finite orders do not invent a current occurrence', () => {
  const q = new PlayOrder();
  assert.equal(q.current, null);
  assert.equal(q.hasNext, false);
  assert.equal(q.hasPrevious, false);
  assert.equal(q.next(), q);
  assert.equal(q.ended(), null);
  const idle = order();
  assert.equal(idle.current, null);
  assert.equal(idle.next().current, tracks[0]);
});

test('unknown source kinds and malformed occurrence identities are rejected', () => {
  assert.throws(() => new PlaybackContext({ source: { kind: 'forgotten' } }), TypeError);
  assert.throws(() => new PlaybackContext({ order: order(), entryIds: [0, 0, 1] }), TypeError);
  assert.throws(() => new PlayOrder({ repeat: 'forgotten' }), TypeError);
  assert.throws(() => order().startAt(-1), RangeError);
});

test('omission is immutable, skips source rows and can be restored', () => {
  const q = context();
  const omitted = q.omit(q.entryIds[1]);
  assert.equal(omitted.next().context.current, tracks[2]);
  assert.deepEqual(omitted.visibleIndices, [0, 2]);
  assert.deepEqual(q.visibleIndices, [0, 1, 2]);
  assert.equal(omitted.restore(q.entryIds[1]).next().context.current, tracks[1]);
});

// ActiveQueueTest C117: retained shuffle history may exceed source length.
test('omitted shuffle history is crossed with a history-plus-lap traversal budget', () => {
  let sequence = order().withShuffle(true).startAt(2);
  for (let i = 0; i < 12; i++) sequence = sequence.jumpTo(1);
  sequence = sequence.jumpTo(0);
  const source = new PlaybackContext({ source: { kind: 'playlist', id: 'A' }, order: sequence }).omit(1);
  assert.equal(source.hasPrevious, true);
  assert.equal(source.previous().context.current, tracks[2]);
  let before = sequence;
  for (let i = 0; i < 13; i++) before = before.previous();
  const forward = source.withOrder(before);
  assert.equal(forward.hasNext, true);
  assert.equal(forward.next().context.current, tracks[0]);
});

test('duplicate source occurrence IDs match FIFO across edits and metadata changes', () => {
  const initial = new PlaybackContext({ source: { kind: 'random', session: 1 },
    order: order([tracks[0], tracks[0], tracks[1]]) });
  const changed = initial.omit(1).withOrder(order([tracks[0], tracks[1], tracks[0], tracks[2]]));
  assert.deepEqual(changed.entryIds, [0, 2, 1, 3]);
  assert.deepEqual(changed.omitted, [1]);
  assert.equal(changed.nextEntryId, 4);
  const learned = changed.withOrder(changed.order.withTracks(changed.order.tracks.map((t) => ({ ...t, name: 'Learned' }))));
  assert.deepEqual(learned.entryIds, changed.entryIds);
});

const selectedA = () => new PlaybackWorkspace()
  .selectPlaylist({ id: 'A', name: 'Playlist A', tracks }).playPlaylist(0);

// PlaylistSessionState.switchPlaylist/writeSnapshot/discard and ActiveQueueTest ownership.
test('selecting B while A plays preserves A transport and leaves B unselected', () => {
  const a = selectedA();
  const b = a.selectPlaylist({ id: 'B', name: 'Playlist B', tracks: [tracks[2]] });
  assert.equal(b.context, a.context);
  assert.equal(b.context.current, tracks[0]);
  assert.equal(b.context.next().context.current, tracks[1]);
  assert.equal(b.editor.id, 'B');
  assert.equal(b.editor.order.current, null);
  assert.equal(b.context.belongsTo(b.editor.id), false);
});

test('only explicit playlist play transfers ownership to the selected playlist', () => {
  const b = selectedA().selectPlaylist({ id: 'B', tracks: [tracks[2]] });
  const playing = b.playPlaylist(0);
  assert.equal(playing.context.belongsTo('B'), true);
  assert.equal(playing.context.current, tracks[2]);
  assert.equal(playing.editor.order, playing.context.order);
  assert.equal(b.context.belongsTo('A'), true);
});

for (const source of [{ kind: 'collection', origin: 'history' }, { kind: 'collection', origin: 'search', query: 'x' },
  { kind: 'collection', origin: 'folder', path: 'author' }, { kind: 'random', session: 9 }]) {
  test(`${source.origin ?? source.kind} keeps ownership when a playlist is viewed`, () => {
    const playing = selectedA().playCollection(tracks, 1, source);
    const selected = playing.selectPlaylist({ id: 'B', tracks: [tracks[0]] });
    assert.equal(selected.context, playing.context);
    assert.equal(selected.context.current, tracks[1]);
    assert.equal(selected.context.fromHistory, source.origin === 'history');
  });
}

test('editing and discarding B never changes the independently playing source A', () => {
  const b = selectedA().selectPlaylist({ id: 'B', tracks: [tracks[1], tracks[2]] });
  const edited = b.editTracks([tracks[2]]);
  assert.equal(edited.context, b.context);
  assert.equal(edited.editor.dirty, true);
  const discarded = edited.editTracks([tracks[1], tracks[2]], { dirty: false });
  assert.equal(discarded.context, b.context);
  assert.equal(discarded.editor.dirty, false);
});

test('editing the selected source playlist keeps its order linked to transport', () => {
  const before = selectedA();
  const edited = before.editTracks([tracks[0], tracks[2]]);
  assert.equal(edited.context.order, edited.editor.order);
  assert.equal(edited.context.next().context.current, tracks[2]);
  assert.equal(before.context.next().context.current, tracks[1]);
});

test('a save snapshots the selected editor rather than its unrelated playback source', () => {
  const b = selectedA().selectPlaylist({ id: 'B', tracks: [tracks[2]] }).editTracks([tracks[2], tracks[1]]);
  const snapshot = b.saveSnapshot();
  assert.equal(snapshot.playlistId, 'B');
  assert.deepEqual(snapshot.tracks, [tracks[2], tracks[1]]);
  assert.equal(b.markSaved(snapshot).editor.dirty, false);
  assert.equal(b.context.current, tracks[0]);
});

test('a late save cannot clear a newer edit or mark another selected playlist saved', () => {
  const b = selectedA().editTracks([tracks[0], tracks[2]]);
  const snapshot = b.saveSnapshot();
  const later = b.editTracks([tracks[0]]);
  assert.equal(later.markSaved(snapshot), later);
  const elsewhere = b.selectPlaylist({ id: 'B', tracks }).editTracks([tracks[1]]);
  assert.equal(elsewhere.markSaved(snapshot), elsewhere);
});

test('transport advancing during a save does not make unchanged playlist contents dirty', () => {
  const edited = selectedA().editTracks([tracks[0], tracks[2]]);
  const snapshot = edited.saveSnapshot();
  const advancing = edited.activate(edited.context.next().context);
  assert.equal(advancing.context.current, tracks[2]);
  assert.equal(advancing.markSaved(snapshot).editor.dirty, false);
});

test('source navigation may reveal the live source draft instead of a stale disk list', () => {
  const active = selectedA().editTracks([tracks[0], tracks[2]]);
  const away = active.selectPlaylist({ id: 'B', tracks });
  const restored = away.selectPlaylist({ id: 'A', tracks }, { preservePlayback: true });
  assert.equal(restored.context, active.context);
  assert.deepEqual(restored.editor.order.tracks, [tracks[0], tracks[2]]);
  assert.equal(restored.editor.dirty, true);
});

test('all returned order lists and ownership snapshots resist accidental mutation', () => {
  const before = selectedA();
  assert.throws(() => before.editor.order.tracks.push(tracks[0]), TypeError);
  assert.throws(() => before.context.entryIds.push(9), TypeError);
  assert.throws(() => { before.editor.id = 'B'; }, TypeError);
  assert.throws(() => { before.context.source.id = 'B'; }, TypeError);
});

test('transport availability does not consume randomness or deal a new lap during rendering', () => {
  let calls = 0;
  const draw = () => { calls++; return 0.41; };
  const list = new PlayOrder({ tracks, random: draw }).withShuffle(true).withRepeat('all');
  const shuffled = list.startAt(0);
  const last = shuffled.copy({ history: [shuffled.indices.at(-1)], cursor: 0 });
  const active = new PlaybackContext({ source: { kind: 'playlist', id: 'A' }, order: last });
  const before = calls;
  for (let i = 0; i < 12; i++) { assert.equal(last.hasNext, true); assert.equal(active.hasNext, true); }
  assert.equal(calls, before);
  const next = active.next();
  assert.equal(next.kind, 'play');
  assert.ok(calls > before, 'the real advance, rather than a view read, deals the new lap');
});

test('an upcoming-file preview and actual Next share one fresh shuffled lap', () => {
  let calls = 0; const values = [0.99, 0.99, 0, 0];
  const last = new PlayOrder({ tracks, shuffle: true, repeat: 'all', indices: [0, 1, 2], history: [2], cursor: 0,
    random: () => values[calls++ % values.length] });
  const active = new PlaybackContext({ source: { kind: 'playlist', id: 'A' }, order: last });
  const preview = active.next(); const next = active.next();
  assert.equal(preview.context.current.url, 'a'); assert.equal(next.context.current.url, 'a'); assert.equal(calls, 2);
  assert.equal(last.current.url, 'c'); assert.equal(active.order, last); assert(Object.isFrozen(last));
});

test('learning titles retains a prepared new lap without another random draw', () => {
  let calls = 0;
  const last = new PlayOrder({ tracks, shuffle: true, repeat: 'all', indices: [0, 1, 2], history: [2], cursor: 0,
    random: () => { calls++; return 0.99; } });
  const preview = last.advanced();
  const learned = last.withTracks(tracks.map((track) => ({ ...track, name: 'Learned ' + track.url })));
  const next = learned.advanced();
  assert.equal(next.current.url, preview.current.url); assert.equal(next.current.name, 'Learned ' + next.current.url); assert.equal(calls, 2);
});

test('a prepared shuffled lap still plays each occurrence once before dealing the following lap', () => {
  let calls = 0;
  const last = new PlayOrder({ tracks, shuffle: true, repeat: 'all', indices: [0, 1, 2], history: [2], cursor: 0,
    random: () => { calls++; return 0.41; } });
  last.advanced(); let active = last.advanced();
  const visited = [active.current.url];
  for (let i = 0; i < 2; i++) { active = active.advanced(); visited.push(active.current.url); }
  assert.equal(new Set(visited).size, 3); assert.equal(calls, 2);
  active.advanced(); assert.equal(calls, 4);
  assert.equal(last.withRepeat('off').advanced(), null);
  const one = last.withRepeat('one'); assert.equal(one.ended(), one);
});

assert.ok(checks > 0);
console.log(`${checks} playback-context checks passed.`);
