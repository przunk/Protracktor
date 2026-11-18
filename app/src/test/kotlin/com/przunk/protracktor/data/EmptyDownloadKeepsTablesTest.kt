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

/** RV-B007: a download that parses to nothing leaves the table it was meant to replace as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class EmptyDownloadKeepsTablesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val maintenance = "<html><body>Down for maintenance</body></html>".toByteArray()

    @After fun close() { ProtracktorDatabase.of(context).close() }

    @Test fun `track metadata`() = runBlocking {
        val store = TrackMetadataStore(context)
        assertEquals(2, store.replaceAllFrom("000000000001\tA\t\t\t1992\n000000000002\tB\t\t\t1993".toByteArray()))
        assertEquals(0, store.replaceAllFrom(maintenance))
        assertEquals(2, store.count())
    }

    @Test fun `song lengths`() = runBlocking {
        val store = SongDbLengthStore(context)
        assertEquals(1, store.replaceAllFrom("00000b104a70\t1\t7800,p".toByteArray()))
        assertEquals(0, store.replaceAllFrom(maintenance))
        assertEquals(1, store.count())
    }

    @Test fun `Modland favourites`() = runBlocking {
        val store = FavouriteStore(context)
        val row = "0115668c8d39fe73ad8245b065d1d205\t1\t183386\tplayer\tft2play\tFasttracker\t4\t109036\t65d587da\t208f02c2\tProtracker/- unknown/x.mod"
        assertEquals(1, store.replaceAllFrom(row.toByteArray()))
        assertEquals(0, store.replaceAllFrom(maintenance))
        assertEquals(1, store.count())
    }

    @Test fun `a real replacement still replaces`() = runBlocking {
        val store = SongDbLengthStore(context)
        store.replaceAllFrom("00000b104a70\t1\t7800,p".toByteArray())
        assertEquals(2, store.replaceAllFrom("00000b104a71\t1\t7800,p\n00000b104a72\t1\t7800,p".toByteArray()))
        assertEquals(2, store.count())
    }
}
