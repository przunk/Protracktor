// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.io.File
import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class SavedPlacesMigrationTest {
    @Test fun `upgrade from exported 22 preserves user data and adds an independent bookmark table`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            fun sql(value: String) = db.createStatement().use { it.execute(value) }
            fun count(table: String) = db.createStatement().use { s ->
                s.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) }
            }
            sql("PRAGMA foreign_keys=ON")
            File("schemas/22.sql").readText().split(';').filter { it.isNotBlank() }.forEach { sql(it) }
            sql("INSERT INTO playlists(id,name,position) VALUES(1,'Kept',0)")
            sql("INSERT INTO tracks(id,title) VALUES('track','Kept')")
            sql("INSERT INTO playlist_tracks(playlist_id,track_id,position) VALUES(1,'track',0)")
            sql("INSERT INTO play_history(track_id,title,played_at,play_count,listened) VALUES('track','Kept',123,7,1)")
            sql("INSERT INTO modland_favourites(path) VALUES('Kept')")
            sql("INSERT INTO catalogues(id,display_name) VALUES('modland','Modland')")
            sql("INSERT INTO catalogue_tracks(catalogue_id,path,format,author,title,size) VALUES('modland','a.mod','Protracker','Artist','A',123)")
            db.autoCommit = false
            SchemaSql.migrationsBetween(22, 23).forEach { sql(it) }
            db.commit()
            listOf("playlists", "tracks", "playlist_tracks", "play_history", "modland_favourites", "catalogues", "catalogue_tracks")
                .forEach { assertEquals(it, 1, count(it)) }
            assertEquals(0, count("saved_places"))
            assertEquals(1, count("play_history WHERE played_at=123 AND play_count=7 AND listened=1"))
            sql("INSERT INTO saved_places VALUES('modland','Protracker','Artist','Modland',1)")
            sql("DELETE FROM catalogues")
            sql("DELETE FROM play_history")
            assertEquals(1, count("saved_places"))
        }
    }
}
