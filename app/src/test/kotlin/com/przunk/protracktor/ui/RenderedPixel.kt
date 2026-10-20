// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.RenderNode
import android.view.View
import androidx.compose.ui.geometry.Offset
import org.robolectric.util.ReflectionHelpers

/** Lazy-item layers need a hardware draw; a software Canvas silently omits their RenderNodes. */
internal fun View.renderedPixel(point: Offset): Int {
    val node = RenderNode("row-regression-test")
    node.setPosition(0, 0, width, height)
    draw(node.beginRecording())
    node.endRecording()
    val rendered = ReflectionHelpers.callStaticMethod<Bitmap>(HardwareRenderer::class.java, "createHardwareBitmap",
        ReflectionHelpers.ClassParameter.from(RenderNode::class.java, node),
        ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, width),
        ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, height))
    val bitmap = rendered.copy(Bitmap.Config.ARGB_8888, false)
    rendered.recycle()
    return try {
        bitmap.getPixel(point.x.toInt(), point.y.toInt())
    } finally { bitmap.recycle(); node.discardDisplayList() }
}
