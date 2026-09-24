// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.SupportedFormats
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each catalogue row knows its platform (`docs/STATUS.md` C89) -- and the stored column says what
 * `Platforms.forCatalogueRow` says, for every name and directory the table knows.
 *
 * Against a real SQLite, because the column is written by an `UPDATE` built from the same maps the
 * function reads, and a `CASE` that drifted from the function would file music under the wrong
 * machine with nothing on screen to show it.
 */
class PlatformColumnTest {

    private fun database(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { connection ->
            connection.createStatement().use { statement -> SchemaSql.CREATE.forEach(statement::execute) }
            connection.createStatement().use {
                it.execute("INSERT INTO catalogues (id, display_name) VALUES ('modland', 'Modland'), ('asma', 'ASMA'), ('unexotica', 'UnExoticA')")
            }
        }

    private data class Row(val catalogue: String, val format: String, val title: String)

    /** Stored with no platform, as a row migrated to version 20 is before its first re-decision. */
    private fun Connection.add(rows: List<Row>) {
        prepareStatement(
            "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size, ext, pre, playable) " +
                "VALUES (?, ?, ?, '', ?, 1, ?, ?, 1)"
        ).use { insert ->
            rows.forEachIndexed { i, row ->
                insert.setString(1, row.catalogue)
                insert.setString(2, "${row.format}/$i/${row.title}")
                insert.setString(3, row.format)
                insert.setString(4, row.title)
                insert.setString(5, SupportedFormats.extensionOf(row.title))
                insert.setString(6, SupportedFormats.prefixOf(row.title))
                insert.execute()
            }
        }
    }

    private fun Connection.redecide() {
        val (sql, arguments) = platformUpdate()
        prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { i, value -> statement.setString(i + 1, value) }
            statement.execute()
        }
    }

    private fun Connection.platforms(): Map<String, String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT path, platform FROM catalogue_tracks").use { rows ->
                buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
            }
        }

    @Test
    fun `the stored platform is the function's, for every name and directory in the table`() {
        val rows = buildList {
            for (platform in Platforms.all) {
                // Each directory, with a name that belongs to no platform, and with one that does.
                for (format in platform.catalogueFormats) {
                    add(Row("modland", format.uppercase(), "tune.zzz"))
                    add(Row("modland", format, "tune.sap"))
                }
                // Each name, as an extension and as a prefix, under a directory nobody maps.
                for (name in platform.names) {
                    add(Row("modland", "Unmapped", "tune.$name"))
                    add(Row("modland", "Unmapped", "$name.tune"))
                }
            }
            add(Row("asma", "Composers", "Bonio.sap"))
            add(Row("asma", "Games", "anything.mod"))
            add(Row("unexotica", "Turrican", "mdat.turrican"))
            add(Row("modland", "Unmapped", "readme"))
            add(Row("modland", "Unmapped", "tune.zzz"))
        }
        database().use { db ->
            db.add(rows)
            db.redecide()
            val stored = db.platforms()
            rows.forEachIndexed { i, row ->
                val expected = Platforms.forCatalogueRow(row.catalogue, row.format, row.title)?.id.orEmpty()
                assertEquals("$row", expected, stored["${row.format}/$i/${row.title}"])
            }
        }
    }

    @Test
    fun `the Atari 8-bit filter finds ASMA's tunes, and Modland's`() {
        // The owner's search: Atari 8-bit, "przunk" -- ASMA's `Composers/przunk`, never matched by
        // directory because ASMA's directories are sections.
        database().use { db ->
            db.add(listOf(
                Row("asma", "Composers", "Bonio.sap"),
                Row("modland", "Slight Atari Player", "elsewhere.sap"),
                Row("modland", "Protracker", "amiga.mod"),
            ))
            db.redecide()
            val (clause, arguments) = platformClause(setOf("atari-8bit"))
            val found = db.prepareStatement("SELECT title FROM catalogue_tracks WHERE playable = 1$clause ORDER BY title").use { select ->
                arguments.forEachIndexed { i, value -> select.setString(i + 1, value) }
                select.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
            assertEquals(listOf("Bonio.sap", "elsewhere.sap"), found)
        }
    }
}
