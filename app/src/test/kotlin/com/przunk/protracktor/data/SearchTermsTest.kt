// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a typed search means, and that SQL means the same thing.
 *
 * `RuleCasesTest` checks [SearchTerms.matchesAny] against the rows the page's checks read too. That
 * covers the *rule* and not the **query built from it**, which is where this half of the app
 * actually does the work — so the SQL is run here against a real SQLite, with the same three names
 * the page's checks use, and the two are compared to each other rather than to a hand-written
 * expectation.
 */
class SearchTermsTest {

    private val names = listOf("space_ninja.mod", "spaceninja.mod", "space-robot.mod")

    /** The rows a query finds, through the SQL `SearchTerms` builds. */
    private fun sqlFinds(query: String): List<String> =
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { it.execute("CREATE TABLE t (title TEXT, author TEXT)") }
            db.prepareStatement("INSERT INTO t VALUES (?, ?)").use { insert ->
                for (name in names) {
                    insert.setString(1, name)
                    insert.setString(2, "4-Mat")
                    insert.execute()
                }
            }
            val (clause, args) = SearchTerms.sqlFor(query, "title", "author")
            db.prepareStatement("SELECT title FROM t WHERE $clause ORDER BY title").use { select ->
                args.forEachIndexed { at, value -> select.setString(at + 1, value) }
                select.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    /** The rows the rule finds, in memory. */
    private fun ruleFinds(query: String): List<String> =
        names.filter { SearchTerms.matchesAny(query, it, "4-Mat") }.sorted()

    @Test
    fun `the SQL finds exactly what the rule finds`() {
        // Including a blank query, which means everything rather than nothing, and a run-on query,
        // which deliberately finds only the run-on name.
        for (query in listOf(
            "ninja", "space ninja", "ninja space", "SPACE NINJA", "spaceninja",
            "space robot", "4-mat ninja", "nothing", "",
        )) {
            assertEquals("'$query'", ruleFinds(query), sqlFinds(query))
        }
    }

    @Test
    fun `two words find the separated name, which is what this was for`() {
        // Typing "space ninja" has to find "space_ninja".
        assertEquals(listOf("space_ninja.mod", "spaceninja.mod"), sqlFinds("space ninja"))
    }

    @Test
    fun `a word typed with a wildcard in it searches for that character`() {
        // `_` is LIKE's own single-character wildcard, so an unescaped `_` would match every name
        // here. A search box that silently means something else is worse than no search box.
        assertEquals(listOf("space_ninja.mod"), sqlFinds("space_ninja"))
        assertTrue(sqlFinds("%").isEmpty())
    }

    @Test
    fun `a blank query is every row rather than none`() {
        // How "everything on the Commodore 64" is asked for: a platform scope and an empty field.
        assertEquals(names.sorted(), sqlFinds(""))
        assertEquals(names.sorted(), sqlFinds("   "))
    }

    @Test
    fun `one group of arguments per word, one argument per column`() {
        val (clause, args) = SearchTerms.sqlFor("space ninja", "title", "author")
        assertEquals(4, args.size)
        assertEquals("(title LIKE ? ESCAPE '!' OR author LIKE ? ESCAPE '!') AND " +
            "(title LIKE ? ESCAPE '!' OR author LIKE ? ESCAPE '!')", clause)
    }
}
