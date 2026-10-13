// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object RecentSearches {
    const val RETAINED = 20
    const val SHOWN = 3

    fun clean(queries: List<String>): List<String> = queries.fold(emptyList()) { kept, query ->
        val trimmed = query.trim()
        if (trimmed.isBlank() || kept.size == RETAINED || kept.any { it.equals(trimmed, ignoreCase = true) }) kept
        else kept + trimmed
    }

    fun record(queries: List<String>, submitted: String): List<String> =
        if (submitted.isBlank()) queries else clean(listOf(submitted) + queries)

    fun suggestions(queries: List<String>, typed: String): List<String> = queries
        .filter { it.startsWith(typed.trimStart(), ignoreCase = true) }.take(SHOWN)
}

/** Kept outside Browse snapshots and playback History; typing is never a submission. */
internal class RecentSearchHistory(private val store: Store) {
    interface Store {
        fun read(): List<String>
        fun write(queries: List<String>)
    }
    private val stored = MutableStateFlow(RecentSearches.clean(store.read()))
    val queries: StateFlow<List<String>> get() = stored

    fun record(query: String) {
        val next = RecentSearches.record(stored.value, query)
        if (next == stored.value) return
        store.write(next)
        stored.value = next
    }
}
