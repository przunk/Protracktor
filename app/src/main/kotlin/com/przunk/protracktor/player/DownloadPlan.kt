// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Asma
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.net.UnExoticA

/**
 * What a ticked set of boxes actually downloads, and in what order.
 *
 * A pure function over ids, so the rules below can be tested without a screen, a network or a
 * database — which is the only way anybody will check them again.
 *
 * **Three rules, and each is a decision:**
 *
 * 1. **Modland brings its favourites.** They are Modland's own list of Modland paths and mean
 *    nothing without it, so the Modland box is one idea (decided 2026-09-21). HVSC's SID lengths
 *    used to ride here too; they moved to the song metadata box, because they answer "how long is
 *    this SID" about tunes from anywhere, not only from Modland.
 * 2. **The catalogues come first, in the order they are listed**, which puts Modland — the largest
 *    catalogue and the smallest download — at the front. Somebody who stops the run early still
 *    has the biggest thing in it.
 * 3. **The song metadata goes last**: HVSC's SID lengths and songdb's credits and lengths, one box,
 *    because nobody should have to know which database says what. It improves what is written
 *    *under* a title, which is worth less than the first catalogue that puts titles on screen.
 */
object DownloadPlan {

    /** The one box that is not a catalogue: every fact about files the files cannot carry. */
    const val SONG_METADATA = DownloadKeys.SONG_METADATA

    /**
     * Every box this phone can offer, in the order they are drawn.
     *
     * Catalogues with nothing to download — The Mod Archive, which is searched live — are not
     * offered, because there is nothing a tick could fetch.
     */
    fun choices(): List<String> =
        Catalogue.all.filter { !it.isOnlineOnly }.map { it.id } + SONG_METADATA

    /**
     * The steps [selected] comes to, as [DownloadKeys] and catalogue ids, in the order to run them.
     *
     * An id nobody offers is ignored rather than trusted: this crosses a screen boundary, and a
     * stale selection surviving a catalogue being switched off (`UnExoticA.ENABLED`) must not turn
     * into a download of something that no longer exists.
     */
    fun stepsFor(selected: Set<String>): List<String> {
        val offered = choices().toSet()
        val wanted = selected.filter { it in offered }
        val steps = mutableListOf<String>()
        for (catalogue in Catalogue.all.filter { !it.isOnlineOnly }) {
            if (catalogue.id !in wanted) continue
            steps += catalogue.id
            if (catalogue.id == Modland.id) steps += DownloadKeys.FAVOURITES
        }
        if (SONG_METADATA in wanted) {
            steps += DownloadKeys.SONG_LENGTHS
            steps += DownloadKeys.TRACK_METADATA
        }
        return steps
    }

    /** What [selected] will cost, in whole megabytes, as the button has to say before it is run. */
    fun megabytesFor(selected: Set<String>): Int =
        stepsFor(selected).sumOf { step ->
            when (step) {
                Modland.id -> DownloadSizes.MODLAND_MB
                DownloadKeys.SONG_LENGTHS -> DownloadSizes.SONG_LENGTHS_MB
                DownloadKeys.FAVOURITES -> DownloadSizes.FAVOURITES_MB
                DownloadKeys.TRACK_METADATA -> DownloadSizes.TRACK_METADATA_MB
                Asma.id -> DownloadSizes.ASMA_MB
                UnExoticA.id -> DownloadSizes.UNEXOTICA_MB
                else -> 0
            }
        }
}
