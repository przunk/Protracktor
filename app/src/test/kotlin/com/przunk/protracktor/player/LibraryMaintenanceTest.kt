// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.LibraryMaintenance.Notice
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMaintenanceTest {
    private suspend fun CoroutineScope.drain() {
        while (coroutineContext.job.children.any()) coroutineContext.job.children.toList().joinAll()
    }

    private fun assertPropagated(expected: Throwable, actual: Throwable) {
        assertEquals(expected.javaClass, actual.javaClass)
        assertEquals(expected.message, actual.message)
        // Coroutine stack recovery may copy an exception across dispatchers, retaining its cause.
        assertTrue(generateSequence(actual) { it.cause }.any { it === expected })
    }

    private class Fixture(val scope: CoroutineScope) : Closeable, LibraryMaintenance.Store,
        LibraryMaintenance.Files, LibraryMaintenance.Native, LibraryMaintenance.Ui, LibraryMaintenance.Text {
        val background = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val player = MutableStateFlow(PlayerUiState(playing = true, dirty = true))
        val asma = CatalogueSummary("asma", "Archive title", 9, 11L, archiveCount = 4)
        val modland = CatalogueSummary("modland", "Modland title", 17, 19L)
        val browse = MutableStateFlow(BrowseState(
            catalogues = listOf(asma, modland), archiveBytes = mapOf("asma" to 23L),
            openCatalogue = asma, searchScope = SearchScope.Online(setOf("asma", "modland")),
            held = HeldSets(true, true, true, true, true), heldCountsKnown = true,
            songLengthCount = 3, trackMetadataCount = 5, songDbLengthCount = 7,
            replayCount = 2, replayBytes = 29L, playerCount = 3, playerBytes = 31L, query = "kept",
        ))
        val calls = Collections.synchronizedList(mutableListOf<Pair<String, Thread>>())
        val events: List<String> get() = synchronized(calls) { calls.map { it.first } }
        val notices = mutableListOf<Notice>()
        val state = LibraryMaintenanceState(player, browse, ::message)
        val maintenance = LibraryMaintenance(scope, background, this, this, state, this, this, this)
        var before: suspend (String) -> Unit = {}
        var fileAction: (String) -> Unit = {}
        var allowBegin = true
        var cacheSize = 41L
        var sc68Removed = true
        var uadeRemoved = true
        var configuredVersion: String? = null
        private fun note(name: String) { calls += name to Thread.currentThread() }
        private suspend fun read(name: String) { note(name); before(name) }
        private fun file(name: String) { note(name); fileAction(name) }
        override suspend fun clearIndex(catalogueId: String) = read("index:$catalogueId")
        override suspend fun clearFavourites() = read("favourites")
        override suspend fun clearSongLengths() = read("lengths")
        override suspend fun clearTrackMetadata() = read("metadata")
        override suspend fun clearSongDbLengths() = read("songdb")
        override fun cacheBytes(): Long { file("cache-size"); return cacheSize }
        override fun clearCache() { file("clear-cache"); cacheSize = 7L }
        override fun deleteArchive(catalogueId: String): Boolean { file("archive:$catalogueId"); return false }
        override fun deleteSc68(): Boolean { file("sc68"); return sc68Removed }
        override fun deleteUade(): Boolean { file("uade"); return uadeRemoved }
        override fun deleteAdoptedUade(): Boolean { file("adopted"); return false }
        override fun installedVersion(): String { file("version"); return "777" }
        override fun configureUade(version: String) { file("configure"); configuredVersion = version }
        override fun beginDownload(key: String, label: String): Boolean {
            note("begin:$key")
            assertEquals("deleting label", label)
            if (!allowBegin || key in browse.value.indexing) return false
            browse.update { it.copy(indexing = it.indexing + (key to label)) }
            return true
        }
        override fun endDownload(key: String) { note("end:$key"); browse.update { it.copy(indexing = it.indexing - key) } }
        override fun refreshOnPhone() { note("on-phone") }
        override fun refreshCatalogues() { note("refresh") }
        override fun leaveFavourites() { note("leave-favourites"); browse.update { it.copy(randomScope = RandomScope.Everything) } }
        override fun deleting(): String { note("label"); return "deleting label" }
        override fun message(notice: Notice): Message { note("notice"); notices += notice; return Message(notice.toString()) }
        override fun close() { background.close() }
    }

    private suspend fun CoroutineScope.withFailures(block: suspend (Fixture, MutableList<Throwable>) -> Unit) {
        val failures = mutableListOf<Throwable>()
        val owner = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, e -> failures += e })
        Fixture(owner).use { f ->
            try { block(f, failures) } finally { owner.cancel(); owner.coroutineContext.job.join() }
        }
    }

    @Test
    fun `construction performs no work or publication`() = runBlocking {
        Fixture(this).use { f ->
            assertTrue(f.events.isEmpty())
            assertTrue(coroutineContext.job.children.none())
            assertEquals("kept", f.browse.value.query)
            assertEquals(null, f.player.value.message)
        }
    }

    @Test
    fun `cache clearing measures on the background lane then refreshes marks notice and counts`() = runBlocking {
        Fixture(this).use { f ->
            val before = f.player.value
            val browse = f.browse.value
            f.maintenance.clearFetchedCache()
            assertTrue(f.events.isEmpty())
            drain()
            assertEquals(listOf("cache-size", "clear-cache", "cache-size", "on-phone", "notice", "refresh"), f.events)
            assertEquals(listOf(Notice.Freed(34L)), f.notices)
            assertEquals(browse, f.browse.value)
            assertEquals(before.copy(message = f.player.value.message), f.player.value)
            assertTrue(f.calls.take(3).all { it.second !== Thread.currentThread() })
            assertTrue(f.calls.drop(3).all { it.second === Thread.currentThread() })
        }
    }

    @Test
    fun `cache failure propagates without a completion notice or refresh`() = runBlocking {
        withFailures { f, failures ->
            val failure = IllegalStateException("cache unavailable")
            f.fileAction = { if (it == "clear-cache") throw failure }
            f.maintenance.clearFetchedCache()
            f.scope.drain()
            assertPropagated(failure, failures.single())
            assertEquals(listOf("cache-size", "clear-cache"), f.events)
            assertTrue(f.notices.isEmpty())
        }
    }

    @Test
    fun `a declined catalogue guard leaves rows and messages untouched and starts no job`() = runBlocking {
        Fixture(this).use { f ->
            f.allowBegin = false
            val before = f.browse.value
            f.maintenance.deleteCatalogueIndex("asma")
            assertEquals(listOf("label", "begin:asma"), f.events)
            assertEquals(before, f.browse.value)
            assertTrue(f.notices.isEmpty())
            assertTrue(coroutineContext.job.children.none())
        }
    }

    @Test(timeout = 5_000)
    fun `catalogue deletion publishes immediately and ignores archive false before dropping only its scope`() = runBlocking {
        Fixture(this).use { f ->
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            f.before = { if (it == "index:asma") { entered.complete(Unit); gate.await() } }
            val before = f.browse.value
            f.maintenance.deleteCatalogueIndex("asma")
            assertEquals(listOf("label", "begin:asma", "notice"), f.events)
            assertEquals(listOf(Notice.Freed(23L)), f.notices)
            assertEquals(before.copy(
                indexing = mapOf("asma" to "deleting label"), deleting = setOf("asma"), archiveBytes = emptyMap(),
                catalogues = listOf(f.asma.copy(trackCount = 0, indexedAt = null, archiveCount = 0), f.modland),
            ), f.browse.value)
            entered.await()
            val during = f.browse.value.copy(query = "changed", openFormat = "kept", openAuthor = "kept")
            f.browse.value = during
            gate.complete(Unit)
            drain()
            assertEquals(during.copy(indexing = emptyMap(), deleting = emptySet(), openCatalogue = null,
                searchScope = SearchScope.Online(setOf("modland"))), f.browse.value)
            assertEquals(listOf("label", "begin:asma", "notice", "index:asma", "archive:asma", "on-phone", "refresh", "end:asma"), f.events)
            assertEquals(1, f.notices.size)
            assertTrue(f.calls.single { it.first == "archive:asma" }.second !== Thread.currentThread())
            assertTrue(f.player.value.playing)
        }
    }

    @Test
    fun `index failure uses the captured display name and releases optimistic deletion without refreshing`() = runBlocking {
        Fixture(this).use { f ->
            f.before = {
                f.browse.update { it.copy(catalogues = emptyList()) }
                throw IllegalStateException("index unavailable")
            }
            f.maintenance.deleteCatalogueIndex("asma")
            drain()
            assertEquals(listOf(Notice.Freed(23L), Notice.DeleteFailed("Archive title")), f.notices)
            assertEquals(listOf("label", "begin:asma", "notice", "index:asma", "notice", "end:asma"), f.events)
            assertTrue(f.browse.value.indexing.isEmpty())
            assertTrue(f.browse.value.deleting.isEmpty())
            assertEquals(f.asma, f.browse.value.openCatalogue)
            assertTrue(f.browse.value.archiveBytes.isEmpty())
        }
    }

    @Test(timeout = 5_000)
    fun `cancelling an index read retains the failure notice and releases both deletion markers`() = runBlocking {
        withFailures { f, failures ->
            val entered = CompletableDeferred<Unit>()
            f.before = { entered.complete(Unit); awaitCancellation() }
            f.maintenance.deleteCatalogueIndex("asma")
            entered.await()
            f.scope.cancel()
            f.scope.coroutineContext.job.join()
            assertTrue(failures.isEmpty())
            assertEquals(listOf(Notice.Freed(23L), Notice.DeleteFailed("Archive title")), f.notices)
            assertEquals("end:asma", f.events.last())
            assertTrue(f.browse.value.indexing.isEmpty())
            assertTrue(f.browse.value.deleting.isEmpty())
            assertFalse("refresh" in f.events)
        }
    }

    @Test
    fun `only Modland drops favourites and the random scope is sampled after clearing them`() = runBlocking {
        for ((id, changeScope) in listOf("modland" to false, "modland" to true, "unknown" to false)) {
            Fixture(this).use { f ->
                f.browse.update { it.copy(randomScope = RandomScope.Favourites, searchScope = SearchScope.Everywhere) }
                f.before = { if (it == "favourites" && changeScope) f.browse.update { state -> state.copy(randomScope = RandomScope.Everything) } }
                f.maintenance.deleteCatalogueIndex(id)
                drain()
                assertEquals(listOf(Notice.IndexDeleted), f.notices)
                assertEquals(id == "modland", "favourites" in f.events)
                assertEquals(id == "modland" && !changeScope, "leave-favourites" in f.events)
                assertEquals(if (id == "modland") RandomScope.Everything else RandomScope.Favourites, f.browse.value.randomScope)
                assertEquals(SearchScope.Everywhere, f.browse.value.searchScope)
                assertEquals(f.asma, f.browse.value.openCatalogue)
                assertFalse(f.events.any { it.startsWith("archive:") })
            }
        }
    }

    @Test
    fun `favourite clearing failure propagates but still releases the catalogue guard`() = runBlocking {
        withFailures { f, failures ->
            val failure = IllegalStateException("favourites unavailable")
            f.before = { if (it == "favourites") throw failure }
            f.maintenance.deleteCatalogueIndex("modland")
            f.scope.drain()
            assertSame(failure, failures.single())
            assertEquals(listOf("label", "begin:modland", "notice", "index:modland", "favourites", "end:modland"), f.events)
            assertTrue(f.browse.value.indexing.isEmpty())
            assertTrue(f.browse.value.deleting.isEmpty())
        }
    }

    @Test
    fun `metadata deletion clears only downloaded flags immediately and preserves unknown held state`() = runBlocking {
        for (known in listOf(false, true)) Fixture(this).use { f ->
            if (!known) f.browse.update { it.copy(held = null) }
            val before = f.browse.value
            f.maintenance.deleteSongMetadata()
            assertEquals(listOf("notice"), f.events)
            assertEquals(before.copy(held = before.held?.copy(songLengths = false, trackMetadata = false, songDbLengths = false),
                songLengthCount = 0, trackMetadataCount = 0, songDbLengthCount = 0), f.browse.value)
            drain()
            assertEquals(listOf("notice", "lengths", "metadata", "songdb", "refresh"), f.events)
            assertEquals(listOf(Notice.MetadataDeleted), f.notices)
            assertTrue(f.player.value.playing)
        }
    }

    @Test
    fun `metadata failure stops the remaining tables reports failure and still refreshes`() = runBlocking {
        val tables = listOf("lengths", "metadata", "songdb")
        for (failedAt in tables.indices) Fixture(this).use { f ->
            f.before = { if (it == tables[failedAt]) throw IllegalStateException("table unavailable") }
            f.maintenance.deleteSongMetadata()
            drain()
            assertEquals(listOf("notice") + tables.take(failedAt + 1) + listOf("notice", "refresh"), f.events)
            assertEquals(listOf(Notice.MetadataDeleted, Notice.MetadataDeleteFailed), f.notices)
        }
    }

    @Test(timeout = 5_000)
    fun `metadata cancellation retains the existing caught-failure and refresh calls`() = runBlocking {
        withFailures { f, failures ->
            val entered = CompletableDeferred<Unit>()
            f.before = { entered.complete(Unit); awaitCancellation() }
            f.maintenance.deleteSongMetadata()
            entered.await()
            f.scope.cancel()
            f.scope.coroutineContext.job.join()
            assertEquals(listOf("notice", "lengths", "notice", "refresh"), f.events)
            assertEquals(listOf(Notice.MetadataDeleted, Notice.MetadataDeleteFailed), f.notices)
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun `replay removal keeps all three deletes version timing and background configuration even after false`() = runBlocking {
        for ((sc68, uade) in listOf(true to true, false to true, true to false, false to false)) Fixture(this).use { f ->
            f.sc68Removed = sc68
            f.uadeRemoved = uade
            val before = f.browse.value
            f.maintenance.deleteReplayRoutines()
            assertEquals(listOf("notice"), f.events)
            assertEquals(before.copy(held = before.held?.copy(replays = false, players = false),
                replayCount = 0, replayBytes = 0L, playerCount = 0, playerBytes = 0L), f.browse.value)
            drain()
            val expected = listOf(Notice.Freed(60L)) + if (sc68 && uade) emptyList() else listOf(Notice.ReplaysDeleteFailed)
            assertEquals(expected, f.notices)
            assertEquals(listOf("notice", "sc68", "uade", "adopted") +
                (if (sc68 && uade) emptyList() else listOf("notice")) + listOf("version", "configure", "refresh"), f.events)
            assertEquals("777", f.configuredVersion)
            assertTrue(f.calls.filter { it.first in setOf("sc68", "uade", "adopted", "configure") }.all { it.second !== Thread.currentThread() })
            assertTrue(f.calls.filter { it.first in setOf("notice", "version", "refresh") }.all { it.second === Thread.currentThread() })
        }
    }

    @Test
    fun `replay file or version exceptions propagate and do not report completion`() = runBlocking {
        for (failedAt in listOf("uade", "version")) withFailures { f, failures ->
            val failure = IllegalStateException(failedAt)
            f.fileAction = { if (it == failedAt) throw failure }
            f.maintenance.deleteReplayRoutines()
            f.scope.drain()
            assertPropagated(failure, failures.single())
            assertEquals(listOf("notice", "sc68", "uade") + (if (failedAt == "version") listOf("adopted", "version") else emptyList()), f.events)
            assertEquals(listOf(Notice.Freed(60L)), f.notices)
            assertEquals(null, f.configuredVersion)
        }
    }

    @Test
    fun `cache notices are constructed inside atomic retries but ordinary notices are rendered once`() {
        val player = MutableStateFlow(PlayerUiState())
        var calls = 0
        val state = LibraryMaintenanceState(player, MutableStateFlow(BrowseState())) { notice ->
            calls++
            if (calls == 1) player.update { it.copy(positionSeconds = it.positionSeconds + 1.0) }
            Message("$notice-$calls")
        }
        state.cacheCleared(17L)
        assertEquals(2, calls)
        assertEquals(1.0, player.value.positionSeconds, 0.0)
        assertEquals("Freed(bytes=17)-2", player.value.message?.text)
        calls = 0
        state.say(Notice.IndexDeleted)
        assertEquals(1, calls)
        assertEquals(2.0, player.value.positionSeconds, 0.0)
        assertEquals("IndexDeleted-1", player.value.message?.text)
    }
}
