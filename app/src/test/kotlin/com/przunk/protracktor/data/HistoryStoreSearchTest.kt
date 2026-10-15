// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import com.przunk.protracktor.player.HistoryPages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HistoryStoreSearchTest {
    @Test fun `production store finds old Unicode metadata without changing history`() = runBlocking {
        val store = HistoryStore(RuntimeEnvironment.getApplication())
        store.clear()
        store.record("old", "Żółw", "Source", "old.mod", "Hülsbeck", 123)
        store.qualify("old")
        repeat(130) { store.record("new-$it", "Other", "", "$it.mod", "", 0) }
        val before = store.page(0, 200)
        assertEquals("old", store.read("ŻÓŁW", 0).rows.single().trackId)
        assertEquals("old", store.read("HÜLSBECK", 0).rows.single().trackId)
        assertEquals("old", store.read("OLD.MOD", 0).rows.single().trackId)
        assertEquals(before, store.page(0, 200))
        assertEquals(setOf("old"), store.listenedAmong(setOf("old")))
        assertEquals(0, store.read("%", 0).total)
        assertEquals(131, store.read(" ", 0).total)
    }

    @Test fun `large History uses real ordered cursor counts and bounded pages`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = HistoryStore(context)
        store.clear()
        ProtracktorDatabase.of(context).writableDatabase.transaction {
            repeat(10_000) { i ->
                execSQL(SchemaSql.PLAY_HISTORY_RECORD, arrayOf<Any>(
                    "id-$i", if (i % 2 == 0) "Needle $i" else "Other $i", "", "$i.mod", "", 0L,
                    100_000L - i, "id-$i",
                ))
            }
        }
        val started = System.nanoTime()
        val found = store.read("NEEDLE", 1)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        println("B49 host/native SQLite: 10000 rows, 5000 matches, read ${elapsed}ms; not phone latency")
        assertEquals(5000, found.total)
        assertEquals(HistoryPages.SIZE, found.rows.size)
        assertEquals("id-200", found.rows.first().trackId)
        assertEquals("id-398", found.rows.last().trackId)
        assertEquals(49, store.read("needle", Int.MAX_VALUE).page)
        store.clear()
        assertEquals(0, store.read("needle", 1).total)
    }
}
