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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A scrollbar you can take hold of.
 *
 * `docs/BACKLOG.md` A3, in the owner's own words: *"a visible scrollbar on the right that you can
 * grab and drag down."* Three hundred rows is a long way by flinging, and this app's lists reach
 * half a million.
 *
 * **It is a third drag on a screen that already has two**, which is the thing to be careful about
 * (`docs/ARCHITECTURE.md` §17): a playlist row has a drag handle for reordering, and a long press
 * starts selecting. They stay apart by living in different places — the handle is inside the row,
 * this is at the very edge, and it is narrow enough that a thumb scrolling the list does not land
 * on it.
 *
 * Absent when everything already fits: a scrollbar for a list of six is furniture.
 */
@Composable
internal fun DraggableScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layout = listState.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo.size
    if (total == 0 || visible == 0 || visible >= total) return

    val scope = rememberCoroutineScope()
    // Where the thumb is while a drag is in progress, as a fraction of the track. Null when nothing
    // is dragging, so the thumb follows the list rather than the finger.
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    // Item counts rather than pixels. Rows here are one height, and asking a lazy list for the pixel
    // extent of half a million unmeasured items is not a question it can answer.
    val lastFirstIndex = (total - visible).coerceAtLeast(1)
    val thumbFraction = (visible.toFloat() / total).coerceIn(MIN_THUMB, 1f)
    val positionFraction = dragFraction
        ?: (listState.firstVisibleItemIndex.toFloat() / lastFirstIndex).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(TRACK_WIDTH)
            .padding(vertical = 4.dp)
            .pointerInput(total, visible) {
                detectVerticalDragGestures(
                    onDragStart = { start -> dragFraction = (start.y / size.height).coerceIn(0f, 1f) },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null },
                ) { change, delta ->
                    change.consume()
                    val moved = ((dragFraction ?: 0f) + delta / size.height).coerceIn(0f, 1f)
                    dragFraction = moved
                    // scrollToItem, not animateScrollToItem: the list belongs under the thumb now,
                    // not on its way there. An animation chasing a finger reads as lag.
                    scope.launch { listState.scrollToItem((lastFirstIndex * moved).roundToInt()) }
                }
            },
    ) {
        // Three weighted boxes rather than a measured offset. This composable re-runs on every
        // frame of a scroll -- that is what a scrollbar is -- so it must not do anything expensive
        // per frame, and `BoxWithConstraints` is a subcomposition. Weights are pure layout.
        val slack = 1f - thumbFraction
        Spacer(Modifier.weight((slack * positionFraction).coerceAtLeast(MIN_WEIGHT)))
        Box(
            modifier = Modifier
                .weight(thumbFraction)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    MaterialTheme.shapes.small,
                ),
        )
        Spacer(Modifier.weight((slack * (1f - positionFraction)).coerceAtLeast(MIN_WEIGHT)))
    }
}

/** Wide enough to grab, narrow enough not to be grabbed by accident. */
private val TRACK_WIDTH = 10.dp

/** However long the list, the thumb stays big enough to hit. */
private const val MIN_THUMB = 0.06f

/** A weight must be positive; at the very top or bottom one of the spacers would otherwise be zero. */
private const val MIN_WEIGHT = 0.0001f
