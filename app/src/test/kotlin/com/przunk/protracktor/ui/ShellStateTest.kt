// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Redesign E0: one state for where the shell is, with the flags' precedence and Back. */
class ShellStateTest {
    private val all = ShellState(open = ShellScreen.entries.toSet())

    @Test fun `the scanner hides Settings, which hides Browse, which hides Random`() {
        var state = all
        val shown = mutableListOf<ShellScreen?>()
        while (true) {
            shown += state.shown
            val top = state.shown ?: break
            state = state.with(top, false)
        }
        assertEquals(listOf(ShellScreen.SCANNER, ShellScreen.SETTINGS, ShellScreen.BROWSE, ShellScreen.RANDOM, null), shown)
    }

    @Test fun `Back acts on what shows, remembered Random last`() {
        assertEquals(ShellBack.Close(ShellScreen.SCANNER), all.back())
        assertEquals(ShellBack.Close(ShellScreen.SETTINGS), all.with(ShellScreen.SCANNER, false).back())
        // Browse opened from Random: Back is Browse's, not Random's.
        val browseOverRandom = ShellState(open = setOf(ShellScreen.BROWSE, ShellScreen.RANDOM))
        assertEquals(ShellBack.BrowseUp, browseOverRandom.back())
        assertEquals(ShellBack.RandomToBrowse, ShellState(open = setOf(ShellScreen.RANDOM)).back())
        assertEquals(ShellBack.Nothing, ShellState().back())
    }

    @Test fun `opening and closing one place leaves the others`() {
        val state = ShellState().with(ShellScreen.RANDOM, true).with(ShellScreen.BROWSE, true)
        assertTrue(state.isOpen(ShellScreen.RANDOM))
        assertEquals(ShellScreen.RANDOM, state.with(ShellScreen.BROWSE, false).shown)
        assertFalse(ShellState().covered)
        assertTrue(ShellState(queue = true).covered)
    }

    @Test fun `a rebuild keeps the places and Now Playing, not the queue`() {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val before = ShellState(open = setOf(ShellScreen.SETTINGS, ShellScreen.RANDOM), queue = true, nowPlaying = true)
        val saved = with(ShellState.Saver) { scope.save(before) }!!
        val after = ShellState.Saver.restore(saved)!!
        assertEquals(before.open, after.open)
        assertTrue(after.nowPlaying)
        assertFalse(after.queue)
        assertNull(ShellState.Saver.restore(emptyList())!!.shown)
    }
}
