// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Redesign round 2: the bar's two chosen places, as plain Kotlin. */
class NavSlotsTest {
    private val all = NavSlots.available(alarms = true)

    @Test fun `the bar is Playlist, Browse, the two places and More, Search and Random at first`() {
        assertEquals(listOf(ShellTab.PLAYLIST, ShellTab.BROWSE, ShellTab.SEARCH, ShellTab.RANDOM, ShellTab.MORE), NavSlots.DEFAULT.bar)
    }

    @Test fun `choosing a free place puts it there and leaves the other`() {
        assertEquals(NavSlots(ShellTab.HISTORY, ShellTab.RANDOM), NavSlots.DEFAULT.choose(0, ShellTab.HISTORY))
        assertEquals(NavSlots(ShellTab.SEARCH, ShellTab.LOCAL), NavSlots.DEFAULT.choose(1, ShellTab.LOCAL))
    }

    @Test fun `choosing the other place's choice swaps the two, from either side`() {
        assertEquals(NavSlots(ShellTab.RANDOM, ShellTab.SEARCH), NavSlots.DEFAULT.choose(0, ShellTab.RANDOM))
        assertEquals(NavSlots(ShellTab.RANDOM, ShellTab.SEARCH), NavSlots.DEFAULT.choose(1, ShellTab.SEARCH))
    }

    @Test fun `choosing what is already there changes nothing`() {
        assertEquals(NavSlots.DEFAULT, NavSlots.DEFAULT.choose(0, ShellTab.SEARCH))
    }

    @Test fun `stored places come back, and anything unknown, unavailable or doubled falls back`() {
        assertEquals(NavSlots(ShellTab.HISTORY, ShellTab.ALARMS), NavSlots.restore("HISTORY", "ALARMS", all))
        assertEquals(NavSlots.DEFAULT, NavSlots.restore(null, null, all))
        assertEquals(NavSlots.DEFAULT, NavSlots.restore("NOPE", "PLAYLIST", all))
        // A build without alarms: each place falls back to its own default, unless the other holds it.
        assertEquals(NavSlots(ShellTab.HISTORY, ShellTab.RANDOM),
            NavSlots.restore("HISTORY", "ALARMS", NavSlots.available(alarms = false)))
        assertEquals(NavSlots(ShellTab.RANDOM, ShellTab.SEARCH), NavSlots.restore("ALARMS", "SEARCH", NavSlots.available(alarms = false)))
        assertEquals(NavSlots(ShellTab.SEARCH, ShellTab.RANDOM), NavSlots.restore("ALARMS", "ALARMS", NavSlots.available(alarms = false)))
        assertEquals(NavSlots(ShellTab.LOCAL, ShellTab.RANDOM), NavSlots.restore("LOCAL", "LOCAL", all))
    }

    @Test fun `Alarms is offered only where the build has music alarms`() {
        assertTrue(ShellTab.ALARMS in NavSlots.available(alarms = true))
        assertFalse(ShellTab.ALARMS in NavSlots.available(alarms = false))
    }

    @Test fun `a place on the bar is marked while it shows, one that is not marks Browse`() {
        val browse = ShellState().tab(ShellScreen.BROWSE)
        val history = NavSlots(ShellTab.HISTORY, ShellTab.LOCAL)
        assertEquals(ShellTab.HISTORY, browse.tabOf(BrowseDomain.HISTORY, history))
        assertEquals(ShellTab.LOCAL, browse.tabOf(BrowseDomain.LOCAL, history))
        assertEquals(ShellTab.BROWSE, browse.tabOf(BrowseDomain.SEARCH, history))
        assertEquals(ShellTab.BROWSE, browse.tabOf(BrowseDomain.ROOT, history))
        // Random without its place on the bar is reached from Browse, so Browse is marked.
        assertEquals(ShellTab.BROWSE, ShellState().tab(ShellScreen.RANDOM).tabOf(BrowseDomain.ROOT, history))
        assertEquals(ShellTab.RANDOM, ShellState().tab(ShellScreen.RANDOM).tabOf(BrowseDomain.ROOT, NavSlots.DEFAULT))
    }

    @Test fun `only the very top of a place opened from the bar lets Back leave`() {
        val top = BrowseState(domain = BrowseDomain.LOCAL)
        assertTrue(BrowseEntry.atTop(BrowseDomain.LOCAL, top))
        assertFalse(BrowseEntry.atTop(null, top))
        assertFalse(BrowseEntry.atTop(BrowseDomain.HISTORY, top))
        assertFalse(BrowseEntry.atTop(BrowseDomain.LOCAL, top.copy(openFolder = com.przunk.protracktor.data.GrantedFolder("content://tree/music", "Music"))))
        assertFalse(BrowseEntry.atTop(BrowseDomain.ONLINE, BrowseState(domain = BrowseDomain.ONLINE, openAuthor = "Jester")))
        val online = BrowseState(domain = BrowseDomain.ONLINE)
        assertTrue(BrowseEntry.atTop(BrowseDomain.ONLINE, online))
        assertFalse(BrowseEntry.atTop(BrowseDomain.ONLINE, online.copy(arrivedByJump = true)))
        val search = BrowseState(domain = BrowseDomain.SEARCH)
        assertTrue(BrowseEntry.atTop(BrowseDomain.SEARCH, search))
        assertFalse(BrowseEntry.atTop(BrowseDomain.SEARCH, search.copy(searchScope = SearchScope.Local)))
    }
}
