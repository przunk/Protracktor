// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Whether pressing play should start a track again rather than resume it.
 *
 * **Because "finished" has two meanings here and only one of them was being asked about**
 * (`docs/STATUS.md` C24). `Track.isFinished()` is set by the engine when a backend renders a short
 * buffer — and some backends never do: libsidplayfp is running a 6502 in a loop and has no idea the
 * music is over. For those the app ends the track itself, on the duration HVSC or the file supplied,
 * and the engine still says the track is running.
 *
 * So a SID that reached its end answered *false* to `isFinished()`, play called `start()`, the
 * decoder resumed from a position already past the known duration, and the poll ended the track
 * again within two hundred milliseconds. From the outside: a button that does nothing. Which is the
 * one thing worse than a disabled button, because a disabled one is at least honest.
 *
 * Its own file, and pure, for the reason `SubsongAdvance` is: the alternative is reasoning about it
 * inside a method that needs a phone to run.
 */
object PlayFromEnd {

    /**
     * @param engineSaysFinished what `Track.isFinished()` answered
     * @param positionSeconds where the decoder is
     * @param durationSeconds how long the track is known to be, or 0 when nothing knows
     */
    fun shouldRestart(
        engineSaysFinished: Boolean,
        positionSeconds: Double,
        durationSeconds: Double,
    ): Boolean {
        if (engineSaysFinished) return true
        // Zero means nobody knows the length -- a MOD with no duration, a SID with no HVSC entry --
        // and a track of unknown length is never "at the end". Guarding on it rather than letting
        // `0.0 >= 0.0` be true is the difference between resuming a paused tune and restarting it.
        return durationSeconds > 0.0 && positionSeconds >= durationSeconds
    }
}
