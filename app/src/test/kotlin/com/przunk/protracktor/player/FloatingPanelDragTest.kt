// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class FloatingPanelDragTest {
    @Test fun `a tap and sub-slop jitter do not move or persist a panel`() {
        val drag = FloatingPanelDrag(10f)
        drag.begin(100f, 100f)
        assertNull(drag.move(104f, 104f))
        assertFalse(drag.moving)
        assertFalse(drag.end())
    }

    @Test fun `movement follows screen coordinates across window origin changes`() {
        val drag = FloatingPanelDrag(10f)
        drag.begin(100f, 100f)
        assertEquals(PanelOffset(20, 30), drag.move(120f, 130f))
        assertEquals(PanelOffset(15, 10), drag.move(135f, 140f))
        assertEquals(PanelOffset(0, 0), drag.move(135f, 140f))
        assertTrue(drag.end())
        assertNull(drag.move(200f, 200f))
    }

    @Test fun `a fresh gesture discards previous displacement and ends once`() {
        val drag = FloatingPanelDrag(10f)
        drag.begin(100f, 100f)
        drag.move(150f, 100f)
        assertTrue(drag.end())
        assertFalse(drag.end())
        drag.begin(300f, 500f)
        assertEquals(PanelOffset(-20, 0), drag.move(280f, 500f))
    }

    @Test fun `small movements accumulate rather than disappearing at high touch sampling rates`() {
        val drag = FloatingPanelDrag(10f)
        drag.begin(100f, 100f)
        assertEquals(PanelOffset(20, 0), drag.move(120f, 100f))
        assertEquals(PanelOffset(0, 0), drag.move(120.3f, 100f))
        assertEquals(PanelOffset(1, 0), drag.move(120.6f, 100f))
        assertEquals(PanelOffset(0, 0), drag.move(120.9f, 100f))
    }
}
