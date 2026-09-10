// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Scrolls to an item, animating only when it is close.
 *
 * Animating across three hundred rows is a long, silly animation nobody asked to watch, and it is
 * also the difference between "put me back where I was" feeling instant and feeling like a ride.
 *
 * Shared rather than duplicated: the playlist uses it to follow the playing track (B13, B14) and
 * Browse uses it to put you back on the folder you came out of (A20). One policy, so the two cannot
 * drift into behaving differently for no reason anybody chose.
 */
internal suspend fun LazyListState.bringIntoView(index: Int) {
    val distance = kotlin.math.abs(index - firstVisibleItemIndex)
    if (distance > ANIMATE_WITHIN) scrollToItem(index) else animateScrollToItem(index)
}

/** Whether [index] is one of the items currently on screen. */
internal fun LazyListState.isVisible(index: Int): Boolean =
    layoutInfo.visibleItemsInfo.any { it.index == index }

/**
 * Puts [index] on screen **without moving anything if it is already there.**
 *
 * The difference from [bringIntoView] is the whole point: that one scrolls the item to the *start*
 * of the view every time, which is right when you are being taken somewhere and wrong when you are
 * being kept somewhere. Following a playing item with it means the item is pinned to the left edge
 * and everything before it becomes unreachable — and each advance drags the view again.
 *
 * @param follow whether to move at all when the item is off screen. Pass false once the user has
 *   scrolled away deliberately: a view that snaps back to the playing item is a view you cannot
 *   read while music is playing.
 */
internal suspend fun LazyListState.keepInView(index: Int, follow: Boolean) {
    if (isVisible(index)) return
    if (!follow) return
    bringIntoView(index)
}

/**
 * Keeps the playing row on screen as the music moves, **one row at a time and only when it leaves.**
 *
 * Built for the Random view and moved here when the owner asked for it on every list (2026-09-10):
 * it replaced the follow-track button, the small FAB that had to be switched on and was switched
 * off again by the first drag. Nothing to switch now: the list moves when the playing row would
 * otherwise be out of sight, and not otherwise.
 *
 * **Arriving is not a change.** The key the list is composed with is remembered and ignored; only a
 * *later* key moves anything. Without that, every return from Browse or Settings would throw the
 * playlist back to the playing row and lose the place you were reading — and taking you there once,
 * on purpose, is what "Show in playlist" in Now Playing is for.
 *
 * @param key what identifies the playing row *as a change*. The track's id for the playlist, so
 *   that reordering and removing rows around it does not scroll; the cursor for the Random record,
 *   whose rows may repeat a tune.
 * @param active false while the list is behind glass, while rows are being ticked, or while one is
 *   being dragged -- a list that scrolls under a moving finger fights the finger.
 */
@Composable
internal fun KeepRowInView(listState: LazyListState, index: Int?, key: Any?, active: Boolean = true) {
    val currentIndex by rememberUpdatedState(index)
    val currentActive by rememberUpdatedState(active)
    val seen = remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == seen.value) return@LaunchedEffect
        seen.value = key
        if (!currentActive) return@LaunchedEffect
        val target = currentIndex ?: return@LaunchedEffect
        listState.revealRow(target)
    }
}

/**
 * Scrolls the least that puts [index] wholly on screen, or not at all.
 *
 * Every rule here was a defect first, found by the owner stepping through the Random record:
 *
 * - **Measured after the row exists.** The playing row moves at the moment the list grows, and on
 *   that pass `layoutInfo` still describes the list without it.
 * - **Measured against what can be seen**, between the content paddings. The viewport runs under
 *   them, and the bottom one is the dock: a row behind it counted as visible and was left there.
 * - **The side it left by comes from the index when the row is not laid out.** Absent means
 *   appended *or* scrolled off the top, and treating absent as "below" sent Previous a page down.
 * - **To an index, never by a distance.** A relative scroll is cancelled part-way by the next
 *   press and the shortfall accumulates; an absolute one says where to end up.
 *
 * Off the top it lands at the top edge, off the bottom at the bottom edge: a step of one row is a
 * scroll of one row, either way. Far jumps do not animate, for [bringIntoView]'s reason.
 */
internal suspend fun LazyListState.revealRow(index: Int) {
    snapshotFlow { layoutInfo.totalItemsCount }.first { it > index }
    val layout = layoutInfo
    val top = layout.viewportStartOffset + layout.beforeContentPadding
    val bottom = layout.viewportEndOffset - layout.afterContentPadding
    val row = layout.visibleItemsInfo.firstOrNull { it.index == index }
    if (row != null && row.offset >= top && row.offset + row.size <= bottom) return

    val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
    val above = if (row != null) row.offset < top else index < first
    val height = row?.size ?: layout.visibleItemsInfo.firstOrNull()?.size ?: 0
    // A negative offset is what lands a row at the bottom edge rather than the top.
    val offset = if (above) 0 else -(bottom - top - height).coerceAtLeast(0)
    if (kotlin.math.abs(index - firstVisibleItemIndex) > ANIMATE_WITHIN) {
        scrollToItem(index, offset)
    } else {
        animateScrollToItem(index, offset)
    }
}

private const val ANIMATE_WITHIN = 15
