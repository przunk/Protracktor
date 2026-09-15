// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/STATUS.md` C56: how long to play a tune whose length nothing knows.
 *
 * Small, and worth having anyway — the value comes out of a database column whose default is zero,
 * and zero is the one input that must **not** be taken literally. A tune that ends immediately is a
 * worse fault than the one this setting exists to fix.
 */
class FallbackLengthTest {

    @Test
    fun `never set means the default, not an instant end`() {
        assertEquals(FallbackLength.DEFAULT_SECONDS, FallbackLength.fromStored(0))
    }

    @Test
    fun `a negative value cannot come from the slider, and is still not obeyed`() {
        assertEquals(FallbackLength.DEFAULT_SECONDS, FallbackLength.fromStored(-1))
    }

    @Test
    fun `a value from a newer build is clamped rather than refused`() {
        // Refusing it would leave the app with no answer at all, which is the failure being fixed.
        assertEquals(FallbackLength.RANGE_SECONDS.last, FallbackLength.fromStored(99 * 60))
        assertEquals(FallbackLength.RANGE_SECONDS.first, FallbackLength.fromStored(1))
    }

    @Test
    fun `a chosen value is kept exactly`() {
        for (minutes in 3..10) {
            assertEquals(minutes * 60, FallbackLength.fromStored(minutes * 60))
        }
    }

    @Test
    fun `the owner's range is three to ten minutes, defaulting to three`() {
        assertEquals(3 * 60, FallbackLength.RANGE_SECONDS.first)
        assertEquals(10 * 60, FallbackLength.RANGE_SECONDS.last)
        assertEquals(3 * 60, FallbackLength.DEFAULT_SECONDS)
    }

    @Test
    fun `the slider has one notch per minute`() {
        // `steps` in Compose counts the notches *between* the ends, so the screen passes
        // STEPS - 2. If this count is wrong the slider lands between minutes and the label shows a
        // number the setting cannot hold.
        assertEquals(8, FallbackLength.STEPS)
        assertTrue(FallbackLength.STEPS - 2 > 0)
    }
}
