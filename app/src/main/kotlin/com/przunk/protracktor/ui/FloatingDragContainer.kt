// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.przunk.protracktor.player.FloatingPanelDrag

/** Observe raw coordinates before Compose; moving the window must not move the gesture origin. */
internal class FloatingDragContainer(
    context: Context,
    private val onTouchStart: () -> Unit,
    private val onTapEnd: () -> Unit,
    private val onDragStart: () -> Unit,
    private val onDrag: (Int, Int, Float, Float) -> Unit,
    private val onDragEnd: (cancelled: Boolean) -> Unit,
) : FrameLayout(context) {
    private val drag = FloatingPanelDrag(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val haptics = Haptics(this)
    private var cancelled = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelled = false
                onTouchStart()
                drag.begin(event.rawX, event.rawY)
                super.dispatchTouchEvent(event)
                return true // Background and disabled controls must also be draggable.
            }
            MotionEvent.ACTION_MOVE -> {
                if (cancelled) return true
                val wasMoving = drag.moving
                val amount = drag.move(event.rawX, event.rawY)
                if (amount != null) {
                    if (!wasMoving) {
                        cancelChild(event)
                        haptics.gestureStart()
                        onDragStart()
                    }
                    onDrag(amount.x, amount.y, event.rawX, event.rawY)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (cancelled) return true
                if (drag.moving) {
                    // Up can contain a newer position than the last Move, especially on a fast drop.
                    drag.move(event.rawX, event.rawY)?.let { onDrag(it.x, it.y, event.rawX, event.rawY) }
                    drag.end()
                    haptics.gestureEnd()
                    onDragEnd(false)
                    return true
                }
                drag.end()
                super.dispatchTouchEvent(event)
                onTapEnd()
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                val moving = drag.end()
                cancelled = true
                cancelChild(event)
                if (moving) onDragEnd(true) else onTapEnd()
                return true
            }
        }
        if (!cancelled) super.dispatchTouchEvent(event)
        return true
    }

    fun cancelGesture(notify: Boolean = true) {
        val moving = drag.end()
        cancelled = true
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        try { super.dispatchTouchEvent(event) } finally { event.recycle() }
        if (moving && notify) onDragEnd(true)
    }

    private fun cancelChild(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
    }
}
