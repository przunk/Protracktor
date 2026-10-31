// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryLiveUpdatesTest {
    private class Fixture(scope: CoroutineScope) {
        val stored = mutableListOf(TrackRef("old", "Old tune"))
        val browse = MutableStateFlow(BrowseState(historyVisit = 1))
        val calls = mutableListOf<Pair<String, Int>>()
        val errors = mutableListOf<Throwable>()
        var readGate: CompletableDeferred<Unit>? = null
        var readFails = false
        val writes = HistoryWrites(scope,
            record = { track -> stored.removeAll { it.id == track.id }; stored.add(0, track) },
            qualify = {}, erase = { stored.clear() }, failed = { errors += it })
        val browser = HistoryBrowser(scope, browse, writes.revision,
            read = { query, page ->
                calls += query to page
                val result = HistoryFilter.page(stored.asSequence().filter {
                    HistoryFilter.matches(query, it.title, it.author, it.fileName)
                }, page)
                readGate?.let { withContext(NonCancellable) { it.await() } }
                if (readFails) error("controlled History read failure")
                result
            }, clear = { stored.clear() }, onFailure = { errors += it })

        fun record(id: String = "new", title: String = "New tune", fromHistory: Boolean = false) {
            if (HistoryRecording.records(fromHistory)) writes.record(TrackRef(id, title), writes.generation)
        }
    }

    @Test fun `a committed play appears in the open History without a new visit or loading frame`() = runTest {
        val f = Fixture(backgroundScope)
        f.browser.open(); runCurrent()
        val visit = f.browse.value.historyVisit
        val loadingFrames = mutableListOf<Boolean>()
        backgroundScope.launch { f.browse.collect { loadingFrames += it.loading } }
        runCurrent()
        f.record(); runCurrent()
        assertEquals(listOf("new", "old"), f.browse.value.tracks.map { it.id })
        assertEquals(2, f.browse.value.historyTotal)
        assertEquals(visit, f.browse.value.historyVisit)
        assertFalse(f.browse.value.loading)
        assertFalse(loadingFrames.any { it })
        assertTrue(f.errors.isEmpty())
    }

    @Test fun `refresh retains the phrase and page and leaves captured playback rows immutable`() = runTest {
        val f = Fixture(backgroundScope)
        f.stored.clear()
        f.stored += (0 until 150).map { TrackRef("match-$it", "Match $it") }
        f.browser.open(); runCurrent()
        f.browser.setQuery(" MATCH "); advanceTimeBy(200); runCurrent()
        f.browser.showPage(1); runCurrent()
        val captured = f.browse.value.tracks
        f.record("other", "Unrelated"); runCurrent()
        f.record("new-match", "Match new"); runCurrent()
        assertEquals(" MATCH ", f.browse.value.historyQuery)
        assertEquals(1, f.browse.value.historyPage)
        assertEquals(151, f.browse.value.historyTotal)
        assertEquals("match-99", f.browse.value.tracks.first().id)
        assertEquals("match-100", captured.first().id)
        assertEquals(50, captured.size)
    }

    @Test fun `a write during a held read is not lost when that older read completes`() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.readGate = gate
        f.browser.open(); runCurrent()
        f.record(); runCurrent()
        assertTrue(f.browse.value.loading)
        f.readGate = null
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("new", "old"), f.browse.value.tracks.map { it.id })
        assertEquals(2, f.calls.size)
    }

    @Test fun `writes do not bypass a pending phrase debounce`() = runTest {
        val f = Fixture(backgroundScope)
        f.browser.open(); runCurrent()
        f.browser.setQuery("new")
        f.record(); runCurrent()
        advanceTimeBy(199); runCurrent()
        assertEquals(listOf("" to 0), f.calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("new"), f.browse.value.tracks.map { it.id })
        assertEquals("new", f.browse.value.historyQuery)
    }

    @Test fun `clear retires a held refresh and cannot be cancelled by the revision observer`() = runTest {
        val f = Fixture(backgroundScope)
        f.browser.open(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        f.readGate = gate
        f.record(); runCurrent()
        val cleared = f.writes.clear()
        var confirmed = false
        f.browser.clearAll({ cleared.await(); confirmed = true }, { f.errors += it })
        runCurrent()
        f.readGate = null
        gate.complete(Unit); runCurrent()
        assertTrue(confirmed)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertEquals(0, f.browse.value.historyTotal)
        assertTrue(f.errors.isEmpty())
        f.record("after-clear"); runCurrent()
        assertEquals(listOf("after-clear"), f.browse.value.tracks.map { it.id })
    }

    @Test fun `other domains do not query History and a new visit reads the latest rows`() = runTest {
        val f = Fixture(backgroundScope)
        f.browser.open(); runCurrent()
        f.browse.update { it.copy(domain = BrowseDomain.ONLINE, tracks = listOf(TrackRef("foreign", "Foreign"))) }
        f.record(); runCurrent()
        assertEquals(1, f.calls.size)
        assertEquals("foreign", f.browse.value.tracks.single().id)
        f.browse.update { BrowseNavigation.enteringDomain(it, BrowseDomain.HISTORY) }
        f.browser.open(); runCurrent()
        assertEquals(listOf("new", "old"), f.browse.value.tracks.map { it.id })
        assertEquals(2, f.calls.size)
    }

    @Test fun `History playback and qualification never change recency or the row count`() = runTest {
        val f = Fixture(backgroundScope)
        f.browser.open(); runCurrent()
        f.record(fromHistory = true)
        f.writes.qualify(ListeningSession.Qualified("old", f.writes.generation)); runCurrent()
        assertEquals(listOf("old"), f.browse.value.tracks.map { it.id })
        assertEquals(1, f.browse.value.historyTotal)
    }

    @Test fun `returning preserves the captured History source until another committed write`() = runTest {
        val f = Fixture(backgroundScope)
        f.stored.clear()
        f.stored += (0 until 150).map { TrackRef("match-$it", "Match $it") }
        f.browser.open(); runCurrent()
        f.browser.setQuery("match"); advanceTimeBy(200); runCurrent()
        f.browser.showPage(1); runCurrent()
        val snapshot = f.browse.value
        f.record("new-match", "Match new"); runCurrent()
        f.browse.update { BrowseNavigation.enteringDomain(it, BrowseDomain.HISTORY) }
        f.browser.open(); runCurrent()
        f.browse.update { QueueSourceNavigation.restore(snapshot, it) }; runCurrent()
        assertEquals(snapshot.tracks, f.browse.value.tracks)
        assertEquals(snapshot.historyTotal, f.browse.value.historyTotal)
        f.record("another-match", "Match another"); runCurrent()
        assertEquals("match", f.browse.value.historyQuery)
        assertEquals(1, f.browse.value.historyPage)
        assertEquals(152, f.browse.value.historyTotal)
        assertEquals("match-98", f.browse.value.tracks.first().id)
        assertEquals("match-100", snapshot.tracks.first().id)
    }

    @Test fun `a failed automatic refresh reports once and the same phrase retries`() = runTest {
        val f = Fixture(backgroundScope)
        f.browser.open(); runCurrent()
        f.readFails = true
        f.record(); runCurrent()
        assertTrue(f.browse.value.historyReadFailed)
        assertFalse(f.browse.value.loading)
        assertEquals(1, f.errors.size)
        val failedCalls = f.calls.size
        runCurrent()
        assertEquals(failedCalls, f.calls.size)
        f.readFails = false
        f.browser.setQuery(""); advanceTimeBy(200); runCurrent()
        assertEquals(listOf("new", "old"), f.browse.value.tracks.map { it.id })
        assertFalse(f.browse.value.historyReadFailed)
    }
}
