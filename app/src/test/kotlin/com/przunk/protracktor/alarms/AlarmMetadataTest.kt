// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.data.SongDbMetadata
import com.przunk.protracktor.player.PlaybackMetadata
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AlarmMetadataTest {
    private val ref = TrackRef("/modland/Protracker/Patrick Phelan/lotus.mod", "Lotus", author = "MOD", fileName = "lotus.mod")
    private val found = SongDbMetadata.Entry("a".repeat(12), "Patrick Phelan", "Publisher", "Lotus", "1992")
    private val credits = listOf(SongDbMetadata.Credit("Jakim", "1990"), SongDbMetadata.Credit("Jester", "1991"))
    @Test fun `shared playback enrichment fills missing artist and invalid year without replacing file facts`() {
        val metadata = PlaybackMetadata.merge(mapOf("title" to "Lotus", "year" to "0"), found)
        assertEquals("Patrick Phelan", metadata["artist"])
        assertEquals("1992", metadata["year"])
        assertEquals("Real artist", PlaybackMetadata.merge(mapOf("artist" to "Real artist"), found)["artist"])
    }
    @Test fun `format names never override the actual artist and year from song metadata`() {
        val metadata = AlarmMetadata.describe(ref, "Protracker", mapOf("artist" to "MOD", "format" to "MOD"), found, credits)
        assertEquals("Patrick Phelan", metadata.author)
        assertEquals("1992", metadata.year)
        assertEquals(listOf("Jakim", "Jester"), metadata.authorAlternatives)
        assertEquals(listOf("1990", "1991"), metadata.yearAlternatives)
    }
    @Test fun `one prepared song can ask artist and year using real persisted offline alternatives`() {
        val metadata = AlarmMetadata.describe(ref, "Protracker", emptyMap(), found, credits)
        val song = metadata.applyTo(AlarmSong("song", "a".repeat(64), "Lotus", durationMillis = 3000))
        val categories = (0..80).map { AlarmQuizFactory(emptyList()).create(song, listOf(song), Random(it))!!.category }.toSet()
        assertEquals(setOf(AlarmQuestion.AUTHOR, AlarmQuestion.YEAR), categories)
        val years = (0..30).mapNotNull { AlarmQuizFactory(emptyList()).create(song, listOf(song), Random(it)) }.filter { it.category == AlarmQuestion.YEAR }
        assertTrue(years.isNotEmpty())
        years.forEach { assertEquals(setOf("1990", "1991", "1992"), it.choices.map { c -> c.value }.toSet()) }
    }
    @Test fun `year ranges and format-only credits never create misleading questions`() {
        val song = AlarmSong("song", "a".repeat(64), "Lotus", "MOD", durationMillis = 3000,
            year = "1990–1992", authorAlternatives = listOf("Jakim", "Jester"), yearAlternatives = listOf("1991", "1993"))
        assertNull(AlarmQuizFactory(emptyList()).create(song, listOf(song)))
    }
    @Test fun `refresh retains offline facts when an index was cleared and collection boundaries are frozen`() {
        val authors = mutableListOf("Jakim", "Jester")
        val song = AlarmSong("song", "a".repeat(64), "Lotus", "Patrick Phelan", durationMillis = 3000,
            year = "1992", authorAlternatives = authors, yearAlternatives = listOf("1990", "1991"))
        val book = AlarmBook(songs = listOf(song)).frozen()
        authors.clear()
        assertEquals(2, book.songs.single().authorAlternatives.size)
        val refreshed = AlarmMetadata.Description("Lotus", "", "", emptyList(), emptyList()).applyTo(book.songs.single())
        assertEquals("Patrick Phelan", refreshed.author)
        assertEquals("1992", refreshed.year)
        assertEquals(2, refreshed.authorAlternatives.size)
    }
}
