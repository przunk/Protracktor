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

    @Test fun `what is below is the archive or the format, never an author's own tunes`() {
        val summary = CatalogueSummary(id = "modland", displayName = "Modland", trackCount = 1, indexedAt = 0L)
        val online = BrowseState(domain = BrowseDomain.ONLINE, openCatalogue = summary)
        assertEquals(BrowseFilter.Below.InCatalogue("modland", null), BrowseFilter.below(online))
        assertEquals(BrowseFilter.Below.InCatalogue("modland", "Protracker"),
            BrowseFilter.below(online.copy(openFormat = "Protracker")))
        assertEquals(null, BrowseFilter.below(online.copy(openFormat = "Protracker", openAuthor = "Nork")))
        val local = BrowseState(domain = BrowseDomain.LOCAL, folderAt = "Music/Atari",
            openFolder = GrantedFolder(uri = "content://x", displayName = "Music"))
        assertEquals(BrowseFilter.Below.InFolder("Music/Atari"), BrowseFilter.below(local))
        assertEquals(null, BrowseFilter.below(BrowseState(domain = BrowseDomain.SEARCH, openCatalogue = summary)))
        assertEquals(null, BrowseFilter.below(BrowseState(domain = BrowseDomain.ONLINE)))
    }

    @Test fun `the tunes below a folder are its subfolders' that match, not its own or a neighbour's`() {
        fun tune(name: String) = TrackRef("content://t/$name", name, fileName = name)
        val all = listOf(
            "Music" to tune("mod here.mod"),
            "Music/Atari" to tune("mod one.sap"),
            "Music/Atari/good" to tune("deep mod.sap"),
            "Music/Atari/good" to tune("other.sap"),
            "Musical" to tune("mod next door.mod"),
        )
        assertEquals(listOf("mod one.sap", "deep mod.sap"),
            BrowseFilter.inFoldersBelow(all, "Music", "MOD", limit = 10).map { it.title })
        assertEquals(listOf("deep mod.sap"), BrowseFilter.inFoldersBelow(all, "Music/Atari", "mod", 10).map { it.title })
        assertEquals(listOf("mod one.sap"), BrowseFilter.inFoldersBelow(all, "Music", "mod", limit = 1).map { it.title })
        assertEquals(emptyList<TrackRef>(), BrowseFilter.inFoldersBelow(all, "Music", "  ", 10))
        // Matched by what the row shows, a learned title included, and handed back as stored.
        val learned = BrowseFilter.inFoldersBelow(all, "Music", "bossa", 10) {
            if (it.title == "other.sap") it.copy(title = "Bossa Nova") else it
        }
        assertEquals(listOf("other.sap"), learned.map { it.title })
    }
}
