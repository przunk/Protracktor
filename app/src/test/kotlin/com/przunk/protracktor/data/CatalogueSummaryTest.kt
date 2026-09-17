// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a catalogue index is out of date.
 *
 * `docs/STATUS.md` C9 and `docs/BACKLOG.md` A22: a Modland index built before libsidplayfp offers
 * no C64 music at all. An index is filtered **at index time** to the formats a backend can play,
 * so one built before a backend existed is permanently missing that backend's formats — 60,572 of
 * them here — and looks empty rather than stale.
 */
class CatalogueSummaryTest {

    /**
     * A catalogue whose rows are all unplayable here is still downloaded.
     *
     * The two branches that met in round 12 were each right on their own: the index keeps every row
     * the archive lists, and `indexed` meant "are any of them playable". Together they report a
     * fully downloaded catalogue as one that still needs downloading, on a build that happens to
     * play none of it — and offer a download that would change nothing.
     */
    @Test
    fun `an index this build cannot play is still an index`() {
        val held = CatalogueSummary(
            id = "somewhere",
            displayName = "Somewhere",
            trackCount = 0,
            indexedAt = 1L,
            archiveCount = 4_356,
            complete = true,
        )
        assertTrue(held.indexed)
        assertFalse("it must not be offered for download again", held.requiresIndex)
    }

    @Test
    fun `and one that was never fetched still needs fetching`() {
        val absent = CatalogueSummary(
            id = "somewhere",
            displayName = "Somewhere",
            trackCount = 0,
            indexedAt = null,
        )
        assertFalse(absent.indexed)
        assertTrue(absent.requiresIndex)
    }


    private val current = "openmpt:0.8.9;sc68:3.0.0b;asap:8.0.0;gme:0.6.5;sidplayfp:3.1.1"
    private val older = "openmpt:0.8.9;sc68:2.2.1;asap:8.0.0;gme:0.6.5"

    private fun summary(
        count: Int = 500_000,
        backends: String = current,
        onlineOnly: Boolean = false,
    ) = CatalogueSummary("modland", "Modland", count, 1L, onlineOnly, backends)

    @Test
    fun `an index built by the current decoders is not stale`() {
        assertFalse(summary().isStale(current))
    }

    @Test
    fun `an index built by a different set is stale`() {
        assertTrue(summary(backends = older).isStale(current))
    }

    @Test
    fun `an index that never recorded its decoders is treated as stale`() {
        // Empty means it predates the column — which is exactly the case that prompted it. Unknown
        // is how the problem looked, so unknown has to be reported.
        assertTrue(summary(backends = "").isStale(current))
    }

    @Test
    fun `a catalogue nobody has indexed is not stale, it is empty`() {
        // "Not indexed yet" and "indexed with the wrong decoders" are different sentences and the
        // row shows a different one for each. Saying both would be noise.
        assertFalse(summary(count = 0, backends = "").isStale(current))
        assertTrue(summary(count = 0, backends = "").requiresIndex)
    }

    @Test
    fun `a search-only catalogue is never stale`() {
        // The Mod Archive is queried live and holds no index, so there is nothing to go out of date.
        assertFalse(summary(count = 0, backends = "", onlineOnly = true).isStale(current))
        assertFalse(summary(count = 0, backends = "", onlineOnly = true).requiresIndex)
    }

    @Test
    fun `a downloaded catalogue stops requiring an index once it has tracks`() {
        assertFalse(summary(count = 1).requiresIndex)
    }
}
