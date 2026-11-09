// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import android.app.Application
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.ui.PlaybackShellTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** B60: Browse's bar with Search closed and open, and B61's tunes below, to look at before an APK goes out. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class BrowseFilterScreensTest : PlaybackShellTest() {
    @Test fun `Browse Search`() = shots("browse-search")

    @Test @Config(sdk = [29], application = Application::class, qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Browse Search in Polish`() = shots("browse-search-pl")

    private fun shots(name: String) {
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame()
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.ONLINE,
                openCatalogue = CatalogueSummary("amp", "AMP (Amiga Music Preservation)", 177705, 1L), openFormat = "MOD",
                groups = listOf("Nooze", "Nork", "Nightlight", "4-Mat", "Jogeir Liljedahl").map { CatalogueGroup(it, 12) },
                loading = false)
        }
        frame(300)
        picture().saveAs("$name-closed")
        compose.onNodeWithTag("browse-search-action").performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-field").performTextInput("no")
        frame(300)
        picture().saveAs("$name-open")
    }

    @Test fun `tunes below`() = below("browse-search-below")

    @Test @Config(sdk = [29], application = Application::class, qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `tunes below in Polish`() = below("browse-search-below-pl")

    private fun below(name: String) {
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame()
        val store = com.przunk.protracktor.data.CatalogueStore(application)
        kotlinx.coroutines.runBlocking {
            store.replaceIndex(com.przunk.protracktor.net.Modland, listOf("Nork" to "funky nork.mod", "Nork" to "nork tune.mod",
                "Moby" to "norkish.mod", "Nooze" to "strap.mod").map { (author, title) ->
                com.przunk.protracktor.net.CatalogueEntry("Protracker/$author/$title", "Protracker", author, title, 42)
            }, "test")
        }
        try {
            compose.runOnIdle {
                browse.value = browse.value.copy(domain = BrowseDomain.ONLINE,
                    openCatalogue = CatalogueSummary("modland", "Modland", 4, 1L), openFormat = "Protracker",
                    groups = listOf("Moby", "Nooze", "Nork").map { CatalogueGroup(it, 1) }, loading = false)
            }
            frame(300)
            compose.onNodeWithTag("browse-search-action").performClick()
            frame(300)
            compose.onNodeWithTag("browse-filter-field").performTextInput("nork")
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                compose.mainClock.advanceTimeBy(50)
                compose.onAllNodes(androidx.compose.ui.test.hasTestTag("below-row-2")).fetchSemanticsNodes().isNotEmpty()
            }
            frame(300)
            picture().saveAs(name)
        } finally {
            kotlinx.coroutines.runBlocking { store.clearIndex(com.przunk.protracktor.net.Modland.id) }
        }
    }

    private fun picture(): android.graphics.Bitmap {
        val view = activity.get().window.decorView
        return android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            .also { view.draw(android.graphics.Canvas(it)) }
    }
}
