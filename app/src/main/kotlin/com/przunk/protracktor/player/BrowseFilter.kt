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
 *
 * **And what is below** (2026-11-09): under the rows it narrows, the tunes further down that match
 * -- in an archive or a format, every author's; in a folder, every subfolder's -- so "mod" finds both
 * a folder called MOD and the tunes with "mod" in their names, without opening each folder. Shown
 * as one more section of the same list rather than a second mode, so it costs the screen a heading.
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

    /** Where the tunes below a level are, or null where a level holds only tunes. */
    sealed interface Below {
        data class InCatalogue(val catalogueId: String, val format: String?) : Below
        data class InFolder(val at: String) : Below
    }

    fun below(state: BrowseState): Below? = when {
        !filterable(state) -> null
        state.domain == BrowseDomain.ONLINE -> state.openCatalogue
            ?.takeIf { state.openAuthor == null }
            ?.let { Below.InCatalogue(it.id, state.openFormat) }
        else -> Below.InFolder(state.folderAt)
    }

    /**
     * The tunes in [all] deeper than the folder at [at] that match [query], at most [limit], by the
     * playlist's rule on what each shows. The folder's own files are not among them: they are the
     * level itself, listed above.
     */
    fun inFoldersBelow(
        all: List<Pair<String, TrackRef>>,
        at: String,
        query: String,
        limit: Int,
        shown: (TrackRef) -> TrackRef = { it },
    ): List<TrackRef> {
        if (SearchTerms.of(query).isEmpty()) return emptyList()
        val deeper = all.filter { it.first.startsWith("$at/") }.map { it.second }
        return PlaylistSearch.indices(deeper.map(shown), query).take(limit).map(deeper::get)
    }

    /** How many tunes below one Search shows: Browse → Search's own cap. */
    const val BELOW_LIMIT = SearchResults.PER_SOURCE_LIMIT

    private fun matches(name: String, terms: List<String>): Boolean =
        SearchTerms.fold(name).let { text -> terms.all(text::contains) }
}
