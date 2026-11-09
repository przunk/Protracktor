// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.CatalogueSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Browse's Search narrows the level on screen by the playlist's rule (2026-11-09). */
class BrowseFilterTest {
    private val authors = listOf("Nork", "Nooze", "Jogeir Liljedahl", "Michał", "4-Mat").map { CatalogueGroup(it, 3) }

    @Test fun `names match every word anywhere, in any case and without accents`() {
        assertEquals(listOf("Nork"), BrowseFilter.groups(authors, "NORK").map { it.name })
        assertEquals(listOf("Nork", "Nooze"), BrowseFilter.groups(authors, "no").map { it.name })
        assertEquals(listOf("Jogeir Liljedahl"), BrowseFilter.groups(authors, "lilje jog").map { it.name })
        assertEquals(listOf("Michał"), BrowseFilter.groups(authors, "michal").map { it.name })
        assertEquals(emptyList<String>(), BrowseFilter.groups(authors, "nork zzz").map { it.name })
    }

    @Test fun `an empty or blank query is the whole level`() {
        assertEquals(authors, BrowseFilter.groups(authors, ""))
        assertEquals(authors, BrowseFilter.groups(authors, "   "))
        val folders = listOf(LocalTree.Folder("Demos", "a/Demos", 2), LocalTree.Folder("Games", "a/Games", 1))
        assertEquals(folders, BrowseFilter.folders(folders, " "))
        assertEquals(listOf("Games"), BrowseFilter.folders(folders, "gam").map { it.name })
    }

    @Test fun `tunes are matched as the playlist matches them, by index into the level`() {
        val tunes = listOf(
            TrackRef(id = "amp://N/Nork/MOD.Nk-SaveTheFunky.gz", title = "MOD.Nk-SaveTheFunky", author = "Nork"),
            TrackRef(id = "amp://N/Nork/MOD.other.gz", title = "MOD.other", author = "Nork"),
        )
        assertEquals(listOf(0), BrowseFilter.tracks(tunes, "funky"))
        assertEquals(PlaylistSearch.indices(tunes, "nork"), BrowseFilter.tracks(tunes, "nork"))
        assertEquals(listOf(0, 1), BrowseFilter.tracks(tunes, ""))
    }

    @Test fun `only an open archive or folder has a list to narrow`() {
        val summary = CatalogueSummary(id = "amp", displayName = "AMP", trackCount = 1, indexedAt = 0L)
        assertTrue(BrowseFilter.filterable(BrowseState(domain = BrowseDomain.ONLINE, openCatalogue = summary)))
        assertTrue(BrowseFilter.filterable(BrowseState(domain = BrowseDomain.LOCAL,
            openFolder = GrantedFolder(uri = "content://x", displayName = "Music"))))
        for (domain in BrowseDomain.entries.filter { it != BrowseDomain.ONLINE && it != BrowseDomain.LOCAL })
            assertFalse(domain.name, BrowseFilter.filterable(BrowseState(domain = domain, openCatalogue = summary)))
        assertFalse(BrowseFilter.filterable(BrowseState(domain = BrowseDomain.ONLINE)))
        assertFalse(BrowseFilter.filterable(BrowseState(domain = BrowseDomain.LOCAL)))
    }
}
