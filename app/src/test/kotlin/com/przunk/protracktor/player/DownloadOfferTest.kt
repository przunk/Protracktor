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
            songLengthCount = 61_157, trackMetadataCount = 300_000,
        )
        assertFalse(held.offersDownloadEverything)
    }

    @Test
    fun `a catalogue without its index is offered`() {
        val partial = BrowseState(
            catalogues = listOf(indexed, missing), knowsWhatIsHeld = true, heldCountsKnown = true,
            songLengthCount = 61_157, trackMetadataCount = 300_000,
        )
        assertTrue(partial.offersDownloadEverything)
    }

    @Test
    fun `so are the song lengths or the metadata, once it is known they are missing`() {
        val noLengths = BrowseState(
            catalogues = listOf(indexed), knowsWhatIsHeld = true, heldCountsKnown = true,
            songLengthCount = 0, trackMetadataCount = 300_000,
        )
        assertTrue(noLengths.offersDownloadEverything)
    }
}
