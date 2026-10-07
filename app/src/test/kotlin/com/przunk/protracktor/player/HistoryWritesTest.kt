// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryWritesTest {
    @Test fun `clear waits for active write rejects queued old work and allows new plays`() = runTest {
        val release = CompletableDeferred<Unit>()
        val rows = mutableSetOf<String>()
        val writes = HistoryWrites(backgroundScope,
            record = { release.await(); rows += it.id },
            qualify = { rows += "qualified:$it" },
            erase = { rows.clear() }, failed = { throw AssertionError(it) })
        writes.record(TrackRef("old", "old", ""), 0)
        runCurrent()
        writes.qualify(ListeningSession.Qualified("old", 0))
        val clear = writes.clear()
        assertTrue(writes.revision.value.clearing)
        writes.record(TrackRef("late", "late", ""), 0)
        writes.record(TrackRef("new", "new", ""), writes.generation)
        release.complete(Unit)
        runCurrent()
        clear.await()
        assertEquals(setOf("new"), rows)
        assertFalse(writes.revision.value.clearing)
    }

    @Test fun `qualification follows record without changing its ordering`() = runTest {
        val events = mutableListOf<String>()
        val writes = HistoryWrites(backgroundScope,
            record = { events += "record:${it.id}" }, qualify = { events += "qualify:$it" },
            erase = { events.clear() }, failed = { throw AssertionError(it) })
        writes.record(TrackRef("a", "a", ""), 0)
        writes.qualify(ListeningSession.Qualified("a", 0))
        runCurrent()
        assertEquals(listOf("record:a", "qualify:a"), events)
        assertEquals(1L, writes.revision.value.number)
    }
}
