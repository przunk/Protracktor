// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Every table a fresh install has. Named once, because two tests check against it. */
    private fun freshTableNames(): Set<String> = setOf(
        "playlists", "tracks", "playlist_tracks", "granted_folders", "player_state",
        "catalogues", "catalogue_tracks", "song_lengths", "play_history", "library_index",
        "track_metadata", "modland_favourites", "songdb_lengths", "learned_lengths",
    )

    @Test
    fun `the create statements execute`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            assertEquals(freshTableNames(), connection.tableNames())
        }
    }

    /**
     * `docs/STATUS.md` C41: adding a track to another playlist took it out of the one it was in.
     *
     * **SQLite's REPLACE is a DELETE and an INSERT**, and `playlist_tracks.track_id` references
     * `tracks(id)` `ON DELETE CASCADE` with foreign keys on. So re-writing the `tracks` row for a
     * track already in a playlist deleted every `playlist_tracks` row that pointed at it -- and the
     * write that followed put it back in one playlist only. A copy became a move.
     */
    @Test
    fun `writing a track again leaves it in the playlists it is already in`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO playlists (id, name, position) VALUES (1, 'source', 0)",
                    "INSERT INTO playlists (id, name, position) VALUES (2, 'target', 1)",
                    "INSERT INTO tracks (id, title, subtitle) VALUES ('x', 'Tune', 'where')",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'x', 0)",
                )
            )

            // What `LibraryStore.replaceTracks` does for the target: the track's own row, then its
            // place in that playlist.
            connection.run(
                listOf(
                    "INSERT OR IGNORE INTO tracks (id, title, subtitle) VALUES ('x', 'Tune', 'where')",
                    "UPDATE tracks SET title = 'Tune', subtitle = 'where' WHERE id = 'x'",
                    "INSERT OR REPLACE INTO playlist_tracks (playlist_id, track_id, position) VALUES (2, 'x', 0)",
                )
            )

            assertEquals(setOf(1L, 2L), connection.playlistsHolding("x"))
        }
    }

    /** The same write the old way, to show what the rule above is protecting against. */
    @Test
    fun `replacing the track row instead would empty the other playlists`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO playlists (id, name, position) VALUES (1, 'source', 0)",
                    "INSERT INTO playlists (id, name, position) VALUES (2, 'target', 1)",
                    "INSERT INTO tracks (id, title, subtitle) VALUES ('x', 'Tune', 'where')",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'x', 0)",
                    "INSERT OR REPLACE INTO tracks (id, title, subtitle) VALUES ('x', 'Tune', 'where')",
                    "INSERT OR REPLACE INTO playlist_tracks (playlist_id, track_id, position) VALUES (2, 'x', 0)",
                )
            )
            assertEquals(setOf(2L), connection.playlistsHolding("x"))
        }
    }

    private fun Connection.playlistsHolding(trackId: String): Set<Long> =
        prepareStatement("SELECT playlist_id FROM playlist_tracks WHERE track_id = ?").use { statement ->
            statement.setString(1, trackId)
            statement.executeQuery().use { rows ->
                buildSet { while (rows.next()) add(rows.getLong(1)) }
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
    fun `the same track can belong to multiple playlists`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf(
                    "INSERT INTO playlists (id, name, position) VALUES (1, 'Main', 0)",
                    "INSERT INTO playlists (id, name, position) VALUES (2, 'Favorites', 1)",
                    "INSERT INTO tracks (id, title) VALUES ('modland://tune.mod', 'Cool Tune')",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 'modland://tune.mod', 0)",
                    "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (2, 'modland://tune.mod', 0)",
                )
            )

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) FROM playlist_tracks WHERE track_id = 'modland://tune.mod'"
                ).use { rows ->
                    rows.next()
                    assertEquals(2, rows.getInt(1))
                }
            }
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
    fun `an index already stored learns how big its archive is without being fetched again`() {
        // Version 16, and the point of backfilling it by counting rather than by re-downloading:
        // version 15 had just made "how many rows" and "how many play" different numbers, and an
        // index stored the day before would otherwise have said its archive held nothing.
        memoryDatabase().use { connection ->
            connection.run(VERSION_1_SCHEMA + SchemaSql.migrationsBetween(1, 15))
            connection.run(
                listOf(
                    "INSERT INTO catalogues (id, display_name, track_count) VALUES ('modland', 'Modland', 2)",
                    "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size, playable) " +
                        "VALUES ('modland', 'a', 'Protracker', '4-Mat', 'a.mod', 1, 1)",
                    "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size, playable) " +
                        "VALUES ('modland', 'b', 'Protracker', '4-Mat', 'b.mod', 1, 1)",
                    "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size, playable) " +
                        "VALUES ('modland', 'c', 'Pictures', 'me', 'c.jpg', 1, 0)",
                )
            )

            connection.run(SchemaSql.migrationsBetween(15, SchemaSql.VERSION))

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT track_count, archive_count FROM catalogues").use { rows ->
                    rows.next()
                    assertEquals("what this build opens is untouched", 2, rows.getInt(1))
                    assertEquals("and what the archive holds is counted, not guessed", 3, rows.getInt(2))
                }
            }
        }
    }

    @Test
    fun `songdb's lengths arrive in their own table and touch nothing else`() {
        // Version 17 (A52). A phone at 16 holds playlists, SID lengths and songdb's metadata; all of
        // it must be exactly where it was, and the new table must take the widest key songdb can
        // publish -- 48 bits, which a text key would have stored as twelve characters.
        memoryDatabase().use { connection ->
            connection.run(VERSION_1_SCHEMA + SchemaSql.migrationsBetween(1, 16))
            connection.run(
                listOf(
                    "INSERT INTO playlists (name, position) VALUES ('Mine', 0)",
                    "INSERT INTO song_lengths (md5, seconds) VALUES ('0123456789abcdef0123456789abcdef', '120 30')",
                    "INSERT INTO track_metadata (md5, author, publisher, album, year) " +
                        "VALUES ('00000b104a70', 'Devastator', 'Shrimps Design', 'Crunched Chips #5', '1995')",
                )
            )

            connection.run(SchemaSql.migrationsBetween(16, SchemaSql.VERSION))
            connection.run(
                listOf(
                    "INSERT INTO songdb_lengths (key, first_subsong, subsongs) " +
                        "VALUES (${0xffffffffffffL}, 1, '308000,p')",
                )
            )

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM playlists").use { rows ->
                    rows.next(); assertEquals("Mine", rows.getString(1))
                }
                statement.executeQuery("SELECT seconds FROM song_lengths").use { rows ->
                    rows.next(); assertEquals("120 30", rows.getString(1))
                }
                statement.executeQuery("SELECT author FROM track_metadata").use { rows ->
                    rows.next(); assertEquals("Devastator", rows.getString(1))
                }
                statement.executeQuery(
                    "SELECT subsongs FROM songdb_lengths WHERE key = ${0xffffffffffffL}"
                ).use { rows ->
                    assertTrue("the widest 48-bit key is found by itself", rows.next())
                    assertEquals("308000,p", rows.getString(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM learned_lengths").use { rows ->
                    rows.next(); assertEquals("learnt lengths start empty", 0, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `the browse indexes cover only what is offered`() {
        // `docs/ROADMAP_FORMATS.md` step 0: the table holds the whole archive and every screen asks
        // for the playable part, so indexing the rest is 16 MB of b-tree nothing reads -- measured
        // at Modland's size, 129.0 MB against 112.8. Checked as SQL rather than as a comment,
        // because a partial index reverted to a full one is invisible until somebody measures.
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            val indexes = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"
                ).use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                }
            }
            for (name in listOf("idx_catalogue_browse", "idx_catalogue_title")) {
                val sql = indexes[name]
                assertTrue("$name is missing", sql != null)
                assertTrue("$name is not partial: $sql", sql!!.contains("WHERE playable = 1"))
            }
        }
    }

    @Test
    fun `an index written before version 15 keeps every row it has`() {
        // `docs/ROADMAP_FORMATS.md` step 0. The migration's `DEFAULT 1` on `playable` is
        // load-bearing: an index written earlier holds **only** rows the format list of the day
        // accepted, so every row in it is playable — and defaulting to 0 would empty Browse for
        // anybody who did not re-index that minute.
        memoryDatabase().use { connection ->
            connection.run(VERSION_1_SCHEMA + SchemaSql.migrationsBetween(1, 14))
            connection.run(
                listOf(
                    "INSERT INTO catalogues (id, display_name) VALUES ('modland', 'Modland')",
                    "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size) " +
                        "VALUES ('modland', 'p', 'Protracker', '4-Mat', 'tune.mod', 1)",
                )
            )

            connection.run(SchemaSql.migrationsBetween(14, SchemaSql.VERSION))

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT playable FROM catalogue_tracks").use { rows ->
                    rows.next()
                    assertEquals("an old row stays visible", 1, rows.getInt(1))
                }
                // And says what it is: part of the archive, not all of it, so a format added later
                // still needs the index fetching again — which no local recompute can supply.
                statement.executeQuery("SELECT complete FROM catalogues").use { rows ->
                    rows.next()
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `an upgraded phone reads the fallback length as never set`() {
        // `docs/STATUS.md` C56. The column's default is 0 and 0 means "never chosen",
        // which `FallbackLength.fromStored` turns into the default rather than into a tune that
        // ends instantly. Getting this wrong ends every unlisted tune the moment it starts, which
        // is a worse fault than the one being fixed.
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.migrationsBetween(1, 13).let { VERSION_1_SCHEMA + it })
            connection.run(listOf("UPDATE player_state SET shuffle = 1"))

            connection.run(SchemaSql.migrationsBetween(13, SchemaSql.VERSION))

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT shuffle, fallback_length_seconds FROM player_state"
                ).use { rows ->
                    rows.next()
                    assertEquals("the setting beside it did not survive", 1, rows.getInt(1))
                    assertEquals("an upgraded phone must read 0, meaning never set", 0, rows.getInt(2))
                }
            }
        }
    }

    @Test
    fun `a chosen fallback length survives being written and read`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(listOf("UPDATE player_state SET fallback_length_seconds = 420"))
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT fallback_length_seconds FROM player_state").use { rows ->
                    rows.next()
                    assertEquals(420, rows.getInt(1))
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

    /**
     * What `ProtracktorDatabase.onDowngrade` runs, against a real engine.
     *
     * The path in this file that needs a test most: it meets a phone holding somebody's data
     * exactly once. A list of tables to drop written at version 1 and never grown makes the
     * recreate stop at `catalogues`, and the app then cannot start at all.
     */
    @Test
    fun `a downgrade recreates the database instead of tripping over what is already there`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf("INSERT INTO catalogues (id, display_name) VALUES ('m', 'Modland')")
            )

            val existing = connection.createStatement().use { statement ->
                statement.executeQuery(SchemaSql.TABLE_NAMES).use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
            connection.run(SchemaSql.dropStatements(existing))
            connection.run(SchemaSql.CREATE)

            // The shape a fresh install has, and nothing left of what was there.
            assertEquals(freshTableNames(), connection.tableNames())
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM catalogues").use { rows ->
                    rows.next()
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `the favourites clause draws only from tunes that are both listed and indexed`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.run(
                listOf("INSERT INTO catalogues (id, display_name) VALUES ('modland', 'Modland')") +
                    // Three indexed tunes, two of them favourites.
                    (1..3).map {
                        "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size) " +
                            "VALUES ('modland', 'p$it', 'Protracker', 'a', 't$it', 1)"
                    } +
                    // A favourite Modland has moved since the list was compiled. A hundred of the
                    // real 991 are in this state, and the join is what silently drops them -- if it
                    // did not, the dice would hand out a download that 404s.
                    listOf(
                        "INSERT INTO modland_favourites (path) VALUES ('p1')",
                        "INSERT INTO modland_favourites (path) VALUES ('p2')",
                        "INSERT INTO modland_favourites (path) VALUES ('gone')",
                    )
            )
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT path FROM catalogue_tracks WHERE catalogue_id = 'modland' " +
                        "AND path IN (SELECT path FROM modland_favourites) ORDER BY path"
                ).use { rows ->
                    assertEquals(
                        listOf("p1", "p2"),
                        buildList { while (rows.next()) add(rows.getString(1)) },
                    )
                }
            }
        }
    }

    private fun Connection.record(id: String, title: String, at: Long) =
        prepareStatement(SchemaSql.PLAY_HISTORY_RECORD).use { statement ->
            statement.setString(1, id)
            statement.setString(2, title)
            statement.setString(3, "")
            statement.setString(4, "")
            statement.setString(5, "")
            statement.setLong(6, 0)
            statement.setLong(7, at)
            statement.setString(8, id)
            statement.executeUpdate()
        }

    private fun Connection.historyRows(): List<Triple<String, String, Int>> =
        createStatement().use { statement ->
            statement.executeQuery(
                "SELECT track_id, title, play_count FROM play_history ORDER BY played_at DESC"
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(Triple(rows.getString(1), rows.getString(2), rows.getInt(3)))
                    }
                }
            }
        }

    @Test
    fun `playing the same track again counts it instead of adding a row`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.record("a", "First name", 1_000)
            connection.record("b", "Other", 2_000)
            // The same track again, with the better title the app learned by opening it.
            connection.record("a", "Its real name", 3_000)

            val rows = connection.historyRows()
            assertEquals(2, rows.size)
            // Most recent first, so the replayed one has come back to the top.
            assertEquals("a", rows[0].first)
            assertEquals("Its real name", rows[0].second)
            assertEquals(2, rows[0].third)
            assertEquals(1, rows[1].third)
        }
    }

    @Test
    fun `history forgets the oldest once it is over its limit`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            val over = SchemaSql.PLAY_HISTORY_LIMIT + 5
            (1..over).forEach { connection.record("t$it", "t$it", it.toLong()) }
            connection.run(listOf(SchemaSql.PLAY_HISTORY_PRUNE))

            val rows = connection.historyRows()
            assertEquals(SchemaSql.PLAY_HISTORY_LIMIT, rows.size)
            // The newest survived and the oldest did not.
            assertEquals("t$over", rows.first().first)
            assertTrue(rows.none { it.first == "t1" })
        }
    }

    private fun Connection.indexRow(uri: String, folder: String, backends: String) =
        run(listOf(
            "INSERT INTO library_index (uri, folder_uri, path, file_name, indexed_at, backends) " +
                "VALUES ('$uri', '$folder', 'p', 'f', 1, '$backends')"
        ))

    @Test
    fun `rescanning one folder does not touch another`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.indexRow("a1", "folderA", "set1")
            connection.indexRow("a2", "folderA", "set1")
            connection.indexRow("b1", "folderB", "set1")

            // What replaceFolder does: clear this folder, then insert. A global delete here would
            // silently cost the user every other folder they had scanned.
            connection.run(listOf("DELETE FROM library_index WHERE folder_uri = 'folderA'"))
            connection.indexRow("a9", "folderA", "set2")

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT folder_uri, COUNT(*) FROM library_index GROUP BY folder_uri ORDER BY folder_uri"
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals("folderA", rows.getString(1)); assertEquals(1, rows.getInt(2))
                    assertTrue(rows.next())
                    assertEquals("folderB", rows.getString(1)); assertEquals(1, rows.getInt(2))
                }
            }
        }
    }

    @Test
    fun `an index built by other decoders is reported stale`() {
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            connection.indexRow("a1", "folderA", "sc68:2.2.1")
            connection.indexRow("a2", "folderA", "sc68:2.2.1")

            val stale = { backends: String ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT 1 FROM library_index WHERE folder_uri = 'folderA' " +
                            "AND backends <> '$backends' LIMIT 1"
                    ).use { it.next() }
                }
            }
            // Replacing sc68 2.2.1 with 3.0.0b took .sndh from 14/30 to 30/30 -- every "nothing can
            // play this" the old set wrote down became wrong, and the index has to notice.
            assertTrue(stale("sc68:3.0.0b"))
            assertFalse(stale("sc68:2.2.1"))
        }
    }

    @Test
    fun `migrating to the library index keeps what was already indexed and played`() {
        memoryDatabase().use { connection ->
            // Everything up to the version before the index existed.
            connection.run(VERSION_1_SCHEMA)
            connection.run(SchemaSql.migrationsBetween(1, 7))
            connection.run(listOf(
                "INSERT INTO playlists (name, position) VALUES ('Mine', 0)",
                "INSERT INTO tracks (id, title) VALUES ('u1', 'A tune')",
                "INSERT INTO play_history (track_id, title, played_at) VALUES ('u1', 'A tune', 5)",
            ))

            connection.run(SchemaSql.migrationsBetween(7, SchemaSql.VERSION))

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT (SELECT COUNT(*) FROM playlists), (SELECT COUNT(*) FROM tracks), " +
                        "(SELECT COUNT(*) FROM play_history), (SELECT COUNT(*) FROM library_index)"
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                    assertEquals(1, rows.getInt(2))
                    assertEquals(1, rows.getInt(3))
                    // New and empty, which is what a non-destructive migration looks like: the
                    // index is built by scanning, not invented from what was there.
                    assertEquals(0, rows.getInt(4))
                }
            }
        }
    }

    @Test
    fun `a migration run twice fails, which is why there is one database helper`() {
        // Not a wish, a constraint. The statements are plain CREATE TABLE, so running a migration
        // twice throws -- and `SQLiteOpenHelper` synchronises within an instance, not between
        // instances. Five stores each holding their own helper would be five things that could
        // independently decide to migrate, and two of them racing during an upgrade crashes the
        // launch that upgrades.
        //
        // If somebody makes these idempotent and this test starts failing, the right response is
        // not to delete it: it is to ask whether ProtracktorDatabase still needs to be a singleton,
        // and to answer that question deliberately. `docs/review.md` R3.
        memoryDatabase().use { connection ->
            connection.run(SchemaSql.CREATE)
            val again = runCatching { connection.run(SchemaSql.MIGRATIONS.getValue(SchemaSql.VERSION)) }
            assertTrue(
                "the newest migration replayed without error; see the comment above",
                again.isFailure,
            )
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
