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
package com.przunk.protracktor.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayQueueTest {

    private fun queueOf(count: Int) = PlayQueue(
        tracks = (0 until count).map { TrackRef(id = "t$it", title = "Track $it") }
    )

    private fun PlayQueue.titleNow() = current?.title

    // --- R5: backward returns to what was actually played -------------------------------------

    @Test
    fun `backward in shuffle returns the previously played track, not another random one`() {
        // A fixed seed so the shuffled order is known; the property under test is about history,
        // not about which permutation came out.
        var q = queueOf(10).withShuffle(true, Random(1234))

        q = q.next()
        val first = q.titleNow()
        q = q.next()
        val second = q.titleNow()
        q = q.next()
        val third = q.titleNow()

        assertEquals(third, q.titleNow())
        q = q.previous()
        assertEquals(second, q.titleNow())
        q = q.previous()
        assertEquals(first, q.titleNow())
    }

    @Test
    fun `forward after backward replays the same track rather than picking a new one`() {
        // History has to DIVERGE from the play order for this to test anything. Built with plain
        // next() calls the two are identical, so removing the redo branch entirely still produced
        // the right answer by accident -- mutation testing caught exactly that. Jumping around with
        // startAt is what makes the two disagree.
        var q = queueOf(10)
        q = q.startAt(7).startAt(2).startAt(5)

        q = q.previous().previous()
        assertEquals("Track 7", q.titleNow())

        // Order-next from 7 would be 8. Redoing history gives 2, then 5.
        q = q.next()
        assertEquals("Track 2", q.titleNow())
        q = q.next()
        assertEquals("Track 5", q.titleNow())
    }

    @Test
    fun `backward at the very beginning stays put`() {
        var q = queueOf(3)
        q = q.next()
        val first = q.titleNow()
        assertFalse(q.hasPrevious)
        assertEquals(first, q.previous().titleNow())
    }

    // --- repeat modes -------------------------------------------------------------------------

    @Test
    fun `repeat off stops at the end`() {
        var q = queueOf(3)
        repeat(3) { q = q.next() }
        assertEquals("Track 2", q.titleNow())
        assertFalse(q.hasNext)
        assertEquals("Track 2", q.next().titleNow())
    }

    @Test
    fun `repeat playlist wraps around`() {
        var q = queueOf(3).withRepeat(RepeatMode.PLAYLIST)
        repeat(3) { q = q.next() }
        assertTrue(q.hasNext)
        assertEquals("Track 0", q.next().titleNow())
    }

    @Test
    fun `repeat one replays the track when it ends`() {
        var q = queueOf(3).withRepeat(RepeatMode.ONE)
        q = q.next().next()
        val stuck = q.titleNow()

        repeat(20) { q = q.onTrackEnded()!! }
        assertEquals(stuck, q.titleNow())
    }

    @Test
    fun `repeat one does not bury the history under its own loops`() {
        // Twenty loops of one track are one thing the user chose, not twenty things to step back
        // through. One press of previous must leave the track, however long it has been repeating.
        var q = queueOf(3).withRepeat(RepeatMode.ONE)
        q = q.next().next()
        repeat(20) { q = q.onTrackEnded()!! }

        assertEquals("Track 0", q.previous().titleNow())
    }

    @Test
    fun `repeat one does not disable the next button`() {
        // ONE governs what happens when a track ENDS. If it also captured next, the button would
        // visibly do nothing and there would be no way out except changing the mode first.
        var q = queueOf(3).withRepeat(RepeatMode.ONE)
        q = q.next()
        assertTrue(q.hasNext)
        assertEquals("Track 1", q.next().titleNow())
    }

    @Test
    fun `playback stops at the end of the playlist with repeat off`() {
        var q = queueOf(2)
        q = q.next().next()
        assertNull(q.onTrackEnded())
    }

    @Test
    fun `repeat mode cycles off - playlist - one - off`() {
        assertEquals(RepeatMode.PLAYLIST, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.PLAYLIST.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }

    // --- shuffle ------------------------------------------------------------------------------

    @Test
    fun `shuffle visits every track exactly once before the playlist ends`() {
        var q = queueOf(25).withShuffle(true, Random(7))
        val seen = mutableListOf<String>()
        while (q.hasNext) {
            q = q.next()
            seen += q.titleNow()!!
        }
        assertEquals(25, seen.size)
        assertEquals(25, seen.toSet().size)
    }

    @Test
    fun `turning shuffle on does not interrupt the current track`() {
        var q = queueOf(10)
        q = q.next().next()
        val playing = q.titleNow()
        assertEquals(playing, q.withShuffle(true, Random(3)).titleNow())
        assertEquals(playing, q.withShuffle(true, Random(3)).withShuffle(false).titleNow())
    }

    @Test
    fun `shuffle reshuffles on each lap rather than repeating one permutation`() {
        // Two laps of a 40-track playlist landing in the same order would be a fixed order wearing
        // shuffle's name. With 40 tracks the odds of a genuine coincidence are vanishing.
        var q = queueOf(40).withShuffle(true, Random(11)).withRepeat(RepeatMode.PLAYLIST)
        val lapOne = mutableListOf<String>()
        repeat(40) { q = q.next(); lapOne += q.titleNow()!! }
        val lapTwo = mutableListOf<String>()
        repeat(40) { q = q.next(); lapTwo += q.titleNow()!! }

        assertEquals(lapOne.toSet(), lapTwo.toSet())
        assertFalse("second lap repeated the first lap's order", lapOne == lapTwo)
    }

    // --- edges --------------------------------------------------------------------------------

    @Test
    fun `an empty queue has nothing to play and does not throw`() {
        val q = queueOf(0)
        assertNull(q.current)
        assertFalse(q.hasNext)
        assertFalse(q.hasPrevious)
        assertNull(q.next().current)
        assertNull(q.previous().current)
    }

    @Test
    fun `starting at a track makes it current and forward continues from there`() {
        var q = queueOf(5).startAt(3)
        assertEquals("Track 3", q.titleNow())
        q = q.next()
        assertEquals("Track 4", q.titleNow())
    }

    @Test
    fun `starting at a track discards forward history`() {
        var q = queueOf(5)
        q = q.next().next().next()
        q = q.previous()               // cursor now sits mid-history
        q = q.startAt(0)

        assertEquals("Track 0", q.titleNow())
        // Forward must continue from the new choice, not resume the abandoned branch.
        assertEquals("Track 1", q.next().titleNow())
    }
}
