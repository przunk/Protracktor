// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class FloatingPanelPositionTest {
    @Test fun `release chooses the nearest horizontal edge without changing height`() {
        assertEquals(FloatingPanelPosition(0f, .3f), FloatingPanelPosition(.49f, .3f).snapped())
        assertEquals(FloatingPanelPosition(1f, .3f), FloatingPanelPosition(.5f, .3f).snapped())
        assertEquals(FloatingPanelPosition(1f, .55f), FloatingPanelPosition(Float.NaN, Float.NaN).snapped())
        assertEquals(FloatingPanelPosition(0f, 1f), FloatingPanelPosition(-4f, 9f).snapped())
    }

    @Test fun `dismissal needs a drop inside the circular target`() {
        val target = FloatingDropZone(100f, 200f, 40f)
        assertTrue(target.contains(100f, 200f))
        assertTrue(target.contains(100f, 240f))
        assertFalse(target.contains(139f, 239f))
        assertFalse(target.contains(100f, 241f))
        assertFalse(target.contains(Float.NaN, 200f))
        assertFalse(FloatingDropZone(0f, 0f, 0f).contains(0f, 0f))
    }

    @Test fun `saved fractions keep a panel within portrait and landscape travel`() {
        val position = FloatingPanelPosition(.8f, .7f)
        assertEquals(PanelOffset(27, 445), position.place(360, 800, 320, 160, 8))
        assertEquals(PanelOffset(379, 137), position.place(800, 360, 320, 160, 8))
    }

    @Test fun `dragging beyond every edge clamps before persistence`() {
        val position = FloatingPanelPosition.from(PanelOffset(-1000, 9000), 360, 800, 320, 160, 8)
        assertEquals(FloatingPanelPosition(0f, 1f), position)
        assertEquals(PanelOffset(8, 632), position.place(360, 800, 320, 160, 8))
    }

    @Test fun `collapsed travel and corrupt fractions remain reachable`() {
        assertEquals(PanelOffset(0, 0), FloatingPanelPosition(Float.NaN, Float.POSITIVE_INFINITY)
            .place(100, 100, 320, 160, 8))
        assertEquals(FloatingPanelPosition(0f, 0f),
            FloatingPanelPosition.from(PanelOffset(40, 70), 100, 100, 320, 160, 8))
        assertEquals(PanelOffset(32, 351), FloatingPanelPosition(Float.NaN, Float.NaN)
            .place(360, 800, 320, 160, 8))
    }

    @Test fun `restoring the normalized dragged position preserves its pixel location`() {
        val offset = PanelOffset(25, 257)
        val normalized = FloatingPanelPosition.from(offset, 360, 800, 320, 160, 8)
        assertEquals(offset, normalized.place(360, 800, 320, 160, 8))
    }
}
