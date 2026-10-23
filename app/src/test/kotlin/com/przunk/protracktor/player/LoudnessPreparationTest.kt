// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoudnessPreparationTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun `successful preparation leaves the decoder open for playback adoption`() = runBlocking {
        var closes=0
        val opened=AutoCloseable { closes++ }
        assertEquals("ready",opened.prepareOrClose { "ready" })
        assertEquals(0,closes)
        opened.close()
        assertEquals(1,closes)
    }
    @Test fun `failed preparation closes its private decoder without masking the cause`() = runBlocking {
        var closes=0
        val failure=java.io.IOException("Controlled preparation failure")
        try {
            AutoCloseable { closes++ }.prepareOrClose { throw failure }
            fail("Preparation failure was swallowed")
        } catch (actual: java.io.IOException) {
            assertSame(failure,actual)
        }
        assertEquals(1,closes)
    }
    @Test fun `cancellation at the worker handoff closes the still private decoder once`() = runBlocking {
        var closes=0
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val job=async(Dispatchers.Default) {
            AutoCloseable { closes++ }.prepareOrClose {
                withContext(Dispatchers.IO) {
                    entered.countDown(); check(release.await(5,TimeUnit.SECONDS))
                    "ready"
                }
            }
        }
        try { assertTrue(entered.await(5,TimeUnit.SECONDS)); job.cancel() }
        finally { release.countDown(); job.join() }
        assertEquals(1,closes)
    }
    @Test fun `cancellation also closes a native allocation whose worker result was never delivered`() = runBlocking {
        var closes=0
        var opened: AutoCloseable?=null
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val job=async(Dispatchers.Default) {
            AutoCloseable { opened?.close() }.prepareOrClose {
                withContext(Dispatchers.IO) {
                    opened=AutoCloseable { closes++ }
                    entered.countDown(); check(release.await(5,TimeUnit.SECONDS))
                    "allocated"
                }
            }
        }
        try { assertTrue(entered.await(5,TimeUnit.SECONDS)); job.cancel() }
        finally { release.countDown(); job.join() }
        assertEquals(1,closes)
    }
    @Test fun `a content matched cache avoids decoding and a different subsong or mix does not`() = runBlocking {
        var calls=0
        val preparation=LoudnessPreparation(LoudnessProfiles(temporary.newFolder()),{"decoder"}) { _,_,_,s,_ ->
            calls++; doubleArrayOf(20.0,-23.0,.4,0.0,s.toDouble())
        }
        val b=byteArrayOf(1,2,3)
        val first=preparation.prepare(b,"a.mod",emptyList(),0,100)
        assertNotNull(first.profile)
        assertEquals(first,preparation.prepare(b,"a.mod",emptyList(),0,100)); assertEquals(1,calls)
        preparation.prepare(b,"a.mod",emptyList(),1,100)
        preparation.prepare(b,"a.mod",emptyList(),0,0)
        assertEquals(3,calls)
    }
    @Test fun `a decoder's initial audible subsong warms its actual identity not zero`() = runBlocking {
        val cache=LoudnessProfiles(temporary.newFolder())
        val preparation=LoudnessPreparation(cache,{"decoder"}) { _,_,_,_,_ -> doubleArrayOf(10.0,-21.0,.3,0.0,7.0) }
        val first=preparation.prepare(byteArrayOf(1),"a.nsf",emptyList(),-1,100)
        assertNull(cache.read(LoudnessProfiles.key(first.base,0,100)))
        assertEquals(first.profile,cache.read(LoudnessProfiles.key(first.base,7,100)))
    }
    @Test fun `optional analysis failure falls back without rejecting the audible track`() = runBlocking {
        val preparation=LoudnessPreparation(LoudnessProfiles(temporary.newFolder()),{"decoder"}) { _,_,_,_,_ ->
            throw java.io.IOException("Controlled optional analysis failure")
        }
        assertNull(preparation.prepare(byteArrayOf(1),"a.mod",emptyList(),0,100).profile)
    }
    @Test fun `a cancelled native analysis cannot publish its result after a newer command`() = runBlocking {
        val cache=LoudnessProfiles(temporary.newFolder())
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val preparation=LoudnessPreparation(cache,{"decoder"}) { _,_,_,_,_ ->
            entered.countDown(); check(release.await(5,TimeUnit.SECONDS))
            doubleArrayOf(20.0,-23.0,.4,0.0,0.0)
        }
        val bytes=byteArrayOf(1)
        val job=async(Dispatchers.IO) { preparation.prepare(bytes,"a.mod",emptyList(),0,100) }
        try { assertTrue(entered.await(5,TimeUnit.SECONDS)); job.cancel() }
        finally { release.countDown(); job.join() }
        val base=LoudnessProfiles.contentKey(bytes,"a.mod",emptyList(),"decoder")
        assertNull(cache.read(LoudnessProfiles.key(base,0,100)))
    }
}
