// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every number the app prints is grouped, in both languages.
 *
 * Two of the forty-five were, and the rest were not, so the same phone said "61,157 tunes" on one
 * row and "300000 tunes described" on the next. `%,d` is locale-aware — a comma in English, a
 * non-breaking space in Polish — which is the whole reason to spell it rather than to group the
 * digits by hand somewhere in Kotlin.
 *
 * Small numbers are grouped too. `%,d` of 5 is "5", so there is nothing to decide per string, and a
 * rule with no exceptions is one nobody has to remember.
 */
class NumbersAreGroupedTest {

    private val strings: List<File> by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        val files = listOf("values", "values-pl")
            .map { File(here, "app/src/main/res/$it/strings.xml") }
        files.forEach { assertTrue("${it.path} is missing", it.isFile) }
        files
    }

    @Test
    fun `no string prints an ungrouped integer`() {
        val ungrouped = Regex("""%(\d+\$)?d""")
        val offenders = strings.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> ungrouped.containsMatchIn(line) }
                .map { (index, line) -> "${file.parentFile.name}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(
            "these print a bare number; use %,d so 300000 reads as 300,000 (or 300 000 in Polish)",
            emptyList<String>(),
            offenders,
        )
    }
}
