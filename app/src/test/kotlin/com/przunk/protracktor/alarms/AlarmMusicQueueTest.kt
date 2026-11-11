// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.R
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B62: tunes picked in *Add music* are prepared one at a time, each saying where it is. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmMusicQueueTest {
    private val a = TrackRef("content://a", "A")
    private val b = TrackRef("content://b", "B")

    @Test fun `a second pick waits while the first is prepared, then is prepared itself`() = runTest {
        val gates = mapOf(a.id to CompletableDeferred<String>(), b.id to CompletableDeferred())
        val started = mutableListOf<String>()
        val queue = AlarmMusicQueue(backgroundScope, room = { 64 }) { ref -> started += ref.id; gates.getValue(ref.id).await() }
        queue.add(a); queue.add(b); runCurrent()
        assertEquals(listOf(a.id), started)
        assertEquals(listOf(AlarmPickState.Preparing, AlarmPickState.Waiting), queue.picks.value.map { it.state })
        gates.getValue(a.id).complete("song-a"); runCurrent()
        assertEquals(listOf(a.id, b.id), started)
        assertEquals(AlarmPickState.Added("song-a"), queue.picks.value[0].state)
        gates.getValue(b.id).complete("song-b"); runCurrent()
        assertEquals(AlarmPickState.Added("song-b"), queue.picks.value[1].state)
    }

    @Test fun `picking the same tune again does nothing until it has failed`() = runTest {
        var calls = 0
        var fail = true
        val queue = AlarmMusicQueue(backgroundScope, room = { 64 }) { calls++; if (fail) error("unreadable") else "song" }
        queue.add(a); runCurrent()
        assertEquals(AlarmPickState.Failed(R.string.alarm_prepare_failed), queue.picks.value.single().state)
        fail = false
        queue.add(a); runCurrent()
        assertEquals(AlarmPickState.Added("song"), queue.picks.value.single().state)
        queue.add(a); runCurrent()
        assertEquals(2, calls)
    }

    @Test fun `a full library says so instead of preparing`() = runTest {
        var calls = 0
        val queue = AlarmMusicQueue(backgroundScope, room = { 0 }) { calls++; "song" }
        queue.add(a); runCurrent()
        assertEquals(AlarmPickState.Failed(R.string.alarm_music_full), queue.picks.value.single().state)
        assertEquals(0, calls)
    }
}
