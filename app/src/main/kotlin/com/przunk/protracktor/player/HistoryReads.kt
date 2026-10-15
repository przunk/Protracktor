// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val read: suspend (String, Int) -> HistoryPage<T>,
    private val clear: suspend () -> Unit,
    private val isCurrent: (HistoryRequest) -> Boolean,
    private val show: (Shown<T>) -> Unit,
    private val failed: (HistoryRequest, Throwable) -> Unit,
) {
    data class Shown<T>(val total: Int, val page: Int, val rows: List<T>, val request: HistoryRequest = HistoryRequest())

    private val newest = AtomicLong(0)
    private var running: Job? = null

    fun open(request: HistoryRequest = HistoryRequest()) = load(request, 0, false)
    fun filter(request: HistoryRequest) = load(request, 0, true)
    fun turn(wanted: Int, total: Int, request: HistoryRequest = HistoryRequest()) =
        load(request, HistoryPages.clamp(wanted, total), false)

    private fun owns(ticket: Long, request: HistoryRequest) = newest.get() == ticket && isCurrent(request)

    private fun load(request: HistoryRequest, page: Int, debounce: Boolean) = launchRequest(request) { ticket ->
        if (debounce) delay(200)
        if (!owns(ticket, request)) return@launchRequest
        val result = read(request.query, page)
        if (owns(ticket, request)) show(Shown(result.total, result.page, result.rows, request))
    }

    fun clearAll(
        request: HistoryRequest = HistoryRequest(),
        clearAction: suspend () -> Unit = clear,
        onFailure: (Throwable) -> Unit = { throw it },
    ) = launchRequest(request, checkAtStart = false) { ticket ->
        try {
            clearAction()
            if (owns(ticket, request)) show(Shown(0, 0, emptyList(), request))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(error)
        }
    }

    private fun launchRequest(request: HistoryRequest, checkAtStart: Boolean = true, work: suspend (Long) -> Unit): Job {
        val ticket = newest.incrementAndGet()
        running?.cancel()
        // Assign before Main.immediate can complete or reenter.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!checkAtStart || owns(ticket, request)) work(ticket)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (owns(ticket, request)) failed(request, error)
            } finally {
                if (newest.get() == ticket) running = null
            }
        }
        running = job
        job.start()
        return job
    }
}

internal data class HistoryRequest(val query: String = "", val visit: Long = 0) {
    fun matches(state: BrowseState) = state.domain == BrowseDomain.HISTORY &&
        state.historyQuery.trim() == query && state.historyVisit == visit

    companion object {
        fun of(state: BrowseState) = HistoryRequest(state.historyQuery.trim(), state.historyVisit)
    }
}
