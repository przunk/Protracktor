// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPlacesTest {
    private val key = SavedPlaceKey("modland", "Protracker", "Artist")
    private val place = SavedPlace(key, "Modland", 1)
    private val destination = SavedPlaceDestination.Found(CatalogueSummary("modland", "Modland", 1, 1), listOf(TrackRef("a", "A")))

    private class Store : SavedPlaces.Store {
        val rows = linkedMapOf<SavedPlaceKey, SavedPlace>()
        var fail = false
        override suspend fun save(place: SavedPlace): Boolean {
            check(!fail)
            if (place.key in rows) return false
            rows[place.key] = place
            return true
        }
        override suspend fun list() = rows.values.toList()
        override suspend fun remove(key: SavedPlaceKey) { rows.remove(key) }
    }

    @Test fun `save list duplicate and removal do not resolve or open a location`() = runTest {
        val store = Store()
        val notices = mutableListOf<SavedPlaces.Notice>()
        val saved = SavedPlaces(backgroundScope, store,
            availability = { it.map { p -> SavedPlaceRow(p, SavedPlaceAvailability.AVAILABLE) } },
            resolve = { error("Must not read tracks") }, show = { _, _ -> error("Must not navigate") },
            isShowingList = { true }, notice = notices::add)
        saved.refresh(); runCurrent()
        assertFalse(saved.state.value.loading)
        saved.save(place); runCurrent()
        saved.save(place.copy(savedAt = 9)); runCurrent()
        assertEquals(listOf(place), saved.state.value.rows.map { it.place })
        saved.remove(key); runCurrent()
        assertTrue(saved.state.value.rows.isEmpty())
        assertEquals(listOf(SavedPlaces.Notice.SAVED, SavedPlaces.Notice.ALREADY_SAVED, SavedPlaces.Notice.REMOVED), notices)
        store.fail = true
        saved.save(place); runCurrent()
        assertEquals(SavedPlaces.Notice.FAILED, notices.last())
        assertFalse(saved.state.value.writing)
    }

    @Test fun `cancelled noncancellable location read cannot navigate after Back`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val opened = mutableListOf<SavedPlaceKey>()
        val saved = SavedPlaces(backgroundScope, Store(), { emptyList() },
            resolve = { withContext(NonCancellable) { gate.await() }; destination },
            show = { k, _ -> opened += k }, isShowingList = { true }, notice = {})
        saved.open(key); runCurrent()
        saved.cancelOpening()
        gate.complete(Unit); runCurrent()
        assertTrue(opened.isEmpty())
        assertNull(saved.state.value.opening)
    }

    @Test fun `missing group updates availability without deleting the bookmark or navigating`() = runTest {
        val store = Store().apply { rows[key] = place }
        val notices = mutableListOf<SavedPlaces.Notice>()
        val saved = SavedPlaces(backgroundScope, store,
            { it.map { p -> SavedPlaceRow(p, SavedPlaceAvailability.AVAILABLE) } },
            resolve = { SavedPlaceDestination.Unavailable(SavedPlaceAvailability.GROUP_MISSING) },
            show = { _, _ -> error("Must not navigate") }, isShowingList = { true }, notice = notices::add)
        saved.refresh(); runCurrent()
        saved.open(key); runCurrent()
        assertEquals(SavedPlaceAvailability.GROUP_MISSING, saved.state.value.rows.single().availability)
        assertEquals(place, store.rows[key])
        assertEquals(listOf(SavedPlaces.Notice.UNAVAILABLE), notices)
    }

    @Test fun `opening uses resolved contents and rejects results outside the saved list`() = runTest {
        var inList = true
        val opened = mutableListOf<Pair<SavedPlaceKey, SavedPlaceDestination.Found>>()
        val saved = SavedPlaces(backgroundScope, Store(), { emptyList() }, { destination },
            { k, d -> opened += k to d }, { inList }, {})
        saved.open(key); runCurrent()
        assertEquals(listOf(key to destination), opened)
        inList = false
        saved.open(key); runCurrent()
        assertEquals(1, opened.size)
    }

    @Test fun `save action exists only for exact supported author or game routes`() {
        val online = BrowseState(domain = BrowseDomain.ONLINE, openCatalogue = destination.summary,
            openFormat = "Protracker", openAuthor = "")
        assertEquals(key.copy(author = ""), online.saveablePlace())
        assertNull(online.copy(openAuthor = null).saveablePlace())
        assertNull(online.copy(domain = BrowseDomain.SEARCH).saveablePlace())
        assertNull(online.copy(domain = BrowseDomain.LOCAL).saveablePlace())
        assertNull(online.copy(openCatalogue = destination.summary.copy(id = "modarchive", isOnlineOnly = true)).saveablePlace())
        assertNull(online.copy(openCatalogue = destination.summary.copy(id = "removed")).saveablePlace())
    }
}
