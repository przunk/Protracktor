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

import androidx.compose.foundation.lazy.LazyListState

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

private const val ANIMATE_WITHIN = 15
