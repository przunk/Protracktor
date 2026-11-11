// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C140: a native player is never freed under a seek running on another thread. */
class NativeLifetimeTest {
    @Test fun `a close during a seek waits for the seek, then frees once`() {
        val freed = AtomicInteger()
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val life = NativeLifetime { freed.incrementAndGet(); events += "freed" }
        val seeking = CountDownLatch(1)
        val letGo = CountDownLatch(1)
        val seek = thread {
            life.during {
                seeking.countDown()
                letGo.await(5, TimeUnit.SECONDS)
                events += "seek ended"
            }
        }
        assertTrue(seeking.await(5, TimeUnit.SECONDS))
        life.close()                       // the tune is left while it seeks
        assertEquals("not freed under the seek", 0, freed.get())
        assertTrue(life.ending)
        letGo.countDown()
        seek.join(5_000)
        assertEquals(1, freed.get())
        assertEquals(listOf("seek ended", "freed"), events)
        life.close()
        assertEquals("freed once", 1, freed.get())
    }

    @Test fun `with nothing running a close frees at once, and nothing starts after it`() {
        val freed = AtomicInteger()
        val life = NativeLifetime { freed.incrementAndGet() }
        assertFalse(life.ending)
        life.close()
        assertEquals(1, freed.get())
        var ran = false
        life.during { ran = true }
        assertFalse("no seek on a freed player", ran)
    }

    @Test fun `two seeks at once, freed after the last`() {
        val freed = AtomicInteger()
        val life = NativeLifetime { freed.incrementAndGet() }
        val started = CountDownLatch(2)
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val a = thread { life.during { started.countDown(); first.await(5, TimeUnit.SECONDS) } }
        val b = thread { life.during { started.countDown(); second.await(5, TimeUnit.SECONDS) } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        life.close()
        first.countDown(); a.join(5_000)
        assertEquals(0, freed.get())
        second.countDown(); b.join(5_000)
        assertEquals(1, freed.get())
    }
}
