// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The stored songdb lengths, and the lookup that uses them (`docs/PLAN_SONGDB_LENGTHS.md`, A52).
 *
 * Parsing lives in [SongDbLengths]; this is where the result is kept and asked — the split
 * [TrackMetadataStore] and [SongLengthStore] already make, for the same reason.
 */
class SongDbLengthStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    /** How many files are known. Zero means the lengths have not been downloaded. */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM songdb_lengths", null).use { row ->
            if (row.moveToFirst()) row.getInt(0) else 0
        }
    }

    /** Throws the whole table away. Re-downloadable with the track metadata. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("songdb_lengths", null, null)
        Unit
    }

    /**
     * Replaces the whole table from the downloaded bytes, and returns how many rows it wrote.
     *
     * Wholesale, in one transaction, and **streamed**: [TrackMetadataStore.replaceAllFrom] says what
     * reading twelve megabytes into a list first costs on a phone. One row alive at a time.
     */
    suspend fun replaceAllFrom(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        var written = 0
        helper.writableDatabase.transaction {
            delete("songdb_lengths", null, null)
            compileStatement(
                "INSERT OR REPLACE INTO songdb_lengths (key, first_subsong, subsongs) VALUES (?, ?, ?)"
            ).use { statement ->
                bytes.inputStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
                    SongDbLengths.parse(lines).forEach { row ->
                        statement.clearBindings()
                        statement.bindLong(1, row.key)
                        statement.bindLong(2, row.firstSubsong.toLong())
                        statement.bindString(3, row.subsongs)
                        statement.executeInsert()
                        written++
                    }
                }
            }
        }
        written
    }

    /**
     * Every subsong's length for the file with this [md5], in order, zero where a subsong has none;
     * empty when songdb has never heard of the file.
     *
     * Takes the full hash the caller already has and keys on its first twelve characters, as a
     * number. Looking up the whole hash would miss every time, silently.
     */
    suspend fun forMd5(md5: String): List<Double> = withContext(Dispatchers.IO) {
        val key = SongDbLengths.keyOf(md5) ?: return@withContext emptyList()
        helper.readableDatabase.rawQuery(
            "SELECT subsongs FROM songdb_lengths WHERE key = CAST(? AS INTEGER)",
            arrayOf(key.toString()),
        ).use { row ->
            if (row.moveToFirst()) SongDbLengths.lengths(row.getString(0)) else emptyList()
        }
    }
}
