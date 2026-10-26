// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Pure counterpart of Android PlaybackController.applyScheduledStep/applyQueueStep.
import { PlaybackPlan } from './playback-schedule.js';

const result = (plan, effect) => Object.freeze({ plan, effect: Object.freeze(effect) });

export function applyPlaybackStep(plan, step, { autoPlay = true } = {}) {
  if (step.kind === 'select') {
    const context = step.schedule.contextForSelection(step.occurrence, plan.context);
    return result(new PlaybackPlan(context, step.schedule), { kind: 'select', track: step.occurrence.track,
      fromHistory: step.occurrence.fromHistory, autoPlay });
  }
  if (step.kind !== 'context') throw new TypeError(`Unknown scheduled step: ${step.kind}`);
  const action = step.step;
  switch (action.kind) {
    case 'play': {
      const schedule = plan.schedule.contextSelected(action.context, step.placement);
      return result(new PlaybackPlan(action.context, schedule), { kind: 'select', track: schedule.current.track,
        fromHistory: schedule.current.fromHistory, autoPlay });
    }
    case 'random-next': case 'random-previous':
      return result(plan, { kind: action.kind, placement: step.placement, autoPlay });
    case 'replay': return result(plan, { kind: 'replay' });
    case 'stop': return result(new PlaybackPlan(plan.context, plan.schedule.finishCurrent()), { kind: 'stop' });
    case 'unchanged': return result(plan, { kind: 'none' });
    default: throw new TypeError(`Unknown source step: ${action.kind}`);
  }
}

/** Session-only removal follows Android's paused/current removal policy. */
export function removeManualRequest(plan, id, { autoPlay = true } = {}) {
  const current = !plan.schedule.consumed && plan.schedule.current?.request?.id === id
    ? plan.schedule.current.request : null;
  const before = current ? plan.schedule.copy({ pending: [current, ...plan.schedule.pending] }) : plan.schedule;
  if (!before.pending.some((entry) => entry.id === id)) return result(plan, { kind: 'none' });
  const schedule = before.remove(id);
  const changed = new PlaybackPlan(plan.context, schedule);
  return Object.freeze({ ...(current ? advanceAfterRemoval(changed, autoPlay) : result(changed, { kind: 'none' })),
    undo: Object.freeze({ kind: 'manual', id: schedule.removal.id, track: schedule.removal.request.track }) });
}

export function removeSourceOccurrence(plan, id, generation, { autoPlay = true } = {}) {
  if (generation !== plan.schedule.generation || !plan.context.entryIds.includes(id) || plan.context.omitted.includes(id))
    return result(plan, { kind: 'none' });
  const changed = new PlaybackPlan(plan.context.omit(id), plan.schedule);
  const current = !plan.schedule.current?.manual && id === plan.context.currentEntryId;
  const track = plan.context.order.tracks[plan.context.entryIds.indexOf(id)];
  return Object.freeze({ ...(current ? advanceAfterRemoval(changed, autoPlay) : result(changed, { kind: 'none' })),
    undo: Object.freeze({ kind: 'source', id, generation, track }) });
}

function advanceAfterRemoval(plan, autoPlay) {
  if (plan.schedule.hasNext(plan.context)) return applyPlaybackStep(plan, plan.schedule.next(plan.context), { autoPlay });
  const context = plan.context.currentAvailable ? plan.context : plan.context.withOrder(plan.context.order.withoutPosition());
  return result(new PlaybackPlan(context, plan.schedule.copy({ current: null, consumed: false })), { kind: 'clear-current' });
}

export function undoQueueRemoval(plan, token) {
  if (token.kind === 'manual') return new PlaybackPlan(plan.context, plan.schedule.undo(token.id));
  if (token.kind === 'source' && token.generation === plan.schedule.generation)
    return new PlaybackPlan(plan.context.restore(token.id), plan.schedule);
  return plan;
}
