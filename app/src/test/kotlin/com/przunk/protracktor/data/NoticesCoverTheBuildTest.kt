// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The open-source licences screen names everything that is built into the app.
 *
 * **Because a missing notice is invisible.** The BSD, MIT and Apache licences of libopenmpt,
 * HivelyTracker, bencodetools, libzakalwe, {fmt} and Oboe ask for their notices to travel with the
 * binary; nothing in the app fails when one is left out. So a decoder that `native/CMakeLists.txt`
 * builds and the table does not name fails here, and so does a table row naming a file that does
 * not exist -- the build would copy nothing, and the screen would show a heading over no text.
 */
class NoticesCoverTheBuildTest {

    private val root: File by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        assertTrue("the project root was not found", here != null)
        here!!
    }

    private val table: String by lazy { File(root, "app/notices/components.tsv").readText() }

    @Test
    fun `every decoder the native build adds has a notice`() {
        val cmake = File(root, "native/CMakeLists.txt").readText()
        val built = Regex("""add_subdirectory\((?:backends/)?(\w+)\)""").findAll(cmake)
            .map { it.groupValues[1] }
            .filter { it != "engine" }
            .toSet()
        assertTrue("the build lists its decoders", built.size >= 9)
        val named = OpenSourceNotices.parse(table).map { it.id }.toSet()
        // UADE brings two libraries of its author's with it, built by its own CMake file.
        val required = built + if ("uade" in built) setOf("bencodetools", "libzakalwe") else emptySet()
        assertEquals("decoders built with no notice", emptySet<String>(), required - named)
    }

    @Test
    fun `every file a row names exists`() {
        val missing = table.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .flatMap { OpenSourceNotices.sourcesOf(it).asSequence() }
            .filterNot { File(root, it).isFile }
            .toList()
        assertEquals("licence files named and not there", emptyList<String>(), missing)
    }

    @Test
    fun `a row becomes asset paths under its own id`() {
        val parsed = OpenSourceNotices.parse(
            "# comment\nuade\tUADE\t3.05\tGPL\tnative/vendor/uade/COPYING,native/vendor/uade/COPYING.GPL\n"
        ).single()
        assertEquals(listOf("notices/uade/COPYING", "notices/uade/COPYING.GPL"), parsed.files)
        assertEquals("3.05", parsed.version)
    }

    @Test
    fun `the app's own licence is first`() {
        assertEquals("protracktor", OpenSourceNotices.parse(table).first().id)
    }
}
