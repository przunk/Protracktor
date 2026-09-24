// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
    fun `entering Search forgets the words as well as the results`() {
        // Entering Search a second time must not show the previous words still written in the
        // box: `tracks` is emptied here, so a query left behind is a query with nothing under it,
        // which reads as a search that found nothing rather than as a screen waiting for a new
        // one.
        val searched = BrowseState(
            domain = BrowseDomain.SEARCH,
            query = "zoolook",
            tracks = listOf(),
        )
        val fresh = BrowseNavigation.enteringDomain(searched, BrowseDomain.SEARCH)
        assertEquals("", fresh.query)
        assertTrue(fresh.tracks.isEmpty())
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
            searchScope = SearchScope.ByPlatform(setOf("amiga")),
            songLengthCount = 61157,
        )
        val fresh = BrowseNavigation.enteringDomain(withSettings, BrowseDomain.ONLINE)
        // Search scope and downloaded-data counts are the user's settings and the app's facts, not
        // a position in a hierarchy. Clearing them here would be a second bug wearing C6's clothes.
        assertEquals(SearchScope.ByPlatform(setOf("amiga")), fresh.searchScope)
        assertEquals(61157, fresh.songLengthCount)

        // **The query is deliberately not asserted here.** A scope is a setting and a count is a
        // fact; a query is neither -- it is the input that produced the results this function has
        // just thrown away, so keeping it would leave the two halves of one screen disagreeing.
        // The test above owns it.
    }

    @Test
    fun `a jump from search results keeps the search to come back to`() {
        val results = listOf(TrackRef(id = "https://modland.com/pub/modules/AHX/Pink/frog.ahx", title = "frog.ahx"))
        val search = BrowseState(
            domain = BrowseDomain.SEARCH, query = "frog", searchScope = SearchScope.Local, tracks = results,
        )
        val kept = BrowseNavigation.searchToReturnTo(search)
        assertEquals(search, kept)
        // Back from the folder: the same words, scope and rows, no longer a jump, not loading.
        val back = BrowseNavigation.returningTo(kept!!.copy(arrivedByJump = true, loading = true))
        assertEquals("frog", back.query)
        assertEquals(SearchScope.Local, back.searchScope)
        assertEquals(results, back.tracks)
        assertEquals(false, back.arrivedByJump)
        assertEquals(false, back.loading)
    }

    @Test
    fun `a jump from anywhere but a search has no search to come back to`() {
        assertEquals(null, BrowseNavigation.searchToReturnTo(BrowseState(domain = BrowseDomain.ONLINE)))
        assertEquals(null, BrowseNavigation.searchToReturnTo(BrowseState(domain = BrowseDomain.ROOT)))
        // A folder already reached by a jump is not a search, whatever the domain field says.
        assertEquals(null, BrowseNavigation.searchToReturnTo(BrowseState(domain = BrowseDomain.SEARCH, arrivedByJump = true)))
    }
}

