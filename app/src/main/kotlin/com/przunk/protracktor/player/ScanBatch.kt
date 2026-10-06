// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.IndexedFile
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Executes one scan plan; the caller owns its job, platform I/O and presentation. */
internal class ScanBatch(
    private val parallel: CoroutineDispatcher,
    private val serial: CoroutineDispatcher,
    private val elapsedRealtime: () -> Long,
) {
    suspend fun run(
        plan: IncrementalScan.Plan,
        total: Int,
        probe: suspend (MediaScanner.Candidate) -> IndexedFile?,
        saveProgress: suspend (List<IndexedFile>) -> Unit,
        saveCompleted: suspend (List<IndexedFile>) -> Unit,
        onProgress: (done: Int) -> Unit,
        onSnapshot: (done: Int, indexed: List<IndexedFile>) -> Unit,
        onStopped: (done: Int) -> Unit,
    ): List<IndexedFile> {
        // Found so far: what was kept, and what the workers add. Shared between them, and read
        // whole for a save, so every touch holds its lock.
        val indexed = Collections.synchronizedList(plan.kept.toMutableList())
        val done = AtomicInteger(plan.kept.size)
        onProgress(done.get())
        val (together, alone) = IncrementalScan.lanes(plan.toOpen)
        val nextTogether = ConcurrentLinkedQueue(together)
        var savedAt = done.get()
        // What is already in the index: everything kept from the last scan. A save adds only
        // what came after -- rewriting the whole folder each time cost the size of the index.
        var savedRows = plan.kept.size
        suspend fun savedSoFar() {
            val fresh = synchronized(indexed) { indexed.subList(savedRows, indexed.size).toList() }
            savedRows += fresh.size
            withContext(NonCancellable) { saveProgress(fresh) }
        }
        suspend fun saved() {
            val snapshot = synchronized(indexed) { indexed.toList() }
            withContext(NonCancellable) { saveCompleted(snapshot) }
        }
        var shownAt = 0L
        fun notify(now: Int) {
            // At most twice a second: a notification per file is one the system drops.
            val at = elapsedRealtime()
            if (at - shownAt < 500 && now != total) return
            shownAt = at
            val sofar = synchronized(indexed) { indexed.toList() }
            onSnapshot(now, sofar)
        }
        notify(done.get())
        suspend fun opened(candidate: MediaScanner.Candidate, on: CoroutineDispatcher) {
            withContext(on) { probe(candidate) }?.let(indexed::add)
            val now = done.incrementAndGet()
            // Every file would be a state update per file and a recomposition per file; every
            // twenty-fifth is still movement on screen and costs almost nothing.
            if (now % 25 == 0 || now == total) {
                onProgress(now)
                notify(now)
            }
        }
        try {
            coroutineScope {
                // **Side by side where the name allows it** (A67: 11,582 ASMA files took about a
                // quarter of an hour, 80 ms each, one after another).
                val workers = List(IncrementalScan.PARALLEL) {
                    launch {
                        while (true) {
                            ensureActive()
                            opened(nextTogether.poll() ?: break, parallel)
                        }
                    }
                } + launch {
                    for (candidate in alone) {
                        ensureActive()
                        opened(candidate, serial)
                    }
                }
                // **Saved as it goes**, so a scan stopped -- by the X, or by the system killing
                // the app in the background -- is only paused: the next one keeps what is saved.
                launch {
                    while (workers.any { it.isActive }) {
                        delay(1_000)
                        if (done.get() - savedAt >= IncrementalScan.CHECKPOINT) {
                            savedAt = done.get()
                            savedSoFar()
                        }
                    }
                }
            }
        } catch (stopped: CancellationException) {
            withContext(NonCancellable) {
                savedSoFar()
                onStopped(done.get())
            }
            throw stopped
        }

        saved()
        return indexed.toList()
    }
}
