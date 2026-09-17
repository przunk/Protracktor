// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One list out of four sources, and no id twice.
 *
 * A repeated id is not a cosmetic fault: the results list is a `LazyColumn` keyed by track id, and
 * a repeated key throws on the main thread while drawing.
 */
class SearchResultsTest {

    private fun track(id: String, title: String = id) =
        TrackRef(id = id, title = title, subtitle = "", sizeBytes = 0, fileName = title)

    /**
     * The crash, as a test.
     *
     * Find a Modland tune by searching, add it to a playlist, search again. The playlist scan finds
     * it because it is in a playlist now; the catalogue index finds it because it is still in
     * Modland. For a catalogue track the id **is** its URL, so both carry the same one.
     */
    @Test
    fun `a tune in a playlist and in a catalogue appears once`() {
        val url = "https://modland.com/pub/modules/SNDH/Dubmood/Tempest_fjortisfacials.sndh"
        val combined = SearchResults.combine(
            fromIndex = emptyList(),
            fromPlaylists = listOf(track(url, "Tempest fjortisfacials")),
            fromCatalogues = listOf(track(url, "Tempest_fjortisfacials.sndh")),
            fromLiveSearch = emptyList(),
        )
        assertEquals(1, combined.size)
        assertEquals("the copy you already have wins", "Tempest fjortisfacials", combined[0].title)
    }

    /** Every pair of sources, because the old code only guarded one of the six. */
    @Test
    fun `no two sources can repeat an id`() {
        val id = "x"
        val sources = listOf(
            listOf(track(id)), listOf(track(id)), listOf(track(id)), listOf(track(id)),
        )
        for (a in 0..3) {
            for (b in 0..3) {
                if (a == b) continue
                val lists = List(4) { if (it == a || it == b) sources[it] else emptyList() }
                assertEquals(
                    "sources $a and $b",
                    1,
                    SearchResults.combine(lists[0], lists[1], lists[2], lists[3]).size,
                )
            }
        }
    }

    /** A source repeating itself is possible too — an index built twice, a live search echoing. */
    @Test
    fun `one source repeating itself is collapsed`() {
        val combined = SearchResults.combine(
            fromIndex = listOf(track("a"), track("a"), track("b")),
            fromPlaylists = emptyList(),
            fromCatalogues = emptyList(),
            fromLiveSearch = emptyList(),
        )
        assertEquals(listOf("a", "b"), combined.map { it.id })
    }

    /**
     * Display order: library, playlists, **live search**, then the offline indexes.
     *
     * Deliberately not the same order as precedence — which copy of a duplicate survives — which
     * runs offline-first and is asserted separately below. The two are the same list until a catalogue
     * page of two thousand rows makes the difference matter.
     */
    @Test
    fun `display order puts the live search ahead of the offline indexes`() {
        val combined = SearchResults.combine(
            fromIndex = listOf(track("i")),
            fromPlaylists = listOf(track("p")),
            fromCatalogues = listOf(track("c")),
            fromLiveSearch = listOf(track("l")),
        )
        assertEquals(listOf("i", "p", "l", "c"), combined.map { it.id })
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals(
            emptyList<TrackRef>(),
            SearchResults.combine(emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }

    /**
     * A live search's handful of results is not buried under the offline indexes' two thousand.
     *
     * `docs/STATUS.md` C15 was reported as "The Mod Archive returns nothing", and this is one way
     * that happens with nothing broken: forty results appended after a full catalogue page sit at
     * row two thousand and one. Present, correct, and never seen.
     */
    @Test
    fun `live results are placed where they can be seen`() {
        val bulk = (1..2_000).map { track("catalogue/$it") }
        val live = listOf(track("live/a"), track("live/b"))
        val combined = SearchResults.combine(
            fromIndex = listOf(track("local/1")),
            fromPlaylists = emptyList(),
            fromCatalogues = bulk,
            fromLiveSearch = live,
        )
        assertEquals(2_003, combined.size)
        assertEquals("local/1", combined[0].id)
        assertEquals("live/a", combined[1].id)
        assertEquals("live/b", combined[2].id)
        assertEquals("catalogue/1", combined[3].id)
    }

    /**
     * Being placed early does not make the live copy win a duplicate.
     *
     * The two orders are separate on purpose: a tune you already have should present itself as
     * yours, whichever source is listed first on screen.
     */
    @Test
    fun `precedence still prefers the local copy of a duplicate`() {
        val combined = SearchResults.combine(
            fromIndex = listOf(track("same", title = "mine")),
            fromPlaylists = emptyList(),
            fromCatalogues = emptyList(),
            fromLiveSearch = listOf(track("same", title = "theirs")),
        )
        assertEquals(1, combined.size)
        assertEquals("mine", combined[0].title)
    }

    /** De-duplication across sources must not shift the boundary the placement counts from. */
    @Test
    fun `placement counts what survived, not what was offered`() {
        val combined = SearchResults.combine(
            fromIndex = listOf(track("a"), track("b")),
            // Both already seen, so nothing from here survives and nothing shifts.
            fromPlaylists = listOf(track("a"), track("b")),
            fromCatalogues = listOf(track("c")),
            fromLiveSearch = listOf(track("d")),
        )
        assertEquals(listOf("a", "b", "d", "c"), combined.map { it.id })
    }
}
