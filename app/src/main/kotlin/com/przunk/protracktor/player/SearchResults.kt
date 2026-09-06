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
     * How many rows a single source will return for one query.
     *
     * There has always been a cap -- 200 on the scanned library and 300 on the catalogues -- and
     * the trouble was that it was silent and that the two numbers were different for no reason.
     * Searching ".sap" returned 506 and ".mod" returned 308, which are 200+300+6 and 300+8: the
     * caps, not the archive. The owner read that as the search being broken, and he was right to.
     *
     * Two thousand, and the count of what was left out is now shown. The number is not a guess
     * about memory -- a `TrackRef` is small and the list is drawn lazily, so ten times this would
     * still render -- it is a guess about people: past a couple of thousand rows the answer to
     * "where is my tune" is a better query, not more scrolling. Browse by format is the tool for
     * "every SAP file", and it has no cap at all.
     */
    const val PER_SOURCE_LIMIT = 2_000

    /**
     * One list, first occurrence of each id kept.
     *
     * **Two orders, not one, and separating them is the point.**
     *
     * *Which copy wins* is unchanged: the scanned library first because it knows real titles and
     * covers files nobody put in a playlist; then the playlists; then the offline indexes; and the
     * live search last, because a tune you already have should present itself as yours rather than
     * as a download.
     *
     * *Where each lands on screen* is not the same question. The offline indexes can return
     * [PER_SOURCE_LIMIT] rows and the live search returns a page of about forty, so appending the
     * live results put them at row two thousand and one — present, correct, and unreachable. That
     * is what "The Mod Archive returns nothing" looks like from the sofa (`docs/STATUS.md` C15),
     * and it needs no network fault to happen.
     *
     * So the live results are placed with the local ones and the bulk follows, while the *identity*
     * of a duplicate is still decided by the precedence above. Forty rows cannot bury anything.
     */
    fun combine(
        fromIndex: List<TrackRef>,
        fromPlaylists: List<TrackRef>,
        fromCatalogues: List<TrackRef>,
        fromLiveSearch: List<TrackRef>,
    ): List<TrackRef> {
        val seen = HashSet<String>()
        val local = ArrayList<TrackRef>(fromIndex.size + fromPlaylists.size)
        val bulk = ArrayList<TrackRef>(fromCatalogues.size)
        val live = ArrayList<TrackRef>(fromLiveSearch.size)

        // Precedence order, so a duplicate keeps the copy the earlier source described. Where each
        // kept row is put is decided by which list it lands in, and is counted rather than
        // predicted -- a source's size is not how many of its rows survived de-duplication.
        for ((source, into) in listOf(
            fromIndex to local,
            fromPlaylists to local,
            fromCatalogues to bulk,
            fromLiveSearch to live,
        )) {
            for (track in source) {
                if (seen.add(track.id)) into += track
            }
        }
        return local + live + bulk
    }
}
