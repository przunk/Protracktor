// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import com.przunk.protracktor.R
import com.przunk.protracktor.player.*
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Redesign E2: the dock's two forms, the grip, the swipes and the collapsed seek bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "en-w360dp-h800dp-xhdpi")
class DockCollapseUiTest {
    private class Host : org.junit.rules.ExternalResource() {
        lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
        override fun before() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
        override fun after() { controller.close() }
    }
    private val hostRule = Host()
    @get:Rule val compose: androidx.compose.ui.test.junit4.ComposeContentTestRule =
        androidx.compose.ui.test.junit4.AndroidComposeTestRule(hostRule) { hostRule.controller.get() }
    private val tune = TrackRef("song", "Battle of honour", fileName = "battle.it", author = "Treks")
    private val state = mutableStateOf(PlayerUiState(queue = PlayQueue(listOf(tune, tune.copy(id = "two"))).startAt(0),
        playing = true, durationSeconds = 200.0, positionSeconds = 20.0, metadata = mapOf("title" to "Battle of honour")))
    private val collapsed = mutableStateOf(false)
    private val calls = mutableListOf<String>()
    private val seeks = mutableListOf<Double>()
    private fun text(id: Int) = hostRule.controller.get().getString(id)

    // Driven frame by frame, as the shell tests are: with the clock running by itself this harness
    // never reports idle after some presses, while the same presses settle in the shell.
    private fun frame(ms: Long = 300) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(ms)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun show() = compose.also { it.mainClock.autoAdvance = false }.setContent { MaterialTheme {
        PlayerDock(state.value, onSeek = { seeks += it }, onKeep = { calls += "keep" }, onExpand = { calls += "nowPlaying" },
            onBrowse = {}, onPlayPause = { calls += "play" }, onPrevious = { calls += "previous" }, onPreviousFile = {},
            onNext = { calls += "next" }, onNextFile = {}, onShuffle = {}, onRepeat = {}, onQueue = { calls += "queue" },
            collapsed = collapsed.value, onCollapsedChange = { collapsed.value = it })
    } }

    @Test fun `expanded at first, a drag down collapses it, and TalkBack has both as actions`() {
        show()
        compose.onNodeWithContentDescription(text(R.string.a11y_shuffle_off)).assertExists()
        compose.onNodeWithTag("dock-collapsed").assertDoesNotExist()
        // No grip: nothing is drawn to say the dock moves.
        compose.onNodeWithTag("dock-grip").assertDoesNotExist()
        compose.onNodeWithTag("player-dock").performTouchInput {
            down(Offset(centerX, 20f)); moveBy(Offset(0f, 60f)); moveBy(Offset(0f, 60f)); up()
        }
        frame()
        assertTrue(collapsed.value)
        compose.onNodeWithTag("dock-collapsed").assertExists()
        val dock = compose.onNodeWithTag("player-dock").fetchSemanticsNode()
        val actions = dock.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertEquals(listOf(text(R.string.dock_expand)), actions.map { it.label })
        actions.single().action()
        frame()
        assertFalse(collapsed.value)
        val again = compose.onNodeWithTag("player-dock").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertEquals(listOf(text(R.string.dock_collapse)), again.map { it.label })
    }

    @Test fun `dragging the collapsed tune up expands`() {
        collapsed.value = true
        show()
        compose.onNodeWithTag("dock-collapsed-tune").performTouchInput { swipeUp(startY = centerY, endY = centerY - 120f) }
        frame()
        assertFalse(collapsed.value)
    }

    @Test fun `swiping the collapsed tune moves between tunes, also as TalkBack actions`() {
        collapsed.value = true
        show()
        val tuneArea = compose.onNodeWithTag("dock-collapsed-tune")
        tuneArea.performTouchInput { swipeLeft() }; frame()
        tuneArea.performTouchInput { swipeRight() }
        frame()
        assertEquals(listOf("next", "previous"), calls)
        val actions = tuneArea.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertEquals(listOf(text(R.string.a11y_next), text(R.string.a11y_previous)), actions.map { it.label })
        actions.forEach { it.action() }
        assertEquals(listOf("next", "previous", "next", "previous"), calls)
        // A tap on the tune expands rather than playing anything.
        tuneArea.performClick()
        frame()
        assertFalse(collapsed.value)
    }

