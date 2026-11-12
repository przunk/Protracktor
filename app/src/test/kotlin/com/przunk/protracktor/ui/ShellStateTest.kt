// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.runtime.saveable.SaverScope
import com.przunk.protracktor.player.BrowseDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Redesign E0/E1: where the shell is, as a stack of places, and the tab each belongs to. */
class ShellStateTest {
    @Test fun `Back walks the places in reverse, an author from Random from Browse`() {
        // Playlist -> Browse -> Random (from Browse) -> More from this author (Browse again).
        var state = ShellState().with(ShellScreen.BROWSE, true).with(ShellScreen.RANDOM, true).with(ShellScreen.BROWSE, true)
        assertEquals(listOf(ShellScreen.BROWSE, ShellScreen.RANDOM, ShellScreen.BROWSE), state.stack)
        assertEquals(ShellBack.BrowseUp, state.back())
        state = state.with(ShellScreen.BROWSE, false)          // leaving the author
        assertEquals(ShellScreen.RANDOM, state.shown)
        assertEquals(ShellBack.Close(ShellScreen.RANDOM), state.back())
        state = state.with(ShellScreen.RANDOM, false)
        assertEquals(ShellScreen.BROWSE, state.shown)
        state = state.with(ShellScreen.BROWSE, false)
        assertNull(state.shown)
        assertEquals(ShellBack.Nothing, state.back())
    }

    @Test fun `opening what already shows does not stack it twice`() {
        val state = ShellState().with(ShellScreen.SETTINGS, true).with(ShellScreen.SETTINGS, true)
        assertEquals(listOf(ShellScreen.SETTINGS), state.stack)
        assertEquals(state, state.with(ShellScreen.SCANNER, false))
    }

    @Test fun `a tab replaces the stack, and closes the queue`() {
        val deep = ShellState(stack = listOf(ShellScreen.BROWSE, ShellScreen.RANDOM), queue = true)
        assertEquals(listOf(ShellScreen.RANDOM), deep.tab(ShellScreen.RANDOM).stack)
        assertFalse(deep.tab(ShellScreen.RANDOM).queue)
        assertTrue(deep.tab(null).stack.isEmpty())
        assertEquals(BrowseDomain.SEARCH, deep.tab(ShellScreen.BROWSE, BrowseDomain.SEARCH).browseEntry)
        // Closing the Browse a tab opened forgets where the tab opened it.
        assertNull(deep.tab(ShellScreen.BROWSE, BrowseDomain.SEARCH).with(ShellScreen.BROWSE, false).browseEntry)
    }

    @Test fun `the marked tab follows what shows`() {
        assertEquals(ShellTab.PLAYLIST, ShellState().tabOf(BrowseDomain.ROOT))
        val browse = ShellState(stack = listOf(ShellScreen.BROWSE))
        assertEquals(ShellTab.BROWSE, browse.tabOf(BrowseDomain.ONLINE))
        assertEquals(ShellTab.SEARCH, browse.tabOf(BrowseDomain.SEARCH))
        // History and Saved places are Browse's own places.
        assertEquals(ShellTab.BROWSE, browse.tabOf(BrowseDomain.HISTORY))
        assertEquals(ShellTab.BROWSE, browse.tabOf(BrowseDomain.SAVED))
        assertEquals(ShellTab.RANDOM, ShellState(stack = listOf(ShellScreen.BROWSE, ShellScreen.RANDOM)).tabOf(BrowseDomain.ROOT))
        assertEquals(ShellTab.MORE, ShellState(stack = listOf(ShellScreen.SETTINGS)).tabOf(BrowseDomain.ROOT))
    }

    @Test fun `a rebuild keeps the stack, Now Playing and the entry, not the queue`() {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val before = ShellState(stack = listOf(ShellScreen.BROWSE, ShellScreen.RANDOM, ShellScreen.SETTINGS),
            queue = true, nowPlaying = true, browseEntry = BrowseDomain.HISTORY)
        val saved = with(ShellState.Saver) { scope.save(before) }!!
        val after = ShellState.Saver.restore(saved)!!
        assertEquals(before.stack, after.stack)
        assertTrue(after.nowPlaying)
        assertFalse(after.queue)
        assertEquals(BrowseDomain.HISTORY, after.browseEntry)
    }
}
