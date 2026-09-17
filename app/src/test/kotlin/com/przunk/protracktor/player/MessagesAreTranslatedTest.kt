// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every sentence the app says comes from `strings.xml`, and both languages have all of them.
 *
 * A `Message("…")` built from a literal compiles, runs and reads perfectly well in the workshop.
 * It is only wrong on somebody else's phone, in the other language, where the screen is Polish and
 * the notice that lands on top of it is English — which is what the first round of testers
 * reported (`docs/STATUS.md`).
 *
 * Two checks, and they fail for different reasons:
 *
 * - **no literal reaches [PlaybackController.Message]**, so a sentence cannot be added in one
 *   language by accident;
 * - **`values/` and `values-pl/` name exactly the same strings**, so a sentence cannot be added in
 *   one language on purpose and forgotten in the other.
 */
class MessagesAreTranslatedTest {

    /** Walked up to, for `RuleCasesTest`'s reason: a test that finds nothing must fail. */
    private val root: File by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        assertTrue("repository root not found from ${File(".").absolutePath}", here != null)
        here!!
    }

    @Test
    fun `no notice is built from a literal`() {
        val sources = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("no Kotlin sources found under $root", sources.isNotEmpty())

        val offenders = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> LITERAL_MESSAGE.containsMatchIn(line) }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(
            "a notice built from a literal reaches a Polish phone in English; " +
                "put it in strings.xml and read it with context.getString",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the two languages name the same strings`() {
        val english = namesIn(File(root, "app/src/main/res/values/strings.xml"))
        val polish = namesIn(File(root, "app/src/main/res/values-pl/strings.xml"))
        assertTrue("no strings found in values/strings.xml", english.isNotEmpty())
        assertEquals("translated in English and missing in Polish", emptySet<String>(), english - polish)
        assertEquals("present in Polish and unknown in English", emptySet<String>(), polish - english)
    }

    private fun namesIn(file: File): Set<String> {
        assertTrue("${file.path} is missing", file.isFile)
        return NAME.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    private companion object {
        /** `Message("…")` and `Message(text = "…")`, which are the two shapes it is written in. */
        val LITERAL_MESSAGE = Regex("""Message\(\s*(text\s*=\s*)?"""")
        val NAME = Regex("""<(?:string|plurals)\s+name="([^"]+)"""")
    }
}
