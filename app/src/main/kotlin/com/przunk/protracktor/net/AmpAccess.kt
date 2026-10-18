// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

/** One permission epoch owns a transfer; off/on cannot resurrect a previous request. */
internal class AmpAccess(initiallyEnabled: Boolean = true) {
    private val lock = Any()
    private var epoch = 0L
    private var active = initiallyEnabled
    private val transfers = mutableSetOf<() -> Unit>()

    val enabled: Boolean get() = synchronized(lock) { active }
    val revision: Long get() = synchronized(lock) { epoch }
    fun capture(): Long? = synchronized(lock) { epoch.takeIf { active } }
    fun allows(ticket: Long): Boolean = synchronized(lock) { active && epoch == ticket }

    fun setEnabled(enabled: Boolean): Boolean {
        val cancelled = synchronized(lock) {
            if (active == enabled) return false
            active = enabled
            epoch++
            transfers.toList().also { transfers.clear() }
        }
        cancelled.forEach { runCatching { it() } }
        return true
    }

    fun attach(ticket: Long, cancel: () -> Unit): Boolean = synchronized(lock) {
        if (!active || epoch != ticket) false else transfers.add(cancel)
    }

    fun detach(cancel: () -> Unit) { synchronized(lock) { transfers.remove(cancel) } }
}
