// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.przunk.protracktor.R
import com.przunk.protracktor.player.FloatingDropZone
import kotlin.math.roundToInt

/** A bounded, non-touchable drop indicator; the original panel retains the touch stream. */
internal class FloatingDismissTarget(context: Context) : LinearLayout(context) {
    private val badge = TextView(context).apply {
        text = "×"
        textSize = 34f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val caption = TextView(context).apply {
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setLines(2)
        background = GradientDrawable().apply {
            setColor(Color.rgb(16, 24, 32)); cornerRadius = dp(8).toFloat()
        }
        setPadding(dp(4), 0, dp(4), 0)
    }
    var armed = false
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(badge, LayoutParams(dp(64), dp(64)))
        addView(caption, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        paintState()
    }

    fun hover(rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val inside = isShown && badge.width > 0 && FloatingDropZone(
            location[0] + badge.left + badge.width / 2f,
            location[1] + badge.top + badge.height / 2f, badge.width / 2f + dp(12),
        ).contains(rawX, rawY)
        val entered = inside && !armed
        if (inside != armed) { armed = inside; paintState() }
        return entered
    }

    private fun paintState() {
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (armed) Color.rgb(148, 35, 45) else Color.rgb(16, 24, 32))
            setStroke(dp(if (armed) 3 else 1), Color.WHITE)
        }
        badge.scaleX = if (armed) 1.1f else 1f
        badge.scaleY = badge.scaleX
        caption.setText(if (armed) R.string.floating_release_hide else R.string.floating_drop_hide)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
