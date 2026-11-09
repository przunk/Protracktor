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

/** B60: Browse's bar with Search closed and open, to look at before an APK goes out. */
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

    private fun picture(): android.graphics.Bitmap {
        val view = activity.get().window.decorView
        return android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            .also { view.draw(android.graphics.Canvas(it)) }
    }
}
