// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the empty playlist offers: Browse, or the download sheet.
 *
 * The rule is one line of [BrowseState] and it was wrong the first time, in a way no screenshot
 * would explain: `CatalogueSummary.indexed` is `trackCount > 0 || isOnlineOnly`, so The Mod
 * Archive — which never needs an index — made `any { it.indexed }` true on a phone holding
 * nothing. The offer that exists to catch the empty install never appeared on one.
 */
class EmptyScreenOfferTest {

    private fun summary(id: String, tracks: Int, onlineOnly: Boolean = false) =
        CatalogueSummary(
            id = id,
            displayName = id,
            trackCount = tracks,
            indexedAt = if (tracks > 0) 1L else null,
            isOnlineOnly = onlineOnly,
        )

    @Test
    fun `a fresh install has nothing to browse`() {
        val fresh = BrowseState(catalogues = listOf(summary("modland", 0), summary("asma", 0)))
        assertFalse(fresh.hasSomethingToBrowse)
    }

    @Test
    fun `and the live-only catalogue does not make it look otherwise`() {
        // The defect, as a test. The Mod Archive is present on every install from the first launch.
        val fresh = BrowseState(
            catalogues = listOf(summary("modland", 0), summary("modarchive", 0, onlineOnly = true)),
        )
        assertTrue("the fixture is the case being checked", fresh.catalogues.any { it.indexed })
        assertFalse(fresh.hasSomethingToBrowse)
    }

    @Test
    fun `one indexed catalogue is enough`() {
        val held = BrowseState(catalogues = listOf(summary("modland", 516_107)))
        assertTrue(held.hasSomethingToBrowse)
    }

    @Test
    fun `so is a granted folder, with no catalogue at all`() {
        // Somebody who only plays their own files must not be pushed towards a 49 MB download.
        val local = BrowseState(
            catalogues = listOf(summary("modland", 0)),
            folders = listOf(GrantedFolder(uri = "content://tree/music", displayName = "Music")),
        )
        assertTrue(local.hasSomethingToBrowse)
    }
}
