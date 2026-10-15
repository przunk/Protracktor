// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseReadOwnerTest {
    @Test fun `capturing an intent does not retire the current reader when the intent cannot navigate`() {
        val browse = MutableStateFlow(BrowseState(domain = BrowseDomain.ONLINE, loading = true))
        val reader = BrowseReadOwner.begin(browse) { it }
        val before = browse.value
        val intent = BrowseReadOwner.capture(before)
        assertEquals(before, browse.value)
        reader.publish(browse) { it.copy(loading = false, query = "completed") }
        assertEquals("completed", browse.value.query)
        assertFalse(browse.value.loading)
        assertTrue(intent.matches(browse.value))
        BrowseReadOwner.begin(browse) { it }
        assertFalse(intent.matches(browse.value))
    }

    @Test fun `consecutive reads of an identical route have different owners`() {
        val browse = MutableStateFlow(BrowseState(domain = BrowseDomain.ONLINE))
        val first = BrowseReadOwner.begin(browse) { it }
        val second = BrowseReadOwner.begin(browse) { it }
        first.publish(browse) { it.copy(query = "obsolete") }
        assertEquals("", browse.value.query)
        assertFalse(first.matches(browse.value))
        assertTrue(second.matches(browse.value))
    }

    @Test fun `publication preserves unrelated changes and retries against current ownership`() {
        val browse = MutableStateFlow(BrowseState(domain = BrowseDomain.LOCAL))
        val owner = BrowseReadOwner.begin(browse) { it }
        browse.update { it.copy(favouriteCount = 17) }
        var calls = 0
        owner.publish(browse) {
            if (++calls == 1) browse.value = BrowseNavigation.enteringDomain(browse.value, BrowseDomain.HISTORY)
            it.copy(query = "obsolete", loading = true)
        }
        assertEquals(1, calls)
        assertEquals(BrowseDomain.HISTORY, browse.value.domain)
        assertEquals("", browse.value.query)
        assertEquals(17, browse.value.favouriteCount)
        assertFalse(browse.value.loading)
    }

    @Test fun `restoring a snapshot advances the current visit rather than reviving its old owner`() {
        val browse = MutableStateFlow(BrowseState())
        val owner = BrowseReadOwner.begin(browse) { it }
        val snapshot = browse.value
        repeat(3) { browse.value = BrowseNavigation.enteringDomain(browse.value, BrowseDomain.ONLINE) }
        val visit = browse.value.navigationVisit
        browse.value = BrowseNavigation.returningTo(snapshot, browse.value)
        assertEquals(visit + 1, browse.value.navigationVisit)
        assertFalse(owner.matches(browse.value))
    }

    @Test fun `changing a route without a visit cannot admit a completion from another route`() {
        val catalogue = CatalogueSummary("one", "One", 1, 1)
        val initial = BrowseState(domain = BrowseDomain.ONLINE, openCatalogue = catalogue,
            openFormat = "MOD", openAuthor = "Artist")
        val browse = MutableStateFlow(initial)
        val owner = BrowseReadOwner.begin(browse) { it }
        val current = browse.value
        for (other in listOf(current.copy(domain = BrowseDomain.SEARCH),
            current.copy(openCatalogue = catalogue.copy(id = "two")),
            current.copy(openFormat = "SID"), current.copy(openAuthor = "Other"))) {
            assertFalse(owner.matches(other))
        }
        owner.publish(browse) { it.copy(loading = false, query = "current") }
        assertEquals("current", browse.value.query)
    }
}
