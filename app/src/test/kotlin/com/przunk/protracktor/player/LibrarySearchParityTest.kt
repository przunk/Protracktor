// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.SearchTerms
import java.io.File
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchParityTest {
    @Test fun `query terms agree with the shared WEB matching cases`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val rows = File(root, "docs/rules/search-library-cases.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("case\tquery\tfirst\tsecond\texpected", rows.first())
        assertTrue("No shared library-search cases executed", rows.size > 1)
        rows.drop(1).forEach { row ->
            val cells = row.split('\t')
            assertEquals(row, 5, cells.size)
            val values = cells.subList(1, 4).map { URLDecoder.decode(it, "UTF-8") }
            assertEquals(cells[0], cells[4].toBooleanStrict(),
                SearchTerms.matchesAny(values[0], values[1], values[2]))
        }
    }
}
