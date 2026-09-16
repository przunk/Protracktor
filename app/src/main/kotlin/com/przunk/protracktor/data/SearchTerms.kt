// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * What a typed search means.
 *
 * **Every word, anywhere, in any order** — rather than the whole query as one substring, which is
 * what this was until 2026-09-16 and which fails on the way tracker files are actually named.
 * `space ninja` did not find `space_ninja`, because one character in the middle is a separator
 * rather than a space. Owner, 2026-09-16.
 *
 * Splitting the query is the whole rule and it covers more than it looks:
 *
 * | typed | file | why |
 * | --- | --- | --- |
 * | `space ninja` | `space_ninja` | both words are in it |
 * | `space ninja` | `spaceninja` | still both, and adjacent |
 * | `ninja space` | `space-ninja` | order is not part of the rule |
 * | `space ninja` | `Space Ninja` | case is not either |
 *
 * **What it deliberately does not do is normalise the file's name**, which would be the other half:
 * typing `spaceninja` still misses `space_ninja`, because that needle is not in that haystack.
 * Doing it properly means stripping separators from the *stored* side too, and that was measured
 * before it was rejected — on 500,000 catalogue rows, the worst case (a search that matches
 * nothing, which is every search still being typed) goes from **36 ms to 217 ms** in SQL, or costs
 * a normalised column and about twelve megabytes. Splitting the query costs **nothing**: 36.5 ms to
 * 36.8 ms for two words, because the extra clause short-circuits over the same scan.
 *
 * **No Android imports on purpose**, like [SongLengths] beside it: the rule is shared with
 * `web/src/rules.js` through `docs/rules/queue-cases.tsv`, and a rule that cannot be run in a test
 * here is a rule that gets decided twice.
 */
object SearchTerms {

    /**
     * The words to look for, lower-cased, in the order typed.
     *
     * Empty for a blank query, and that means "everything" rather than "nothing" — a scope with no
     * words typed is how "every tune on the Commodore 64" is asked for, and the caller's limit is
     * what bounds it.
     */
    fun of(query: String): List<String> =
        query.trim().lowercase().split(' ', '\t', '\n').filter { it.isNotEmpty() }

    /** Whether [text] satisfies [query] by this rule. The in-memory half of what the SQL does. */
    fun matches(query: String, text: String): Boolean {
        val haystack = text.lowercase()
        return of(query).all { haystack.contains(it) }
    }

    /**
     * The same rule as SQL: a `WHERE` fragment and the arguments to bind to it.
     *
     * Built here rather than at the two call sites, because `search` and `countMatches` ask the
     * same question about the same table and a search whose count disagrees with its results is a
     * screen that argues with itself.
     *
     * One `(col LIKE ? OR col LIKE ?)` group per word, `AND`ed — so a word may be satisfied by any
     * column, and every word has to be satisfied by something. **A blank query is `1`**, which is
     * every row: a scope with nothing typed is how "everything on the Commodore 64" is asked for,
     * and the caller's limit is what bounds it.
     *
     * `%`, `_` and the escape character itself are escaped, so somebody searching for `_` finds an
     * underscore rather than everything. A search box that silently means something else is worse
     * than no search box.
     */
    fun sqlFor(query: String, vararg columns: String): Pair<String, Array<String>> {
        val words = of(query)
        if (words.isEmpty() || columns.isEmpty()) return "1" to emptyArray()
        val group = columns.joinToString(" OR ") { "$it LIKE ? ESCAPE '!'" }
        val clause = words.joinToString(" AND ") { "($group)" }
        val args = words.flatMap { word -> columns.map { like(word) } }.toTypedArray()
        return clause to args
    }

    private fun like(word: String) =
        "%" + word.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

    /**
     * Whether any of [texts] satisfies [query] **as a whole**.
     *
     * Not `texts.any { matches(query, it) }`, and the difference is the point: a tune whose title
     * holds one word and whose author holds the other is a hit. `4-Mat` searched for as
     * `4-mat space` should find `space.mod` by 4-Mat, and would not if each field had to carry
     * every word by itself.
     */
    fun matchesAny(query: String, vararg texts: String): Boolean {
        val haystacks = texts.map { it.lowercase() }
        return of(query).all { word -> haystacks.any { it.contains(word) } }
    }
}
