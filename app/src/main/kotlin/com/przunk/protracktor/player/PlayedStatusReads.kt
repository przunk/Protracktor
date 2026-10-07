// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Reads only the shown lists, not all History; stale/cancelled reads never restore cleared marks. */
internal class PlayedStatusReads(
    scope: CoroutineScope,
    ids: Flow<Set<String>>,
    revision: StateFlow<HistoryWrites.Revision>,
    query: suspend (Set<String>) -> Set<String>,
    show: (Set<String>) -> Unit,
    failed: (Throwable) -> Unit,
) {
    init {
        scope.launch {
            var shown = emptySet<String>()
            combine(ids.distinctUntilChanged(), revision) { wanted, version -> wanted to version }
                .collectLatest { (wanted, version) ->
                    shown = if (version.clearing) emptySet() else shown.intersect(wanted)
                    show(shown)
                    if (version.clearing || wanted.isEmpty()) return@collectLatest
                    try {
                        val found = query(wanted)
                        currentCoroutineContext().ensureActive()
                        if (version == revision.value) {
                            shown = found
                            show(shown)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failed(error)
                    }
                }
        }
    }
}
