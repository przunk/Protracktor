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
    @Test(timeout = 5_000)
    fun `abandoned source navigation cannot publish a late playlist and does not block later saves`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var allowed = true
        var completed = false
        f.before = { if (it == "tracks:2") { entered.complete(Unit); release.await() } }
        f.session.switchToPlaylist(2, preservePlayback = true, canActivate = { allowed }) { completed = true }
        entered.await()
        allowed = false
        release.complete(Unit)
        drain()
        assertFalse(completed)
        assertEquals(1L, f.player.value.activePlaylistId)
        f.player.value = f.player.value.copy(dirty = true)
        f.session.savePlaylist()
        drain()
        assertFalse(f.player.value.dirty)
        assertEquals(1L, f.writes.single().first)
    }

    @Test(timeout = 5_000)
    fun `returning to a source playlist keeps the exact running context and manual requests`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val context = ActiveQueue(QueueSource.Playlist(1), PlayQueue(listOf(track("context-only"))).startAt(0))
        val schedule = PlaybackSchedule().contextSelected(context, SchedulePlacement.REPLACE).enqueue(track("manual"))
        f.player.value = f.player.value.withPlayback(context).copy(schedule = schedule, playing = true, positionSeconds = 12.5)
        f.session.switchToPlaylist(2)
        drain()
        var completed: Boolean? = null
        f.session.switchToPlaylist(1, preservePlayback = true) { completed = it }
        drain()
        assertEquals(true, completed)
        assertEquals(1L, f.player.value.activePlaylistId)
        assertEquals(context.order.tracks, f.player.value.queue.tracks)
        assertTrue("A captured order differing from disk is an unsaved draft, not an automatic write", f.player.value.dirty)
        assertEquals(listOf(track("one")), f.rows[1])
        assertEquals(context, f.player.value.activePlayback)
        assertEquals(schedule, f.player.value.schedule)
        assertTrue(f.player.value.playing)
        assertEquals(12.5, f.player.value.positionSeconds, 0.0)
    }

    @Test(timeout = 5_000)
    fun `source navigation reports a deleted playlist without publishing an empty replacement`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val before = f.player.value
        var completed: Boolean? = null
        f.session.switchToPlaylist(99, preservePlayback = true) { completed = it }
        drain()
        assertEquals(false, completed)
        assertEquals(before, f.player.value)
        assertEquals(1L, f.session.playlistId)
    }

    @Test(timeout = 5_000)
    fun `source navigation reports read failure while retaining the selected editor`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val before = f.player.value
        f.before = { if (it == "tracks:2") throw java.io.IOException("Controlled read failure") }
        var completed: Boolean? = null
        f.session.switchToPlaylist(2, preservePlayback = true) { completed = it }
        drain()
        assertEquals(false, completed)
        assertEquals(before, f.player.value)
        assertEquals(1L, f.session.playlistId)
    }

    private fun activationFixture(scope: CoroutineScope) = Fixture(scope).apply {
        lists[3] = SavedPlaylist(3, "Three")
        rows[1] = listOf(track("one"))
        rows[2] = listOf(track("two"))
        rows[3] = listOf(track("three"))
    }

    @Test(timeout = 5_000)
    fun `publication makes destination writes available before metadata follow-up`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val edit = listOf(track("destination-edit"))
        f.onResolve = {
            assertEquals(2L, f.player.value.activePlaylistId)
            assertEquals(2L, f.session.playlistId)
            f.player.value = f.player.value.copy(queue = PlayQueue(edit), dirty = true)
            f.session.savePlaylist()
        }
        f.session.switchToPlaylist(2)
        drain()
        assertEquals(listOf(2L to edit), f.writes)
        assertFalse(f.player.value.dirty)
        assertEquals(listOf(track("one")), f.rows[1])
    }

    @Test(timeout = 5_000)
    fun `latest activation wins when an older track read finishes last`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val latest = CompletableDeferred<Unit>()
        f.before = { if (it == "tracks:2") { entered.complete(Unit); gate.await() } }
        f.onResolve = { if (f.player.value.activePlaylistId == 3L) latest.complete(Unit) }
        f.session.switchToPlaylist(2)
        entered.await()
        f.session.switchToPlaylist(3)
        latest.await()
        gate.complete(Unit)
        drain()
        assertEquals(3L, f.player.value.activePlaylistId)
        assertEquals(3L, f.session.playlistId)
        assertEquals(listOf(track("three")), f.player.value.queue.tracks)
        assertEquals(1, f.events.count { it == "resolve" })
        assertEquals(3L, f.savedStates.last().activePlaylistId)
        val edit = listOf(track("third-edit"))
        f.player.value = f.player.value.copy(queue = PlayQueue(edit), dirty = true)
        f.session.savePlaylist()
        drain()
        assertEquals(listOf(3L to edit), f.writes)
        assertFalse(f.player.value.dirty)
        assertEquals(listOf(track("two")), f.rows[2])
    }

    @Test(timeout = 5_000)
    fun `latest activation wins when an older playlist-list read finishes last`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val latest = CompletableDeferred<Unit>()
        var holdFirstList = true
        f.before = { if (it == "lists" && holdFirstList) {
            holdFirstList = false
            entered.complete(Unit)
            gate.await()
        } }
        f.onResolve = { if (f.player.value.activePlaylistId == 3L) latest.complete(Unit) }
        f.session.switchToPlaylist(2)
        entered.await()
        f.session.switchToPlaylist(3)
        latest.await()
        gate.complete(Unit)
        drain()
        assertEquals(3L, f.player.value.activePlaylistId)
        assertEquals(3L, f.session.playlistId)
        assertEquals(listOf(track("three")), f.player.value.queue.tracks)
        assertEquals(1, f.events.count { it == "resolve" })
        assertEquals(3L, f.savedStates.last().activePlaylistId)
    }

    @Test(timeout = 5_000)
    fun `superseded settings save cannot stop playback or begin its activation`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var holdFirstSave = true
        f.before = { if (it.startsWith("save:") && holdFirstSave) {
            holdFirstSave = false
            entered.complete(Unit)
            gate.await()
        } }
        f.session.switchToPlaylist(2)
        entered.await()
        f.session.switchToPlaylist(3)
        yield()
        assertEquals(1, f.events.count { it.startsWith("save:") })
        gate.complete(Unit)
        drain()
        assertFalse(f.events.contains("tracks:2"))
        assertEquals(0, f.events.count { it == "stop" })
        assertEquals(1, f.events.count { it == "resolve" })
        assertEquals(3L, f.player.value.activePlaylistId)
        assertEquals(3L, f.session.playlistId)
        assertEquals(3L, f.savedStates.last().activePlaylistId)
    }

    @Test(timeout = 5_000)
    fun `published playlist and persistence target remain aligned while activation loads`() = runBlocking {
        val f = activationFixture(this)
        restore(f)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.before = { if (it == "tracks:2") { entered.complete(Unit); gate.await() } }
        f.session.switchToPlaylist(2)
        entered.await()
        assertEquals(1L, f.player.value.activePlaylistId)
        assertEquals(1L, f.session.playlistId)
        gate.complete(Unit)
        drain()
        assertEquals(2L, f.player.value.activePlaylistId)
        assertEquals(2L, f.session.playlistId)
    }

    @Test(timeout = 5_000)
    fun `failed activation retains an aligned source and allows its next explicit save`() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        try {
            val f = activationFixture(owner)
            owner.restore(f)
            val failure = IllegalStateException("destination read failed")
            f.before = { if (it == "tracks:2") throw failure }
            f.session.switchToPlaylist(2)
            owner.drain()
            assertSame(failure, failures.single())
            assertEquals(1L, f.player.value.activePlaylistId)
            assertEquals(1L, f.session.playlistId)
            val edit = listOf(track("source-edit"))
            f.player.value = f.player.value.copy(queue = PlayQueue(edit), dirty = true)
            f.session.savePlaylist()
            owner.drain()
            assertEquals(listOf(1L to edit), f.writes)
            assertFalse(f.player.value.dirty)
        } finally { owner.cancel() }
    }

    @Test(timeout = 5_000)
    fun `superseded activation failures do not affect the latest selected playlist`() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        try {
            val f = activationFixture(owner)
            owner.restore(f)
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val latest = CompletableDeferred<Unit>()
            f.before = { if (it == "tracks:2") {
                entered.complete(Unit)
                gate.await()
                error("superseded read failed")
            } }
            f.onResolve = { if (f.player.value.activePlaylistId == 3L) latest.complete(Unit) }
            f.session.switchToPlaylist(2)
            entered.await()
            f.session.switchToPlaylist(3)
            latest.await()
            gate.complete(Unit)
            owner.drain()
            assertTrue(failures.isEmpty())
            assertEquals(3L, f.player.value.activePlaylistId)
            assertEquals(3L, f.session.playlistId)
            assertEquals(listOf(track("three")), f.player.value.queue.tracks)
        } finally { owner.cancel() }
    }

    @Test fun `notification save completion follows persistence and captures earlier edits`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        val oldEdit = track("old-edit")
        val played = track("played")
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(oldEdit)), playback = randomPlayback(played), dirty = true)
        val completed = mutableListOf<PlaylistHeartSave.Outcome>()
        val saver = PlaylistHeartSave(f.player,
            save = { done -> f.session.savePlaylist(done) }, resolveMetadata = {},
            notice = { result, _ -> completed += result })
        saver.add(PlaylistHeart.of(f.player.value)!!.request)
        assertTrue(completed.isEmpty())
        drain()
        assertEquals(listOf(PlaylistHeartSave.Outcome.ADDED_AND_SAVED), completed)
        assertEquals(listOf(oldEdit, played), f.rows[1])
        assertFalse(f.player.value.dirty)
    }

    @Test fun `notification save failure reports false and keeps unsaved contents`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        val edit = track("edit")
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(edit)), dirty = true)
        f.before = { if (it == "replace:1") error("storage refused") }
        val completed = mutableListOf<Boolean>()
        f.session.savePlaylist { completed += it }
        drain()
        assertEquals(listOf(false), completed)
        assertTrue(f.player.value.dirty)
        assertEquals(listOf(edit), f.player.value.queue.tracks)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun `notification save rejects an unrestored target with completion feedback`() = runBlocking {
        val f = Fixture(this)
        val completed = mutableListOf<Boolean>()
        f.session.savePlaylist { completed += it }
        drain()
        assertEquals(listOf(false), completed)
        assertTrue(f.writes.isEmpty())
    }

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
            playback = randomPlayback(track("random"), shuffle = true, repeat = RepeatMode.PLAYLIST),
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
    fun `pending metadata writes retain their source playlist and snapshot after switching`() = runBlocking {
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
        assertEquals(listOf(1L to listOf(track("old"))), f.writes)
        assertEquals(listOf(track("new")), f.rows[2])
        assertEquals(listOf(resolved), f.player.value.queue.tracks)
        assertEquals(listOf("save:1", "tracks:2", "lists", "resolve", "save:2", "replace:1", "lists"), f.events)
        assertEquals(1, f.player.value.playlists.first { it.id == 1L }.trackCount)
        assertEquals(0, f.player.value.playlists.first { it.id == 2L }.trackCount)
    }

    @Test(timeout = 5_000)
    fun `a pending metadata write cannot replace destination contents during a suspended switch`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val written = CompletableDeferred<Unit>()
        val old = listOf(track("old"))
        val destination = listOf(track("destination"))
        val f = Fixture(this).apply { rows[1] = old; rows[2] = destination }
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
        assertEquals(1L, f.session.playlistId)
        assertEquals(1L, f.player.value.activePlaylistId)
        assertEquals(old, f.player.value.queue.tracks)
        assertEquals(listOf(1L to old), f.writes)
        gate.complete(Unit)
        drain()
        assertEquals(destination, f.rows[2])
        assertEquals(destination, f.player.value.queue.tracks)
    }

    @Test(timeout = 5_000)
    fun `save completion keeps a newer edit dirty without changing its state or the saved snapshot`() = runBlocking {
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
        assertEquals(during, f.player.value)
        assertEquals(listOf("replace:1"), f.events)
        assertTrue(f.notices.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `metadata debounce retains the requested snapshot rather than persisting a later unsaved edit`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        val resolved = listOf(track("resolved").copy(title = "Known title"))
        f.player.value = f.player.value.copy(queue = PlayQueue(resolved))
        f.session.scheduleTrackWrite()
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("edit"))), dirty = true)
        drain()
        assertEquals(listOf(1L to resolved), f.writes)
        assertEquals(listOf(track("edit")), f.player.value.queue.tracks)
        assertTrue(f.player.value.dirty)
    }

    @Test(timeout = 5_000)
    fun `repeated metadata requests debounce to the latest snapshot of the same playlist`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("old"))))
        f.session.scheduleTrackWrite()
        yield()
        val latest = listOf(track("latest"))
        f.player.value = f.player.value.copy(queue = PlayQueue(latest))
        f.session.scheduleTrackWrite()
        drain()
        assertEquals(listOf(1L to latest), f.writes)
    }

    @Test(timeout = 5_000)
    fun `save captures the clicked playlist contents before its coroutine starts`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        val clicked = listOf(track("clicked"))
        f.player.value = f.player.value.copy(queue = PlayQueue(clicked), dirty = true)
        f.session.savePlaylist()
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("later"))))
        drain()
        assertEquals(listOf(1L to clicked), f.writes)
        assertTrue(f.player.value.dirty)
    }

    @Test(timeout = 5_000)
    fun `successful save clears dirty despite unrelated transport and scan updates`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this)
        restore(f)
        val tracks = listOf(track("a"), track("b"))
        f.player.value = f.player.value.copy(queue = PlayQueue(tracks), dirty = true)
        f.before = { if (it == "replace:1") { entered.complete(Unit); gate.await() } }
        f.session.savePlaylist()
        entered.await()
        f.player.value = f.player.value.copy(
            queue = f.player.value.queue.startAt(1).withRepeat(RepeatMode.ONE),
            playing = true, scanning = true, positionSeconds = 42.0,
        )
        val current = f.player.value
        gate.complete(Unit)
        drain()
        assertEquals(listOf(1L to tracks), f.writes)
        assertEquals(current.copy(dirty = false), f.player.value)
    }

    @Test(timeout = 5_000)
    fun `save completion for a previous playlist cannot clear destination edits`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val switched = CompletableDeferred<Unit>()
        val sameTracks = listOf(track("same"))
        val f = Fixture(this).apply { rows[1] = sameTracks; rows[2] = sameTracks }
        restore(f)
        f.player.value = f.player.value.copy(dirty = true)
        f.before = { if (it == "replace:1") { entered.complete(Unit); gate.await() } }
        f.onResolve = { switched.complete(Unit) }
        f.session.savePlaylist()
        entered.await()
        f.session.switchToPlaylist(2)
        switched.await()
        f.player.value = f.player.value.copy(dirty = true, scanning = true)
        val destination = f.player.value
        gate.complete(Unit)
        drain()
        assertEquals(listOf(1L to sameTracks), f.writes)
        assertEquals(destination, f.player.value)
    }

    @Test(timeout = 5_000)
    fun `explicit save supersedes pending older metadata so it cannot overwrite the saved edit`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("old"))))
        f.session.scheduleTrackWrite()
        yield()
        val edited = listOf(track("edited"))
        f.player.value = f.player.value.copy(queue = PlayQueue(edited), dirty = true)
        f.session.savePlaylist()
        drain()
        assertEquals(listOf(1L to edited), f.writes)
        assertEquals(edited, f.rows[1])
        assertFalse(f.player.value.dirty)
    }

    @Test(timeout = 5_000)
    fun `overlapping saves finish in request order and only the matching completion clears dirty`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this)
        restore(f)
        val initial = listOf(track("initial"))
        val latest = listOf(track("latest"))
        var writesStarted = 0
        f.before = {
            if (it == "replace:1") {
                writesStarted++
                if (writesStarted == 1) { entered.complete(Unit); gate.await() }
                else assertTrue("The older completion must leave the newer edit dirty", f.player.value.dirty)
            }
        }
        f.player.value = f.player.value.copy(queue = PlayQueue(initial), dirty = true)
        f.session.savePlaylist()
        entered.await()
        f.player.value = f.player.value.copy(queue = PlayQueue(latest))
        f.session.savePlaylist()
        yield()
        assertEquals("A newer save must not overtake the suspended older write", 1, writesStarted)
        gate.complete(Unit)
        drain()
        assertEquals(listOf(1L to initial, 1L to latest), f.writes)
        assertEquals(latest, f.rows[1])
        assertFalse(f.player.value.dirty)
    }

    @Test(timeout = 5_000)
    fun `write snapshots do not retain a mutable caller list`() = runBlocking {
        for (metadata in listOf(false, true)) {
            val f = Fixture(this)
            restore(f)
            val callerList = mutableListOf(track("initial"))
            f.player.value = f.player.value.copy(queue = PlayQueue(callerList), dirty = true)
            if (metadata) f.session.scheduleTrackWrite() else f.session.savePlaylist()
            callerList += track("later")
            drain()
            assertEquals(listOf(1L to listOf(track("initial"))), f.writes)
            assertTrue(f.player.value.dirty)
        }
    }

    @Test(timeout = 5_000)
    fun `write requests during a suspended activation cannot pair the destination ID with the old queue`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply { rows[1] = listOf(track("old")); rows[2] = listOf(track("new")) }
        restore(f)
        f.before = { if (it == "tracks:2") { entered.complete(Unit); gate.await() } }
        f.session.switchToPlaylist(2)
        entered.await()
        f.session.savePlaylist()
        f.session.scheduleTrackWrite()
        f.session.writeTracks()
        yield()
        gate.complete(Unit)
        drain()
        assertTrue(f.writes.isEmpty())
        assertEquals(listOf(track("new")), f.rows[2])
    }

    @Test(timeout = 5_000)
    fun `playlist writes cannot overwrite storage before restore has published a queue`() = runBlocking {
        val f = Fixture(this)
        f.session.savePlaylist()
        f.session.scheduleTrackWrite()
        f.session.writeTracks()
        drain()
        assertTrue(f.events.isEmpty())
        assertTrue(f.writes.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `automatic additions capture their owner and contents without marking newer edits saved`() = runBlocking {
        val f = Fixture(this)
        restore(f)
        val added = listOf(track("added"))
        f.player.value = f.player.value.copy(queue = PlayQueue(added))
        f.session.writeTracks()
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("later-edit"))), dirty = true)
        drain()
        assertEquals(listOf(1L to added), f.writes)
        assertTrue(f.player.value.dirty)
        assertEquals(1, f.player.value.playlists.first { it.id == 1L }.trackCount)
    }

    @Test(timeout = 5_000)
    fun `discard cancels a pending snapshot so abandoned edits cannot reappear on disk`() = runBlocking {
        val stored = listOf(track("stored"))
        val f = Fixture(this).apply { rows[1] = stored }
        restore(f)
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("abandoned"))), dirty = true)
        f.session.scheduleTrackWrite()
        yield()
        f.session.discardChanges()
        drain()
        assertTrue(f.writes.isEmpty())
        assertEquals(stored, f.rows[1])
        assertEquals(stored, f.player.value.queue.tracks)
        assertFalse(f.player.value.dirty)
    }

    @Test(timeout = 5_000)
    fun `resetting the last playlist cancels its pending snapshot instead of restoring deleted contents`() = runBlocking {
        val f = Fixture(this).apply { lists.remove(2); rows[1] = listOf(track("removed")) }
        restore(f)
        f.session.scheduleTrackWrite()
        yield()
        f.session.deletePlaylist(1)
        drain()
        assertEquals(listOf(1L to emptyList<TrackRef>()), f.writes)
        assertTrue(f.rows[1].orEmpty().isEmpty())
        assertTrue(f.player.value.queue.tracks.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `deleting the active playlist cancels its pending snapshot and preserves the destination`() = runBlocking {
        val next = listOf(track("next"))
        val f = Fixture(this).apply { rows[1] = listOf(track("removed")); rows[2] = next }
        restore(f)
        f.session.scheduleTrackWrite()
        yield()
        f.session.deletePlaylist(1)
        drain()
        assertTrue(f.writes.isEmpty())
        assertFalse(f.rows.containsKey(1))
        assertEquals(next, f.rows[2])
        assertEquals(next, f.player.value.queue.tracks)
    }

    @Test(timeout = 5_000)
    fun `an in flight save must finish before a last playlist reset so it cannot restore old contents`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply { lists.remove(2); rows[1] = listOf(track("removed")) }
        restore(f)
        var writesStarted = 0
        f.before = {
            if (it == "replace:1" && ++writesStarted == 1) { entered.complete(Unit); gate.await() }
        }
        f.session.savePlaylist()
        entered.await()
        f.session.deletePlaylist(1)
        yield()
        assertEquals(1, writesStarted)
        gate.complete(Unit)
        drain()
        assertEquals(listOf(1L to listOf(track("removed")), 1L to emptyList<TrackRef>()), f.writes)
        assertTrue(f.rows[1].orEmpty().isEmpty())
    }

    @Test(timeout = 5_000)
    fun `metadata arriving during reset cannot restore the old queue afterwards`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply { lists.remove(2); rows[1] = listOf(track("removed")) }
        restore(f)
        f.before = { if (it.startsWith("rename:1:")) { entered.complete(Unit); gate.await() } }
        f.session.deletePlaylist(1)
        entered.await()
        f.session.scheduleTrackWrite()
        gate.complete(Unit)
        drain()
        assertEquals(listOf(1L to emptyList<TrackRef>()), f.writes)
        assertTrue(f.rows[1].orEmpty().isEmpty())
        assertTrue(f.player.value.queue.tracks.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `writes requested during discard cannot restore abandoned contents after its read`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val stored = listOf(track("stored"))
        val f = Fixture(this).apply { rows[1] = stored }
        restore(f)
        f.player.value = f.player.value.copy(queue = PlayQueue(listOf(track("abandoned"))), dirty = true)
        f.before = { if (it == "tracks:1") { entered.complete(Unit); gate.await() } }
        f.session.discardChanges()
        entered.await()
        f.session.writeTracks()
        f.session.savePlaylist()
        yield()
        gate.complete(Unit)
        drain()
        assertTrue(f.writes.isEmpty())
        assertEquals(stored, f.rows[1])
        assertEquals(stored, f.player.value.queue.tracks)
        assertFalse(f.player.value.dirty)
    }

    @Test(timeout = 5_000)
    fun `requests after deletion but before destination publication cannot recreate membership rows`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val next = listOf(track("next"))
        val f = Fixture(this).apply { rows[1] = listOf(track("removed")); rows[2] = next }
        restore(f)
        f.before = { if (it == "lists" && !f.lists.containsKey(1)) { entered.complete(Unit); gate.await() } }
        f.session.deletePlaylist(1)
        entered.await()
        f.session.writeTracks()
        f.session.savePlaylist()
        f.session.scheduleTrackWrite()
        yield()
        gate.complete(Unit)
        drain()
        assertTrue(f.writes.isEmpty())
        assertFalse(f.rows.containsKey(1))
        assertEquals(next, f.rows[2])
    }

    @Test(timeout = 5_000)
    fun `a suspended discard cannot replace the newly activated playlist queue`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val switched = CompletableDeferred<Unit>()
        val next = listOf(track("next"))
        val f = Fixture(this).apply { rows[1] = listOf(track("old")); rows[2] = next }
        restore(f)
        f.before = { if (it == "tracks:1") { entered.complete(Unit); gate.await() } }
        f.onResolve = { switched.complete(Unit) }
        f.session.discardChanges()
        entered.await()
        f.session.switchToPlaylist(2)
        switched.await()
        f.player.value = f.player.value.copy(dirty = true)
        gate.complete(Unit)
        drain()
        assertEquals(next, f.player.value.queue.tracks)
        assertTrue(f.player.value.dirty)
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
        val expected = before.queue.withTracks(listOf(a, b))
        assertEquals(before.copy(queue = expected, playback = before.activePlayback.copy(order = expected),
            dirty = false, message = f.player.value.message), f.player.value)
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
            assertEquals(listOf("create:$expectedName", "lists", "save:1", "tracks:99", "lists", "resolve", "save:99"), f.events)
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
                positionSeconds = 42.0, durationSeconds = 99.0, playback = randomPlayback(track("random")),
                subsong = 3, loadingTrack = true,
            )
            val before = f.player.value
            f.language = "pl"
            f.session.deletePlaylist(1)
            drain()
            assertEquals(listOf("lists", "replace:1", "rename:1:pl default", "lists"), f.events)
            assertEquals(listOf(1L to emptyList<TrackRef>()), f.writes)
            assertEquals(before.copy(
                queue = PlayQueue(emptyList(), shuffle = true, repeat = RepeatMode.ONE),
                playlists = f.lists.values.toList(), dirty = false, message = f.player.value.message,
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
        assertEquals(listOf("lists", "delete:1", "lists", "save:1", "tracks:2", "lists", "resolve", "save:2"), f.events)
        assertEquals(2L, f.session.playlistId)
        assertEquals(listOf(track("b")), f.player.value.queue.tracks)
        assertEquals(listOf(1L, 2L), f.savedStates.map { it.activePlaylistId })
    }

    @Test(timeout = 5_000)
    fun `switch preserves playback while publishing a different selected playlist and abandoning its edits`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val old = track("old")
        val next = track("next")
        val f = Fixture(this).apply { rows[1] = listOf(old); rows[2] = listOf(next) }
        restore(f)
        f.player.value = f.player.value.copy(
            queue = PlayQueue(listOf(old), shuffle = true, repeat = RepeatMode.ONE).startAt(0),
            playing = true, dirty = true, metadata = mapOf("format" to "test"),
            positionSeconds = 12.0, durationSeconds = 34.0, playback = randomPlayback(track("random"), shuffle = true, repeat = RepeatMode.ONE), subsong = 2,
        )
        f.onStop = { assertEquals(1L, f.session.playlistId); assertEquals("old", f.savedStates.single().currentTrackId) }
        f.before = { if (it == "tracks:2") { entered.complete(Unit); gate.await() } }
        f.session.switchToPlaylist(2)
        entered.await()
        assertEquals(1L, f.session.playlistId)
        assertEquals(1L, f.player.value.activePlaylistId)
        f.player.value = f.player.value.copy(scanning = true)
        val before = f.player.value
        gate.complete(Unit)
        drain()
        assertEquals(before.copy(
            queue = PlayQueue(listOf(next), shuffle = true, repeat = RepeatMode.ONE), playlists = f.lists.values.toList(),
            activePlaylistId = 2, dirty = false,
        ), f.player.value)
        assertEquals(listOf("save:1", "tracks:2", "lists", "resolve", "save:2"), f.events)
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
        for (action in listOf("reload", "empty")) {
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
