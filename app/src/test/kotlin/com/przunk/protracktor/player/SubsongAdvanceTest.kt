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
package com.przunk.protracktor.player

import com.przunk.protracktor.player.SubsongAdvance.Next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule a multi-tune file follows when one of its tunes ends.
 *
 * `docs/STATUS.md` C13. The owner reported that repeat-one did not repeat a subsong; reading the
 * code found two faults, and this covers the one that lives in Kotlin.
 */
class SubsongAdvanceTest {

    @Test
    fun `play-all walks to the next tune`() {
        assertEquals(
            Next.Subsong(1),
            SubsongAdvance.after(playAll = true, subsong = 0, subsongCount = 8, repeatOne = false),
        )
        assertEquals(
            Next.Subsong(7),
            SubsongAdvance.after(playAll = true, subsong = 6, subsongCount = 8, repeatOne = false),
        )
    }

    /**
     * The fault as reported. Repeat-one used to be unreachable with "play all" on: the walk
     * returned first for every tune but the last, and the last fell through to a plain restart.
     */
    @Test
    fun `repeat-one loops the whole file, not its last tune`() {
        assertEquals(
            Next.Subsong(0),
            SubsongAdvance.after(playAll = true, subsong = 7, subsongCount = 8, repeatOne = true),
        )
    }

    @Test
    fun `without repeat, the end of the last tune is the end of the file`() {
        assertEquals(
            Next.FileFinished,
            SubsongAdvance.after(playAll = true, subsong = 7, subsongCount = 8, repeatOne = false),
        )
    }

    /**
     * The default, and the owner's own decision: one tune per file unless asked otherwise, so a
     * 256-subsong SAP does not hold the playlist hostage.
     */
    @Test
    fun `with play-all off the file never walks, whatever repeat says`() {
        for (repeat in listOf(false, true)) {
            assertEquals(
                Next.FileFinished,
                SubsongAdvance.after(playAll = false, subsong = 4, subsongCount = 15, repeatOne = repeat),
            )
        }
    }

    /** An ordinary single-tune track must not be treated as a file to loop through. */
    @Test
    fun `a single-tune file is never a subsong decision`() {
        for (playAll in listOf(false, true)) {
            for (repeat in listOf(false, true)) {
                assertEquals(
                    Next.FileFinished,
                    SubsongAdvance.after(playAll, subsong = 0, subsongCount = 1, repeatOne = repeat),
                )
            }
        }
    }

    /**
     * Under repeat-one with "play all", a file must cycle for ever and never report itself
     * finished — walked here rather than asserted at one point, because "for ever" is the claim.
     */
    @Test
    fun `repeat-one with play-all cycles through every tune, for ever`() {
        var subsong = 0
        val visited = mutableSetOf(0)
        val order = mutableListOf<Int>()
        repeat(500) { step ->
            val next = SubsongAdvance.after(true, subsong, subsongCount = 3, repeatOne = true)
            assertTrue("step $step from $subsong reported the file finished", next is Next.Subsong)
            subsong = (next as Next.Subsong).index
            visited += subsong
            if (order.size < 6) order += subsong
        }
        assertEquals("every tune is reached", setOf(0, 1, 2), visited)
        assertEquals("and in order, wrapping at the end", listOf(1, 2, 0, 1, 2, 0), order)
    }
}
