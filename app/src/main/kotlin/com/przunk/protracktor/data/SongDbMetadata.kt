// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * `audacious-uade-tools`' metadata table, as parsing and nothing else.
 *
 * **The second database of facts the formats cannot carry**, after HVSC's song lengths, and it
 * exists for the same reason. A plain `.mod` or `.xm` has nowhere to record a release year — that
 * is the format, not an omission — so the year `docs/WISHLIST.md` B20 shows for a SNDH is blank for
 * the 80,000 ProTracker files in Modland. Measured 2026-09-07: **67,601 of the Modland files this
 * app claims would gain one**, and the formats that gain most are precisely the ones that cannot
 * carry it.
 *
 * GPL-2.0-or-later, read from the `SPDX-License-Identifier` lines in the generator scripts rather
 * than from `COPYING`, which holds the version 2 text alone. Compatible with ours.
 *
 * **No Android imports on purpose**, the same reason as [SongLengths]: fifteen megabytes of
 * somebody else's text format is the thing to test on the JVM rather than discover on a phone.
 *
 * ### The file
 *
 * ```
 * 00000b104a70\tDevastator\tShrimps Design\tCrunched Chips #5\t1995
 * 00002cf7031f\tCybarite\t\t\t
 * 003753e423a3\t4mat\tSlipstream\tMusicdisk 7\t1990
 * ```
 *
 * Tab-separated, five columns: **hash, author, publisher, album, year**. The order was confirmed
 * against rows whose answer is known rather than taken from the README, which lists the fields in a
 * different order in two places — getting author and publisher the wrong way round is the kind of
 * mistake that looks like data and never gets reported.
 *
 * Measured over the whole file on 2026-09-07: **380,282 rows, every one of them five fields with a
 * well-formed key**, 213,002 carrying a year. A source that knew less leaves the column empty
 * rather than stopping short. Missing columns are tolerated anyway, because the sibling files in
 * the same repository are not all this tidy and this parser is the obvious thing to point at them.
 *
 * ### The key is twelve characters, not thirty-two
 *
 * The published database is keyed by the **first 48 bits of the MD5**, deliberately. Storing or
 * looking up the whole hash would miss every time, silently — the table would fill and answer
 * nothing.
 */
object SongDbMetadata {

    /** How many hex characters of the MD5 the published database keys on. */
    const val KEY_LENGTH = 12

    data class Entry(
        val md5: String,
        val author: String,
        val publisher: String,
        val album: String,
        val year: String,
    ) {
        /** Nothing worth storing: a row that names a hash and says nothing about it. */
        val isEmpty: Boolean
            get() = author.isBlank() && publisher.isBlank() && album.isBlank() && year.isBlank()
    }

    /**
     * Every row that says something, **lazily**.
     *
     * A sequence rather than a list, and that is not a style choice. Materialising this file makes
     * 380,282 `Entry` objects holding 1.9 million strings; counted against the JVM's per-object
     * overhead that is about 90 MB of headers alone, and with the source text and the downloaded
     * bytes still alive the import peaks near 150 MB. On a phone that is an out-of-memory crash,
     * and one that would only ever happen on the owner's device — every test here runs on the JVM
     * against a handful of rows. HVSC's database, which this was modelled on, is a sixth the size
     * and got away with it.
     *
     * Rows with an implausible key are dropped rather than repaired: a hash is either the shape the
     * publisher uses or it is a line we do not understand, and guessing at the difference is how a
     * lookup table quietly acquires entries nothing will ever match.
     */
    fun parse(lines: Sequence<String>): Sequence<Entry> = lines.mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val fields = line.split('\t')
        val md5 = fields[0].trim().lowercase()
        if (md5.length != KEY_LENGTH || !md5.all { it in "0123456789abcdef" }) return@mapNotNull null
        val entry = Entry(
            md5 = md5,
            author = fields.getOrElse(1) { "" }.trim(),
            publisher = fields.getOrElse(2) { "" }.trim(),
            album = fields.getOrElse(3) { "" }.trim(),
            year = fields.getOrElse(4) { "" }.trim(),
        )
        entry.takeIf { !it.isEmpty }
    }

    /** The same, for a string that is already in memory. Used by the tests, not by the import. */
    fun parse(text: String): List<Entry> = parse(text.lineSequence()).toList()

    /** The key for a file's bytes: the publisher's twelve characters, not the whole hash. */
    fun keyOf(fullMd5: String): String = fullMd5.lowercase().take(KEY_LENGTH)
}
