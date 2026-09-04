/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player


/**
 * Putting four sources of search results into one list.
 *
 * The scanned library, the playlists, the offline catalogue indexes and The Mod Archive's live
 * search. A tune can legitimately turn up in more than one of them, and the same tune twice is
 * three separate problems: it reads as a broken search, it wastes a row, and **it crashes the app**
 * — a `LazyColumn` keyed by track id throws `IllegalArgumentException` on a repeated key, which is
 * a hard crash on the main thread with a rendering stack trace that says nothing about search.
 *
 * That crash was real, on 2026-09-04, and the sequence is worth keeping because it is so ordinary:
 * find a Modland track by searching, add it to a playlist, search for it again. The playlist scan
 * finds it because it is now in a playlist; the catalogue search finds it because it is still in
 * Modland; both carry the same id, because for a catalogue track the id **is** its URL. The old
 * code de-duplicated the first two sources against each other and then concatenated the other two.
 */
object SearchResults {

    /**
     * One list, first occurrence of each id kept.
     *
     * **Order is the answer to "which copy wins".** The scanned library first because it knows
     * real titles and covers files nobody put in a playlist; then the playlists, for anything the
     * folders miss; then the offline indexes; then live search. A tune you already have should
     * present itself as yours rather than as a download.
     */
    fun combine(
        fromIndex: List<TrackRef>,
        fromPlaylists: List<TrackRef>,
        fromCatalogues: List<TrackRef>,
        fromLiveSearch: List<TrackRef>,
    ): List<TrackRef> {
        val seen = HashSet<String>()
        val out = ArrayList<TrackRef>(
            fromIndex.size + fromPlaylists.size + fromCatalogues.size + fromLiveSearch.size
        )
        for (source in listOf(fromIndex, fromPlaylists, fromCatalogues, fromLiveSearch)) {
            for (track in source) {
                if (seen.add(track.id)) out += track
            }
        }
        return out
    }
}
