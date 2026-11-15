// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.PlaylistFile
import com.przunk.protracktor.net.Modland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * C143: The Mod Archive's tunes read "MOD · MOD", and catalogue tunes from an imported playlist
 * showed a download address. Both shapes they come in -- as the app gives them, and as an imported
 * playlist file stored them -- read right.
 */
class CatalogueLabelsTest {
    private val id = "https://api.modarchive.org/downloads.php?moduleid=48956#lotus3_4.mod"

    /** As The Mod Archive's search makes it. */
    private val searched = TrackRef(id = id, title = "L3_CD4-SpaceNinja", subtitle = "The Mod Archive/MOD",
        fileName = "lotus3_4.mod", author = "")

    /** As an import of an exported playlist stored it: author "MOD", host and address as location. */
    private val imported = TrackRef(id = id, title = "L3_CD4-SpaceNinja", subtitle = "https://api.modarchive.org",
        fileName = "downloads.php?moduleid=48956#lotus3_4.mod", author = "MOD")

    @Test fun `a searched tune has no author made of its format`() {
        assertEquals("", searched.displayAuthor)
    }

    @Test fun `an imported tune reads as a searched one`() {
        val fixed = imported.canonical()
        assertEquals("lotus3_4.mod", fixed.fileName)
        assertEquals("The Mod Archive", fixed.subtitle)
        assertEquals("", fixed.author)
        assertEquals("", fixed.displayAuthor)
        // Where the source line and Now Playing's File take the location from: no address.
        assertEquals("The Mod Archive/lotus3_4.mod", LearnedTitles.sourceLine(fixed))
        // Even read raw, the format is not its author.
        assertEquals("", imported.displayAuthor)
    }

    @Test fun `a real author stays, and the search's own subtitle is kept`() {
        val named = searched.copy(author = "Patrick Phelan").canonical()
        assertEquals("Patrick Phelan", named.displayAuthor)
        assertEquals("The Mod Archive/MOD", named.subtitle)
    }

    private val modlandId = Modland.urlFor("Pro Tracker 2/- unknown/intro sux.mod")

    @Test fun `a Modland tune as the app gives it is untouched`() {
        val own = TrackRef(id = modlandId, title = "INTRO SUX", subtitle = "Modland/Pro Tracker 2/- unknown",
            fileName = "intro sux.mod", author = "- unknown")
        assertSame(own, own.canonical())
    }

    @Test fun `an imported Modland tune shows where it is filed, not its address`() {
        val imported = TrackRef(id = modlandId, title = "INTRO SUX",
            subtitle = modlandId.substringBeforeLast('/'), fileName = modlandId.substringAfterLast('/'), author = "Lav")
        val fixed = imported.canonical()
        assertEquals("Modland/Pro Tracker 2/- unknown", fixed.subtitle)
        assertEquals("intro sux.mod", fixed.fileName)
        assertEquals("Lav", fixed.author)
        assertEquals("Modland/Pro Tracker 2/- unknown/intro sux.mod", LearnedTitles.sourceLine(fixed))
    }

    @Test fun `a tune of no catalogue is untouched`() {
        val local = TrackRef(id = "content://doc/42", title = "tucano '91",
            subtitle = "Music/Blo", fileName = "tucano '91.mod")
        assertSame(local, local.canonical())
        assertEquals("Blo", local.displayAuthor)
    }

    @Test fun `an exported playlist no longer writes the format as the author`() {
        val text = PlaylistFile.write("Favorites", listOf(searched))
        assertFalse(text, text.contains("MOD - L3_CD4-SpaceNinja"))
    }
}
