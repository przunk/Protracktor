// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing at launch reads half a million rows to learn that it has nothing to do.
 *
 * `pruneUnknownCatalogues` runs at every start-up and used to begin with
 * `DELETE FROM catalogue_tracks WHERE catalogue_id NOT IN (…)`. A negation indexes nothing, and
 * since v15 the two browse indexes are partial (`WHERE playable = 1`), so they could not serve it
 * even if it were positive: a full scan, inside a write transaction, on the one connection every
 * other read at launch is queueing behind. Measured at **55.6 ms on 516,000 rows on a desktop**,
 * and a phone's storage is several times slower (`docs/BACKLOG.md` A48).
 *
 * The same question asked of the `catalogues` table — one row per catalogue — is answered from an
 * index in no measurable time. This is that rule, as a query plan rather than as a stopwatch, so it
 * holds on whatever machine runs the suite.
 */
class LaunchDoesNotScanTheIndexTest {

    private val known = listOf("modland", "asma", "modarchive", "unexotica")

    private fun database(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { connection ->
            connection.createStatement().use { statement ->
                SchemaSql.CREATE.forEach(statement::execute)
                statement.execute(
                    "INSERT INTO catalogues (id, display_name, track_count) " +
                        "VALUES ('modland', 'Modland', 3)"
                )
                statement.execute(
                    "INSERT INTO catalogue_tracks (catalogue_id, path, format, author, title, size) " +
                        "VALUES ('modland', 'Protracker/4-Mat/a.mod', 'Protracker', '4-Mat', 'a.mod', 40000)"
                )
            }
        }

    private fun plan(sql: String): String =
        database().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("EXPLAIN QUERY PLAN $sql").use { rows ->
                    buildString { while (rows.next()) append(rows.getString("detail")).append('\n') }
                }
            }
        }

    private val placeholders = known.joinToString(",") { "'$it'" }

    @Test
    fun `the question the launch asks does not touch the index table`() {
        val plan = plan("SELECT EXISTS(SELECT 1 FROM catalogues WHERE id NOT IN ($placeholders))")
        assertFalse("it reads catalogue_tracks: $plan", plan.contains("catalogue_tracks"))
        assertTrue("it should read catalogues: $plan", plan.contains("catalogues"))
    }

    @Test
    fun `and the delete it guards would have scanned it`() {
        // The reason the guard exists, kept as evidence rather than as a claim in a comment. If a
        // future index makes this plan stop scanning, the guard can go — and this test will say so
        // by failing.
        val plan = plan("DELETE FROM catalogue_tracks WHERE catalogue_id NOT IN ($placeholders)")
        assertTrue("no longer a scan: $plan", plan.contains("SCAN"))
    }
}
