// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/** The tunes below one Browse level that matched [query] there (`BrowseFilter.below`). */
data class TunesFound(val level: String, val query: String, val tracks: List<TrackRef>)

/** Every tune row Browse can show for this state: the level's own, then those found below it. */
fun BrowseState.onScreenIds(): List<String> = tracks.map { it.id } + below?.tracks.orEmpty().map { it.id }
