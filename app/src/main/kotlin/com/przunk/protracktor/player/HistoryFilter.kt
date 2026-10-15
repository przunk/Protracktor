// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

internal data class HistoryPage<T>(val total: Int, val page: Int, val rows: List<T>)

/** Literal, Unicode-aware matching; no SQLite wildcards or whole-History allocation. */
internal object HistoryFilter {
    fun matches(query: String, title: String, author: String, fileName: String): Boolean {
        val phrase = query.trim()
        return title.contains(phrase, ignoreCase = true) || author.contains(phrase, ignoreCase = true) ||
            fileName.contains(phrase, ignoreCase = true)
    }

    fun <T> page(matches: Sequence<T>, requested: Int): HistoryPage<T> {
        val wanted = requested.coerceAtLeast(0)
        val start = wanted.toLong() * HistoryPages.SIZE
        val selected = ArrayList<T>(HistoryPages.SIZE)
        val last = ArrayList<T>(HistoryPages.SIZE)
        var total = 0
        for (row in matches) {
            if (total % HistoryPages.SIZE == 0) last.clear()
            last.add(row)
            if (total.toLong() >= start && total.toLong() < start + HistoryPages.SIZE) selected.add(row)
            total++
        }
        val page = HistoryPages.clamp(wanted, total)
        return HistoryPage(total, page, if (page == wanted) selected else last)
    }
}
