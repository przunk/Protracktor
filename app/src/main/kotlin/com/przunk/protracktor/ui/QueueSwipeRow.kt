// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Gesture end commits once; enqueue never dismisses the original row. Vertical scrolling stays free. */
@Composable
internal fun QueueSwipeRow(
    onAdd: (() -> Unit)?, onRemove: (() -> Unit)? = null, enabled: Boolean = true,
    modifier: Modifier = Modifier, content: @Composable () -> Unit,
) {
    if (!enabled || onAdd == null && onRemove == null) { Box(modifier) { content() }; return }
    val liveAdd by rememberUpdatedState(onAdd)
    val liveRemove by rememberUpdatedState(onRemove)
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val minimum = with(LocalDensity.current) { 56.dp.toPx() }
    val addLabel = stringResource(R.string.action_add_to_queue)
    val removeLabel = stringResource(R.string.queue_remove)
    val reset = {
        settling?.cancel()
        settling = scope.launch {
            animate(offset, 0f, animationSpec = tween(140)) { value, _ -> offset = value }
        }
    }
    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { width = it.width.toFloat() }
        .semantics {
            customActions = buildList {
                if (onAdd != null) add(CustomAccessibilityAction(addLabel) { liveAdd?.invoke(); true })
                if (onRemove != null) add(CustomAccessibilityAction(removeLabel) { liveRemove?.invoke(); true })
            }
        }
        .pointerInput(onAdd != null, onRemove != null, minimum) {
            detectHorizontalDragGestures(
                onDragStart = { settling?.cancel(); offset = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    offset = (offset + amount).coerceIn(if (liveRemove != null) -width else 0f, if (liveAdd != null) width else 0f)
                },
                onDragCancel = { reset() },
                onDragEnd = {
                    val threshold = maxOf(minimum, width * 0.30f)
                    val action = when { offset >= threshold -> liveAdd; offset <= -threshold -> liveRemove; else -> null }
                    reset()
                    if (action != null) { haptics.press(); action() }
                },
            )
        }) {
        if (offset != 0f) {
            val adding = offset > 0
            Row(Modifier.matchParentSize().background(if (adding) Color(0xFF1B5E20) else Color(0xFFB71C1C))
                .testTag(if (adding) "swipe-add-background" else "swipe-remove-background")
                .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (adding) androidx.compose.foundation.layout.Arrangement.Start
                    else androidx.compose.foundation.layout.Arrangement.End) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Icon(if (adding) PlayerIcons.QueueAdd else PlayerIcons.Remove, null)
                    Text(if (adding) addLabel else removeLabel, Modifier.padding(start = 8.dp))
                }
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
    }
}
