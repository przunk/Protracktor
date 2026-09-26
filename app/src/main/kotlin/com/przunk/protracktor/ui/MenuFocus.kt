// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * **The row a menu came from, and nothing else** (the owner, 2026-09-26): a menu is a rectangle
 * floating over the list, and nothing about it said which tune it was about. While a row's menu is
 * open everything but that row is dimmed, the way the page does it.
 *
 * The dimming only draws. Taking the press beside the menu is the menu's own doing: a Material menu
 * is a focusable popup, and a press outside it closes it and goes no further, so the rows under the
 * dimming cannot be started by the press that closes it.
 */
internal class MenuFocus {
    /** Where the row is, in the app's own coordinates; null while no row's menu is open. */
    var bounds by mutableStateOf<Rect?>(null)
}

internal val LocalMenuFocus = staticCompositionLocalOf<MenuFocus?> { null }

/** Drawn over the whole app, above everything but the menu's own popup, with the row cut out. */
@Composable
internal fun MenuFocusDimming(focus: MenuFocus) {
    val row = focus.bounds ?: return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val hole = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(row, CornerRadius(8.dp.toPx())))
        }
        drawPath(hole, DIM)
    }
}

/** On a row with a menu: says where the row is while [open]. */
@Composable
internal fun Modifier.menuFocus(open: Boolean): Modifier {
    val focus = LocalMenuFocus.current ?: return this
    var bounds by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(open, bounds) {
        if (open) focus.bounds = bounds
        onDispose { if (open) focus.bounds = null }
    }
    return onGloballyPositioned { bounds = it.boundsInRoot() }
}

/** The page's `rgba(0, 0, 0, .55)`: dark enough to read as "not now" in either theme. */
private val DIM = Color.Black.copy(alpha = 0.55f)
