// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * Where the music comes from, read from `app/notices/sources.tsv` (`docs/WISHLIST.md` B41).
 *
 * The same table the page reads, copied into the assets by the build beside the licence table. Pure,
 * so its shape and its coverage of the catalogues are tested on the JVM (`MusicSourcesTest`).
 */
object MusicSources {

    /** Where the build puts the table. */
    const val TABLE_ASSET = "notices/sources.tsv"

    data class Source(val id: String, val name: String, val url: String, val text: String)

    /**
     * The rows [build] shows, with the text in Polish when [polish] and in English otherwise;
     * comments and blank lines skipped, a short row refused.
     */
    fun parse(text: String, polish: Boolean, build: String = OpenSourceNotices.APP): List<Source> =
        text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
            .filter { it.size >= 6 }
            .filter { cells -> build in cells[3].split(',').map { it.trim() } }
            .map { cells ->
                Source(
                    id = cells[0].trim(),
                    name = cells[1].trim(),
                    url = cells[2].trim(),
                    text = (if (polish) cells[5] else cells[4]).trim(),
                )
            }
            .toList()
}
