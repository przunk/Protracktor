// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.Catalogue

/** Exact index keys, not normalised display labels or transient row IDs. */
data class SavedPlaceKey(val catalogueId: String, val format: String, val author: String)

data class SavedPlace(val key: SavedPlaceKey, val catalogueName: String, val savedAt: Long)

enum class SavedPlaceAvailability { AVAILABLE, INDEX_MISSING, GROUP_MISSING, CATALOGUE_UNAVAILABLE }

data class SavedPlaceRow(val place: SavedPlace, val availability: SavedPlaceAvailability)

data class SavedPlacesState(
    val rows: List<SavedPlaceRow> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val writing: Boolean = false,
    val opening: SavedPlaceKey? = null,
) {
    fun contains(key: SavedPlaceKey) = rows.any { it.place.key == key }
}

sealed interface SavedPlaceDestination {
    data class Found(val summary: CatalogueSummary, val tracks: List<TrackRef>) : SavedPlaceDestination
    data class Unavailable(val reason: SavedPlaceAvailability) : SavedPlaceDestination
}

fun BrowseState.saveablePlace(): SavedPlaceKey? {
    if (domain != BrowseDomain.ONLINE) return null
    val source = openCatalogue ?: return null
    if (Catalogue.byId(source.id)?.isOnlineOnly != false) return null
    return SavedPlaceKey(source.id, openFormat ?: return null, openAuthor ?: return null)
}
