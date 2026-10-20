// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import kotlin.math.abs

internal enum class QueueSwipeIntent { UNDECIDED, VERTICAL, HORIZONTAL }

internal fun queueSwipeStartDistance(minimum: Float, touchSlop: Float): Float =
    maxOf(minimum, touchSlop * 2f)

/** Resolve before consuming movement: diagonal/tied intent belongs to the scrolling parent. */
internal fun queueSwipeIntent(x: Float, y: Float, touchSlop: Float, startDistance: Float): QueueSwipeIntent = when {
    abs(y) >= touchSlop && abs(x) <= abs(y) * 2f -> QueueSwipeIntent.VERTICAL
    abs(x) >= startDistance && abs(x) > abs(y) * 2f -> QueueSwipeIntent.HORIZONTAL
    else -> QueueSwipeIntent.UNDECIDED
}
