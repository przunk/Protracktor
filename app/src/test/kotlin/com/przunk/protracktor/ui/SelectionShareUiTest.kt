// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B59: the playlist's selection bar offers Select all and one Share for the whole selection. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SelectionShareUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val tracks = listOf("One", "Two", "Three").map {
        TrackRef(Modland.urlFor("Protracker/Artist/$it.mod"), it, fileName = "$it.mod")
    }
    private val calls = mutableListOf<String>()

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PlaylistScreen(
                    state = PlayerUiState(queue = PlayQueue(tracks)), listState = rememberLazyListState(),
                    onPlayAt = {}, onRemoveAt = {}, onMove = { _, _ -> }, onShowNeighbours = {},
                    onShareFile = { calls += "files:" + it.joinToString(",") { t -> t.title } },
                    onShareAudio = { calls += "audio:" + it.title },
                    onShareLink = { calls += "links:" + it.joinToString(",") { t -> t.title } },
                    onSendToWeb = { calls += "web:" + it.joinToString(",") { t -> t.title } },
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

    private fun startSelecting() = compose.onNodeWithText("One").performTouchInput { longClick() }

    @Test fun `Select all ticks every row, then becomes Select none`() {
        startSelecting()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("3 selected", substring = true).assertExists()
        compose.onNodeWithText("Select none").performClick()
        compose.onNodeWithText("Select all").assertDoesNotExist()
    }

    @Test fun `Share offers files, links and Share with Protracktor for the whole selection`() {
        startSelecting()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Share as audio").assertDoesNotExist()
        compose.onNodeWithText("Share the links").performClick()
        compose.onNodeWithText("Share").assertDoesNotExist()
        assertEquals(listOf("links:One,Two,Three"), calls)
    }

    @Test fun `one ticked tune keeps the one-tune options, audio included`() {
        startSelecting()
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Share the file").assertExists()
        compose.onNodeWithText("Share as audio").performClick()
        assertEquals(listOf("audio:One"), calls)
    }

    @Test fun `files and Share with Protracktor carry the selection`() {
        startSelecting()
        compose.onNodeWithText("Two").performClick()
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Share the files").performClick()
        startSelecting()
        compose.onNodeWithText("Three").performClick()
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Share with Protracktor").performClick()
        assertEquals(listOf("files:One,Two", "web:One,Three"), calls)
    }
}
