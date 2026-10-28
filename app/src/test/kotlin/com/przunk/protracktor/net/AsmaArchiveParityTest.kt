// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.net

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same complete-archive inputs used by the WEB reader, including documentation and shallow paths. */
class AsmaArchiveParityTest {
    @Test fun `full ASMA archive matches shared WEB paths and index fields`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val lines = File(root, "docs/rules/asma-archive-cases.tsv").readLines()
        val count = lines.first { it.startsWith("# cases: ") }.substringAfter(": ").toInt()
        val data = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("path\tbody\tformat\tauthor\ttitle\tsize", data.first())
        val cases = data.drop(1).map { it.split('\t') }
        require(count > 0)
        assertEquals("A shared archive case is missing", count, cases.size)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("asma/"))
            zip.closeEntry()
            cases.forEach { row ->
                assertEquals(6, row.size)
                val bytes = row[1].toByteArray(Charsets.UTF_8)
                // The published ASMA ZIP declares sizes in local headers (no descriptor flag).
                zip.putNextEntry(ZipEntry(row[0]).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val entries = Asma.parseIndex(output.toByteArray())
        assertEquals(count, entries.size)
        cases.zip(entries).forEach { (row, entry) ->
            assertEquals(row[0], entry.path)
            assertEquals(row[2], entry.format)
            assertEquals(row[3].takeUnless { it == "-" }.orEmpty(), entry.author)
            assertEquals(row[4], entry.title)
            assertEquals(row[5].toLong(), entry.size)
            assertEquals(row[0], Asma.pathFrom(Asma.urlFor(entry.path)))
        }
    }
}
