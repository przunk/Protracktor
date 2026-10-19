// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single active source cannot simultaneously be Random, results and an external file. */
class RandomIsNotSearchTest {

    private fun ref(id: String) = TrackRef(id = id, title = id, fileName = id)

    private val results = PlayQueue(tracks = listOf(ref("a"), ref("b")))

    @Test
    fun `a dice pick playing over a stale results queue is not search mode`() {
        val state = PlayerUiState(playback = randomPlayback(ref("dice")))
        assertTrue("the fixture is a Random session", state.randomMode)
        assertFalse("and it must not also read as search", state.searchMode)
    }

    @Test
    fun `search results playing on their own still are`() {
        val state = PlayerUiState(playback = collectionPlayback(results))
        assertFalse(state.randomMode)
        assertTrue(state.searchMode)
    }

    @Test
    fun `and the dice waiting under an author's list still is`() {
        // A41: the list on screen owns next and previous while the dice waits underneath.
        val state = PlayerUiState(playback = collectionPlayback(results), randomPicks = listOf(ref("dice")))
        assertTrue(state.searchMode)
    }

    @Test
    fun `a file from another app is neither`() {
        val state = PlayerUiState(playback = externalPlayback(ref("shared")))
        assertTrue(state.externalMode)
        assertFalse(state.randomMode)
        assertFalse(state.searchMode)
    }
}
