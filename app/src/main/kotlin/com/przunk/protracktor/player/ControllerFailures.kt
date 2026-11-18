// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * **The one failure path of the controller's work** (RV-B014).
 *
 * The controller launches its work -- saving playlists, browsing, restoring the session -- on one
 * scope, and most of those blocks catch nothing. Without a handler an exception from any of them
 * reached the thread's default handler and ended the app: a full disk while a playlist was saved,
 * or a migration that failed, which then failed again at every start. History, search and a few
 * others catch and say something; this is what everything else falls back to.
 *
 * An [Exception] is reported and the app carries on; the supervisor keeps the scope alive for the
 * next press. An [Error] -- out of memory, a broken invariant of the runtime -- is not something to
 * carry on from, and goes to the thread's default handler as it did before.
 */
internal object ControllerFailures {
    fun scope(report: (Exception) -> Unit): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + handler(report))

    fun handler(report: (Exception) -> Unit): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, failure ->
            when (failure) {
                is Exception -> report(failure)
                else -> Thread.currentThread().let { it.uncaughtExceptionHandler?.uncaughtException(it, failure) ?: throw failure }
            }
        }

    /** Whether [failure] is the storage's: the database or the file system, where "full" is the usual cause. */
    fun isStorage(failure: Exception): Boolean =
        failure is android.database.SQLException || failure is java.io.IOException
}
