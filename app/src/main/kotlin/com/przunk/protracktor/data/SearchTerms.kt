// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.text.Normalizer

/**
 * What a typed search means.
 *
 * **Every word, anywhere, in any order** — rather than the whole query as one substring, which
 * fails on the way tracker files are named: `space ninja` does not occur in `space_ninja`, one
 * character in the middle being a separator rather than a space.
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
 * **Accents do not count either** (`docs/BACKLOG.md` A53): `michal` finds `Michał`, and `akes
 * lekhorna` finds `Åkes lekhörna`. Both sides go through [fold]. SQLite's `LIKE` folds case for
 * ASCII only and Android's SQLite takes no custom function, so the stored side is folded when a
 * row is written, into a `folded` column that is filled **only for rows with anything outside
 * ASCII** ([foldedOrNull]) -- 9 of Modland's 515,509 rows, and the titles a library scan reads out
 * of files. An ASCII row is matched by its own columns exactly as before.
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
        fold(query.trim()).split(' ', '\t', '\n').filter { it.isNotEmpty() }

    /**
     * Text as search compares it: lower case, compatibility-decomposed (NFKD), combining marks
     * dropped, and the letters Unicode does not decompose mapped by hand.
     *
     * NFKD rather than NFD because Modland holds `Ｎerual Ｔtoille` in fullwidth letters, which only
     * the compatibility form turns into `nerual ttoille`. `ł`, `đ`, `ø` and friends carry no
     * combining mark at all -- `ł` is a letter of its own, not `l` plus a stroke -- which is what
     * the table is for. `web/src/rules.js` `foldText` is the same function; `docs/rules/queue-cases.tsv`
     * holds them to it.
     */
    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
        val out = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            out.append(UNDECOMPOSED[c] ?: c.toString())
        }
        return out.toString()
    }

    /** Letters with no combining mark to drop, and what they fold to. The same table as `rules.js`. */
    private val UNDECOMPOSED: Map<Char, String> = mapOf(
        'ł' to "l", 'đ' to "d", 'ø' to "o", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'þ' to "th",
        'ħ' to "h", 'ı' to "i",
    )

    /**
     * What a row stores in its `folded` column: the folded [texts] joined by line breaks, or
     * **null when every one of them is plain ASCII** -- which is nearly every row, and needs no
     * second copy because its own columns already match a folded query.
     */
    fun foldedOrNull(vararg texts: String): String? =
        if (texts.all { text -> text.all { it.code < 0x80 } }) null
        else texts.joinToString("\n") { fold(it) }

    /** Whether [text] satisfies [query] by this rule. The in-memory half of what the SQL does. */
    fun matches(query: String, text: String): Boolean {
        val haystack = fold(text)
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
    fun sqlFor(query: String, vararg columns: String, sparse: String? = null): Pair<String, Array<String>> {
        val words = of(query)
        if (words.isEmpty() || columns.isEmpty()) return "1" to emptyArray()
        // [sparse] is a column that is NULL on nearly every row -- `folded` (A53). `LIKE` is
        // evaluated even on NULL, and asking first is measured: on Modland's 515,509 rows a search
        // that matches nothing went 56.6 ms -> 71.1 ms with the column plain, 62.0 ms guarded.
        val searched = columns.map { "$it LIKE ? ESCAPE '!'" } +
            listOfNotNull(sparse?.let { "($it IS NOT NULL AND $it LIKE ? ESCAPE '!')" })
        val group = searched.joinToString(" OR ")
        val clause = words.joinToString(" AND ") { "($group)" }
        val args = words.flatMap { word -> searched.map { like(word) } }.toTypedArray()
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
        val haystacks = texts.map { fold(it) }
        return of(query).all { word -> haystacks.any { it.contains(word) } }
    }
}
