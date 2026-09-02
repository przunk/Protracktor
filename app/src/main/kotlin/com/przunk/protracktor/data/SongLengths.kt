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
package com.przunk.protracktor.data

/**
 * HVSC's song length database, as parsing and nothing else.
 *
 * A SID file carries no duration. The tune is a program: it plays until someone stops it, and
 * "how long is it" is a question the format cannot answer. The High Voltage SID Collection answers
 * it by hand -- a person listened and wrote a number down -- and publishes the result as
 * `DOCUMENTS/Songlengths.md5`. Without it every SID in the app shows an unknown length and the
 * position readout has nothing to count towards.
 *
 * **No Android imports on purpose**, the same reason as [SchemaSql]: 5 MB of somebody else's text
 * format is exactly the thing to test on the JVM rather than discover on a phone.
 *
 * ### The file
 *
 * ```
 * [Database]
 * ; /MUSICIANS/H/Hubbard_Rob/Commando.sid
 * 6d019ecba831a9f853675aac29a61c10=3:55.594 1:01.288 0:06 ...
 * ```
 *
 * The key is the **plain MD5 of the whole SID file**. That is worth stating because it used not to
 * be: older HVSC releases keyed the database on a hash built from selected header fields, which
 * libsidplayfp still exposes as `SidTune::createMD5`. Checked against the real file on 2026-09-02 --
 * three tunes fetched from the collection, all three found by plain file MD5 -- so nothing here
 * needs the SID parser at all, and a lookup costs one hash of bytes we are holding anyway.
 *
 * One line per tune, one time per subsong, `M:SS` or `M:SS.fff` where the fraction is a decimal
 * fraction of a second. Measured over the whole 5.2 MB file on 2026-09-02: 61,157 entries, no
 * duplicate keys, no token of any other shape, at most 256 subsongs on one line, longest tune
 * 33:46.
 */
object SongLengths {

    /** One tune: the file's MD5, and a length in seconds per subsong. */
    data class Entry(val md5: String, val seconds: List<Double>)

    private val ENTRY = Regex("^([0-9a-fA-F]{32})=(.+)$")
    private val TIME = Regex("^(\\d+):([0-5]\\d)(?:\\.(\\d{1,3}))?$")

    /**
     * Reads the database.
     *
     * Anything that is not an entry line is skipped rather than rejected -- the `[Database]` header
     * and a comment naming the path above every single entry are both expected, and a future
     * release adding a section this does not know about should cost the lengths it does know, not
     * all of them. A malformed time inside an otherwise good line drops that **line**, because a
     * tune whose third subsong is unreadable is a tune whose lengths we cannot trust.
     */
    fun parse(text: String): List<Entry> = text.lineSequence().mapNotNull { line ->
        val match = ENTRY.matchEntire(line.trim()) ?: return@mapNotNull null
        val times = match.groupValues[2].trim().split(' ').filter { it.isNotEmpty() }
        val seconds = times.map { parseTime(it) ?: return@mapNotNull null }
        if (seconds.isEmpty()) null else Entry(match.groupValues[1].lowercase(), seconds)
    }.toList()

    /** `3:55.594` to 235.594. Null if it is not a time, which is the caller's cue to drop the line. */
    fun parseTime(token: String): Double? {
        val match = TIME.matchEntire(token) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[3]
        // "3.5" is three and a half seconds. Spelling it out because the field is described as
        // milliseconds in places, and reading .5 as five milliseconds would make every fractional
        // length wrong by up to a second in a way nobody would notice from the numbers.
        val frac = if (fraction.isEmpty()) 0.0 else fraction.toInt() / TENS[fraction.length]
        return minutes * 60.0 + match.groupValues[2].toInt() + frac
    }

    private val TENS = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)

    /** How the seconds go into one column, and come back out. Space-separated, as the file has them. */
    fun pack(seconds: List<Double>): String = seconds.joinToString(" ") { trimZeros(it) }

    fun unpack(packed: String): List<Double> =
        packed.split(' ').mapNotNull { it.toDoubleOrNull() }

    private fun trimZeros(value: Double): String {
        val whole = value.toLong()
        return if (value == whole.toDouble()) whole.toString() else value.toString()
    }
}
