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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The database, opened and upgraded.
 *
 * Hand-written rather than generated. Room is the obvious choice and was the intention, but it
 * needs KSP, and KSP has no release built against the Kotlin that AGP 9.2.1 embeds -- its latest is
 * 2.3.11 against Kotlin 2.3, while AGP 9.2.1 carries Kotlin 2.4.0. Reconsider when that changes;
 * the schema lives in [SchemaSql] as plain statements precisely so the move would be mechanical.
 * `docs/ARCHITECTURE.md` §9 records the decision.
 *
 * All the SQL is in [SchemaSql] so it can be tested on the JVM. This class only executes it.
 */
class ProtracktorDatabase(context: Context) :
    SQLiteOpenHelper(context, SchemaSql.NAME, null, SchemaSql.VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        // Off by default on Android. The schema leans on ON DELETE CASCADE, which without this is
        // decoration.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.transaction { SchemaSql.CREATE.forEach(::execSQL) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.transaction {
            SchemaSql.migrationsBetween(oldVersion, newVersion).forEach(::execSQL)
        }
    }

    /**
     * Going down recreates the database rather than refusing to open.
     *
     * Installing an older build over a newer one happens whenever anyone tests from a file. The
     * default behaviour throws on **every** start, which leaves an app that cannot be launched at
     * all until its data is cleared by hand -- losing exactly the same data, and leaving the user
     * to work out why (AGENTS.md §10).
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.transaction {
            listOf("player_state", "granted_folders", "playlist_tracks", "tracks", "playlists")
                .forEach { execSQL("DROP TABLE IF EXISTS $it") }
            SchemaSql.CREATE.forEach(::execSQL)
        }
    }
}

internal inline fun SQLiteDatabase.transaction(body: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        body()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}
