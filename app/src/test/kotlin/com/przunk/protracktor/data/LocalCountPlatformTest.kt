// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** RV-B021: the total beside local search results is counted under the same platform filter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalCountPlatformTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun close() { ProtracktorDatabase.of(context).close() }

    private fun file(name: String) = IndexedFile("content://t/$name", "content://t", "Music", name, 1, "x", "x",
        "Tune $name", "Someone", 0, 1)

    @Test fun `a platform filter applies to the count as to the results`() = runBlocking {
        val store = LibraryIndexStore(context)
        store.addToFolder(listOf(file("one.sid"), file("two.sid"), file("three.mod")), "test")
        assertEquals(3, store.countMatches("Tune"))
        assertEquals(2, store.countMatches("Tune", setOf("c64")))
        assertEquals(store.search("Tune", 100, setOf("c64")).size, store.countMatches("Tune", setOf("c64")))
    }
}
