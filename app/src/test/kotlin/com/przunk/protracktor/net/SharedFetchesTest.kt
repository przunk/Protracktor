// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** `docs/BACKLOG.md` A55: a tap on a track being fetched ahead waits for that fetch. */
class SharedFetchesTest {

    @Test
    fun `two asking for one address share one download`() = runBlocking {
        val shared = SharedFetches<String>(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        var downloads = 0
        val gate = CompletableDeferred<Unit>()
        val first = async { shared.get("u") { downloads++; gate.await(); "bytes" } }
        withTimeout(1_000) { while (!shared.inFlight("u")) yield() }
        val second = async { shared.get("u") { downloads++; "other" } }
        yield()
        gate.complete(Unit)
        assertEquals("bytes", first.await())
        assertEquals("bytes", second.await())
        assertEquals(1, downloads)
    }

    @Test
    fun `a download goes on when whoever asked for it leaves`() = runBlocking {
        val shared = SharedFetches<String>(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val gate = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<String>()
        val asker = launch { shared.get("u") { gate.await(); "bytes".also { finished.complete(it) } } }
        withTimeout(1_000) { while (!shared.inFlight("u")) yield() }
        asker.cancel()
        gate.complete(Unit)
        assertEquals("bytes", withTimeout(1_000) { finished.await() })
    }

    @Test
    fun `a finished download is not handed out again`() = runBlocking {
        val shared = SharedFetches<Int>(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        var downloads = 0
        assertEquals(1, shared.get("u") { ++downloads })
        assertFalse(shared.inFlight("u"))
        assertEquals(2, shared.get("u") { ++downloads })
    }
}
