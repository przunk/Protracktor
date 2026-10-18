// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.*
import org.junit.After
import org.junit.Assert.*
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
class QueueExperimentUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private var rolls = 0

    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { activity.close() }
    private fun measure() {
        val view = activity.get().window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 720, 1600)
        compose.waitForIdle()
    }
    private fun dice() {
        activity.get().setContent { MaterialTheme { RandomDice(onReroll = { rolls++ }) } }
        measure()
    }

    @Test fun `a physical single tap never rerolls`() {
        dice()
        compose.onNodeWithContentDescription("Start a new Random list").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(0, rolls) }
    }

    @Test fun `a physical double tap rerolls exactly once`() {
        dice()
        compose.onNodeWithContentDescription("Start a new Random list").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1, rolls) }
    }

    @Test fun `accessibility activation has an equivalent reset without a custom gesture`() {
        dice()
        compose.onNodeWithContentDescription("Start a new Random list")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, rolls) }
    }

    @Test fun `an empty Random record shows rolling only while its own request is pending`() {
        val shown = androidx.compose.runtime.mutableStateOf(PlayerUiState(
            playback = ActiveQueue(QueueSource.Random(1), PlayQueue(emptyList())), loadingTrack = true,
        ))
        activity.get().setContent { MaterialTheme {
            RandomScreen(state = shown.value, browse = BrowseState(),
                listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                onPlayAt = {}, onRemoveAt = {}, onFilter = {}, onShowNeighbours = {},
                onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                onAddToOtherPlaylist = {}, onAddSelectedToPlaylist = {},
                contentPadding = androidx.compose.foundation.layout.PaddingValues())
        } }
        measure()
        compose.onNodeWithText("Rolling…").assertExists()
        compose.runOnIdle { shown.value = shown.value.copy(loadingTrack = false) }
        compose.onNodeWithText("Rolling…").assertDoesNotExist()
        compose.runOnIdle {
            shown.value = shown.value.copy(loadingTrack = true,
                playback = collectionPlayback(PlayQueue(listOf(TrackRef("author", "Author"))).startAt(0)))
        }
        compose.onNodeWithText("Rolling…").assertDoesNotExist()
    }

    @Test fun `queue rows can be played and inspected but have no removal or selection editing`() {
        val rows = listOf(TrackRef("a", "First"), TrackRef("b", "Second"))
        val chosen = mutableListOf<Int>()
        val state = PlayerUiState(playback = collectionPlayback(PlayQueue(rows).startAt(0)))
        activity.get().setContent { MaterialTheme {
            QueueScreen(state, onPlay = { chosen += it }, onShowNeighbours = {}, onShareFile = {},
                onShareAudio = {}, onShareLink = {}, onSendToWeb = {}, onAddToPlaylist = {})
        } }
        measure()
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle { assertEquals(listOf(1), chosen) }
        compose.onNodeWithContentDescription("Actions for Second").performClick()
        compose.onNodeWithText(activity.get().getString(R.string.action_info)).assertExists()
        compose.onNodeWithText(activity.get().getString(R.string.action_delete)).assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }
}
