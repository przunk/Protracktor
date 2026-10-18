// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.randomPlayback
import com.przunk.protracktor.player.externalPlayback
import com.przunk.protracktor.player.collectionPlayback
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PlaylistReturnNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val tune = TrackRef(id = "random", title = "Random tune")
    private val state = mutableStateOf(PlayerUiState(
        queue = PlayQueue(tracks = listOf(TrackRef(id = "playlist", title = "Playlist tune"))),
        playback = randomPlayback(tune),
    ))
    private val calls = mutableListOf<String>()

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun stop() { activity.close() }

    private fun show(paired: Boolean = false) {
        activity.get().setContent {
            MaterialTheme {
                Column {
                    PlaylistTopBar(
                        state = state.value,
                        paired = paired,
                        onChoosePlaylist = { calls += "choose" },
                        onBrowse = { calls += "browse" },
                        onDiscard = { calls += "discard" },
                        onSave = { calls += "save" },
                        onSendToBrowser = { calls += "web" },
                        onRescan = { calls += "rescan" },
                        onSettings = { calls += "settings" },
                    )
                    PlaylistScreen(
                        state = state.value,
                        listState = rememberLazyListState(),
                        onPlayAt = { calls += "play:$it" }, onRemoveAt = {}, onMove = { _, _ -> },
                        onShowNeighbours = {}, onShareFile = {}, onShareAudio = {},
                        onShareLink = {}, onSendToWeb = {}, onBrowse = { calls += "browse" },
                        onPickDownloads = {}, canBrowse = true, stateKnown = true,
                        contentPadding = PaddingValues(),
                    )
                }
            }
        }
        val view = activity.get().window.decorView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 720, 1600)
    }

    @Test fun `Random playback leaves the playlist and navigation usable`() {
        show()
        compose.onNodeWithText("Back to Random").assertDoesNotExist()
        compose.onNodeWithText("Playlist tune").assertExists().performClick()
        compose.onNodeWithText("Browse").assertIsEnabled().performClick()
        compose.onNodeWithText("Settings").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("play:0", "browse", "settings"), calls)
            assertEquals(tune, state.value.current)
        }
    }

    @Test fun `both Web gestures remain available while another collection plays`() {
        show()
        compose.onNodeWithText("WEB").assertIsEnabled().performTouchInput { click(); longClick() }
        compose.runOnIdle { assertEquals(listOf("web", "rescan"), calls) }
    }

    @Test fun `external playback also leaves playlist navigation available`() {
        state.value = state.value.copy(playback = externalPlayback(tune))
        show()
        compose.onNodeWithText("Browse").assertIsEnabled()
        compose.onNodeWithText("WEB").assertIsEnabled()
        compose.onNodeWithText("Settings").assertIsEnabled()
        compose.onNodeWithText("Playlist tune").assertExists()
    }

    @Test fun `results playback has no blocking return-choice cover`() {
        state.value = state.value.copy(playback = collectionPlayback(PlayQueue(listOf(tune)).startAt(0)))
        show()
        compose.onNodeWithText("Back to the playlist").assertDoesNotExist()
        compose.onNodeWithText("Browse").assertIsEnabled()
        compose.onNodeWithText("Playlist tune").assertExists()
    }

}
