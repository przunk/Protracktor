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
     * The rule, in the owner's own three words: *"one to one"*.
     *
     * Repeat-one outranks "play all". While it is on, nothing advances — not between tunes, not at
     * the end of the file — and the tune that just ended plays again. `Next.FileFinished` is how
     * that is said here, because repeating what is playing is the caller's `restart()`, the same
     * path an ordinary track already uses.
     *
     * **The first version of this test asserted the opposite** and passed: it said repeat-one on
     * the last tune should go back to the first, on the reasoning that "one" meant one row of the
     * playlist. He tried it and the answer was immediate — with "play all" on, repeat-one still
     * moved him off the tune he was listening to, and a repeat that goes somewhere else is not a
     * repeat.
     */
    @Test
    fun `repeat-one repeats the tune that is playing, wherever it is in the file`() {
        for (subsong in 0..7) {
            assertEquals(
                "subsong $subsong",
                Next.FileFinished,
                SubsongAdvance.after(playAll = true, subsong = subsong, subsongCount = 8, repeatOne = true),
            )
        }
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

    /** With repeat off, "play all" still walks the file to its end and then stops. */
    @Test
    fun `play-all without repeat walks once and stops`() {
        val visited = mutableListOf(0)
        var subsong = 0
        while (true) {
            val next = SubsongAdvance.after(true, subsong, subsongCount = 4, repeatOne = false)
            if (next is Next.FileFinished) break
            subsong = (next as Next.Subsong).index
            visited += subsong
            assertTrue("walked past the end", visited.size <= 4)
        }
        assertEquals(listOf(0, 1, 2, 3), visited)
    }
}
