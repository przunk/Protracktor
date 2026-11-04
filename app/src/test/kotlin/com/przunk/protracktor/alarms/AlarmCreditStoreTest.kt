// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.data.TrackMetadataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AlarmCreditStoreTest {
    @Test fun `real database credits are distinct bounded and survive only in the prepared snapshot`() = runBlocking {
        val store = TrackMetadataStore(ApplicationProvider.getApplicationContext<Context>())
        val rows = (1..200).joinToString("\n") { it.toString(16).padStart(12, '0') + "\tArtist $it\t\t\t1992" }
        store.replaceAllFrom(rows.toByteArray())
        val sampled = store.sampleCredits()
        assertEquals(128, sampled.size)
        assertEquals(sampled.size, sampled.distinct().size)
        assertTrue(sampled.all { it.author.startsWith("Artist ") && it.year == "1992" })
        store.clear()
        assertTrue(store.sampleCredits().isEmpty())
        assertEquals(128, sampled.size)
    }
}
