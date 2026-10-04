// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.SearchTerms
import com.przunk.protracktor.net.ModArchive

/** Searches the four existing sources; the caller owns loading state and message presentation. */
internal class LibrarySearch(private val sources: Sources) {
    interface Sources {
        suspend fun searchLocal(query: String, limit: Int, platforms: Set<String>): List<TrackRef>
        suspend fun playlistTracks(): List<TrackRef>
        suspend fun searchCatalogues(query: String, ids: Set<String>, limit: Int, platforms: Set<String>): List<TrackRef>
        suspend fun searchLive(query: String): ModArchive.Outcome
        suspend fun countLocal(query: String): Int
        suspend fun countCatalogues(query: String, ids: Set<String>, platforms: Set<String>): Int
    }

    data class Request(val query: String, val scope: SearchScope, val indexedCatalogues: Set<String>)
    data class Result(val tracks: List<TrackRef>, val matches: Int, val liveSearchNeededQuery: Boolean)

    suspend fun run(request: Request, onLiveResult: (ModArchive.Outcome) -> Unit): Result {
        val searching = request.scope
        val platformIds = (searching as? SearchScope.ByPlatform)?.platformIds.orEmpty()

        // The scanned library first: it knows tunes by their real titles, and it knows files
        // nobody has added to any playlist. Then the playlists, for anything indexed folders do
        // not cover -- an individually picked file, or a folder whose grant is gone.
        val fromIndex = if (searching.searchesLocal) {
            sources.searchLocal(request.query, SearchResults.PER_SOURCE_LIMIT, platformIds)
        } else {
            emptyList()
        }

        val fromLocal = if (searching.searchesLocal) {
            // The library, meaning every playlist's tracks -- searching only the active one
            // would answer a question nobody asked.
            // Capped like the other two. It never was, because a typed query is its own limit
            // -- but an empty query matches every track in every playlist, and this is the one
            // source that would have handed back all of them.
            sources.playlistTracks().asSequence().filter {
                // The same rule the two SQL searches use (`SearchTerms`), so a query that finds
                // a tune in the index does not miss the copy of it sitting in a playlist.
                SearchTerms.matchesAny(request.query, it.title, it.fileName) &&
                    (platformIds.isEmpty() || Platforms.matches(it.fileName, platformIds))
            }.take(SearchResults.PER_SOURCE_LIMIT).toList()
        } else {
            emptyList()
        }

        // Explicit. "No catalogue ticked means all of them" was the earlier rule and it made
        // the filter look broken: unticking Modland searched Modland anyway. It is safe again
        // here for the one reason it was not then -- the label states the scope out loud, so
        // "Online" plainly covers everything and "Online: Modland" plainly does not.
        val chosen = (searching as? SearchScope.Online)?.catalogueIds.orEmpty()
        val indexed = request.indexedCatalogues
        val wanted = (if (chosen.isEmpty()) indexed else chosen)
        val dbCatalogues = wanted.filter { it != ModArchive.id }.toSet()
        val fromOnline = if (searching.searchesOnline && dbCatalogues.isNotEmpty()) {
            sources.searchCatalogues(
                request.query, dbCatalogues, SearchResults.PER_SOURCE_LIMIT, platformIds,
            )
        } else {
            emptyList()
        }

        // The Mod Archive is searched live and has no index to narrow, so a platform scope
        // cannot reach it at the query -- its results are filtered here instead of being
        // dropped, which would have made "Amiga" quietly mean "Amiga except The Mod Archive".
        // The Mod Archive is the one source an empty query cannot ask: it is a live search
        // against somebody else's server, with no index here to list. Skipped rather than sent
        // an empty query it would answer badly or refuse.
        val liveInScope = searching.searchesOnline && ModArchive.id in wanted
        val live = if (liveInScope && request.query.isNotBlank()) {
            sources.searchLive(request.query)
        } else {
            ModArchive.Outcome.Found(emptyList())
        }
        // Said out loud, because the difference between "the archive does not have it", "we
        // could not ask" and "it answered with something we cannot read" is the difference
        // between a fact and two different faults. C15 was reported as the first and the app
        // had no way of telling anyone it might be one of the others.
        onLiveResult(live)
        val fromModArchive = (live as? ModArchive.Outcome.Found)?.tracks.orEmpty().filter {
            platformIds.isEmpty() || Platforms.matches(it.fileName, platformIds)
        }

        // De-duplicated across **all four**, not just the first two. The old code guarded
        // index-against-playlists and then concatenated the catalogues, which crashed the app
        // the moment somebody added an online track to a playlist and searched for it again --
        // see `SearchResults`.
        val results = SearchResults.combine(
            fromIndex = fromIndex,
            fromPlaylists = fromLocal,
            fromCatalogues = fromOnline,
            fromLiveSearch = fromModArchive,
        )

        // Asked only when a cap was actually reached. A COUNT over a couple of hundred thousand
        // catalogue rows is cheap but not free, and a search that fits does not need it.
        val capped = fromIndex.size == SearchResults.PER_SOURCE_LIMIT ||
            fromOnline.size == SearchResults.PER_SOURCE_LIMIT
        val matches = if (!capped) 0 else {
            (if (searching.searchesLocal) sources.countLocal(request.query) else 0) +
                (if (searching.searchesOnline && dbCatalogues.isNotEmpty()) {
                    sources.countCatalogues(request.query, dbCatalogues, platformIds)
                } else {
                    0
                })
        }

        return Result(
            tracks = results,
            matches = matches,
            // Only worth saying when it is the whole story: with Modland also in scope, the
            // live source contributing nothing to a blank search is unremarkable.
            liveSearchNeededQuery = liveInScope && request.query.isBlank() &&
                dbCatalogues.isEmpty() && !searching.searchesLocal,
        )
    }
}
