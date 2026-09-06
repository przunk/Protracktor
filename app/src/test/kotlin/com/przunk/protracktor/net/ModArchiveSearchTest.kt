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
package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mod Archive's search page, parsed from a copy of the real thing.
 *
 * This catalogue has no index to download: it is scraped, live, from somebody else's HTML. That is
 * the one source in the app that can break with nothing here changing, and `docs/STATUS.md` C15 is
 * what that looks like when it does — every search returning nothing, silently, because the failure
 * used to be swallowed.
 *
 * `app/src/test/resources/modarchive-search-elysium.html` is the page the site actually returned on
 * 2026-09-06 for `query=elysium`, saved unedited. Pinning the parser to a real page is the only
 * honest way to test a scraper: markup written from memory would pass forever and prove nothing.
 * When this test fails, the site has changed and the parser has to follow.
 */
class ModArchiveSearchTest {

    private fun page(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("modarchive-search-elysium.html"))
            .bufferedReader().use { it.readText() }

    @Test
    fun `both results are found in the page the site really returned`() {
        val results = ModArchive.parseSearchResults(page())
        assertEquals(2, results.size)

        val xm = results.first { it.fileName == "elysium_96.xm" }
        assertEquals("https://api.modarchive.org/downloads.php?moduleid=134890#elysium_96.xm", xm.id)
        // The title comes out of the listing span, and its HTML entity is decoded: the archive
        // writes `eLYSiuM &#039;96`, which is an apostrophe, not five characters of punctuation.
        assertEquals("eLYSiuM '96", xm.title)
        assertTrue("format should reach the subtitle", xm.subtitle.contains("XM"))

        val mod = results.first { it.fileName == "ELYSIUM.MOD" }
        assertEquals("elysium", mod.title)
        assertTrue(mod.subtitle.contains("MOD"))
    }

    /**
     * A page with download links that yields nothing is the shape a silent breakage takes.
     *
     * Stated as a test because it is the assumption the whole catalogue rests on: if the rows stop
     * being rows, every search returns zero and nothing else goes wrong.
     */
    @Test
    fun `a page with download links yields results`() {
        val html = page()
        assertTrue("the saved page should still contain links", html.contains("downloads.php"))
        assertTrue(ModArchive.parseSearchResults(html).isNotEmpty())
    }

    /** Nothing to parse is not a crash, and not a failure either — it is an empty answer. */
    @Test
    fun `an unrelated page yields nothing without complaining`() {
        assertTrue(ModArchive.parseSearchResults("<html><body>No results.</body></html>").isEmpty())
    }
}
