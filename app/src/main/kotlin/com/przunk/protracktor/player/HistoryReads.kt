// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * History's reads -- opening it, turning a page, clearing it -- with **only the newest one allowed
 * to reach the screen** (`docs/review-2026-09-28.md` F1).
 *
 * A read of a page takes a moment, and since write-ahead logging (A64) it runs beside a clear rather
 * than after it. A read of page 0 that began before *Clear history* and ended after it put the
 * cleared tunes back on screen, over an empty database; one begun by opening History put back their
 * count too. Cancelling the read is not enough -- one that has its rows already reaches the screen
 * all the same -- so every request takes a number, and a read shows what it found only if no newer
 * request has been made since it began.
 *
 * Kept apart from the controller, which needs Android, so a test can hold a read open, clear, and
 * let the read finish.
 */
internal class HistoryReads<T>(
    private val scope: CoroutineScope,
    private val count: suspend () -> Int,
    private val page: suspend (offset: Int, limit: Int) -> List<T>,
    private val clear: suspend () -> Unit,
    private val show: (Shown<T>) -> Unit,
) {
    /** What History should show: how many tunes it holds, which page, and that page's tunes. */
    data class Shown<T>(val total: Int, val page: Int, val rows: List<T>)

    private val newest = AtomicLong(0)
    private var running: Job? = null

    /** History opened: its count, and its newest page. */
    fun open() = request { ticket ->
        val total = count()
        val rows = page(HistoryPages.offset(0, total), HistoryPages.SIZE)
        if (newest.get() == ticket) show(Shown(total, 0, rows))
    }

    /** Page [wanted] of the [total] already counted. */
    fun turn(wanted: Int, total: Int) = request { ticket ->
        val shown = HistoryPages.clamp(wanted, total)
        val rows = page(HistoryPages.offset(shown, total), HistoryPages.SIZE)
        if (newest.get() == ticket) show(Shown(total, shown, rows))
    }

    /** Everything forgotten, and nothing already on its way allowed to bring any of it back. */
    fun clearAll() = request { ticket ->
        clear()
        if (newest.get() == ticket) show(Shown(0, 0, emptyList()))
    }

    private fun request(read: suspend (Long) -> Unit): Job {
        val ticket = newest.incrementAndGet()
        running?.cancel()
        return scope.launch { read(ticket) }.also { running = it }
    }
}
