// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The stored Modland favourites, and the counts that tell the UI whether they exist.
 *
 * Parsing lives in [ModlandFavourites]; this is where the result is kept — the same split as
 * [SongDbMetadata] and [TrackMetadataStore]. Picking *from* the list is not here either: that is
 * [CatalogueStore.randomSample], because a favourite only becomes playable by meeting the index,
 * and the index is that store's table.
 */
class FavouriteStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    /** How many favourites are listed. Zero means the list has not been downloaded. */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM modland_favourites", null).use { row ->
            if (row.moveToFirst()) row.getInt(0) else 0
        }
    }

    /**
     * How many of them this device could actually play — listed **and** in the Modland index.
     *
     * Shown rather than the raw count, because the raw count is a promise the app cannot keep. A
     * hundred of the paths no longer exist in Modland and another fifty-odd are formats no backend
     * here claims; saying "991" beside a dice that draws from 835 is the kind of small lie that
     * costs an evening when somebody finally checks. It also reads as zero when the catalogue has
     * not been indexed, which is exactly the state the chip must be disabled in.
     */
    suspend fun playableCount(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM modland_favourites f " +
                "JOIN catalogue_tracks t ON t.path = f.path AND t.catalogue_id = ?",
            arrayOf(MODLAND_ID),
        ).use { row -> if (row.moveToFirst()) row.getInt(0) else 0 }
    }

    /** Throws the list away. Re-downloadable from the button that fetched it. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("modland_favourites", null, null)
        Unit
    }

    /**
     * Replaces the whole list from the downloaded bytes, and returns how many paths it wrote.
     *
     * Wholesale rather than merged, like the metadata table: the list is somebody's editorial
     * judgement and it changes in both directions, so a tune dropped upstream should drop here.
     */
    suspend fun replaceAllFrom(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        var written = 0
        helper.writableDatabase.transaction {
            delete("modland_favourites", null, null)
            compileStatement("INSERT OR REPLACE INTO modland_favourites (path) VALUES (?)")
                .use { statement ->
                    bytes.inputStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
                        ModlandFavourites.parse(lines).forEach { path ->
                            statement.clearBindings()
                            statement.bindString(1, path)
                            statement.executeInsert()
                            written++
                        }
                    }
                }
        }
        written
    }

    companion object {
        /**
         * Which catalogue the paths belong to.
         *
         * A constant rather than [com.przunk.protracktor.net.Modland]`.id` so this file stays free
         * of the net package, and named here rather than passed in because the list is Modland's:
         * the favourites of an archive are not a general idea this store could hold for any of
         * them.
         */
        const val MODLAND_ID = "modland"
    }
}
