/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a catalogue index is out of date.
 *
 * `docs/STATUS.md` C9 and `docs/BACKLOG.md` A22, from a real morning: the owner could find no C64
 * music at all, because his Modland index predated libsidplayfp. An index is filtered **at index
 * time** to the formats a backend can play, so one built before a backend existed is permanently
 * missing that backend's formats — 60,572 of them here — and looks empty rather than stale.
 */
class CatalogueSummaryTest {

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
