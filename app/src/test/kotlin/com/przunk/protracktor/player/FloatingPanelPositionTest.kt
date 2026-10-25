// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class FloatingPanelPositionTest {
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
