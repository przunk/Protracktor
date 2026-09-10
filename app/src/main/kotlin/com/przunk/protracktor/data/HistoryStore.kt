// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tune that was played, and when it last was. */
data class PlayedTrack(
    val trackId: String,
    val title: String,
    val subtitle: String,
    val fileName: String,
    val author: String,
    val sizeBytes: Long,
    val playedAt: Long,
    val playCount: Int,
)

/**
 * What has been played.
 *
 * The question it answers is "that tune two days ago, what was it" — not "audit this app". That is
 * what decides the two things about it that are otherwise arguable: it holds one row per track
 * rather than one per play, and it forgets the oldest rather than growing forever.
 */
class HistoryStore(context: Context) {

    private val helper = ProtracktorDatabase.of(context)

    /**
     * Records a play, or moves an existing one to the top and counts it.
     *
     * The title and author are written every time rather than only on insert, because they improve:
     * a track is filed under its filename until it has been opened, and afterwards under the name
     * the tune calls itself. History should show the better one once it exists.
     */
    suspend fun record(
        trackId: String,
        title: String,
        subtitle: String,
        fileName: String,
        author: String,
        sizeBytes: Long,
    ) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.transaction {
            execSQL(
                SchemaSql.PLAY_HISTORY_RECORD,
                arrayOf<Any>(
                    trackId, title, subtitle, fileName, author, sizeBytes,
                    System.currentTimeMillis(), trackId,
                ),
            )
            // Forgetting the oldest, in the same transaction as the insert, so the table cannot
            // be left over its limit by a crash between the two.
            execSQL(SchemaSql.PLAY_HISTORY_PRUNE)
        }
    }

    /** Most recently played first. */
    suspend fun recent(): List<PlayedTrack> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT track_id, title, subtitle, file_name, author, size, played_at, play_count " +
                "FROM play_history ORDER BY played_at DESC",
            null,
        ).use { row ->
            buildList {
                while (row.moveToNext()) {
                    add(
                        PlayedTrack(
                            trackId = row.getString(0),
                            title = row.getString(1),
                            subtitle = row.getString(2),
                            fileName = row.getString(3),
                            author = row.getString(4),
                            sizeBytes = row.getLong(5),
                            playedAt = row.getLong(6),
                            playCount = row.getInt(7),
                        )
                    )
                }
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("play_history", null, null)
        Unit
    }
}
