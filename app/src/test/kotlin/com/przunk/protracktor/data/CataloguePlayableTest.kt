// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import com.przunk.protracktor.player.SupportedFormats
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The index stops being a function of what we can play (`docs/ROADMAP_FORMATS.md` step 0).
 *
 * The change is worth a test of its own because its failure mode is quiet in both directions: a
 * missed filter shows the owner files the app cannot open, and a wrong recompute empties Browse.
 * Run against a real SQLite, since the statement that does the work is SQL and not Kotlin.
 */
class CataloguePlayableTest {

    private fun database(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            connection.createStatement().use { statement ->
                SchemaSql.CREATE.forEach(statement::execute)
            }
        }

    /** Writes a row the way `replaceIndex` does, verdict included. */
    private fun Connection.add(title: String) {
        createStatement().use {
            it.execute("INSERT OR IGNORE INTO catalogues (id, display_name) VALUES ('modland', 'Modland')")
        }
        val ext = SupportedFormats.extensionOf(title)
        val pre = SupportedFormats.prefixOf(title)
        val playable = ext in SupportedFormats.extensions || pre in SupportedFormats.prefixes
        prepareStatement(
            "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size, ext, pre, playable) " +
                "VALUES ('modland', ?, 'Protracker', '4-Mat', ?, 1, ?, ?, ?)"
        ).use {
            it.setString(1, "Protracker/4-Mat/$title")
            it.setString(2, title)
            it.setString(3, ext)
            it.setString(4, pre)
            it.setInt(5, if (playable) 1 else 0)
            it.execute()
        }
    }

    private fun Connection.playable(): List<String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT title FROM catalogue_tracks WHERE playable = 1 ORDER BY title")
                .use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }

    /** The recompute, exactly as `CatalogueStore.refreshPlayable` writes it. */
    private fun Connection.recompute(extensions: Collection<String>, prefixes: Collection<String>) {
        val ext = extensions.joinToString(",") { "'$it'" }
        val pre = prefixes.joinToString(",") { "'$it'" }
        createStatement().use {
            it.execute(
                "UPDATE catalogue_tracks SET playable = " +
                    "(CASE WHEN ext IN ($ext) OR pre IN ($pre) THEN 1 ELSE 0 END)"
            )
        }
    }

    @Test
    fun `a row the build cannot play is stored and not offered`() {
        database().use { db ->
            db.add("tune.mod")
            db.add("tune.zzzznope")
            // Both are in the archive and both are in the table -- that is the point of the change.
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM catalogue_tracks").use { rows ->
                    rows.next()
                    assertEquals("both rows are kept", 2, rows.getInt(1))
                }
            }
            assertEquals(listOf("tune.mod"), db.playable())
        }
    }

    @Test
    fun `an Amiga prefix name counts, and a bare name does not`() {
        database().use { db ->
            db.add("mod.chipfunk")      // prefix, the Amiga convention
            db.add("readme")            // no dot at all
            assertEquals(listOf("mod.chipfunk"), db.playable())
        }
    }

    @Test
    fun `adding a format re-decides stored rows without fetching anything`() {
        database().use { db ->
            db.add("tune.zzzznope")
            assertTrue("not playable to begin with", db.playable().isEmpty())

            // The whole of what item 0 bought: a format arrives, and the answer is already here.
            db.recompute(SupportedFormats.extensions + "zzzznope", SupportedFormats.prefixes)
            assertEquals(listOf("tune.zzzznope"), db.playable())
        }
    }

    @Test
    fun `removing a format takes its rows out of view and leaves them in the table`() {
        database().use { db ->
            db.add("tune.mod")
            db.recompute(SupportedFormats.extensions - "mod", SupportedFormats.prefixes - "mod")
            assertTrue("no longer offered", db.playable().isEmpty())
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM catalogue_tracks").use { rows ->
                    rows.next()
                    // Still there, so putting the format back costs nothing again.
                    assertEquals(1, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `only a partial index can be stale`() {
        val whole = CatalogueSummary(
            id = "modland", displayName = "Modland", trackCount = 10, indexedAt = 1L,
            backends = "old", complete = true,
        )
        val partial = whole.copy(complete = false)
        assertFalse("a whole index answers a format change locally", whole.isStale("new"))
        assertTrue("a partial one is genuinely missing rows", partial.isStale("new"))
        assertFalse("and is not stale when nothing moved", partial.isStale("old"))
    }
}
