// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.net.Catalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads catalogue views in the existing scope; navigation and playback remain caller-owned. */
internal class CatalogueBrowser(
    private val scope: CoroutineScope,
    private val indexes: Indexes,
    private val files: Files,
    private val state: CatalogueBrowseState,
    private val backends: () -> String,
) {
    interface Indexes {
        suspend fun hasSongLengths(): Boolean
        suspend fun hasTrackMetadata(): Boolean
        suspend fun hasSongDbLengths(): Boolean
        suspend fun summaries(): List<CatalogueSummary>
        suspend fun songLengthCount(): Int
        suspend fun trackMetadataCount(): Int
        suspend fun songDbLengthCount(): Int
        suspend fun playableFavourites(): Int
        suspend fun listedFavourites(): Int
        suspend fun platformCounts(): Map<String, Int>
        suspend fun formats(catalogueId: String): List<CatalogueGroup>
        suspend fun authors(catalogueId: String, format: String): List<CatalogueGroup>
        suspend fun tracks(catalogueId: String, format: String, author: String): List<CatalogueTrack>
    }

    interface Files {
        fun replayCount(): Int
        fun replayBytes(): Long
        fun playersPresent(): Boolean
        fun playerCount(): Int
        fun playerBytes(): Long
        fun pairedBrowser(): Boolean
        fun cacheBytes(): Long
        fun permanentBytes(): Long
        fun archiveBytes(catalogueId: String): Long
        fun databaseBytes(): Long
    }

    /**
     * Also drops the platform counts, so the next screen that needs them recounts.
     *
     * Every path that changes what is indexed ends here — indexing a catalogue, deleting one, and
     * the storage screen's clearing. The counts are derived from exactly that, so surviving one of
     * these would leave a chip disabled after the index that would have lit it, for the rest of the
     * session.
     */
    fun refreshCatalogues() {
        scope.launch {
            // **What is here, before how much** (C75). Five yes-or-no questions that stop at the
            // first row or the first file, published at once; the counts below walk hundreds of
            // thousands of rows and queue behind the platform counts, and the ticks waited seconds
            // for them.
            val held = HeldSets(
                songLengths = indexes.hasSongLengths(),
                trackMetadata = indexes.hasTrackMetadata(),
                songDbLengths = indexes.hasSongDbLengths(),
                replays = withContext(Dispatchers.IO) { files.replayCount() > 0 },
                players = withContext(Dispatchers.IO) { files.playersPresent() },
            )
            state.showHeld(held)

            // Recounted rather than kept: this runs whenever what is indexed has changed, and the
            // platform counts are derived from exactly that.
            refreshPlatformCounts()
            val summaries = indexes.summaries()
            val lengths = indexes.songLengthCount()
            val metadataRows = indexes.trackMetadataCount()
            val lengthRows = indexes.songDbLengthCount()
            // Counted here, with the index, and not where it is downloaded: it is a fact about the
            // list *and* the Modland index together, so indexing Modland changes it as surely as
            // downloading the list does.
            val favouriteRows = indexes.playableFavourites()
            val favouriteRowsListed = indexes.listedFavourites()
            val paired = files.pairedBrowser()
            val storage = withContext(Dispatchers.IO) {
                files.cacheBytes() to files.permanentBytes()
            }
            val archives = withContext(Dispatchers.IO) {
                Catalogue.all.associate { it.id to files.archiveBytes(it.id) }
                    .filterValues { it > 0 }
            }
            // Measured as a file, because that is the only honest measurement available. Only an
            // archive catalogue is a file of its own; Modland's index is rows in this database,
            // sharing its pages, its indexes and its free list with the playlists and the history.
            // Splitting that number between catalogues would be a guess presented as a size.
            val database = withContext(Dispatchers.IO) {
                files.databaseBytes()
            }
            val replays = withContext(Dispatchers.IO) {
                files.replayCount() to files.replayBytes()
            }
            val players = withContext(Dispatchers.IO) {
                files.playerCount() to files.playerBytes()
            }
            state.showOverview(
                CatalogueOverview(
                    summaries, lengths, metadataRows, lengthRows, favouriteRows, favouriteRowsListed,
                    paired, storage, archives, database, replays, players,
                ),
                backends,
            )
        }
    }

    /** Read on opening the sheet; navigation snapshots must not decide whether filters exist. */
    suspend fun randomFilterAvailability() = RandomFilterAvailability(
        platformCounts = indexes.platformCounts(),
        favouriteCount = indexes.playableFavourites(),
        favouritesListed = indexes.listedFavourites(),
    )

    /** Counts the platform chips from the catalogue index, which is what makes a dead chip honest. */
    fun refreshPlatformCounts() {
        scope.launch {
            // Counted by the column each row carries (C89), so ASMA counts towards Atari 8-bit.
            val byPlatform = indexes.platformCounts()
            state.showPlatforms(byPlatform)
        }
    }

    /** Recounts the favourites. Cheap: two `COUNT`s over about a thousand rows. */
    fun refreshFavouriteCount() {
        scope.launch {
            val playable = indexes.playableFavourites()
            val listed = indexes.listedFavourites()
            state.showFavourites(playable, listed)
        }
    }

    fun openCatalogue(summary: CatalogueSummary) {
        scope.launch {
            state.openingCatalogue(summary)
            val formats = indexes.formats(summary.id)
            state.showGroups(formats)
        }
    }

    fun openFormat(format: String) {
        val catalogueId = state.catalogueId ?: return
        scope.launch {
            state.openingFormat(format)
            val authors = indexes.authors(catalogueId, format)
            state.showGroups(authors)
        }
    }

    fun openAuthor(author: String) {
        val (catalogueId, format) = state.authorLocation() ?: return
        scope.launch {
            state.openingAuthor(author)
            val found = indexes.tracks(catalogueId, format, author).map(CatalogueTracks::referenceOf)
            state.showTracks(found)
        }
    }
}
