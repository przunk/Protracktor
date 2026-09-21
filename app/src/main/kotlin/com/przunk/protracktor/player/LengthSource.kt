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

    /** Whether the backend needs to work the length out itself: nothing above knows any subsong. */
    fun needsMeasuring(backend: Double, known: List<Double>): Boolean =
        backend <= 0.0 && known.none { it > 0.0 }
}
