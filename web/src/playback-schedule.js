// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { PlaybackContext, PlayOrder } from './playback-context.js';

const frozen = (items) => Object.isFrozen(items) ? items : Object.freeze([...items]);
const sameSource = (a, b) => [...new Set([...Object.keys(a), ...Object.keys(b)])]
  .every((key) => JSON.stringify(a[key]) === JSON.stringify(b[key]));
const occurrence = ({ id, track, context, request = null }) => Object.freeze({ id, track, context, request,
  manual: request !== null, fromHistory: request?.fromHistory ?? context.fromHistory });
const request = (id, track, fromHistory = false) => Object.freeze({ id, track, fromHistory });
const contextStep = (step, placement) => Object.freeze({ kind: 'context', step: Object.freeze(step), placement });
export const SchedulePlacement = Object.freeze({ REPLACE: 'replace', APPEND: 'append', PREPEND: 'prepend' });

/** One-shot manual FIFO. Previous always belongs to the parked source context. */
export class PlaybackSchedule {
  constructor({ pending = [], current = null, consumed = false, nextId = 1,
    generation = 0, removal = null } = {}) {
    this.pending = frozen(pending);
    this.current = current;
    this.consumed = consumed;
    this.nextId = nextId;
    this.generation = generation;
    this.removal = removal;
    Object.freeze(this);
  }

  copy(values) { return new PlaybackSchedule({ ...this, ...values }); }
  enqueue(track, fromHistory = false) {
    return this.copy({ pending: [...this.pending, request(this.nextId, track, fromHistory)], nextId: this.nextId + 1 });
  }
  moveRequest(id, targetId) {
    const from = this.pending.findIndex((entry) => entry.id === id);
    const target = this.pending.findIndex((entry) => entry.id === targetId);
    if (from < 0 || target < 0 || from === target) return this;
    const pending = [...this.pending];
    pending.splice(target, 0, pending.splice(from, 1)[0]);
    return this.copy({ pending });
  }
  resetTraversal() { return this.copy({ current: null, consumed: false, generation: this.generation + 1 }); }
  contextSelected(context, placement = 'replace') {
    if (!Object.values(SchedulePlacement).includes(placement)) throw new TypeError(`Unknown schedule placement: ${placement}`);
    return this.copy({ current: context.current ? occurrence({ id: this.nextId, track: context.current, context }) : null,
      consumed: false, nextId: this.nextId + 1, generation: this.generation + (placement === 'replace' ? 1 : 0) });
  }
  finishCurrent() { return this.copy({ consumed: true }); }
  resumeCurrent() { return this.copy({ consumed: false }); }

