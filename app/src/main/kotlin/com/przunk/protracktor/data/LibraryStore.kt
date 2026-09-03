/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.data

import android.content.ContentValues
import android.content.Context
import com.przunk.protracktor.player.RepeatMode
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the player needs to come back to where it was (R2). */
data class SavedPlayerState(
    val activePlaylistId: Long,
    val currentTrackId: String?,
    val shuffle: Boolean,
    val repeat: RepeatMode,
)

data class SavedPlaylist(val id: Long, val name: String)

/** A folder the user granted, kept so browsing does not start at a file picker every session. */
data class GrantedFolder(val uri: String, val displayName: String)

/**
 * Reading and writing everything that has to outlive the process.
 *
 * Every call suspends onto IO. A database call on the main thread is a dropped frame at best, and
 * this one runs while a scan of a network share is in flight.
 */
class LibraryStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    // --- playlists ----------------------------------------------------------------------------

    /**
     * The playlist everything lands in until the user makes others.
     *
     * Created on demand rather than seeded by the schema, so a fresh install and an upgraded one
     * take the same path through this code instead of two.
     */
    suspend fun defaultPlaylistId(name: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.rawQuery("SELECT id FROM playlists ORDER BY position, id LIMIT 1", null).use { row ->
            if (row.moveToFirst()) return@withContext row.getLong(0)
        }
        db.insert("playlists", null, ContentValues().apply {
            put("name", name)
            put("position", 0)
        })
    }

    suspend fun playlists(): List<SavedPlaylist> = withContext(Dispatchers.IO) {
        helper.readableDatabase
            .rawQuery("SELECT id, name FROM playlists ORDER BY position, id", null)
            .use { row ->
                buildList { while (row.moveToNext()) add(SavedPlaylist(row.getLong(0), row.getString(1))) }
            }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val nextPosition = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM playlists", null)
            .use { row -> if (row.moveToFirst()) row.getInt(0) else 0 }
        db.insert("playlists", null, ContentValues().apply {
            put("name", name)
            put("position", nextPosition)
        })
    }

    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            "playlists", ContentValues().apply { put("name", name) }, "id = ?", arrayOf(id.toString()),
        )
        Unit
    }

    /**
     * Removes a playlist and its membership rows; the tracks themselves stay.
     *
     * A track belongs to the library, not to the list that happened to mention it -- deleting a
     * playlist must not take the user's music with it.
     */
    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("playlists", "id = ?", arrayOf(id.toString()))
        Unit
    }

    // --- tracks -------------------------------------------------------------------------------

    suspend fun tracksIn(playlistId: Long): List<TrackRef> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
            SELECT t.id, t.title, t.subtitle, t.size, t.file_name, t.author
            FROM playlist_tracks pt
            JOIN tracks t ON t.id = pt.track_id
            WHERE pt.playlist_id = ?
            ORDER BY pt.position
            """.trimIndent(),
            arrayOf(playlistId.toString()),
        ).use { row ->
            buildList {
                while (row.moveToNext()) {
                    add(
                        TrackRef(
                            id = row.getString(0),
                            title = row.getString(1),
                            subtitle = row.getString(2),
                            sizeBytes = row.getLong(3),
                            fileName = row.getString(4),
                            author = row.getString(5),
                        )
                    )
                }
            }
        }
    }

    /** Every track known to the library, whichever playlist it belongs to. For searching. */
    suspend fun allTracks(): List<TrackRef> = withContext(Dispatchers.IO) {
        helper.readableDatabase
            .rawQuery("SELECT id, title, subtitle, size, file_name, author FROM tracks ORDER BY title", null)
            .use { row ->
                buildList {
                    while (row.moveToNext()) {
                        add(
                            TrackRef(
                                id = row.getString(0),
                                title = row.getString(1),
                                subtitle = row.getString(2),
                                sizeBytes = row.getLong(3),
                                fileName = row.getString(4),
                                author = row.getString(5),
                            )
                        )
                    }
                }
            }
    }

    /**
     * Makes the stored playlist match [tracks] exactly, order included.
     *
     * Replacing wholesale rather than diffing: the in-memory list is the truth the user is looking
     * at, and a diff is a second implementation of "what changed" that can disagree with the first.
     * These lists are hundreds of rows, not millions.
     */
    suspend fun replaceTracks(playlistId: Long, tracks: List<TrackRef>) = withContext(Dispatchers.IO) {
        helper.writableDatabase.transaction {
            delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId.toString()))
            tracks.forEachIndexed { position, track ->
                insertWithOnConflict(
                    "tracks", null,
                    ContentValues().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("subtitle", track.subtitle)
                        put("size", track.sizeBytes)
                        put("file_name", track.fileNameOrTitle)
                        put("author", track.author)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
                insertWithOnConflict(
                    "playlist_tracks", null,
                    ContentValues().apply {
                        put("playlist_id", playlistId)
                        put("track_id", track.id)
                        put("position", position)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    // --- player state -------------------------------------------------------------------------

    suspend fun loadPlayerState(): SavedPlayerState? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT active_playlist_id, current_track_id, shuffle, repeat_mode FROM player_state WHERE id = 0",
            null,
        ).use { row ->
            if (!row.moveToFirst()) return@withContext null
            SavedPlayerState(
                activePlaylistId = if (row.isNull(0)) 0L else row.getLong(0),
                currentTrackId = if (row.isNull(1)) null else row.getString(1),
                shuffle = row.getInt(2) != 0,
                // An unknown mode from a newer build must not crash an older one.
                repeat = runCatching { RepeatMode.valueOf(row.getString(3)) }.getOrDefault(RepeatMode.OFF),
            )
        }
    }

    suspend fun savePlayerState(state: SavedPlayerState) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            "player_state",
            ContentValues().apply {
                put("active_playlist_id", state.activePlaylistId)
                put("current_track_id", state.currentTrackId)
                put("shuffle", if (state.shuffle) 1 else 0)
                put("repeat_mode", state.repeat.name)
            },
            "id = 0", null,
        )
        Unit
    }

    // --- granted folders ----------------------------------------------------------------------

    suspend fun rememberFolder(folder: GrantedFolder) = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertWithOnConflict(
            "granted_folders", null,
            ContentValues().apply {
                put("uri", folder.uri)
                put("display_name", folder.displayName)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    suspend fun grantedFolders(): List<GrantedFolder> = withContext(Dispatchers.IO) {
        helper.readableDatabase
            .rawQuery("SELECT uri, display_name FROM granted_folders ORDER BY display_name", null)
            .use { row ->
                buildList { while (row.moveToNext()) add(GrantedFolder(row.getString(0), row.getString(1))) }
            }
    }

    suspend fun forgetFolder(uri: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("granted_folders", "uri = ?", arrayOf(uri))
        Unit
    }
}
