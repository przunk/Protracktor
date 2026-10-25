// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlaylist
import org.junit.Assert.*
import org.junit.Test

class FloatingControlsStateTest {
    private val tracks = (1..3).map { TrackRef("song-$it", "Song $it") }
    private fun state() = PlayerUiState(queue = PlayQueue(tracks).startAt(1),
        restored = true, activePlaylistId = 7, playlists = listOf(SavedPlaylist(7, "My list")))

    @Test fun `controls follow the active source rather than the editor playlist cursor`() {
        val state = state().copy(playback = ActiveQueue(QueueSource.Collection(SessionSource.Search("query")),
            PlayQueue(tracks).startAt(0)))
        val shown = FloatingControlsState.of(state)
        assertFalse(shown.previous)
        assertTrue(shown.next)
        assertEquals(PlaylistHeart.of(state), shown.heart)
    }

    @Test fun `progress ticks do not change the panel projection`() {
        val state = state()
        assertEquals(FloatingControlsState.of(state), FloatingControlsState.of(state.copy(positionSeconds = 99.0)))
        assertNotEquals(FloatingControlsState.of(state), FloatingControlsState.of(state.copy(playing = true)))
    }

    @Test fun `pending saves and loading retain fixed semantic slots`() {
        val state = state()
        val pending = FloatingControlsState.of(state.copy(notificationPlaylistSaving = true))
        assertTrue(pending.heart!!.saving)
        assertEquals(PlaylistHeart.of(state)!!.request, pending.heart.request)
        val loading = FloatingControlsState.of(state.copy(loadingTrack = true))
        assertTrue(loading.loading)
        assertNull(loading.heart)
    }

    @Test fun `subsong transport availability and an absent playlist reuse existing rules`() {
        val state = state().copy(subsong = 1, subsongCount = 3, playAllSubsongs = true,
            queue = PlayQueue(listOf(tracks[0])).startAt(0), playlists = emptyList())
        val shown = FloatingControlsState.of(state)
        assertTrue(shown.previous)
        assertTrue(shown.next)
        assertNull(shown.heart)
        assertFalse(FloatingControlsState.of(PlayerUiState()).hasTrack)
    }
}
