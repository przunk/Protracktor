// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.net.UnExoticA
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseCacheTest {
    private fun track(name: String, size: Long = 10) = TrackRef("https://example.org/$name.mod", name, sizeBytes = size)

    private class Fixture : BrowseCache.Files, BrowseCache.Progress {
        val cache = BrowseCache(this, { meteredChecks++; metered }, this)
        val cached = mutableSetOf<String>()
        val archives = mutableSetOf<String>()
        val queried = Collections.synchronizedList(mutableListOf<String>())
        val queryThreads = Collections.synchronizedList(mutableListOf<Thread>())
        val progressThreads = mutableListOf<Thread>()
        val started = mutableListOf<String>()
        val finished = mutableListOf<Pair<String, Boolean>>()
        val active = mutableSetOf<String>()
        var clears = 0
        var metered = false
        var meteredChecks = 0
        var queryFailure: Throwable? = null
        var fetch: suspend (String) -> Boolean = { true }

        override fun isCached(url: String): Boolean {
            queryThreads += Thread.currentThread()
            queried += "cache:$url"
            queryFailure?.let { throw it }
            return url in cached
        }
        override fun hasArchive(catalogueId: String): Boolean {
            queryThreads += Thread.currentThread()
            queried += "archive:$catalogueId"
            return catalogueId in archives
        }
        override suspend fun fetch(url: String): Boolean = fetch.invoke(url)
        override fun clear() {
            progressThreads += Thread.currentThread()
            clears++
            active.clear()
        }
        override fun started(url: String) {
            progressThreads += Thread.currentThread()
            started += url
            assertTrue(active.add(url))
        }
        override fun finished(url: String, cached: Boolean) {
            progressThreads += Thread.currentThread()
            assertTrue(active.remove(url))
            finished += url to cached
        }
    }

    @Test
    fun `source checks run on IO and query a whole archive only once per call`() = runBlocking {
        val f = Fixture()
        val remote = track("cached").id
        val missing = track("missing").id
        val amigaPath = "Game/Composer/Title.lha/Title/mod.name"
        val amiga = "unexotica://$amigaPath"
        val archiveUrl = UnExoticA.archiveUrlFor(amigaPath)!!
        val asma = listOf("asma://Music/one.sap", "asma://Music/two.sap")
        val local = listOf("content://provider/music/42", "file:///music/song.mod")
        f.cached += listOf(remote, archiveUrl)
        f.archives += "asma"
        val ids = listOf(remote, remote, missing, amiga) + asma + local + "unsupported"
        assertEquals((listOf(remote, amiga) + asma + local).toSet(), f.cache.onPhoneOf(ids))
        assertEquals(listOf("cache:$remote", "cache:$remote", "cache:$missing", "cache:$archiveUrl", "archive:asma"), f.queried)
        f.queryThreads.forEach { assertNotSame(Thread.currentThread(), it) }
        assertEquals(0, f.clears)

        f.queried.clear()
        f.archives.clear()
        assertTrue(f.cache.onPhoneOf(asma).isEmpty())
        assertEquals(listOf("archive:asma"), f.queried)
    }

    @Test
    fun `only a loaded nonempty Modland author folder supplies a key and row changes change that key`() {
        val row = track("one")
        val summary = CatalogueSummary(Modland.id, "Modland", 1, 0L)
        val browse = BrowseState(openCatalogue = summary, openAuthor = "Author", tracks = listOf(row))
        val key = BrowseCache.Folder(listOf(row))
        assertEquals(key, BrowseCache.folderOf(browse))
        assertNull(BrowseCache.folderOf(browse.copy(openAuthor = null)))
        assertNull(BrowseCache.folderOf(browse.copy(loading = true)))
        assertNull(BrowseCache.folderOf(browse.copy(tracks = emptyList())))
        assertNull(BrowseCache.folderOf(browse.copy(openCatalogue = null)))
        assertNull(BrowseCache.folderOf(browse.copy(openCatalogue = summary.copy(id = "asma"))))
        assertEquals(key, BrowseCache.folderOf(browse.copy(query = "unrelated", cachedHere = setOf(row.id))))
        assertNotEquals(key, BrowseCache.folderOf(browse.copy(tracks = listOf(row.copy(sizeBytes = 20)))))
    }

    @Test
    fun `no eligible folder clears only fetch progress without asking files or network`() = runBlocking {
        val f = Fixture().apply { active += "previous-folder" }
        f.cache.fetchFolderAhead(null, CacheAhead.ALWAYS)
        assertEquals(1, f.clears)
        assertTrue(f.active.isEmpty())
        assertTrue(f.queried.isEmpty())
        assertTrue(f.started.isEmpty())
        assertEquals(0, f.meteredChecks)
    }

    @Test
    fun `every network mode still checks cached rows before deciding whether to fetch`() = runBlocking {
        val row = track("one")
        for (mode in CacheAhead.entries) for (metered in listOf(false, true)) {
            val f = Fixture().apply { this.metered = metered }
            f.cache.fetchFolderAhead(BrowseCache.Folder(listOf(row)), mode)
            assertEquals(listOf("cache:${row.id}"), f.queried)
            val allowed = mode == CacheAhead.ALWAYS || (mode == CacheAhead.WIFI_ONLY && !metered)
            assertEquals(if (allowed) listOf(row.id) else emptyList<String>(), f.started)
            assertEquals(if (allowed) 2 else 1, f.clears)
            assertTrue(f.active.isEmpty())
            f.progressThreads.forEach { assertSame(Thread.currentThread(), it) }
        }
    }

    @Test
    fun `folder planning excludes cached bytes and stops at the existing size budget`() = runBlocking {
        val f = Fixture()
        val held = track("held", 100L * 1024 * 1024)
        val first = track("first", 60L * 1024 * 1024)
        val tooFar = track("too-far", 60L * 1024 * 1024)
        val later = track("later", 1)
        f.cached += held.id
        f.cache.fetchFolderAhead(BrowseCache.Folder(listOf(held, first, tooFar, later)), CacheAhead.ALWAYS)
        assertEquals(listOf(first.id), f.started)
        assertEquals(listOf(first.id to true), f.finished)
        assertEquals(2, f.clears)
    }

    @Test
    fun `each success failure or thrown fetch reports its own result and cleanup leaves no progress`() = runBlocking {
        val rows = listOf("good", "missing", "throws").map { track(it) }
        val f = Fixture().apply {
            fetch = { url ->
                when (url) {
                    rows[0].id -> true
                    rows[1].id -> false
                    else -> error("synthetic fetch failure")
                }
            }
        }
        f.cache.fetchFolderAhead(BrowseCache.Folder(rows), CacheAhead.ALWAYS)
        assertEquals(rows.map { it.id }, f.started)
        assertEquals(listOf(rows[0].id to true, rows[1].id to false, rows[2].id to false), f.finished)
        assertTrue(f.active.isEmpty())
        assertEquals(2, f.clears)
    }

    @Test(timeout = 5_000)
    fun `cancelling a folder stops queued work and clears all three in-flight marks`() = runBlocking {
        val rows = (1..6).map { track("$it") }
        val entered = Channel<String>(Channel.UNLIMITED)
        val gate = CompletableDeferred<Boolean>()
        val f = Fixture().apply { fetch = { url -> entered.send(url); gate.await() } }
        val job = launch { f.cache.fetchFolderAhead(BrowseCache.Folder(rows), CacheAhead.ALWAYS) }
        repeat(3) { entered.receive() }
        assertEquals(rows.take(3).map { it.id }.toSet(), f.active)
        assertEquals(rows.take(3).map { it.id }, f.started)
        job.cancelAndJoin()
        assertEquals(3, f.started.size)
        assertTrue(f.finished.isEmpty())
        assertTrue(f.active.isEmpty())
        assertEquals(2, f.clears)
        assertFalse(gate.isCompleted)
    }

    @Test(timeout = 5_000)
    fun `changing to a metered network stops queued work but lets the three active reads finish`() = runBlocking {
        val rows = (1..6).map { track("$it") }
        val entered = Channel<String>(Channel.UNLIMITED)
        val gate = CompletableDeferred<Boolean>()
        val f = Fixture().apply { fetch = { url -> entered.send(url); gate.await() } }
        val job = launch { f.cache.fetchFolderAhead(BrowseCache.Folder(rows), CacheAhead.WIFI_ONLY) }
        repeat(3) { entered.receive() }
        f.metered = true
        gate.complete(true)
        job.join()
        assertEquals(rows.take(3).map { it.id }, f.started)
        assertEquals(rows.take(3).map { it.id to true }, f.finished)
        assertTrue(f.active.isEmpty())
        assertEquals(2, f.clears)
    }

    @Test
    fun `a cache-query failure propagates before progress is cleared or downloads begin`() = runBlocking {
        val failure = IllegalStateException("synthetic cache lookup failure")
        val f = Fixture().apply { queryFailure = failure; active += "previous" }
        val caught = runCatching { f.cache.fetchFolderAhead(BrowseCache.Folder(listOf(track("one"))), CacheAhead.ALWAYS) }.exceptionOrNull()
        assertEquals(failure.javaClass, caught?.javaClass)
        assertEquals(failure.message, caught?.message)
        assertTrue(generateSequence(caught) { it.cause }.any { it === failure })
        assertEquals(0, f.clears)
        assertEquals(setOf("previous"), f.active)
        assertTrue(f.started.isEmpty())
        assertEquals(0, f.meteredChecks)
    }
}
