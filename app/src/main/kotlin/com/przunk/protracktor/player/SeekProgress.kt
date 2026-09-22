// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * What the time and the seek bar show while a seek is under way (Q11, the owner's choice
 * 2026-09-22: a spinner in place of the elapsed time, beside the bar being dragged).
 *
 * A SID or an SNDH seeks by running its machine to the place, which takes whole seconds on a long
 * SID. Meanwhile the engine still reports where it *was* -- the new position is published when
 * the seek is done -- so the bar, fed by the position poll, sprang back under the finger that had
 * just moved it, and read as a control that had refused.
 */
object SeekProgress {

    /** A seek shorter than this shows nothing: modules and SNDH land before a spinner could mean anything. */
    const val SPINNER_AFTER_MS = 300L

    /** While a seek is under way the bar and the time show where it is going, not where it was. */
    fun shownPosition(reported: Double, seekingTo: Double?): Double = seekingTo ?: reported

    /** Whether a seek has run long enough to say so. */
    fun showsSpinner(seekingTo: Double?, elapsedMs: Long): Boolean =
        seekingTo != null && elapsedMs >= SPINNER_AFTER_MS
}
