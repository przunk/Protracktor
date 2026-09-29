// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/** `docs/review-2026-09-28.md` F1: a read already on its way must not undo *Clear history*. */
class HistoryReadsTest {

    /** A History of [total] tunes whose page reads wait for [gate] -- a read held open. */
    private class Fake(var total: Int) {
        var gate: CompletableDeferred<Unit>? = null
        val shown = mutableListOf<HistoryReads.Shown<String>>()
        suspend fun page(offset: Int, limit: Int): List<String> {
            // Got its rows before the clear, as a real read that had already queried would have:
            // the rows are in hand, only the return is late -- and not cancellable, like a query.
            val rows = (offset until minOf(offset + limit, total)).map { "t$it" }
            gate?.let { withContext(NonCancellable) { it.await() } }
            return rows
        }
    }

    private fun reads(fake: Fake, scope: kotlinx.coroutines.CoroutineScope) = HistoryReads(
        scope = scope,
        count = { fake.total },
        page = fake::page,
        clear = { fake.total = 0 },
        show = { fake.shown += it },
    )

    @Test
    fun `a page read that ends after a clear does not bring the cleared tunes back`() = runBlocking {
        val fake = Fake(250)
        val history = reads(fake, this)
        history.open().join()
        fake.gate = CompletableDeferred()

        history.turn(1, 250)          // Older, and before the page has come...
        yield()
        history.clearAll().join()     // ...Clear history.
        fake.gate!!.complete(Unit)
        yield(); yield()

        assertEquals(HistoryReads.Shown(0, 0, emptyList<String>()), fake.shown.last())
    }

    @Test
    fun `opening History and clearing it before it arrives leaves it empty, count and all`() = runBlocking {
        val fake = Fake(250)
        val history = reads(fake, this)
        fake.gate = CompletableDeferred()

        history.open()
        yield()
        history.clearAll().join()
        fake.gate!!.complete(Unit)
        yield(); yield()

        assertEquals(HistoryReads.Shown(0, 0, emptyList<String>()), fake.shown.last())
    }

    @Test
    fun `without a clear, reads show what they read`() = runBlocking {
        val fake = Fake(250)
        val history = reads(fake, this)
        history.open().join()
        history.turn(2, 250).join()
        assertEquals(HistoryReads.Shown(250, 0, (0 until 100).map { "t$it" }), fake.shown[0])
        assertEquals(HistoryReads.Shown(250, 2, (200 until 250).map { "t$it" }), fake.shown[1])
    }
}
