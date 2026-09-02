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

private const val ANIMATE_WITHIN = 15
