// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSwipeIntentTest {
    @Test fun `start distance respects density minimum and larger platform slop`() {
        assertEquals(48f, queueSwipeStartDistance(48f, 16f), 0f)
        assertEquals(80f, queueSwipeStartDistance(48f, 40f), 0f)
    }

    @Test fun `small sideways movements remain unclaimed in both directions`() {
        for (sign in listOf(-1, 1))
            assertEquals(QueueSwipeIntent.UNDECIDED, queueSwipeIntent(sign * 47f, 0f, 16f, 48f))
    }

    @Test fun `meaningful diagonals and ratio ties favour vertical scrolling`() {
        for (x in listOf(-60f, -32f, 32f, 60f)) for (sign in listOf(-1, 1))
            assertEquals(QueueSwipeIntent.VERTICAL, queueSwipeIntent(x, sign * 40f, 16f, 48f))
        assertEquals(QueueSwipeIntent.VERTICAL, queueSwipeIntent(48f, 24f, 16f, 48f))
    }

    @Test fun `clear horizontal intent crosses both distance and dominance requirements`() {
        for (sign in listOf(-1, 1)) {
            assertEquals(QueueSwipeIntent.UNDECIDED, queueSwipeIntent(sign * 47f, 12f, 16f, 48f))
            assertEquals(QueueSwipeIntent.HORIZONTAL, queueSwipeIntent(sign * 48f, 23f, 16f, 48f))
        }
    }

    @Test fun `sub-slop noise alone does not prematurely lock vertical intent`() {
        assertEquals(QueueSwipeIntent.UNDECIDED, queueSwipeIntent(0f, 15f, 16f, 48f))
        assertEquals(QueueSwipeIntent.VERTICAL, queueSwipeIntent(2f, -16f, 16f, 48f))
    }
}
