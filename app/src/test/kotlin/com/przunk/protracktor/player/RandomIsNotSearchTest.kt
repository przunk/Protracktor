// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A dice pick playing is not a search playing, whatever the last list left in the state.
 *
 * **The defect this is written from**: play anything from Browse, then open Random. The tune
 * played and the playlist was on screen — the Random view opened and closed itself in the same
 * frame. `ProtracktorApp` closes it when search results take over playback (`docs/STATUS.md`
 * C49), and it asked [PlayerUiState.searchMode], which was `resultsQueue != null` alone. Nothing
 * cleared the queue that Browse had left behind, so the answer was yes while the dice was the
 * only thing playing.
 *
 * The transport had the rule right all along — `nextFile` reads `transient != null &&
 * !diceWaiting` as "the dice owns this" — and the derived property disagreed with it. Two readings
 * of one question is how a view closes itself.
 *
 * The digression stays a digression: while the dice waits under an author's list
 * (`docs/BACKLOG.md` A41) the list *is* driving next and previous, and search mode is still true.
 */
class RandomIsNotSearchTest {

    private fun ref(id: String) = TrackRef(id = id, title = id, fileName = id)

    private val results = PlayQueue(tracks = listOf(ref("a"), ref("b")))

    @Test
    fun `a dice pick playing over a stale results queue is not search mode`() {
        val state = PlayerUiState(resultsQueue = results, transient = ref("dice"))
        assertTrue("the fixture is a Random session", state.randomMode)
        assertFalse("and it must not also read as search", state.searchMode)
    }

    @Test
    fun `search results playing on their own still are`() {
        val state = PlayerUiState(resultsQueue = results)
        assertFalse(state.randomMode)
        assertTrue(state.searchMode)
    }

    @Test
    fun `and the dice waiting under an author's list still is`() {
        // A41: the list on screen owns next and previous while the dice waits underneath.
        val state = PlayerUiState(resultsQueue = results, transient = ref("dice"), diceWaiting = true)
        assertTrue(state.searchMode)
    }

    @Test
    fun `a file from another app is neither`() {
        val state = PlayerUiState(transient = ref("shared"), externalOpen = true)
        assertTrue(state.externalMode)
        assertFalse(state.randomMode)
        assertFalse(state.searchMode)
    }
}
