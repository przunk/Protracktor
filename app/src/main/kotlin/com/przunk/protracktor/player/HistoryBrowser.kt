// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** History view mutations only: playback retains its separately captured queue. */
internal class HistoryBrowser(
    scope: CoroutineScope,
    private val browse: MutableStateFlow<BrowseState>,
    read: suspend (String, Int) -> HistoryPage<TrackRef>,
    clear: suspend () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val reads = HistoryReads(scope, read, clear,
        isCurrent = { it.matches(browse.value) },
        show = { shown -> browse.update {
            if (shown.request.matches(it)) it.copy(historyTotal = shown.total, historyPage = shown.page,
                tracks = shown.rows, loading = false, historyReadFailed = false) else it
        } },
        failed = { request, error ->
            browse.update { if (request.matches(it)) it.copy(loading = false, historyReadFailed = true) else it }
            if (request.matches(browse.value)) onFailure(error)
        },
    )

    fun open() {
        browse.update { it.copy(domain = BrowseDomain.HISTORY, historyQuery = "", historyPage = 0,
            historyTotal = 0, tracks = emptyList(), loading = true, historyReadFailed = false) }
        reads.open(HistoryRequest.of(browse.value))
    }

    fun setQuery(query: String) {
        val current = browse.value
        if (current.domain != BrowseDomain.HISTORY || (query == current.historyQuery && !current.historyReadFailed)) return
        browse.update { it.copy(historyQuery = query, historyPage = 0, historyTotal = 0,
            tracks = emptyList(), loading = true, historyReadFailed = false) }
        reads.filter(HistoryRequest.of(browse.value))
    }

    fun showPage(page: Int) {
        val current = browse.value
        if (current.domain != BrowseDomain.HISTORY || current.loading) return
        browse.update { it.copy(loading = true, historyReadFailed = false) }
        reads.turn(page, current.historyTotal, HistoryRequest.of(current))
    }

    fun clearAll(clearAction: suspend () -> Unit, onFailure: (Throwable) -> Unit) {
        val request = HistoryRequest.of(browse.value)
        reads.clearAll(request, clearAction, onFailure = { error ->
            // A failed clear must not leave a cancelled filter's spinner running.
            if (request.matches(browse.value) && browse.value.loading) reads.open(request)
            onFailure(error)
        })
    }
}
