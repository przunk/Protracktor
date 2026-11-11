// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Redesign E2: what a drag on the dock asks for. */
class DockDragTest {
    private fun intent(x: Float, y: Float) = DockDrag.intent(Offset(x, y), threshold = 100f)

    @Test fun `mostly up expands, mostly down collapses`() {
        assertEquals(DockDrag.Intent.EXPAND, intent(30f, -120f))
        assertEquals(DockDrag.Intent.COLLAPSE, intent(-30f, 120f))
    }

    @Test fun `mostly sideways moves a tune, left to the next`() {
        assertEquals(DockDrag.Intent.NEXT, intent(-150f, 40f))
        assertEquals(DockDrag.Intent.PREVIOUS, intent(150f, -40f))
    }

    @Test fun `short of the threshold asks nothing`() {
        assertEquals(DockDrag.Intent.NONE, intent(-60f, 20f))
        assertEquals(DockDrag.Intent.NONE, intent(10f, -90f))
    }
}
