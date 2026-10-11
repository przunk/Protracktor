// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/** Five progressing seconds or a natural end; loading, pause and seek time do not count. */
internal class ListeningSession(private val nowMs: () -> Long) {
    data class Qualified(val trackId: String, val historyGeneration: Long)

    private var candidate: Qualified? = null
    private var elapsed = 0.0
    private var lastTime = 0L
    private var lastPosition = 0.0
    private var running = false
    private var completionEligible = true

    fun begin(trackId: String, historyGeneration: Long, position: Double, playing: Boolean) {
        candidate = Qualified(trackId, historyGeneration)
        elapsed = 0.0
        completionEligible = true
        baseline(position, playing)
    }

    /** A seek keeps earned time, but reaching the end afterwards does not prove a full listen. */
    fun seeking(position: Double) {
        completionEligible = false
        baseline(position, playing = false)
    }

    /** A seek/subsong boundary retains earned time but discards its position discontinuity. */
    fun baseline(position: Double, playing: Boolean) {
        lastTime = nowMs()
        lastPosition = position
        running = playing
    }

    fun sample(position: Double, playing: Boolean, completed: Boolean = false): Qualified? {
        val now = nowMs()
        if (running) {
            val wall = (now - lastTime).coerceAtLeast(0) / 1000.0
            val audio = (position - lastPosition).coerceAtLeast(0.0)
            elapsed += minOf(wall, audio)
        }
        lastTime = now
        lastPosition = position
        running = playing
        val finishedListen = completed && completionEligible && elapsed > 0.0
        return if (elapsed + 1e-9 >= 5.0 || finishedListen) candidate.also { candidate = null } else null
    }

    /** Clear must not be undone by the same still-playing occurrence. */
    fun forget() {
        candidate = null
        running = false
        elapsed = 0.0
    }
}
