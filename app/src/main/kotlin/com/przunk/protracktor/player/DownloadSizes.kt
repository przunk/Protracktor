// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * What the one-press download costs, so the button can say so before it is pressed.
 *
 * **Measured, not estimated** — `curl -I` against each address on 2026-09-17, in mebibytes:
 *
 * | | MB |
 * | --- | --- |
 * | Modland `allmods.zip` | 5.5 |
 * | ASMA `asma.zip` | 19.2 |
 * | UnExoticA `unexotica.tsv` | 1.7 |
 * | HVSC `Songlengths.md5` | 5.0 |
 * | songdb `metadata.tsv` | 14.1 |
 * | Modland favourites | 0.1 |
 *
 * Held here rather than read from `Content-Length` at the moment the screen draws, which would be
 * six requests to five hosts to put a number on a button, and would show nothing at all offline.
 * `DownloadSizesTest` keeps the total equal to the sum, so editing one line cannot quietly make the
 * button lie.
 *
 * They drift as the archives grow. A number a little low is a promise broken by a megabyte; if
 * these are ever re-measured, round **up**.
 */
object DownloadSizes {

    const val MODLAND_MB = 6
    const val ASMA_MB = 20
    const val UNEXOTICA_MB = 2
    const val SONG_LENGTHS_MB = 5
    const val TRACK_METADATA_MB = 15
    const val FAVOURITES_MB = 1

    /** What the button says. The parts are rounded up individually, so this is a ceiling. */
    const val EVERYTHING_MB =
        MODLAND_MB + ASMA_MB + UNEXOTICA_MB + SONG_LENGTHS_MB + TRACK_METADATA_MB + FAVOURITES_MB
}
