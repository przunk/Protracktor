// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

import java.io.File
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheBudgetParityTest {
    private fun rows(name: String, header: String): List<List<String>> {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
        requireNotNull(root) { "Project root is missing" }
        val lines = File(root, "docs/rules/$name.tsv").readLines()
        val count = lines.first { it.startsWith("# cases: ") }.substringAfter(": ").toInt()
        val data = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(header, data.first())
        val rows = data.drop(1).map { it.split('\t') }
        assertTrue("Shared cases must not be empty", count > 0)
        assertEquals("A shared case is missing", count, rows.size)
        return rows
    }

    private fun text(value: String) = URLDecoder.decode(value, "UTF-8")
    private fun names(value: String) = if (value == "-") emptyList() else value.split('|').map(::text)

    @Test fun `cache budgets match the shared WEB cases`() {
        val cases = rows("cache-budget-cases", "case\tentries\tinUse\tceiling\tevicted\ttotal")
        assertEquals(cases.size, cases.map { it[0] }.toSet().size)
        cases.forEach { row ->
            assertEquals(6, row.size)
            val entries = if (row[1] == "-") emptyList() else row[1].split('|').map { part ->
                val fields = part.split(',')
                CacheEntry(text(fields[0]), fields[1].toLong(), fields[2].toLong())
            }
            val ceiling = if (row[3] == "default") CacheBudget.DEFAULT_CEILING_BYTES else row[3].toLong()
            assertEquals(row[0], row[5].toLong(), CacheBudget.totalBytes(entries))
            assertEquals(row[0], names(row[4]), CacheBudget.evictions(entries, names(row[2]).toSet(), ceiling))
        }
    }

    @Test fun `cache deletion quantities match the shared WEB cases`() {
        rows("cache-freed-cases", "bytes\tkind\tcount").forEach { row ->
            assertEquals(3, row.size)
            val (kind, count) = when (val freed = CacheBudget.describeFreed(row[0].toLong())) {
                CacheBudget.Freed.NOTHING -> "nothing" to 0L
                CacheBudget.Freed.LESS_THAN_A_MEGABYTE -> "under-megabyte" to 0L
                is CacheBudget.Freed.Megabytes -> "megabytes" to freed.count
            }
            assertEquals(row[0], row[1], kind)
            assertEquals(row[0], row[2].toLong(), count)
        }
    }
}
