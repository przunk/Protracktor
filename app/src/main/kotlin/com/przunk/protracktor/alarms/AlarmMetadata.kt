// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.data.SongDbMetadata
import com.przunk.protracktor.player.PlaybackMetadata
import com.przunk.protracktor.player.ReleaseYear
import com.przunk.protracktor.player.TrackRef

/** Resolve facts while the original file/index is available, never at scheduled wake-up. */
internal object AlarmMetadata {
    data class Description(val title: String, val author: String, val year: String,
        val authorAlternatives: List<String>, val yearAlternatives: List<String>) {
        fun applyTo(song: AlarmSong) = song.copy(title = title, author = author.ifBlank { song.author }, year = year.ifBlank { song.year },
            authorAlternatives = (authorAlternatives + song.authorAlternatives).distinctBy(AlarmQuizFactory::fold).take(16),
            yearAlternatives = (yearAlternatives + song.yearAlternatives).distinct().take(16))
    }
    fun describe(ref: TrackRef, format: String, described: Map<String, String>,
        found: SongDbMetadata.Entry?, credits: List<SongDbMetadata.Credit>): Description {
        val metadata = PlaybackMetadata.merge(described, found)
        val formats = setOf(format, metadata["format"].orEmpty(), ref.fileNameOrTitle.substringAfterLast('.', ""), "Modland", "Local", "Files")
            .map(AlarmQuizFactory::fold).filter(String::isNotBlank).toSet()
        fun author(value: String) = value.trim().takeIf {
            AlarmQuizFactory.authorKnown(it) && AlarmQuizFactory.fold(it) !in formats
        }
        val actualAuthor = listOf(metadata["artist"].orEmpty(), metadata["composer"].orEmpty(), found?.author.orEmpty(), ref.author)
            .firstNotNullOfOrNull(::author).orEmpty().take(1024)
        val year = ReleaseYear.from(metadata)
        val authors = credits.map { it.author.trim() }.filter { it.length <= 160 && author(it) != null }
            .filter { AlarmQuizFactory.fold(it) != AlarmQuizFactory.fold(actualAuthor) }
            .distinctBy(AlarmQuizFactory::fold).take(16)
        val years = credits.map { ReleaseYear.of(it.year) }.filter { ReleaseYear.isSingle(it) && it != year }
            .distinct().take(16)
        return Description(metadata["title"].orEmpty().ifBlank { ref.title.ifBlank { ref.fileNameOrTitle } }.take(1024),
            actualAuthor, year, authors, years)
    }
}
