// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ManualPlaybackStateTest {
    private val sourceTrack = TrackRef("source", "Source track")
    private val requested = TrackRef("manual", "Manual track")
    private val context = ActiveQueue(QueueSource.Playlist(1), PlayQueue(listOf(sourceTrack)).startAt(0))
    private fun manual(source: ActiveQueue = context, history: Boolean = false): PlayerUiState {
        val next = PlaybackSchedule().enqueue(requested, history).next(source) as ScheduledStep.Select
        return PlayerUiState(activePlaylistId = 1, queue = source.order, playback = source, schedule = next.schedule)
    }

    @Test fun `actual manual file differs from the parked playlist cursor`() {
        val state = manual()
        assertEquals(sourceTrack, state.activePlayback.current)
        assertEquals(requested, state.current)
        assertTrue(state.manualCurrent)
        assertTrue(state.awayFromPlaylist)
        assertEquals(listOf(sourceTrack), state.queue.tracks)
    }

    @Test fun `manual copy of the context file is not a sounding source row`() {
        val next = PlaybackSchedule().enqueue(sourceTrack).next(context) as ScheduledStep.Select
        val state = PlayerUiState(activePlaylistId = 1, queue = context.order, schedule = next.schedule)
        assertEquals(sourceTrack, state.current)
        assertTrue(state.awayFromPlaylist)
    }

    @Test fun `History origin belongs to actual request and not suspended context`() {
        val history = context.copy(source = QueueSource.Collection(SessionSource.History))
        assertFalse(manual(history).resultsFromHistory)
        assertTrue(manual(history = true).resultsFromHistory)
    }

    @Test fun `pending enables file Next at context end and from empty state`() {
        val state = PlayerUiState(queue = context.order, activePlaylistId = 1)
        assertFalse(state.canGoNextFile)
        assertTrue(state.copy(schedule = state.schedule.enqueue(requested)).canGoNextFile)
        assertTrue(PlayerUiState(schedule = PlaybackSchedule().enqueue(requested)).canGoNextFile)
    }

    @Test fun `manual Previous can return to source and consumed manual is not a forward destination`() {
        val state = manual()
        assertTrue(state.canGoPreviousFile)
        val back = (state.schedule.previous(context) as ScheduledStep.Context).step as QueueStep.Play
        val returned = state.copy(schedule = state.schedule.contextSelected(back.queue, SchedulePlacement.PREPEND))
        assertFalse(returned.canGoNextFile)
    }

    @Test fun `subsong availability uses actual file and is independent of source size`() {
        val state = manual().copy(playAllSubsongs = true, subsong = 1, subsongCount = 3)
        assertTrue(state.canGoNext)
        assertTrue(state.canGoPrevious)
        assertFalse(state.canGoNextFile)
    }

    @Test fun `new Random session clears actual manual occurrence but retains waiting requests`() {
        val state = manual().copy(schedule = manual().schedule.enqueue(TrackRef("later", "Later")))
        val rerolled = state.preparingRandom(5)
        assertNull(rerolled.current)
        assertFalse(rerolled.manualCurrent)
        assertEquals(state.schedule.pending, rerolled.schedule.pending)
        assertTrue(rerolled.randomMode)
    }

    @Test fun `learning metadata improves actual request without changing source or identity`() {
        val state = manual()
        val improved = state.withLearnedTrack(requested.copy(title = "Learned title", author = "Author"))
        assertEquals("Learned title", improved.current!!.title)
        assertEquals(state.schedule.current!!.id, improved.schedule.current!!.id)
        assertEquals(state.activePlayback, improved.activePlayback)
        assertEquals(state.queue, improved.queue)
    }

    @Test fun `learning a repeated Random file does not move the parked context position`() {
        val random = ActiveQueue(QueueSource.Random(1), PlayQueue(listOf(sourceTrack, requested, sourceTrack)).startAt(0))
        val state = manual(random)
        val improved = state.withLearnedTrack(sourceTrack.copy(title = "Learned"))
        assertEquals(0, improved.activePlayback.order.currentIndex)
        assertEquals(0, improved.schedule.current!!.context.order.currentIndex)
        assertEquals(state.current, improved.current)
    }

    @Test fun `emptying source playlist does not stop its temporary manual occurrence`() = runTest {
        val before = manual().copy(playing = true, metadata = mapOf("title" to "Manual"), positionSeconds = 9.0)
        val player = MutableStateFlow(before)
        val adapter = PlaylistSessionState(player, MutableStateFlow(BrowseState())) { Message(it.name) }
        assertFalse(adapter.ownsPlayback)
        adapter.emptyPlaylist { emptyList() }
        assertTrue(player.value.playing)
        assertEquals(before.current, player.value.current)
        assertEquals(before.metadata, player.value.metadata)
        assertEquals(9.0, player.value.positionSeconds, 0.0)
        assertTrue(player.value.queue.tracks.isEmpty())
        assertTrue(player.value.schedule.pending.isEmpty())
    }
}
