// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.engine

/**
 * When a native player may be freed (C140, 2026-11-11).
 *
 * A seek runs on a worker thread and can take seconds -- an emulator reaches a place by playing up
 * to it -- while the tune can be left meanwhile: the previous one swiped to, say. Closing then
 * deleted the native player under the running seek, which called into an object half destroyed
 * (`__cxa_pure_virtual` in `nativeSeek`) and took the app down. Here a close that finds work
 * running is only noted, and the last piece of work to finish carries it out, once.
 *
 * Pure, so a JVM test with real threads holds it; [release] is the native free.
 */
internal class NativeLifetime(private val release: () -> Unit) {
    private val lock = Any()
    private var running = 0
    private var closeWaiting = false
    private var closed = false

    /** Whether the player is gone or going: nothing new may start on it. */
    val ending: Boolean get() = synchronized(lock) { closed || closeWaiting }

    /** Runs [work] with the player kept alive throughout, or not at all if it is ending. */
    fun during(work: () -> Unit) {
        synchronized(lock) {
            if (closed || closeWaiting) return
            running++
        }
        try {
            work()
        } finally {
            val releaseNow = synchronized(lock) {
                running--
                (running == 0 && closeWaiting && !closed).also { if (it) closed = true }
            }
            if (releaseNow) release()
        }
    }

    /** Frees the player now, or when the work running on it has finished. */
    fun close() {
        synchronized(lock) {
            if (closed || closeWaiting) return
            if (running > 0) { closeWaiting = true; return }
            closed = true
        }
        release()
    }
}
