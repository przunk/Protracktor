// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import org.junit.Assert.assertEquals
import org.junit.Test

/** A61: the playlist's cover names what truly plays, not "search" for everything. */
class SessionSourceTest {

    @Test
    fun `a search is named with its words`() {
        assertEquals(SessionSource.Search("przunk"), SessionSource.of(BrowseState(domain = BrowseDomain.SEARCH, query = " przunk ")))
    }

    @Test
    fun `an online folder is named by its catalogue, format and author`() {
        val browse = BrowseState(
            domain = BrowseDomain.ONLINE,
            openCatalogue = CatalogueSummary(id = "modland", displayName = "Modland", trackCount = 1, indexedAt = 1L),
            openFormat = "Protracker",
            openAuthor = "4-Mat",
        )
        assertEquals(SessionSource.Folder("Modland / Protracker / 4-Mat"), SessionSource.of(browse))
    }

    @Test
    fun `a local folder is named by its own name, and History is History`() {
        assertEquals(
            SessionSource.Folder("Music"),
            SessionSource.of(BrowseState(domain = BrowseDomain.LOCAL, openFolder = GrantedFolder("content://tree/x", "Music"))),
        )
        assertEquals(SessionSource.History, SessionSource.of(BrowseState(domain = BrowseDomain.HISTORY)))
    }

    @Test
    fun `nothing is named where Browse lists no tunes`() {
        assertEquals(null, SessionSource.of(BrowseState(domain = BrowseDomain.ROOT)))
        assertEquals(null, SessionSource.of(BrowseState(domain = BrowseDomain.ONLINE)))
    }
}
