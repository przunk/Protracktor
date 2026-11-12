// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

/** Where on the edge bar a finger is, as a fraction of the tune. Pure, so the JVM holds it. */
internal object EdgeSeek {
    fun fraction(x: Float, width: Float): Float = if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

    /** How many notches the finger passes across the bar: the same feel as the full bar's. */
    const val NOTCHES = 40
}

/**
 * The collapsed dock's seek bar (redesign round 2, after my sketch of 2026-11-11): **a line on the
 * dock's top edge**, where the list ends and the dock begins, so it takes no row of its own, and a
 * **▲ under it**, its tip on the line where the tune has got to. Nothing sticks out over the list.
 *
 * The line is thin; the band under it is what is pressed: a tap there seeks to that place, a
 * sideways drag scrubs and seeks on letting go. **A drag down is not taken**: it passes to the dock,
 * which collapses -- only a sideways move past the touch slop is a seek. A tune that cannot be
 * seeked keeps the line and the marker, dimmed (C45: never draw nothing).
 *
 * Written rather than a restyled `Slider`: the Material slider centres its own thumb and track,
 * and a line at the very edge with a marker below it is neither.
 */
@Composable
internal fun EdgeSeekBar(
    positionSeconds: Double,
    durationSeconds: Double,
    enabled: Boolean,
    onSeek: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val measured = durationSeconds > 0.0
    val canSeek = enabled && measured
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val fraction = if (measured) (scrubbing ?: (positionSeconds / durationSeconds).toFloat()).coerceIn(0f, 1f) else 0f
    val seek by rememberUpdatedState(onSeek)
    val duration by rememberUpdatedState(durationSeconds)
    val haptics = rememberHaptics()
    var notch by remember { mutableIntStateOf(-1) }

    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val played = if (canSeek) primary else lerp(primary, surface, EDGE_DIMMED)
    val rest = MaterialTheme.colorScheme.surfaceVariant

    val gestures = if (!canSeek) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures(onTap = { at ->
                haptics.grab()
                seek(EdgeSeek.fraction(at.x, size.width.toFloat()) * duration)
                haptics.gestureEnd()
            })
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { at ->
                    val f = EdgeSeek.fraction(at.x, size.width.toFloat())
                    haptics.grab()
                    notch = (f * EdgeSeek.NOTCHES).toInt()
                    scrubbing = f
                },
                onDragEnd = {
                    scrubbing?.let { seek(it * duration) }
                    scrubbing = null
                    notch = -1
                    haptics.gestureEnd()
                },
                onDragCancel = { scrubbing = null; notch = -1 },
            ) { change, _ ->
                change.consume()
                val f = EdgeSeek.fraction(change.position.x, size.width.toFloat())
                val step = (f * EdgeSeek.NOTCHES).toInt()
                if (step != notch) { notch = step; haptics.scrub() }
                scrubbing = f
            }
        }

    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(EDGE_BAND)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(
                    (fraction * durationSeconds).toFloat(), 0f..durationSeconds.toFloat().coerceAtLeast(0.001f))
                if (canSeek) setProgress { value -> seek(value.toDouble()); true }
            }
            .then(gestures)
            .drawBehind {
                val line = EDGE_LINE.toPx()
                val x = fraction * size.width
                drawRect(rest, Offset.Zero, Size(size.width, line))
                drawRect(played, Offset.Zero, Size(x, line))
                // The marker's tip touches the line's lower edge exactly at the position.
                val half = EDGE_MARK_WIDTH.toPx() / 2f
                val tall = EDGE_MARK_HEIGHT.toPx()
                drawPath(Path().apply {
                    moveTo(x, line)
                    lineTo(x + half, line + tall)
                    lineTo(x - half, line + tall)
                    close()
                }, played)
            },
    )
}

/**
 * The band that answers to a finger, under the line. 19.5 dp (24 until 2026-11-12, when the dock's
 * content moved up to sit centred in its height, measured equal above and below); with the line and the marker it is still a strip
 * a thumb finds along the dock's whole width.
 */
internal val EDGE_BAND = 19.5.dp
/** The line along the dock's top edge. */
internal val EDGE_LINE = 3.dp
internal val EDGE_MARK_WIDTH = 12.dp
internal val EDGE_MARK_HEIGHT = 8.dp
/** As [SeekBar]'s dimming: the playing colour taken this far towards the surface. */
private const val EDGE_DIMMED = 0.4f
