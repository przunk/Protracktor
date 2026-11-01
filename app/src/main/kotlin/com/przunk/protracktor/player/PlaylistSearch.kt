// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.SearchTerms

/** A projection of the editor, never a replacement playback order. Indices address the full list. */
object PlaylistSearch {
    fun indices(tracks: List<TrackRef>, query: String): List<Int> {
        val terms = SearchTerms.of(query)
        if (terms.isEmpty()) return tracks.indices.toList()
        return tracks.indices.filter { index ->
            val track = tracks[index]
            val text = SearchTerms.fold(listOf(track.title, track.fileNameOrTitle,
                track.displayAuthor, track.subtitle).joinToString("\n"))
            terms.all(text::contains)
        }
    }
}
