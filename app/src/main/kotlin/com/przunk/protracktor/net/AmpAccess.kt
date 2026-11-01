// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

/** Transfer tickets are bounded by the build gate, including attempts to re-enable retired work. */
internal class AmpAccess(initiallyEnabled: Boolean = true) {
    private val lock = Any()
    private var epoch = 0L
    private var active = initiallyEnabled && Amp.ENABLED
    private val transfers = mutableSetOf<() -> Unit>()

    val enabled: Boolean get() = synchronized(lock) { active }
    val revision: Long get() = synchronized(lock) { epoch }
    fun capture(): Long? = synchronized(lock) { epoch.takeIf { active } }
    fun allows(ticket: Long): Boolean = synchronized(lock) { active && epoch == ticket }

    fun setEnabled(enabled: Boolean): Boolean {
        val available = enabled && Amp.ENABLED
        val cancelled = synchronized(lock) {
            if (active == available) return false
            active = available
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
