// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistReorderParityTest {
    @Test fun `reordered tracks retain native position and traversal in shared WEB cases`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val rows = File(root, "docs/rules/playlist-reorder-cases.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("case\tcount\tat\tfrom\tto\trepeat\torder\tcurrent\tnext\tprevious", rows.first())
        assertTrue("No shared reorder cases executed", rows.size > 1)
        rows.drop(1).forEach { row ->
            val c = row.split('\t')
            val tracks = List(c[1].toInt()) { TrackRef(id = "$it", title = "$it") }
            var before = PlayQueue(tracks = tracks).withRepeat(when (c[5]) {
                "off" -> RepeatMode.OFF
                "all" -> RepeatMode.PLAYLIST
                "one" -> RepeatMode.ONE
                else -> error(c[5])
            })
            if (c[2] != "-") before = before.startAt(c[2].toInt())
            val from = c[3].toInt()
            val to = c[4].toInt()
            val after = if (from == to || from !in tracks.indices || to !in tracks.indices) before
                else before.withTracks(tracks.toMutableList().apply { add(to, removeAt(from)) })
            assertEquals(c[0], c[6], after.tracks.joinToString(",") { it.id }.ifEmpty { "-" })
            assertEquals(c[0], c[7], after.currentIndex?.toString() ?: "-")
            assertEquals(c[0], c[8], after.next().currentIndex?.toString() ?: "-")
            assertEquals(c[0], c[9], after.previous().currentIndex?.toString() ?: "-")
            assertEquals(c[0], before.current?.id, after.current?.id)
        }
    }
}
