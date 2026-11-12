// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A scrollbar you can take hold of.
 *
 * `docs/BACKLOG.md` A3: a visible scrollbar on the right that can be grabbed and dragged. Three
 * hundred rows is a long way by flinging, and this app's lists reach half a million.
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
    /**
     * What a bubble beside the thumb says while it is dragged, for the row at the top of the list
     * (2026-11-12): "120 / 2,345", or an author's first letter. Without it, where the thumb has got
     * to and how far is left are guesses. Visual only; the list is what TalkBack reads.
     */
    bubble: ((firstIndex: Int, total: Int) -> String)? = null,
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

    var trackHeight by remember { mutableStateOf(0) }
    Box(modifier.fillMaxHeight().width(TRACK_WIDTH)) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(TRACK_WIDTH)
            .padding(vertical = 4.dp)
            .onSizeChanged { trackHeight = it.height }
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
    // The bubble, only while the thumb is held, level with its middle and to its left.
    val dragged = dragFraction
    if (bubble != null && dragged != null && trackHeight > 0) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val thumbPx = trackHeight * thumbFraction
        val centre = (trackHeight - thumbPx) * dragged + thumbPx / 2f
        val index = (lastFirstIndex * dragged).roundToInt().coerceIn(0, total - 1)
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .wrapContentSize(align = Alignment.TopEnd, unbounded = true)
                .offset { androidx.compose.ui.unit.IntOffset(
                    -(TRACK_WIDTH + BUBBLE_GAP).roundToPx(),
                    (centre - with(density) { BUBBLE_HEIGHT.toPx() } / 2f).roundToInt()) }
                .testTag("scrollbar-bubble"),
        ) {
            Text(bubble(index, total), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                modifier = Modifier.height(BUBBLE_HEIGHT).wrapContentHeight().padding(horizontal = 14.dp))
        }
    }
    }
}

/** The bubble's own height, so it can sit level with the thumb's middle. */
private val BUBBLE_HEIGHT = 40.dp
private val BUBBLE_GAP = 8.dp

/** "120 / 2,345": the row at the top and how many there are, grouped as every number is. */
internal fun positionBubble(context: android.content.Context): (Int, Int) -> String =
    { first, total -> context.getString(com.przunk.protracktor.R.string.scrollbar_position, first + 1, total) }

/** Wide enough to grab, narrow enough not to be grabbed by accident. */
private val TRACK_WIDTH = 10.dp

/** However long the list, the thumb stays big enough to hit. */
private const val MIN_THUMB = 0.06f

/** A weight must be positive; at the very top or bottom one of the spacers would otherwise be zero. */
private const val MIN_WEIGHT = 0.0001f
