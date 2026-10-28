// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.data

import com.przunk.protracktor.player.TrackRef
import java.io.File
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFileParityTest {
    private fun cases(file: String): List<List<String>> {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        return File(root, "docs/rules/$file.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
            .map { line -> line.split('\t').map { URLDecoder.decode(it, "UTF-8") } }
            .also { assertTrue("No shared playlist cases executed", it.isNotEmpty()) }
    }
    @Test fun `native parser agrees with shared WEB file cases`() {
        cases("playlist-file-read-cases").forEach { c ->
            assertEquals(c[0], c[2], PlaylistFile.nameIn(c[1]) ?: "-")
            val entries = PlaylistFile.read(c[1]).joinToString("\n") {
                listOf(it.id ?: "-", it.title, it.author, it.sizeBytes.toString(), it.location, it.fileName).joinToString("\t")
            }
            assertEquals(c[0], c[3], entries.ifEmpty { "-" })
        }
    }
    @Test fun `native writer agrees byte for byte with shared WEB file cases`() {
        cases("playlist-file-write-cases").forEach { c ->
            val track = TrackRef(id = c[2], title = c[3], subtitle = c[4], fileName = c[5], author = c[6], sizeBytes = c[7].toLong())
            assertEquals(c[0], c[8], PlaylistFile.write(c[1], listOf(track)))
        }
    }
}
