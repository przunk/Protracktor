// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteFileCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `cache reads complete bytes and keeps partial files outside accounting and eviction`() {
        val directory = temporary.newFolder()
        val cache = RemoteFileCache(directory)
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(cache.store("track", bytes))
        File(directory, "unfinished.part").writeBytes(ByteArray(20))
        assertArrayEquals(bytes, cache.read("track"))
        assertTrue(cache.contains("track"))
        assertEquals(3L, cache.bytes())
        cache.enforceBudget(inUse = setOf("track"), ceilingBytes = 0)
        assertTrue(cache.contains("track"))
        assertTrue(File(directory, "unfinished.part").exists())
        cache.clear()
        assertEquals(0L, cache.bytes())
        assertFalse(cache.contains("track"))
    }

    @Test fun `failed publication preserves the destination and removes its temporary file`() {
        val directory = temporary.newFolder()
        val occupied = File(directory, "occupied").apply { mkdir() }
        val previous = File(occupied, "keep").apply { writeText("previous") }
        val cache = RemoteFileCache(directory)
        assertFalse(cache.store("occupied", byteArrayOf(4)))
        assertEquals("previous", previous.readText())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(CacheBudget.PARTIAL_SUFFIX) })
    }

    @Test(timeout = 10_000)
    fun `an eviction snapshot cannot delete bytes published by a concurrent store`() {
        val directory = temporary.newFolder()
        File(directory, "track").apply { writeBytes(byteArrayOf(1)); setLastModified(1) }
        val captured = CountDownLatch(1)
        val release = CountDownLatch(1)
        val intercepted = AtomicBoolean()
        val snapshotDirectory = object : File(directory.path) {
            override fun listFiles(): Array<File>? = super.listFiles()?.map { original ->
                object : File(original.path) {
                    override fun lastModified(): Long {
                        val stamp = super.lastModified()
                        if (intercepted.compareAndSet(false, true)) {
                            captured.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                        return stamp
                    }
                }
            }?.toTypedArray()
        }
        val cache = RemoteFileCache(snapshotDirectory)
        val failure = AtomicReference<Throwable?>()
        fun launch(action: () -> Unit) = thread(isDaemon = true) {
            try { action() } catch (error: Throwable) { failure.compareAndSet(null, error) }
        }
        val eviction = launch { cache.enforceBudget(ceilingBytes = 0) }
        var writer: Thread? = null
        try {
            assertTrue(captured.await(5, TimeUnit.SECONDS))
            writer = launch { check(cache.store("track", byteArrayOf(2, 3))) }
            // Either the old implementation publishes early, or the transaction blocks the writer.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (writer.isAlive && writer.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
        } finally {
            release.countDown()
            eviction.join(5_000)
            writer?.join(5_000)
        }
        failure.get()?.let { throw AssertionError("cache worker failed", it) }
        assertFalse(eviction.isAlive)
        assertFalse(writer.isAlive)
        assertArrayEquals(byteArrayOf(2, 3), cache.read("track"))
    }
}
