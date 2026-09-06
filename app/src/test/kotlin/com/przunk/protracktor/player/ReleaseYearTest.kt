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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Digging a year out of five formats that record it five ways.
 *
 * Every string below is the *shape* a real backend produces, taken from what each library documents
 * or emits: sc68 a bare year or `0`, ASAP a date, libsidplayfp its "released" line, game-music-emu
 * a copyright, libopenmpt an ISO date. The parser is the part of `docs/WISHLIST.md` B20 that was
 * always going to need care, because four of the five are prose that happens to contain a number.
 */
class ReleaseYearTest {

    @Test
    fun `a bare year is a year`() {
        assertEquals("1992", ReleaseYear.of("1992"))
        assertEquals("1992", ReleaseYear.from(mapOf("year" to "1992")))
    }

    /** sc68 says `0` when it does not know, and an empty field is not a zero year. */
    @Test
    fun `nothing said is nothing shown`() {
        assertEquals("", ReleaseYear.from(emptyMap()))
        assertEquals("", ReleaseYear.from(mapOf("year" to "0")))
        assertEquals("", ReleaseYear.from(mapOf("year" to "   ")))
        assertEquals("", ReleaseYear.of("unknown"))
    }

    /** libsidplayfp's released line and gme's copyright are sentences with a year in them. */
    @Test
    fun `a year inside a sentence is found`() {
        assertEquals("1987", ReleaseYear.of("1987 Rob Hubbard"))
        assertEquals("1990", ReleaseYear.of("Copyright 1990 Konami"))
        assertEquals("1985", ReleaseYear.of("Hubbard, Rob (1985)"))
    }

    /** ASAP and libopenmpt hand over dates rather than years. */
    @Test
    fun `a date gives up its year`() {
        assertEquals("1994", ReleaseYear.of("13/09/1994"))
        assertEquals("2003", ReleaseYear.of("2003-04-17"))
        assertEquals("1998", ReleaseYear.from(mapOf("date" to "1998-01-01")))
    }

    /**
     * A range stays a range.
     *
     * `1987-1989` is what the file says, and picking its first year would be us deciding something
     * the composer did not. It has to be a *span* though: two years in one sentence are two numbers.
     */
    @Test
    fun `a range is kept and a coincidence is not`() {
        assertEquals("1987\u20131989", ReleaseYear.of("1987-1989"))
        assertEquals("1987\u20131989", ReleaseYear.of("1987 - 1989"))
        assertEquals("1987", ReleaseYear.of("1987 Rob Hubbard, remastered 2019"))
    }

    /**
     * A catalogue number is not a year, which is why the range is bounded rather than "four digits".
     *
     * `KMCA-1234` in a copyright line is the case that made this a range check and not a
     * `\d{4}`: game-music-emu's copyright field carries whatever the ripper typed.
     */
    @Test
    fun `numbers that cannot be years are ignored`() {
        assertEquals("", ReleaseYear.of("KMCA-1234"))
        assertEquals("", ReleaseYear.of("Track 0042"))
        assertEquals("1994", ReleaseYear.of("KMCA-1234, 1994 Konami"))
        assertEquals("", ReleaseYear.of("released 12345"))
    }

    /**
     * Which field wins when several are present.
     *
     * `year` first because sc68 states one and nothing else does; `copyright` last because it is
     * the one that can be a label, a catalogue number or a re-release date.
     */
    @Test
    fun `the most reliable field is asked first`() {
        assertEquals(
            "1988",
            ReleaseYear.from(
                mapOf("year" to "1988", "date" to "1999", "copyright" to "2019 Some Label"),
            ),
        )
        assertEquals(
            "1999",
            ReleaseYear.from(mapOf("year" to "0", "date" to "1999", "copyright" to "2019 Label")),
        )
    }
}
