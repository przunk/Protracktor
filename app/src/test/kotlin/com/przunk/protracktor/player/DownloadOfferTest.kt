// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Online catalogues offers "Get some music to browse".
 *
 * **The defect this is written from**: the row appeared for a moment every time Online catalogues
 * opened, on a phone holding everything, and then vanished. The rule read HVSC's song lengths and
 * the track metadata as counts, and those counts are read only when the screen opens -- until then
 * they are zero, which is also what "not downloaded" looks like. `docs/STATUS.md` C71.
 */
class DownloadOfferTest {

    private val indexed = CatalogueSummary(
        id = "modland", displayName = "Modland", trackCount = 516_107, indexedAt = 1L,
    )
    private val missing = CatalogueSummary(
        id = "asma", displayName = "ASMA", trackCount = 0, indexedAt = null,
    )

    @Test
    fun `nothing is offered before the counts have been read`() {
        // The frame between the screen opening and the counts arriving: everything is held, and the
        // counts are still their defaults.
        val opening = BrowseState(catalogues = listOf(indexed), knowsWhatIsHeld = true)
        assertFalse(opening.offersDownloadEverything)
    }

    @Test
    fun `and nothing once they say everything is here`() {
        val held = BrowseState(
            catalogues = listOf(indexed), knowsWhatIsHeld = true, heldCountsKnown = true,
            songLengthCount = 61_157, trackMetadataCount = 300_000, songDbLengthCount = 476_919,
        )
        assertFalse(held.offersDownloadEverything)
    }

    @Test
    fun `a catalogue without its index is offered`() {
        val partial = BrowseState(
            catalogues = listOf(indexed, missing), knowsWhatIsHeld = true, heldCountsKnown = true,
            songLengthCount = 61_157, trackMetadataCount = 300_000, songDbLengthCount = 476_919,
        )
        assertTrue(partial.offersDownloadEverything)
    }

    @Test
    fun `so are the song lengths or the metadata, once it is known they are missing`() {
        val noLengths = BrowseState(
            catalogues = listOf(indexed), knowsWhatIsHeld = true, heldCountsKnown = true,
            songLengthCount = 0, trackMetadataCount = 300_000, songDbLengthCount = 476_919,
        )
        assertTrue(noLengths.offersDownloadEverything)
    }

    @Test
    fun `metadata fetched before the lengths existed still offers the lengths`() {
        // A52. The metadata tick now brings songdb's lengths as well, and a phone that took the
        // metadata before that holds half of it. Asking only about the metadata would never offer
        // the rest, and those phones' Amiga tunes would wait for a measurement for ever.
        val halfway = BrowseState(
            catalogues = listOf(indexed), knowsWhatIsHeld = true, heldCountsKnown = true,
            songLengthCount = 61_157, trackMetadataCount = 300_000, songDbLengthCount = 0,
        )
        assertFalse(halfway.songDbComplete)
        assertTrue(halfway.offersDownloadEverything)
    }

    @Test
    fun `the song metadata row is complete only with all three databases`() {
        // One button fetches HVSC's lengths and both halves of songdb (decided 2026-09-21).
        val all = BrowseState(songLengthCount = 61_157, trackMetadataCount = 380_282, songDbLengthCount = 476_919)
        assertTrue(all.songMetadataComplete)
        assertFalse(all.copy(songLengthCount = 0).songMetadataComplete)
        assertFalse(all.copy(songDbLengthCount = 0).songMetadataComplete)
    }

    @Test
    fun `the replay routines row is complete only with both sets`() {
        val both = BrowseState(replayCount = 98, playerCount = 176)
        assertTrue(both.replayRoutinesComplete)
        assertFalse(both.copy(playerCount = 0).replayRoutinesComplete)
        assertFalse(both.copy(replayCount = 0).replayRoutinesComplete)
    }
}
