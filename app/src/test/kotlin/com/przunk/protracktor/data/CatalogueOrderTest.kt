// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.net.Asma
import com.przunk.protracktor.net.CatalogueEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** C136: a catalogue's formats and authors are listed the same whatever the case of their names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CatalogueOrderTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `small letters sort among capitals, not after Z`() = runBlocking {
        ProtracktorDatabase.of(context).writableDatabase.delete("catalogues", null, null)
        val store = CatalogueStore(context)
        val rows = listOf("Composers/Zyx", "Composers/przunk", "Composers/Aki", "Composers/null1024",
            "Composers/Nork", "Composers/nork", "Games/x", "demos/y").map { place ->
            val (section, author) = place.split('/')
            CatalogueEntry("asma/$section/$author/tune.sap", section, author, "tune.sap", 42)
        }
        store.replaceIndex(Asma, rows, "test")
        assertEquals(listOf("Aki", "Nork", "nork", "null1024", "przunk", "Zyx"),
            store.authors(Asma.id, "Composers").map { it.name })
        assertEquals(listOf("Composers", "demos", "Games"), store.formats(Asma.id).map { it.name })
    }
}
