// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context

import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * The stored songdb metadata, and the lookup that uses it.
 *
 * Parsing lives in [SongDbMetadata]; this is where the result is kept and asked — the same split as
 * [SongLengths] and [SongLengthStore], and for the same reason: fifteen megabytes of somebody
 * else's text format belongs in a JVM test, while this needs a phone.
 */
class TrackMetadataStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    /** How many tunes are known. Zero means the database has not been downloaded. */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM track_metadata", null).use { row ->
            if (row.moveToFirst()) row.getInt(0) else 0
        }
    }

    /** Throws the whole table away. Re-downloadable from the button that fetched it. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("track_metadata", null, null)
        Unit
    }

    /**
     * Replaces the whole table.
     *
     * Wholesale rather than merged, like the song lengths: the source publishes corrections as well
     * as additions, so a row that vanished upstream should vanish here. One transaction, because
     * 380,000 autocommits take minutes and a half-written table hands out wrong authors.
     */
    suspend fun replaceAll(entries: List<SongDbMetadata.Entry>) = withContext(Dispatchers.IO) {
        helper.writableDatabase.transaction {
            delete("track_metadata", null, null)
            compileStatement(
                "INSERT OR REPLACE INTO track_metadata (md5, author, publisher, album, year) " +
                    "VALUES (?, ?, ?, ?, ?)"
            ).use { statement ->
                entries.forEach { entry ->
                    statement.clearBindings()
                    statement.bindString(1, entry.md5)
                    statement.bindString(2, entry.author)
                    statement.bindString(3, entry.publisher)
                    statement.bindString(4, entry.album)
                    statement.bindString(5, entry.year)
                    statement.executeInsert()
                }
            }
        }
    }

    /**
     * What is known about the tune in [bytes], or null if the database has never heard of it.
     *
     * Takes the bytes for the same reason [SongLengthStore.secondsFor] does: it is what the player
     * is holding when it needs the answer, and a track fetched over the network has no path worth
     * hashing.
     *
     * **The key is [SongDbMetadata.KEY_LENGTH] characters**, not the whole hash. Looking up the
     * full MD5 would run, return nothing, and look exactly like a database that had not been
     * downloaded.
     */
    suspend fun forBytes(bytes: ByteArray): SongDbMetadata.Entry? = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val key = SongDbMetadata.keyOf(digest.joinToString("") { "%02x".format(it) })
        helper.readableDatabase.rawQuery(
            "SELECT author, publisher, album, year FROM track_metadata WHERE md5 = ?",
            arrayOf(key),
        ).use { row ->
            if (row.moveToFirst()) {
                SongDbMetadata.Entry(key, row.getString(0), row.getString(1), row.getString(2), row.getString(3))
            } else {
                null
            }
        }
    }
}
