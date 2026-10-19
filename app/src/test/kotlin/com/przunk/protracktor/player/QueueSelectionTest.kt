// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class QueueSelectionTest {
    private val a = listOf(TrackRef("a1", "A1"), TrackRef("a2", "A2"))
    private val b = listOf(TrackRef("b1", "B1"))
    private val lists = listOf(SavedPlaylist(1, "A"), SavedPlaylist(2, "B"))
    private fun fixture(): Pair<MutableStateFlow<PlayerUiState>, PlaylistSessionState> {
        val order = PlayQueue(a).startAt(0)
        val player = MutableStateFlow(PlayerUiState(restored = true, activePlaylistId = 1,
            playlists = lists, queue = order, playback = ActiveQueue(QueueSource.Playlist(1, "A"), order),
            playing = true, positionSeconds = 12.0, metadata = mapOf("title" to "A1")))
        return player to PlaylistSessionState(player, MutableStateFlow(BrowseState())) { Message(it.name) }
    }

    @Test fun `selecting another playlist preserves current audio and all transport metadata`() {
        val (player, session) = fixture()
        session.switchPlaylist(2, b, lists)
        assertEquals(a[0], player.value.current)
        assertEquals(b, player.value.queue.tracks)
        assertTrue(player.value.playing)
        assertTrue(player.value.awayFromPlaylist)
        assertEquals(12.0, player.value.positionSeconds, 0.0)
        assertEquals("A1", player.value.metadata["title"])
    }

    @Test fun `queue selection changes A cursor without touching displayed B and is restored when A is viewed`() {
        val (player, session) = fixture()
        session.switchPlaylist(2, b, lists)
        player.value = player.value.withPlayback(player.value.activePlayback.select(1))
        assertEquals(a[1], player.value.current)
        assertEquals(b, player.value.queue.tracks)
        assertNull(player.value.queue.currentIndex)
        session.switchPlaylist(1, a, lists)
        assertEquals(1, player.value.queue.currentIndex)
        assertEquals(a[1], player.value.current)
        assertFalse(player.value.awayFromPlaylist)
    }

    @Test fun `editing and writing B never writes active queue A`() {
        val (player, session) = fixture()
        session.switchPlaylist(2, b, lists)
        player.value = player.value.copy(queue = player.value.queue.withTracks(emptyList()), dirty = true)
        val write = session.writeSnapshot(2)!!
        assertEquals(2L, write.playlistId)
        assertTrue(write.tracks.isEmpty())
        assertEquals(a, player.value.transportQueue.tracks)
        assertEquals(a[0], player.value.current)
    }

    @Test fun `explicit B selection transfers ownership and keeps the two cursors synchronized`() {
        val (player, session) = fixture()
        session.switchPlaylist(2, b, lists)
        player.value = player.value.withPlayback(ActiveQueue(QueueSource.Playlist(2, "B"), player.value.queue.startAt(0)))
        assertEquals(b[0], player.value.current)
        assertEquals(player.value.transportQueue.currentIndex, player.value.queue.currentIndex)
        assertFalse(player.value.awayFromPlaylist)
    }

    @Test fun `History-origin queue remains History-origin while another playlist is viewed`() {
        val (player, session) = fixture()
        player.value = player.value.withPlayback(collectionPlayback(PlayQueue(a).startAt(1), SessionSource.History))
        session.switchPlaylist(2, b, lists)
        assertTrue(player.value.resultsFromHistory)
        assertFalse(HistoryRecording.records(player.value.searchMode, player.value.resultsFromHistory))
        assertEquals(a[1], player.value.current)
    }

    @Test fun `learning A metadata while viewing B does not change B or reorder active tracks`() {
        val (player, session) = fixture()
        session.switchPlaylist(2, b, lists)
        val before = player.value
        player.value = before.withLearnedTrack(a[0].copy(title = "Learned title"))
        assertEquals(before.queue, player.value.queue)
        assertEquals(a.map { it.id }, player.value.transportQueue.tracks.map { it.id })
        assertEquals("Learned title", player.value.current?.title)
        assertEquals(before.activePlayback.source, player.value.activePlayback.source)
    }

    @Test fun `explicit Random reset owns transport before its first asynchronous pick arrives`() {
        val (player, _) = fixture()
        player.value = player.value.preparingRandom(8)
        assertTrue(player.value.randomMode)
        assertEquals(QueueSource.Random(8), player.value.activePlayback.source)
        assertEquals(QueueStep.RandomNext, player.value.activePlayback.next())
        assertTrue(player.value.loadingTrack)
        assertFalse(player.value.playing)
        assertNull(player.value.current)
    }

    @Test fun `startup playlist restore cannot replace an already opened external file`() {
        val (player, session) = fixture()
        val external = TrackRef("external", "External")
        player.value = player.value.copy(playback = externalPlayback(external))
        session.restore(null, lists, a, 1)
        assertEquals(external, player.value.current)
        assertTrue(player.value.externalMode)
    }
}
