// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.ui.PlaylistScreen
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B59: the playlist's selection bar and its Share menu, to look at before an APK goes out. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SelectionBarScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val tracks = listOf("Space Debris", "Enigma", "Hybris", "Stardust Memories").map {
        TrackRef(Modland.urlFor("Protracker/Artist/$it.mod"), it, "Modland / Protracker / Artist", fileName = "$it.mod")
    }

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            ProtracktorTheme(theme = AppTheme.DARK, dynamicColour = false) {
                PlaylistScreen(
                    state = PlayerUiState(queue = PlayQueue(tracks)), listState = rememberLazyListState(),
                    onPlayAt = {}, onRemoveAt = {}, onMove = { _, _ -> }, onShowNeighbours = {},
                    onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                    onBrowse = {}, onPickDownloads = {}, canBrowse = true, stateKnown = true,
                    contentPadding = PaddingValues(),
                )
            }
        }
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    @Test fun `selection bar`() = shot("selection-bar")

    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `selection bar in Polish`() = shot("selection-bar-pl")

    private fun shot(name: String) {
        compose.onNodeWithText("Space Debris").performTouchInput { longClick() }
        compose.onNodeWithText("Hybris").performClick()
        compose.waitForIdle()
        // Drawn straight from the window, as Render does: Compose's captureToImage waits for a
        // redraw Robolectric does not deliver. The Share menu is a popup window of its own and is
        // held by SelectionShareUiTest instead.
        val view = activity.get().window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        bitmap.saveAs(name)
    }
}
