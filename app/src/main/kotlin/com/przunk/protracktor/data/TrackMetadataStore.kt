// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context

import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * Replaces the whole table from the downloaded bytes, and returns how many rows it wrote.
     *
     * Wholesale rather than merged, like the song lengths: the source publishes corrections as well
     * as additions, so a row that vanished upstream should vanish here. One transaction, because
     * 380,000 autocommits take minutes and a half-written table hands out wrong authors.
     *
     * **It parses as it writes, and takes bytes rather than a list.** Reading the file into a list
     * first costs about 150 MB — 1.9 million strings, plus a 30 MB copy of the text, plus the
     * download still in hand — which is an out-of-memory crash on a phone and invisible in every
     * test here, because those run on the JVM against a handful of rows. Streaming keeps one row
     * alive at a time.
     */
    suspend fun replaceAllFrom(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        var written = 0
        helper.writableDatabase.transaction {
            delete("track_metadata", null, null)
            compileStatement(
                "INSERT OR REPLACE INTO track_metadata (md5, author, publisher, album, year) " +
                    "VALUES (?, ?, ?, ?, ?)"
            ).use { statement ->
                bytes.inputStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
                    SongDbMetadata.parse(lines).forEach { entry ->
                        statement.clearBindings()
                        statement.bindString(1, entry.md5)
                        statement.bindString(2, entry.author)
                        statement.bindString(3, entry.publisher)
                        statement.bindString(4, entry.album)
                        statement.bindString(5, entry.year)
                        statement.executeInsert()
                        written++
                    }
                }
            }
        }
        written
    }

    /**
     * What is known about the tune in [bytes], or null if the database has never heard of it.
     *
     * Takes the hash rather than the bytes because the caller has one already: two databases key
     * on the same digest and hashing several megabytes twice per track is work nobody asked for.
     *
     * **The key is [SongDbMetadata.KEY_LENGTH] characters**, not the whole hash. Looking up the
     * full MD5 would run, return nothing, and look exactly like a database that had not been
     * downloaded.
     */
    suspend fun forMd5(md5: String): SongDbMetadata.Entry? = withContext(Dispatchers.IO) {
        val key = SongDbMetadata.keyOf(md5)
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
