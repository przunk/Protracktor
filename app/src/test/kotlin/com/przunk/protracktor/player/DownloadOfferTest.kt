// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a downloadable set counts as here: the rules the rows' ticks, their buttons and the
 * download sheet's pre-ticked boxes all read.
 *
 * Three defects are behind these, one after another. C71: an offer read counts that are zero
 * until the screen has read them, and flashed. C74: the replay rows did the same one screen down.
 * C75: the ticks then waited seconds for those counts. The answer is [HeldSets], a quick yes or no
 * per set, and nothing is claimed until it has arrived.
 */
class DownloadOfferTest {

    private val everything = HeldSets(
        songLengths = true, trackMetadata = true, songDbLengths = true, replays = true, players = true,
    )

    @Test
    fun `nothing is claimed before the quick look`() {
        // The frame between the screen opening and the answer: no tick, no "complete".
        val opening = BrowseState(held = null)
        assertFalse(opening.songMetadataComplete)
        assertFalse(opening.replayRoutinesComplete)
    }

    @Test
    fun `the song metadata is complete only with all three databases`() {
        // One button fetches HVSC's lengths and both halves of songdb (decided 2026-09-21).
        assertTrue(BrowseState(held = everything).songMetadataComplete)
        assertFalse(BrowseState(held = everything.copy(songLengths = false)).songMetadataComplete)
        assertFalse(BrowseState(held = everything.copy(songDbLengths = false)).songMetadataComplete)
    }

    @Test
    fun `metadata fetched before the lengths existed is not complete`() {
        // A52 added songdb's lengths to the metadata download. A phone that took the metadata
        // before that holds half of it; calling that complete would never offer it the rest, and
        // its Amiga tunes would wait for a measurement for ever.
        val halfway = BrowseState(held = everything.copy(songDbLengths = false))
        assertFalse(halfway.songDbComplete)
        assertFalse(halfway.songMetadataComplete)
    }

    @Test
    fun `the replay routines are complete only with both sets`() {
        assertTrue(BrowseState(held = everything).replayRoutinesComplete)
        assertFalse(BrowseState(held = everything.copy(players = false)).replayRoutinesComplete)
        assertFalse(BrowseState(held = everything.copy(replays = false)).replayRoutinesComplete)
    }

    @Test
    fun `the counts play no part in it`() {
        // C75: the counts arrive seconds later. Here with every count still zero, the answer must
        // already be there.
        val early = BrowseState(held = everything, songLengthCount = 0, trackMetadataCount = 0, songDbLengthCount = 0)
        assertTrue(early.songMetadataComplete)
        assertTrue(early.replayRoutinesComplete)
    }
}
