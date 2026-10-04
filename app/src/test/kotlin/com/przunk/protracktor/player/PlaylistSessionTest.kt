// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.SavedPlaylist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSessionTest {
    private fun track(id: String) = TrackRef(id, id)

    private suspend fun CoroutineScope.drain() {
        while (coroutineContext.job.children.any()) coroutineContext.job.children.toList().joinAll()
    }

    private class Fixture(scope: CoroutineScope) : PlaylistSession.Store {
        val player = MutableStateFlow(PlayerUiState())
        val browse = MutableStateFlow(BrowseState())
        var language = "en"
        val notices = mutableListOf<PlaylistSessionState.Notice>()
        val state = PlaylistSessionState(player, browse) {
            notices += it
            Message("$language:$it")
        }
        val session = PlaylistSession(scope, this, state, { language + " default" },
            stopPlayback = { events += "stop"; onStop() },
            resolveMetadataInBackground = { events += "resolve"; onResolve() })
        val events = mutableListOf<String>()
        var saved: SavedPlayerState? = SavedPlayerState(1, null, false, RepeatMode.OFF)
        val lists = linkedMapOf(1L to SavedPlaylist(1, "One"), 2L to SavedPlaylist(2, "Two"))
        val rows = mutableMapOf<Long, List<TrackRef>>()
        val savedStates = mutableListOf<SavedPlayerState>()
        val writes = mutableListOf<Pair<Long, List<TrackRef>>>()
        var before: suspend (String) -> Unit = {}
        var onStop: () -> Unit = {}
        var onResolve: () -> Unit = {}

        private suspend fun record(event: String) { events += event; before(event) }
        override suspend fun loadPlayerState(): SavedPlayerState? { record("load"); return saved }
        override suspend fun savePlayerState(saved: SavedPlayerState) {
            record("save:${saved.activePlaylistId}")
            savedStates += saved
        }
        override suspend fun playlists(): List<SavedPlaylist> { record("lists"); return lists.values.toList() }
        override suspend fun defaultPlaylistId(name: String): Long {
            record("default:$name")
            if (lists.isEmpty()) lists[1] = SavedPlaylist(1, name)
            return lists.keys.first()
        }
        override suspend fun tracksIn(id: Long): List<TrackRef> { record("tracks:$id"); return rows[id].orEmpty() }
        override suspend fun replaceTracks(id: Long, tracks: List<TrackRef>) {
            record("replace:$id")
            rows[id] = tracks
            writes += id to tracks
            lists[id]?.let { lists[id] = it.copy(trackCount = tracks.size) }
        }
        override suspend fun createPlaylist(name: String): Long {
            record("create:$name")
            lists[99] = SavedPlaylist(99, name)
            return 99
        }
        override suspend fun renamePlaylist(id: Long, name: String) {
            record("rename:$id:$name")
            lists[id]?.let { lists[id] = it.copy(name = name) }
        }
        override suspend fun deletePlaylist(id: Long) { record("delete:$id"); lists.remove(id); rows.remove(id) }
    }

    private suspend fun CoroutineScope.restore(f: Fixture) {
        f.session.restore()
        drain()
        f.events.clear()
    }

    @Test
    fun `construction neither reads storage nor publishes state nor starts playback work`() = runBlocking {
        val f = Fixture(this)
        yield()
        assertTrue(f.events.isEmpty())
        assertEquals(0L, f.session.playlistId)
        assertFalse(f.player.value.restored)
        assertTrue(coroutineContext.job.children.none())
    }

    @Test(timeout = 5_000)
    fun `restore selects the saved playlist before awaiting its tracks and publishes before resolving metadata`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val a = track("a")
        val b = track("b")
        val f = Fixture(this).apply {
            saved = SavedPlayerState(2, "b", true, RepeatMode.ONE, true, "platform:c64", 305)
            rows[2] = listOf(a, b)
            before = { if (it == "tracks:2") { entered.complete(Unit); gate.await() } }
            onResolve = {
                assertTrue(player.value.restored)
                assertEquals(b, player.value.queue.current)
                assertEquals(RandomScope.OnPlatform("c64"), browse.value.randomScope)
            }
        }
        f.session.restore()
        entered.await()
        assertEquals(2L, f.session.playlistId)
        assertEquals(0L, f.player.value.activePlaylistId)
        assertFalse(f.player.value.restored)
        f.player.value = f.player.value.copy(scanning = true, shareAudioMinutes = 7)
        gate.complete(Unit)
        drain()
        assertEquals(listOf("load", "lists", "tracks:2", "resolve"), f.events)
        val state = f.player.value
        assertEquals(listOf(a, b), state.queue.tracks)
        assertEquals(b, state.queue.current)
        assertTrue(state.queue.shuffle)
        assertEquals(RepeatMode.ONE, state.queue.repeat)
        assertTrue(state.playAllSubsongs)
        assertEquals(300, state.fallbackLengthSeconds)
        assertTrue(state.scanning)
        assertEquals(7, state.shareAudioMinutes)
        assertFalse(state.playing)
        assertTrue(f.savedStates.isEmpty())
        assertTrue(f.writes.isEmpty())
    }

    @Test
    fun `missing saved playlists fall back and reload lists with current locale and compatible defaults`() = runBlocking {
        for (missing in listOf<SavedPlayerState?>(null, SavedPlayerState(77, "gone", false, RepeatMode.OFF))) {
            val f = Fixture(this).apply {
                saved = missing
                lists.clear()
                rows[1] = listOf(track("a"))
                player.value = player.value.copy(queue = PlayQueue(emptyList(), shuffle = true, repeat = RepeatMode.ONE))
            }
            f.session.restore()
            f.language = "pl"
            drain()
            assertEquals(listOf("load", "lists", "default:pl default", "lists", "tracks:1", "resolve"), f.events)
            assertEquals(1L, f.session.playlistId)
            assertEquals(listOf(SavedPlaylist(1, "pl default")), f.player.value.playlists)
            assertEquals(1L, f.player.value.activePlaylistId)
            assertNull(f.player.value.queue.current)
            assertTrue(f.player.value.queue.shuffle)
            assertEquals(RepeatMode.OFF, f.player.value.queue.repeat)
            assertEquals(FallbackLength.DEFAULT_SECONDS, f.player.value.fallbackLengthSeconds)
            assertEquals(RandomScope.Everything, f.browse.value.randomScope)
        }
    }

    @Test(timeout = 5_000)
    fun `scheduled settings writes cannot replace an unrestored session`() = runBlocking {
        val f = Fixture(this)
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("unsaved"))).startAt(0))
        f.session.scheduleSave()
        drain()
        assertTrue(f.savedStates.isEmpty())
        assertTrue(f.events.isEmpty())
        assertFalse(f.player.value.restored)
    }

    @Test(timeout = 5_000)
    fun `settings writes debounce and sample the latest queue and modes rather than the transient track`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("old"))).startAt(0))
        f.session.scheduleSave()
        yield()
        f.session.scheduleSave()
        val started = System.nanoTime()
        yield()
        f.player.value = f.player.value.copy(
            queue = PlayQueue(listOf(track("new")), shuffle = true, repeat = RepeatMode.PLAYLIST).startAt(0),
            transient = track("random"), resultsQueue = PlayQueue(listOf(track("result"))).startAt(0),
            playAllSubsongs = true, fallbackLengthSeconds = 420, dirty = true,
        )
        f.browse.value = f.browse.value.copy(randomScope = RandomScope.Favourites)
        drain()
        assertTrue("The production debounce must remain 400 ms", (System.nanoTime() - started) / 1_000_000 >= 390)
        assertEquals(listOf(SavedPlayerState(1, "new", true, RepeatMode.PLAYLIST, true, "favourites", 420)), f.savedStates)
        assertTrue(f.writes.isEmpty())
        assertTrue(f.player.value.dirty)
        assertEquals(listOf("save:1"), f.events)
    }

    @Test(timeout = 6_000)
    fun `pending track writes survive switching and sample the destination queue after the debounce`() = runBlocking {
        val f = Fixture(this).apply { rows[1] = listOf(track("old")); rows[2] = listOf(track("new")) }
        restore(f)
        f.session.scheduleTrackWrite()
        yield()
        f.session.scheduleTrackWrite()
        val started = System.nanoTime()
        val switched = CompletableDeferred<Unit>()
        f.onResolve = { switched.complete(Unit) }
        f.session.switchToPlaylist(2)
        switched.await()
        val resolved = track("new").copy(title = "Resolved")
        f.player.value = f.player.value.copy(queue = f.player.value.queue.withTracks(listOf(resolved)))
        drain()
        assertTrue("The production track debounce must remain 1500 ms", (System.nanoTime() - started) / 1_000_000 >= 1490)
        assertEquals(listOf(2L to listOf(resolved)), f.writes)
        assertEquals(listOf("save:1", "stop", "tracks:2", "lists", "resolve", "save:2", "replace:2", "lists"), f.events)
        assertEquals(1, f.player.value.playlists.first { it.id == 2L }.trackCount)
    }

    @Test(timeout = 5_000)
    fun `a pending track write uses the new persistence ID even while the previous queue is still displayed`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val written = CompletableDeferred<Unit>()
        val old = listOf(track("old"))
        val f = Fixture(this).apply { rows[1] = old; rows[2] = listOf(track("destination")) }
        restore(f)
        f.before = {
            if (it == "tracks:2") { entered.complete(Unit); gate.await() }
            if (it.startsWith("replace:")) written.complete(Unit)
        }
        f.session.scheduleTrackWrite()
        yield()
        f.session.switchToPlaylist(2)
        entered.await()
        written.await()
        assertEquals(2L, f.session.playlistId)
        assertEquals(1L, f.player.value.activePlaylistId)
        assertEquals(old, f.player.value.queue.tracks)
        assertEquals(listOf(2L to old), f.writes)
        gate.complete(Unit)
        drain()
        assertEquals(old, f.rows[2])
    }

    @Test(timeout = 5_000)
    fun `explicit save keeps its initial tracks but clears dirty on the current state without refreshing lists`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this)
        restore(f)
        val initial = listOf(track("initial"))
        f.player.value = f.player.value.copy(queue = PlayQueue(initial), dirty = true)
        f.before = { if (it == "replace:1") { entered.complete(Unit); gate.await() } }
        f.session.savePlaylist()
        entered.await()
        assertTrue("An unfinished write must not report the edits as saved", f.player.value.dirty)
        val during = f.player.value.copy(queue = PlayQueue(listOf(track("during-write"))), scanning = true, dirty = true)
        f.player.value = during
        gate.complete(Unit)
        drain()
        assertEquals(listOf(1L to initial), f.writes)
        assertEquals(during.copy(dirty = false), f.player.value)
        assertEquals(listOf("replace:1"), f.events)
        assertTrue(f.notices.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `discard applies stored tracks to the current queue without stopping playback or writing storage`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val a = track("a")
        val b = track("b")
        val f = Fixture(this).apply { rows[1] = listOf(a, b) }
        restore(f)
        f.before = { if (it == "tracks:1") { entered.complete(Unit); gate.await() } }
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(a)), playing = true, dirty = true, positionSeconds = 42.0)
        f.session.discardChanges()
        entered.await()
        f.language = "pl"
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(a, b), repeat = RepeatMode.ONE).startAt(1))
        val before = f.player.value
        gate.complete(Unit)
        drain()
        assertEquals(before.copy(queue = before.queue.withTracks(listOf(a, b)), dirty = false, message = f.player.value.message), f.player.value)
        assertEquals("pl:CHANGES_DISCARDED", f.player.value.message?.text)
        assertEquals(listOf(PlaylistSessionState.Notice.CHANGES_DISCARDED), f.notices)
        assertEquals(listOf("tracks:1"), f.events)
        assertTrue(f.writes.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `creating a playlist resolves blank names when the job runs then refreshes and switches`() = runBlocking {
        for (name in listOf("  ", "  Custom  ")) {
            val f = Fixture(this)
            restore(f)
            f.session.createPlaylist(name)
            f.language = "pl"
            drain()
            val expectedName = if (name.isBlank()) "pl default" else name
            assertEquals(listOf("create:$expectedName", "lists", "save:1", "stop", "tracks:99", "lists", "resolve", "save:99"), f.events)
            assertEquals(expectedName, f.player.value.activePlaylistName)
            assertEquals(99L, f.session.playlistId)
            assertTrue(f.writes.isEmpty())
        }
    }

    @Test
    fun `rename ignores blanks and otherwise keeps the supplied name without switching or saving`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        f.session.renamePlaylist(2, " \t ")
        drain()
        assertTrue(f.events.isEmpty())
        val before = f.player.value
        f.session.renamePlaylist(2, "  Renamed  ")
        drain()
        assertEquals(listOf("rename:2:  Renamed  ", "lists"), f.events)
        assertEquals(before.copy(playlists = f.lists.values.toList()), f.player.value)
        assertEquals(1L, f.session.playlistId)
    }

    @Test
    fun `deleting the last playlist resets it in order while retaining unrelated state and playback modes`() = runBlocking {
        for (count in listOf(0, 1)) {
            val f = Fixture(this)
            restore(f)
            f.lists.remove(2)
            if (count == 0) f.lists.clear()
            f.player.value = f.player.value.copy(
                queue = PlayQueue(listOf(track("a")), shuffle = true, repeat = RepeatMode.ONE),
                playing = true, dirty = true, metadata = mapOf("title" to "Old"),
                positionSeconds = 42.0, durationSeconds = 99.0, transient = track("random"),
                subsong = 3, loadingTrack = true,
            )
            val before = f.player.value
            f.language = "pl"
            f.session.deletePlaylist(1)
            drain()
            assertEquals(listOf("lists", "replace:1", "rename:1:pl default", "stop", "lists"), f.events)
            assertEquals(listOf(1L to emptyList<TrackRef>()), f.writes)
            assertEquals(before.copy(
                queue = PlayQueue(emptyList(), shuffle = true, repeat = RepeatMode.ONE),
                playlists = f.lists.values.toList(), playing = false, dirty = false, metadata = emptyMap(),
                positionSeconds = 0.0, durationSeconds = 0.0, message = f.player.value.message,
            ), f.player.value)
            assertEquals("pl:PLAYLIST_EMPTIED", f.player.value.message?.text)
            assertEquals(listOf(PlaylistSessionState.Notice.PLAYLIST_EMPTIED), f.notices)
            assertEquals(1L, f.session.playlistId)
            assertTrue(f.savedStates.isEmpty())
        }
    }

    @Test
    fun `deleting an inactive playlist only refreshes the list`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        val before = f.player.value
        f.session.deletePlaylist(2)
        drain()
        assertEquals(listOf("lists", "delete:2", "lists"), f.events)
        assertEquals(before.copy(playlists = f.lists.values.toList()), f.player.value)
        assertEquals(1L, f.session.playlistId)
        assertTrue(f.savedStates.isEmpty())
        assertTrue(f.writes.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `deleting the active playlist switches to the first remaining list and retains save ordering`() = runBlocking {
        val f = Fixture(this).apply { lists[3] = SavedPlaylist(3, "Third"); rows[2] = listOf(track("b")) }
        restore(f)
        f.session.deletePlaylist(1)
        drain()
        assertEquals(listOf("lists", "delete:1", "lists", "save:1", "stop", "tracks:2", "lists", "resolve", "save:2"), f.events)
        assertEquals(2L, f.session.playlistId)
        assertEquals(listOf(track("b")), f.player.value.queue.tracks)
        assertEquals(listOf(1L, 2L), f.savedStates.map { it.activePlaylistId })
    }

    @Test(timeout = 5_000)
    fun `switch saves settings before stop exposes the ID before the read and abandons only playlist edits`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val old = track("old")
        val next = track("next")
        val f = Fixture(this).apply { rows[1] = listOf(old); rows[2] = listOf(next) }
        restore(f)
        f.player.value = f.player.value.copy(
            queue = PlayQueue(listOf(old), shuffle = true, repeat = RepeatMode.ONE).startAt(0),
            playing = true, dirty = true, metadata = mapOf("format" to "test"),
            positionSeconds = 12.0, durationSeconds = 34.0, transient = track("random"), subsong = 2,
        )
        f.onStop = { assertEquals(1L, f.session.playlistId); assertEquals("old", f.savedStates.single().currentTrackId) }
        f.before = { if (it == "tracks:2") { entered.complete(Unit); gate.await() } }
        f.session.switchToPlaylist(2)
        entered.await()
        assertEquals(2L, f.session.playlistId)
        assertEquals(1L, f.player.value.activePlaylistId)
        f.player.value = f.player.value.copy(scanning = true)
        val before = f.player.value
        gate.complete(Unit)
        drain()
        assertEquals(before.copy(
            queue = PlayQueue(listOf(next), shuffle = true, repeat = RepeatMode.ONE), playlists = f.lists.values.toList(),
            activePlaylistId = 2, playing = false, dirty = false, metadata = emptyMap(), positionSeconds = 0.0, durationSeconds = 0.0,
        ), f.player.value)
        assertEquals(listOf("save:1", "stop", "tracks:2", "lists", "resolve", "save:2"), f.events)
        assertTrue(f.writes.isEmpty())
        assertNull(f.savedStates.last().currentTrackId)
    }

    @Test(timeout = 5_000)
    fun `a failed pre-switch save leaves the playlist and playback untouched`() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        try {
            val f = Fixture(owner)
            owner.restore(f)
            val before = f.player.value
            val failure = IllegalStateException("write refused")
            f.before = { if (it == "save:1") throw failure }
            f.session.switchToPlaylist(2)
            owner.drain()
            assertEquals(listOf("save:1"), f.events)
            assertEquals(1L, f.session.playlistId)
            assertSame(before, f.player.value)
            assertSame(failure, failures.single())
        } finally { owner.cancel() }
    }

    @Test(timeout = 5_000)
    fun `a failed explicit save does not clear dirty or announce success`() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        try {
            val f = Fixture(owner)
            owner.restore(f)
            f.player.value = f.player.value.copy(dirty = true)
            val before = f.player.value
            val failure = IllegalStateException("disk unavailable")
            f.before = { if (it == "replace:1") throw failure }
            f.session.savePlaylist()
            owner.drain()
            assertSame(before, f.player.value)
            assertSame(failure, failures.single())
            assertTrue(f.notices.isEmpty())
            assertTrue(f.writes.isEmpty())
        } finally { owner.cancel() }
    }

    @Test(timeout = 5_000)
    fun `cancelling a pending restore does not publish it or resolve metadata`() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val entered = CompletableDeferred<Unit>()
            val f = Fixture(owner).apply {
                before = { if (it == "tracks:1") { entered.complete(Unit); awaitCancellation() } }
            }
            f.session.restore()
            entered.await()
            owner.cancel()
            owner.coroutineContext.job.join()
            assertEquals(listOf("load", "lists", "tracks:1"), f.events)
            assertEquals(1L, f.session.playlistId)
            assertFalse(f.player.value.restored)
            assertEquals(0L, f.player.value.activePlaylistId)
            assertTrue(f.savedStates.isEmpty())
        } finally { owner.cancel() }
    }

    @Test(timeout = 5_000)
    fun `suspended list publications retry their read if another state update wins the atomic update`() = runBlocking {
        for (action in listOf("reload", "switch", "empty")) {
            val f = Fixture(this)
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            var reads = 0
            val read: suspend () -> List<SavedPlaylist> = {
                reads++
                if (reads == 1) { entered.complete(Unit); gate.await() }
                listOf(SavedPlaylist(2, "Read $reads"))
            }
            launch {
                when (action) {
                    "reload" -> f.state.reloadPlaylists(read)
                    "switch" -> f.state.switchPlaylist(2, listOf(track("b")), read)
                    else -> f.state.emptyPlaylist(read)
                }
            }
            entered.await()
            f.player.value = f.player.value.copy(scanning = true, queue = PlayQueue(emptyList(), repeat = RepeatMode.ONE))
            gate.complete(Unit)
            drain()
            assertEquals("$action must retain the existing retry boundary", 2, reads)
            assertEquals("Read 2", f.player.value.playlists.single().name)
            assertTrue(f.player.value.scanning)
            assertEquals(RepeatMode.ONE, f.player.value.queue.repeat)
        }
    }
}
