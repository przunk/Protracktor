// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * When a folder's tracks may be fetched before anybody asks for them (`docs/BACKLOG.md` A55, D4).
 *
 * **Nobody pressed anything**, so this spends data on the user's behalf -- the objection A46 made to
 * downloading at first launch. So the default is an unmetered network only, and the setting says so
 * in the words a person looks for: Wi-Fi.
 */
enum class CacheAhead(val stored: String) {
    WIFI_ONLY("wifi"),
    ALWAYS("always"),
    OFF("off");

    /** Whether fetching ahead may run on a network that is [metered] (mobile data, a hotspot). */
    fun allows(metered: Boolean): Boolean = when (this) {
        WIFI_ONLY -> !metered
        ALWAYS -> true
        OFF -> false
    }

    companion object {
        val DEFAULT = WIFI_ONLY

        fun fromStored(value: String?): CacheAhead = entries.firstOrNull { it.stored == value } ?: DEFAULT
    }
}

/**
 * What to fetch ahead in an opened folder, and how (`docs/PLAN_ROUND_13.md` A55).
 *
 * No Android and no network here: the choice of *which* tracks is [plan], the *how* is [run], and
 * both are held by JVM tests. The controller supplies the fetch, the network check and the rows.
 */
object FolderPrefetch {

    /** How many tracks are fetched at once. The tapped track is fetched outside these. */
    const val PARALLEL = 3

    /**
     * Where a folder stops (D6). A folder bigger than this would otherwise push a fifth of the
     * 512 MB cache out to make room for tracks nobody has heard, including tracks played often.
     */
    const val FOLDER_BUDGET_BYTES: Long = 100L * 1024 * 1024

    /** One row as the plan sees it: its address and what the index says it weighs. */
    data class Candidate(val url: String, val sizeBytes: Long)

    /**
     * The tracks to fetch, in the folder's order: those not already on the phone, until the next
     * one would take the folder past [budgetBytes].
     *
     * **A stop, not a skip.** Skipping a large file to fit a smaller one after it would fetch the
     * folder out of order, and the order is the one the user sees and plays down.
     * Tracks already cached cost nothing and are not counted.
     */
    fun plan(
        tracks: List<Candidate>,
        cached: Set<String>,
        budgetBytes: Long = FOLDER_BUDGET_BYTES,
    ): List<String> {
        val wanted = mutableListOf<String>()
        var spent = 0L
        for (track in tracks) {
            if (track.url in cached) continue
            val size = track.sizeBytes.coerceAtLeast(0L)
            if (spent + size > budgetBytes) break
            spent += size
            wanted += track.url
        }
        return wanted
    }

    /**
     * Fetches [urls] in order, [parallel] at a time, each once.
     *
     * - **A failure is not retried.** Nobody asked for this track; tapping it fetches and reports.
     * - **[mayContinue] is asked before each start**, so leaving Wi-Fi stops the rest.
     * - **Cancelling stops what has not started.** Whether a fetch already running completes is the
     *   fetch's business -- the controller's goes through a shared download that outlives its
     *   waiter, so the bytes are not thrown away half-read.
     *
     * [onStart] and [onDone] are told about each track, for the spinner and the mark.
     */
    suspend fun run(
        urls: List<String>,
        fetch: suspend (String) -> Boolean,
        mayContinue: () -> Boolean = { true },
        onStart: (String) -> Unit = {},
        onDone: (String, Boolean) -> Unit = { _, _ -> },
        parallel: Int = PARALLEL,
    ) = coroutineScope {
        val queue = ArrayDeque(urls)
        // Several workers take from one queue, on whatever dispatcher the caller runs, so the
        // handover is locked.
        fun next(): String? = synchronized(queue) { queue.removeFirstOrNull() }
        repeat(parallel.coerceAtLeast(1)) {
            launch {
                while (true) {
                    if (!mayContinue()) break
                    val url = next() ?: break
                    onStart(url)
                    val ok = runCatching { fetch(url) }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        false
                    }
                    onDone(url, ok)
                }
            }
        }
    }
}
