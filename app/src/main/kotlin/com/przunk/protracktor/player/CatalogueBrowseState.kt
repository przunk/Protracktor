// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class CatalogueOverview(
    val catalogues: List<CatalogueSummary>,
    val songLengthCount: Int,
    val trackMetadataCount: Int,
    val songDbLengthCount: Int,
    val favouriteCount: Int,
    val favouritesListed: Int,
    val pairedBrowser: Boolean,
    val storageBytes: Pair<Long, Long>,
    val archiveBytes: Map<String, Long>,
    val databaseBytes: Long,
    val replays: Pair<Int, Long>,
    val players: Pair<Int, Long>,
)

/** Publishes catalogue reads into the existing Browse flow without owning another selection. */
internal class CatalogueBrowseState(private val browse: MutableStateFlow<BrowseState>) {
    val catalogueId: String? get() = browse.value.openCatalogue?.id

    fun authorLocation(): Pair<String, String>? {
        val current = browse.value
        val catalogueId = current.openCatalogue?.id ?: return null
        val format = current.openFormat ?: return null
        return catalogueId to format
    }

    fun openingCatalogue(summary: CatalogueSummary) {
        browse.update {
            it.copy(
                openCatalogue = summary, openFormat = null, openAuthor = null,
                loading = true, tracks = emptyList(), arrivedByJump = false,
            )
        }
    }

    fun openingFormat(format: String) {
        browse.update {
            it.copy(
                openFormat = format, openAuthor = null, loading = true,
                tracks = emptyList(), arrivedByJump = false,
            )
        }
    }

    fun openingAuthor(author: String) {
        browse.update {
            it.copy(openAuthor = author, loading = true, tracks = emptyList(), arrivedByJump = false)
        }
    }

    fun showGroups(groups: List<CatalogueGroup>) {
        browse.update { it.copy(groups = groups, loading = false) }
    }

    fun showTracks(tracks: List<TrackRef>) {
        browse.update { it.copy(tracks = tracks, loading = false) }
    }

    fun showHeld(held: HeldSets) {
        browse.update { it.copy(held = held) }
    }

    fun showPlatforms(counts: Map<String, Int>) {
        browse.update { it.copy(platformCounts = counts) }
    }

    fun showFavourites(playable: Int, listed: Int) {
        browse.update { it.copy(favouriteCount = playable, favouritesListed = listed) }
    }

    fun showOverview(overview: CatalogueOverview, backends: () -> String) {
        browse.update { current ->
            current.copy(
                catalogues = overview.catalogues,
                songLengthCount = overview.songLengthCount,
                trackMetadataCount = overview.trackMetadataCount,
                songDbLengthCount = overview.songDbLengthCount,
                favouriteCount = overview.favouriteCount,
                favouritesListed = overview.favouritesListed,
                pairedBrowser = overview.pairedBrowser,
                storageBytes = overview.storageBytes,
                archiveBytes = overview.archiveBytes,
                databaseBytes = overview.databaseBytes,
                replayCount = overview.replays.first,
                replayBytes = overview.replays.second,
                playerCount = overview.players.first,
                playerBytes = overview.players.second,
                heldCountsKnown = true,
                backends = backends(),
            )
        }
    }
}
