// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekProgressTest {

    @Test
    fun `while a seek runs the bar shows where it is going`() {
        assertEquals(180.0, SeekProgress.shownPosition(reported = 12.0, seekingTo = 180.0), 0.0)
    }

    @Test
    fun `with no seek the bar shows what the engine reports`() {
        assertEquals(12.0, SeekProgress.shownPosition(reported = 12.0, seekingTo = null), 0.0)
    }

    @Test
    fun `a quick seek shows no spinner, a slow one does`() {
        assertFalse(SeekProgress.showsSpinner(seekingTo = 60.0, elapsedMs = 120))
        assertTrue(SeekProgress.showsSpinner(seekingTo = 60.0, elapsedMs = 300))
        assertFalse("no seek, no spinner", SeekProgress.showsSpinner(seekingTo = null, elapsedMs = 5000))
    }
}
