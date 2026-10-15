// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class HistoryBrowserTest {
    private class Fixture(scope: CoroutineScope) {
        val browse = MutableStateFlow(BrowseState(domain = BrowseDomain.HISTORY, historyVisit = 1))
        val calls = mutableListOf<Pair<String, Int>>()
        val errors = mutableListOf<Throwable>()
        var reader: suspend (String, Int) -> HistoryPage<TrackRef> = { query, _ ->
            HistoryPage(1, 0, listOf(TrackRef(query.ifEmpty { "all" }, query)))
        }
        val browser = HistoryBrowser(scope, browse,
            read = { query, page -> calls += query to page; reader(query, page) },
            clear = {}, onFailure = { errors += it })
    }

    private suspend fun CoroutineScope.drain() {
        while (coroutineContext.job.children.any()) coroutineContext.job.children.toList().joinAll()
    }

    @Test(timeout = 5000) fun `edits debounce to one latest full History request and reset the page`() = runBlocking {
        val f = Fixture(this)
        f.browser.open(); drain()
        f.browse.update { it.copy(historyPage = 3) }
        val started = System.nanoTime()
        f.browser.setQuery("superseded")
        f.browser.setQuery(" latest ")
        assertTrue(f.browse.value.loading)
        assertTrue(f.browse.value.tracks.isEmpty())
        drain()
        assertTrue("200 ms debounce did not run", (System.nanoTime() - started) / 1_000_000 >= 180)
        assertEquals(listOf("" to 0, "latest" to 0), f.calls)
        assertEquals(" latest ", f.browse.value.historyQuery)
        assertEquals(0, f.browse.value.historyPage)
        assertEquals("latest", f.browse.value.tracks.single().id)
    }

    @Test(timeout = 5000) fun `retired identical input cannot replace a newer occurrence`() = runBlocking {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var occurrence = 0
        f.reader = { query, _ ->
            val number = ++occurrence
            if (number == 1) { entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
            HistoryPage(1, 0, listOf(TrackRef("$query-$number", query)))
        }
        f.browser.setQuery("same")
        entered.await()
        f.browser.setQuery("intermediate")
        f.browser.setQuery("same")
        withTimeout(3000) { f.browse.first { it.tracks.firstOrNull()?.id == "same-2" } }
        gate.complete(Unit)
        drain()
        assertEquals("same-2", f.browse.value.tracks.single().id)
        assertEquals(2, f.calls.size)
        assertTrue(f.errors.isEmpty())
    }

    @Test(timeout = 5000) fun `leaving History suppresses late errors and does not clear another view loading`() = runBlocking {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.reader = { _, _ ->
            entered.complete(Unit)
            withContext(NonCancellable) { gate.await() }
            error("obsolete failure")
        }
        f.browser.setQuery("old")
        entered.await()
        f.browse.update { it.copy(domain = BrowseDomain.ONLINE, loading = true, tracks = listOf(TrackRef("foreign", "Foreign"))) }
        val expected = f.browse.value
        gate.complete(Unit); drain()
        assertSame(expected, f.browse.value)
        assertTrue(f.errors.isEmpty())
    }

    @Test(timeout = 5000) fun `new visit invalidates an abandoned filtered read`() = runBlocking {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.reader = { query, _ ->
            if (query == "old") { entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
            HistoryPage(1, 0, listOf(TrackRef(query.ifEmpty { "new visit" }, query)))
        }
        f.browser.setQuery("old"); entered.await()
        f.browse.update { BrowseNavigation.enteringDomain(it, BrowseDomain.HISTORY) }
        f.browser.open()
        withTimeout(3000) { f.browse.first { !it.loading } }
        gate.complete(Unit); drain()
        assertEquals("", f.browse.value.historyQuery)
        assertEquals("new visit", f.browse.value.tracks.single().id)
    }

    @Test(timeout = 5000) fun `current failures stop loading and the same phrase can retry`() = runBlocking {
        val f = Fixture(this)
        var fail = true
        f.reader = { query, _ ->
            if (fail) error("current failure")
            HistoryPage(1, 0, listOf(TrackRef(query, query)))
        }
        f.browser.setQuery("retry"); drain()
        assertFalse(f.browse.value.loading)
        assertTrue(f.browse.value.historyReadFailed)
        assertEquals(1, f.errors.size)
        fail = false
        f.browser.setQuery("retry"); drain()
        assertFalse(f.browse.value.historyReadFailed)
        assertEquals("retry", f.browse.value.tracks.single().id)
    }

    @Test(timeout = 5000) fun `clear cancels a pending debounce and leaves no old rows`() = runBlocking {
        val f = Fixture(this)
        f.browser.open(); drain()
        f.browser.setQuery("pending")
        var erased = false
        f.browser.clearAll({ erased = true }, { throw it })
        drain()
        assertTrue(erased)
        assertEquals(listOf("" to 0), f.calls)
        assertTrue(f.browse.value.tracks.isEmpty())
        assertEquals(0, f.browse.value.historyTotal)
        assertFalse(f.browse.value.loading)
    }

    @Test(timeout = 5000) fun `failed clear reloads a cancelled pending filter rather than leaving a spinner`() = runBlocking {
        val f = Fixture(this)
        f.browser.setQuery("pending")
        var failures = 0
        f.browser.clearAll({ error("clear failed") }, { failures++ })
        drain()
        assertEquals(1, failures)
        assertFalse(f.browse.value.loading)
        assertFalse(f.browse.value.historyReadFailed)
        assertEquals("pending", f.browse.value.tracks.single().id)
    }

    @Test fun `unchanged input is a no op and edits outside History do not mutate another domain`() = runBlocking {
        val f = Fixture(this)
        f.browser.open(); drain()
        val initial = f.browse.value
        f.browser.setQuery("")
        assertSame(initial, f.browse.value)
        f.browse.update { it.copy(domain = BrowseDomain.SEARCH) }
        val other = f.browse.value
        f.browser.setQuery("ignored")
        assertSame(other, f.browse.value)
        assertEquals(1, f.calls.size)
    }
}
