// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.net.ModArchive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseWorkflowTest {
    @Test fun `author intent revision changes for restored words and identical resubmissions`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        val initial = f.workflow.searchRevision
        f.workflow.setQuery("different")
        f.workflow.setQuery("typed")
        assertTrue(f.workflow.searchRevision > initial)
        val restored = f.workflow.searchRevision
        f.workflow.setQuery("typed")
        assertEquals(restored, f.workflow.searchRevision)
        f.workflow.runSearch()
        val submitted = f.workflow.searchRevision
        f.workflow.runSearch()
        assertTrue(f.workflow.searchRevision > submitted)
        drain()
    }

    @Test
    fun `Back preserves a parent result published synchronously by its reader`() = runBlocking {
        val f = Fixture(this)
        f.onAction = { f.browse.update { it.copy(groups = listOf(CatalogueGroup("parent", 2)),
            query = "updated", loading = false) } }
        assertTrue(f.workflow.browseBack())
        assertNull(f.browse.value.openAuthor)
        assertEquals(listOf(CatalogueGroup("parent", 2)), f.browse.value.groups)
        assertEquals("updated", f.browse.value.query)
    }

    @Test(timeout = 5_000)
    fun `resubmitting identical inputs owns a distinct request from its predecessor`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val newerDone = CompletableDeferred<Unit>()
        var first = true
        f.onRead = { if (it.startsWith("local:typed:") && first) {
            first = false
            entered.complete(Unit)
            withContext(NonCancellable) { gate.await() }
        } }
        f.onLive = { newerDone.complete(Unit) }
        f.workflow.runSearch()
        entered.await()
        val newer = listOf(TrackRef("newer", "Newer"))
        f.rows = { newer }
        f.workflow.runSearch()
        newerDone.await()
        f.rows = { listOf(f.track) }
        gate.complete(Unit)
        drain()
        assertEquals(newer, f.browse.value.tracks)
        assertEquals(listOf("typed", "typed"), f.submitted)
        assertEquals(1, f.liveResults.size)
    }

    @Test(timeout = 5_000)
    fun `unchanged words and inapplicable filter toggles do not cancel a current search`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.onRead = { if (it.startsWith("local:typed:")) { entered.complete(Unit); gate.await() } }
        f.workflow.runSearch()
        entered.await()
        f.workflow.setQuery("typed")
        f.workflow.toggleSearchCatalogue("ignored")
        f.workflow.toggleSearchPlatform("ignored")
        gate.complete(Unit)
        drain()
        assertEquals(f.rows("typed"), f.browse.value.tracks)
        assertEquals(1, f.liveResults.size)
        assertTrue(f.searchFailures.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `explicit retirement protects a restored identical search snapshot`() = runBlocking {
        val f = Fixture(this)
        f.browse.update { it.copy(domain = BrowseDomain.SEARCH, searchScope = SearchScope.Local) }
        val snapshot = f.browse.value
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.onRead = { if (it.startsWith("local:typed:")) {
            entered.complete(Unit)
            withContext(NonCancellable) { gate.await() }
        } }
        f.workflow.runSearch()
        entered.await()
        f.workflow.cancelSearch()
        f.browse.value = BrowseNavigation.returningTo(snapshot)
        val expected = f.browse.value
        gate.complete(Unit)
        drain()
        assertSame(expected, f.browse.value)
        assertTrue(f.liveResults.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `a query changed before launch cannot start an obsolete lookup`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        f.workflow.runSearch()
        f.workflow.setQuery("new words")
        val expected = f.browse.value
        drain()
        assertTrue(f.events.isEmpty())
        assertSame(expected, f.browse.value)
        assertEquals(listOf("typed"), f.submitted)
    }

    @Test(timeout = 5_000)
    fun `older completion cannot replace rows or end loading for a newer pending search`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val newerEntered = CompletableDeferred<Unit>()
        val newerGate = CompletableDeferred<Unit>()
        f.onRead = {
            if (it.startsWith("local:typed:")) {
                entered.complete(Unit)
                withContext(NonCancellable) { gate.await() }
            }
            if (it.startsWith("local:newer:")) { newerEntered.complete(Unit); newerGate.await() }
        }
        f.workflow.runSearch()
        val older = coroutineContext.job.children.single()
        entered.await()
        f.workflow.searchFor("newer")
        newerEntered.await()
        gate.complete(Unit)
        older.join()
        val during = f.browse.value
        newerGate.complete(Unit)
        drain()
        assertTrue(during.loading)
        assertTrue(during.tracks.isEmpty())
        assertEquals(f.rows("newer"), f.browse.value.tracks)
        assertEquals(1, f.liveResults.size)
    }

    @Test(timeout = 5_000)
    fun `late live notices and results cannot modify History or its loading state`() = runBlocking {
        val f = Fixture(this)
        f.liveSearch()
        f.live = ModArchive.Outcome.NotReached
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.onRead = { entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
        f.onAction = { if (it == "history") {
            f.browse.update { state -> state.copy(loading = true, tracks = listOf(f.track)) }
        } }
        f.workflow.runSearch()
        entered.await()
        f.workflow.openDomain(BrowseDomain.HISTORY)
        val expected = f.browse.value
        gate.complete(Unit)
        drain()
        assertSame(expected, f.browse.value)
        assertTrue(f.browse.value.loading)
        assertTrue(f.liveResults.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `leaving and reentering identical search inputs still retires the original request`() = runBlocking {
        val f = Fixture(this)
        f.browse.update { it.copy(domain = BrowseDomain.SEARCH, searchScope = SearchScope.Local) }
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.onRead = { if (it.startsWith("local:typed:")) {
            entered.complete(Unit)
            withContext(NonCancellable) { gate.await() }
        } }
        f.workflow.runSearch()
        entered.await()
        f.workflow.openDomain(BrowseDomain.ROOT)
        f.workflow.openDomain(BrowseDomain.SEARCH)
        f.workflow.setQuery("typed")
        val expected = f.browse.value
        gate.complete(Unit)
        drain()
        assertSame(expected, f.browse.value)
        assertFalse(f.browse.value.loading)
        assertTrue(f.liveResults.isEmpty())
    }

    private suspend fun CoroutineScope.pendingSearchIsRetired(change: String) {
        val f = Fixture(this)
        f.browse.update { it.copy(domain = BrowseDomain.SEARCH, searchScope = when (change) {
            "catalogue" -> SearchScope.Online(setOf(ModArchive.id))
            "platform" -> SearchScope.ByPlatform()
            else -> SearchScope.Local
        }) }
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var first = true
        f.onRead = { if (first) {
            first = false
            entered.complete(Unit)
            withContext(NonCancellable) { gate.await() }
        } }
        f.workflow.runSearch()
        entered.await()
        when (change) {
            "query" -> f.workflow.setQuery("other words")
            "scope" -> f.workflow.setSearchScope(SearchScope.Everywhere)
            "catalogue" -> f.workflow.toggleSearchCatalogue("indexed")
            "platform" -> f.workflow.toggleSearchPlatform("c64")
            "back" -> f.workflow.browseBack()
        }
        val expected = f.browse.value
        gate.complete(Unit)
        drain()
        assertSame(change, expected, f.browse.value)
        assertFalse(change, f.browse.value.loading)
        assertTrue(change, f.liveResults.isEmpty())
    }

    @Test(timeout = 5_000) fun `editing words retires the pending search`() = runBlocking { pendingSearchIsRetired("query") }
    @Test(timeout = 5_000) fun `changing scope retires the pending search`() = runBlocking { pendingSearchIsRetired("scope") }
    @Test(timeout = 5_000) fun `toggling a catalogue retires the pending search`() = runBlocking { pendingSearchIsRetired("catalogue") }
    @Test(timeout = 5_000) fun `toggling a platform retires the pending search`() = runBlocking { pendingSearchIsRetired("platform") }
    @Test(timeout = 5_000) fun `Back retires the pending narrowed search`() = runBlocking { pendingSearchIsRetired("back") }

    @Test(timeout = 5_000)
    fun `a superseded storage exception cannot report failure for the newer search`() = runBlocking {
        withFailures { f, failures ->
            f.localSearch()
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val newerDone = CompletableDeferred<Unit>()
            f.onRead = { if (it.startsWith("local:typed:")) {
                entered.complete(Unit)
                withContext(NonCancellable) { gate.await() }
                error("obsolete storage failure")
            } }
            f.onLive = { newerDone.complete(Unit) }
            f.workflow.runSearch()
            entered.await()
            f.workflow.searchFor("newer")
            newerDone.await()
            gate.complete(Unit)
            f.scope.drain()
            assertTrue(failures.isEmpty())
            assertTrue(f.searchFailures.isEmpty())
            assertEquals(f.rows("newer"), f.browse.value.tracks)
        }
    }

    @Test fun `typing does not submit but choosing a suggestion immediately searches the current scope once`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        f.workflow.setQuery("prefix")
        assertTrue(f.submitted.isEmpty())
        assertTrue(f.events.isEmpty())
        f.workflow.searchFor("Suggestion")
        assertEquals(listOf("Suggestion"), f.submitted)
        drain()
        assertEquals("Suggestion", f.browse.value.query)
        assertEquals(SearchScope.Local, f.browse.value.searchScope)
        assertEquals(1, f.events.count { it.startsWith("local:Suggestion:") })
        assertFalse(f.events.any { it.startsWith("local:prefix:") })
    }

    @Test fun `ordinary explicit search records its captured query once`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        f.workflow.runSearch()
        assertEquals(listOf("typed"), f.submitted)
        drain()
        assertEquals(listOf("typed"), f.submitted)
    }

    private suspend fun CoroutineScope.drain() {
        while (coroutineContext.job.children.any()) coroutineContext.job.children.toList().joinAll()
    }

    private class Fixture(val scope: CoroutineScope) : BrowseWorkflow.Actions, LibrarySearch.Sources {
        val catalogue = CatalogueSummary("indexed", "Indexed", 1, 1L)
        val track = TrackRef("old", "Old", fileName = "old.mod")
        val folder = GrantedFolder("tree:music", "Music")
        val browse = MutableStateFlow(BrowseState(
            domain = BrowseDomain.ONLINE, query = "typed", tracks = listOf(track),
            catalogues = listOf(catalogue), openCatalogue = catalogue, openFormat = "Protracker",
            openAuthor = "Artist", groups = listOf(CatalogueGroup("Artist", 1)),
            openFolder = folder, folderRoot = "Music", folderAt = "Music/sub", folderAll = listOf("Music" to track),
            subfolders = listOf(LocalTree.Folder("sub", "Music/sub", 1)),
            historyPage = 2, historyTotal = 201, folderUnscanned = true, folderStale = true,
            searched = true, searchMatches = 123, liveSearchNeededQuery = true, scanProgress = 3 to 9,
        ))
        val events = mutableListOf<String>()
        val threads = mutableListOf<Thread>()
        val liveResults = mutableListOf<ModArchive.Outcome>()
        val submitted = mutableListOf<String>()
        val searchFailures = mutableListOf<Throwable>()
        var onAction: (String) -> Unit = {}
        var onRead: suspend (String) -> Unit = {}
        var onLive: (ModArchive.Outcome) -> Unit = {}
        var renderLevel: (BrowseState, String) -> BrowseState = { state, _ -> state.copy(query = "level result") }
        var rows: (String) -> List<TrackRef> = { listOf(TrackRef("result:$it", "Result", fileName = "result.mod")) }
        var live: ModArchive.Outcome = ModArchive.Outcome.Found(emptyList())
        val workflow = BrowseWorkflow(scope, browse, LibrarySearch(this), this,
            onSubmitted = { submitted += it }, onSearchFailed = { searchFailures += it }) {
            events += "notice"
            liveResults += it
            onLive(it)
        }

        private fun action(name: String) { events += name; threads += Thread.currentThread(); onAction(name) }
        private suspend fun read(name: String) { events += name; threads += Thread.currentThread(); onRead(name) }
        override fun refreshFolders() = action("folders")
        override fun refreshCatalogues() = action("catalogues")
        override fun refreshPlatformCounts() = action("platforms")
        override fun refreshFavouriteCount() = action("favourites")
        override fun openHistory() = action("history")
        override fun openSavedPlaces() = action("saved")
        override fun openFormat(format: String) = action("format:$format")
        override fun openCatalogue(summary: CatalogueSummary) { assertSame(catalogue, summary); action("catalogue:${summary.id}") }
        override fun localLevel(state: BrowseState, at: String): BrowseState { action("level:$at"); return renderLevel(state, at) }
        override suspend fun searchLocal(query: String, limit: Int, platforms: Set<String>): List<TrackRef> {
            read("local:$query:$limit:${platforms.sorted()}"); return rows(query)
        }
        override suspend fun playlistTracks(): List<TrackRef> { read("playlists"); return emptyList() }
        override suspend fun searchCatalogues(query: String, ids: Set<String>, limit: Int, platforms: Set<String>): List<TrackRef> {
            read("indexed:$query:${ids.sorted()}:$limit:${platforms.sorted()}"); return rows(query)
        }
        override suspend fun searchLive(query: String): ModArchive.Outcome { read("live:$query"); return live }
        override suspend fun countLocal(query: String): Int { read("count-local:$query"); return 801 }
        override suspend fun countCatalogues(query: String, ids: Set<String>, platforms: Set<String>): Int {
            read("count-indexed:$query"); return 902
        }
        fun localSearch() { browse.update { it.copy(searchScope = SearchScope.Local) } }
        fun liveSearch() { browse.update { it.copy(searchScope = SearchScope.Online(setOf(ModArchive.id))) } }
    }

    private suspend fun CoroutineScope.withFailures(block: suspend (Fixture, MutableList<Throwable>) -> Unit) {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, error -> failures += error })
        try { block(Fixture(owner), failures) } finally { owner.cancel(); owner.coroutineContext.job.join() }
    }

    @Test
    fun `construction leaves the shared state and services untouched`() = runBlocking {
        val f = Fixture(this)
        assertEquals("typed", f.browse.value.query)
        assertTrue(f.events.isEmpty())
        assertTrue(coroutineContext.job.children.none())
    }

    @Test fun `Back from a saved route returns to bookmarks in one step then Browse root`() = runBlocking {
        val f = Fixture(this)
        f.browse.value = f.browse.value.copy(returnToSavedPlaces = true)
        assertTrue(f.workflow.browseBack())
        assertEquals(BrowseDomain.SAVED, f.browse.value.domain)
        assertNull(f.browse.value.openAuthor)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertEquals(listOf("saved"), f.events)
        assertTrue(f.workflow.browseBack())
        assertEquals(BrowseDomain.ROOT, f.browse.value.domain)
    }

    @Test
    fun `every domain is published before its ordered refresh actions`() = runBlocking {
        for (domain in BrowseDomain.entries) {
            val f = Fixture(this)
            val before = f.browse.value.copy(arrivedByJump = true)
            f.browse.value = before
            val expected = BrowseNavigation.enteringDomain(before, domain)
            f.onAction = { assertEquals(expected, f.browse.value) }
            f.workflow.openDomain(domain)
            val actions = when (domain) {
                BrowseDomain.ROOT -> listOf("platforms", "favourites")
                BrowseDomain.LOCAL -> listOf("folders")
                BrowseDomain.ONLINE -> listOf("catalogues")
                BrowseDomain.SEARCH -> listOf("catalogues", "platforms")
                BrowseDomain.HISTORY -> listOf("history")
                BrowseDomain.SAVED -> listOf("saved")
            }
            assertEquals(domain.toString(), actions, f.events)
            assertEquals(expected, f.browse.value)
            assertTrue(f.threads.all { it === Thread.currentThread() })
            assertTrue(coroutineContext.job.children.none())
        }
    }

    @Test
    fun `root reuses known platform counts but always refreshes favourite counts`() = runBlocking {
        val f = Fixture(this)
        f.browse.update { it.copy(platformCounts = mapOf("amiga" to 0), favouriteCount = 17) }
        f.workflow.openDomain(BrowseDomain.ROOT)
        f.workflow.openDomain(BrowseDomain.ROOT)
        assertEquals(listOf("favourites", "favourites"), f.events)
        assertEquals(mapOf("amiga" to 0), f.browse.value.platformCounts)
    }

    @Test
    fun `a failed entry callback keeps the published domain and stops later callbacks`() = runBlocking {
        for (domain in listOf(BrowseDomain.SEARCH, BrowseDomain.ROOT)) {
            val f = Fixture(this)
            val before = f.browse.value
            val failure = IllegalStateException("refresh")
            f.onAction = { throw failure }
            assertSame(failure, runCatching { f.workflow.openDomain(domain) }.exceptionOrNull())
            assertEquals(BrowseNavigation.enteringDomain(before, domain), f.browse.value)
            assertEquals(listOf(if (domain == BrowseDomain.SEARCH) "catalogues" else "platforms"), f.events)
        }
    }

    @Test
    fun `Back ends a jump before any scope or hierarchy handling`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value.copy(arrivedByJump = true, domain = BrowseDomain.SEARCH, searchScope = SearchScope.Local)
        f.browse.value = before
        assertFalse(f.workflow.browseBack())
        assertEquals(before.copy(arrivedByJump = false, navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `Back widens search scope before parents and clears only the stale result fields`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value.copy(domain = BrowseDomain.SEARCH, searchScope = SearchScope.Local)
        f.browse.value = before
        assertTrue(f.workflow.browseBack())
        assertEquals(before.copy(searchScope = SearchScope.Everywhere, tracks = emptyList(),
            searchMatches = 0, searched = false, liveSearchNeededQuery = false,
            navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `Back from an author publishes the parent before requesting its format`() = runBlocking {
        for (format in listOf("Protracker", null)) {
            val f = Fixture(this)
            val before = f.browse.value.copy(openFormat = format)
            f.browse.value = before
            val expected = before.copy(openAuthor = null, tracks = emptyList(), navigationVisit = before.navigationVisit + 1)
            f.onAction = { assertEquals(expected, f.browse.value) }
            assertTrue(f.workflow.browseBack())
            assertEquals(listOf("format:${format.orEmpty()}"), f.events)
            assertEquals(expected, f.browse.value)
        }
    }

    @Test
    fun `Back from a format requests its catalogue only when the catalogue is present`() = runBlocking {
        for (hasCatalogue in listOf(true, false)) {
            val f = Fixture(this)
            val before = f.browse.value.copy(openAuthor = null, openCatalogue = f.catalogue.takeIf { hasCatalogue })
            f.browse.value = before
            val expected = before.copy(openFormat = null, tracks = emptyList(), navigationVisit = before.navigationVisit + 1)
            f.onAction = { assertEquals(expected, f.browse.value) }
            assertTrue(f.workflow.browseBack())
            assertEquals(if (hasCatalogue) listOf("catalogue:indexed") else emptyList<String>(), f.events)
            assertEquals(expected, f.browse.value)
        }
    }

    @Test
    fun `Back from a catalogue clears only the catalogue and groups`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value.copy(openAuthor = null, openFormat = null)
        f.browse.value = before
        assertTrue(f.workflow.browseBack())
        assertEquals(before.copy(openCatalogue = null, groups = emptyList(), navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `Back walks a local parent through the existing renderer then closes from the root`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value.copy(openCatalogue = null, openFormat = null, openAuthor = null)
        f.browse.value = before
        val rendered = before.copy(folderAt = "Music", query = "renderer result")
        f.renderLevel = { state, at -> assertSame(before, state); assertEquals("Music", at); rendered }
        assertTrue(f.workflow.browseBack())
        assertEquals(rendered.copy(navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertEquals(listOf("level:Music"), f.events)
        assertTrue(f.workflow.browseBack())
        assertEquals(rendered.copy(openFolder = null, tracks = emptyList(), folderAll = emptyList(), subfolders = emptyList(),
            navigationVisit = before.navigationVisit + 2), f.browse.value)
        assertEquals(listOf("level:Music"), f.events)
    }

    @Test
    fun `Back leaves domain results only for search and returns false at the root`() = runBlocking {
        for (domain in BrowseDomain.entries) {
            val f = Fixture(this)
            val before = f.browse.value.copy(domain = domain, openAuthor = null, openFormat = null,
                openCatalogue = null, openFolder = null)
            f.browse.value = before
            assertEquals(domain != BrowseDomain.ROOT, f.workflow.browseBack())
            val expected = when (domain) {
                BrowseDomain.ROOT -> before.copy(navigationVisit = before.navigationVisit + 1)
                BrowseDomain.SEARCH -> before.copy(domain = BrowseDomain.ROOT, navigationVisit = before.navigationVisit + 1)
                else -> before.copy(domain = BrowseDomain.ROOT, tracks = emptyList(), navigationVisit = before.navigationVisit + 1)
            }
            assertEquals(expected, f.browse.value)
            assertTrue(f.events.isEmpty())
        }
    }

    @Test
    fun `a reentrant parent read survives Back publication`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value
        f.onAction = { f.browse.update { it.copy(query = "callback", loading = true) } }
        assertTrue(f.workflow.browseBack())
        assertEquals(before.copy(openAuthor = null, tracks = emptyList(), query = "callback", loading = true,
            navigationVisit = before.navigationVisit + 1), f.browse.value)
        assertEquals(listOf("format:Protracker"), f.events)
    }

    @Test
    fun `a failed parent read leaves the parent transition and concurrent fields intact`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value
        val failure = IllegalStateException("parent")
        f.onAction = { f.browse.update { it.copy(query = "callback") }; throw failure }
        assertSame(failure, runCatching { f.workflow.browseBack() }.exceptionOrNull())
        assertEquals(before.copy(openAuthor = null, tracks = emptyList(), query = "callback",
            navigationVisit = before.navigationVisit + 1), f.browse.value)
    }

    @Test
    fun `typing changes only the query and mismatched filter toggles do nothing`() = runBlocking {
        val f = Fixture(this)
        val before = f.browse.value
        f.workflow.setQuery("new words")
        assertEquals(before.copy(query = "new words"), f.browse.value)
        val typed = f.browse.value
        f.workflow.toggleSearchCatalogue("extra")
        f.workflow.toggleSearchPlatform("amiga")
        assertSame(typed, f.browse.value)
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `filter toggles add and remove within their scope without clearing or rerunning results`() = runBlocking {
        val f = Fixture(this)
        for (platform in listOf(false, true)) {
            val emptyScope = if (platform) SearchScope.ByPlatform() else SearchScope.Online()
            val before = f.browse.value.copy(searchScope = emptyScope)
            f.browse.value = before
            val toggle: (String) -> Unit = if (platform) f.workflow::toggleSearchPlatform else f.workflow::toggleSearchCatalogue
            toggle("one")
            toggle("two")
            val selected = if (platform) SearchScope.ByPlatform(setOf("one", "two")) else SearchScope.Online(setOf("one", "two"))
            assertEquals(before.copy(searchScope = selected), f.browse.value)
            toggle("one")
            toggle("two")
            assertEquals(before, f.browse.value)
        }
        assertTrue(f.events.isEmpty())
        assertTrue(coroutineContext.job.children.none())
    }

    @Test
    fun `setting even the same scope clears stale results without touching the words or starting a search`() = runBlocking {
        for (scope in listOf(SearchScope.Everywhere, SearchScope.Local, SearchScope.Online(), SearchScope.ByPlatform())) {
            val f = Fixture(this)
            val before = f.browse.value.copy(searchScope = scope)
            f.browse.value = before
            f.workflow.setSearchScope(scope)
            assertEquals(before.copy(tracks = emptyList(), searchMatches = 0, searched = false, liveSearchNeededQuery = false), f.browse.value)
            assertTrue(f.events.isEmpty())
            assertTrue(coroutineContext.job.children.none())
        }
    }

    @Test(timeout = 5_000)
    fun `search captures its indexed catalogues but preserves unrelated newer Browse fields`() = runBlocking {
        val f = Fixture(this)
        val archive = CatalogueSummary("archive", "Archive", 0, 1L, archiveCount = 7)
        val absent = CatalogueSummary("absent", "Absent", 0, null)
        f.browse.update { it.copy(searchScope = SearchScope.Online(), catalogues = listOf(f.catalogue, archive, absent)) }
        val before = f.browse.value
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.onRead = { entered.complete(Unit); gate.await() }
        f.workflow.runSearch()
        assertSame(before, f.browse.value)
        assertTrue(f.events.isEmpty())
        f.browse.update { it.copy(catalogues = emptyList(), storageBytes = 17L to 19L) }
        val newer = f.browse.value
        entered.await()
        assertEquals(newer.copy(loading = true, tracks = emptyList(), searched = false,
            searchMatches = 0, liveSearchNeededQuery = false), f.browse.value)
        assertEquals(listOf("indexed:typed:[archive, indexed]:${SearchResults.PER_SOURCE_LIMIT}:[]"), f.events)
        gate.complete(Unit)
        drain()
        assertEquals(newer.copy(tracks = f.rows("typed"), loading = false, searched = true,
            searchMatches = 0, liveSearchNeededQuery = false), f.browse.value)
        assertEquals(1, f.liveResults.size)
        assertTrue(f.threads.all { it === Thread.currentThread() })
    }

    @Test
    fun `empty queries still use the real search service and publish its result`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        f.workflow.setQuery("")
        val before = f.browse.value
        f.workflow.runSearch()
        drain()
        assertEquals(listOf("local::${SearchResults.PER_SOURCE_LIMIT}:[]", "playlists", "notice"), f.events)
        assertEquals(before.copy(tracks = f.rows(""), loading = false, searched = true,
            searchMatches = 0, liveSearchNeededQuery = false), f.browse.value)
    }

    @Test
    fun `capped match counts and a live-only empty query are published from the search result`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        f.rows = { List(SearchResults.PER_SOURCE_LIMIT) { TrackRef("result:$it", "Result") } }
        f.workflow.runSearch()
        drain()
        assertEquals(801, f.browse.value.searchMatches)
        assertEquals(SearchResults.PER_SOURCE_LIMIT, f.browse.value.tracks.size)
        f.liveSearch()
        f.workflow.setQuery("")
        f.events.clear()
        f.workflow.runSearch()
        drain()
        assertEquals(listOf("notice"), f.events)
        assertTrue(f.browse.value.liveSearchNeededQuery)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertEquals(0, f.browse.value.searchMatches)
    }

    @Test
    fun `live outcomes reach the existing notice callback before result publication`() = runBlocking {
        for (outcome in listOf(ModArchive.Outcome.NotReached, ModArchive.Outcome.Unreadable,
            ModArchive.Outcome.Found(listOf(TrackRef("live", "Live", fileName = "live.mod"))))) {
            val f = Fixture(this)
            f.liveSearch()
            f.live = outcome
            f.onLive = { assertSame(outcome, it); assertTrue(f.browse.value.loading); assertTrue(f.browse.value.tracks.isEmpty()) }
            f.workflow.runSearch()
            drain()
            assertEquals(listOf("live:typed", "notice"), f.events)
            assertSame(outcome, f.liveResults.single())
            assertEquals((outcome as? ModArchive.Outcome.Found)?.tracks.orEmpty(), f.browse.value.tracks)
            assertFalse(f.browse.value.loading)
            assertTrue(f.browse.value.searched)
        }
    }

    @Test
    fun `current store and notice failures stop loading and reach the failure callback`() = runBlocking {
        for (noticeFailure in listOf(false, true)) withFailures { f, failures ->
            f.localSearch()
            val before = f.browse.value
            val failure = IllegalStateException("search failed")
            if (noticeFailure) f.onLive = { throw failure } else f.onRead = { throw failure }
            f.workflow.runSearch()
            f.scope.drain()
            assertTrue(failures.isEmpty())
            assertEquals(listOf(failure), f.searchFailures)
            assertEquals(before.copy(loading = false, tracks = emptyList(), searched = false,
                searchMatches = 0, liveSearchNeededQuery = false), f.browse.value)
        }
    }

    @Test(timeout = 5_000)
    fun `cancelling the current search clears its loading state without failure feedback`() = runBlocking {
        withFailures { f, failures ->
            f.localSearch()
            val before = f.browse.value
            val entered = CompletableDeferred<Unit>()
            f.onRead = { entered.complete(Unit); awaitCancellation() }
            f.workflow.runSearch()
            entered.await()
            f.scope.cancel()
            f.scope.coroutineContext.job.join()
            assertEquals(before.copy(loading = false, tracks = emptyList(), searched = false,
                searchMatches = 0, liveSearchNeededQuery = false), f.browse.value)
            assertTrue(failures.isEmpty())
            assertTrue(f.liveResults.isEmpty())
            assertTrue(f.searchFailures.isEmpty())
        }
    }

    @Test(timeout = 5_000)
    fun `an older search cannot publish after a newer query and domain have been selected`() = runBlocking {
        val f = Fixture(this)
        f.localSearch()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val newerDone = CompletableDeferred<Unit>()
        f.onRead = { if (it.startsWith("local:typed:")) { entered.complete(Unit); gate.await() } }
        f.onLive = { newerDone.complete(Unit) }
        f.workflow.runSearch()
        entered.await()
        f.workflow.setQuery("newer")
        f.workflow.runSearch()
        newerDone.await()
        assertEquals(f.rows("newer"), f.browse.value.tracks)
        f.workflow.openDomain(BrowseDomain.HISTORY)
        gate.complete(Unit)
        drain()
        assertEquals(BrowseDomain.HISTORY, f.browse.value.domain)
        assertEquals("", f.browse.value.query)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertEquals(1, f.liveResults.size)
    }
}
