// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PlaylistHeartTest {
    private val first = TrackRef("first", "First")
    private val played = TrackRef("remote", "Played", fileName = "song.mod", sizeBytes = 42)
    private fun state() = PlayerUiState(restored = true, activePlaylistId = 7,
        playlists = listOf(SavedPlaylist(7, "My list")),
        queue = PlayQueue(listOf(first)).startAt(0), transient = played,
        playing = true, positionSeconds = 12.0, subsong = 2)

    private class Fixture(initial: PlayerUiState) {
        val player = MutableStateFlow(initial)
        val writes = mutableListOf<Pair<Long, List<TrackRef>>>()
        val notices = mutableListOf<Pair<PlaylistHeartSave.Outcome, PlaylistHeart?>>()
        var completion: ((Boolean) -> Unit)? = null
        var resolutions = 0
        val saver = PlaylistHeartSave(player, save = { done ->
            writes += player.value.activePlaylistId to player.value.queue.tracks.toList()
            completion = done
        }, resolveMetadata = { resolutions++ }, notice = { result, heart -> notices += result to heart })
        fun press() = saver.add(PlaylistHeart.of(player.value)?.request)
        fun complete(success: Boolean) { completion!!.invoke(success) }
    }

    @Test fun `missing restored selected or loaded track offers no heart`() {
        assertNull(PlaylistHeart.of(PlayerUiState()))
        assertNull(PlaylistHeart.of(state().copy(restored = false)))
        assertNull(PlaylistHeart.of(state().copy(activePlaylistId = 0)))
        assertNull(PlaylistHeart.of(state().copy(playlists = emptyList())))
        assertNull(PlaylistHeart.of(state().copy(loadingTrack = true)))
        assertNull(PlaylistHeart.of(state().copy(queue = PlayQueue(emptyList()), transient = null)))
    }

    @Test fun `random adds and immediately saves all existing working edits without transport changes`() {
        val edit = TrackRef("edit", "Prior edit")
        val before = state().copy(queue = PlayQueue(listOf(edit)).startAt(0), dirty = true)
        val f = Fixture(before)
        f.press()
        assertEquals(listOf(7L to listOf(edit, played)), f.writes)
        assertTrue(f.player.value.notificationPlaylistSaving)
        assertTrue(f.player.value.dirty)
        assertTrue(f.notices.isEmpty())
        assertEquals(played, f.player.value.current)
        assertEquals(before.transient, f.player.value.transient)
        assertEquals(before.positionSeconds, f.player.value.positionSeconds, 0.0)
        assertEquals(before.playing, f.player.value.playing)
        assertEquals(before.subsong, f.player.value.subsong)
        assertEquals(edit, f.player.value.queue.current)
        assertEquals(1, f.resolutions)
        f.complete(true)
        assertFalse(f.player.value.notificationPlaylistSaving)
        assertEquals(PlaylistHeartSave.Outcome.ADDED_AND_SAVED, f.notices.single().first)
    }

    @Test fun `browse saves the played result not the playlist cursor`() {
        val f = Fixture(state().copy(transient = null, resultsQueue = PlayQueue(listOf(played)).startAt(0)))
        f.press()
        assertEquals(listOf(first, played), f.writes.single().second)
        assertEquals(played, f.player.value.resultsQueue?.current)
        assertEquals(first, f.player.value.queue.current)
    }

    @Test fun `filled heart saves prior edits without removing or duplicating the file`() {
        val localAlias = played.copy(id = "content://alias", title = "Old title")
        val f = Fixture(state().copy(queue = PlayQueue(listOf(localAlias)), dirty = true))
        assertTrue(PlaylistHeart.of(f.player.value)!!.member)
        f.press()
        assertEquals(listOf(localAlias), f.writes.single().second)
        assertEquals(0, f.resolutions)
        f.complete(true)
        assertEquals(PlaylistHeartSave.Outcome.ALREADY_AND_SAVED, f.notices.single().first)
    }

    @Test fun `playlist playback remains unchanged when its own filled heart is pressed`() {
        val f = Fixture(state().copy(transient = null))
        f.press()
        assertEquals(listOf(first), f.player.value.queue.tracks)
        assertEquals(first, f.player.value.current)
        f.complete(true)
        assertEquals(PlaylistHeartSave.Outcome.ALREADY_AND_SAVED, f.notices.single().first)
    }

    @Test fun `stale track playlist missing and loading requests never save`() {
        val f = Fixture(state())
        f.saver.add(PlaylistHeart.Request("other", 7))
        f.saver.add(PlaylistHeart.Request(played.id, 8))
        f.saver.add(null)
        f.player.value = f.player.value.copy(loadingTrack = true)
        f.saver.add(PlaylistHeart.Request(played.id, 7))
        assertTrue(f.writes.isEmpty())
        assertEquals(listOf(first), f.player.value.queue.tracks)
        assertEquals(4, f.notices.size)
        assertTrue(f.notices.all { it.first == PlaylistHeartSave.Outcome.STALE })
    }

    @Test fun `a second press while saving cannot enqueue a second write`() {
        val f = Fixture(state())
        f.press()
        f.press()
        assertEquals(1, f.writes.size)
        assertEquals(PlaylistHeartSave.Outcome.SAVING, f.notices.single().first)
    }

    @Test fun `failure retains an unsaved addition and retry persists it without duplicates`() {
        val f = Fixture(state())
        f.press()
        f.complete(false)
        assertTrue(f.player.value.dirty)
        assertFalse(f.player.value.notificationPlaylistSaving)
        assertEquals(PlaylistHeartSave.Outcome.FAILED, f.notices.single().first)
        f.press()
        assertEquals(listOf(first, played), f.writes.last().second)
        assertEquals(2, f.writes.size)
        f.complete(true)
        assertEquals(PlaylistHeartSave.Outcome.ALREADY_AND_SAVED, f.notices.last().first)
    }

    @Test fun `paused playback stays paused when saving`() {
        val f = Fixture(state().copy(playing = false))
        f.press()
        f.complete(true)
        assertFalse(f.player.value.playing)
        assertEquals(12.0, f.player.value.positionSeconds, 0.0)
    }

    @Test fun `completion names the accepted destination even after navigation`() {
        val f = Fixture(state())
        f.press()
        f.player.value = f.player.value.copy(activePlaylistId = 9,
            playlists = listOf(SavedPlaylist(9, "Another")), queue = PlayQueue(listOf(first)))
        f.complete(true)
        assertEquals(7L, f.writes.single().first)
        assertEquals("My list", f.notices.single().second?.playlistName)
        assertEquals(9L, f.player.value.activePlaylistId)
        assertEquals(listOf(first), f.player.value.queue.tracks)
    }
}
