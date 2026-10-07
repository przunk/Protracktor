// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/** Five seconds of progressing audio, never time spent loading, paused or seeking. */
internal class ListeningSession(private val nowMs: () -> Long) {
    data class Qualified(val trackId: String, val historyGeneration: Long)

    private var candidate: Qualified? = null
    private var elapsed = 0.0
    private var lastTime = 0L
    private var lastPosition = 0.0
    private var running = false

    fun begin(trackId: String, historyGeneration: Long, position: Double, playing: Boolean) {
        candidate = Qualified(trackId, historyGeneration)
        elapsed = 0.0
        baseline(position, playing)
    }

    /** A seek/subsong boundary retains earned time but discards its position discontinuity. */
    fun baseline(position: Double, playing: Boolean) {
        lastTime = nowMs()
        lastPosition = position
        running = playing
    }

    fun sample(position: Double, playing: Boolean): Qualified? {
        val now = nowMs()
        if (running) {
            val wall = (now - lastTime).coerceAtLeast(0) / 1000.0
            val audio = (position - lastPosition).coerceAtLeast(0.0)
            elapsed += minOf(wall, audio)
        }
        lastTime = now
        lastPosition = position
        running = playing
        return if (elapsed + 1e-9 >= 5.0) candidate.also { candidate = null } else null
    }

    /** Clear must not be undone by the same still-playing occurrence. */
    fun forget() {
        candidate = null
        running = false
        elapsed = 0.0
    }
}
