// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * One download per address, however many ask for it at once (`docs/BACKLOG.md` A55).
 *
 * **Why it exists.** Fetching a folder ahead means a tap can land on a track that is already
 * downloading. Without this the tap starts a second download of the same file; with it the tap
 * waits for the one running, which is also the faster answer.
 *
 * **A download outlives whoever asked for it.** It runs in [scope], not in the caller's coroutine,
 * so leaving a folder -- which cancels the fetching-ahead that asked -- lets a download already in
 * flight finish and land in the cache instead of throwing its bytes away half-read. Only what had
 * not started stops.
 *
 * No Android, so a JVM test holds it.
 */
class SharedFetches<T>(private val scope: CoroutineScope) {
    private val running = mutableMapOf<String, Deferred<T>>()

    /** [block]'s result for [key], joining a run already in flight rather than starting another. */
    suspend fun get(key: String, block: suspend () -> T): T {
        val (deferred, mine) = synchronized(running) {
            running[key]?.let { it to false } ?: (
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        block()
                    } finally {
                        synchronized(running) { running.remove(key) }
                    }
                }.also { running[key] = it } to true
            )
        }
        // Started only once it is in the map: on an immediate dispatcher a quick block could
        // otherwise finish, remove itself, and then be put back as a finished run that every later
        // caller would be handed instead of a fresh download.
        if (mine) deferred.start()
        return deferred.await()
    }

    /** Whether [key] is being fetched right now. */
    fun inFlight(key: String): Boolean = synchronized(running) { key in running }
}
