// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

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
        var q = queueOf(10).withShuffle(true, seed = 1234L)

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
    fun `in shuffle, forward after backward replays the same track rather than picking a new one`() {
        // History has to DIVERGE from the play order for this to test anything. Built with plain
        // next() calls within one order the two are identical, so removing the redo branch entirely
        // still produced the right answer by accident -- mutation testing caught exactly that.
        // Taps used to make them disagree; since 2026-09-25 a tap starts history afresh (the tapped
        // tune is the first). A lap does it now: repeat all deals a new order, and history runs
        // across both.
        var q = queueOf(4).withShuffle(true, seed = 42L).withRepeat(RepeatMode.PLAYLIST)
        val played = mutableListOf<String>()
        repeat(6) { q = q.next(); played += q.titleNow()!! }

        repeat(3) { q = q.previous() }
        assertEquals(played[2], q.titleNow())
        q = q.next()
        assertEquals(played[3], q.titleNow())
        q = q.next()
        assertEquals(played[4], q.titleNow())
        q = q.next()
        assertEquals(played[5], q.titleNow())
    }

    @Test
    fun `under shuffle the tapped tune is the first, and back stops there`() {
        var q = queueOf(10).withShuffle(true, seed = 42L).startAt(7).next().startAt(2)
        assertFalse("back stepped into what played before the tap", q.hasPrevious)
        q = q.next()
        assertEquals("Track 2", q.previous().titleNow())
    }

    // --- what the buttons mean without shuffle ---------------------------------------------------

    @Test
    fun `without shuffle, previous is the row above and not the last thing played`() {
        // Reported from a device as "next and previous behave randomly". They were walking the tap
        // history, which is right in shuffle and wrong here: the list is on screen, and a button
        // that disagrees with it looks broken however defensible its bookkeeping.
        var q = queueOf(10)
        q = q.startAt(7).startAt(2)

        assertEquals("Track 1", q.previous().titleNow())
        assertEquals("Track 3", q.startAt(2).next().titleNow())
    }

    @Test
    fun `without shuffle, previous stops at the top unless the playlist repeats`() {
        val top = queueOf(4).startAt(0)
        assertFalse(top.hasPrevious)
        assertEquals("Track 0", top.previous().titleNow())

        val wrapping = top.withRepeat(RepeatMode.PLAYLIST)
        assertTrue(wrapping.hasPrevious)
        assertEquals("Track 3", wrapping.previous().titleNow())
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
        var q = queueOf(25).withShuffle(true, seed = 7L)
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
        assertEquals(playing, q.withShuffle(true, seed = 3L).titleNow())
        assertEquals(playing, q.withShuffle(true, seed = 3L).withShuffle(false).titleNow())
    }

    @Test
    fun `shuffle reshuffles on each lap rather than repeating one permutation`() {
        // Two laps of a 40-track playlist landing in the same order would be a fixed order wearing
        // shuffle's name. With 40 tracks the odds of a genuine coincidence are vanishing.
        var q = queueOf(40).withShuffle(true, seed = 11L).withRepeat(RepeatMode.PLAYLIST)
        val lapOne = mutableListOf<String>()
        repeat(40) { q = q.next(); lapOne += q.titleNow()!! }
        val lapTwo = mutableListOf<String>()
        repeat(40) { q = q.next(); lapTwo += q.titleNow()!! }

        assertEquals(lapOne.toSet(), lapTwo.toSet())
        assertFalse("second lap repeated the first lap's order", lapOne == lapTwo)
    }

    /** Every tune [q] goes on to play with next, until it stops. */
    private fun walk(start: PlayQueue): List<String> {
        var q = start
        val seen = mutableListOf(q.titleNow()!!)
        while (q.hasNext && seen.size < 1000) {
            q = q.next()
            seen += q.titleNow()!!
        }
        return seen
    }

    @Test
    fun `a tune chosen under shuffle starts a fresh order that plays every tune once`() {
        // The owner, 2026-09-25, on a playlist of 41: from wherever he started, next next next
        // ended on the same tune, and some were never played. The order was one permutation
        // fixed for the session; a tapped tune sat somewhere in its middle, and next walked only
        // what came after it.
        for (tapped in listOf(0, 7, 20, 40)) {
            val played = walk(queueOf(41).withShuffle(true, seed = 5L).startAt(tapped))
            assertEquals("from track $tapped", "Track $tapped", played.first())
            assertEquals("from track $tapped, every tune once: $played", 41, played.size)
            assertEquals("from track $tapped, no tune twice", 41, played.toSet().size)
        }
    }

    @Test
    fun `choosing the same tune again deals a new order rather than the same one`() {
        val shuffled = queueOf(41).withShuffle(true, seed = 5L)
        val orders = (1..5).map { walk(shuffled.startAt(20)) }
        assertTrue("five taps gave one order: ${orders.first()}", orders.toSet().size > 1)
    }

    @Test
    fun `shuffle turned on part way plays every other tune once from there`() {
        val playing = queueOf(30).next().next().next()
        val played = walk(playing.withShuffle(true, seed = 9L))
        assertEquals(playing.titleNow(), played.first())
        assertEquals(30, played.size)
        assertEquals(30, played.toSet().size)
    }

    @Test
    fun `a tune's title learnt while playing does not deal the order again`() {
        // The title is replaced by the tune's own name once it has been opened, which replaces the
        // track list -- and a new order at every tune would undo "every tune once".
        var q = queueOf(12).withShuffle(true, seed = 4L).startAt(3)
        val ahead = walk(q)
        q = q.withTracks(q.tracks.map { if (it.id == "t3") it.copy(title = "Track 3") else it })
        assertEquals(ahead, walk(q))
    }

    // --- the invariant that broke on a device ---------------------------------------------------

    @Test
    fun `a queue built empty and filled afterwards can play`() {
        // `order` must not be a constructor property defaulting to tracks.indices: copy() does
        // not re-evaluate default arguments, so a queue created empty and then given tracks keeps
        // the empty order and the first next() indexes into nothing.
        val filled = PlayQueue(tracks = emptyList())
            .withTracks((0 until 3).map { TrackRef(id = "t$it", title = "Track $it") })

        assertTrue(filled.hasNext)
        assertEquals("Track 0", filled.next().titleNow())
    }

    @Test
    fun `copying in a new track list keeps the order consistent`() {
        // Same invariant reached the other way. copy() is public on a data class and cannot be
        // taken away, so the order has to be derived rather than stored.
        val filled = PlayQueue(tracks = emptyList())
            .copy(tracks = (0 until 3).map { TrackRef(id = "t$it", title = "Track $it") })

        assertTrue(filled.hasNext)
        assertEquals("Track 0", filled.next().titleNow())
    }

    @Test
    fun `shrinking the track list drops history that no longer addresses anything`() {
        // Shuffle, because that is the mode where history is what previous walks. Without it
        // previous means the row above and would say nothing about whether history was pruned.
        var q = queueOf(5).withShuffle(true, seed = 5L)
        q = q.startAt(4).startAt(1)

        q = q.withTracks(q.tracks.take(2))
        assertEquals("Track 1", q.titleNow())
        assertFalse("history should have lost the entry for the removed track", q.hasPrevious)
    }

    @Test
    fun `removing an earlier track keeps the right one playing`() {
        // Removal shifts every later index by one. History held as raw positions would keep
        // pointing at the same NUMBERS and therefore at different tracks -- a wrong answer that
        // looks entirely plausible on screen.
        var q = queueOf(5).startAt(3)
        assertEquals("Track 3", q.titleNow())

        q = q.withTracks(q.tracks.filterIndexed { index, _ -> index != 1 })

        assertEquals("Track 3", q.titleNow())
        assertEquals("Track 4", q.next().titleNow())
    }

    @Test
    fun `removing the playing track leaves nothing current rather than the wrong thing`() {
        var q = queueOf(4).startAt(2)
        q = q.withTracks(q.tracks.filterIndexed { index, _ -> index != 2 })
        assertNull(q.current)
    }

    // --- reading ahead ---------------------------------------------------------------------------

    @Test
    fun `upcoming is what next would land on, without moving`() {
        val q = queueOf(5).startAt(1)
        assertEquals("Track 2", q.upcoming?.title)
        // Asking must not advance anything: the whole point is to read ahead while this one plays.
        assertEquals("Track 1", q.titleNow())
        assertEquals(q.upcoming?.title, q.next().titleNow())
    }

    @Test
    fun `upcoming is nothing at the end of the playlist`() {
        val q = queueOf(3).startAt(2)
        assertNull(q.upcoming)
        assertEquals("Track 0", q.withRepeat(RepeatMode.PLAYLIST).upcoming?.title)
    }

    @Test
    fun `upcoming follows the shuffled order, not the list order`() {
        var q = queueOf(20).withShuffle(true, seed = 77L)
        q = q.next()
        assertEquals(q.next().titleNow(), q.upcoming?.title)
    }

    // --- telling one file from two -----------------------------------------------------------

    @Test
    fun `the same file reached by two different URIs counts as one track`() {
        // Reported from a device: the same track could be added twice. The storage access framework
        // hands out a different document URI for the same file depending on how it was reached, so
        // comparing ids alone lets a duplicate straight through.
        val viaFolder = TrackRef("content://tree/x/document/x%3AMusic%2Fa.mod", "a.mod", "Music", 4096)
        val viaPicker = TrackRef("content://com.android.providers/document/1234", "a.mod", "", 4096)

        assertTrue(viaFolder.sameFileAs(viaPicker))
        assertTrue(viaPicker.sameFileAs(viaFolder))
    }

    @Test
    fun `same name but a different size is a different track`() {
        val one = TrackRef("uri://1", "a.mod", "", 4096)
        val other = TrackRef("uri://2", "a.mod", "", 8192)
        assertFalse(one.sameFileAs(other))
    }

    @Test
    fun `without a size, only the id can settle it`() {
        // Names alone are worthless here: half of Modland is called something.mod.
        val one = TrackRef("uri://1", "a.mod")
        val other = TrackRef("uri://2", "a.mod")
        assertFalse(one.sameFileAs(other))
        assertTrue(one.sameFileAs(one.copy(title = "A.MOD")))
    }

    @Test
    fun `adopting a tune's real name does not change which file it is`() {
        // Titles are rewritten from metadata once a track has been played. If identity followed the
        // title, a track would stop matching itself the moment it was first played, and the
        // duplicate check would let a second copy in.
        val scanned = TrackRef("uri://1", "4mat-elysium.mod", "Music/mods", 4096, "4mat-elysium.mod")
        val renamed = scanned.copy(title = "elysium")

        assertTrue(scanned.sameFileAs(renamed))
        assertTrue(renamed.sameFileAs(TrackRef("uri://2", "anything", "", 4096, "4MAT-ELYSIUM.MOD")))
    }

    @Test
    fun `the author falls back to the folder a track came from`() {
        // This music is filed by author far more often than it is tagged with one, so the folder is
        // usually the right answer until the file has been opened.
        val untagged = TrackRef("uri://1", "elysium.mod", "Music/mods/4-Mat", 4096, "elysium.mod")
        assertEquals("4-Mat", untagged.displayAuthor)

        val tagged = untagged.copy(author = "Rob Hubbard")
        assertEquals("Rob Hubbard", tagged.displayAuthor)
    }

    @Test
    fun `a catalogue subtitle yields its author half`() {
        val fromCatalogue = TrackRef("https://x/y", "a.mod", "Protracker · 4-Mat")
        assertEquals("4-Mat", fromCatalogue.displayAuthor)
    }

    @Test
    fun `reordering keeps the right track playing`() {
        // Same guarantee removal needed, for the same reason: history holds indices, and moving a
        // row changes what every index after it means.
        var q = queueOf(5).startAt(3)
        val playing = q.titleNow()

        val moved = q.tracks.toMutableList().apply { add(0, removeAt(4)) }
        q = q.withTracks(moved)

        assertEquals(playing, q.titleNow())
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
