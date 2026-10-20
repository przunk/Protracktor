// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
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

    @androidx.compose.runtime.Composable
    private fun panel(state: PlayerUiState, onPlay: (Long) -> Unit = {}) {
        QueueScreen(state, onPlay, onEnqueue = { _, _ -> }, onPlayPending = {}, onRemovePending = {}, onRemoveContext = {}, onClear = {},
            onMovePending = { _, _ -> })
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
        compose.mainClock.autoAdvance = false
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
        compose.onNodeWithText("Double-tap the dice to reroll the list").assertIsDisplayed()
        compose.onNodeWithText("Rolling…").assertExists()
        compose.runOnIdle {
            shown.value = shown.value.copy(loadingTrack = false)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Rolling…").assertDoesNotExist()
        compose.runOnIdle {
            shown.value = shown.value.copy(loadingTrack = true,
                playback = collectionPlayback(PlayQueue(listOf(TrackRef("author", "Author"))).startAt(0)))
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Rolling…").assertDoesNotExist()
    }

    @Test fun `unified rows play on tap and open Info on physical hold without visible action buttons`() {
        val rows = listOf(TrackRef("a", "First"), TrackRef("b", "Second"))
        val chosen = mutableListOf<Long>()
        val state = PlayerUiState(playback = collectionPlayback(PlayQueue(rows).startAt(0)))
        activity.get().setContent { MaterialTheme {
            panel(state, onPlay = { chosen += it })
        } }
        measure()
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle { assertEquals(listOf(1L), chosen) }
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        compose.onNodeWithText("Second").performTouchInput { longClick() }
        compose.onNodeWithText(activity.get().getString(R.string.action_close)).assertExists()
        compose.runOnIdle { assertEquals(listOf(1L), chosen) }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test fun `opening Queue starts with the playing row at the top and reopening follows the new cursor`() {
        compose.mainClock.autoAdvance = false
        val rows = (0..60).map { TrackRef("row:$it", "Tune $it") }
        val shown = androidx.compose.runtime.mutableStateOf(true)
        val selected = androidx.compose.runtime.mutableStateOf(30)
        activity.get().setContent { MaterialTheme {
            if (shown.value) panel(PlayerUiState(playback = collectionPlayback(PlayQueue(rows).startAt(selected.value))))
        } }
        measure()
        compose.onNodeWithText("Tune 30").assertIsDisplayed()
        compose.onNodeWithText("Tune 29").assertIsNotDisplayed()
        val top = compose.onNodeWithText("Tune 30").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Tune 32").performScrollTo()
        compose.runOnIdle { shown.value = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle { selected.value = 45; shown.value = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithText("Tune 45").assertIsDisplayed()
        compose.onNodeWithText("Tune 44").assertIsNotDisplayed()
        assertEquals(top, compose.onNodeWithText("Tune 45").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun `Queue is a separate right hand action outside the expandable player card`() {
        var expands = 0
        var queues = 0
        activity.get().setContent { MaterialTheme {
            PlayerDock(PlayerUiState(queue = PlayQueue(listOf(TrackRef("a", "Current tune"))).startAt(0)),
                onSeek = {}, onKeep = {}, onExpand = { expands++ }, onBrowse = {}, onPlayPause = {},
                onPrevious = {}, onPreviousFile = {}, onNext = {}, onNextFile = {},
                onShuffle = {}, onRepeat = {}, onQueue = { queues++ })
        } }
        measure()
        val card = compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_expand_player))
        val queue = compose.onNodeWithText("Queue")
        assertTrue(queue.fetchSemanticsNode().boundsInRoot.left >= card.fetchSemanticsNode().boundsInRoot.right)
        queue.performClick()
        compose.runOnIdle { assertEquals(1, queues); assertEquals(0, expands) }
        card.performClick()
        compose.runOnIdle { assertEquals(1, queues); assertEquals(1, expands) }
    }

    @Test fun `system Back closes the visible screen before remembered Random`() {
        compose.mainClock.autoAdvance = false
        val level = androidx.compose.runtime.mutableStateOf(3)
        val calls = mutableListOf<String>()
        activity.get().setContent { MaterialTheme {
            androidx.compose.material3.Text("Navigation")
            ScreenBackHandler(scanning = level.value >= 3, settings = level.value >= 2,
                browse = level.value >= 1, random = true,
                onScanner = { calls += "scanner" }, onSettings = { calls += "settings" },
                onBrowse = { calls += "browse" }, onRandom = { calls += "random" })
        } }
        measure()
        for (destination in 3 downTo 0) {
            compose.runOnIdle { level.value = destination; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        }
        compose.runOnIdle { assertEquals(listOf("scanner", "settings", "browse", "random"), calls) }
    }

    @Test fun `equal manual and source files coexist but only the manual occurrence is current`() {
        val track = TrackRef("a", "Source tune")
        val context = ActiveQueue(QueueSource.Playlist(1), PlayQueue(listOf(track)).startAt(0))
        val manual = PlaybackSchedule().enqueue(track).next(context) as ScheduledStep.Select
        activity.get().setContent { MaterialTheme {
            panel(PlayerUiState(playback = context, schedule = manual.schedule))
        } }
        measure()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithTag("playback-row-source:0").assertIsDisplayed()
        compose.onNodeWithTag("playback-row-manual:${manual.occurrence.id}").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Now playing").assertCountEquals(1)
        compose.onNodeWithContentDescription("Now playing")
            .assert(hasAnyAncestor(hasTestTag("playback-row-manual:${manual.occurrence.id}")))
        compose.onNodeWithContentDescription("Manual request").assertIsDisplayed()
        compose.onNodeWithText("Current track: Source tune").assertDoesNotExist()
    }

    @Test fun `manual loading over an empty Random context does not claim the dice is rolling`() {
        val context = ActiveQueue(QueueSource.Random(1), PlayQueue(emptyList()))
        val manual = PlaybackSchedule().enqueue(TrackRef("x", "Manual")).next(context) as ScheduledStep.Select
        activity.get().setContent { MaterialTheme {
            RandomScreen(PlayerUiState(playback = context, schedule = manual.schedule, loadingTrack = true),
                browse = BrowseState(), listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                onPlayAt = {}, onRemoveAt = {}, onFilter = {}, onShowNeighbours = {}, onShareFile = {},
                onShareAudio = {}, onShareLink = {}, onSendToWeb = {}, onAddToOtherPlaylist = {},
                onAddSelectedToPlaylist = {}, contentPadding = androidx.compose.foundation.layout.PaddingValues())
        } }
        measure()
        compose.onNodeWithText("Rolling…").assertDoesNotExist()
    }

    @Test fun `open context learns titles without moving rows or losing the source filename`() {
        compose.mainClock.autoAdvance = false
        val rows = listOf(TrackRef("a", "a.mod", fileName = "a.mod"), TrackRef("b", "b.mod", fileName = "b.mod"))
        val shown = androidx.compose.runtime.mutableStateOf(PlayerUiState(
            playback = collectionPlayback(PlayQueue(rows).startAt(0))))
        activity.get().setContent { MaterialTheme {
            panel(shown.value)
        } }
        measure()
        val before = compose.onNodeWithTag("playback-row-source:1").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            shown.value = shown.value.copy(learnedTitles = mapOf("b" to "Learned title"), positionSeconds = 4.0)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Learned title").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("playback-row-source:1").fetchSemanticsNode().boundsInRoot)
        assertEquals("b.mod", shown.value.activePlayback.order.tracks[1].fileName)
    }
}
