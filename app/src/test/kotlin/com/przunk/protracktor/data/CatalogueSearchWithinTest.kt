// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.net.Asma
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Browse's Search, below the folders it narrows: one archive, or one format of it (2026-11-09). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CatalogueSearchWithinTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun mod(format: String, author: String, title: String) =
        CatalogueEntry("$format/$author/$title", format, author, title, 42)

    @Before fun index() { runBlocking {
        ProtracktorDatabase.of(context).writableDatabase.delete("catalogues", null, null)
        val store = CatalogueStore(context)
        store.replaceIndex(Modland, listOf(
            mod("Protracker", "Nork", "funky.mod"),
            mod("Protracker", "Nooze", "nothing.mod"),
            mod("Fasttracker 2", "Nork", "funky two.xm"),
            mod("Fasttracker 2", "Other", "quiet.xm"),
        ), "test")
        store.replaceIndex(Asma, listOf(CatalogueEntry("asma/Composers/funk/funky.sap", "Composers", "funk", "funky.sap", 42)), "test")
    } }

    @Test fun `the whole archive, or one format, by title or author`() = runBlocking {
        val store = CatalogueStore(context)
        assertEquals(listOf("funky two.xm", "funky.mod"), store.searchWithin(Modland.id, null, "FUNKY", 10).map { it.title })
        assertEquals(listOf("funky.mod"), store.searchWithin(Modland.id, "Protracker", "funky", 10).map { it.title })
        assertEquals(listOf("funky two.xm", "funky.mod"), store.searchWithin(Modland.id, null, "nork", 10).map { it.title })
        assertEquals(listOf("funky two.xm"), store.searchWithin(Modland.id, null, "two nork", 10).map { it.title })
        assertEquals(1, store.searchWithin(Modland.id, null, "funky", 1).size)
    }

    @Test fun `nothing typed asks nothing, and another archive is not searched`() = runBlocking {
        val store = CatalogueStore(context)
        assertEquals(emptyList<CatalogueTrack>(), store.searchWithin(Modland.id, null, "  ", 10))
        assertEquals(listOf("asma/Composers/funk/funky.sap"), store.searchWithin(Asma.id, null, "funky", 10).map { it.path })
        assertEquals(emptyList<CatalogueTrack>(), store.searchWithin("nonesuch", null, "funky", 10))
    }
}
