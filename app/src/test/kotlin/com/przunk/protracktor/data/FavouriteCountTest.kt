// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** RV-B020: the favourites count is of what Random can draw -- playable rows only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FavouriteCountTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun close() { ProtracktorDatabase.of(context).close() }

    @Test fun `a favourite this build cannot play is not counted`() = runBlocking {
        CatalogueStore(context).replaceIndex(Modland, listOf(
            CatalogueEntry("Protracker/Artist/song.mod", "Protracker", "Artist", "song.mod", 100),
            CatalogueEntry("Unknown/Artist/song.xyz", "Unknown", "Artist", "song.xyz", 200),
        ), "test")
        val row = { path: String -> "0115668c8d39fe73ad8245b065d1d205\t1\t183386\tplayer\tft2play\tFasttracker\t4\t109036\t65d587da\t208f02c2\t$path" }
        val favourites = FavouriteStore(context)
        assertEquals(2, favourites.replaceAllFrom((row("Protracker/Artist/song.mod") + "\n" + row("Unknown/Artist/song.xyz")).toByteArray()))
        assertEquals(1, favourites.playableCount())
    }
}
