// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.UnExoticA
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFilesTest {
    @Test fun `retained AMP references cannot fall through to document or network reads`() = runBlocking {
        val access = Access(byteArrayOf(1))
        val files = TrackFiles(access, Dispatchers.Default) {}
        assertNull(files.read(track("amp://A/Artist/MOD.song.gz")))
        assertTrue(access.calls.isEmpty())
    }

    private val payload = byteArrayOf(1, 2, 3)
    private fun track(id: String, name: String = id.substringAfterLast('/')) =
        TrackRef(id = id, title = name, fileName = name)

    private class Access(val bytes: ByteArray) : TrackFiles.Access {
        val calls = mutableListOf<String>()
        var fetched: ByteArray? = bytes
        var extracted: ByteArray? = bytes
        var document: ByteArray? = bytes
        var fetchFailure: CancellationException? = null
        var readThread: Thread? = null
        var extractThread: Thread? = null

        override suspend fun fetch(url: String): ByteArray? {
            calls += "fetch:$url"
            fetchFailure?.let { throw it }
            return fetched
        }

        override suspend fun readArchive(catalogueId: String, entry: String): ByteArray? {
            calls += "archive:$catalogueId:$entry"
            return bytes
        }

        override fun readDocument(id: String): ByteArray? {
            readThread = Thread.currentThread()
            calls += "document:$id"
            return document
        }

        override fun readDocumentCompanions(id: String): List<Pair<String, ByteArray>> {
            readThread = Thread.currentThread()
            calls += "companions:$id"
            return listOf("smpl.song" to bytes)
        }

        override fun decodeFileName(name: String): String {
            calls += "decode:$name"
            return name.replace("%20", " ")
        }

        override fun extractLha(archive: ByteArray, member: String): ByteArray? {
            assertSame(bytes, archive)
            extractThread = Thread.currentThread()
            calls += "extract:$member"
            return extracted
        }

        override fun reportMissingMember(archiveUrl: String, member: String) {
            calls += "missing:$archiveUrl:$member"
        }
    }

    private fun files(access: Access) = TrackFiles(access, Dispatchers.Unconfined) { access.calls += "refreshed" }

    @Test
    fun `an HTTP read refreshes after success on the caller thread`() = runBlocking {
        val access = Access(payload)
        val caller = Thread.currentThread()
        val files = TrackFiles(access, Dispatchers.Unconfined) {
            assertSame(caller, Thread.currentThread())
            access.calls += "refreshed"
        }
        assertSame(payload, files.read(track("https://example.org/song.mod")))
        assertEquals(listOf("fetch:https://example.org/song.mod", "refreshed"), access.calls)
    }

    @Test
    fun `failed or cancelled downloads do not refresh and cancellation propagates`() = runBlocking {
        val access = Access(payload).apply { fetched = null }
        assertNull(files(access).read(track("http://example.org/song.mod")))
        val cancellation = CancellationException("caller left")
        access.fetchFailure = cancellation
        val failure = runCatching { files(access).read(track("https://example.org/song.mod")) }.exceptionOrNull()
        assertSame(cancellation, failure)
        assertTrue(access.calls.all { it.startsWith("fetch:") })
    }

    @Test
    fun `a downloaded catalogue reads its stored archive without fetching or refreshing`() = runBlocking {
        val access = Access(payload)
        assertSame(payload, files(access).read(track("asma://Composers/Song.sap")))
        assertEquals(listOf("archive:asma:Composers/Song.sap"), access.calls)
    }

    @Test
    fun `local reads use IO and leave cache marks alone including on failure`() = runBlocking {
        val access = Access(payload)
        val id = "content://music/document/42"
        assertSame(payload, files(access).read(track(id)))
        assertNotSame(Thread.currentThread(), access.readThread)
        access.document = null
        assertNull(files(access).read(track(id)))
        assertEquals(listOf("document:$id", "document:$id"), access.calls)
    }

    @Test
    fun `UnExoticA extracts on the supplied archive dispatcher then refreshes on the caller`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { archiveWork ->
            val archiveThread = withContext(archiveWork) { Thread.currentThread() }
            val access = Access(payload)
            val path = "Game/Artist/Title.lha/Music/mod.song"
            val caller = Thread.currentThread()
            val files = TrackFiles(access, archiveWork) {
                assertSame(caller, Thread.currentThread())
                access.calls += "refreshed"
            }
            assertSame(payload, files.read(track("unexotica://$path")))
            assertSame(archiveThread, access.extractThread)
            assertEquals(listOf("fetch:${UnExoticA.archiveUrlFor(path)}", "extract:Music/mod.song", "refreshed"), access.calls)
        }
    }

    @Test
    fun `bad archive paths and failed fetches stop before extraction`() = runBlocking {
        val access = Access(payload)
        assertNull(files(access).read(track("unexotica://Game/mod.song")))
        assertTrue(access.calls.isEmpty())
        access.fetched = null
        assertNull(files(access).read(track("unexotica://Game/Title.lha/mod.song")))
        assertEquals(listOf("fetch:${UnExoticA.archiveUrlFor("Game/Title.lha/mod.song")}"), access.calls)
    }

    @Test
    fun `an absent main archive member is reported without refreshing`() = runBlocking {
        val access = Access(payload).apply { extracted = null }
        val path = "Game/Title.lha/mod.song"
        val url = UnExoticA.archiveUrlFor(path)
        assertNull(files(access).read(track("unexotica://$path")))
        assertEquals(listOf("fetch:$url", "extract:mod.song", "missing:$url:mod.song"), access.calls)
    }

    @Test
    fun `single-file formats do not perform any companion IO`() = runBlocking {
        val access = Access(payload)
        for (id in listOf("https://example.org/song.mod", "asma://song.sap", "unexotica://Game/Title.lha/mod.song")) {
            assertTrue(files(access).companions(track(id)).isEmpty())
        }
        assertTrue(access.calls.isEmpty())
    }

    @Test
    fun `HTTP companions use encoded sibling URLs and decoded names without refreshing`() = runBlocking {
        val access = Access(payload)
        val result = files(access).companions(track("https://example.org/Music/mdat.a%20song", "mdat.a song"))
        assertEquals("smpl.a song", result.single().first)
        assertSame(payload, result.single().second)
        assertEquals(listOf("fetch:https://example.org/Music/smpl.a%20song", "decode:smpl.a%20song"), access.calls)
        access.calls.clear()
        access.fetched = null
        assertTrue(files(access).companions(track("https://example.org/mdat.song")).isEmpty())
        assertEquals(listOf("fetch:https://example.org/smpl.song"), access.calls)
    }

    @Test
    fun `LHA companions preserve the member directory and run on the archive dispatcher`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { archiveWork ->
            val archiveThread = withContext(archiveWork) { Thread.currentThread() }
            val access = Access(payload)
            val path = "Game/Title.lha/Music/mdat.song"
            val files = TrackFiles(access, archiveWork) { error("Companions must not refresh") }
            val result = files.companions(track("unexotica://$path"))
            assertEquals("smpl.song", result.single().first)
            assertSame(payload, result.single().second)
            assertSame(archiveThread, access.extractThread)
            assertEquals(listOf("fetch:${UnExoticA.archiveUrlFor(path)}", "extract:Music/smpl.song"), access.calls)
            access.calls.clear()
            access.extracted = null
            assertTrue(files.companions(track("unexotica://$path")).isEmpty())
            assertTrue(access.calls.none { it.startsWith("missing:") })
        }
    }

    @Test
    fun `a document companion can be requested by display name even when its URI is opaque`() = runBlocking {
        val access = Access(payload)
        val id = "content://music/document/42"
        val result = files(access).companions(track(id, "mdat.song"))
        assertEquals("smpl.song", result.single().first)
        assertSame(payload, result.single().second)
        assertEquals(listOf("companions:$id"), access.calls)
        assertNotSame(Thread.currentThread(), access.readThread)
    }

    @Test
    fun `the URI can request a companion even when the title cannot and unsupported sources are skipped`() = runBlocking {
        val access = Access(payload)
        assertEquals(1, files(access).companions(track("https://example.org/mdat.song", "Song")).size)
        access.calls.clear()
        assertTrue(files(access).companions(track("file:///Music/mdat.song")).isEmpty())
        assertTrue(files(access).companions(track("unexotica://Game/mdat.song")).isEmpty())
        assertTrue(access.calls.isEmpty())
    }
}
