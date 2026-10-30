// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// State/effect counterparts of ManualQueueControllerTest; no audio-device claim.
import assert from 'node:assert/strict';
import { PlaybackContext, PlayOrder } from '../web/src/playback-context.js';
import { PlaybackSchedule, PlaybackPlan } from '../web/src/playback-schedule.js';
import { applyPlaybackStep, removeManualRequest, removeSourceOccurrence, undoQueueRemoval } from '../web/src/playback-steps.js';

const track = (name) => Object.freeze({ url: name, name });
const make = (at = 1, repeat = 'off') => {
  const context = new PlaybackContext({ source: { kind: 'playlist', id: 1 },
    order: new PlayOrder({ tracks: ['A', 'B', 'C'].map(track), repeat }).startAt(at) });
  return new PlaybackPlan(context, new PlaybackSchedule().contextSelected(context));
};
const enqueue = (plan, ...names) => new PlaybackPlan(plan.context, names.reduce((state, name) => state.enqueue(track(name)), plan.schedule));
const next = (plan, completed = false) => applyPlaybackStep(plan, plan.schedule.next(plan.context, completed));
const previous = (plan) => applyPlaybackStep(plan, plan.schedule.previous(plan.context));
const names = (plan) => plan.schedule.rows(plan.context).map((row) => row.track.url);
let count = 0;
const check = (name, run) => { try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  count++; console.log(`✓ ${name}`); };

check('actual manual selection retains its parked source and pending FIFO', () => {
  const plan = enqueue(make(), 'X', 'Y'), selected = next(plan);
  assert.equal(selected.plan.context, plan.context); assert.equal(selected.effect.kind, 'select');
  assert.equal(selected.effect.track.url, 'X'); assert.equal(selected.effect.autoPlay, true);
  assert.deepEqual(names(selected.plan), ['A', 'B', 'X', 'Y', 'C']);
  assert.equal(selected.plan.schedule.pending[0].track.url, 'Y');
});
check('actual Next drains one-shot requests and Previous never resurrects consumed rows', () => {
  const x = next(enqueue(make(), 'X', 'Y')), y = next(x.plan), back = previous(y.plan);
  assert.equal(y.effect.track.url, 'Y'); assert.equal(back.effect.track.url, 'B');
  assert.deepEqual(names(back.plan), ['A', 'B', 'C']);
  assert.equal(next(back.plan).effect.track.url, 'C');
});
check('removing current manual advances once and Undo restores it only as pending', () => {
  const x = next(enqueue(make(), 'X', 'Y')).plan;
  const removed = removeManualRequest(x, x.schedule.current.id);
  assert.equal(removed.effect.track.url, 'Y');
  const restored = undoQueueRemoval(removed.plan, removed.undo);
  assert.equal(restored.schedule.current.track.url, 'Y');
  assert.deepEqual(restored.schedule.pending.map((request) => request.track.url), ['X']);
});
check('removing a pending occurrence never issues a playback effect', () => {
  const plan = enqueue(make(), 'X', 'X');
  const removed = removeManualRequest(plan, plan.schedule.pending[0].id);
  assert.equal(removed.effect.kind, 'none'); assert.equal(removed.plan.schedule.current, plan.schedule.current);
  assert.equal(removed.plan.schedule.pending.length, 1);
  assert.equal(undoQueueRemoval(removed.plan, removed.undo).schedule.pending.length, 2);
});
check('removing current source starts pending before source continuation without editing raw tracks', () => {
  const plan = enqueue(make(), 'X');
  const removed = removeSourceOccurrence(plan, plan.context.currentEntryId, plan.schedule.generation);
  assert.equal(removed.effect.track.url, 'X'); assert.equal(removed.plan.context.order.tracks, plan.context.order.tracks);
  const continued = next(removed.plan).plan;
  assert.equal(continued.schedule.current.track.url, 'C');
  assert.equal(previous(continued).effect.track.url, 'A');
});
check('paused source removal selects pending without an autoplay effect', () => {
  const plan = enqueue(make(), 'X');
  const removed = removeSourceOccurrence(plan, plan.context.currentEntryId, plan.schedule.generation, { autoPlay: false });
  assert.equal(removed.effect.track.url, 'X'); assert.equal(removed.effect.autoPlay, false);
});
check('paused manual removal selects its successor without an autoplay effect', () => {
  const plan = next(enqueue(make(), 'X', 'Y')).plan;
  const removed = removeManualRequest(plan, plan.schedule.current.id, { autoPlay: false });
  assert.equal(removed.effect.track.url, 'Y'); assert.equal(removed.effect.autoPlay, false);
});
check('paused source removal without pending selects the source successor', () => {
  const plan = make();
  const removed = removeSourceOccurrence(plan, plan.context.currentEntryId, plan.schedule.generation, { autoPlay: false });
  assert.equal(removed.effect.track.url, 'C'); assert.equal(removed.effect.autoPlay, false);
});
check('removing an exhausted current clears actual selection without inventing a successor', () => {
  const plan = make(2);
  const removed = removeSourceOccurrence(plan, plan.context.currentEntryId, plan.schedule.generation);
  assert.equal(removed.effect.kind, 'clear-current'); assert.equal(removed.plan.schedule.current, null);
  assert.equal(removed.plan.context.order.currentIndex, null); assert.deepEqual(names(removed.plan), ['A', 'B']);
});
check('generation-bound source removal and Undo cannot act on a replacement source', () => {
  const plan = make(), removed = removeSourceOccurrence(plan, plan.context.entryIds[0], plan.schedule.generation);
  const context = plan.context.select(2);
  const replacement = new PlaybackPlan(context, removed.plan.schedule.contextSelected(context));
  assert.equal(undoQueueRemoval(replacement, removed.undo), replacement);
  assert.equal(removeSourceOccurrence(replacement, context.entryIds[0], plan.schedule.generation).plan, replacement);
});
check('completion marks the actual manual occurrence consumed and hides it', () => {
  const manual = next(enqueue(make(2), 'X')).plan, completed = next(manual, true);
  assert.equal(completed.effect.kind, 'stop'); assert.equal(completed.plan.schedule.consumed, true);
  assert.deepEqual(names(completed.plan), ['A', 'B', 'C']);
});
check('Repeat One completion replays the actual request without consuming the pending FIFO', () => {
  const manual = next(enqueue(make(1, 'one'), 'X', 'Y')).plan, repeated = next(manual, true);
  assert.equal(repeated.effect.kind, 'replay'); assert.equal(repeated.plan, manual);
  assert.equal(next(manual).effect.track.url, 'Y');
});
check('History recording policy follows the selected occurrence instead of the parked source', () => {
  const original = make(), context = original.context.copy({ source: { kind: 'collection', origin: 'history' } });
  const history = new PlaybackPlan(context, new PlaybackSchedule().contextSelected(context).enqueue(track('X')));
  assert.equal(next(history).effect.fromHistory, false);
  const requested = new PlaybackPlan(original.context, original.schedule.enqueue(track('H'), true));
  assert.equal(next(requested).effect.fromHistory, true);
});
check('Random advancement retains plan ownership until its external draw completes', () => {
  const original = make(), context = original.context.copy({ source: { kind: 'random', session: 7 } });
  const plan = new PlaybackPlan(context, new PlaybackSchedule().contextSelected(context));
  const result = next(plan);
  assert.equal(result.plan, plan); assert.equal(result.effect.kind, 'random-next');
  assert.equal(result.effect.placement, 'append'); assert.equal(result.effect.autoPlay, true);
});
console.log(`${count} Android controller state/effect cases passed`);
