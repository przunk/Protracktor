// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The way back from a track to where it came from.
 *
 * `pathFrom` is the inverse of `urlFor`, and an inverse that is subtly wrong is worse than none:
 * it would send "more from this author" to a folder that does not exist, or to the wrong one.
 */
class CatalogueTest {

    @Test
    fun `a Modland path survives the trip out to a URL and back`() {
        val paths = listOf(
            "Protracker/4-Mat/elysium.mod",
            // Spaces, brackets and ampersands are all common in this archive's filenames.
            "Protracker/Jester (Volker Tripp)/elysium & friends.mod",
            "Ad Lib/Unknown/a+b.sng",
            "Future Composer 1.4/Mad Max/cool.fc14",
        )
        paths.forEach { path ->
            assertEquals(path, Modland.pathFrom(Modland.urlFor(path)))
        }
    }

    @Test
    fun `a catalogue does not claim another catalogue's reference`() {
        assertNull(Modland.pathFrom("asma://asma/Composers/Aki/Atari_Style.sap"))
        assertNull(Asma.pathFrom("https://modland.com/pub/modules/Protracker/4-Mat/elysium.mod"))
        // A local file is a document URI and belongs to nobody.
        assertNull(Modland.pathFrom("content://com.android.providers/document/1234"))
        assertNull(Asma.pathFrom("content://com.android.providers/document/1234"))
    }

    @Test
    fun `an ASMA path survives the trip out and back`() {
        val path = "asma/Composers/Pieczko_Szymon/SimCity_2000.sap"
        assertEquals(path, Asma.pathFrom(Asma.urlFor(path)))
    }

    @Test
    fun `a link is only offered where the catalogue actually publishes one`() {
        // Modland serves every file over HTTP, so the track URL is the shareable link.
        assertEquals(
            Modland.urlFor("Protracker/4-Mat/elysium.mod"),
            Modland.webUrlFor("Protracker/4-Mat/elysium.mod"),
        )
        // ASMA serves every file at its zip entry's own path too (measured 2026-09-11), so its link
        // is that file -- never the asma:// reference this phone reads it by.
        assertEquals("https://asma.atari.org/asma/Games/Abracadabra.sap", Asma.webUrlFor("asma/Games/Abracadabra.sap"))
        assertEquals("https://asma.atari.org/", Asma.homeUrl)
    }

    @Test
    fun `owning finds the catalogue a reference belongs to, and admits when there is none`() {
        assertEquals(Modland, Catalogue.owning(Modland.urlFor("Protracker/4-Mat/elysium.mod")))
        assertEquals(Asma, Catalogue.owning(Asma.urlFor("asma/Games/Abracadabra.sap")))
        assertEquals(
            ModArchive,
            Catalogue.owning("https://api.modarchive.org/downloads.php?moduleid=67183#dalezy-lotus_drei_remix.xm")
        )
        assertNull(Catalogue.owning("content://com.android.providers/document/1234"))
    }

    @Test
    fun `ModArchive webUrl points to module view page`() {
        val path = "67183#dalezy-lotus_drei_remix.xm"
        assertEquals(
            "https://modarchive.org/index.php?request=view_by_moduleid&query=67183",
            ModArchive.webUrlFor(path),
        )
        assertEquals(
            "https://api.modarchive.org/downloads.php?moduleid=67183#dalezy-lotus_drei_remix.xm",
            ModArchive.urlFor(path),
        )
    }

    /**
     * One row, field by field. `ModArchiveSearchTest` parses the page the site really returned; this
     * stays because it says which pattern produces which field, which a whole page does not.
     *
     * The heading is part of the fixture and not decoration: the parser refuses anything that is
     * not a results page, because the archive answers a fruitless search with ten unrelated modules
     * that carry download links of their own.
     */
    @Test
    fun `ModArchive parses search results HTML correctly`() {
        val sampleHtml = """
            <h1 class="site-wide-page-head-title">Search Results</h1>
            <tr>
            <td valign="top" width="75">
            <a href="https://api.modarchive.org/downloads.php?moduleid=67183#dalezy-lotus_drei_remix.xm" title="Download">
            <img class="inline" src="style/images/icons/world_go.png" alt="GRAB!" border="0"></a>
            <span class="format-icon">XM</span>
            </td>
            <td valign="top" width="200">
            <a class="standard-link" href="index.php?request=view_by_moduleid&amp;query=67183" title="lotus drei remix">dalezy-lotus_drei_remix.xm</a>
            </td>
            <td valign="top" width="300">
            <span class="module-listing">
            lotus drei remix
            </span>
            </td>
            </tr>
        """.trimIndent()

        val results = ModArchive.parseSearchResults(sampleHtml)
        assertEquals(1, results.size)
        val track = results[0]
        assertEquals("lotus drei remix", track.title)
        assertEquals("dalezy-lotus_drei_remix.xm", track.fileName)
        assertEquals("The Mod Archive/XM", track.subtitle)
        assertEquals("https://api.modarchive.org/downloads.php?moduleid=67183#dalezy-lotus_drei_remix.xm", track.id)
    }
}
