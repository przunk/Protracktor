// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `docs/BACKLOG.md` A55: which tracks a folder fetches ahead, and how. */
class FolderPrefetchTest {

    private fun candidates(vararg sizes: Long) =
        sizes.mapIndexed { i, size -> FolderPrefetch.Candidate("t$i", size) }

    @Test
    fun `the plan keeps the folder's order and leaves out what is already on the phone`() {
        assertEquals(
            listOf("t0", "t2", "t3"),
            FolderPrefetch.plan(candidates(10, 10, 10, 10), cached = setOf("t1")),
        )
    }

    @Test
    fun `a folder stops at its budget rather than skipping ahead to smaller files`() {
        // 40 + 40 fit in 100; the third, 30, would not, and the 5 after it is not fetched either.
        assertEquals(
            listOf("t0", "t1"),
            FolderPrefetch.plan(candidates(40, 40, 30, 5), cached = emptySet(), budgetBytes = 100),
        )
    }

    @Test
    fun `cached tracks do not count against the budget`() {
        assertEquals(
            listOf("t1"),
            FolderPrefetch.plan(candidates(90, 90), cached = setOf("t0"), budgetBytes = 100),
        )
    }

    @Test
    fun `the default budget is 100 MB`() {
        assertEquals(100L * 1024 * 1024, FolderPrefetch.FOLDER_BUDGET_BYTES)
    }

    @Test
    fun `each setting decides the network the same way`() {
        assertTrue(CacheAhead.WIFI_ONLY.allows(metered = false))
        assertFalse(CacheAhead.WIFI_ONLY.allows(metered = true))
        assertTrue(CacheAhead.ALWAYS.allows(metered = true))
        assertTrue(CacheAhead.ALWAYS.allows(metered = false))
        assertFalse(CacheAhead.OFF.allows(metered = false))
        assertFalse(CacheAhead.OFF.allows(metered = true))
    }

    @Test
    fun `nothing stored means Wi-Fi only, and every value reads back as itself`() {
        assertEquals(CacheAhead.WIFI_ONLY, CacheAhead.fromStored(null))
        assertEquals(CacheAhead.WIFI_ONLY, CacheAhead.fromStored("nonsense"))
        CacheAhead.entries.forEach { assertEquals(it, CacheAhead.fromStored(it.stored)) }
    }

    @Test
    fun `three at a time, never four, and every track once`() = runBlocking(Dispatchers.Default) {
        val running = java.util.concurrent.atomic.AtomicInteger()
        var most = 0
        val fetched = Collections.synchronizedList(mutableListOf<String>())
        FolderPrefetch.run(
            urls = (0 until 10).map { "t$it" },
            fetch = { url ->
                val now = running.incrementAndGet()
                synchronized(this@FolderPrefetchTest) { most = maxOf(most, now) }
                repeat(20) { yield() }
                Thread.sleep(2)
                running.decrementAndGet()
                fetched += url
                true
            },
        )
        assertEquals(3, most)
        assertEquals((0 until 10).map { "t$it" }.toSet(), fetched.toSet())
        assertEquals(10, fetched.size)
    }

    @Test
    fun `the first three start in the folder's order`() = runBlocking {
        val started = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            FolderPrefetch.run(
                urls = listOf("a", "b", "c", "d"),
                fetch = { gate.await(); true },
                onStart = { started += it },
            )
        }
        withTimeout(1_000) { while (started.size < 3) yield() }
        assertEquals(listOf("a", "b", "c"), started)
        gate.complete(Unit)
        job.join()
        assertEquals(listOf("a", "b", "c", "d"), started)
    }

    @Test
    fun `a failed fetch is not tried again`() = runBlocking {
        val attempts = mutableListOf<String>()
        val done = mutableListOf<Pair<String, Boolean>>()
        FolderPrefetch.run(
            urls = listOf("bad", "good"),
            fetch = { url -> attempts += url; if (url == "bad") error("404") else true },
            onDone = { url, ok -> done += url to ok },
            parallel = 1,
        )
        assertEquals(listOf("bad", "good"), attempts)
        assertEquals(listOf("bad" to false, "good" to true), done)
    }

    @Test
    fun `leaving the network stops what has not started`() = runBlocking {
        var allowed = true
        val attempts = mutableListOf<String>()
        FolderPrefetch.run(
            urls = listOf("a", "b", "c"),
            fetch = { url -> attempts += url; allowed = false; true },
            mayContinue = { allowed },
            parallel = 1,
        )
        assertEquals(listOf("a"), attempts)
    }

    @Test
    fun `leaving the folder starts nothing new`() = runBlocking {
        val started = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            FolderPrefetch.run(
                urls = (0 until 9).map { "t$it" },
                fetch = { gate.await(); true },
                onStart = { started += it },
            )
        }
        withTimeout(1_000) { while (started.size < 3) yield() }
        job.cancel()
        job.join()
        gate.complete(Unit)
        assertEquals(listOf("t0", "t1", "t2"), started)
    }

    @Test
    fun `the manifest lets the app ask whether the network is metered`() {
        // Without this permission the question throws, and the first build read that as "metered":
        // Wi-Fi only then meant never, on every network, and nothing said so.
        var here: java.io.File? = java.io.File(".").absoluteFile
        while (here != null && !java.io.File(here, "settings.gradle.kts").isFile) here = here.parentFile
        val manifest = java.io.File(here, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }
}

