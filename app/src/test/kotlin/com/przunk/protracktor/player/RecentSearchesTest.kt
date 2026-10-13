// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class RecentSearchesTest {
    @Test fun `empty field returns only the three latest queries`() {
        assertEquals(listOf("four", "three", "two"), RecentSearches.suggestions(listOf("four", "three", "two", "one"), ""))
        assertTrue(RecentSearches.suggestions(emptyList(), "").isEmpty())
    }

    @Test fun `prefix filtering happens before the three entry cap`() {
        val history = listOf("alpha", "beta", "gamma", "delta one", "DELTA two", "delta three", "delta four")
        assertEquals(listOf("delta one", "DELTA two", "delta three"), RecentSearches.suggestions(history, "DeLtA"))
    }

    @Test fun `substring matches are not prefix matches and no match offers no entries`() {
        val history = listOf("alpha delta", "delta", "red delta")
        assertEquals(listOf("delta"), RecentSearches.suggestions(history, "delta"))
        assertTrue(RecentSearches.suggestions(history, "missing").isEmpty())
    }

    @Test fun `matching is case insensitive for accented letters and ignores leading field whitespace`() {
        assertEquals(listOf("Żółw"), RecentSearches.suggestions(listOf("Żółw", "żaba"), "  żó"))
    }

    @Test fun `reusing a query moves its latest trimmed spelling to the front without duplicates`() {
        assertEquals(listOf("ALPHA", "beta", "gamma"), RecentSearches.record(listOf("beta", "alpha", "gamma"), " ALPHA "))
        assertEquals(listOf("alpha"), RecentSearches.clean(listOf("", " alpha ", "ALPHA", " ")))
    }

    @Test fun `only the latest twenty unique nonblank queries survive and inputs remain unchanged`() {
        val input = (20 downTo 1).map { "query $it" }
        val result = RecentSearches.record(input, "new")
        assertEquals(20, result.size)
        assertEquals("new", result.first())
        assertEquals("query 2", result.last())
        assertEquals("query 1", input.last())
    }

    @Test fun `blank submissions never displace real queries`() {
        val input = listOf("one", "two")
        assertSame(input, RecentSearches.record(input, " \n\t "))
    }

    @Test fun `history records only submissions and writes before publishing a changed list`() {
        val writes = mutableListOf<List<String>>()
        lateinit var history: RecentSearchHistory
        history = RecentSearchHistory(object : RecentSearchHistory.Store {
            override fun read() = listOf(" old ", "OLD", "")
            override fun write(queries: List<String>) {
                assertEquals(listOf("old"), history.queries.value)
                writes += queries
            }
        })
        assertEquals(listOf("old"), history.queries.value)
        assertTrue(writes.isEmpty())
        history.record(" ")
        assertTrue(writes.isEmpty())
        history.record("new")
        assertEquals(listOf(listOf("new", "old")), writes)
        assertEquals(writes.last(), history.queries.value)
        history.record("new")
        assertEquals(1, writes.size)
    }
}
