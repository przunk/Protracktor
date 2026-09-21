// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.przunk.protracktor.player.LengthSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lengths this phone learnt by playing a tune to its end (A50, folded into A52).
 *
 * For the tunes songdb does not know — most often a file in a granted folder — UADE measures a
 * subsong in the background the first time it plays; this keeps the answer, so the second play
 * shows the total and the slider at once. Keyed like `songdb_lengths`, the MD5's first 48 bits as a
 * number, so one hash answers both. The rules for combining and recording are pure and tested in
 * [LengthSource]; this only stores.
 */
class LearnedLengthStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    /** Every subsong's learnt length for the file with this [md5], zero where none; empty if none. */
    suspend fun forMd5(md5: String): List<Double> = withContext(Dispatchers.IO) {
        val key = SongDbLengths.keyOf(md5) ?: return@withContext emptyList()
        read(helper.readableDatabase, key)
    }

    /** Records [seconds] for [subsong] of the file with this [md5]. Writes only if it changed. */
    suspend fun remember(md5: String, subsong: Int, seconds: Double) = withContext(Dispatchers.IO) {
        val key = SongDbLengths.keyOf(md5) ?: return@withContext
        val database = helper.writableDatabase
        val updated = LengthSource.learn(read(database, key), subsong, seconds) ?: return@withContext
        database.insertWithOnConflict(
            "learned_lengths",
            null,
            ContentValues().apply {
                put("key", key)
                put("seconds", LengthSource.encode(updated))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun read(database: SQLiteDatabase, key: Long): List<Double> =
        database.rawQuery(
            "SELECT seconds FROM learned_lengths WHERE key = CAST(? AS INTEGER)",
            arrayOf(key.toString()),
        ).use { row -> if (row.moveToFirst()) LengthSource.decode(row.getString(0)) else emptyList() }
}
