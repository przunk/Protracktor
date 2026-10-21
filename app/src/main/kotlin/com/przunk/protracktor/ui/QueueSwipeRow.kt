// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
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
import kotlin.math.sign

/** Gesture end commits once; enqueue never dismisses the original row. Vertical scrolling stays free. */
@Composable
internal fun QueueSwipeRow(
    onAdd: (() -> Unit)?, onRemove: (() -> Unit)? = null, enabled: Boolean = true,
    modifier: Modifier = Modifier,
    canSwipe: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    if (!enabled || onAdd == null && onRemove == null) { Box(modifier) { content() }; return }
    val liveAdd by rememberUpdatedState(onAdd)
    val liveRemove by rememberUpdatedState(onRemove)
    val liveCanSwipe by rememberUpdatedState(canSwipe)
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val minimum = with(LocalDensity.current) { 56.dp.toPx() }
    val swipeStartMinimum = with(LocalDensity.current) { 24.dp.toPx() }
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
        .pointerInput(onAdd != null, onRemove != null, minimum, swipeStartMinimum) {
            val touchSlop = viewConfiguration.touchSlop
            val startDistance = queueSwipeStartDistance(swipeStartMinimum, touchSlop)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Latch before a parent swipe could cancel the child grip and release its lock.
                val permitted = liveCanSwipe()
                if (!permitted) return@awaitEachGesture
                var total = Offset.Zero
                var swiping = false
                var rejected = false
                var movedBeyondSlop = false
                try {
                    while (true) {
                        // Cancel an abandoned drag's click before a child handles the release.
                        val initial = awaitPointerEvent(PointerEventPass.Initial)
                        val release = initial.changes.firstOrNull { it.id == down.id }
                        if (release != null && !release.pressed && !swiping && movedBeyondSlop) {
                            release.consume()
                            return@awaitEachGesture
                        }
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (change.isConsumed || !liveCanSwipe() ||
                            event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture
                        if (!change.pressed) {
                            if (swiping) {
                                val threshold = maxOf(minimum, width * 0.30f)
                                val action = when {
                                    offset >= threshold -> liveAdd
                                    offset <= -threshold -> liveRemove
                                    else -> null
                                }
                                if (action != null) { haptics.press(); action() }
                            }
                            return@awaitEachGesture
                        }
                        val delta = change.positionChange()
                        total += delta
                        movedBeyondSlop = movedBeyondSlop || total.getDistance() > touchSlop
                        var amount = delta.x
                        if (!swiping && !rejected) {
                            when (queueSwipeIntent(total.x, total.y, touchSlop, startDistance)) {
                                QueueSwipeIntent.VERTICAL -> rejected = true
                                QueueSwipeIntent.UNDECIDED -> Unit
                                QueueSwipeIntent.HORIZONTAL -> {
                                    if (total.x > 0 && liveAdd == null || total.x < 0 && liveRemove == null) {
                                        rejected = true
                                    } else {
                                        swiping = true
                                        settling?.cancel()
                                        offset = 0f
                                        amount = total.x - sign(total.x) * startDistance
                                    }
                                }
                            }
                        }
                        if (swiping) {
                            change.consume()
                            offset = (offset + amount).coerceIn(if (liveRemove != null) -width else 0f,
                                if (liveAdd != null) width else 0f)
                        } else {
                            // The parent scrollable handles Main after this child.
                            if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed })
                                return@awaitEachGesture
                        }
                    }
                } finally {
                    if (swiping) reset()
                }
            }
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
