// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/** Ephemeral transport state, deliberately separate from the saved playlist editor. */
internal data class PlaybackPlan(val context: ActiveQueue, val schedule: PlaybackSchedule) {
    fun clear(): QueueClear? {
        if (context.source == QueueSource.None && schedule.pending.isEmpty()) return null
        val empty = ActiveQueue(QueueSource.None, PlayQueue(emptyList(),
            shuffle = context.order.shuffle, repeat = context.order.repeat))
        val actual = schedule.current ?: context.current?.let {
            PlaybackOccurrence(schedule.nextId, it, context)
        }
        val retained = actual?.copy(context = empty, request = actual.request ?:
            ManualRequest(actual.id, actual.track, actual.fromHistory))
        val cleared = schedule.copy(pending = emptyList(), current = retained, removal = null,
            nextId = maxOf(schedule.nextId, (retained?.id ?: 0) + 1), generation = schedule.generation + 1)
        return QueueClear(this, PlaybackPlan(empty, cleared))
    }
}

internal data class QueueClear(val before: PlaybackPlan, val after: PlaybackPlan) {
    fun undo(live: PlaybackPlan): PlaybackPlan? {
        if (live.context.source != QueueSource.None || live.schedule.generation != after.schedule.generation ||
            live.schedule.current?.id != after.schedule.current?.id || live.schedule.consumed != after.schedule.consumed ||
            live.schedule.nextId != after.schedule.nextId || live.schedule.pending.isNotEmpty()) return null
        val restored = before.context.withOrder(before.context.order
            .withRepeat(live.context.order.repeat).withShuffle(live.context.order.shuffle))
        return PlaybackPlan(restored, before.schedule.copy(
            nextId = maxOf(before.schedule.nextId, live.schedule.nextId), generation = live.schedule.generation + 1))
    }
}
