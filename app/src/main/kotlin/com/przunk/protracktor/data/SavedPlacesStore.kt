// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import com.przunk.protracktor.player.SavedPlace
import com.przunk.protracktor.player.SavedPlaceKey
import com.przunk.protracktor.player.SavedPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SavedPlacesStore(context: Context) : SavedPlaces.Store {
    private val helper = ProtracktorDatabase.of(context)

    override suspend fun save(place: SavedPlace): Boolean = withContext(Dispatchers.IO) {
        helper.writableDatabase.compileStatement(SchemaSql.SAVED_PLACE_INSERT).use { statement ->
            statement.bindString(1, place.key.catalogueId)
            statement.bindString(2, place.key.format)
            statement.bindString(3, place.key.author)
            statement.bindString(4, place.catalogueName)
            statement.bindLong(5, place.savedAt)
            statement.executeInsert() != -1L
        }
    }

    override suspend fun list(): List<SavedPlace> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(SchemaSql.SAVED_PLACES_LIST, null).use { rows ->
            buildList {
                while (rows.moveToNext()) add(SavedPlace(
                    SavedPlaceKey(rows.getString(0), rows.getString(1), rows.getString(2)),
                    rows.getString(3), rows.getLong(4),
                ))
            }
        }
    }

    override suspend fun remove(key: SavedPlaceKey) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("saved_places", "catalogue_id = ? AND format = ? AND author = ?",
            arrayOf(key.catalogueId, key.format, key.author))
        Unit
    }
}
