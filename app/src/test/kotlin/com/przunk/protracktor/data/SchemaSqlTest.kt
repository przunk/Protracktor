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
                setOf("playlists", "tracks", "playlist_tracks", "granted_folders", "player_state"),
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
    fun `a fresh install and a fully migrated database end up identical`() {
        // The step that gets forgotten is updating CREATE after adding a migration, and the symptom
        // is a schema that differs between an upgraded phone and a fresh one. Trivially true at
        // version 1; the point is that it stops being trivially true the moment it matters.
        memoryDatabase().use { fresh ->
            fresh.run(SchemaSql.CREATE)
            memoryDatabase().use { migrated ->
                migrated.run(SchemaSql.CREATE)
                migrated.run(SchemaSql.migrationsBetween(SchemaSql.VERSION, SchemaSql.VERSION))
                assertEquals(schemaOf(fresh), schemaOf(migrated))
            }
        }
    }

    @Test
    fun `a missing migration step is an error rather than a silent skip`() {
        val thrown = runCatching { SchemaSql.migrationsBetween(SchemaSql.VERSION, SchemaSql.VERSION + 5) }
        assertTrue("upgrading past the last known version should refuse", thrown.isFailure)
    }

    private fun schemaOf(connection: Connection): List<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name"
            ).use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
}
