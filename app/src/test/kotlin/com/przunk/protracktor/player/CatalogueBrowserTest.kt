// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.net.Catalogue
import java.util.Collections
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueBrowserTest {
    private fun catalogue(id: String = "modland") = CatalogueSummary(id, id, 7, 1L)
    private fun track(id: String) = TrackRef(id, id)
    private suspend fun CoroutineScope.drain() {
        while (coroutineContext.job.children.any()) coroutineContext.job.children.toList().joinAll()
    }

    private class Fixture(scope: CoroutineScope) : CatalogueBrowser.Indexes, CatalogueBrowser.Files {
        val browse = MutableStateFlow(BrowseState())
        val state = CatalogueBrowseState(browse)
        val calls = Collections.synchronizedList(mutableListOf<Pair<String, Thread>>())
        val events: List<String> get() = synchronized(calls) { calls.map { it.first } }
        val browser = CatalogueBrowser(scope, this, this, state) { note("backends"); fingerprint() }
        var before: suspend (String) -> Unit = {}
        var fingerprint: () -> String = { "test-backends" }
        val summaries = listOf(CatalogueSummary("modland", "Modland", 7, 1L))
        val groups = listOf(CatalogueGroup("Group", 3))
        val rows = listOf(CatalogueTrack("modland", "Protracker/Artist/tune.mod", "Protracker", "Artist", "Displayed", 123))
        val platforms = mapOf("amiga" to 11)
        val held = HeldSets(songLengths = true, trackMetadata = false, songDbLengths = true, replays = true, players = true)

        private fun note(name: String) { calls += name to Thread.currentThread() }
        private suspend fun read(name: String) { note(name); before(name) }
        override suspend fun hasSongLengths(): Boolean { read("has-lengths"); return true }
        override suspend fun hasTrackMetadata(): Boolean { read("has-metadata"); return false }
        override suspend fun hasSongDbLengths(): Boolean { read("has-songdb"); return true }
        override suspend fun summaries(): List<CatalogueSummary> { read("summaries"); return summaries }
        override suspend fun songLengthCount(): Int { read("lengths"); return 5 }
        override suspend fun trackMetadataCount(): Int { read("metadata"); return 7 }
        override suspend fun songDbLengthCount(): Int { read("songdb"); return 11 }
        override suspend fun playableFavourites(): Int { read("playable"); return 13 }
        override suspend fun listedFavourites(): Int { read("listed"); return 17 }
        override suspend fun platformCounts(): Map<String, Int> { read("platforms"); return platforms }
        override suspend fun formats(catalogueId: String): List<CatalogueGroup> { read("formats:$catalogueId"); return groups }
        override suspend fun authors(catalogueId: String, format: String): List<CatalogueGroup> {
            read("authors:$catalogueId:$format"); return groups
        }
        override suspend fun tracks(catalogueId: String, format: String, author: String): List<CatalogueTrack> {
            read("tracks:$catalogueId:$format:$author"); return rows
        }
        override fun replayCount(): Int { note("replay-count"); return 3 }
        override fun replayBytes(): Long { note("replay-bytes"); return 29 }
        override fun playersPresent(): Boolean { note("players-present"); return true }
        override fun playerCount(): Int { note("player-count"); return 2 }
        override fun playerBytes(): Long { note("player-bytes"); return 31 }
        override fun pairedBrowser(): Boolean { note("paired"); return true }
        override fun cacheBytes(): Long { note("cache"); return 19 }
        override fun permanentBytes(): Long { note("permanent"); return 23 }
        override fun archiveBytes(catalogueId: String): Long { note("archive:$catalogueId"); return if (catalogueId == "asma") 37 else 0 }
        override fun databaseBytes(): Long { note("database"); return 41 }
    }

    @Test
    fun `construction does not read a store file or fingerprint or change Browse`() = runBlocking {
        val f = Fixture(this)
        yield()
        assertTrue(f.events.isEmpty())
        assertEquals(BrowseState(), f.browse.value)
        assertTrue(coroutineContext.job.children.none())
    }

    @Test(timeout = 5_000)
    fun `overview publishes held flags first and retains ordered counts and unrelated state`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply {
            browse.value = browse.value.copy(query = "kept", domain = BrowseDomain.SEARCH, tracks = listOf(track("shown")))
            before = { if (it == "summaries") { entered.complete(Unit); gate.await() } }
        }
        f.browser.refreshCatalogues()
        entered.await()
        assertEquals(f.held, f.browse.value.held)
        assertFalse(f.browse.value.heldCountsKnown)
        assertEquals(0, f.browse.value.songLengthCount)
        val during = f.browse.value.copy(query = "changed while reading")
        f.browse.value = during
        gate.complete(Unit)
        drain()
        assertEquals(during.copy(
            catalogues = f.summaries, songLengthCount = 5, trackMetadataCount = 7, songDbLengthCount = 11,
            favouriteCount = 13, favouritesListed = 17, pairedBrowser = true, storageBytes = 19L to 23L,
            archiveBytes = mapOf("asma" to 37L), databaseBytes = 41, replayCount = 3, replayBytes = 29,
            playerCount = 2, playerBytes = 31, heldCountsKnown = true, backends = "test-backends", platformCounts = f.platforms,
        ), f.browse.value)
        assertEquals(
            listOf("has-lengths", "has-metadata", "has-songdb", "replay-count", "players-present", "summaries",
                "lengths", "metadata", "songdb", "playable", "listed", "paired", "cache", "permanent") +
                Catalogue.all.map { "archive:${it.id}" } +
                listOf("database", "replay-count", "replay-bytes", "player-count", "player-bytes", "backends"),
            f.events.filter { it != "platforms" },
        )
    }

    @Test(timeout = 5_000)
    fun `file counts remain on IO while pairing and fingerprint remain on the caller thread`() = runBlocking {
        val caller = Thread.currentThread()
        val f = Fixture(this)
        f.browser.refreshCatalogues()
        drain()
        val fileNames = setOf("replay-count", "players-present", "cache", "permanent", "database", "replay-bytes", "player-count", "player-bytes")
        val io = f.calls.filter { it.first in fileNames || it.first.startsWith("archive:") }
        assertEquals(fileNames + Catalogue.all.map { "archive:${it.id}" }, io.map { it.first }.toSet())
        assertTrue(io.all { it.second !== caller })
        assertSame(caller, f.calls.single { it.first == "paired" }.second)
        assertSame(caller, f.calls.single { it.first == "backends" }.second)
    }

    @Test(timeout = 5_000)
    fun `overview completes independently of a suspended platform count and that count publishes later`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val overview = CompletableDeferred<Unit>()
        val f = Fixture(this).apply {
            before = { if (it == "platforms") { entered.complete(Unit); gate.await() } }
            fingerprint = { overview.complete(Unit); "ready" }
        }
        f.browser.refreshCatalogues()
        entered.await()
        overview.await()
        assertTrue(f.browse.value.heldCountsKnown)
        assertTrue(f.browse.value.platformCounts.isEmpty())
        f.browse.value = f.browse.value.copy(query = "later")
        gate.complete(Unit)
        drain()
        assertEquals(f.platforms, f.browse.value.platformCounts)
        assertEquals("later", f.browse.value.query)
    }

    @Test
    fun `platform refresh alone does not query overview sources`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value.copy(query = "kept", favouriteCount = 99)
        f.browse.value = before
        f.browser.refreshPlatformCounts()
        drain()
        assertEquals(listOf("platforms"), f.events)
        assertEquals(before.copy(platformCounts = f.platforms), f.browse.value)
    }

    @Test(timeout = 5_000)
    fun `favourite counts publish together after both reads without replacing other state`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply { before = { if (it == "listed") { entered.complete(Unit); gate.await() } } }
        f.browser.refreshFavouriteCount()
        entered.await()
        assertEquals(0, f.browse.value.favouriteCount)
        val during = f.browse.value.copy(query = "during", favouriteCount = 91, favouritesListed = 92)
        f.browse.value = during
        gate.complete(Unit)
        drain()
        assertEquals(listOf("playable", "listed"), f.events)
        assertEquals(during.copy(favouriteCount = 13, favouritesListed = 17), f.browse.value)
    }

    @Test(timeout = 5_000)
    fun `catalogue opening changes only its loading fields then publishes groups into current state`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply {
            before = { if (it == "formats:modland") { entered.complete(Unit); gate.await() } }
            browse.value = browse.value.copy(openFormat = "old", openAuthor = "old", arrivedByJump = true,
                tracks = listOf(track("old")), groups = listOf(CatalogueGroup("previous", 9)), query = "kept")
        }
        val before = f.browse.value
        val summary = catalogue()
        f.browser.openCatalogue(summary)
        assertEquals(before, f.browse.value)
        entered.await()
        assertEquals(before.copy(openCatalogue = summary, openFormat = null, openAuthor = null,
            arrivedByJump = false, tracks = emptyList(), loading = true), f.browse.value)
        val during = f.browse.value.copy(query = "during")
        f.browse.value = during
        gate.complete(Unit)
        drain()
        assertEquals(listOf("formats:modland"), f.events)
        assertEquals(during.copy(groups = f.groups, loading = false), f.browse.value)
    }

    @Test
    fun `format opening needs a catalogue and captures its identity before launching`() = runBlocking {
        val f = Fixture(this)
        f.browser.openFormat("ignored")
        drain()
        assertTrue(f.events.isEmpty())
        f.browse.value = f.browse.value.copy(openCatalogue = catalogue(), openAuthor = "old", arrivedByJump = true, tracks = listOf(track("old")))
        f.browser.openFormat("Protracker")
        f.browse.value = f.browse.value.copy(openCatalogue = catalogue("changed"), query = "later")
        drain()
        assertEquals(listOf("authors:modland:Protracker"), f.events)
        assertEquals("changed", f.browse.value.openCatalogue?.id)
        assertEquals("Protracker", f.browse.value.openFormat)
        assertEquals(null, f.browse.value.openAuthor)
        assertFalse(f.browse.value.arrivedByJump)
        assertFalse(f.browse.value.loading)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertEquals(f.groups, f.browse.value.groups)
        assertEquals("later", f.browse.value.query)
    }

    @Test
    fun `author opening requires both levels and captures them once before launching`() = runBlocking {
        val f = Fixture(this)
        f.browser.openAuthor("ignored")
        f.browse.value = f.browse.value.copy(openCatalogue = catalogue())
        f.browser.openAuthor("ignored")
        drain()
        assertTrue(f.events.isEmpty())
        f.browse.value = f.browse.value.copy(openFormat = "Protracker", arrivedByJump = true)
        f.browser.openAuthor("Artist")
        f.browse.value = f.browse.value.copy(openCatalogue = catalogue("changed"), openFormat = "changed", query = "later")
        drain()
        assertEquals(listOf("tracks:modland:Protracker:Artist"), f.events)
        assertEquals("Artist", f.browse.value.openAuthor)
        assertEquals("changed", f.browse.value.openFormat)
        assertEquals("changed", f.browse.value.openCatalogue?.id)
        assertEquals(f.rows.map(CatalogueTracks::referenceOf), f.browse.value.tracks)
        assertFalse(f.browse.value.loading)
        assertFalse(f.browse.value.arrivedByJump)
        assertEquals("later", f.browse.value.query)
    }

    @Test(timeout = 5_000)
    fun `overlapping catalogue reads keep the existing completion order without cancelling earlier jobs`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val f = Fixture(this).apply {
            before = { if (it == "formats:first") { entered.complete(Unit); gate.await() } }
        }
        f.browser.openCatalogue(catalogue("first"))
        entered.await()
        f.browser.openCatalogue(catalogue("second"))
        yield()
        assertEquals("second", f.browse.value.openCatalogue?.id)
        assertFalse(f.browse.value.loading)
        f.browse.value = f.browse.value.copy(groups = listOf(CatalogueGroup("newer", 1)), loading = true)
        gate.complete(Unit)
        drain()
        assertEquals("second", f.browse.value.openCatalogue?.id)
        assertEquals(f.groups, f.browse.value.groups)
        assertFalse(f.browse.value.loading)
        assertEquals(listOf("formats:first", "formats:second"), f.events)
    }

    @Test(timeout = 5_000)
    fun `overview failure propagates after held publication without replacing the previous counts`() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        try {
            val failure = IllegalStateException("database unavailable")
            val f = Fixture(owner).apply {
                browse.value = browse.value.copy(songLengthCount = 99, heldCountsKnown = true)
                before = { if (it == "lengths") throw failure }
            }
            f.browser.refreshCatalogues()
            owner.drain()
            assertSame(failure, failures.single())
            assertEquals(f.held, f.browse.value.held)
            assertEquals(99, f.browse.value.songLengthCount)
            assertTrue(f.browse.value.heldCountsKnown)
            assertEquals(f.platforms, f.browse.value.platformCounts)
            assertFalse("metadata" in f.events)
            assertFalse("backends" in f.events)
        } finally { owner.cancel() }
    }

    @Test(timeout = 5_000)
    fun `cancelling an author read retains the existing loading state without publishing tracks`() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val entered = CompletableDeferred<Unit>()
            val f = Fixture(owner).apply {
                browse.value = browse.value.copy(openCatalogue = catalogue(), openFormat = "Protracker", tracks = listOf(track("old")))
                before = { if (it.startsWith("tracks:")) { entered.complete(Unit); awaitCancellation() } }
            }
            f.browser.openAuthor("Artist")
            entered.await()
            val waiting = f.browse.value
            owner.cancel()
            owner.coroutineContext.job.join()
            assertSame(waiting, f.browse.value)
            assertTrue(waiting.loading)
            assertTrue(waiting.tracks.isEmpty())
            assertEquals("Artist", waiting.openAuthor)
        } finally { owner.cancel() }
    }

    @Test
    fun `fingerprint is evaluated within the atomic overview update and retried with current state`() {
        val browse = MutableStateFlow(BrowseState(query = "before"))
        val state = CatalogueBrowseState(browse)
        val overview = CatalogueOverview(emptyList(), 1, 2, 3, 4, 5, false, 0L to 0L, emptyMap(), 0L, 0 to 0L, 0 to 0L)
        var calls = 0
        state.showOverview(overview) {
            calls++
            if (calls == 1) browse.value = browse.value.copy(query = "during")
            "fingerprint-$calls"
        }
        assertEquals(2, calls)
        assertEquals("during", browse.value.query)
        assertEquals("fingerprint-2", browse.value.backends)
        assertEquals(3, browse.value.songDbLengthCount)
    }
}
