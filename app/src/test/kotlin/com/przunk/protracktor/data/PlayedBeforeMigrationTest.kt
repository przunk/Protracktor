// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class PlayedBeforeMigrationTest {
    private fun database() = DriverManager.getConnection("jdbc:sqlite::memory:")
    private fun Connection.run(sql: String) = createStatement().use { it.execute(sql); Unit }
    private fun Connection.scalar(sql: String): Int = createStatement().use {
        it.executeQuery(sql).use { row -> check(row.next()); row.getInt(1) }
    }
    private fun Connection.oldSchema() {
        (SchemaSql.CREATE - SchemaSql.MIGRATIONS[22].orEmpty().toSet()).forEach { run(it) }
    }
    private fun Connection.record(id: String) {
        prepareStatement(SchemaSql.PLAY_HISTORY_RECORD).use {
            listOf(id, "Title", "Source", "file.mod", "Artist").forEachIndexed { i, value -> it.setString(i + 1, value) }
            it.setLong(6, 123)
            it.setLong(7, 456)
            it.setString(8, id)
            it.executeUpdate()
        }
    }

    @Test fun `upgrade qualifies legacy history without changing counts metadata or other data`() {
        database().use { db ->
            db.oldSchema()
            db.run("INSERT INTO play_history(track_id,title,played_at,play_count) VALUES ('old','Legacy',123,7)")
            db.run("INSERT INTO playlists(id,name,position) VALUES(1,'Keep',0)")
            db.run("INSERT INTO tracks(id,title) VALUES('old','Keep')")
            db.run("INSERT INTO playlist_tracks(playlist_id,track_id,position) VALUES(1,'old',0)")
            SchemaSql.migrationsBetween(21, 22).forEach { db.run(it) }
            assertEquals(1, db.scalar("SELECT listened FROM play_history WHERE track_id='old'"))
            assertEquals(7, db.scalar("SELECT play_count FROM play_history WHERE title='Legacy' AND played_at=123"))
            assertEquals(1, db.scalar("SELECT COUNT(*) FROM playlist_tracks"))
            db.record("new")
            assertEquals(0, db.scalar("SELECT listened FROM play_history WHERE track_id='new'"))
            db.record("old")
            assertEquals(1, db.scalar("SELECT listened FROM play_history WHERE track_id='old'"))
            assertEquals(8, db.scalar("SELECT play_count FROM play_history WHERE track_id='old'"))
        }
    }

    @Test fun `fresh entries qualify without changing their history order and clear never reseeds`() {
        database().use { db ->
            SchemaSql.CREATE.forEach { db.run(it) }
            db.record("a")
            assertEquals(0, db.scalar("SELECT listened FROM play_history"))
            db.run("UPDATE play_history SET listened=1 WHERE track_id='a'")
            assertEquals(456, db.scalar("SELECT played_at FROM play_history"))
            assertEquals(1, db.scalar("SELECT play_count FROM play_history"))
            db.run("DELETE FROM play_history")
            SchemaSql.migrationsBetween(SchemaSql.VERSION, SchemaSql.VERSION).forEach { db.run(it) }
            db.record("a")
            assertEquals(0, db.scalar("SELECT listened FROM play_history"))
        }
    }

    @Test fun `an empty old history stays empty after migration`() {
        database().use { db ->
            db.oldSchema()
            SchemaSql.migrationsBetween(21, 22).forEach { db.run(it) }
            assertEquals(0, db.scalar("SELECT COUNT(*) FROM play_history"))
            db.record("a")
            assertEquals(0, db.scalar("SELECT listened FROM play_history"))
        }
    }
}
