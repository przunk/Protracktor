// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.IndexedFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanBatchTest {
    private fun candidate(id: String, extension: String = "mod") =
        MediaScanner.Candidate(id, "Music", "$id.$extension", 10)

    private fun row(id: String) = IndexedFile(
        id, "tree", "Music", "$id.mod", 10, "openmpt", "MOD", id, "", 1_000, 1,
    )

    private class Observed {
        val progress = mutableListOf<Int>()
        val snapshots = mutableListOf<Pair<Int, List<IndexedFile>>>()
        val appended = mutableListOf<List<IndexedFile>>()
        val replaced = mutableListOf<List<IndexedFile>>()
        val stopped = mutableListOf<Int>()
    }

    private suspend fun run(
        plan: IncrementalScan.Plan,
        observed: Observed,
        batch: ScanBatch = ScanBatch(Dispatchers.Unconfined, Dispatchers.Unconfined) { 1_000L },
        saveProgress: suspend (List<IndexedFile>) -> Unit = { observed.appended += it },
        probe: suspend (MediaScanner.Candidate) -> IndexedFile? = { row(it.uri) },
    ): List<IndexedFile> = batch.run(
        plan = plan,
        total = plan.kept.size + plan.toOpen.size,
        probe = probe,
        saveProgress = saveProgress,
        saveCompleted = { observed.replaced += it },
        onProgress = { observed.progress += it },
        onSnapshot = { done, rows -> observed.snapshots += done to rows },
        onStopped = { observed.stopped += it },
    )

    @Test(timeout = 5_000)
    fun `completion keeps unchanged rows opens only candidates and counts refusals`() = runBlocking {
        val observed = Observed()
        val opened = mutableListOf<String>()
        val plan = IncrementalScan.Plan(listOf(row("kept")), listOf(candidate("new"), candidate("bad")))
        val result = run(plan, observed) {
            opened += it.uri
            if (it.uri == "bad") null else row(it.uri)
        }
        assertEquals(listOf("new", "bad"), opened)
        assertEquals(listOf("kept", "new"), result.map { it.uri })
        assertEquals(listOf(1, 3), observed.progress)
        assertEquals(listOf(1, 3), observed.snapshots.map { it.first })
        assertEquals(listOf("kept"), observed.snapshots.first().second.map { it.uri })
        assertEquals(listOf(result), observed.replaced)
        assertTrue(observed.appended.isEmpty())
        assertTrue(observed.stopped.isEmpty())
    }

    @Test(timeout = 5_000)
    fun `cancellation saves only new rows without replacing the folder and rethrows`() = runBlocking {
        val observed = Observed()
        val blocked = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        var savingWasActive = false
        val plan = IncrementalScan.Plan(listOf(row("kept")), listOf(candidate("new"), candidate("blocked")))
        val job = launch {
            run(plan, observed, saveProgress = {
                delay(1)
                savingWasActive = currentCoroutineContext().isActive
                observed.appended += it
            }) {
                if (it.uri == "blocked") {
                    blocked.complete(Unit)
                    never.await()
                }
                row(it.uri)
            }
        }
        blocked.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(savingWasActive)
        assertEquals(listOf(listOf("new")), observed.appended.map { rows -> rows.map { it.uri } })
        assertTrue(observed.replaced.isEmpty())
        assertEquals(listOf(2), observed.stopped)
    }

    @Test(timeout = 5_000)
    fun `a checkpoint does not rewrite kept rows or repeat them when cancelled`() = runBlocking {
        val observed = Observed()
        val checkpoint = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val fresh = (0 until IncrementalScan.CHECKPOINT).map { candidate("new$it") }
        val plan = IncrementalScan.Plan(listOf(row("kept")), fresh + candidate("blocked"))
        val job = launch {
            run(plan, observed, saveProgress = {
                observed.appended += it
                checkpoint.complete(Unit)
            }) {
                if (it.uri == "blocked") never.await()
                row(it.uri)
            }
        }
        checkpoint.await()
        assertEquals(listOf(fresh.map { it.uri }), observed.appended.map { rows -> rows.map { it.uri } })
        job.cancelAndJoin()
        assertEquals(listOf(IncrementalScan.CHECKPOINT, 0), observed.appended.map { it.size })
        assertTrue(observed.replaced.isEmpty())
        assertEquals(listOf(IncrementalScan.CHECKPOINT + 1), observed.stopped)
    }

    @Test(timeout = 5_000)
    fun `no more than four safe probes and one serial probe run at once`() = runBlocking {
        val observed = Observed()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var activeParallel = 0
        var activeSerial = 0
        var maxParallel = 0
        var maxSerial = 0
        val candidates = (0 until 7).map { candidate("p$it") } + (0 until 3).map { candidate("s$it", "ahx") }
        val job = launch {
            run(IncrementalScan.Plan(emptyList(), candidates), observed) {
                val parallel = it.fileName.endsWith(".mod")
                if (parallel) {
                    activeParallel++
                    maxParallel = maxOf(maxParallel, activeParallel)
                } else {
                    activeSerial++
                    maxSerial = maxOf(maxSerial, activeSerial)
                }
                if (activeParallel == 4 && activeSerial == 1) started.complete(Unit)
                release.await()
                if (parallel) activeParallel-- else activeSerial--
                row(it.uri)
            }
        }
        started.await()
        assertEquals(4, activeParallel)
        assertEquals(1, activeSerial)
        release.complete(Unit)
        job.join()
        assertEquals(4, maxParallel)
        assertEquals(1, maxSerial)
        assertEquals(10, observed.replaced.single().size)
    }

    @Test(timeout = 5_000)
    fun `the safe and unsafe probes use their supplied dispatchers`() = runBlocking {
        val parallelThread = AtomicReference<Thread>()
        val serialThread = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor {
            Thread(it, "test-scan-parallel").also(parallelThread::set)
        }.asCoroutineDispatcher().use { parallel ->
            Executors.newSingleThreadExecutor {
                Thread(it, "test-scan-serial").also(serialThread::set)
            }.asCoroutineDispatcher().use { serial ->
                val threads = ConcurrentHashMap<String, Thread>()
                val observed = Observed()
                run(
                    IncrementalScan.Plan(emptyList(), listOf(candidate("safe"), candidate("unsafe", "ahx"))),
                    observed, ScanBatch(parallel, serial) { 1_000L },
                ) {
                    threads[it.uri] = Thread.currentThread()
                    row(it.uri)
                }
                assertSame(parallelThread.get(), threads["safe"])
                assertSame(serialThread.get(), threads["unsafe"])
            }
        }
    }

    @Test(timeout = 5_000)
    fun `progress is batched and snapshots throttled but completion is always shown`() = runBlocking {
        val observed = Observed()
        var clock = 1_000L
        val candidates = (1..26).map { candidate("new$it") }
        run(
            IncrementalScan.Plan(emptyList(), candidates), observed,
            ScanBatch(Dispatchers.Unconfined, Dispatchers.Unconfined) { clock },
        ) {
            clock = 1_400L
            row(it.uri)
        }
        assertEquals(listOf(0, 25, 26), observed.progress)
        assertEquals(listOf(0, 26), observed.snapshots.map { it.first })
        assertEquals(26, observed.snapshots.last().second.size)
    }

    @Test(timeout = 5_000)
    fun `an empty completed scan replaces its snapshot with an empty list`() = runBlocking {
        val observed = Observed()
        val result = run(IncrementalScan.Plan(emptyList(), emptyList()), observed) {
            error("An empty plan must not probe")
        }
        assertTrue(result.isEmpty())
        assertEquals(listOf(emptyList<IndexedFile>()), observed.replaced)
        assertEquals(listOf(0), observed.progress)
        assertEquals(listOf(0), observed.snapshots.map { it.first })
        assertTrue(observed.stopped.isEmpty())
    }
}
