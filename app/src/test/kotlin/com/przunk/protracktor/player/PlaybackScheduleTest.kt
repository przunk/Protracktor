// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

/** Third-iteration contract: consumed manual entries never join backward/forward source history. */
class PlaybackScheduleTest {
    private fun track(name: String) = TrackRef(name, name)
    private fun context(index: Int = 1, repeat: RepeatMode = RepeatMode.OFF) = ActiveQueue(
        QueueSource.Playlist(1), PlayQueue(listOf("A", "B", "C", "D").map(::track), repeat = repeat).startAt(index))
    private fun selected(step: ScheduledStep) = step as ScheduledStep.Select
    private fun start(source: ActiveQueue) = PlaybackSchedule().contextSelected(source, SchedulePlacement.REPLACE)
    private fun names(state: PlaybackSchedule, source: ActiveQueue) = state.rows(source).map { it.track.id }

    @Test fun `manual FIFO is inserted after parked source and consumed rows disappear`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("Y"))
        assertEquals(listOf("A", "B", "X", "Y", "C", "D"), names(state, source))
        val x = selected(state.next(source)).schedule
        assertEquals(listOf("A", "B", "X", "Y", "C", "D"), names(x, source))
        val y = selected(x.next(source)).schedule
        assertEquals(listOf("A", "B", "Y", "C", "D"), names(y, source))
        assertEquals("B", source.current!!.id)
        val after = (y.next(source) as ScheduledStep.Context).step as QueueStep.Play
        assertEquals("C", after.queue.current!!.id)
        assertEquals(listOf("A", "B", "C", "D"), names(y.contextSelected(after.queue, SchedulePlacement.APPEND), after.queue))
    }

    @Test fun `Previous from any manual file returns parked source not consumed requests`() {
        val source = context()
        val x = selected(start(source).enqueue(track("X")).enqueue(track("Y")).next(source)).schedule
        val y = selected(x.next(source)).schedule
        assertEquals(source, ((y.previous(source) as ScheduledStep.Context).step as QueueStep.Play).queue)
        val returned = y.contextSelected(source, SchedulePlacement.PREPEND)
        assertEquals("C", (((returned.next(source) as ScheduledStep.Context).step as QueueStep.Play).queue.current!!.id))
    }

    @Test fun `Previous does not discard other waiting requests`() {
        val source = context()
        val manual = selected(start(source).enqueue(track("X")).enqueue(track("Y")).next(source)).schedule
        val returned = manual.contextSelected(source, SchedulePlacement.PREPEND)
        assertEquals(listOf("Y"), returned.pending.map { it.track.id })
        assertEquals("Y", selected(returned.next(source)).occurrence.track.id)
        assertFalse(names(returned, source).contains("X"))
    }

    @Test fun `equal files have distinct manual identities and source stays untouched`() {
        val source = context()
        val state = start(source).enqueue(track("B")).enqueue(track("B"))
        assertNotEquals(state.pending[0].id, state.pending[1].id)
        assertEquals(listOf("A", "B", "B", "B", "C", "D"), names(state, source))
        assertEquals(6, state.rows(source).map { it.key }.toSet().size)
        assertEquals(4, source.order.tracks.size)
    }

    @Test fun `explicit pending Play leaves earlier requests to play next`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("Y"))
        val picked = state.playRequest(state.pending.last().id, source)!!
        assertEquals(listOf("A", "B", "Y", "X", "C", "D"), names(picked.schedule, source))
        assertNull(state.playRequest(-1, source))
    }

    @Test fun `new source plays selected row first and preserves pending FIFO`() {
        val old = context()
        val manual = selected(start(old).enqueue(track("X")).enqueue(track("Y")).next(old)).schedule
        val next = context(2)
        val changed = manual.contextSelected(next, SchedulePlacement.REPLACE)
        assertEquals("C", changed.current!!.track.id)
        assertEquals(listOf("A", "B", "C", "Y", "D"), names(changed, next))
        assertTrue(changed.generation > manual.generation)
        assertEquals(changed.pending, changed.resetTraversal().pending)
    }

    @Test fun `Repeat One retains current manual until explicit file Next`() {
        val source = context(repeat = RepeatMode.ONE)
        val x = selected(start(source).enqueue(track("X")).enqueue(track("Y")).next(source)).schedule
        assertEquals(QueueStep.Replay, (x.next(source, completed = true) as ScheduledStep.Context).step)
        assertEquals("Y", selected(x.next(source)).occurrence.track.id)
    }

    @Test fun `Repeat All wraps source without replaying consumed requests`() {
        val source = context(3, RepeatMode.PLAYLIST)
        val manual = selected(start(source).enqueue(track("X")).next(source)).schedule
        val lap = (manual.next(source, completed = true) as ScheduledStep.Context).step as QueueStep.Play
        assertEquals("A", lap.queue.current!!.id)
        assertTrue(manual.pending.isEmpty())
    }

    @Test fun `idle manual playback has no invented source and exhausted row is hidden`() {
        val draft = ActiveQueue(QueueSource.Playlist(1), PlayQueue(listOf(track("A"))))
        val selected = selected(PlaybackSchedule().enqueue(track("X")).next(draft))
        val source = selected.schedule.contextForReplay(selected.occurrence, draft)
        assertEquals(QueueSource.None, source.source)
        assertEquals(QueueStep.Stop, (selected.schedule.next(source, true) as ScheduledStep.Context).step)
        assertEquals(listOf("X"), names(selected.schedule, source))
        assertTrue(selected.schedule.finishCurrent().rows(source).isEmpty())
        assertEquals(listOf("X"), names(selected.schedule.finishCurrent().resumeCurrent(), source))
        assertFalse(selected.schedule.hasPrevious(source))
    }

    @Test fun `actual origin follows request not parked History`() {
        val history = context().copy(source = QueueSource.Collection(SessionSource.History))
        assertFalse(selected(start(history).enqueue(track("X")).next(history)).occurrence.fromHistory)
        assertTrue(selected(start(context()).enqueue(track("H"), true).next(context())).occurrence.fromHistory)
    }

    @Test fun `remove and Undo restore exactly one duplicate and reject stale actions`() {
        val state = start(context()).enqueue(track("X")).enqueue(track("X")).enqueue(track("X"))
        val removed = state.remove(state.pending[1].id)
        assertEquals(2, removed.pending.size)
        assertEquals(state.pending, removed.undo(removed.removal!!.id).pending)
        assertEquals(removed, removed.undo(-1))
        val another = removed.remove(removed.pending[0].id)
        assertEquals(another, another.undo(removed.removal!!.id))
        assertEquals(state, state.remove(-1))
    }

    @Test fun `Undo follows surviving neighbour after other requests play`() {
        val source = context()
        val state = start(source).enqueue(track("X")).enqueue(track("Y")).enqueue(track("Z"))
        val removed = state.remove(state.pending[1].id)
        val played = selected(removed.next(source)).schedule
        assertEquals(listOf("Y", "Z"), played.undo(removed.removal!!.id).pending.map { it.track.id })
    }

    @Test fun `metadata enrichment preserves occurrence keys and parked duplicate cursor`() {
        val source = ActiveQueue(QueueSource.Random(1), PlayQueue(listOf(track("R"), track("S"), track("R"))).startAt(0))
        val x = selected(start(source).enqueue(track("R")).next(source)).schedule
        val improved = x.withLearnedTrack(track("R").copy(title = "Learned"))
        assertEquals(x.current!!.id, improved.current!!.id)
        assertEquals(0, improved.current!!.context.order.currentIndex)
        assertEquals(x.current!!.context.entryIds, improved.current!!.context.entryIds)
    }

    @Test fun `source omissions preserve raw source and remain skipped on repeat laps`() {
        val source = context(1, RepeatMode.PLAYLIST)
        val omitted = source.omit(source.entryIds[2])
        assertEquals(source.order, omitted.order)
        assertEquals("D", (omitted.next() as QueueStep.Play).queue.current!!.id)
        val all = source.entryIds.fold(source) { at, id -> at.omit(id) }
        assertFalse(all.hasNext)
        assertFalse(all.hasPrevious)
        assertEquals(QueueStep.Stop, all.ended())
        assertEquals(source.order.tracks, all.order.tracks)
    }

    @Test fun `Previous skips omitted parked row and returns nearest surviving source`() {
        val source = context().omit(context().entryIds[1])
        val manual = selected(start(source).enqueue(track("X")).next(source)).schedule
        assertEquals("A", ((manual.previous(source) as ScheduledStep.Context).step as QueueStep.Play).queue.current!!.id)
    }

    @Test fun `growing duplicate Random rows retain identities and only one occurrence is omitted`() {
        val source = ActiveQueue(QueueSource.Random(1), PlayQueue(listOf(track("R"), track("R"))).startAt(0))
        val removed = source.omit(source.entryIds[0])
        val grown = removed.withOrder(removed.order.copy(tracks = removed.order.tracks + track("R")))
        assertEquals(source.entryIds, grown.entryIds.take(2))
        assertEquals(3, grown.entryIds.toSet().size)
        assertEquals(listOf(1, 2), grown.visibleIndices)
        assertEquals(source.visibleIndices, removed.restore(source.entryIds[0]).visibleIndices)
    }

    @Test fun `source edit remaps identities without restoring removed rows`() {
        val source = context()
        val omitted = source.omit(source.entryIds[2])
        val edited = omitted.withOrder(omitted.order.withTracks(omitted.order.tracks.drop(1)))
        assertEquals(source.entryIds.drop(1), edited.entryIds)
        assertEquals(listOf("B", "D"), edited.visibleIndices.map { edited.order.tracks[it].id })
    }

    @Test fun `projection follows shuffle and in-list jump does not reshuffle or restore omissions`() {
        val source = context().copy(order = context().order.withShuffle(true, 47))
        val target = source.order.playbackIndices.last()
        val omittedId = source.entryIds[source.order.playbackIndices[1]]
        val jumped = source.omit(omittedId).jump(target)
        assertEquals(source.order.playbackIndices, jumped.order.playbackIndices)
        val state = start(jumped).enqueue(track("X"))
        assertEquals(jumped.visibleIndices, state.rows(jumped).filterIsInstance<PlaybackRow.Source>().map { it.index })
        assertEquals("X", state.rows(jumped).last().track.id)
        assertTrue(omittedId in jumped.omitted)
    }
}
