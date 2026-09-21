// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The order a length is taken in: the file, HVSC, songdb, nothing. */
class LengthSourceTest {

    @Test
    fun `the file wins, whatever a database says`() {
        // A MOD: libopenmpt states 180 s and songdb happens to disagree. The file is not a guess.
        assertEquals(180.0, LengthSource.forSubsong(180.0, LengthSource.known(emptyList(), listOf(175.0)), 0), 0.0)
    }

    @Test
    fun `HVSC before songdb`() {
        val known = LengthSource.known(hvsc = listOf(120.0, 30.0), songdb = listOf(99.0, 99.0))
        assertEquals(listOf(120.0, 30.0), known)
    }

    @Test
    fun `songdb when HVSC has nothing`() {
        // An Amiga tune: UADE states nothing and HVSC has never heard of it.
        val known = LengthSource.known(hvsc = emptyList(), songdb = listOf(53.8))
        assertEquals(53.8, LengthSource.forSubsong(0.0, known, 0), 0.0)
    }

    @Test
    fun `the length is the one for the subsong that is playing`() {
        // cust.paradroid, from songdb: seven subsongs, the seventh 69.2 s.
        val known = listOf(46.5, 4.8, 1.9, 3.9, 3.9, 1.9, 69.2)
        assertEquals(69.2, LengthSource.forSubsong(0.0, known, 6), 0.0)
        // A file that opened at its second subsong shows the second's length, not the first's.
        assertEquals(4.8, LengthSource.forSubsong(0.0, known, 1), 0.0)
    }

    @Test
    fun `an unknown subsong reads zero, and so does one past the end`() {
        val known = listOf(10.0, 0.0)
        assertEquals(0.0, LengthSource.forSubsong(0.0, known, 1), 0.0)
        assertEquals(0.0, LengthSource.forSubsong(0.0, known, 5), 0.0)
    }

    @Test
    fun `measuring is needed only when nothing knows any subsong`() {
        assertTrue(LengthSource.needsMeasuring(0.0, emptyList()))
        assertTrue(LengthSource.needsMeasuring(0.0, listOf(0.0, 0.0)))
        assertFalse(LengthSource.needsMeasuring(0.0, listOf(53.8)))
        assertFalse(LengthSource.needsMeasuring(120.0, emptyList()))
    }
}
