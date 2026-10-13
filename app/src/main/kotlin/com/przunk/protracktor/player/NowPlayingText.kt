// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/** One subtitle policy for the dock and platform playback surfaces; callers supply localised statuses. */
internal object NowPlayingText {
    fun subtitle(state: PlayerUiState, loading: String, idle: String): String {
        if (state.loadingTrack) return loading
        val track = state.current ?: return idle
        val machine = Platforms.forFileName(track.fileName)?.name
        val author = track.displayAuthor
        val lead = author.ifBlank { machine ?: state.metadata["format"].orEmpty() }
        // Without an author the platform replaces the format rather than repeating it.
        return listOfNotNull(
            lead,
            machine.takeIf { author.isNotBlank() },
            state.releaseYear.takeIf { it.isNotBlank() },
        ).filter(String::isNotBlank).joinToString(" \u00b7 ")
    }
}
