// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StereoMixTest {
    @Test fun `modes use the agreed native widths and stable preference names`() {
        assertEquals(listOf("original", "narrow", "mono"), StereoMix.entries.map { it.stored })
        assertEquals(listOf(100, 50, 0), StereoMix.entries.map { it.widthPercent })
    }

    @Test fun `every stored mode round trips`() {
        StereoMix.entries.forEach { assertEquals(it, StereoMix.fromStored(it.stored)) }
    }

    @Test fun `absent or unknown settings leave playback in Original`() {
        listOf(null, "", "headphones", "future-mode").forEach {
            assertEquals(StereoMix.ORIGINAL, StereoMix.fromStored(it))
        }
        assertEquals(StereoMix.ORIGINAL, PlayerUiState().stereoMix)
    }

    @Test fun `shared stored mode cases match WEB without changing native defaults`() {
        val root = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .first { java.io.File(it, "settings.gradle.kts").isFile }
        val lines = java.io.File(root, "docs/rules/stereo-mix-cases.tsv").readLines()
        val count = lines.first { it.startsWith("# cases: ") }.substringAfter(": ").toInt()
        val rows = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        require(count > 0)
        assertEquals("case\tstored\tmode\twidth", rows.first())
        assertEquals("A shared stereo case is missing", count, rows.size - 1)
        rows.drop(1).forEach { line ->
            val fields = line.split('\t')
            assertEquals(4, fields.size)
            val raw = when (fields[1]) { "-" -> null; "<empty>" -> ""; else -> fields[1] }
            val mode = StereoMix.fromStored(raw)
            assertEquals(fields[0], fields[2], mode.stored)
            assertEquals(fields[0], fields[3].toInt(), mode.widthPercent)
        }
    }
}
