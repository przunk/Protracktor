// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
     *
     * **And this method did the same thing for seven versions.** It dropped a list of five tables
     * written when there were five, then ran a [SchemaSql.CREATE] that had grown to twelve -- so
     * the recreate met a `catalogues` that was still there and threw, on every start, exactly the
     * failure above. Nothing caught it because a downgrade needs a device: the tests here run
     * migrations, and migrations never take this path.
     *
     * The tables are asked of the file now. A hard-coded list is a claim about the schema that
     * lives somewhere other than the schema, and it went stale the first time one was added.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.transaction {
            val existing = rawQuery(SchemaSql.TABLE_NAMES, null).use { row ->
                buildList { while (row.moveToNext()) add(row.getString(0)) }
            }
            SchemaSql.dropStatements(existing).forEach(::execSQL)
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
