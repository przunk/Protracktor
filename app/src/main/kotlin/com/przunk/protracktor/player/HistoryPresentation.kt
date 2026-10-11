// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/** History supplies presentation only; catalogue ordering and playback references stay untouched. */
data class HistoryPresentation(val title: String, val listened: Boolean)

internal object LearnedTitles {
    fun display(track: TrackRef, titles: Map<String, String>): TrackRef {
        val title = titles[track.id]?.trim()?.takeIf { it.isNotEmpty() } ?: return track
        return track.copy(title = title, fileName = track.fileNameOrTitle)
    }

    fun sourceLine(track: TrackRef): String {
        val file = track.fileNameOrTitle
        if (track.title == file) return track.subtitle
        val path = track.subtitle.trimEnd('/')
        return when {
            path.isBlank() -> file
            path == file || path.endsWith("/$file") -> path
            else -> "$path/$file"
        }
    }
}
