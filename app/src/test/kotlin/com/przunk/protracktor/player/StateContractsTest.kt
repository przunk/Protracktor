// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Observable state contracts shared by the controller, screens and service. */
class StateContractsTest {
    private fun ref(id: String) = TrackRef(id = id, title = id, fileName = "$id.mod")

    @Test
    fun `repeated notices are distinct but copying a notice preserves its identity`() {
        val first = Message("Added")
        val second = Message("Added")
        assertNotEquals(first.id, second.id)
        assertEquals(first.id, first.copy(actionLabel = "undo").id)
    }

    @Test
    fun `current track comes only from the active source independently of the visible playlist`() {
        val queued = ref("playlist")
        val result = ref("result")
        val transient = ref("transient")
        val playlist = PlayerUiState(queue = PlayQueue(listOf(queued)).startAt(0))
        val search = playlist.copy(playback = collectionPlayback(PlayQueue(listOf(result)).startAt(0)))
        assertEquals(queued, playlist.current)
        assertEquals(result, search.current)
        assertEquals(transient, search.copy(playback = randomPlayback(transient)).current)
        assertNull(PlayerUiState().current)
    }

    @Test
    fun `an undescribed track does not show a fallback bar or offer seeking`() {
        val waiting = PlayerUiState(playback = randomPlayback(ref("loading")), fallbackLengthSeconds = 180)
        assertEquals(0.0, waiting.bar.seconds, 0.0)
        assertFalse(waiting.bar.approximate)
        assertFalse(waiting.seekable)
        val described = waiting.copy(metadata = mapOf("seekable" to "1"))
        assertEquals(180.0, described.bar.seconds, 0.0)
        assertTrue(described.bar.approximate)
        assertTrue(described.seekable)
        assertFalse(described.copy(metadata = mapOf("seekable" to "0")).seekable)
        assertFalse(described.copy(playback = null).seekable)
    }

    @Test
    fun `an external file has no neighbour but can navigate its subsongs`() {
        val external = PlayerUiState(
            playback = externalPlayback(ref("external")), subsongCount = 3,
        )
        assertFalse(external.canGoNextFile)
        assertFalse(external.canGoPreviousFile)
        assertFalse(external.canGoNext)
        assertFalse(external.canGoPrevious)
        val first = external.copy(playAllSubsongs = true)
        assertTrue(first.canGoNext)
        assertFalse(first.canGoPrevious)
        val last = first.copy(subsong = 2)
        assertFalse(last.canGoNext)
        assertTrue(last.canGoPrevious)
        assertFalse(last.canGoNextFile)
        assertFalse(last.canGoPreviousFile)
    }

    @Test
    fun `download groups are complete only when every constituent is held`() {
        val unknown = BrowseState()
        assertFalse(unknown.songDbComplete)
        assertFalse(unknown.songMetadataComplete)
        assertFalse(unknown.replayRoutinesComplete)
        for (bits in 0 until 32) {
            val held = HeldSets(
                songLengths = bits and 1 != 0,
                trackMetadata = bits and 2 != 0,
                songDbLengths = bits and 4 != 0,
                replays = bits and 8 != 0,
                players = bits and 16 != 0,
            )
            val browse = BrowseState(held = held)
            assertEquals("songdb $bits", bits and 6 == 6, browse.songDbComplete)
            assertEquals("metadata $bits", bits and 7 == 7, browse.songMetadataComplete)
            assertEquals("replays $bits", bits and 24 == 24, browse.replayRoutinesComplete)
        }
    }
}
