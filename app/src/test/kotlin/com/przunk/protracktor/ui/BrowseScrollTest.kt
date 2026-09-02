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
package com.przunk.protracktor.ui

import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which level of Browse you are on, and remembering the row you left it by.
 *
 * `docs/BACKLOG.md` A20: descending into an author's folder and coming back put you at the top of
 * the author list. The identity of a level, and of the row you came out of, are the two things that
 * had to exist before it could be fixed — so they are the two things tested here.
 */
class BrowseScrollTest {

    private val modland = CatalogueSummary("modland", "Modland", 100, null)

    @Test
    fun `each level of a catalogue is a different level`() {
        val root = BrowseState(domain = BrowseDomain.ONLINE)
        val inCatalogue = root.copy(openCatalogue = modland)
        val inFormat = inCatalogue.copy(openFormat = "Protracker")
        val inAuthor = inFormat.copy(openAuthor = "4-Mat")

        val keys = listOf(root, inCatalogue, inFormat, inAuthor).map { it.levelKey() }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("online", keys[0])
        assertEquals("online/modland/Protracker/4-Mat", keys[3])
    }

    @Test
    fun `the same place reached twice is the same level`() {
        val a = BrowseState(domain = BrowseDomain.ONLINE, openCatalogue = modland, openFormat = "MOD")
        val b = BrowseState(domain = BrowseDomain.ONLINE, openCatalogue = modland, openFormat = "MOD")
        assertEquals(a.levelKey(), b.levelKey())
    }

    @Test
    fun `two folders are two levels, and so are two searches`() {
        val one = BrowseState(domain = BrowseDomain.LOCAL, openFolder = GrantedFolder("uri://a", "A"))
        val two = BrowseState(domain = BrowseDomain.LOCAL, openFolder = GrantedFolder("uri://b", "B"))
        assertNotEquals(one.levelKey(), two.levelKey())

        // Searching for something else is a different list, not a scrolled one: restoring the old
        // offset onto new results would put you somewhere meaningless.
        assertNotEquals(
            BrowseState(domain = BrowseDomain.SEARCH, query = "cat").levelKey(),
            BrowseState(domain = BrowseDomain.SEARCH, query = "dog").levelKey(),
        )
    }

    @Test
    fun `a pending return is remembered until it is used`() {
        val scroll = BrowseScroll()
        assertNull(scroll.pendingReturn("online"))

        scroll.descendingFrom("online", "modland")
        // Asking does not consume it: the list may not have loaded yet, and a marker thrown away
        // before it could be used is the bug this is meant to prevent.
        assertEquals("modland", scroll.pendingReturn("online"))
        assertEquals("modland", scroll.pendingReturn("online"))

        scroll.returned("online")
        assertNull(scroll.pendingReturn("online"))
    }

    @Test
    fun `levels do not share their pending returns`() {
        val scroll = BrowseScroll()
        scroll.descendingFrom("online", "modland")
        scroll.descendingFrom("online/modland", "Protracker")
        assertEquals("modland", scroll.pendingReturn("online"))
        assertEquals("Protracker", scroll.pendingReturn("online/modland"))

        scroll.returned("online/modland")
        // Coming back one level must not forget where you were on the level above it.
        assertEquals("modland", scroll.pendingReturn("online"))
    }
}
