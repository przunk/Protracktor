// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPresentationReadsTest {
    private fun rows(ids: Set<String>) = ids.associateWith { HistoryPresentation("Title $it", true) }

    @Test fun `record revisions refresh titles for unchanged IDs before listened qualification`() = runTest {
        val ids = MutableStateFlow(setOf("a"))
        val revision = MutableStateFlow(HistoryWrites.Revision())
        var title = "First"
        val shown = mutableListOf<Map<String, HistoryPresentation>>()
        HistoryPresentationReads(backgroundScope, ids, revision,
            query = { mapOf("a" to HistoryPresentation(title, false)) },
            show = { shown += it }, failed = { throw AssertionError(it) })
        runCurrent()
        assertEquals(HistoryPresentation("First", false), shown.last()["a"])
        title = "Better"
        revision.value = HistoryWrites.Revision(1)
        runCurrent()
        assertEquals(HistoryPresentation("Better", false), shown.last()["a"])
        revision.value = HistoryWrites.Revision(2, clearing = true)
        runCurrent()
        assertTrue(shown.last().isEmpty())
    }
    @Test fun `clear rejects a noncancellable late read and does not query until clear completes`() = runTest {
        val ids = MutableStateFlow(setOf("old"))
        val revision = MutableStateFlow(HistoryWrites.Revision())
        val gate = CompletableDeferred<Unit>()
        val shown = mutableListOf<Set<String>>()
        var queries = 0
        HistoryPresentationReads(backgroundScope, ids, revision, query = {
            queries++
            if (queries == 1) withContext(NonCancellable) { gate.await() }
            if (queries == 1) rows(setOf("old")) else emptyMap()
        }, show = { shown += it.keys }, failed = { throw AssertionError(it) })
        runCurrent()
        revision.value = HistoryWrites.Revision(1, clearing = true)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertFalse(shown.any { "old" in it })
        assertEquals(1, queries)
        revision.value = HistoryWrites.Revision(2)
        runCurrent()
        assertEquals(2, queries)
        assertEquals(emptySet<String>(), shown.last())
    }

    @Test fun `queries only requested IDs and retains known marks during refresh`() = runTest {
        val ids = MutableStateFlow(setOf("a"))
        val revision = MutableStateFlow(HistoryWrites.Revision())
        val requested = mutableListOf<Set<String>>()
        val shown = mutableListOf<Set<String>>()
        var gate: CompletableDeferred<Unit>? = null
        HistoryPresentationReads(backgroundScope, ids, revision, query = {
            requested += it
            gate?.await()
            rows(it)
        }, show = { shown += it.keys }, failed = { throw AssertionError(it) })
        runCurrent()
        assertEquals(setOf("a"), shown.last())
        gate = CompletableDeferred()
        revision.value = HistoryWrites.Revision(1)
        runCurrent()
        assertEquals(setOf("a"), shown.last())
        gate.complete(Unit)
        runCurrent()
        ids.value = setOf("b")
        runCurrent()
        assertEquals(listOf(setOf("a"), setOf("a"), setOf("b")), requested)
        assertEquals(setOf("b"), shown.last())
    }
}
