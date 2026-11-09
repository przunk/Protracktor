// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.SearchTerms

/**
 * Browse's Search: the list on screen narrowed to what matches, the playlist's rule applied to
 * whatever the level holds -- an archive's formats, a format's authors, an author's tunes, a
 * folder's folders and files (2026-11-09). It narrows only what is already listed; Browse → Search
 * is still where a whole catalogue is searched.
 *
 * Tunes go through [PlaylistSearch] itself, so the two searches cannot come to mean different
 * things; a name (a format, an author, a folder) is matched by the same words-anywhere rule.
 */
object BrowseFilter {

    /** Whether this level holds a list Search can narrow. */
    fun filterable(state: BrowseState): Boolean = when (state.domain) {
        BrowseDomain.ONLINE -> state.openCatalogue != null
        BrowseDomain.LOCAL -> state.openFolder != null
        else -> false
    }

    fun groups(groups: List<CatalogueGroup>, query: String): List<CatalogueGroup> =
        SearchTerms.of(query).takeIf { it.isNotEmpty() }
            ?.let { terms -> groups.filter { matches(it.name, terms) } } ?: groups

    fun folders(folders: List<LocalTree.Folder>, query: String): List<LocalTree.Folder> =
        SearchTerms.of(query).takeIf { it.isNotEmpty() }
            ?.let { terms -> folders.filter { matches(it.name, terms) } } ?: folders

    /** Indices into [tracks], so playing a match still plays from the whole level. */
    fun tracks(tracks: List<TrackRef>, query: String): List<Int> = PlaylistSearch.indices(tracks, query)

    private fun matches(name: String, terms: List<String>): Boolean =
        SearchTerms.fold(name).let { text -> terms.all(text::contains) }
}
