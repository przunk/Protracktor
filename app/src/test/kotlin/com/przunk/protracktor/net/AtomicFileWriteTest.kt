// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileWriteTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `successful replacement publishes complete bytes and removes its staging file`() {
        val directory = temporary.newFolder()
        val target = File(directory, "archive.zip").apply { writeBytes(byteArrayOf(1)) }
        val anotherWriter = File(directory, "other.part").apply { writeText("in flight") }
        val fresh = byteArrayOf(2, 3, 4)
        assertTrue(AtomicFileWrite.write(target, fresh))
        assertArrayEquals(fresh, target.readBytes())
        assertEquals(setOf("archive.zip", "other.part"), directory.list().orEmpty().toSet())
        assertEquals("in flight", anotherWriter.readText())
    }

    @Test fun `failure does not remove the destination or another writer's staging file`() {
        val directory = temporary.newFolder()
        val target = File(directory, "occupied.zip").apply { mkdir() }
        val previous = File(target, "keep").apply { writeText("previous") }
        val anotherWriter = File(directory, "other.part").apply { writeText("in flight") }
        assertFalse(AtomicFileWrite.write(target, byteArrayOf(2)))
        assertEquals("previous", previous.readText())
        assertEquals("in flight", anotherWriter.readText())
        assertEquals(setOf("occupied.zip", "other.part"), directory.list().orEmpty().toSet())
    }

    @Test(timeout = 10_000)
    fun `concurrent writers and a reader see whole publications without shared staging names`() {
        val directory = temporary.newFolder()
        val target = File(directory, "archive.zip")
        val payloads = listOf(ByteArray(127) { 1 }, ByteArray(257) { 2 }, ByteArray(513) { 3 })
        target.writeBytes(payloads.first())
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val jobs = (0..3).map { worker -> thread(isDaemon = true) {
            try {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(50) {
                    if (worker < 3) check(AtomicFileWrite.write(target, payloads[worker]))
                    else check(payloads.any { it.contentEquals(target.readBytes()) })
                }
            } catch (error: Throwable) { failure.compareAndSet(null, error) }
        } }
        start.countDown()
        jobs.forEach { it.join(5_000) }
        failure.get()?.let { throw AssertionError("file worker failed", it) }
        assertTrue(jobs.none { it.isAlive })
        assertEquals(listOf("archive.zip"), directory.list().orEmpty().toList())
        assertTrue(payloads.any { it.contentEquals(target.readBytes()) })
    }
}
