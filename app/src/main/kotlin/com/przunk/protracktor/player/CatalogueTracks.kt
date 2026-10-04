// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.net.Catalogue

internal object CatalogueTracks {
    fun referenceOf(track: CatalogueTrack): TrackRef {
        val catalogue = Catalogue.byId(track.catalogueId)
        return TrackRef(
            // The URL is the identity. A catalogue track has no document URI and never will, and
            // the URL is what both the cache and the player key on.
            id = catalogue?.urlFor(track.path) ?: track.path,
            title = track.title,
            // The source, as a path, the same shape a local file's is: "Modland/Protracker/4-Mat".
            // A path rather than "format · author", which reads well in a row and badly everywhere
            // else -- the information panel has to say where a file came from, and a search result
            // has to answer "which one of these is it". The author keeps its own field, so nothing
            // is lost.
            subtitle = listOf(catalogue?.displayName, track.path.substringBeforeLast('/', ""))
                .filter { !it.isNullOrBlank() }
                .joinToString("/"),
            sizeBytes = track.size,
            fileName = track.title,
            // Catalogues file by author, so this is known before the file is ever opened.
            author = track.author,
        )
    }
}
