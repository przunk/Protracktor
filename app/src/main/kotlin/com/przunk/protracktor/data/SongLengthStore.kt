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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * The stored HVSC song lengths, and the lookup that uses them.
 *
 * Parsing lives in [SongLengths]; this is where the result is kept and asked. Separate because the
 * parser is 5 MB of somebody else's text format and belongs in a JVM test, while this needs a phone.
 */
class SongLengthStore(context: Context) {

    private val helper = ProtracktorDatabase(context.applicationContext)

    /** How many tunes are known. Zero means the database has not been downloaded. */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM song_lengths", null).use { row ->
            if (row.moveToFirst()) row.getInt(0) else 0
        }
    }

    /**
     * Replaces the whole database.
     *
     * Wholesale rather than merged: HVSC publishes corrections as well as additions, so an entry
     * that vanished from the file is an entry that should vanish here. One transaction for the same
     * reason as the catalogue index -- sixty thousand autocommits would take minutes, and a
     * half-written length database would quietly hand out wrong durations.
     */
    suspend fun replaceAll(entries: List<SongLengths.Entry>) = withContext(Dispatchers.IO) {
        helper.writableDatabase.transaction {
            delete("song_lengths", null, null)
            compileStatement("INSERT OR REPLACE INTO song_lengths (md5, seconds) VALUES (?, ?)")
                .use { statement ->
                    entries.forEach { entry ->
                        statement.clearBindings()
                        statement.bindString(1, entry.md5)
                        statement.bindString(2, SongLengths.pack(entry.seconds))
                        statement.executeInsert()
                    }
                }
        }
    }

    /**
     * Every subsong's length for the tune in [bytes], or null if HVSC has never heard of it.
     *
     * Takes the bytes rather than a path because that is what the player is holding when it needs
     * the answer, and because the key is a hash of the file's contents -- a SID reached over the
     * network has no path to speak of.
     */
    suspend fun secondsFor(bytes: ByteArray): List<Double>? = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val md5 = digest.joinToString("") { "%02x".format(it) }
        helper.readableDatabase
            .rawQuery("SELECT seconds FROM song_lengths WHERE md5 = ?", arrayOf(md5))
            .use { row -> if (row.moveToFirst()) SongLengths.unpack(row.getString(0)) else null }
    }
}
