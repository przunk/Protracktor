// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.FavouriteStore
import com.przunk.protracktor.data.SongDbLengthStore
import com.przunk.protracktor.data.SongLengthStore
import com.przunk.protracktor.data.SongLengths
import com.przunk.protracktor.data.TrackMetadataStore
import com.przunk.protracktor.engine.NativeEngine
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.RemoteFiles

/** Uses the controller's existing cache/stores; construction performs no reads or native calls. */
internal class StoreDownloadSources(
    private val remoteFiles: RemoteFiles,
    private val catalogues: CatalogueStore,
    private val songLengths: SongLengthStore,
    private val trackMetadata: TrackMetadataStore,
    private val songDbLengths: SongDbLengthStore,
    private val favourites: FavouriteStore,
) : LibraryDownloads.Sources {
    override suspend fun fetchIndex(url: String) = remoteFiles.fetchIndex(url)
    override suspend fun storeArchive(id: String, bytes: ByteArray) = remoteFiles.storeArchive(id, bytes)
    override suspend fun replaceIndex(catalogue: Catalogue, entries: List<CatalogueEntry>) =
        catalogues.replaceIndex(catalogue, entries, NativeEngine.backendsFingerprint())

    override suspend fun replaceSongLengths(entries: List<SongLengths.Entry>) {
        songLengths.replaceAll(entries)
    }
    override suspend fun replaceTrackMetadata(bytes: ByteArray) = trackMetadata.replaceAllFrom(bytes)
    override suspend fun replaceSongDbLengths(bytes: ByteArray) = songDbLengths.replaceAllFrom(bytes)
    override suspend fun replaceFavourites(bytes: ByteArray) = favourites.replaceAllFrom(bytes)
    override suspend fun playableFavourites() = favourites.playableCount()
}
