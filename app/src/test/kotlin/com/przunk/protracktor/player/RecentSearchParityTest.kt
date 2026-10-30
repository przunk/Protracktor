// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import java.io.File
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchParityTest {
    @Test fun `recent query rules match the shared WEB cases`() {
        val relative = "docs/rules/recent-search-cases.tsv"
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
        requireNotNull(root) { "Project root is missing" }
        assertTrue("$relative is missing", File(root, relative).isFile)
        fun text(value: String) = URLDecoder.decode(value, "UTF-8")
        fun list(value: String) = if (value == "-") emptyList() else value.split('|').map(::text)
        val lines = File(root, relative).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("case\top\tqueries\tinput\texpected", lines.first())
        val rows = lines.drop(1)
        assertTrue("No shared recent-search cases executed", rows.isNotEmpty())
        rows.forEach { line ->
            val cells = line.split('\t')
            assertEquals(line, 5, cells.size)
            val queries = list(cells[2])
            val actual = when (cells[1]) {
                "clean" -> RecentSearches.clean(queries)
                "record" -> RecentSearches.record(queries, text(cells[3]))
                "suggest" -> RecentSearches.suggestions(queries, text(cells[3]))
                else -> error("Unknown recent-search operation: ${cells[1]}")
            }
            assertEquals(cells[0], list(cells[4]), actual)
        }
    }
}
