// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DatabaseDowngradeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val future = SchemaSql.VERSION + 1

    @Before fun freshFixture() {
        ProtracktorDatabase.of(context).close()
        // Robolectric changes the application's database directory between tests, unlike a process.
        resetHelper()
        context.deleteDatabase(SchemaSql.NAME)
    }

    @After fun closeFixture() {
        ProtracktorDatabase.of(context).close()
        resetHelper()
    }

    private fun resetHelper() {
        ProtracktorDatabase::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, null)
        }
    }

    private fun seedFuture() {
        val file = context.getDatabasePath(SchemaSql.NAME)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            SchemaSql.CREATE.forEach(db::execSQL)
            db.execSQL("INSERT INTO playlists VALUES (9, 'Keep playlist', 0)")
            db.execSQL("INSERT INTO tracks (id, title) VALUES ('keep', 'Keep me')")
            db.execSQL("INSERT INTO playlist_tracks VALUES (9, 'keep', 0)")
            db.execSQL(SchemaSql.PLAY_HISTORY_RECORD,
                arrayOf<Any>("keep", "Keep me", "Modland", "keep.mod", "Author", 12L, 42L, "keep"))
            db.execSQL("UPDATE play_history SET listened = 1")
            db.execSQL(SchemaSql.SAVED_PLACE_INSERT, arrayOf<Any>("modland", "Protracker", "Author", "Modland", 43L))
            db.execSQL("CREATE TABLE future_data (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("CREATE INDEX future_value ON future_data(value)")
            db.execSQL("CREATE TRIGGER future_no_delete BEFORE DELETE ON future_data BEGIN SELECT RAISE(ABORT, 'keep'); END")
            db.execSQL("INSERT INTO future_data VALUES (1, 'future content')")
            db.version = future
        }
    }

    private fun assertPreserved(db: SQLiteDatabase) {
        assertEquals(future, db.version)
        db.rawQuery("SELECT p.name, t.title FROM playlists p JOIN playlist_tracks pt ON pt.playlist_id = p.id JOIN tracks t ON t.id = pt.track_id", null).use {
            assertTrue(it.moveToFirst()); assertEquals("Keep playlist", it.getString(0)); assertEquals("Keep me", it.getString(1))
        }
        db.rawQuery("SELECT title, listened, play_count FROM play_history WHERE track_id = 'keep'", null).use {
            assertTrue(it.moveToFirst()); assertEquals("Keep me", it.getString(0))
            assertEquals(1, it.getInt(1)); assertEquals(1, it.getInt(2))
        }
        db.rawQuery("SELECT author FROM saved_places", null).use {
            assertTrue(it.moveToFirst()); assertEquals("Author", it.getString(0))
        }
        db.rawQuery("SELECT value FROM future_data", null).use {
            assertTrue(it.moveToFirst()); assertEquals("future content", it.getString(0))
        }
        db.rawQuery("SELECT count(*) FROM sqlite_master WHERE name IN ('future_value', 'future_no_delete')", null).use {
            assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
        }
    }

    @Test fun `real helper refuses a newer schema on every attempt`() {
        seedFuture()
        repeat(2) {
            assertThrows(SQLiteException::class.java) { ProtracktorDatabase.of(context).writableDatabase }
        }
    }

    @Test fun `failed older opens leave data and version available to a compatible helper`() {
        seedFuture()
        repeat(2) { runCatching { ProtracktorDatabase.of(context).writableDatabase } }
        ProtracktorDatabase.of(context).close()
        val compatible = object : SQLiteOpenHelper(context, SchemaSql.NAME, null, future) {
            override fun onCreate(db: SQLiteDatabase) { error("Existing database must survive") }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("The older app must not change the version")
            }
        }
        compatible.use { assertPreserved(it.writableDatabase) }
    }

    @Test fun `read only preflight refuses newer data without altering the file`() {
        seedFuture()
        val file = context.getDatabasePath(SchemaSql.NAME)
        val before = file.readBytes()
        repeat(2) { assertEquals(DatabaseAccessState.Newer(future, SchemaSql.VERSION), DatabaseAccess.check(context)) }
        assertArrayEquals(before, file.readBytes())
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use(::assertPreserved)
    }

    @Test fun `preflight sees newer version committed to a live WAL`() {
        seedFuture()
        val file = context.getDatabasePath(SchemaSql.NAME)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertTrue(db.enableWriteAheadLogging())
            db.rawQuery("PRAGMA wal_autocheckpoint = 0", null).use { assertTrue(it.moveToFirst()) }
            db.version = future + 1
            assertEquals(DatabaseAccessState.Newer(future + 1, SchemaSql.VERSION), DatabaseAccess.check(context))
            assertEquals(future + 1, db.version)
        }
    }

    @Test fun `fresh and current files retain the normal create path`() {
        val file = context.getDatabasePath(SchemaSql.NAME)
        assertEquals(DatabaseAccessState.Ready, DatabaseAccess.check(context))
        assertFalse(file.exists())
        assertEquals(SchemaSql.VERSION, ProtracktorDatabase.of(context).writableDatabase.version)
        assertEquals(DatabaseAccessState.Ready, DatabaseAccess.check(context))
    }

    @Test fun `preflight leaves older data for the real helper to migrate`() {
        seedFuture()
        SQLiteDatabase.openDatabase(context.getDatabasePath(SchemaSql.NAME).absolutePath, null,
            SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE saved_places")
            db.version = 22
        }
        assertEquals(DatabaseAccessState.Ready, DatabaseAccess.check(context))
        SQLiteDatabase.openDatabase(context.getDatabasePath(SchemaSql.NAME).absolutePath, null,
            SQLiteDatabase.OPEN_READONLY).use { assertEquals(22, it.version) }
        val db = ProtracktorDatabase.of(context).writableDatabase
        assertEquals(23, db.version)
        db.rawQuery("SELECT title FROM play_history WHERE track_id = 'keep'", null).use {
            assertTrue(it.moveToFirst()); assertEquals("Keep me", it.getString(0))
        }
        db.execSQL(SchemaSql.SAVED_PLACE_INSERT, arrayOf<Any>("modland", "MOD", "Author", "Modland", 99L))
    }

    @Test fun `unreadable input is not deleted or repaired by the probe`() {
        val file = context.getDatabasePath(SchemaSql.NAME)
        file.parentFile!!.mkdirs()
        val bytes = "Synthetic unreadable database fixture".toByteArray()
        file.writeBytes(bytes)
        repeat(2) { assertEquals(DatabaseAccessState.Unreadable, DatabaseAccess.check(context)) }
        assertArrayEquals(bytes, file.readBytes())
    }
}
