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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One list out of four sources, and no id twice.
 *
 * A repeated id is not a cosmetic fault: the results list is a `LazyColumn` keyed by track id, and
 * a repeated key throws on the main thread while drawing. The owner met it on 2026-09-04.
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

    @Test
    fun `order runs library, playlists, catalogues, live search`() {
        val combined = SearchResults.combine(
            fromIndex = listOf(track("i")),
            fromPlaylists = listOf(track("p")),
            fromCatalogues = listOf(track("c")),
            fromLiveSearch = listOf(track("l")),
        )
        assertEquals(listOf("i", "p", "c", "l"), combined.map { it.id })
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals(
            emptyList<TrackRef>(),
            SearchResults.combine(emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }
}
