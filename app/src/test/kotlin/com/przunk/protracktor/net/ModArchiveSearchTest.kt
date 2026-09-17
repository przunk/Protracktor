// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mod Archive's search page, parsed from a copy of the real thing.
 *
 * This catalogue has no index to download: it is scraped, live, from somebody else's HTML. That is
 * the one source in the app that can break with nothing here changing, and `docs/STATUS.md` C15 is
 * what that looks like when it does — every search returning nothing, silently, because the
 * failure was swallowed.
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

    /**
     * The archive's "no results" page is not a blank one, and that is the trap.
     *
     * It answers with **"Or perhaps enjoy some of these…"** and ten unrelated modules, each with a
     * working download link. A parser that only looked for download links returned all ten as
     * though they were matches — measured against the live site on 2026-09-06, searching for
     * `zzzzqqqq`. The page is saved here unedited.
     */
    @Test
    fun `a no-results page offers suggestions and none of them are results`() {
        val html = noResultsPage()
        assertTrue("the page really does carry download links", html.contains("downloads.php"))
        assertTrue("and it is not a results page", !html.contains("site-wide-page-head-title\">Search Results"))
        assertTrue("so nothing here is a match", ModArchive.parseSearchResults(html).isEmpty())
    }

    private fun noResultsPage(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("modarchive-search-no-results.html"))
            .bufferedReader().use { it.readText() }
}
