// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import java.io.File
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterParityTest {
    @Test fun `literal History matching agrees with the shared WEB cases`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val rows = File(root, "docs/rules/history-filter-cases.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("case\tquery\ttitle\tauthor\tfile\texpected", rows.first())
        assertTrue("No shared History cases executed", rows.size > 1)
        rows.drop(1).forEach { row ->
            val cells = row.split('\t')
            assertEquals(row, 6, cells.size)
            val values = cells.subList(1, 5).map { URLDecoder.decode(it, "UTF-8") }
            assertEquals(cells[0], cells[5].toBooleanStrict(),
                HistoryFilter.matches(values[0], values[1], values[2], values[3]))
        }
    }
}
