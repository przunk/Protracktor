// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SupportedFormats` and `web/src/formats.tsv` name the same things.
 *
 * **The page reads the file; the phone reads its own list; this is what stops them drifting**
 * (`GOAL.md` round 8, item 1). The browser keeps a catalogue row only if the file names it, so a
 * format added here and not there would be one the phone indexes and the page silently does not —
 * the kind of disagreement nobody notices until the owner looks for a tune on one screen that the
 * other has.
 *
 * It checks names, not decoders. Which decoder opens a name is the file's to say; whether the name
 * is on the list at all is the question both runtimes must answer the same way.
 */
class SupportedFormatsFileTest {

    private data class Row(val kind: String, val name: String, val decoders: List<String>, val platform: String)

    /** Found by walking up, for `RuleCasesTest`'s reason: a test that finds nothing must fail. */
    private val rows: List<Row> by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, FILE).isFile) here = here.parentFile
        val file = here?.let { File(it, FILE) }
        assertTrue("$FILE not found from ${File(".").absolutePath}", file != null && file.isFile)
        file!!.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val cells = line.split('\t')
                assertEquals("four tab-separated cells in \"$line\"", 4, cells.size)
                Row(cells[0], cells[1], cells[2].split(','), cells[3])
            }
    }

    @Test
    fun `the extensions are the phone's extensions`() {
        val inFile = rows.filter { it.kind == "extension" }.map { it.name }.toSet()
        assertEquals(SupportedFormats.extensions.toSortedSet(), inFile.toSortedSet())
    }

    @Test
    fun `the prefixes are the phone's prefixes`() {
        val inFile = rows.filter { it.kind == "prefix" }.map { it.name }.toSet()
        assertEquals(SupportedFormats.prefixes.toSortedSet(), inFile.toSortedSet())
    }

    @Test
    fun `every row is a kind the page understands and names a decoder the engine has`() {
        rows.forEach { row ->
            assertTrue("unknown kind in $row", row.kind == "extension" || row.kind == "prefix")
            assertTrue("no decoder in $row", row.decoders.isNotEmpty())
            row.decoders.forEach { decoder ->
                assertTrue("unknown decoder \"$decoder\" in $row", decoder in DECODERS)
            }
        }
    }

    /**
     * The machine the page's dock names beside the author, as the phone's dock does. Asked of
     * `Platforms.forFileName` the way a file would ask it -- by extension, or by the Amiga prefix.
     */
    @Test
    fun `every row names the platform the phone gives that name`() {
        rows.forEach { row ->
            val probe = if (row.kind == "extension") "x.${row.name}" else "${row.name}.x"
            val phone = Platforms.forFileName(probe)?.name ?: "-"
            assertEquals("platform of $row", phone, row.platform)
        }
    }

    @Test
    fun `no name is listed twice as the same kind`() {
        val repeated = rows.groupBy { it.kind to it.name }.filterValues { it.size > 1 }.keys
        assertTrue("listed twice: $repeated", repeated.isEmpty())
    }

    private companion object {
        const val FILE = "web/src/formats.tsv"

        /**
         * The engine's own names for its decoders — the tokens `backendsFingerprint()` prints, plus
         * `hively`, which is in every build and so is never printed. A decoder outside this set is a
         * typo, and a typo here would make the page drop a format it can play.
         */
        val DECODERS = setOf("openmpt", "hively", "sc68", "asap", "gme", "zxtune", "sidplayfp")
    }
}
