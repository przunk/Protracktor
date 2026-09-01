/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A snackbar that can be pushed off the screen sideways.
 *
 * Material 3 does not provide this and a notice you cannot dismiss sits over the controls until it
 * decides to leave. Dragging past a quarter of the screen throws it out; letting go short of that
 * springs it back, so a stray touch does not lose a message with an undo on it.
 */
@Composable
fun SwipeableSnackbar(data: SnackbarData) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = rememberHaptics()
    // So the tick fires once on crossing, not on every pixel past the line.
    var pastThreshold by remember { mutableStateOf(false) }

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val dismissThreshold = screenWidthPx / 4f

    Snackbar(
        snackbarData = data,
        modifier = Modifier
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            // Fading with the drag says the gesture is doing something before it has finished.
            .alpha((1f - abs(offset.value) / screenWidthPx).coerceIn(0f, 1f))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch { offset.snapTo(offset.value + delta) }
                    // A tick at the point of no return says "let go now", which the fade alone
                    // only hints at.
                    val past = abs(offset.value + delta) > dismissThreshold
                    if (past != pastThreshold) {
                        pastThreshold = past
                        if (past) haptics.tick()
                    }
                },
                onDragStopped = {
                    if (abs(offset.value) > dismissThreshold) {
                        offset.animateTo(if (offset.value > 0) screenWidthPx else -screenWidthPx)
                        // Left off-screen deliberately. Snapping back to zero here put the snackbar
                        // in the middle at full opacity for the frame between the reset and the host
                        // removing it, which read as a flicker.
                        data.dismiss()
                    } else {
                        offset.animateTo(0f)
                    }
                },
            ),
    )
}

private val Int.dp: androidx.compose.ui.unit.Dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
