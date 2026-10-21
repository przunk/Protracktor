// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackPlanTest {
    private val tracks = listOf("A", "B", "C").map { TrackRef("content://clear/$it.mod", it) }
    private val context = ActiveQueue(QueueSource.Collection(SessionSource.History), PlayQueue(tracks).startAt(1))
    private fun plan() = PlaybackPlan(context, PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE)
        .enqueue(tracks[0]).enqueue(tracks[0]))

    @Test fun `clear retains only actual current identity and History origin without a source anchor`() {
        val before = plan()
        val after = requireNotNull(before.clear()).after
        assertEquals(QueueSource.None, after.context.source)
        assertTrue(after.context.order.tracks.isEmpty())
        assertTrue(after.schedule.pending.isEmpty())
        assertEquals(before.schedule.current!!.id, after.schedule.current!!.id)
        assertEquals(tracks[1], after.schedule.current.track)
        assertTrue(after.schedule.current.manual)
        assertTrue(after.schedule.current.fromHistory)
        assertEquals(listOf(tracks[1]), after.schedule.rows(after.context).map { it.track })
        assertFalse(after.schedule.hasNext(after.context))
        assertFalse(after.schedule.hasPrevious(after.context))
        assertEquals(ScheduledStep.Context(QueueStep.Stop, SchedulePlacement.APPEND), after.schedule.next(after.context, true))
        assertNull(after.clear())
    }

    @Test fun `new duplicate requests drain once and never return to cleared current or source`() {
        val cleared = plan().clear()!!.after
        val pending = cleared.schedule.enqueue(tracks[0]).enqueue(tracks[0])
        val first = pending.next(cleared.context) as ScheduledStep.Select
        assertEquals(listOf(tracks[0], tracks[0]), first.schedule.rows(cleared.context).map { it.track })
        val second = first.schedule.next(cleared.context, true) as ScheduledStep.Select
        assertNotEquals(first.occurrence.id, second.occurrence.id)
        assertEquals(listOf(tracks[0]), second.schedule.rows(cleared.context).map { it.track })
        assertFalse(second.schedule.hasPrevious(cleared.context))
        assertEquals(ScheduledStep.Context(QueueStep.Stop, SchedulePlacement.APPEND), second.schedule.next(cleared.context, true))
    }

    @Test fun `clear while a manual occurrence sounds keeps that occurrence not the parked source`() {
        val before = plan()
        val manual = before.schedule.next(context) as ScheduledStep.Select
        val after = PlaybackPlan(context, manual.schedule).clear()!!.after
        assertEquals(manual.occurrence.request, after.schedule.current!!.request)
        assertEquals(tracks[0], after.schedule.rows(after.context).single().track)
        assertFalse(after.schedule.hasPrevious(after.context))
    }

    @Test fun `clear supports restored source idle and already consumed states`() {
        val restored = PlaybackPlan(context, PlaybackSchedule()).clear()!!.after
        assertEquals(tracks[1], restored.schedule.current!!.track)
        assertTrue(restored.schedule.current.fromHistory)
        assertTrue(restored.schedule.nextId > restored.schedule.current.id)
        val idle = PlaybackPlan(context.copy(order = PlayQueue(tracks)), PlaybackSchedule()).clear()!!.after
        assertNull(idle.schedule.current)
        assertTrue(idle.schedule.rows(idle.context).isEmpty())
        val consumed = plan().let { it.copy(schedule = it.schedule.finishCurrent()) }.clear()!!.after
        assertTrue(consumed.schedule.consumed)
        assertTrue(consumed.schedule.rows(consumed.context).isEmpty())
    }

    @Test fun `clear preserves repeat and shuffle but repeat all cannot revive the removed context`() {
        for (repeat in RepeatMode.entries) {
            val ctx = context.withOrder(context.order.withRepeat(repeat).withShuffle(true, 42))
            val after = PlaybackPlan(ctx, plan().schedule).clear()!!.after
            assertEquals(repeat, after.context.order.repeat)
            assertTrue(after.context.order.shuffle)
            val step = after.schedule.next(after.context, true) as ScheduledStep.Context
            assertEquals(if (repeat == RepeatMode.ONE) QueueStep.Replay else QueueStep.Stop, step.step)
        }
    }

    @Test fun `Undo restores plan without recycling identities and respects live repeat shuffle`() {
        val token = plan().clear()!!
        val live = token.after.copy(context = token.after.context.withOrder(
            token.after.context.order.withRepeat(RepeatMode.PLAYLIST).withShuffle(true, 2)))
        val restored = token.undo(live)!!
        assertEquals(token.before.schedule.pending, restored.schedule.pending)
        assertEquals(token.before.schedule.current, restored.schedule.current)
        assertEquals(context.source, restored.context.source)
        assertEquals(RepeatMode.PLAYLIST, restored.context.order.repeat)
        assertTrue(restored.context.order.shuffle)
        assertTrue(restored.schedule.nextId >= live.schedule.nextId)
        assertTrue(restored.schedule.generation > live.schedule.generation)
        assertNull(token.undo(restored))
    }

    @Test fun `Undo cannot overwrite new work completed current or a newer traversal`() {
        val token = plan().clear()!!
        val live = token.after
        assertNull(token.undo(live.copy(schedule = live.schedule.enqueue(tracks[2]))))
        val changed = live.schedule.enqueue(tracks[2])
        assertNull(token.undo(live.copy(schedule = changed.remove(changed.pending.single().id))))
        assertNull(token.undo(live.copy(schedule = live.schedule.finishCurrent())))
        assertNull(token.undo(live.copy(schedule = live.schedule.resetTraversal())))
        assertNull(token.undo(live.copy(context = context)))
        assertNull(token.undo(live.copy(schedule = live.schedule.copy(current = live.schedule.current!!.copy(id = 999)))))
    }
}
