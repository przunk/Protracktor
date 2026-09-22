// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * How far the seek bar runs, and whether that is a length or only where playback will stop (the
 * owner's variant (a), 2026-09-22).
 *
 * **A known length is the bar**, exact: from the file, a database, or measured -- an NSF that falls
 * silent is measured when it plays. **An unknown one still gets a bar**, to where playback will
 * stop: the earlier of where the decoder itself ends it (`ends_at` -- game-music-emu fades a
 * looping tune at 2:30) and the fallback length, after which the app ends any tune. That is true of
 * what will happen and says nothing about the tune, so it is shown as approximate: `~2:38`.
 * `web/src/rules.js` `barLength` is the same rule; `docs/rules/queue-cases.tsv` holds both to it.
 */
object BarLength {

    data class Bar(val seconds: Double, val approximate: Boolean)

    fun of(durationSeconds: Double, endsAtSeconds: Double?, fallbackSeconds: Double): Bar {
        if (durationSeconds > 0.0) return Bar(durationSeconds, approximate = false)
        val stop = endsAtSeconds?.takeIf { it > 0.0 }?.let { minOf(it, fallbackSeconds) } ?: fallbackSeconds
        return Bar(stop, approximate = true)
    }
}
