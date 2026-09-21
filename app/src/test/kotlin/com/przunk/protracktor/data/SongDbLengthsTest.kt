// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * songdb's song lengths: what a row is, and which of its numbers are lengths.
 *
 * The rows below are copied from `tsv/pretty/md5/songlengths.tsv` at `1bad3e8`, not invented —
 * the parser's job is to read somebody else's file, and a fixture of our own making would test our
 * reading of our own writing.
 */
class SongDbLengthsTest {

    @Test
    fun `one subsong, ended by the routine`() {
        val row = SongDbLengths.parse("00000b104a70\t1\t7800,p").single()
        assertEquals(0x00000b104a70L, row.key)
        assertEquals(1, row.firstSubsong)
        assertEquals(listOf(7.8), SongDbLengths.lengths(row.subsongs))
    }

    @Test
    fun `many subsongs keep their order and their own ends`() {
        val row = SongDbLengths.parse("001a02867da0\t0\t128240,l 5840,s 3360,s 3340,s 3060,s").single()
        val subsongs = SongDbLengths.subsongs(row.subsongs)
        assertEquals(5, subsongs.size)
        assertEquals(SongDbLengths.End.LOOP, subsongs[0].end)
        assertEquals(128.24, subsongs[0].seconds, 1e-9)
        assertEquals(listOf(128.24, 5.84, 3.36, 3.34, 3.06), SongDbLengths.lengths(row.subsongs))
    }

    @Test
    fun `a loop is a length -- one pass through it`() {
        // D4 in the plan: a looping tune plays once through and moves on, as a SID with an HVSC
        // length does. This is the case the app's own measurement can never give.
        assertTrue(SongDbLengths.Subsong(75.62, SongDbLengths.End.LOOP, false).isLength)
    }

    @Test
    fun `a timeout, an error and silence throughout are not lengths`() {
        val row = SongDbLengths.parse("0123456789ab\t1\t512000,t 3000,e 8000,n 4000,p+s").single()
        // The fourth is kept in its place: index three is subsong four, whatever came before.
        assertEquals(listOf(0.0, 0.0, 0.0, 4.0), SongDbLengths.lengths(row.subsongs))
    }

    @Test
    fun `every end code songdb uses is known`() {
        // Counted over the whole file on 2026-09-21. A code missing here would be read as UNKNOWN
        // and refused -- safe, but a length thrown away without anybody deciding to.
        for (code in listOf("p", "p+s", "p+v", "n", "s", "l", "e", "t", "r", "v", "l+v", "l+s")) {
            assertFalse(code, SongDbLengths.End.of(code) == SongDbLengths.End.UNKNOWN)
        }
        assertEquals(SongDbLengths.End.UNKNOWN, SongDbLengths.End.of("x"))
        assertFalse(SongDbLengths.Subsong(10.0, SongDbLengths.End.UNKNOWN, false).isLength)
    }

    @Test
    fun `a duplicate is marked and still a length`() {
        val row = SongDbLengths.parse("ff5c7b3227e0\t0\t65920,p 65920,p,!").single()
        val subsongs = SongDbLengths.subsongs(row.subsongs)
        assertFalse(subsongs[0].duplicate)
        assertTrue(subsongs[1].duplicate)
        assertEquals(listOf(65.92, 65.92), SongDbLengths.lengths(row.subsongs))
    }

    @Test
    fun `malformed rows are refused whole`() {
        val text = listOf(
            "",                                 // blank
            "0000b104a70\t1\t7800,p",           // eleven characters
            "00000b104a7g\t1\t7800,p",          // not hex
            "00000b104a70\tx\t7800,p",          // first subsong not a number
            "00000b104a70\t1\t",                // no subsongs
            "00000b104a70\t1\t7800",            // no end code
            "00000b104a70\t1\t7800,p abc,p",    // one bad subsong spoils the row
            "00000b104a70\t1",                  // too few fields
        ).joinToString("\n")
        assertTrue(SongDbLengths.parse(text).isEmpty())
    }

    @Test
    fun `the key is the first twelve characters of the full hash, as a number`() {
        assertEquals(0x35697d603b73L, SongDbLengths.keyOf("35697D603B73707A6CF949F5B04A183A"))
        assertNull(SongDbLengths.keyOf("35697d60"))
        assertNull(SongDbLengths.keyOf("zz697d603b73707a"))
        // 48 bits, so the largest possible key still fits in a Long with room to spare.
        assertEquals(0xffffffffffffL, SongDbLengths.keyOf("ffffffffffff"))
    }
}
