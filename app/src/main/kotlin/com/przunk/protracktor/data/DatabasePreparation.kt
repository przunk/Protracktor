// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the database is being prepared, so the screen can say so (`docs/BACKLOG.md` A60).
 *
 * The owner: *"when the database migrates, the GUI must say something is happening."* Two things
 * take a moment on the first start after an update, on a phone with Modland indexed: a migration,
 * and the catalogues' rows re-decided for a changed list of formats. Both run before the lists have
 * anything to show, and until now the screen simply waited.
 *
 * **A count, not a flag.** The two can overlap -- the re-decision opens the database, and opening it
 * is what migrates -- and a flag set by one and cleared by the other would say "done" while the
 * other still ran. Process-wide, because `SQLiteOpenHelper` runs the migration on whichever thread
 * first asks for the database, and none of them knows it is the one.
 */
object DatabasePreparation {
    private val running = MutableStateFlow(0)

    val active: StateFlow<Int> = running.asStateFlow()

    /** Runs [work] counted as preparation, however it ends. */
    inline fun <T> during(work: () -> T): T {
        begin()
        try {
            return work()
        } finally {
            end()
        }
    }

    @PublishedApi internal fun begin() = running.update { it + 1 }
    @PublishedApi internal fun end() = running.update { (it - 1).coerceAtLeast(0) }
}
