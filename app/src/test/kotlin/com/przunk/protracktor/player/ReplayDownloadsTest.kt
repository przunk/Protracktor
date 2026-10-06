// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayDownloadsTest {
    private class Fixture(scope: CoroutineScope, work: CoroutineDispatcher = Dispatchers.Unconfined) :
        ReplayDownloads.Native, ReplayDownloads.Ui, DownloadText {
        val downloads = ReplayDownloads(scope, work, this, this, this)
        val events = mutableListOf<String>()
        val busy = mutableSetOf<String>()
        val notices = mutableListOf<DownloadNotice>()
        private val messages = mutableMapOf<Long, DownloadNotice>()
        val activationThreads = mutableListOf<Thread>()
        var noticeThread: Thread? = null
        var sc68: Int? = 3
        var uade: Int? = 5
        var beforeSc68: suspend () -> Unit = {}
        var beforeUade: suspend () -> Unit = {}
        var failAt: String? = null
        val failure = IllegalStateException("synthetic replay failure")

        private fun record(event: String) {
            events += event
            if (failAt == event) throw failure
        }
        override suspend fun downloadSc68(onProgress: (Int, Int) -> Unit): Int? {
            record("download-sc68")
            onProgress(2, 3)
            beforeSc68()
            return sc68
        }
        override suspend fun downloadUade(onProgress: (Int, Int) -> Unit): Int? {
            record("download-uade")
            onProgress(4, 5)
            beforeUade()
            return uade
        }
        override fun adoptSc68() {
            activationThreads += Thread.currentThread()
            record("adopt-sc68")
        }
        override fun configureUade() {
            activationThreads += Thread.currentThread()
            record("configure-uade")
        }
        override fun begin(key: String, label: String): Boolean {
            events += "begin:$key:$label"
            return busy.add(key)
        }
        override fun end(key: String) {
            events += "end:$key"
            busy -= key
        }
        override fun progress(key: String, label: String) {
            assertTrue("progress without guard: $key", key in busy)
            events += "progress:$key:$label"
        }
        override fun say(message: Message) {
            noticeThread = Thread.currentThread()
            notices += messages.getValue(message.id)
            events += "notice"
        }
        override fun refreshCatalogues() { events += "refresh" }
        override fun label(label: DownloadText.Label) = label.name
        override fun message(notice: DownloadNotice) = Message(notice.toString()).also { messages[it.id] = notice }
    }

    private suspend fun CoroutineScope.drain() = coroutineContext.job.children.toList().joinAll()

    @Test
    fun `sc68 then UADE are activated before ending their progress and reporting one combined result`() = runBlocking {
        val f = Fixture(this)
        f.downloads.download()
        drain()
        assertEquals(listOf(
            "begin:${DownloadKeys.REPLAY_ROUTINES}:REPLAY_ROUTINES",
            "begin:${DownloadKeys.REPLAYS}:Atari ST replay routines",
            "download-sc68", "progress:${DownloadKeys.REPLAYS}:Atari ST replay routines 2/3", "adopt-sc68",
            "end:${DownloadKeys.REPLAYS}",
            "begin:${DownloadKeys.PLAYERS}:Amiga replay routines",
            "download-uade", "progress:${DownloadKeys.PLAYERS}:Amiga replay routines 4/5", "configure-uade",
            "end:${DownloadKeys.PLAYERS}", "notice", "end:${DownloadKeys.REPLAY_ROUTINES}", "refresh",
        ), f.events)
        assertEquals(listOf(DownloadNotice.ReplaysDownloaded(3, 5)), f.notices)
        assertTrue(f.busy.isEmpty())
    }

    @Test
    fun `zero and failed downloads do not activate engines and leave the other source usable`() = runBlocking {
        for (sc68 in listOf(null, 0, 3)) for (uade in listOf(null, 0, 5)) {
            val f = Fixture(this).apply { this.sc68 = sc68; this.uade = uade }
            f.downloads.download()
            drain()
            assertEquals(listOf("download-sc68", "download-uade"), f.events.filter { it.startsWith("download-") })
            assertEquals(sc68 != null && sc68 != 0, "adopt-sc68" in f.events)
            assertEquals(uade != null && uade != 0, "configure-uade" in f.events)
            assertEquals(listOf(DownloadNotice.ReplaysDownloaded(sc68?.takeUnless { it == 0 }, uade?.takeUnless { it == 0 })), f.notices)
            assertTrue(f.busy.isEmpty())
        }
    }

    @Test
    fun `group and per-source guards skip existing work without changing the other source`() = runBlocking {
        val group = Fixture(this).apply { busy += DownloadKeys.REPLAY_ROUTINES }
        group.downloads.download()
        drain()
        assertEquals(1, group.events.size)
        assertTrue(group.notices.isEmpty())
        for (key in listOf(DownloadKeys.REPLAYS, DownloadKeys.PLAYERS)) {
            val f = Fixture(this).apply { busy += key }
            f.downloads.download()
            drain()
            assertEquals(listOf(if (key == DownloadKeys.REPLAYS) "download-uade" else "download-sc68"),
                f.events.filter { it.startsWith("download-") })
            assertEquals(setOf(key), f.busy)
            assertEquals(listOf(if (key == DownloadKeys.REPLAYS) DownloadNotice.ReplaysDownloaded(null, 5)
                else DownloadNotice.ReplaysDownloaded(3, null)), f.notices)
        }
    }

    @Test(timeout = 5_000)
    fun `cancellation clears the active row and group without a success notice and allows another attempt`() = runBlocking {
        val f = Fixture(this)
        val started = CompletableDeferred<Unit>()
        f.beforeSc68 = { started.complete(Unit); awaitCancellation() }
        f.downloads.download()
        started.await()
        coroutineContext.job.children.single().cancel()
        drain()
        assertFalse("download-uade" in f.events)
        assertFalse("adopt-sc68" in f.events)
        assertTrue(f.busy.isEmpty())
        assertTrue(f.notices.isEmpty())
        assertEquals(listOf("end:${DownloadKeys.REPLAYS}", "end:${DownloadKeys.REPLAY_ROUTINES}", "refresh"), f.events.takeLast(3))
        f.beforeSc68 = {}
        f.downloads.download()
        drain()
        assertEquals(listOf(DownloadNotice.ReplaysDownloaded(3, 5)), f.notices)
    }

    @Test
    fun `activation uses the supplied background worker and presentation returns to the caller`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { work ->
            val worker = withContext(work) { Thread.currentThread() }
            val f = Fixture(this, work)
            f.downloads.download()
            drain()
            assertEquals(2, f.activationThreads.size)
            f.activationThreads.forEach { assertSame(worker, it) }
            assertSame(Thread.currentThread(), f.noticeThread)
        }
    }

    @Test
    fun `download and activation exceptions propagate through the supplied supervisor after cleanup`() = runBlocking {
        for (point in listOf("download-sc68", "adopt-sc68", "download-uade", "configure-uade")) {
            val errors = mutableListOf<Throwable>()
            val scope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, error -> errors += error })
            try {
                val f = Fixture(scope).apply { failAt = point }
                f.downloads.download()
                scope.drain()
                assertEquals(1, errors.size)
                assertSame(f.failure, errors.single())
                assertTrue(scope.coroutineContext.job.isActive)
                assertTrue(f.busy.isEmpty())
                assertTrue(f.notices.isEmpty())
                assertEquals(listOf("end:${DownloadKeys.REPLAY_ROUTINES}", "refresh"), f.events.takeLast(2))
                if (point.endsWith("sc68")) assertFalse("download-uade" in f.events)
            } finally {
                scope.cancel()
            }
        }
    }
}
