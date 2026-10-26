// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Ports of PlaybackScheduleTest and PlaybackPlanTest at Android baseline 3183eee.
import assert from 'node:assert/strict';
import { PlayOrder, PlaybackContext } from '../web/src/playback-context.js';
import { PlaybackSchedule, PlaybackPlan } from '../web/src/playback-schedule.js';

const track = (name) => Object.freeze({ url: name, name });
const source = (index = 1, repeat = 'off') => new PlaybackContext({ source: { kind: 'playlist', id: 1 },
  order: new PlayOrder({ tracks: ['A', 'B', 'C', 'D'].map(track), repeat, random: () => 0.3 }).startAt(index) });
const start = (context) => new PlaybackSchedule().contextSelected(context, 'replace');
const names = (state, context) => state.rows(context).map((row) => row.track.url);
const pendingNames = (state) => state.pending.map((entry) => entry.track.url);
const selected = (step) => { assert.equal(step.kind, 'select'); return step.schedule; };
let checks = 0;
const check = (name, run) => {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};

check('pending reorder moves duplicate occurrences both ways and retains origin', () => {
  const state = start(source()).enqueue(track('X'), true).enqueue(track('X')).enqueue(track('Y'));
  const [a, b, c] = state.pending;
  const moved = state.moveRequest(c.id, a.id);
  assert.deepEqual(moved.pending, [c, a, b]);
  assert.deepEqual(moved.moveRequest(c.id, b.id), state);
  assert.equal(moved.current, state.current);
  assert.equal(moved.nextId, state.nextId);
  assert.equal(moved.generation, state.generation);
  assert.equal(moved.next(source()).occurrence.request, c);
});
check('reorder rejects current, consumed, same and stale targets', () => {
  const state = selected(start(source()).enqueue(track('X')).enqueue(track('Y')).enqueue(track('Z')).next(source()));
  const first = state.pending[0].id, last = state.pending.at(-1).id;
  for (const [from, to] of [[state.current.id, last], [first, state.current.id], [-1, first], [first, -1], [first, first]])
    assert.equal(state.moveRequest(from, to), state);
  assert.equal(state.moveRequest(last, first).next(source()).occurrence.track.url, 'Z');
});
check('FIFO sits after the parked source and consumed rows disappear', () => {
  const ctx = source(), state = start(ctx).enqueue(track('X')).enqueue(track('Y'));
  const x = selected(state.next(ctx)), y = selected(x.next(ctx));
  assert.deepEqual(names(state, ctx), ['A', 'B', 'X', 'Y', 'C', 'D']);
  assert.deepEqual(names(x, ctx), names(state, ctx));
  assert.deepEqual(names(y, ctx), ['A', 'B', 'Y', 'C', 'D']);
  assert.equal(ctx.current.url, 'B');
  const after = y.next(ctx).step.context;
  assert.equal(after.current.url, 'C');
  assert.deepEqual(names(y.contextSelected(after, 'append'), after), ['A', 'B', 'C', 'D']);
});
check('Previous from any manual occurrence returns the parked source', () => {
  const ctx = source();
  const x = selected(start(ctx).enqueue(track('X')).enqueue(track('Y')).next(ctx));
  const y = selected(x.next(ctx));
  assert.equal(y.previous(ctx).step.context, ctx);
  assert.equal(y.contextSelected(ctx, 'prepend').next(ctx).step.context.current.url, 'C');
});
check('Previous retains waiting requests but never revives the consumed request', () => {
  const ctx = source();
  const manual = selected(start(ctx).enqueue(track('X')).enqueue(track('Y')).next(ctx));
  const returned = manual.contextSelected(ctx, 'prepend');
  assert.deepEqual(pendingNames(returned), ['Y']);
  assert.equal(returned.next(ctx).occurrence.track.url, 'Y');
  assert.ok(!names(returned, ctx).includes('X'));
});
check('duplicate files have unique manual keys and do not change the source', () => {
  const ctx = source(), state = start(ctx).enqueue(track('B')).enqueue(track('B'));
  assert.notEqual(state.pending[0].id, state.pending[1].id);
  assert.deepEqual(names(state, ctx), ['A', 'B', 'B', 'B', 'C', 'D']);
  assert.equal(new Set(state.rows(ctx).map((row) => row.key)).size, 6);
  assert.equal(ctx.order.tracks.length, 4);
});
check('selecting a later manual row retains earlier waiting requests', () => {
  const ctx = source(), state = start(ctx).enqueue(track('X')).enqueue(track('Y'));
  assert.deepEqual(names(state.playRequest(state.pending.at(-1).id, ctx).schedule, ctx), ['A', 'B', 'Y', 'X', 'C', 'D']);
  assert.equal(state.playRequest(-1, ctx), null);
});
check('source replacement starts its chosen row and preserves the waiting FIFO', () => {
  const ctx = source(), manual = selected(start(ctx).enqueue(track('X')).enqueue(track('Y')).next(ctx));
  const next = source(2), changed = manual.contextSelected(next, 'replace');
  assert.equal(changed.current.track.url, 'C');
  assert.deepEqual(names(changed, next), ['A', 'B', 'C', 'Y', 'D']);
  assert.ok(changed.generation > manual.generation);
  assert.equal(changed.resetTraversal().pending, changed.pending);
});
check('Repeat One repeats completion, whereas explicit Next consumes the next manual request', () => {
  const ctx = source(1, 'one'), manual = selected(start(ctx).enqueue(track('X')).enqueue(track('Y')).next(ctx));
  assert.equal(manual.next(ctx, true).step.kind, 'replay');
  assert.equal(manual.next(ctx).occurrence.track.url, 'Y');
});
check('Repeat All wraps only the source, never consumed manual requests', () => {
  const ctx = source(3, 'all'), manual = selected(start(ctx).enqueue(track('X')).next(ctx));
  assert.equal(manual.next(ctx, true).step.context.current.url, 'A');
  assert.equal(manual.pending.length, 0);
});
check('idle manual playback invents no source and hides a completed row', () => {
  const draft = new PlaybackContext({ source: { kind: 'playlist', id: 1 }, order: new PlayOrder({ tracks: [track('A')] }) });
  const step = new PlaybackSchedule().enqueue(track('X')).next(draft);
  const ctx = step.schedule.contextForSelection(step.occurrence, draft);
  assert.equal(ctx.source.kind, 'none');
  assert.equal(step.schedule.next(ctx, true).step.kind, 'stop');
  assert.deepEqual(names(step.schedule, ctx), ['X']);
  assert.deepEqual(names(step.schedule.finishCurrent(), ctx), []);
  assert.deepEqual(names(step.schedule.finishCurrent().resumeCurrent(), ctx), ['X']);
  assert.equal(step.schedule.hasPrevious(ctx), false);
});
check('History origin follows the actual request instead of its parked source', () => {
  const ctx = source().copy({ source: { kind: 'collection', origin: 'history' } });
  assert.equal(start(ctx).enqueue(track('X')).next(ctx).occurrence.fromHistory, false);
  assert.equal(start(source()).enqueue(track('H'), true).next(source()).occurrence.fromHistory, true);
});
check('remove and Undo restore exactly one duplicate and reject stale tokens', () => {
  const state = start(source()).enqueue(track('X')).enqueue(track('X')).enqueue(track('X'));
  const removed = state.remove(state.pending[1].id);
  assert.equal(removed.pending.length, 2);
  assert.deepEqual(removed.undo(removed.removal.id).pending, state.pending);
  assert.equal(removed.undo(-1), removed);
  const another = removed.remove(removed.pending[0].id);
  assert.equal(another.undo(removed.removal.id), another);
  assert.equal(state.remove(-1), state);
});
check('Undo follows a surviving neighbour when another request already played', () => {
  const ctx = source(), state = start(ctx).enqueue(track('X')).enqueue(track('Y')).enqueue(track('Z'));
  const removed = state.remove(state.pending[1].id), played = selected(removed.next(ctx));
  assert.deepEqual(pendingNames(played.undo(removed.removal.id)), ['Y', 'Z']);
});
check('learned metadata keeps duplicate Random occurrence keys and the exact cursor', () => {
  const ctx = new PlaybackContext({ source: { kind: 'random', session: 1 }, order: new PlayOrder({ tracks: ['R', 'S', 'R'].map(track) }).startAt(0) });
  const x = selected(start(ctx).enqueue(track('R')).next(ctx));
  const improved = x.withLearnedTrack({ ...track('R'), name: 'Learned' });
  assert.equal(improved.current.id, x.current.id);
  assert.equal(improved.current.context.order.currentIndex, 0);
  assert.deepEqual(improved.current.context.entryIds, x.current.context.entryIds);
});
check('source omissions do not edit the raw source and remain skipped on repeat laps', () => {
  const ctx = source(1, 'all'), omitted = ctx.omit(ctx.entryIds[2]);
  assert.equal(omitted.order, ctx.order);
  assert.equal(omitted.next().context.current.url, 'D');
  const all = ctx.entryIds.reduce((value, id) => value.omit(id), ctx);
  assert.equal(all.hasNext, false); assert.equal(all.hasPrevious, false);
  assert.equal(all.ended().kind, 'stop'); assert.equal(all.order.tracks, ctx.order.tracks);
});
check('Previous skips an omitted parked row instead of reviving it', () => {
  const ctx = source().omit(source().entryIds[1]);
  assert.equal(selected(start(ctx).enqueue(track('X')).next(ctx)).previous(ctx).step.context.current.url, 'A');
});
check('projection follows the held shuffle order and an in-list jump does not redeal', () => {
  const ctx = source().withOrder(source().order.withShuffle(true));
  const target = ctx.order.indices.at(-1), omitted = ctx.entryIds[ctx.order.indices[1]];
  const jumped = ctx.omit(omitted).jump(target), state = start(jumped).enqueue(track('X'));
  assert.equal(jumped.order.indices, ctx.order.indices);
  assert.deepEqual(state.rows(jumped).filter((row) => row.kind === 'source').map((row) => row.index), jumped.visibleIndices);
  assert.equal(state.rows(jumped).at(-1).track.url, 'X'); assert.ok(jumped.omitted.includes(omitted));
});

