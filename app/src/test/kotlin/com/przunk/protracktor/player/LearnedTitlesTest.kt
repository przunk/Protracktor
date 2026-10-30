// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class LearnedTitlesTest {
    @Test fun `display changes names but never catalogue order identity or duplicate random occurrences`() {
        val a = TrackRef("a", "a.mod", "Modland / Author", 42, author = "Author")
        val z = TrackRef("z", "z.mod", "Modland / Author", 80)
        val rows = listOf(a, z, a)
        val shown = rows.map { LearnedTitles.display(it, mapOf("a" to "Zulu", "z" to "Alpha")) }
        assertEquals(listOf("a", "z", "a"), shown.map { it.id })
        assertEquals(listOf("Zulu", "Alpha", "Zulu"), shown.map { it.title })
        assertEquals(listOf("a.mod", "z.mod", "a.mod"), shown.map { it.fileName })
        assertEquals("Modland / Author/a.mod", LearnedTitles.sourceLine(shown.first()))
        assertEquals(rows.map { it.displayAuthor }, shown.map { it.displayAuthor })
        assertEquals(rows.map { it.subtitle }, shown.map { it.subtitle })
        assertEquals(42L, shown.first().sizeBytes)
        assertEquals("Author", shown.first().author)
        assertEquals("a.mod", rows.first().title)
        assertEquals("Modland / Author", rows.first().subtitle)
    }

    @Test fun `missing or blank learned titles keep the original reference`() {
        val track = TrackRef("a", "a.mod", "Source")
        assertSame(track, LearnedTitles.display(track, emptyMap()))
        assertSame(track, LearnedTitles.display(track, mapOf("a" to "  ")))
        assertSame(track, LearnedTitles.display(track, mapOf("another" to "Title")))
    }

    @Test fun `source label includes filename once and never rewrites file identity`() {
        val track = TrackRef("id:escaped%2Fpart", "file.mod", "Source/file.mod", fileName = "file.mod")
        val shown = LearnedTitles.display(track, mapOf(track.id to "  Decoded  "))
        assertEquals("Decoded", shown.title)
        assertEquals(track.subtitle, shown.subtitle)
        assertEquals(track.id, shown.id)
        assertEquals(shown, LearnedTitles.display(shown, mapOf(track.id to "Decoded")))
        assertEquals("file.mod", LearnedTitles.sourceLine(
            LearnedTitles.display(track.copy(subtitle = ""), mapOf(track.id to "Title"))))
    }

    @Test fun `separator controls are blank but BOM stays in learned presentation`() {
        val track = TrackRef("a", "a.mod", "Source")
        assertSame(track, LearnedTitles.display(track, mapOf("a" to "\u001c\u001f")))
        for ((raw, expected) in listOf("\u001cTitle\u001f" to "Title",
            "\ufeffTitle\ufeff" to "\ufeffTitle\ufeff", "\ufeff" to "\ufeff")) {
            assertEquals(expected, LearnedTitles.display(track, mapOf("a" to raw)).title)
        }
        val titled = track.copy(title = "Title", fileName = "a.mod")
        assertEquals("a.mod", LearnedTitles.sourceLine(titled.copy(subtitle = "\u001c\u001f")))
        assertEquals("\ufeff/a.mod", LearnedTitles.sourceLine(titled.copy(subtitle = "\ufeff")))
    }
}