  next(context, completed = false) {
    if (completed && context.order.repeat === 'one' && (this.current || context.currentAvailable))
      return contextStep({ kind: 'replay' }, 'append');
    if (this.pending.length) return this.playRequest(this.pending[0].id, context);
    return contextStep(completed ? context.ended() : context.next(), 'append');
  }
  previous(context) {
    return contextStep(this.current?.manual && context.currentAvailable ? { kind: 'play', context }
      : context.previous(), 'prepend');
  }
  playRequest(id, context) {
    const entry = this.pending.find((item) => item.id === id);
    if (!entry) return null;
    const anchor = !this.current && !context.current && context.source.kind !== 'random'
      ? new PlaybackContext({ order: new PlayOrder({ shuffle: context.order.shuffle,
        repeat: context.order.repeat, random: context.order.random }) }) : context;
    const current = occurrence({ id: entry.id, track: entry.track, context: anchor, request: entry });
    return Object.freeze({ kind: 'select', schedule: this.copy({
      pending: this.pending.filter((item) => item.id !== id), current, consumed: false }), occurrence: current });
  }
  hasNext(context) { return this.pending.length > 0 || context.hasNext; }
  hasPrevious(context) { return !!(this.current?.manual && context.currentAvailable) || context.hasPrevious; }
  sourcePlaybackIndex(context) {
    const index = context.order.currentIndex;
    return context.currentAvailable && (!this.current || !this.current.manual
      && sameSource(this.current.context.source, context.source)
      && this.current.context.currentEntryId === context.currentEntryId) ? index : null;
  }
  rows(context) {
    const actual = this.sourcePlaybackIndex(context);
    const positions = new Map(context.playbackIndices.map((index, position) => [index, position]));
    const anchor = positions.get(context.order.currentIndex) ?? -1;
    const rows = context.visibleIndices.map((index) => Object.freeze({ kind: 'source', id: context.entryIds[index],
      index, key: `source:${context.entryIds[index]}`, track: context.order.tracks[index], playing: index === actual }));
    const manual = [];
    if (this.current?.request && !this.consumed) manual.push(Object.freeze({ kind: 'manual',
      key: `manual:${this.current.request.id}`, request: this.current.request, track: this.current.track, playing: true }));
    this.pending.forEach((entry) => manual.push(Object.freeze({ kind: 'manual', key: `manual:${entry.id}`,
      request: entry, track: entry.track, playing: false })));
    const insertion = rows.filter((row) => positions.get(row.index) <= anchor).length;
    return frozen([...rows.slice(0, insertion), ...manual, ...rows.slice(insertion)]);
  }
  remove(id) {
    const index = this.pending.findIndex((entry) => entry.id === id);
    if (index < 0) return this;
    return this.copy({ pending: this.pending.filter((entry) => entry.id !== id), nextId: this.nextId + 1,
      removal: Object.freeze({ id: this.nextId, request: this.pending[index], index,
        before: this.pending[index - 1]?.id, after: this.pending[index + 1]?.id }) });
  }
  undo(id) {
    const token = this.removal;
    if (!token || token.id !== id) return this;
    if (this.pending.some((entry) => entry.id === token.request.id) || this.current?.id === token.request.id)
      return this.copy({ removal: null });
    const after = this.pending.findIndex((entry) => entry.id === token.after);
    const before = this.pending.findIndex((entry) => entry.id === token.before);
    const index = after >= 0 ? after : before >= 0 ? before + 1 : Math.min(token.index, this.pending.length);
    return this.copy({ pending: [...this.pending.slice(0, index), token.request, ...this.pending.slice(index)], removal: null });
  }
  withLearnedTrack(track) {
    const improve = (ref) => (ref.url ?? ref.id) === (track.url ?? track.id) ? track : ref;
    const improveRequest = (entry) => request(entry.id, improve(entry.track), entry.fromHistory);
    return this.copy({ pending: this.pending.map(improveRequest), current: this.current ? occurrence({ ...this.current,
      track: improve(this.current.track), request: this.current.request ? improveRequest(this.current.request) : null,
      context: this.current.context.withOrder(this.current.context.order.copy({
        tracks: this.current.context.order.tracks.map(improve) })) }) : null,
    removal: this.removal ? Object.freeze({ ...this.removal, request: improveRequest(this.removal.request) }) : null });
  }
  contextForSelection(entry, live) {
    return entry.context.source.kind === 'none' ? entry.context.withOrder(entry.context.order
      .withRepeat(live.order.repeat).withShuffle(live.order.shuffle)) : live;
  }
}

/** Session-only plan; Clear retains the actual occurrence without inventing a source anchor. */
export class PlaybackPlan {
  constructor(context = new PlaybackContext(), schedule = new PlaybackSchedule()) {
    this.context = context;
    this.schedule = schedule;
    Object.freeze(this);
  }
  clear() {
    if (this.context.source.kind === 'none' && !this.schedule.pending.length) return null;
    const empty = new PlaybackContext({ order: new PlayOrder({ shuffle: this.context.order.shuffle,
      repeat: this.context.order.repeat, random: this.context.order.random }) });
    const actual = this.schedule.current ?? (this.context.current ? occurrence({ id: this.schedule.nextId,
      track: this.context.current, context: this.context }) : null);
    const retained = actual ? occurrence({ ...actual, context: empty,
      request: actual.request ?? request(actual.id, actual.track, actual.fromHistory) }) : null;
    const after = new PlaybackPlan(empty, this.schedule.copy({ pending: [], current: retained, removal: null,
      nextId: Math.max(this.schedule.nextId, (retained?.id ?? 0) + 1), generation: this.schedule.generation + 1 }));
    return new QueueClear(this, after);
  }
}

export class QueueClear {
  constructor(before, after) { this.before = before; this.after = after; Object.freeze(this); }
  undo(live) {
    const after = this.after.schedule;
    const current = live.schedule;
    if (live.context.source.kind !== 'none' || current.generation !== after.generation
      || current.current?.id !== after.current?.id || current.consumed !== after.consumed
      || current.nextId !== after.nextId || current.pending.length) return null;
    const restored = this.before.context.withOrder(this.before.context.order.withRepeat(live.context.order.repeat)
      .withShuffle(live.context.order.shuffle));
    return new PlaybackPlan(restored, this.before.schedule.copy({ nextId: Math.max(this.before.schedule.nextId, current.nextId),
      generation: current.generation + 1 }));
  }
}
