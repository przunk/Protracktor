// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlin.math.roundToInt

/** Screen coordinates avoid feeding a moved window's new origin back into the next drag delta. */
internal class FloatingPanelDrag(private val slop: Float) {
    private var active = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    var moving = false
        private set

    fun begin(x: Float, y: Float) {
        active = true
        moving = false
        startX = x; startY = y
        lastX = x; lastY = y
    }

    fun move(x: Float, y: Float): PanelOffset? {
        if (!active) return null
        if (!moving && (x - startX) * (x - startX) + (y - startY) * (y - startY) <= slop * slop) return null
        moving = true
        val result = PanelOffset((x - lastX).roundToInt(), (y - lastY).roundToInt())
        lastX += result.x; lastY += result.y
        return result
    }

    fun end(): Boolean {
        val moved = moving
        active = false
        moving = false
        return moved
    }
}
