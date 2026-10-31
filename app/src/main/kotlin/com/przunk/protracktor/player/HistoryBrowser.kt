// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** History view mutations only: playback retains its separately captured queue. */
internal class HistoryBrowser(
    scope: CoroutineScope,
    private val browse: MutableStateFlow<BrowseState>,
    private val revision: StateFlow<HistoryWrites.Revision>,
    read: suspend (String, Int) -> HistoryPage<TrackRef>,
    clear: suspend () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private var requestedRevision = revision.value
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

    init {
        scope.launch {
            combine(revision, reads.busy, browse.map { it.domain to it.historyVisit }.distinctUntilChanged()) {
                _, _, _ -> Unit
            }.collect {
                val version = revision.value
                val current = browse.value
                // Let explicit reads/debounce/clear finish; their idle transition retries any newer revision.
                if (!reads.busy.value && !version.clearing && current.domain == BrowseDomain.HISTORY &&
                    version != requestedRevision) {
                    requestRead { reads.turn(current.historyPage, current.historyTotal, HistoryRequest.of(current)) }
                }
            }
        }
    }

    private fun requestRead(action: () -> Unit) {
        requestedRevision = revision.value
        action()
    }

    fun open() {
        browse.update { it.copy(domain = BrowseDomain.HISTORY, historyQuery = "", historyPage = 0,
            historyTotal = 0, tracks = emptyList(), loading = true, historyReadFailed = false) }
        requestRead { reads.open(HistoryRequest.of(browse.value)) }
    }

    fun setQuery(query: String) {
        val current = browse.value
        if (current.domain != BrowseDomain.HISTORY || (query == current.historyQuery && !current.historyReadFailed)) return
        browse.update { it.copy(historyQuery = query, historyPage = 0, historyTotal = 0,
            tracks = emptyList(), loading = true, historyReadFailed = false) }
        requestRead { reads.filter(HistoryRequest.of(browse.value)) }
    }

    fun showPage(page: Int) {
        val current = browse.value
        if (current.domain != BrowseDomain.HISTORY || current.loading) return
        browse.update { it.copy(loading = true, historyReadFailed = false) }
        requestRead { reads.turn(page, current.historyTotal, HistoryRequest.of(current)) }
    }

    fun clearAll(clearAction: suspend () -> Unit, onFailure: (Throwable) -> Unit) {
        val request = HistoryRequest.of(browse.value)
        requestRead {
            reads.clearAll(request, clearAction, onFailure = { error ->
                // A failed clear must not leave a cancelled filter's spinner running.
                if (request.matches(browse.value) && browse.value.loading) requestRead { reads.open(request) }
                onFailure(error)
            })
        }
    }
}
