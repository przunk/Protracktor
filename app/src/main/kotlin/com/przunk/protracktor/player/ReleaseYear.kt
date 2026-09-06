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

/**
 * The year a tune was released, dug out of whatever its format happened to record.
 *
 * `docs/WISHLIST.md` B20 predicted this would end in "a small parser and a decision about what to
 * show when it says `1987-1989` or nothing at all", and it does. Five backends state the year five
 * ways and only one of them states a year:
 *
 * | backend | field | what it actually contains |
 * | --- | --- | --- |
 * | sc68 | `year` | a year, or `0` / empty |
 * | ASAP | `date` | `1987`, or `DD/MM/YYYY`, or a range |
 * | libsidplayfp | `copyright` | the tune's "released" line — usually `1987 Rob Hubbard` |
 * | game-music-emu | `copyright` | usually starts with the year — `1990 Konami` |
 * | libopenmpt | `date` | an ISO date in IT and MPTM, absent nearly everywhere else |
 *
 * So the rule is: look in those three keys in that order, and take the first four-digit number that
 * could be a year. **Bounded to 1970..2099 deliberately** — a copyright line can carry a catalogue
 * number, and `KMCA-1234` must not become 1234.
 */
object ReleaseYear {

    /**
     * In order of how much the field is a year rather than a sentence containing one.
     *
     * `year` is sc68's and needs no interpretation. `date` is a date. `copyright` is prose, and is
     * asked last because it is the one that can be a catalogue number, a label name, or a range.
     */
    private val KEYS = listOf("year", "date", "copyright")

    private val YEAR = Regex("""(?<!\d)(19[7-9]\d|20\d\d)(?!\d)""")

    /** A year, a range, or empty when nothing in the metadata says. */
    fun from(metadata: Map<String, String>): String {
        for (key in KEYS) {
            val value = metadata[key]?.trim().orEmpty()
            if (value.isEmpty() || value == "0") continue
            val found = of(value)
            if (found.isNotEmpty()) return found
        }
        return ""
    }

    /**
     * The year in one string.
     *
     * A range is kept as a range rather than flattened to its first year: `1987-1989` is what the
     * file says and shortening it would be us deciding something the composer did not. Two years
     * count as a range only when nothing but a dash separates them — `1987 Rob Hubbard 1989` is a
     * sentence with two numbers in it, not a span.
     */
    fun of(text: String): String {
        val years = YEAR.findAll(text).toList()
        if (years.isEmpty()) return ""
        val first = years[0]
        if (years.size >= 2) {
            val between = text.substring(first.range.last + 1, years[1].range.first)
            if (between.trim().let { it == "-" || it == "–" || it == "—" }) {
                return "${first.value}\u2013${years[1].value}"
            }
        }
        return first.value
    }
}
