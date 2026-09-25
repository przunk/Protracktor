// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A60: the screen says the database is being prepared for exactly as long as it is. */
class DatabasePreparationTest {

    @Test
    fun `work in progress is counted and nothing is left over after it`() {
        val before = DatabasePreparation.active.value
        val seen = DatabasePreparation.during { DatabasePreparation.active.value }
        assertEquals(before + 1, seen)
        assertEquals(before, DatabasePreparation.active.value)
    }

    @Test
    fun `two overlapping preparations do not end each other`() {
        val before = DatabasePreparation.active.value
        DatabasePreparation.during {
            // A migration opened from inside the re-decision: the inner one ending must not say
            // "done" while the outer still runs.
            DatabasePreparation.during { assertEquals(before + 2, DatabasePreparation.active.value) }
            assertEquals(before + 1, DatabasePreparation.active.value)
        }
        assertEquals(before, DatabasePreparation.active.value)
    }

    @Test
    fun `a preparation that fails still ends, or the strip would stay for ever`() {
        val before = DatabasePreparation.active.value
        assertThrows(IllegalStateException::class.java) {
            DatabasePreparation.during { error("migration failed") }
        }
        assertEquals(before, DatabasePreparation.active.value)
    }
}