const clearContext = () => new PlaybackContext({ source: { kind: 'collection', origin: 'history' },
  order: new PlayOrder({ tracks: ['A', 'B', 'C'].map(track) }).startAt(1) });
const plan = () => { const ctx = clearContext(); return new PlaybackPlan(ctx, start(ctx).enqueue(track('A')).enqueue(track('A'))); };
check('Clear keeps only current identity and History origin without an anchor', () => {
  const before = plan(), after = before.clear().after;
  assert.equal(after.context.source.kind, 'none'); assert.equal(after.context.order.tracks.length, 0);
  assert.equal(after.schedule.pending.length, 0); assert.equal(after.schedule.current.id, before.schedule.current.id);
  assert.equal(after.schedule.current.track.url, 'B'); assert.equal(after.schedule.current.manual, true);
  assert.equal(after.schedule.current.fromHistory, true); assert.deepEqual(names(after.schedule, after.context), ['B']);
  assert.equal(after.schedule.hasNext(after.context), false); assert.equal(after.schedule.hasPrevious(after.context), false);
  assert.equal(after.schedule.next(after.context, true).step.kind, 'stop'); assert.equal(after.clear(), null);
});
check('duplicate requests after Clear drain once and cannot return to cleared material', () => {
  const cleared = plan().clear().after, pending = cleared.schedule.enqueue(track('A')).enqueue(track('A'));
  const first = pending.next(cleared.context), second = first.schedule.next(cleared.context, true);
  assert.deepEqual(names(first.schedule, cleared.context), ['A', 'A']);
  assert.notEqual(second.occurrence.id, first.occurrence.id); assert.deepEqual(names(second.schedule, cleared.context), ['A']);
  assert.equal(second.schedule.hasPrevious(cleared.context), false); assert.equal(second.schedule.next(cleared.context, true).step.kind, 'stop');
});
check('Clear while a manual row plays retains that row rather than its parked source', () => {
  const before = plan(), manual = before.schedule.next(before.context), after = new PlaybackPlan(before.context, manual.schedule).clear().after;
  assert.equal(after.schedule.current.request, manual.occurrence.request); assert.deepEqual(names(after.schedule, after.context), ['A']);
  assert.equal(after.schedule.hasPrevious(after.context), false);
});
check('Clear handles restored, idle and already consumed plans', () => {
  const ctx = clearContext(), restored = new PlaybackPlan(ctx).clear().after;
  assert.equal(restored.schedule.current.track.url, 'B'); assert.ok(restored.schedule.nextId > restored.schedule.current.id);
  const idle = new PlaybackPlan(ctx.withOrder(ctx.order.withoutPosition())).clear().after;
  assert.equal(idle.schedule.current, null); assert.deepEqual(idle.schedule.rows(idle.context), []);
  const before = plan(), consumed = new PlaybackPlan(before.context, before.schedule.finishCurrent()).clear().after;
  assert.equal(consumed.schedule.consumed, true); assert.deepEqual(consumed.schedule.rows(consumed.context), []);
});
check('Clear keeps transport modes, but Repeat All cannot revive the deleted source', () => {
  for (const repeat of ['off', 'all', 'one']) {
    const ctx = clearContext().withOrder(clearContext().order.withRepeat(repeat).withShuffle(true));
    const after = new PlaybackPlan(ctx, plan().schedule).clear().after;
    assert.equal(after.context.order.repeat, repeat); assert.equal(after.context.order.shuffle, true);
    assert.equal(after.schedule.next(after.context, true).step.kind, repeat === 'one' ? 'replay' : 'stop');
  }
});
check('Clear Undo restores content once and retains live Repeat and Shuffle', () => {
  const token = plan().clear(), live = new PlaybackPlan(token.after.context.withOrder(token.after.context.order.withRepeat('all').withShuffle(true)), token.after.schedule);
  const restored = token.undo(live);
  assert.equal(restored.schedule.pending, token.before.schedule.pending); assert.equal(restored.schedule.current, token.before.schedule.current);
  assert.equal(restored.context.source.kind, 'collection'); assert.equal(restored.context.order.repeat, 'all');
  assert.equal(restored.context.order.shuffle, true); assert.ok(restored.schedule.nextId >= live.schedule.nextId);
  assert.ok(restored.schedule.generation > live.schedule.generation); assert.equal(token.undo(restored), null);
});
check('Clear Undo cannot overwrite new work, completion or a replaced traversal', () => {
  const token = plan().clear(), live = token.after, changed = live.schedule.enqueue(track('C'));
  for (const schedule of [changed, changed.remove(changed.pending[0].id), live.schedule.finishCurrent(), live.schedule.resetTraversal(),
    live.schedule.copy({ current: { ...live.schedule.current, id: 999 } })])
    assert.equal(token.undo(new PlaybackPlan(live.context, schedule)), null);
  assert.equal(token.undo(new PlaybackPlan(clearContext(), live.schedule)), null);
});
check('reorder and Undo stay inside the manual block across shuffle and replacement', () => {
  const ctx = source().withOrder(source().order.withShuffle(true));
  const state = start(ctx).enqueue(track('X')).enqueue(track('Y')).enqueue(track('Z'));
  const removed = state.remove(state.pending[1].id);
  const moved = removed.moveRequest(state.pending[2].id, state.pending[0].id);
  const restored = moved.undo(removed.removal.id);
  assert.deepEqual(pendingNames(restored), ['Y', 'Z', 'X']);
  assert.deepEqual(restored.rows(ctx).filter((row) => row.kind === 'source').map((row) => row.index), ctx.visibleIndices);
  const indices = restored.rows(ctx).flatMap((row, i) => row.kind === 'manual' ? [i] : []);
  assert.equal(indices.at(-1) - indices[0] + 1, indices.length);
  assert.equal(restored.contextSelected(source(2)).pending, restored.pending);
});
check('growing equal Random files retains keys and omits only one occurrence', () => {
  const ctx = new PlaybackContext({ source: { kind: 'random', session: 1 }, order: new PlayOrder({ tracks: ['R', 'R'].map(track) }).startAt(0) });
  const removed = ctx.omit(ctx.entryIds[0]);
  const grown = removed.withOrder(removed.order.copy({ tracks: [...removed.order.tracks, track('R')] }));
  assert.deepEqual(grown.entryIds.slice(0, 2), ctx.entryIds); assert.equal(new Set(grown.entryIds).size, 3);
  assert.deepEqual(grown.visibleIndices, [1, 2]);
  assert.deepEqual(removed.restore(ctx.entryIds[0]).visibleIndices, ctx.visibleIndices);
});
check('source edits remap occurrence identities without restoring omissions', () => {
  const ctx = source(), omitted = ctx.omit(ctx.entryIds[2]);
  const edited = omitted.withOrder(omitted.order.withTracks(omitted.order.tracks.slice(1)));
  assert.deepEqual(edited.entryIds, ctx.entryIds.slice(1));
  assert.deepEqual(edited.visibleIndices.map((index) => edited.order.tracks[index].url), ['B', 'D']);
});
check('pruning the sounding Random occurrence cannot highlight a different parked row', () => {
  const ctx = new PlaybackContext({ source: { kind: 'random', session: 1 }, order: new PlayOrder({ tracks: ['R', 'S', 'R'].map(track) }).startAt(2) });
  const playing = start(ctx), pruned = ctx.copy({ order: new PlayOrder({ tracks: ctx.order.tracks.slice(0, 2) }).startAt(1), entryIds: ctx.entryIds.slice(0, 2) });
  assert.equal(playing.current.track.url, 'R'); assert.ok(playing.rows(pruned).every((row) => !row.playing));
  assert.equal(start(pruned).rows(pruned).find((row) => row.playing).track.url, 'S');
});
console.log(`${checks} Android schedule/clear cases passed`);
