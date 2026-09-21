// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Where a tune's length comes from, in order — one rule, in one place (`docs/PLAN_SONGDB_LENGTHS.md`).
 *
 * 1. **The file**, through its backend. A format that states its own length knows it better than a
 *    lookup on a hash can; libopenmpt's MOD and XM keep their own figures whatever a database says.
 * 2. **HVSC**, by the full MD5 — the SID lengths, as before.
 * 3. **songdb**, by the first twelve characters of the same MD5 — the Amiga formats UADE plays.
 * 4. Otherwise unknown, and zero says so (C68). UADE then measures in the background, and the
 *    Settings fallback still ends a tune nothing knows.
 *
 * Pure, so the order is a test and not a sentence in a comment.
 */
object LengthSource {

    /**
     * Every subsong's length known before playing, from the databases: HVSC's list if it has
     * anything for this file, songdb's otherwise. Zeros inside a list mean "this subsong unknown".
     */
    fun known(hvsc: List<Double>, songdb: List<Double>): List<Double> =
        if (hvsc.any { it > 0.0 }) hvsc else songdb

    /**
     * The length to show for [subsong], zero-based.
     *
     * By the subsong the file actually opened at, not the first: `GmeBackend` opens a HES at the
     * first track with sound, and `UadeBackend` skips a leading subsong that is only silence, so
     * index zero is not always what plays. The first version of this read `firstOrNull()` and would
     * have shown `reach for the skies-german.avp` the half-second of its empty first subsong.
     */
    fun forSubsong(backend: Double, known: List<Double>, subsong: Int): Double =
        backend.takeIf { it > 0.0 }
            ?: known.getOrNull(subsong)?.takeIf { it > 0.0 }
            ?: 0.0

    /**
     * [known] with its gaps filled from what this phone learnt by playing (A50).
     *
     * Only the gaps: a database's figure stands over a measurement of ours, because it is the same
     * UADE and a considered one -- songdb trims a fade and a trailing silence that a run to the end
     * does not. The result is as long as the longer of the two, so a subsong only we have heard
     * still gets its length.
     */
    fun fill(known: List<Double>, learned: List<Double>): List<Double> =
        List(maxOf(known.size, learned.size)) { index ->
            known.getOrNull(index)?.takeIf { it > 0.0 } ?: learned.getOrNull(index)?.takeIf { it > 0.0 } ?: 0.0
        }

    /**
     * [learned] with [seconds] recorded for [subsong], padded with zeros to reach it.
     *
     * Returns null when there is nothing new to store -- no length, or the same one already there --
     * so the caller writes only when something changed.
     */
    fun learn(learned: List<Double>, subsong: Int, seconds: Double): List<Double>? {
        if (subsong < 0 || seconds <= 0.0) return null
        if (learned.getOrNull(subsong) == seconds) return null
        return List(maxOf(learned.size, subsong + 1)) { index ->
            if (index == subsong) seconds else learned.getOrNull(index) ?: 0.0
        }
    }

    /** The stored form: one figure per subsong, space-separated. */
    fun encode(lengths: List<Double>): String = lengths.joinToString(" ") { "%.3f".format(java.util.Locale.ROOT, it) }

    /** The stored form read back; anything unreadable is a zero, never a guess. */
    fun decode(text: String): List<Double> =
        text.split(' ').filter { it.isNotEmpty() }.map { it.toDoubleOrNull()?.takeIf { v -> v > 0.0 } ?: 0.0 }
}
