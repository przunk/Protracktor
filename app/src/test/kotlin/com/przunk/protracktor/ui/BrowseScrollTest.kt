// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
    fun `a list that still belongs to the level below is waited for, not acted on`() {
        val scroll = BrowseScroll()
        scroll.descendingFrom("online/modland", "Protracker")

        // Going back sets the new level immediately and fetches its contents asynchronously, so for
        // a moment the key says "formats" while the list still holds the authors from below.
        // Reading that as "Protracker is gone" threw the marker away, and the real list arrived
        // with nothing left to restore -- which is why back restored one level and then landed at
        // the top on the next.
        assertEquals(
            Restore.Wait,
            scroll.restoreFor("online/modland", listOf("4-Mat", "Jester"), loading = true),
        )
        assertEquals(Restore.Wait, scroll.restoreFor("online/modland", emptyList(), loading = false))

        // The marker has to survive both of those.
        assertEquals(
            Restore.ScrollTo(1),
            scroll.restoreFor("online/modland", listOf("Ad Lib", "Protracker"), loading = false),
        )
    }

    @Test
    fun `a row that has really gone is forgotten rather than kept forever`() {
        val scroll = BrowseScroll()
        scroll.descendingFrom("local/uri", "old.mod")
        // A list that genuinely belongs to this level and does not contain it: a rescan removed it.
        // Keeping the marker would jump the list on some later visit.
        assertEquals(
            Restore.Forget,
            scroll.restoreFor("local/uri", listOf("a.mod", "b.mod"), loading = false),
        )
    }

    @Test
    fun `a level with nothing pending is left alone`() {
        assertEquals(
            Restore.Nothing,
            BrowseScroll().restoreFor("online", listOf("modland"), loading = false),
        )
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
