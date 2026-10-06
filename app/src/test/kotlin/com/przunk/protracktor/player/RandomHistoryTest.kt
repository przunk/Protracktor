// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RandomHistoryTest {
    private fun tune(id: String) = TrackRef(id = id, title = id)

    private suspend fun heard(count: Int): RandomHistory = RandomHistory().also { history ->
        history.fill(count, 4) { (1..count).map { tune("heard-$it") } }
        repeat(count) { history.advance() }
    }

    @Test fun `changing scope from an earlier track preserves heard history and hides speculative picks`() = runBlocking {
        val history = RandomHistory()
        val heard = (1..10).map { tune("amiga-$it") }
        history.fill(11, 4) { heard + tune("old-prefetch") }
        repeat(10) { history.advance() }
        history.select(4)
        history.changeScope()

        assertEquals(heard, history.playedPicks)
        assertEquals(heard[4], history.current)
        val fresh = (1..4).map { tune("atari-$it") }
        history.fill(4, 4) { fresh }
        assertEquals("read-ahead is not history", heard, history.playedPicks)
        for (index in 5..9) {
            assertTrue(history.hasNext)
            assertEquals(heard[index], history.advance())
            assertEquals(index, history.cursor)
            assertEquals(heard, history.playedPicks)
        }
        assertEquals(fresh[0], history.advance())
        assertEquals(10, history.cursor)
        assertEquals(heard + fresh[0], history.playedPicks)
        assertEquals(fresh[1], history.advance())
        assertEquals(11, history.cursor)
        assertEquals(heard + fresh.take(2), history.playedPicks)
    }

    @Test fun `without a scope change Next walks the heard record without drawing`() = runBlocking {
        val history = heard(10)
        history.select(2)
        assertTrue(history.hasNext)
        assertEquals(tune("heard-4"), history.advance())
        assertEquals(3, history.cursor)
        assertEquals(10, history.playedPicks.size)
    }

    @Test fun `each advance publishes one new pick not its three read-ahead entries`() = runBlocking {
        val history = RandomHistory()
        history.fill(4, 4) { (1..4).map { tune("new-$it") } }
        assertTrue(history.playedPicks.isEmpty())
        assertEquals(tune("new-1"), history.advance())
        assertEquals(listOf(tune("new-1")), history.playedPicks)
        assertEquals((2..4).map { tune("new-$it") }, history.upcoming(3))
        history.fill(4, 4) { listOf(tune("new-5")) }
        assertEquals(tune("new-2"), history.advance())
        assertEquals((1..2).map { tune("new-$it") }, history.playedPicks)
        assertEquals(1, history.cursor)
    }

    @Test fun `empty new scope keeps the current tune and heard record`() = runBlocking {
        val history = heard(10)
        val before = history.playedPicks
        history.changeScope()
        history.fill(4, 4) { emptyList() }
        assertNull(history.advance())
        assertEquals(tune("heard-10"), history.current)
        assertEquals(9, history.cursor)
        assertEquals(before, history.playedPicks)
        history.fill(4, 4) { listOf(tune("new")) }
        assertEquals(tune("new"), history.advance())
        assertEquals(10, history.cursor)
    }

    @Test fun `empty new scope does not block walking remaining heard history`() = runBlocking {
        val history = heard(5)
        history.select(1)
        history.changeScope()
        history.fill(3, 4) { emptyList() }
        for (number in 3..5) assertEquals(tune("heard-$number"), history.advance())
        assertNull(history.advance())
        assertEquals(5, history.playedPicks.size)
    }

    @Test fun `background preparation targets the unheard tail rather than the current cursor`() = runBlocking {
        val history = heard(10)
        history.select(1)
        history.changeScope()
        var calls = 0
        history.fill(3, 4) { calls++; (1..3).map { tune("fresh-$it") } }
        history.fill(3, 4) { error("tail is already prepared") }
        assertEquals(1, calls)
        assertEquals(10, history.playedPicks.size)
        repeat(8) { history.advance() }
        assertEquals((1..3).map { tune("fresh-$it") }, history.upcoming(3))
    }

    @Test fun `overlapping fills share the same read-ahead capacity`() = runBlocking {
        val history = heard(2)
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<TrackRef>>()
        val first = launch { history.fill(3, 4) { entered.complete(Unit); result.await() } }
        entered.await()
        val second = launch { history.fill(3, 4) { error("first fill already prepared the tail") } }
        result.complete((1..3).map { tune("fresh-$it") })
        first.join()
        second.join()
        assertEquals((1..3).map { tune("fresh-$it") }, history.upcoming(10))
    }

    @Test fun `reroll preserves heard entries and does not move selection before a pick exists`() = runBlocking {
        val history = heard(5)
        history.select(1)
        history.reroll()
        assertEquals(tune("heard-2"), history.current)
        history.fill(4, 4) { listOf(tune("fresh")) }
        assertEquals(tune("fresh"), history.advance())
        assertEquals(6, history.playedPicks.size)
        assertEquals(tune("heard-5"), history.previous())
        assertEquals(tune("fresh"), history.advance())
    }

    @Test fun `unplayed entries cannot be selected or removed`() = runBlocking {
        val history = heard(2)
        history.fill(4, 4) { listOf(tune("ahead")) }
        assertNull(history.select(2))
        history.remove(2)
        assertEquals(tune("ahead"), history.advance())
        assertEquals(3, history.playedPicks.size)
        history.remove(0)
        assertEquals(1, history.cursor)
        assertEquals(listOf(tune("heard-2"), tune("ahead")), history.playedPicks)
    }

    @Test fun `scope changed during the first draw rejects old-scope results`() = runBlocking {
        val history = heard(2)
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<TrackRef>>()
        val job = launch { history.fill(4, 4) { entered.complete(Unit); result.await() } }
        entered.await()
        history.changeScope()
        result.complete(listOf(tune("stale")))
        job.join()
        assertTrue(history.upcoming(3).isEmpty())
        history.fill(4, 4) { listOf(tune("fresh")) }
        assertEquals(tune("fresh"), history.advance())
    }

    @Test fun `scope changed during small-pool fallback rejects its result too`() = runBlocking {
        val history = heard(2)
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<TrackRef>>()
        var calls = 0
        val job = launch {
            history.fill(4, 4) {
                if (++calls == 1) history.playedPicks
                else { entered.complete(Unit); result.await() }
            }
        }
        entered.await()
        history.changeScope()
        result.complete(listOf(tune("heard-1")))
        job.join()
        assertTrue(history.upcoming(3).isEmpty())
        assertEquals(2, history.playedPicks.size)
    }

    @Test(timeout = 10_000) fun `a new session rejects an old draw`() = runBlocking {
        val history = heard(2)
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<TrackRef>>()
        val job = launch { history.fill(4, 4) { entered.complete(Unit); result.await() } }
        entered.await()
        history.clear()
        val fresh = launch { history.fill(4, 4) { listOf(tune("new-session")) } }
        result.complete(listOf(tune("old-session")))
        job.join()
        fresh.join()
        assertEquals(tune("new-session"), history.advance())
        assertEquals(listOf(tune("new-session")), history.playedPicks)
        assertTrue(history.upcoming(3).isEmpty())
    }

    @Test fun `manual selection invalidates suspended advance work`() = runBlocking {
        val history = heard(5)
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<TrackRef>>()
        val generation = history.generation
        val job = launch { history.fill(4, 4) { entered.complete(Unit); result.await() } }
        entered.await()
        history.select(1)
        result.complete(listOf(tune("late")))
        job.join()
        assertTrue(history.generation != generation)
        assertEquals(tune("heard-2"), history.current)
        assertEquals(5, history.playedPicks.size)
        assertEquals(tune("heard-3"), history.advance())
    }

    @Test fun `cancelled draw leaves the record unchanged`() = runBlocking {
        val history = heard(2)
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<TrackRef>>()
        val job = launch { history.fill(4, 4) { entered.complete(Unit); result.await() } }
        entered.await()
        job.cancelAndJoin()
        result.complete(listOf(tune("cancelled")))
        assertEquals(2, history.playedPicks.size)
        assertTrue(history.upcoming(3).isEmpty())
    }

    @Test fun `draw deduplicates history and candidates but small pools may repeat`() = runBlocking {
        val history = heard(2)
        val requests = mutableListOf<Int>()
        history.fill(4, 4) { count ->
            requests += count
            listOf(tune("heard-1"), tune("fresh"), tune("fresh"))
        }
        assertEquals(listOf(16), requests)
        assertEquals(listOf(tune("fresh")), history.upcoming(4))
        history.advance()
        history.fill(4, 4) { count -> requests += count; listOf(tune("heard-1")) }
        assertEquals(listOf(16, 16, 4), requests)
        assertEquals(tune("heard-1"), history.advance())
        assertEquals(4, history.playedPicks.size)
    }
}
