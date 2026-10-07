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
class PlayedStatusReadsTest {
    @Test fun `clear rejects a noncancellable late read and does not query until clear completes`() = runTest {
        val ids = MutableStateFlow(setOf("old"))
        val revision = MutableStateFlow(HistoryWrites.Revision())
        val gate = CompletableDeferred<Unit>()
        val shown = mutableListOf<Set<String>>()
        var queries = 0
        PlayedStatusReads(backgroundScope, ids, revision, query = {
            queries++
            if (queries == 1) withContext(NonCancellable) { gate.await() }
            if (queries == 1) setOf("old") else emptySet()
        }, show = { shown += it }, failed = { throw AssertionError(it) })
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
        PlayedStatusReads(backgroundScope, ids, revision, query = {
            requested += it
            gate?.await()
            it
        }, show = { shown += it }, failed = { throw AssertionError(it) })
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
