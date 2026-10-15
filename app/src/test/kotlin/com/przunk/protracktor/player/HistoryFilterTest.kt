// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class HistoryFilterTest {
    @Test fun `literal phrases match each field with Unicode case folding`() {
        assertTrue(HistoryFilter.matches(" ŻÓŁW ", "Mały żółw", "", "x.mod"))
        assertTrue(HistoryFilter.matches("chris", "Ocean", "Chris Hülsbeck", "x.mod"))
        assertTrue(HistoryFilter.matches("LINKO.MOD", "Decoded", "", "folder/linko.mod"))
        assertFalse(HistoryFilter.matches("Ocean Chris", "Ocean", "Chris", "x.mod"))
        assertTrue(HistoryFilter.matches("", "", "", ""))
    }

    @Test fun `wildcards quotes and accents retain their literal meaning`() {
        assertTrue(HistoryFilter.matches("%_!", "100%_!", "", ""))
        assertFalse(HistoryFilter.matches("%", "Ordinary", "", "x.mod"))
        assertTrue(HistoryFilter.matches("' OR 1=1", "' OR 1=1", "", ""))
        assertFalse(HistoryFilter.matches("zolw", "Żółw", "", ""))
    }

    @Test fun `one traversal counts everything but returns only the requested hundred`() {
        var visited = 0
        val result = HistoryFilter.page(sequence { repeat(100_007) { visited++; yield(it) } }, 1)
        assertEquals(100_007, visited)
        assertEquals(100_007, result.total)
        assertEquals(1, result.page)
        assertEquals((100..199).toList(), result.rows)
    }

    @Test fun `shrinking out of range and enormous pages return the last partial page`() {
        for (wanted in listOf(3, Int.MAX_VALUE)) {
            assertEquals(HistoryPage(207, 2, (200..206).toList()), HistoryFilter.page((0..206).asSequence(), wanted))
        }
        assertEquals(HistoryPage(0, 0, emptyList<Int>()), HistoryFilter.page(emptySequence<Int>(), 9))
        assertEquals(HistoryPage(3, 0, listOf(0, 1, 2)), HistoryFilter.page((0..2).asSequence(), -1))
    }
}
