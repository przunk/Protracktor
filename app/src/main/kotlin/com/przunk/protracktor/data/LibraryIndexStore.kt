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

import android.content.Context
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One file, as a scan found it: what it is, not what it is called. */
data class IndexedFile(
    val uri: String,
    val folderUri: String,
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val backend: String,
    val format: String,
    val title: String,
    val author: String,
    val durationMs: Long,
    val subsongs: Int,
)

/**
 * The scanned local library.
 *
 * A scan opens every file with a real decoder to find out what it is, which is the only way to stop
 * trusting filenames (`docs/BACKLOG.md` A6) and far too expensive to repeat. This is where the
 * answer lives so that re-entering a folder, or launching the app again, reads instead of
 * re-probing.
 */
class LibraryIndexStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    /**
     * Replaces one folder's contents wholesale.
     *
     * Per folder rather than globally, because a scan is per folder: a user rescanning one tree
     * must not lose the index of another. One transaction, for the same reason the catalogue index
     * uses one -- a half-written index is worse than none, and it would be indistinguishable from a
     * folder that genuinely holds less.
     */
    suspend fun replaceFolder(
        folderUri: String,
        entries: List<IndexedFile>,
        backends: String,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.transaction {
            delete("library_index", "folder_uri = ?", arrayOf(folderUri))
            compileStatement(
                "INSERT OR REPLACE INTO library_index " +
                    "(uri, folder_uri, path, file_name, size, backend, format, title, author, " +
                    " duration_ms, subsongs, indexed_at, backends) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                entries.forEach { entry ->
                    statement.clearBindings()
                    statement.bindString(1, entry.uri)
                    statement.bindString(2, entry.folderUri)
                    statement.bindString(3, entry.path)
                    statement.bindString(4, entry.fileName)
                    statement.bindLong(5, entry.sizeBytes)
                    statement.bindString(6, entry.backend)
                    statement.bindString(7, entry.format)
                    statement.bindString(8, entry.title)
                    statement.bindString(9, entry.author)
                    statement.bindLong(10, entry.durationMs)
                    statement.bindLong(11, entry.subsongs.toLong())
                    statement.bindLong(12, now)
                    statement.bindString(13, backends)
                    statement.executeInsert()
                }
            }
        }
    }

    /** What a folder holds, in the form the playlist and browser take. */
    suspend fun tracksIn(folderUri: String): List<TrackRef> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT uri, path, file_name, size, title, author, subsongs FROM library_index " +
                "WHERE folder_uri = ? ORDER BY path, file_name",
            arrayOf(folderUri),
        ).use { row ->
            buildList {
                while (row.moveToNext()) {
                    val fileName = row.getString(2)
                    add(
                        TrackRef(
                            id = row.getString(0),
                            // The tune's own name where the scan found one; the filename otherwise.
                            // A scan opens the file, so unlike the old extension-based one it
                            // usually knows the real title before anything has been played.
                            title = row.getString(4).ifBlank { fileName },
                            subtitle = row.getString(1),
                            sizeBytes = row.getLong(3),
                            fileName = fileName,
                            author = row.getString(5),
                            subsongs = row.getInt(6),
                        )
                    )
                }
            }
        }
    }

    /** How many files a folder has indexed, and when it was last scanned. Null if never. */
    suspend fun summaryOf(folderUri: String): Pair<Int, Long>? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*), MAX(indexed_at) FROM library_index WHERE folder_uri = ?",
            arrayOf(folderUri),
        ).use { row ->
            if (row.moveToFirst() && row.getInt(0) > 0) row.getInt(0) to row.getLong(1) else null
        }
    }

    /**
     * Whether a folder's index was produced by a different set of decoders than this build has.
     *
     * The reason this exists: an index records what a file *is* and whether anything can play it,
     * and both are verdicts of the decoders that produced them. Replacing sc68 2.2.1 with 3.0.0b
     * took `.sndh` from 14 of 30 to 30 of 30 -- so every file the old set rejected deserves asking
     * again. `docs/BACKLOG.md` A7 has the same problem for catalogue indexes and leaves it to a
     * human to remember.
     */
    suspend fun isStale(folderUri: String, backends: String): Boolean = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM library_index WHERE folder_uri = ? AND backends <> ? LIMIT 1",
            arrayOf(folderUri, backends),
        ).use { it.moveToFirst() }
    }

    /** Everything indexed, for searching across folders. */
    /**
     * How many rows [search] would return without its limit.
     *
     * The same `WHERE` as [search], deliberately duplicated rather than shared through a helper:
     * the two are three lines apart and a mismatch is visible here, where a builder that generated
     * both would hide one. What must never drift is the predicate, and that is what a reader can
     * check by looking down.
     */
    /**
     * How far past the wanted count the query reaches when a platform filter is on.
     *
     * A filter that keeps one row in ten would otherwise return a tenth of a page. Five is a guess
     * and is written down as one; it is bounded work either way, because the local index is a user's
     * own folders. If it is ever the wrong shape the fix is a `platform` column at scan time, which
     * is a schema migration and a re-index and is not worth it for this.
     */
    private val PLATFORM_SCAN_FACTOR = 5

    suspend fun countMatches(query: String): Int = withContext(Dispatchers.IO) {
        val like = "%${query.trim()}%"
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM library_index WHERE title LIKE ? OR file_name LIKE ? OR author LIKE ?",
            arrayOf(like, like, like),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * @param platforms when non-empty, only files belonging to one of these platform ids.
     *
     * Applied in Kotlin as the cursor is walked, not in SQL. A local index has no `format` column,
     * so the alternative is sixty `file_name LIKE` patterns for Amiga alone — over a table that is
     * one user's folders rather than half a million catalogue rows. Filtering here also means the
     * limit counts what survives the filter instead of what preceded it.
     */
    suspend fun search(
        query: String,
        limit: Int,
        platforms: Set<String> = emptySet(),
    ): List<TrackRef> = withContext(Dispatchers.IO) {
        val like = "%${query.trim()}%"
        helper.readableDatabase.rawQuery(
            "SELECT uri, path, file_name, size, title, author, subsongs FROM library_index " +
                "WHERE title LIKE ? OR file_name LIKE ? OR author LIKE ? " +
                "ORDER BY title LIMIT ?",
            // No SQL limit: the platform filter runs below, and a limit applied first would cut
            // rows the filter was going to keep. The loop stops itself once it has enough.
            arrayOf(like, like, like, (limit * PLATFORM_SCAN_FACTOR).toString()),
        ).use { row ->
            buildList {
                while (row.moveToNext() && size < limit) {
                    val fileName = row.getString(2)
                    if (platforms.isNotEmpty() && !Platforms.matches(fileName, platforms)) continue
                    add(
                        TrackRef(
                            id = row.getString(0),
                            title = row.getString(4).ifBlank { fileName },
                            subtitle = row.getString(1),
                            sizeBytes = row.getLong(3),
                            fileName = fileName,
                            author = row.getString(5),
                            subsongs = row.getInt(6),
                        )
                    )
                }
            }
        }
    }

    /**
     * A file in the scanned library that matches a name and size.
     *
     * How an imported playlist finds its tracks on a device that did not write it: the document URI
     * it carries was issued by a provider somewhere else and means nothing here, but the same tune
     * under the same name and byte count almost certainly is the same tune. It is the rule
     * `TrackRef.sameFileAs` already uses to stop one file appearing twice in a playlist.
     *
     * Size is part of it, not decoration -- `elysium.mod` is a filename several hundred people have
     * used.
     */
    suspend fun findByFile(fileName: String, sizeBytes: Long): TrackRef? = withContext(Dispatchers.IO) {
        val sql = StringBuilder(
            "SELECT uri, path, file_name, size, title, author, subsongs FROM library_index " +
                "WHERE file_name = ?"
        )
        val args = mutableListOf(fileName)
        if (sizeBytes > 0) {
            sql.append(" AND size = ?")
            args += sizeBytes.toString()
        }
        sql.append(" LIMIT 1")
        helper.readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { row ->
            if (!row.moveToFirst()) return@withContext null
            val name = row.getString(2)
            TrackRef(
                id = row.getString(0),
                title = row.getString(4).ifBlank { name },
                subtitle = row.getString(1),
                sizeBytes = row.getLong(3),
                fileName = name,
                author = row.getString(5),
                subsongs = row.getInt(6),
            )
        }
    }

    suspend fun forgetFolder(folderUri: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("library_index", "folder_uri = ?", arrayOf(folderUri))
        Unit
    }
}
