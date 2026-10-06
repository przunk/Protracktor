// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Applies deletion notices and optimistic rows to existing flows, without a separate state copy. */
internal class LibraryMaintenanceState(
    private val player: MutableStateFlow<PlayerUiState>,
    private val browse: MutableStateFlow<BrowseState>,
    private val message: (LibraryMaintenance.Notice) -> Message,
) {
    val inFavourites: Boolean get() = browse.value.randomScope is RandomScope.Favourites

    fun catalogueName(id: String): String =
        browse.value.catalogues.firstOrNull { it.id == id }?.displayName ?: id

    fun archiveBytes(id: String): Long = browse.value.archiveBytes[id] ?: 0L

    fun replayBytes(): Long {
        val current = browse.value
        return current.replayBytes + current.playerBytes
    }

    fun say(notice: LibraryMaintenance.Notice) {
        val rendered = message(notice)
        player.update { it.copy(message = rendered) }
    }

    fun cacheCleared(bytes: Long) {
        player.update { it.copy(message = message(LibraryMaintenance.Notice.Freed(bytes))) }
    }

    fun startDeleting(catalogueId: String) {
        browse.update { current ->
            current.copy(
                deleting = current.deleting + catalogueId,
                catalogues = current.catalogues.map {
                    if (it.id == catalogueId) it.copy(trackCount = 0, indexedAt = null, archiveCount = 0) else it
                },
                archiveBytes = current.archiveBytes - catalogueId,
            )
        }
    }

    fun finishDeleting(catalogueId: String) {
        browse.update { it.copy(deleting = it.deleting - catalogueId) }
    }

    fun dropCatalogueScope(catalogueId: String) {
        browse.update { current ->
            current.copy(
                // A deleted catalogue drops out of an online scope that named it. Left in, the
                // label would go on claiming a source that no longer exists.
                searchScope = (current.searchScope as? SearchScope.Online)
                    ?.let { SearchScope.Online(it.catalogueIds - catalogueId) }
                    ?: current.searchScope,
                openCatalogue = current.openCatalogue?.takeIf { it.id != catalogueId },
            )
        }
    }

    fun clearMetadata() {
        browse.update {
            it.copy(
                held = it.held?.copy(songLengths = false, trackMetadata = false, songDbLengths = false),
                songLengthCount = 0,
                trackMetadataCount = 0,
                songDbLengthCount = 0,
            )
        }
    }

    fun clearReplays() {
        browse.update {
            it.copy(
                held = it.held?.copy(replays = false, players = false),
                replayCount = 0,
                replayBytes = 0L,
                playerCount = 0,
                playerBytes = 0L,
            )
        }
    }
}
