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
package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entering a domain from the top.
 *
 * `docs/STATUS.md` C6: choosing **Online catalogues** after having browsed deep into one showed a
 * folder with nothing in it, or nothing at all. This is the rule that was missing, and it is a
 * separate object so that it could be tested at all — `PlaybackController` needs a `Context` and
 * there is no emulator here.
 */
class BrowseNavigationTest {

    private val deepInsideOnline = BrowseState(
        domain = BrowseDomain.ONLINE,
        openCatalogue = CatalogueSummary("modland", "Modland", 500_000, 1L),
        openFormat = "Protracker",
        openAuthor = "4-Mat",
        groups = listOf(CatalogueGroup("Protracker", 12)),
        tracks = listOf(TrackRef(id = "https://x/y.mod", title = "y.mod")),
        arrivedByJump = true,
    )

    @Test
    fun `entering a domain forgets where you had got to`() {
        val fresh = BrowseNavigation.enteringDomain(deepInsideOnline, BrowseDomain.ONLINE)

        // The three that survived, which is what produced a folder view with an emptied list.
        assertNull(fresh.openCatalogue)
        assertNull(fresh.openFormat)
        assertNull(fresh.openAuthor)
        assertEquals(BrowseDomain.ONLINE, fresh.domain)
        assertTrue(fresh.groups.isEmpty())
        assertTrue(fresh.tracks.isEmpty())
    }

    @Test
    fun `a local folder is forgotten too`() {
        val inFolder = BrowseState(
            domain = BrowseDomain.LOCAL,
            openFolder = GrantedFolder("uri://music", "Music"),
            folderUnscanned = true,
            folderStale = true,
        )
        val fresh = BrowseNavigation.enteringDomain(inFolder, BrowseDomain.LOCAL)
        assertNull(fresh.openFolder)
        // Notices about a folder must not outlive the folder they were about.
        assertFalse(fresh.folderUnscanned)
        assertFalse(fresh.folderStale)
    }

    @Test
    fun `choosing a domain by hand ends a jump`() {
        // A jump is a place you were put by "more from this author", and back from one returns to
        // the playlist (`docs/ARCHITECTURE.md` §17). Walking somewhere yourself ends that.
        assertFalse(BrowseNavigation.enteringDomain(deepInsideOnline, BrowseDomain.SEARCH).arrivedByJump)
    }

    @Test
    fun `things that are not about where you are survive`() {
        val withSettings = deepInsideOnline.copy(
            query = "elysium",
            searchScope = SearchScope.ByPlatform(setOf("amiga")),
            songLengthCount = 61157,
        )
        val fresh = BrowseNavigation.enteringDomain(withSettings, BrowseDomain.ONLINE)
        // Search scope and downloaded-data counts are the user's settings and the app's facts, not
        // a position in a hierarchy. Clearing them here would be a second bug wearing C6's clothes.
        assertEquals("elysium", fresh.query)
        assertEquals(SearchScope.ByPlatform(setOf("amiga")), fresh.searchScope)
        assertEquals(61157, fresh.songLengthCount)
    }
}
