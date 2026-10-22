// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class QueueSourceNavigationRulesTest {
    @Test fun `manual-only and external sources have no fabricated view`() {
        for (source in listOf(QueueSource.None, QueueSource.External)) {
            assertFalse(QueueSourceNavigation.hasView(source))
            assertNull(QueueSourceNavigation.destination(source, setOf(1), 1, BrowseState()))
        }
    }

    @Test fun `playlist destinations follow identity and reject a deleted playlist`() {
        assertEquals(QueueSourceView.PLAYLIST,
            QueueSourceNavigation.destination(QueueSource.Playlist(2, "Old name"), setOf(1, 2), 1, null))
        assertNull(QueueSourceNavigation.destination(QueueSource.Playlist(3), setOf(1, 2), 1, null))
    }

    @Test fun `Random requires the remembered session instead of silently starting a new one`() {
        assertEquals(QueueSourceView.RANDOM, QueueSourceNavigation.destination(QueueSource.Random(7), emptySet(), 7, null))
        assertNull(QueueSourceNavigation.destination(QueueSource.Random(6), emptySet(), 7, null))
        assertNull(QueueSourceNavigation.destination(QueueSource.Random(0), emptySet(), 0, null))
    }

    @Test fun `collection destinations need their captured route not a similar display label`() {
        val source = QueueSource.Collection(SessionSource.Search("same"))
        val saved = BrowseState(domain = BrowseDomain.SEARCH, query = "same", searchScope = SearchScope.Local)
        assertEquals(QueueSourceView.BROWSE, QueueSourceNavigation.destination(source, emptySet(), 0, saved))
        assertNull(QueueSourceNavigation.destination(source, emptySet(), 0, null))
        assertNull(QueueSourceNavigation.destination(source, emptySet(), 0, saved.copy(query = "different")))
    }

    @Test fun `restoration preserves live shared resources and invalidates old navigation ownership`() {
        val saved = BrowseState(domain = BrowseDomain.SEARCH, query = "saved", searched = true,
            searchScope = SearchScope.Online(setOf("modland")), tracks = listOf(TrackRef("old", "Old")),
            navigationVisit = 99, indexing = mapOf("stale" to "old"), storageBytes = 1L to 2L,
            arrivedByJump = true, returnToSavedPlaces = true)
        val current = BrowseState(navigationVisit = 120, indexing = mapOf("new" to "downloading"),
            storageBytes = 30L to 40L, held = HeldSets(trackMetadata = true), randomScope = RandomScope.Favourites,
            cachedHere = setOf("unrelated"), aheadFetching = setOf("unrelated"), trackMetadataCount = 80)
        val restored = QueueSourceNavigation.restore(saved, current)
        assertEquals(saved.tracks, restored.tracks)
        assertEquals(saved.query, restored.query)
        assertEquals(saved.searchScope, restored.searchScope)
        assertEquals(121L, restored.navigationVisit)
        assertEquals(current.indexing, restored.indexing)
        assertEquals(current.storageBytes, restored.storageBytes)
        assertEquals(current.held, restored.held)
        assertEquals(current.randomScope, restored.randomScope)
        assertEquals(current.trackMetadataCount, restored.trackMetadataCount)
        assertFalse(restored.arrivedByJump)
        assertFalse(restored.returnToSavedPlaces)
        assertTrue(restored.cachedHere.isEmpty())
        assertTrue(restored.aheadFetching.isEmpty())
    }

    @Test fun `History source gets a fresh scroll visit but retains the captured page and query`() {
        val saved = BrowseState(domain = BrowseDomain.HISTORY, historyVisit = 2, historyQuery = "song", historyPage = 3)
        val restored = QueueSourceNavigation.restore(saved, BrowseState(historyVisit = 9))
        assertEquals(10L, restored.historyVisit)
        assertEquals(3, restored.historyPage)
        assertEquals("song", restored.historyQuery)
    }
}
