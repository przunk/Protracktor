// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMarqueeTest {

    @Test
    fun `a line scrolls when animations are on`() {
        assertTrue(DockMarquee.scrolls(animatorScale = 1f, isStatus = false))
        assertTrue("a slowed animator is still an animator", DockMarquee.scrolls(0.5f, isStatus = false))
    }

    @Test
    fun `nothing moves with the system's animations off`() {
        assertFalse(DockMarquee.scrolls(animatorScale = 0f, isStatus = false))
    }

    @Test
    fun `the loading line never scrolls`() {
        assertFalse(DockMarquee.scrolls(animatorScale = 1f, isStatus = true))
    }
}
