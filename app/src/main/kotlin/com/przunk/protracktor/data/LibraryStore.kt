// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
    /** Whether to play every tune inside a file rather than only the first. */
    val playAllSubsongs: Boolean = false,
    /** What Random picks from, as `RandomScope.stored()` writes it. Empty means never set. */
    val randomScope: String = "",
    /**
     * How long to play a tune whose length nothing knows, in seconds. Zero means never set, which
     * reads back as the default (`docs/STATUS.md` C56).
     */
    val fallbackLengthSeconds: Int = 0,
)

data class SavedPlaylist(
    val id: Long,
    val name: String,
    /**
     * How many tracks are in it.
     *
     * Shown wherever a playlist is chosen: a list of bare names gives no way to tell one you filled
     * from one you made and forgot (`docs/BACKLOG.md` A21).
     */
    val trackCount: Int = 0,
)

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
            .rawQuery(
                // Counted in the query rather than by reading every playlist's tracks: the picker
                // shows all of them at once, and one statement is one statement.
                "SELECT p.id, p.name, COUNT(t.track_id) " +
                    "FROM playlists p LEFT JOIN playlist_tracks t ON t.playlist_id = p.id " +
                    "GROUP BY p.id, p.name, p.position ORDER BY p.position, p.id",
                null,
            )
            .use { row ->
                buildList {
                    while (row.moveToNext()) {
                        add(SavedPlaylist(row.getLong(0), row.getString(1), row.getInt(2)))
                    }
                }
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
        // **A playlist cannot hold the same track twice, and this is the one place that can
        // promise it.** Every write of a playlist comes through here, so the guarantee is made
        // once rather than remembered at five call sites -- one of which, importing an M3U, takes
        // a text file anybody can write.
        //
        // Not a tidiness rule: a playlist row is a `LazyColumn` item keyed by track id, and a
        // repeated key throws on the main thread while drawing. Storing the repeat is no better --
        // `playlist_tracks` conflicts on the track id, the second insert replaces the first, and
        // what is left is a gap in `position` and a list shorter than the caller thinks.
        val unique = tracks.distinctBy { it.id }
        helper.writableDatabase.transaction {
            delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId.toString()))
            unique.forEachIndexed { position, track ->
                // **Never REPLACE this row** (`docs/STATUS.md` C41). SQLite's REPLACE is a DELETE
                // and an INSERT, `playlist_tracks.track_id` references it `ON DELETE CASCADE`, and
                // foreign keys are on -- so re-writing a track already in another playlist deletes
                // its place there, and copying to a playlist becomes moving. Insert if it is new,
                // then update the fields: the row itself stays.
                val fields = ContentValues().apply {
                    put("title", track.title)
                    put("subtitle", track.subtitle)
                    put("size", track.sizeBytes)
                    put("file_name", track.fileNameOrTitle)
                    put("author", track.author)
                }
                insertWithOnConflict(
                    "tracks", null,
                    ContentValues(fields).apply { put("id", track.id) },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                )
                update("tracks", fields, "id = ?", arrayOf(track.id))
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
            "SELECT active_playlist_id, current_track_id, shuffle, repeat_mode, play_all_subsongs, " +
                "random_scope, fallback_length_seconds " +
                "FROM player_state WHERE id = 0",
            null,
        ).use { row ->
            if (!row.moveToFirst()) return@withContext null
            SavedPlayerState(
                activePlaylistId = if (row.isNull(0)) 0L else row.getLong(0),
                currentTrackId = if (row.isNull(1)) null else row.getString(1),
                shuffle = row.getInt(2) != 0,
                // An unknown mode from a newer build must not crash an older one.
                repeat = runCatching { RepeatMode.valueOf(row.getString(3)) }.getOrDefault(RepeatMode.OFF),
                playAllSubsongs = row.getInt(4) != 0,
                randomScope = if (row.isNull(5)) "" else row.getString(5),
                fallbackLengthSeconds = row.getInt(6),
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
                put("play_all_subsongs", if (state.playAllSubsongs) 1 else 0)
                put("random_scope", state.randomScope)
                put("fallback_length_seconds", state.fallbackLengthSeconds)
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
