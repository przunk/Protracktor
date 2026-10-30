// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.PlaylistFile
import com.przunk.protracktor.data.SavedPlaylist
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTransferTest {
    private fun track(id: String = "https://example.org/one.mod") = TrackRef(id, "One", fileName = "one.mod", author = "Artist")
    private suspend fun CoroutineScope.drain() = coroutineContext.job.children.toList().joinAll()

    private class Fixture(scope: CoroutineScope) : PlaylistTransfer.Store, PlaylistTransfer.Files, PlaylistTransfer.Ui {
        val transfer = PlaylistTransfer(scope, this, this, this)
        var selection = PlaylistTransfer.ExportSelection(1, emptyList(), listOf(SavedPlaylist(1, "Mix/One?"), SavedPlaylist(2, "Other")))
        val stored = mutableMapOf<Long, List<TrackRef>>()
        var known = emptyList<TrackRef>()
        val matches = mutableMapOf<Pair<String, Long>, TrackRef>()
        var input: String? = "#EXTM3U"
        var label = "primary:Download/Fallback.m3u8"
        var copyUri: String? = "content://shares/playlist"
        var copied: Pair<String, ByteArray>? = null
        val events = mutableListOf<String>()
        val notices = mutableListOf<PlaylistTransfer.Notice>()
        val shares = mutableListOf<Pair<String, String>>()
        val writes = mutableListOf<Pair<Long, List<TrackRef>>>()
        var beforeStored: suspend () -> Unit = {}
        var beforeRead: suspend () -> Unit = {}
        var replaceFailure: Throwable? = null

        override suspend fun tracksIn(id: Long): List<TrackRef> {
            events += "tracks:$id"
            beforeStored()
            return stored[id].orEmpty()
        }
        override suspend fun allTracks(): List<TrackRef> { events += "known"; return known }
        override suspend fun findByFile(name: String, size: Long): TrackRef? {
            events += "match:$name:$size"
            return matches[name to size]
        }
        override suspend fun createPlaylist(name: String): Long { events += "create:$name"; return 99 }
        override suspend fun replaceTracks(id: Long, tracks: List<TrackRef>) {
            events += "replace:$id"
            replaceFailure?.let { throw it }
            writes += id to tracks
        }
        override suspend fun read(uri: String): String? { events += "read:$uri"; beforeRead(); return input }
        override fun labelOf(uri: String): String { events += "label:$uri"; return label }
        override fun shareableCopy(name: String, bytes: ByteArray): String? {
            events += "copy:$name"
            copied = name to bytes
            return copyUri
        }
        override fun exportSelection(): PlaylistTransfer.ExportSelection { events += "selection"; return selection }
        override suspend fun refreshPlaylists() { events += "refresh" }
        override fun say(notice: PlaylistTransfer.Notice) { events += "notice"; notices += notice }
        override fun share(uri: String, subject: String) { events += "share"; shares += uri to subject }
    }

    @Test
    fun `export of the active playlist uses unsaved queue contents and sanitizes only the shared filename`() = runBlocking {
        val rows = listOf(track())
        val f = Fixture(this).apply {
            selection = selection.copy(tracks = rows)
            stored[1] = listOf(track("https://example.org/stale.mod"))
        }
        f.transfer.exportPlaylist(1)
        drain()
        assertEquals(listOf("selection", "copy:Mix_One_.m3u8", "share"), f.events)
        assertEquals(PlaylistFile.write("Mix/One?", rows), f.copied!!.second.toString(Charsets.UTF_8))
        assertEquals(listOf("content://shares/playlist" to "Mix_One_"), f.shares)
        assertTrue(f.writes.isEmpty())
        assertTrue(f.notices.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `another playlist reads stored tracks but retains the initial label while that read suspends`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val rows = listOf(track())
        val f = Fixture(this).apply {
            stored[2] = rows
            beforeStored = { entered.complete(Unit); gate.await() }
        }
        f.transfer.exportPlaylist(2)
        entered.await()
        f.selection = f.selection.copy(activePlaylistId = 2, playlists = listOf(SavedPlaylist(2, "Changed")))
        gate.complete(Unit)
        drain()
        assertEquals(listOf("selection", "tracks:2", "copy:Other.m3u8", "share"), f.events)
        assertEquals(PlaylistFile.write("Other", rows), f.copied!!.second.toString(Charsets.UTF_8))
    }

    @Test
    fun `empty exports and copy failures report distinct notices without sharing`() = runBlocking {
        for (id in listOf(1L, 2L)) {
            val f = Fixture(this)
            f.transfer.exportPlaylist(id)
            drain()
            assertEquals(listOf(PlaylistTransfer.Notice.EmptyPlaylist), f.notices)
            assertNull(f.copied)
            assertTrue(f.shares.isEmpty())
        }
        val failed = Fixture(this).apply { selection = selection.copy(tracks = listOf(track())); copyUri = null }
        failed.transfer.exportPlaylist(1)
        drain()
        assertEquals(listOf(PlaylistTransfer.Notice.ExportFailed), failed.notices)
        assertTrue(failed.shares.isEmpty())
    }

    @Test
    fun `an export with no known playlist label keeps the existing fallback name`() = runBlocking {
        val f = Fixture(this).apply { stored[42] = listOf(track()) }
        f.transfer.exportPlaylist(42)
        drain()
        assertEquals("playlist.m3u8", f.copied!!.first)
        assertEquals("playlist", PlaylistFile.nameIn(f.copied!!.second.toString(Charsets.UTF_8)))
        assertEquals("playlist", f.shares.single().second)
    }

    @Test
    fun `unreadable or blank input differs from a readable file without entries and neither writes data`() = runBlocking {
        for (text in listOf(null, "", " \n", "#EXTM3U\n# comment")) {
            val f = Fixture(this).apply { input = text }
            f.transfer.importPlaylist("input")
            drain()
            val notice = if (text.isNullOrBlank()) PlaylistTransfer.Notice.UnreadableFile else PlaylistTransfer.Notice.NoTracks
            assertEquals(listOf(notice), f.notices)
            assertEquals(listOf("read:input", "notice"), f.events)
            assertTrue(f.writes.isEmpty())
        }
    }

    @Test
    fun `import resolves exact IDs then remote identities then locations and finally indexed filename and size`() = runBlocking {
        val known = track("content://provider/known").copy(title = "Stored title", sizeBytes = 7)
        val remote = track().copy(title = "Remote", sizeBytes = 8)
        val asma = TrackRef("asma://Music/a.sap", "ASMA", subtitle = "Music", sizeBytes = 9, fileName = "a.sap", author = "S")
        val match = track("content://provider/matched").copy(fileName = "matched.mod", sizeBytes = 10)
        val records = listOf(known.copy(title = "Old title"), remote, asma,
            match.copy(id = "content://other/matched", subtitle = "Somewhere"))
        val f = Fixture(this).apply {
            this.known = listOf(known)
            matches["matched.mod" to 10L] = match
            input = PlaylistFile.write("Roundtrip", records) + "#EXTINF:-1,Anon - Direct\nhttp://example.org/direct.mod\nmissing.mod\n"
        }
        f.transfer.importPlaylist("input")
        drain()
        val written = f.writes.single()
        assertEquals(99L, written.first)
        assertEquals(listOf(known.id, remote.id, asma.id, match.id, "http://example.org/direct.mod"), written.second.map { it.id })
        assertSame(known, written.second[0])
        assertSame(match, written.second[3])
        assertEquals(TrackRef(remote.id, "Remote", "https://example.org", 8, "one.mod", "Artist"), written.second[1])
        assertEquals(asma, written.second[2])
        assertEquals(TrackRef("http://example.org/direct.mod", "Direct", "http://example.org", 0, "direct.mod", "Anon"), written.second[4])
        assertEquals(listOf("read:input", "known", "match:matched.mod:10", "match:missing.mod:0", "create:Roundtrip", "replace:99", "refresh", "notice"), f.events)
        assertEquals(listOf(PlaylistTransfer.Notice.Imported(5, 6, "Roundtrip")), f.notices)
    }

    @Test
    fun `duplicate entries keep their offered count and an exact ID uses the last known row`() = runBlocking {
        val old = track("content://provider/known")
        val latest = old.copy(title = "Latest")
        val f = Fixture(this).apply { known = listOf(old, latest); input = PlaylistFile.write("Duplicates", listOf(old, old)) }
        f.transfer.importPlaylist("input")
        drain()
        assertEquals(listOf(latest, latest), f.writes.single().second)
        assertEquals(listOf(PlaylistTransfer.Notice.Imported(2, 2, "Duplicates")), f.notices)
        assertEquals(1, f.events.count { it == "known" })
    }

    @Test
    fun `unresolved entries still create a new empty playlist and use the existing document-name fallbacks`() = runBlocking {
        for ((label, expected) in listOf("primary:Download/Fallback.m3u8" to "Fallback", "name.with.dots.m3u" to "name.with.dots", "" to "Imported")) {
            val f = Fixture(this).apply { input = "missing.mod\n"; this.label = label }
            f.transfer.importPlaylist("input")
            drain()
            assertEquals(listOf(99L to emptyList<TrackRef>()), f.writes)
            assertEquals(listOf("create:$expected", "replace:99", "refresh", "notice"), f.events.takeLast(4))
            assertEquals(listOf(PlaylistTransfer.Notice.Imported(0, 1, expected)), f.notices)
            assertEquals(1L, f.selection.activePlaylistId)
            assertTrue(f.selection.tracks.isEmpty())
        }
    }

    @Test
    fun `an embedded Unicode name is kept without reading the document label`() = runBlocking {
        val f = Fixture(this).apply { input = PlaylistFile.write("Żółć 🎵", listOf(track())) }
        f.transfer.importPlaylist("input")
        drain()
        assertFalse(f.events.any { it.startsWith("label:") })
        assertEquals(listOf(PlaylistTransfer.Notice.Imported(1, 1, "Żółć 🎵")), f.notices)
    }

    @Test(timeout = 5_000)
    fun `cancelling an input read writes nothing and the supplied scope can import again`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val f = Fixture(this).apply { beforeRead = { entered.complete(Unit); awaitCancellation() } }
        f.transfer.importPlaylist("input")
        entered.await()
        coroutineContext.job.children.single().cancel()
        drain()
        assertEquals(listOf("read:input"), f.events)
        assertTrue(f.writes.isEmpty())
        f.beforeRead = {}
        f.transfer.importPlaylist("input")
        drain()
        assertEquals(listOf(PlaylistTransfer.Notice.NoTracks), f.notices)
    }

    @Test
    fun `a failed replacement propagates without a success notice or list refresh`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, error -> errors += error })
        try {
            val failure = IllegalStateException("synthetic playlist write failure")
            val f = Fixture(scope).apply { input = PlaylistFile.write("New", listOf(track())); replaceFailure = failure }
            f.transfer.importPlaylist("input")
            scope.drain()
            assertEquals(listOf("create:New", "replace:99"), f.events.takeLast(2))
            assertTrue(f.writes.isEmpty())
            assertTrue(f.notices.isEmpty())
            assertEquals(1, errors.size)
            assertSame(failure, errors.single())
            assertTrue(scope.coroutineContext.job.isActive)
        } finally { scope.cancel() }
    }
    @Test fun `native export filenames agree with shared WEB cases`() = runBlocking {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val cases = File(root, "docs/rules/playlist-filename-cases.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
        assertTrue(cases.isNotEmpty())
        for (row in cases) {
            val c = row.split('\t').map { URLDecoder.decode(it, "UTF-8") }
            val f = Fixture(this).apply {
                selection = selection.copy(tracks = listOf(track()), playlists = listOf(SavedPlaylist(1, c[1])))
            }
            f.transfer.exportPlaylist(1)
            drain()
            assertEquals(c[0], c[2], f.copied!!.first)
        }
    }

}
