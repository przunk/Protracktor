// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The queue's count on Queue's button (B66), as plain Kotlin. */
class QueueCountTest {
    @Test fun `nothing waiting shows nothing`() {
        assertNull(QueueCount.badge(0))
        assertNull(QueueCount.badge(-1))
    }

    @Test fun `up to two digits as they are, more as 99+`() {
        assertEquals("1", QueueCount.badge(1))
        assertEquals("99", QueueCount.badge(99))
        assertEquals("99+", QueueCount.badge(100))
        assertEquals("99+", QueueCount.badge(12_345))
    }
}
