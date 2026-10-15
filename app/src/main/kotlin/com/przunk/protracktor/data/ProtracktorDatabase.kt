// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
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
class ProtracktorDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, SchemaSql.NAME, null, SchemaSql.VERSION) {

    companion object {
        @Volatile
        private var instance: ProtracktorDatabase? = null

        /**
         * The one helper for this process.
         *
         * **Not a style preference.** `SQLiteOpenHelper` synchronises within an instance and not
         * between instances, so five stores each holding their own -- which is what this was --
         * meant five connection pools on one file and five things that could independently decide
         * to run `onUpgrade`. Two of them opening at once during an upgrade can both read the old
         * version and both migrate; the statements are plain `CREATE TABLE` and the second run
         * fails with "table already exists", on the launch that upgrades, which is the launch that
         * matters. `docs/review.md` R3 has the reproduction.
         *
         * `IF NOT EXISTS` would have silenced the symptom and left five pools racing, which is the
         * wrong half of the problem.
         *
         * Double-checked locking on a `@Volatile` field: the fast path is a read, and the slow one
         * happens once per process.
         */
        fun of(context: Context): ProtracktorDatabase =
            instance ?: synchronized(this) {
                instance ?: ProtracktorDatabase(context.applicationContext).also { instance = it }
            }
    }

    init {
        // **Write-ahead logging, so a read never queues behind another** (the owner, 2026-09-26).
        // Without it Android gives the file one connection and runs every query in turn: History,
        // opened a moment after Browse, waited seconds for Browse's count of the platforms to end,
        // and opened at once when the count had been left to finish first. With it, reads run
        // side by side, and beside a write.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        // Off by default on Android. The schema leans on ON DELETE CASCADE, which without this is
        // decoration.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.transaction { SchemaSql.CREATE.forEach(::execSQL) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = DatabasePreparation.during {
        db.transaction {
            SchemaSql.migrationsBetween(oldVersion, newVersion).forEach(::execSQL)
            // The one migration SQL cannot finish by itself: folding needs Unicode tables SQLite
            // does not have (A53). Same transaction, so a phone never holds the new column half
            // filled.
            if (oldVersion < 19 && newVersion >= 19) {
                SchemaSql.backfillFolded(
                    rows = { sql ->
                        rawQuery(sql, null).use { row ->
                            buildList {
                                while (row.moveToNext()) {
                                    add(row.getLong(0) to Array(row.columnCount - 1) { row.getString(it + 1).orEmpty() })
                                }
                            }
                        }
                    },
                    write = { sql, folded, rowid -> execSQL(sql, arrayOf<Any>(folded, rowid)) },
                )
            }
        }
    }

    /** Keep the file intact so a compatible build can reopen it (C108). */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit =
        throw SQLiteException("Database schema $oldVersion requires a compatible app; supported schema is $newVersion")
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
