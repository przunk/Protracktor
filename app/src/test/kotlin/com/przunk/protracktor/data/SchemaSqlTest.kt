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

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the schema against a real SQLite engine.
 *
 * Compilation proves nothing about SQL. There is no emulator here, so this is where the statements
 * that will one day meet somebody's phone get executed before they do.
 */
class SchemaSqlTest {

    private fun memoryDatabase(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        }

    private fun Connection.run(statements: List<String>) =
        createStatement().use { statement -> statements.forEach(statement::execute) }

    private fun Connection.tableNames(): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            ).use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1)) }
            }
        }

    @Test
    fun `the create statements execute`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            assertEquals(
                setOf(
                    "playlists", "tracks", "playlist_tracks", "granted_folders", "player_state",
                    "catalogues", "catalogue_tracks", "song_lengths",
                ),
                connection.tableNames(),
            )
        }
    }

    @Test
    fun `player_state holds exactly one row and refuses a second`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM player_state").use { rows ->
                    rows.next()
                    assertEquals(1, rows.getInt(1))
                }
            }

            // The CHECK is the point: settings that come as a set must not be able to exist twice,
            // half-written.
            val refused = runCatching {
                connection.run(listOf("INSERT INTO player_state (id) VALUES (1)"))
            }.isFailure
            assertTrue("player_state accepted a second row", refused)
        }
    }

    @Test
    fun `removing a playlist removes its membership rows but not the tracks`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO playlists (id, name, position) VALUES (1, 'Lotus 3', 0)",
                    "INSERT INTO tracks (id, title) VALUES ('uri://a', 'A')",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'uri://a', 0)",
                    "DELETE FROM playlists WHERE id = 1",
                )
            )

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM playlist_tracks").use { rows ->
                    rows.next()
                    assertEquals("cascade should have removed the membership row", 0, rows.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM tracks").use { rows ->
                    rows.next()
                    assertEquals("the track itself belongs to the library, not the playlist", 1, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `a track cannot be in one playlist twice`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO playlists (id, name, position) VALUES (1, 'Lotus 3', 0)",
                    "INSERT INTO tracks (id, title) VALUES ('uri://a', 'A')",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'uri://a', 0)",
                )
            )
            val refused = runCatching {
                connection.run(
                    listOf("INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'uri://a', 1)")
                )
            }.isFailure
            assertTrue("the same track was accepted twice in one playlist", refused)
        }
    }

    @Test
    fun `a version 1 database migrated to the current version matches a fresh install`() {
        // The step that gets forgotten is updating CREATE after adding a migration, and the symptom
        // is a schema that differs between an upgraded phone and a fresh one -- found weeks later,
        // far from its cause.
        memoryDatabase().use { fresh ->
            fresh.run(SchemaSql.CREATE)

            memoryDatabase().use { upgraded ->
                upgraded.run(VERSION_1_SCHEMA)
                upgraded.run(SchemaSql.migrationsBetween(1, SchemaSql.VERSION))
                assertEquals(schemaOf(fresh), schemaOf(upgraded))
            }
        }
    }

    @Test
    fun `migrating from version 1 keeps the data that was already there`() {
        // The only thing that cannot be fixed afterwards. A migration meets a phone holding
        // somebody's playlist exactly once.
        memoryDatabase().use { connection ->
            connection.run(VERSION_1_SCHEMA)
            connection.run(
                listOf(
                    "INSERT INTO playlists (id, name, position) VALUES (1, 'Lotus 3', 0)",
                    "INSERT INTO tracks (id, title, subtitle) VALUES ('uri://a', 'Magnetic Fields', 'lotus')",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'uri://a', 0)",
                    "UPDATE player_state SET current_track_id = 'uri://a', shuffle = 1, repeat_mode = 'ONE'",
                )
            )

            connection.run(SchemaSql.migrationsBetween(1, SchemaSql.VERSION))

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT t.title, t.subtitle FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id"
                ).use { rows ->
                    assertTrue("the playlist did not survive the migration", rows.next())
                    assertEquals("Magnetic Fields", rows.getString(1))
                    assertEquals("lotus", rows.getString(2))
                }
                statement.executeQuery("SELECT current_track_id, shuffle, repeat_mode FROM player_state").use { rows ->
                    rows.next()
                    assertEquals("uri://a", rows.getString(1))
                    assertEquals(1, rows.getInt(2))
                    assertEquals("ONE", rows.getString(3))
                }
            }
        }
    }

    @Test
    fun `removing a catalogue takes its tracks with it`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO catalogues (id, display_name) VALUES ('modland', 'Modland')",
                    "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size) " +
                        "VALUES ('modland', 'Protracker/4-Mat/x.mod', 'Protracker', '4-Mat', 'x.mod', 100)",
                    "DELETE FROM catalogues WHERE id = 'modland'",
                )
            )
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM catalogue_tracks").use { rows ->
                    rows.next()
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `song lengths are keyed by md5 and a second write for one tune replaces the first`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO song_lengths (md5, seconds) VALUES ('6d01', '235.594 61.288')",
                    // HVSC publishes corrections, so the same tune arriving again has to win rather
                    // than collide.
                    "INSERT OR REPLACE INTO song_lengths (md5, seconds) VALUES ('6d01', '240')",
                )
            )
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*), MIN(seconds) FROM song_lengths").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                    assertEquals("240", rows.getString(2))
                }
            }
        }
    }

    @Test
    fun `a random sample returns the number asked for, and no track twice`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf("INSERT INTO catalogues (id, display_name) VALUES ('m', 'Modland')") +
                    (1..20).map {
                        "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size) " +
                            "VALUES ('m', 'p$it', 'MOD', 'a', 't$it', 1)"
                    }
            )
            // The query Random reads ahead with. One statement for three picks, and the reason it
            // is one statement rather than three is that it cannot then hand back a duplicate --
            // which would put the same tune twice in a row into a queue meant to surprise you.
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT path FROM catalogue_tracks ORDER BY RANDOM() LIMIT 3"
                ).use { rows ->
                    val paths = buildList { while (rows.next()) add(rows.getString(1)) }
                    assertEquals(3, paths.size)
                    assertEquals(3, paths.toSet().size)
                }
            }
        }
    }

    @Test
    fun `a missing migration step is an error rather than a silent skip`() {
        val thrown = runCatching { SchemaSql.migrationsBetween(SchemaSql.VERSION, SchemaSql.VERSION + 5) }
        assertTrue("upgrading past the last known version should refuse", thrown.isFailure)
    }

    /**
     * Version 1, frozen.
     *
     * Copied deliberately rather than reached for through SchemaSql: the point of a migration test
     * is to build the shape a real phone is actually holding, and that shape does not change when
     * the current schema does.
     */
    private val VERSION_1_SCHEMA = listOf(
        "CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position INTEGER NOT NULL)",
        "CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '')",
        "CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE, " +
            "track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE, position INTEGER NOT NULL, " +
            "PRIMARY KEY (playlist_id, track_id))",
        "CREATE INDEX idx_playlist_tracks_order ON playlist_tracks(playlist_id, position)",
        "CREATE TABLE granted_folders (uri TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL)",
        "CREATE TABLE player_state (id INTEGER PRIMARY KEY CHECK (id = 0), active_playlist_id INTEGER, " +
            "current_track_id TEXT, shuffle INTEGER NOT NULL DEFAULT 0, repeat_mode TEXT NOT NULL DEFAULT 'OFF')",
        "INSERT INTO player_state (id) VALUES (0)",
    )

    /**
     * The schema as structure, not as text.
     *
     * `sqlite_master` stores the statement exactly as it was written, so the same table declared on
     * one line and across six compares unequal. Collapsing whitespace is what makes this a test of
     * the schema rather than of how the two were typed.
     */
    private fun normalise(sql: String): String = sql
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*([(),])\\s*"), "$1")
        .trim()

    private fun schemaOf(connection: Connection): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name"
            ).use { rows ->
                buildList {
                    while (rows.next()) add(normalise(rows.getString(1)))
                }
            }
        }
}
