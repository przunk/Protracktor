// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import org.junit.After
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
class NowPlayingSharedTextUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        Settings.Global.putFloat(activity.get().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }
    @After fun stop() { activity.get().setContent { }; compose.waitForIdle(); activity.close() }

    @Test fun `actual dock uses the shared author platform year and loading statuses`() {
        val state = mutableStateOf(PlayerUiState(queue = PlayQueue(listOf(
            TrackRef("song", "Song title", fileName = "song.mod", author = "Author"))).startAt(0),
            metadata = mapOf("year" to "1992")))
        activity.get().setContent { MaterialTheme { PlayerDock(state.value,
            onSeek = {}, onKeep = {}, onExpand = {}, onBrowse = {}, onPlayPause = {},
            onPrevious = {}, onPreviousFile = {}, onNext = {}, onNextFile = {}, onShuffle = {}, onRepeat = {}) } }
        compose.onNodeWithText("Song title").assertIsDisplayed()
        compose.onNodeWithText("Author · Amiga · 1992").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(loadingTrack = true) }
        compose.onNodeWithText(activity.get().getString(R.string.dock_loading)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `actual dock retains the Polish idle subtitle`() {
        activity.get().setContent { MaterialTheme { PlayerDock(PlayerUiState(),
            onSeek = {}, onKeep = {}, onExpand = {}, onBrowse = {}, onPlayPause = {},
            onPrevious = {}, onPreviousFile = {}, onNext = {}, onNextFile = {}, onShuffle = {}, onRepeat = {}) } }
        compose.onNodeWithText(activity.get().getString(R.string.dock_idle_subtitle)).assertIsDisplayed()
    }
}
