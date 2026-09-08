// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * Modland's own favourites list, as parsing and nothing else.
 *
 * The owner asked for "random, but only tunes considered good", and the honest reading of that is
 * **somebody else's judgement, published** — not a rating this app invents. Modland keeps a
 * favourites list; `audacious-uade-tools` republishes it as one tab-separated file, which is the
 * same repository and the same licence as the metadata table next door (GPL-2.0-or-later,
 * `docs/LICENSES.md`).
 *
 * ### The file, and the trap in it
 *
 * ```
 * 009bb8a…131\t1\t194050\tplayer\tuade\tSoundtracker II\t0\t123989\t8fb5eab1\t3bf87580\tProtracker/Nightlight/rainyday.mod
 * 088ee335…147\t2\t184445\tplayer
 * 0cbc167f…616\t10\t114599\tloop
 * ```
 *
 * **Two shapes of row, and only one of them names a file.** A tune's first subsong gets the full
 * eleven columns ending in the Modland path; its remaining subsongs get four, saying how long that
 * subsong is and whether it is silence, a loop or a player. Measured over the whole file on
 * 2026-09-08: 1,163 rows, **991 of eleven columns and 172 of four**, and the 991 paths are all
 * distinct. Taking the last field of every row would have produced 172 entries called `loop`,
 * `silence` and `player` — which would have matched nothing, indexed nothing, and looked exactly
 * like a list that was simply smaller than expected.
 *
 * So a row is taken only when it has [FULL_COLUMNS] fields, and the path is the last of them.
 *
 * ### What survives to become music
 *
 * The path is Modland's own, relative to `pub/modules/` — the identical string
 * `catalogue_tracks.path` holds, which is what lets this be a join rather than a second index.
 * Measured against Modland's `allmods.txt` on 2026-09-08: of the 991, **891 are still at the path
 * the archive publishes today** (a hundred have been renamed or removed since the list was
 * compiled) and **835 pass the extension filter an index is built through**. Nothing is dropped
 * here on that account: which favourites are playable is a fact about this build and this user's
 * downloads, so the whole list is stored and the join decides.
 *
 * **No Android imports on purpose**, like [SongDbMetadata] and [SongLengths] beside it: somebody
 * else's text format is the thing to test on the JVM rather than discover on a phone.
 */
object ModlandFavourites {

    /** How many tab-separated fields a row that names a file has. Shorter rows are subsong detail. */
    const val FULL_COLUMNS = 11

    /**
     * Every Modland path in the file, **lazily** and in the order it appears.
     *
     * A sequence for the same reason [SongDbMetadata.parse] is one, though the argument is weaker
     * here — a thousand strings would fit anywhere. It is a sequence so the store can write as it
     * reads, which is the shape that already exists.
     *
     * Duplicates are left in. The table's primary key settles them, and a parser that deduplicated
     * would need to hold the whole list to do it.
     */
    fun parse(lines: Sequence<String>): Sequence<String> = lines.mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size != FULL_COLUMNS) return@mapNotNull null
        fields.last().takeIf { it.isNotBlank() }
    }
}
