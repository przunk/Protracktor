// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.util.Log

internal sealed interface DatabaseAccessState {
    data object Ready : DatabaseAccessState
    data class Newer(val found: Int, val supported: Int) : DatabaseAccessState
    data object Unreadable : DatabaseAccessState
}

/** A read-only preflight, not a second helper: never create, migrate, repair or reset here. */
internal object DatabaseAccess {
    fun check(context: Context): DatabaseAccessState {
        val file = context.getDatabasePath(SchemaSql.NAME)
        if (!file.exists()) return DatabaseAccessState.Ready
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                // Android's default corruption handler deletes files, even during an open.
                { throw SQLiteDatabaseCorruptException("Database compatibility check refused repair") },
            ).use { db ->
                val found = db.version
                if (found > SchemaSql.VERSION) DatabaseAccessState.Newer(found, SchemaSql.VERSION)
                else DatabaseAccessState.Ready
            }
        } catch (error: Exception) {
            Log.w("Protracktor", "Could not check database compatibility; leaving data intact", error)
            DatabaseAccessState.Unreadable
        }
    }
}
