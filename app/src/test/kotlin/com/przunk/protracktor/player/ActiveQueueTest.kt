// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import org.junit.Assert.*
import org.junit.Test

class ActiveQueueTest {
    private val tracks = listOf(TrackRef("a", "A"), TrackRef("b", "B"), TrackRef("c", "C"))
    private fun queue(source: QueueSource = QueueSource.Playlist(1), at: Int = 0) =
        ActiveQueue(source, PlayQueue(tracks).startAt(at))

    @Test fun `playlist and captured collection use identical finite transport`() {
        for (source in listOf(QueueSource.Playlist(1), QueueSource.Collection(SessionSource.Search("test")))) {
            val first = queue(source)
            val next = (first.next() as QueueStep.Play).queue
            assertEquals(tracks[1], next.current)
            assertEquals(source, next.source)
            assertEquals(tracks[0], (next.previous() as QueueStep.Play).queue.current)
            assertEquals(QueueStep.Stop, next.select(2).ended())
            assertEquals(QueueStep.Unchanged, next.select(2).next())
        }
    }

    @Test fun `repeat one applies at completion but never traps an explicit Next`() {
        for (source in listOf(QueueSource.Playlist(1), QueueSource.Collection(SessionSource.History), QueueSource.Random(8), QueueSource.External)) {
            val q = queue(source).let { it.copy(order = it.order.withRepeat(RepeatMode.ONE)) }
            assertEquals(QueueStep.Replay, q.ended())
            assertNotEquals(QueueStep.Replay, q.next())
        }
    }

    @Test fun `repeat all wraps finite collections without falling back to a playlist`() {
        val last = queue(QueueSource.Collection(SessionSource.Folder("artist")), 2)
        val repeating = last.copy(order = last.order.withRepeat(RepeatMode.PLAYLIST))
        val next = (repeating.ended() as QueueStep.Play).queue
        assertEquals(tracks[0], next.current)
        assertEquals(last.source, next.source)
    }

    @Test fun `Random requests its provider rather than advancing a dormant playlist`() {
        val first = queue(QueueSource.Random(7))
        assertEquals(QueueStep.RandomNext, first.next())
        assertEquals(QueueStep.RandomNext, first.ended())
        assertEquals(QueueStep.Unchanged, first.previous())
        assertEquals(QueueStep.RandomPrevious, first.select(1).previous())
        assertTrue(first.hasNext)
    }

    @Test fun `an external file has no neighbour and does not roll Random`() {
        val q = queue(QueueSource.External)
        assertFalse(q.hasNext)
        assertFalse(q.hasPrevious)
        assertEquals(QueueStep.Unchanged, q.next())
        assertEquals(QueueStep.Stop, q.ended())
    }

    @Test fun `source identity is independent of the current file and visible playlist`() {
        val a = queue()
        assertTrue(a.belongsTo(1))
        assertFalse(a.belongsTo(2))
        assertFalse(queue(QueueSource.Collection(SessionSource.History)).belongsTo(1))
        assertTrue(queue(QueueSource.Collection(SessionSource.History)).fromHistory)
        assertFalse(a.fromHistory)
    }

    @Test fun `queue selection and mode changes preserve the source snapshot`() {
        val original = queue(QueueSource.Collection(SessionSource.Search("old query")))
        val selected = original.select(2)
        assertEquals(tracks[0], original.current)
        assertEquals(tracks[2], selected.current)
        assertEquals(original.source, selected.source)
        assertEquals(tracks, selected.order.tracks)
    }
}
