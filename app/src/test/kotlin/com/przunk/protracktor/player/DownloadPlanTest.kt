// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Asma
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.Modland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the boxes in the download sheet come to. The rules are in [DownloadPlan]'s own comment. */
class DownloadPlanTest {

    @Test
    fun `the favourites come with Modland, and the SID lengths no longer do`() {
        // Decided 2026-09-21: Modland is one row and brings its own list of favourite paths. HVSC's
        // lengths moved to the song metadata box -- they answer "how long is this SID" about tunes
        // from anywhere, a granted folder included, not only about Modland's.
        assertEquals(listOf(Modland.id, DownloadKeys.FAVOURITES), DownloadPlan.stepsFor(setOf(Modland.id)))
    }

    @Test
    fun `the song metadata box is all three databases`() {
        // HVSC's SID lengths, then songdb's metadata and lengths -- one box, because nobody should
        // have to know which database says what about a file.
        assertEquals(
            listOf(DownloadKeys.SONG_LENGTHS, DownloadKeys.TRACK_METADATA),
            DownloadPlan.stepsFor(setOf(DownloadPlan.SONG_METADATA)),
        )
    }

    @Test
    fun `and come with nothing else`() {
        // The favourites describe Modland's files. Fetching them because somebody ticked the Atari
        // archive would answer a question nobody asked.
        assertEquals(listOf(Asma.id), DownloadPlan.stepsFor(setOf(Asma.id)))
    }

    @Test
    fun `the song metadata is last, whatever order it was ticked in`() {
        val steps = DownloadPlan.stepsFor(setOf(DownloadPlan.SONG_METADATA, Asma.id, Modland.id))
        assertEquals(DownloadKeys.TRACK_METADATA, steps.last())
        assertTrue("$steps starts with Modland", steps.first() == Modland.id)
    }

    @Test
    fun `nothing ticked downloads nothing`() {
        assertEquals(emptyList<String>(), DownloadPlan.stepsFor(emptySet()))
        assertEquals(0, DownloadPlan.megabytesFor(emptySet()))
    }

    @Test
    fun `an id nobody offers is ignored`() {
        // A selection outlives the sheet it was made in, and a catalogue can be switched off
        // between the two (`UnExoticA.ENABLED`).
        assertEquals(emptyList<String>(), DownloadPlan.stepsFor(setOf("a-catalogue-that-left")))
    }

    @Test
    fun `the live-only catalogue is never offered`() {
        val live = Catalogue.all.filter { it.isOnlineOnly }.map { it.id }
        assertTrue("there is a live-only catalogue to check", live.isNotEmpty())
        live.forEach { assertFalse(it in DownloadPlan.choices()) }
    }

    @Test
    fun `everything ticked costs what the one-press button says`() {
        assertEquals(
            DownloadSizes.EVERYTHING_MB,
            DownloadPlan.megabytesFor(DownloadPlan.choices().toSet()),
        )
    }
}
