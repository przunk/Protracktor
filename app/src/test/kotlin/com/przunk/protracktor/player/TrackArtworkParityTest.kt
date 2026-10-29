// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TrackArtworkParityTest {
    @Test fun `shared WEB covers preserve all native colours and UTF8 file seeds`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val text = File(root, "docs/rules/artwork-cases.tsv").readText()
        val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        assertEquals("kind\tseed\thue\targb", rows.first())
        val cases = rows.drop(1).map { it.split('\t') }
        assertEquals(Regex("(?m)^# cases: (\\d+)$").find(text)!!.groupValues[1].toInt(), cases.size)
        assertEquals((0 until 360).toSet(), cases.filter { it[0] == "hue" }.map { it[2].toInt() }.toSet())
        assertEquals(7, cases.count { it[0] == "seed" })
        for (row in cases) {
            assertEquals(4, row.size)
            val expected = row[3].toLong(16).toInt()
            when (row[0]) {
                "hue" -> assertEquals(row.joinToString(" "), expected, TrackArtwork.backgroundForHue(row[2].toInt()))
                "seed" -> {
                    val seed = row[1].takeUnless { it == "<empty>" }.orEmpty()
                    assertEquals(seed, row[2].toInt(), TrackArtwork.hueOf(seed))
                    assertEquals(seed, expected, TrackArtwork.backgroundOf(seed))
                }
                else -> error("Unknown artwork case: ${row[0]}")
            }
        }
    }
}
