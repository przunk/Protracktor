// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The case that made play do nothing.
 *
 * The engine's own answer was always consulted; the app's was not, and the app is what ends most
 * SIDs (`docs/STATUS.md` C24).
 */
class PlayFromEndTest {

    @Test
    fun `the engine saying so is enough`() {
        assertTrue(PlayFromEnd.shouldRestart(true, positionSeconds = 0.0, durationSeconds = 0.0))
    }

    @Test
    fun `and so is reaching the length something else supplied`() {
        // A SID: libsidplayfp is running a 6502 and never says the music is over, so the app ends
        // it on HVSC's number. This is the case that answered "resume" and meant "do nothing".
        assertTrue(PlayFromEnd.shouldRestart(false, positionSeconds = 180.0, durationSeconds = 180.0))
        assertTrue(PlayFromEnd.shouldRestart(false, positionSeconds = 181.0, durationSeconds = 180.0))
    }

    @Test
    fun `a paused track in the middle resumes`() {
        assertFalse(PlayFromEnd.shouldRestart(false, positionSeconds = 30.0, durationSeconds = 180.0))
    }

    @Test
    fun `and a track of unknown length is never at its end`() {
        // Zero is "nobody knows", not "zero seconds long". Without the guard, `0.0 >= 0.0` would
        // restart every paused tune that states no duration -- which is most of a folder scan.
        assertFalse(PlayFromEnd.shouldRestart(false, positionSeconds = 0.0, durationSeconds = 0.0))
        assertFalse(PlayFromEnd.shouldRestart(false, positionSeconds = 95.0, durationSeconds = 0.0))
    }
}