    @Test fun `the collapsed bar seeks, and Play, Queue and + are there`() {
        collapsed.value = true
        // Playing from Random, so the tune is not the playlist's and + is offered.
        state.value = state.value.copy(playback = ActiveQueue(QueueSource.Random(1L), PlayQueue(listOf(tune)).startAt(0)))
        assertTrue(state.value.awayFromPlaylist)
        show()
        frame()
        compose.runOnIdle { assertTrue(state.value.seekable) }
        val seek = compose.onNodeWithTag("dock-collapsed-seek")
        seek.performTouchInput { down(percentOffset(0.75f, 0.5f)) }; frame(100)
        seek.performTouchInput { up() }; frame()
        assertEquals(1, seeks.size)
        assertEquals(150.0, seeks.single(), 25.0)
        compose.onNodeWithTag("dock-collapsed-play").performClick(); frame()
        compose.onNodeWithTag("queue-toggle").performClick(); frame()
        compose.onNodeWithTag("dock-keep").performClick(); frame()
        assertEquals(listOf("play", "queue", "keep"), calls)
        // As sketched: the tune, then +, then Play, then Queue.
        val keep = compose.onNodeWithTag("dock-keep").fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithTag("dock-collapsed-play").fetchSemanticsNode().boundsInRoot
        val queue = compose.onNodeWithTag("queue-toggle").fetchSemanticsNode().boundsInRoot
        assertTrue(keep.right <= play.left + 0.5f && play.right <= queue.left + 0.5f)
        // Play and Queue are kept apart, so the two filled shapes do not read as one.
        val density = hostRule.controller.get().resources.displayMetrics.density
        assertTrue("Play and Queue too close: ${queue.left - play.right} px",
            queue.left - play.right >= PLAY_QUEUE_GAP.value * density - 0.5f)
        // The seek bar lies on the dock's top edge, above the tune.
        val seekBounds = compose.onNodeWithTag("dock-collapsed-seek").fetchSemanticsNode().boundsInRoot
        val dockTop = compose.onNodeWithTag("player-dock").fetchSemanticsNode().boundsInRoot.top
        val tuneTop = compose.onNodeWithTag("dock-collapsed-tune").fetchSemanticsNode().boundsInRoot.top
        assertEquals(dockTop, seekBounds.top, 0.5f)
        assertTrue(seekBounds.bottom <= tuneTop + 0.5f)
    }

    @Test fun `a swipe that starts on Play moves the tune and does not press Play, while a tap still does`() {
        collapsed.value = true
        state.value = state.value.copy(playback = ActiveQueue(QueueSource.Random(1L), PlayQueue(listOf(tune)).startAt(0)))
        show()
        compose.onNodeWithTag("dock-collapsed-play").performTouchInput {
            down(center); moveBy(Offset(-60f, 0f)); moveBy(Offset(-60f, 0f)); up()
        }
        frame()
        compose.onNodeWithTag("dock-keep").performTouchInput {
            down(center); moveBy(Offset(60f, 0f)); moveBy(Offset(60f, 0f)); up()
        }
        frame()
        assertEquals(listOf("next", "previous"), calls)
        compose.onNodeWithTag("dock-collapsed-play").performClick(); frame()
        assertEquals(listOf("next", "previous", "play"), calls)
    }

    @Test fun `on the full dock a drag down that starts on Pause collapses it and does not pause`() {
        show()
        compose.onNodeWithContentDescription(text(R.string.a11y_pause)).performTouchInput {
            down(center); moveBy(Offset(0f, 60f)); moveBy(Offset(0f, 60f)); up()
        }
        frame()
        assertTrue(collapsed.value)
        assertEquals(emptyList<String>(), calls)
    }

    private fun collapsedFromRandom() {
        collapsed.value = true
        state.value = state.value.copy(playback = ActiveQueue(QueueSource.Random(1L), PlayQueue(listOf(tune)).startAt(0)))
        show()
        frame()
    }

    @Test fun `a sideways drag along the edge bar seeks once, where it is let go`() {
        collapsedFromRandom()
        compose.onNodeWithTag("dock-collapsed-seek").performTouchInput {
            down(percentOffset(0.2f, 0.5f)); moveTo(percentOffset(0.4f, 0.5f)); moveTo(percentOffset(0.5f, 0.5f)); up()
        }
        frame()
        assertEquals(1, seeks.size)
        assertEquals(100.0, seeks.single(), 5.0)
        assertTrue(collapsed.value)
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `a drag up from the edge bar expands the dock and does not seek`() {
        collapsedFromRandom()
        compose.onNodeWithTag("dock-collapsed-seek").performTouchInput {
            down(percentOffset(0.5f, 0.5f)); moveBy(Offset(0f, -60f)); moveBy(Offset(0f, -60f)); up()
        }
        frame()
        assertEquals(emptyList<Double>(), seeks)
        assertFalse(collapsed.value)
    }

    @Test fun `the edge bar tells TalkBack where the tune is and takes a position`() {
        collapsedFromRandom()
        val node = compose.onNodeWithTag("dock-collapsed-seek").fetchSemanticsNode()
        val range = node.config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(20f, range.current, 0.5f)
        assertEquals(200f, range.range.endInclusive, 0.5f)
        assertEquals(text(R.string.a11y_seek), node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        compose.runOnIdle { node.config[androidx.compose.ui.semantics.SemanticsActions.SetProgress].action!!.invoke(60f) }
        assertEquals(listOf(60.0), seeks)
    }

    @Test fun `an edge bar that cannot seek ignores a tap`() {
        collapsed.value = true
        // A backend that cannot seek says so in the tune's description.
        state.value = state.value.copy(metadata = state.value.metadata + ("seekable" to "0"))
        assertFalse(state.value.seekable)
        show(); frame()
        compose.onNodeWithTag("dock-collapsed-seek").performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        frame()
        assertEquals(emptyList<Double>(), seeks)
    }
}
