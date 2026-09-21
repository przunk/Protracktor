// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
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
    fun `what the phone learnt fills the databases' gaps, and only the gaps`() {
        // songdb knows subsong one and not three; we once measured one and three.
        val filled = LengthSource.fill(known = listOf(46.5, 4.8, 0.0), learned = listOf(47.0, 0.0, 2.0))
        assertEquals(listOf(46.5, 4.8, 2.0), filled)
    }

    @Test
    fun `a subsong only we have heard still gets its length`() {
        assertEquals(listOf(53.8, 0.0, 12.0), LengthSource.fill(listOf(53.8), listOf(0.0, 0.0, 12.0)))
    }

    @Test
    fun `learning records one subsong and pads to reach it`() {
        assertEquals(listOf(0.0, 0.0, 69.2), LengthSource.learn(emptyList(), 2, 69.2))
        assertEquals(listOf(46.5, 69.2), LengthSource.learn(listOf(46.5), 1, 69.2))
    }

    @Test
    fun `nothing new is nothing to write`() {
        assertEquals(null, LengthSource.learn(listOf(46.5), 0, 46.5))
        assertEquals(null, LengthSource.learn(listOf(46.5), 0, 0.0))
        assertEquals(null, LengthSource.learn(listOf(46.5), -1, 10.0))
    }

    @Test
    fun `the stored form reads back as it was written, and nonsense reads as unknown`() {
        val lengths = listOf(46.5, 0.0, 1.9)
        assertEquals(lengths, LengthSource.decode(LengthSource.encode(lengths)))
        assertEquals(listOf(0.0, 3.0), LengthSource.decode("abc 3"))
    }
}
