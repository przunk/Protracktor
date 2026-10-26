// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlin.math.roundToInt

/** Fractions of the available travel keep a saved panel reachable after rotation or resizing. */
internal data class FloatingPanelPosition(val x: Float = 1f, val y: Float = .55f) {
    fun snapped() = FloatingPanelPosition(if (finite(x, 1f) < .5f) 0f else 1f, finite(y, .55f))

    fun place(areaWidth: Int, areaHeight: Int, panelWidth: Int, panelHeight: Int, margin: Int): PanelOffset {
        val xRange = travel(areaWidth, panelWidth, margin)
        val yRange = travel(areaHeight, panelHeight, margin)
        return PanelOffset(
            (xRange.first + finite(x, 1f) * (xRange.last - xRange.first)).roundToInt(),
            (yRange.first + finite(y, .55f) * (yRange.last - yRange.first)).roundToInt(),
        )
    }

    companion object {
        fun from(offset: PanelOffset, areaWidth: Int, areaHeight: Int, panelWidth: Int,
                 panelHeight: Int, margin: Int): FloatingPanelPosition {
            fun fraction(value: Int, range: IntRange) =
                if (range.first == range.last) 0f
                else ((value - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
            return FloatingPanelPosition(
                fraction(offset.x, travel(areaWidth, panelWidth, margin)),
                fraction(offset.y, travel(areaHeight, panelHeight, margin)),
            )
        }

        private fun travel(area: Int, panel: Int, margin: Int): IntRange {
            val available = (area - panel).coerceAtLeast(0)
            val edge = margin.coerceIn(0, available / 2)
            return edge..(available - edge)
        }

        private fun finite(value: Float, fallback: Float) =
            if (value.isFinite()) value.coerceIn(0f, 1f) else fallback
    }
}

internal data class PanelOffset(val x: Int, val y: Int)

internal data class FloatingDropZone(val x: Float, val y: Float, val radius: Float) {
    fun contains(pointerX: Float, pointerY: Float): Boolean = radius > 0f && radius.isFinite() &&
        (pointerX - x) * (pointerX - x) + (pointerY - y) * (pointerY - y) <= radius * radius
}
