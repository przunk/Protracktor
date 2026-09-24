// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlin.math.min

/**
 * Sharing a tune as audio -- how long, how it ends, what the file is called (`docs/BACKLOG.md` A62).
 *
 * The owner wants to send a tune on Messenger, which plays an M4A and not a `.mod`. Every decoder
 * already renders PCM, so any format the app plays can be encoded; what has to be decided is the
 * part no decoder answers for a looping tune: where it stops. Pure, so a JVM test holds it.
 */
object AudioExport {

    /** What a file is rendered at when the decoder has no preference -- what nearly all of them give. */
    const val SAMPLE_RATE = 44_100

    /** The fade a cut tune ends on, so it stops as music does rather than mid-note. */
    const val FADE_SECONDS = 3.0

    /** The setting's choices, in minutes, for a tune that states no length. */
    val LIMIT_MINUTES = listOf(1, 3, 5, 10)
    const val DEFAULT_LIMIT_MINUTES = 3

    fun limitFromStored(stored: Int): Int = stored.takeIf { it in LIMIT_MINUTES } ?: DEFAULT_LIMIT_MINUTES

    /** How much to render, and whether it ends on a fade. */
    data class Plan(val seconds: Double, val fade: Boolean)

    /**
     * The tune's own length where it has one and it is within the owner's setting -- it ends by
     * itself; else the setting, ending on the fade. The setting is a limit on every tune, not only
     * on one that states nothing (the owner, 2026-09-24: "let the limit be a setting in the
     * options, just in case, e.g. 3 min"): at 128 kbit/s ten minutes is under 10 MB, which every
     * chat app takes.
     */
    fun plan(knownSeconds: Double, limitMinutes: Int): Plan {
        val limit = limitMinutes * 60.0
        return if (knownSeconds > 0.0 && knownSeconds <= limit) Plan(knownSeconds, fade = false) else Plan(limit, fade = true)
    }

    /** The gain at [frame] of [total]: 1, falling linearly to 0 over the last [fadeFrames]. */
    fun gainAt(frame: Long, total: Long, fadeFrames: Long): Float {
        if (fadeFrames <= 0 || frame < total - fadeFrames) return 1f
        return ((total - frame).toFloat() / fadeFrames).coerceIn(0f, 1f)
    }

    /**
     * The name the other person sees: `Author - Title.m4a`, or the title alone; the file's own
     * extension dropped, so `elysium.mod` does not arrive as `elysium.mod.m4a`, and characters a file
     * system or a chat app would stumble on replaced.
     */
    fun fileName(title: String, author: String): String {
        val bare = title.trim().let { t ->
            val dot = t.lastIndexOf('.')
            if (dot > 0 && t.length - dot in 2..6) t.substring(0, dot) else t
        }.ifBlank { "tune" }
        val named = if (author.isBlank()) bare else "${author.trim()} - $bare"
        return named.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").take(120) + ".m4a"
    }
}
