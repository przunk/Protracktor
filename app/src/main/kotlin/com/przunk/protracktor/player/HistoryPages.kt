// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * History a hundred at a time (the owner, 2026-09-26).
 *
 * History used to forget all but the last 500 tunes; it keeps every one now, and a list of
 * thousands is read from the database a page at a time, with the way to the next page out of the list's scroll. Page
 * 0 is the newest. The page's `pageOf`/`pageRange` in `rules.js`; `docs/rules/queue-cases.tsv`
 * holds both to the same answers.
 */
object HistoryPages {
    const val SIZE = 100

    /** How many pages [total] tunes make: at least one, so an empty history still has a page 0. */
    fun count(total: Int): Int = maxOf(1, (total + SIZE - 1) / SIZE)

    /** [page] where it exists; the last one where the list has since shrunk under it. */
    fun clamp(page: Int, total: Int): Int = page.coerceIn(0, count(total) - 1)

    /** Where [page] starts, counted from zero: the `OFFSET` of its read. */
    fun offset(page: Int, total: Int): Int = clamp(page, total) * SIZE

    /** The positions on [page], counted from one, for "101–200 of 734"; `0..-1` when there are none. */
    fun range(page: Int, total: Int): IntRange {
        if (total == 0) return 0..-1
        val first = clamp(page, total) * SIZE + 1
        return first..minOf(first + SIZE - 1, total)
    }
}
