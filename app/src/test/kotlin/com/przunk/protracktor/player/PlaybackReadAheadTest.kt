// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackReadAheadTest {
    @Test(timeout = 5_000)
    fun `a late result for the same id cannot replace bytes from the new request`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val fresh = byteArrayOf(2)
        var first = true
        val ahead = PlaybackReadAhead(this) {
            if (first) {
                first = false
                entered.complete(Unit)
                withContext(NonCancellable) { gate.await() }
                byteArrayOf(1)
            } else fresh
        }
        ahead.prepare(listOf(track("same")))
        entered.await()
        ahead.prepare(listOf(track("same")))
        yield()
        gate.complete(Unit)
        drain()
        assertSame(fresh, ahead.take("same"))
    }

    @Test(timeout = 5_000)
    fun `cancellation reentrancy cannot overwrite a newer preparation`() = runBlocking {
        val immediate = CoroutineScope(coroutineContext + Dispatchers.Unconfined)
        val calls = mutableListOf<String>()
        lateinit var ahead: PlaybackReadAhead
        ahead = PlaybackReadAhead(immediate) {
            calls += it.id
            if (it.id == "old") {
                try { awaitCancellation() }
                finally { ahead.prepare(listOf(track("reentrant"))) }
            } else byteArrayOf(1)
        }
        ahead.prepare(listOf(track("old")))
        ahead.prepare(listOf(track("outer")))
        drain()
        assertEquals(listOf("old", "reentrant"), calls)
        assertNull(ahead.take("outer"))
        assertTrue(ahead.take("reentrant") != null)
    }

    @Test(timeout = 5_000)
    fun `empty replacement and clear reject bytes from cancellation resistant reads`() = runBlocking {
        for (clear in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val ahead = PlaybackReadAhead(this) {
                entered.complete(Unit)
                withContext(NonCancellable) { gate.await() }
                byteArrayOf(9)
            }
            ahead.prepare(listOf(track("old")))
            entered.await()
            if (clear) ahead.clear() else ahead.prepare(emptyList())
            gate.complete(Unit)
            drain()
            assertNull(ahead.take("old"))
        }
    }

    private fun track(id: String) = TrackRef(id, id)
    private suspend fun CoroutineScope.drain() = coroutineContext.job.children.toList().joinAll()

    @Test(timeout = 5_000)
    fun `requested tracks read concurrently on the caller scope and each result is consumed once`() = runBlocking {
        val tracks = listOf("a", "b", "c").map(::track)
        val entered = Channel<String>(Channel.UNLIMITED)
        val gates = tracks.associate { it.id to CompletableDeferred<ByteArray>() }
        val caller = Thread.currentThread()
        val ahead = PlaybackReadAhead(this) { ref ->
            assertSame(caller, Thread.currentThread())
            entered.send(ref.id)
            gates.getValue(ref.id).await().also { assertSame(caller, Thread.currentThread()) }
        }
        ahead.prepare(tracks)
        assertEquals(setOf("a", "b", "c"), (1..3).map { entered.receive() }.toSet())
        val bytes = tracks.associate { it.id to byteArrayOf(it.id.first().code.toByte()) }
        tracks.reversed().forEach { gates.getValue(it.id).complete(bytes.getValue(it.id)) }
        drain()
        tracks.forEach {
            assertSame(bytes.getValue(it.id), ahead.take(it.id))
            assertNull(ahead.take(it.id))
        }
    }

    @Test
    fun `only wanted entries survive and retained bytes are not fetched until consumed`() = runBlocking {
        val calls = mutableListOf<String>()
        val bytes = listOf("a", "b", "c").associateWith { byteArrayOf(it.first().code.toByte()) }
        val ahead = PlaybackReadAhead(this) { ref -> calls += ref.id; bytes.getValue(ref.id) }
        ahead.prepare(listOf(track("a"), track("b")))
        drain()
        ahead.prepare(listOf(track("b"), track("c")))
        drain()
        assertEquals(listOf("a", "b", "c"), calls)
        assertNull(ahead.take("a"))
        assertSame(bytes.getValue("b"), ahead.take("b"))
        assertSame(bytes.getValue("c"), ahead.take("c"))
        ahead.prepare(listOf(track("b")))
        drain()
        assertEquals(listOf("a", "b", "c", "b"), calls)
        assertSame(bytes.getValue("b"), ahead.take("b"))
    }

    @Test
    fun `four megabyte files fit but larger files do not and null reads store nothing`() = runBlocking {
        val limit = ByteArray(4 * 1024 * 1024)
        val over = ByteArray(limit.size + 1)
        val empty = byteArrayOf()
        val payloads = linkedMapOf("limit" to limit, "over" to over, "missing" to null, "empty" to empty)
        val ahead = PlaybackReadAhead(this) { payloads.getValue(it.id) }
        ahead.prepare(payloads.keys.map(::track))
        drain()
        assertSame(limit, ahead.take("limit"))
        assertNull(ahead.take("over"))
        assertNull(ahead.take("missing"))
        assertSame(empty, ahead.take("empty"))
    }

    @Test(timeout = 5_000)
    fun `the eight megabyte budget is shared by completed reads and freed when bytes are consumed`() = runBlocking {
        val entered = Channel<String>(Channel.UNLIMITED)
        val gates = listOf("a", "b", "c", "d").associateWith { CompletableDeferred<ByteArray>() }
        val fourMb = ByteArray(4 * 1024 * 1024)
        val ahead = PlaybackReadAhead(this) { ref -> entered.send(ref.id); gates.getValue(ref.id).await() }
        ahead.prepare(listOf("a", "b", "c").map(::track))
        repeat(3) { entered.receive() }
        gates.getValue("a").complete(fourMb)
        yield()
        gates.getValue("b").complete(fourMb)
        yield()
        gates.getValue("c").complete(byteArrayOf(1))
        drain()
        assertNull(ahead.take("c"))
        assertSame(fourMb, ahead.take("a"))
        ahead.prepare(listOf("b", "d").map(::track))
        assertEquals("d", entered.receive())
        gates.getValue("d").complete(fourMb)
        drain()
        assertSame(fourMb, ahead.take("b"))
        assertSame(fourMb, ahead.take("d"))
    }

    @Test(timeout = 5_000)
    fun `a new missing candidate cancels the previous read and retains only the replacement`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val fresh = byteArrayOf(2)
        val ahead = PlaybackReadAhead(this) { ref ->
            if (ref.id == "old") {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            } else fresh
        }
        ahead.prepare(listOf(track("old")))
        entered.await()
        ahead.prepare(listOf(track("new")))
        drain()
        assertTrue(cancelled.isCompleted)
        assertNull(ahead.take("old"))
        assertSame(fresh, ahead.take("new"))
    }

    @Test(timeout = 5_000)
    fun `clear cancels pending reads drops stored bytes and allows subsequent preparation`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var cancelled = false
        val bytes = byteArrayOf(3)
        val ahead = PlaybackReadAhead(this) { ref ->
            if (ref.id == "pending") {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            } else bytes
        }
        ahead.prepare(listOf(track("stored"), track("pending")))
        entered.await()
        ahead.clear()
        drain()
        assertTrue(cancelled)
        assertNull(ahead.take("stored"))
        assertNull(ahead.take("pending"))
        ahead.prepare(listOf(track("fresh")))
        drain()
        assertSame(bytes, ahead.take("fresh"))
    }

    @Test(timeout = 5_000)
    fun `an empty or fully held selection cancels existing work before returning`() = runBlocking {
        for (keepStored in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>()
            val pending = CompletableDeferred<ByteArray>()
            var finished = false
            val stored = byteArrayOf(4)
            val late = byteArrayOf(5)
            val ahead = PlaybackReadAhead(this) { ref ->
                if (ref.id == "pending") {
                    entered.complete(Unit)
                    try { pending.await() } finally { finished = true }
                } else stored
            }
            ahead.prepare(listOf(track("stored"), track("pending")))
            entered.await()
            ahead.prepare(if (keepStored) listOf(track("stored")) else emptyList())
            yield()
            assertTrue(finished)
            pending.complete(late)
            drain()
            assertNull(ahead.take("pending"))
            assertSame(if (keepStored) stored else null, ahead.take("stored"))
        }
    }

    @Test(timeout = 5_000)
    fun `read failure cancels sibling reads and propagates to the supplied supervisor`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, error -> errors += error })
        try {
            val entered = CompletableDeferred<Unit>()
            var cancelled = false
            val failure = IllegalStateException("synthetic read failure")
            val ahead = PlaybackReadAhead(scope) { ref ->
                if (ref.id == "waiting") {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { cancelled = true }
                } else {
                    entered.await()
                    throw failure
                }
            }
            ahead.prepare(listOf(track("waiting"), track("failing")))
            scope.drain()
            assertTrue(cancelled)
            assertEquals(1, errors.size)
            assertSame(failure, errors.single())
            assertTrue(scope.coroutineContext.job.isActive)
            assertNull(ahead.take("waiting"))
            assertNull(ahead.take("failing"))
        } finally {
            scope.cancel()
        }
    }
}
