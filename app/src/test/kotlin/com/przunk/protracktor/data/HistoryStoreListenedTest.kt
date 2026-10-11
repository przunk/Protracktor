// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import com.przunk.protracktor.player.HistoryPresentation
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
class HistoryStoreListenedTest {
    @Test fun `real store preserves qualification metadata and clear through production SQL`() = runBlocking {
        val store = HistoryStore(RuntimeEnvironment.getApplication())
        store.clear()
        store.record("a", "First", "Source", "a.mod", "Author", 123)
        val before = store.page(0, 10).single()
        assertEquals(emptySet<String>(), store.listenedAmong(setOf("a")))
        store.qualify("a")
        assertEquals(before, store.page(0, 10).single())
        store.record("a", "Better", "Source", "a.mod", "Author", 123)
        assertEquals(setOf("a"), store.listenedAmong(setOf("a", "missing")))
        assertEquals(2, store.page(0, 10).single().playCount)
        assertEquals("Better", store.page(0, 10).single().title)
        store.qualify("missing")
        assertEquals(1, store.count())
        store.clear()
        store.qualify("a")
        assertEquals(emptySet<String>(), store.listenedAmong(setOf("a")))
        assertEquals(0, store.count())
        store.record("a", "New", "", "a.mod", "", 0)
        assertEquals(emptySet<String>(), store.listenedAmong(setOf("a")))
    }

    @Test fun `batch lookup supports more than one bind batch and special characters`() = runBlocking {
        val store = HistoryStore(RuntimeEnvironment.getApplication())
        store.clear()
        val id = "quote'/?%#\"track"
        store.record(id, "Track", "", "track.mod", "", 0)
        store.qualify(id)
        assertEquals(setOf(id), store.listenedAmong((1..1200).map { "missing$it" }.toSet() + id))
        assertEquals(emptySet<String>(), store.listenedAmong(emptySet()))
        assertEquals(mapOf(id to HistoryPresentation("Track", true)),
            store.presentationAmong((1..1200).map { "missing$it" }.toSet() + id))
        assertEquals(emptyMap<String, HistoryPresentation>(), store.presentationAmong(emptySet()))
    }

    @Test fun `learned titles persist before qualification but clear with History`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = HistoryStore(context)
        store.clear()
        store.record("a", "Decoded", "Source", "a.mod", "", 0)
        store.record("fallback", "fallback.mod", "Source", "fallback.mod", "", 0)
        store.record("blank", "  ", "Source", "blank.mod", "", 0)
        val before = store.page(0, 10)
        val reopened = HistoryStore(context)
        assertEquals(mapOf("a" to HistoryPresentation("Decoded", false),
            "fallback" to HistoryPresentation("", false), "blank" to HistoryPresentation("", false)),
            reopened.presentationAmong(setOf("a", "fallback", "blank", "absent")))
        assertEquals(before, reopened.page(0, 10))
        store.clear()
        assertTrue(reopened.presentationAmong(setOf("a", "fallback", "blank")).isEmpty())
    }
}
