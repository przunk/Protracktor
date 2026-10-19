// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackScheduleTest {
    private fun track(name: String) = TrackRef(name, name)
    private fun context(index: Int = 1, repeat: RepeatMode = RepeatMode.OFF, shuffle: Boolean = false) =
        ActiveQueue(QueueSource.Playlist(1, "Source"),
            PlayQueue(listOf("A", "B", "C", "D").map(::track), repeat = repeat, shuffle = shuffle).startAt(index))
    private fun start(context: ActiveQueue = context()) = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE)
    private fun selected(step: ScheduledStep) = (step as ScheduledStep.Select)
    private fun advance(state: PlaybackSchedule, context: ActiveQueue): Pair<PlaybackSchedule, ActiveQueue> =
        when (val step = state.next(context)) {
            is ScheduledStep.Select -> step.schedule to state.contextForReplay(step.occurrence, context)
            is ScheduledStep.Context -> {
                val next = (step.step as QueueStep.Play).queue
                state.contextSelected(next, step.placement) to next
            }
        }

    @Test fun `FIFO drains before source advances once`() {
        var source = context()
        var state = start(source).enqueue(track("X")).enqueue(track("Y")).enqueue(track("Z"))
        val actual = mutableListOf(state.current!!.track.id)
        repeat(4) {
            val next = advance(state, source)
            state = next.first; source = next.second
            actual += state.current!!.track.id
        }
        assertEquals(listOf("B", "X", "Y", "Z", "C"), actual)
        assertEquals(2, source.order.currentIndex)
        assertTrue(state.pending.isEmpty())
    }

    @Test fun `equal files are independent occurrences and never become playlist entries`() {
        val source = context()
        val state = start(source).enqueue(track("B")).enqueue(track("B"))
        assertNotEquals(state.pending[0].id, state.pending[1].id)
        val first = selected(state.next(source))
        val second = selected(first.schedule.next(source))
        assertNotEquals(first.occurrence.id, second.occurrence.id)
        assertTrue(first.occurrence.manual)
        assertEquals(source, second.occurrence.context)
        assertEquals(listOf("A", "B", "C", "D"), source.order.tracks.map { it.id })
    }

    @Test fun `Previous and Next replay the same manual occurrence without second dequeue`() {
        val source = context()
        val first = selected(start(source).enqueue(track("X")).next(source))
        val previous = selected(first.schedule.previous(source))
        assertEquals("B", previous.occurrence.track.id)
        val replay = selected(previous.schedule.next(source))
        assertEquals(first.occurrence.id, replay.occurrence.id)
        assertTrue(replay.schedule.pending.isEmpty())
        assertEquals(2, replay.schedule.record.size)
    }

    @Test fun `new requests after Previous insert before forward without truncating it`() {
        val source = context()
        var state = start(source).enqueue(track("X")).enqueue(track("Y"))
        state = selected(state.next(source)).schedule
        state = selected(state.next(source)).schedule
        state = selected(state.previous(source)).schedule.enqueue(track("P")).enqueue(track("Q"))
        val names = mutableListOf<String>()
        repeat(3) {
            val next = selected(state.next(source))
            names += next.occurrence.track.id; state = next.schedule
        }
        assertEquals(listOf("P", "Q", "Y"), names)
        assertEquals(listOf("B", "X", "P", "Q", "Y"), state.record.map { it.track.id })
    }

    @Test fun `boundary Previous preserves the original forward source anchor`() {
        val source = context()
        var state = start(source)
        val back = state.previous(source) as ScheduledStep.Context
        val previous = (back.step as QueueStep.Play).queue
        state = state.contextSelected(previous, back.placement)
        assertEquals("A", state.current!!.track.id)
        val next = selected(state.next(previous))
        val restored = state.contextForReplay(next.occurrence, previous)
        assertEquals(source, restored)
        assertEquals("C", ((next.schedule.next(restored) as ScheduledStep.Context).step as QueueStep.Play).queue.current!!.id)
    }

    @Test fun `pending explicit Play keeps earlier requests and the forward record`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("Y")).enqueue(track("Z"))
        val now = state.playRequest(state.pending[2].id, source)!!
        assertEquals("Z", now.occurrence.track.id)
        assertEquals(listOf("X", "Y"), now.schedule.pending.map { it.track.id })
        assertEquals("X", selected(now.schedule.next(source)).occurrence.track.id)
        assertNull(state.playRequest(-1, source))
    }

    @Test fun `context selection and Random reset retain manual FIFO but abandon traversal`() {
        val source = context()
        val state = selected(start(source).enqueue(track("X")).enqueue(track("Y")).next(source)).schedule
        val random = ActiveQueue(QueueSource.Random(4), PlayQueue(listOf(track("R"))).startAt(0))
        val replaced = state.contextSelected(random, SchedulePlacement.REPLACE)
        assertEquals(listOf("R"), replaced.record.map { it.track.id })
        assertEquals(state.pending, replaced.pending)
        assertEquals(state.pending, replaced.resetTraversal().pending)
        assertNull(replaced.resetTraversal().current)
    }

    @Test fun `Repeat One repeats actual manual or context while explicit Next escapes`() {
        val source = context(repeat = RepeatMode.ONE)
        val state = start(source).enqueue(track("X")).enqueue(track("Y"))
        assertEquals(QueueStep.Replay, (state.next(source, completed = true) as ScheduledStep.Context).step)
        val manual = selected(state.next(source))
        assertEquals(QueueStep.Replay, (manual.schedule.next(source, completed = true) as ScheduledStep.Context).step)
        assertEquals("Y", selected(manual.schedule.next(source)).occurrence.track.id)
    }

    @Test fun `last source row still plays pending and Repeat Off then stops`() {
        val source = context(3)
        val state = start(source).enqueue(track("X"))
        assertTrue(state.hasNext(source))
        val manual = selected(state.next(source, completed = true))
        assertFalse(manual.schedule.hasNext(source))
        assertEquals(QueueStep.Stop, (manual.schedule.next(source, completed = true) as ScheduledStep.Context).step)
    }

    @Test fun `Repeat All repeats context and never consumed requests`() {
        val source = context(3, RepeatMode.PLAYLIST)
        val manual = selected(start(source).enqueue(track("X")).next(source))
        val next = manual.schedule.next(source, completed = true) as ScheduledStep.Context
        assertEquals("A", (next.step as QueueStep.Play).queue.current!!.id)
        assertTrue(manual.schedule.pending.isEmpty())
    }

    @Test fun `source shuffle order survives manual insertions`() {
        val source = context(shuffle = true)
        val manual = selected(start(source).enqueue(track("X")).next(source))
        assertEquals(source, manual.occurrence.context)
        assertEquals(source.next(), (manual.schedule.next(source) as ScheduledStep.Context).step)
    }

    @Test fun `empty and external contexts drain manual requests without inventing a loop`() {
        for (source in listOf(ActiveQueue(QueueSource.Playlist(1), PlayQueue(emptyList())),
            ActiveQueue(QueueSource.External, PlayQueue(listOf(track("E"))).startAt(0)))) {
            val state = PlaybackSchedule().enqueue(track("X"))
            assertTrue(state.hasNext(source))
            val next = selected(state.next(source))
            assertEquals("X", next.occurrence.track.id)
            assertEquals(QueueStep.Stop, (next.schedule.next(source, completed = true) as ScheduledStep.Context).step)
        }
    }

    @Test fun `History recording follows request origin not parked context`() {
        val history = context().copy(source = QueueSource.Collection(SessionSource.History))
        assertTrue(start(history).current!!.fromHistory)
        assertFalse(selected(start(history).enqueue(track("X")).next(history)).occurrence.fromHistory)
        val source = context()
        assertTrue(selected(start(source).enqueue(track("H"), fromHistory = true).next(source)).occurrence.fromHistory)
    }

    @Test fun `remove and Undo target one equal-file occurrence and reject stale repeats`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("X")).enqueue(track("X"))
        val id = state.pending[1].id
        val removed = state.remove(id)
        assertEquals(listOf(state.pending[0], state.pending[2]), removed.pending)
        assertEquals(state.current, removed.current)
        val token = removed.removal!!.id
        val restored = removed.undo(token)
        assertEquals(state.pending, restored.pending)
        assertEquals(restored, restored.undo(token))
        assertEquals(state, state.remove(-1))
        assertEquals(removed, removed.undo(-1))
    }

    @Test fun `Undo follows surviving neighbour after earlier requests are consumed`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("Y")).enqueue(track("Z"))
        val removed = state.remove(state.pending[1].id)
        val played = selected(removed.next(source)).schedule
        val restored = played.undo(removed.removal!!.id)
        assertEquals(listOf("Y", "Z"), restored.pending.map { it.track.id })
        assertEquals("X", restored.current!!.track.id)
    }

    @Test fun `new removal invalidates old Undo without restoring unrelated requests`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("Y"))
        val first = state.remove(state.pending[0].id)
        val second = first.remove(first.pending[0].id)
        assertEquals(second, second.undo(first.removal!!.id))
        assertEquals(listOf("Y"), second.undo(second.removal!!.id).pending.map { it.track.id })
    }

    @Test fun `replay keeps newer source edits and global modes`() {
        val source = context()
        val state = start(source)
        val edited = source.copy(order = source.order.withTracks(source.order.tracks.drop(1)).withRepeat(RepeatMode.ONE))
        val restored = state.contextForReplay(state.current!!, edited)
        assertEquals(edited.order.tracks, restored.order.tracks)
        assertEquals(RepeatMode.ONE, restored.order.repeat)
        assertEquals("B", restored.current!!.id)
    }

    @Test fun `title enrichment preserves IDs ordering and Undo identity`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("X"))
        val removed = state.remove(state.pending[0].id)
        val improved = removed.withLearnedTrack(track("X").copy(title = "Learned"))
        assertEquals(removed.pending.map { it.id }, improved.pending.map { it.id })
        assertEquals("Learned", improved.pending[0].track.title)
        assertEquals("Learned", improved.undo(improved.removal!!.id).pending[0].track.title)
        assertEquals(source.order, improved.current!!.context.order)
    }

    @Test fun `replay retains a newer shuffle permutation instead of restoring old seed`() {
        val source = context()
        val shuffled = source.copy(order = source.order.withShuffle(true, seed = 57))
        val state = start(source)
        val restored = state.contextForReplay(state.current!!, shuffled)
        assertEquals(shuffled, restored)
    }

    @Test fun `renaming the same playlist does not invalidate its traversal anchor`() {
        val source = context()
        val state = start(source)
        val live = source.copy(source = QueueSource.Playlist(1, "Renamed"), order = source.order.next())
        val restored = state.contextForReplay(state.current!!, live)
        assertEquals(live.source, restored.source)
        assertEquals("B", restored.current!!.id)
        val unrelated = live.copy(source = QueueSource.Playlist(2, "Another"))
        assertEquals(unrelated, state.contextForReplay(state.current!!, unrelated))
    }

    @Test fun `Random anchor restoration retains duplicate position when heard record grows`() {
        val source = ActiveQueue(QueueSource.Random(1), PlayQueue(listOf("R", "S", "R").map(::track)).startAt(0))
        val state = start(source)
        val live = source.copy(order = source.order.copy(tracks = source.order.tracks + track("T")).startAt(3))
        val restored = state.contextForReplay(state.current!!, live)
        assertEquals(0, restored.order.currentIndex)
        assertEquals(live.order.tracks, restored.order.tracks)
    }

    @Test fun `title enrichment never remaps repeated context files to their last occurrence`() {
        val source = ActiveQueue(QueueSource.Random(1), PlayQueue(listOf("R", "S", "R").map(::track)).startAt(0))
        val manual = selected(start(source).enqueue(track("X")).next(source)).schedule
        val enriched = manual.withLearnedTrack(track("R").copy(title = "Learned"))
        assertEquals(0, enriched.current!!.context.order.currentIndex)
        assertEquals("Learned", enriched.current!!.context.current!!.title)
    }

    @Test fun `idle manual requests do not adopt an unstarted editor playlist as context`() {
        val source = context().copy(order = PlayQueue(listOf(track("A"), track("B"))))
        val next = selected(PlaybackSchedule().enqueue(track("X")).next(source))
        val anchor = next.schedule.contextForReplay(next.occurrence, source)
        assertEquals(QueueSource.None, anchor.source)
        assertTrue(anchor.order.tracks.isEmpty())
        assertEquals(QueueStep.Stop, (next.schedule.next(anchor, completed = true) as ScheduledStep.Context).step)
        assertEquals(listOf("A", "B"), source.order.tracks.map { it.id })
    }
}
