// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.FloatingControlsState
import com.przunk.protracktor.player.PlaylistHeart
import com.przunk.protracktor.player.TrackRef
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
class FloatingControlsPanelTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val heart = PlaylistHeart(PlaylistHeart.Request("song", 7), TrackRef("song", "Song"), "My list", false, false)
    private val model = mutableStateOf(FloatingControlsState(true, true, false, true, true, heart, true, true))
    private val events = mutableListOf<String>()
    private val moves = mutableListOf<Pair<Int, Int>>()
    private var ends = 0
    private var starts = 0
    private val cancellations = mutableListOf<Boolean>()
    private lateinit var root: FloatingDragContainer

    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { root.cancelGesture(notify = false); activity.close() }

    private fun show(scale: Float = 1f) {
        root = FloatingDragContainer(activity.get(), {}, {}, { starts++ },
            { x, y, _, _ -> moves += x to y }, { cancelled -> ends++; cancellations += cancelled })
        val content = ComposeView(activity.get()).apply { setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme {
                    FloatingControlsPanel(model.value,
                        { events += "previous" }, { events += "play" }, { events += "next" },
                        { events += "heart:${it.playlistId}:${it.trackId}" }, { events += "hide" },
                        { x, y -> moves += x to y }, { ends++ },
                        { events += "previousFile"; model.value = model.value.copy(playing = !model.value.playing) },
                        { events += "nextFile"; model.value = model.value.copy(playing = !model.value.playing) },
                        Modifier.width(288.dp))
                }
            }
        } }
        root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT))
        activity.get().setContentView(root)
        compose.waitForIdle()
    }

    // Inject through the native parent, as WindowManager does. Direct Compose injection skips it.
    private fun gesture(tag: String, delta: Offset = Offset.Zero, holdMillis: Long = 0,
                        cancel: Boolean = false) {
        val point = compose.onNodeWithTag("floating-$tag").fetchSemanticsNode().boundsInRoot.center
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, at: Offset) {
            compose.runOnIdle {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
                try { root.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            compose.mainClock.advanceTimeBy(16)
        }
        send(MotionEvent.ACTION_DOWN, point)
        if (delta != Offset.Zero) send(MotionEvent.ACTION_MOVE, point + delta)
        if (holdMillis > 0) compose.mainClock.advanceTimeBy(holdMillis)
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, point + delta)
        compose.waitForIdle()
    }

    @Test fun `physical taps dispatch all actions without moving the panel`() {
        show()
        for (tag in listOf("previous", "play", "next", "heart")) {
            compose.onNodeWithTag("floating-$tag").assertHeightIsAtLeast(64.dp).assertWidthIsAtLeast(56.dp)
        }
        for (tag in listOf("previous", "play", "next", "heart")) gesture(tag)
        assertEquals(listOf("previous", "play", "next", "heart:7:song"), events)
        compose.onNodeWithTag("floating-hide").assertDoesNotExist()
        compose.onNodeWithTag("floating-drag").assertDoesNotExist()
        assertTrue(moves.isEmpty())
        assertEquals(0, ends)
    }

    @Test fun `dragging a transport button moves the whole panel and cancels its click and hold`() {
        show()
        gesture("next", Offset(40f, 0f), holdMillis = 900)
        assertTrue(events.isEmpty())
        assertTrue(moves.any { it.first != 0 || it.second != 0 })
        assertEquals(1, ends)
        assertEquals(1, starts)
        assertEquals(listOf(false), cancellations)
    }

    @Test fun `stationary holds skip files once while ordinary taps retain subsong transport`() {
        show()
        gesture("next", holdMillis = 1400)
        gesture("previous", holdMillis = 1400)
        assertEquals(listOf("nextFile", "previousFile"), events)
        gesture("next")
        gesture("previous")
        assertEquals(listOf("nextFile", "previousFile", "next", "previous"), events)
        assertTrue(moves.isEmpty())
    }

    @Test fun `unavailable file holds do not fall through to a subsong click`() {
        show()
        compose.runOnIdle { model.value = model.value.copy(previousFile = false, nextFile = false) }
        gesture("next", holdMillis = 700)
        gesture("previous", holdMillis = 700)
        assertTrue(events.isEmpty())
        gesture("next")
        assertEquals(listOf("next"), events)
    }

    @Test fun `disabled buttons still drag and cancellation cannot dispatch playback`() {
        show()
        compose.runOnIdle { model.value = model.value.copy(previous = false) }
        gesture("previous", Offset(-50f, 70f), cancel = true)
        assertTrue(events.isEmpty())
        assertTrue(moves.isNotEmpty())
        assertEquals(listOf(true), cancellations)
    }

    @Test fun `small finger movement remains a tap rather than a drag`() {
        show()
        gesture("play", Offset(1f, 1f))
        assertEquals(listOf("play"), events)
        assertTrue(moves.isEmpty())
        assertEquals(0, starts)
    }

    @Test fun `pending save retains every button slot and a second heart tap cannot become Next`() {
        show()
        val tags = listOf("previous", "play", "next", "heart")
        fun bounds() = tags.map { compose.onNodeWithTag("floating-$it").fetchSemanticsNode().boundsInRoot }
        val before = bounds()
        compose.onNodeWithTag("floating-heart").performTouchInput { click() }
        compose.runOnIdle { model.value = model.value.copy(heart = heart.copy(member = true, saving = true)) }
        compose.onNodeWithTag("floating-heart").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(before, bounds())
        assertEquals(listOf("heart:7:song"), events)
        compose.runOnIdle { model.value = model.value.copy(heart = heart.copy(member = true)) }
        compose.onNodeWithTag("floating-heart").performTouchInput { click() }
        assertEquals(listOf("heart:7:song", "heart:7:song"), events)
        assertEquals(before, bounds())
    }

    @Test fun `loading offers Stop and an unavailable heart keeps its place`() {
        show()
        val before = compose.onNodeWithTag("floating-heart").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { model.value = model.value.copy(loading = true, heart = null, previous = false) }
        compose.onNodeWithText("Stop").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Loading…").assertIsDisplayed()
        compose.onNodeWithTag("floating-previous").assertIsNotEnabled()
        assertEquals(before, compose.onNodeWithTag("floating-heart").fetchSemanticsNode().boundsInRoot)
        assertEquals(listOf("play"), events)
    }

    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `large Polish captions remain visible with accessible move actions`() {
        show(scale = 2f)
        for (text in listOf("Wstecz", "Pauza", "Dalej", "Zapisz")) {
            compose.onNodeWithText(text).assertIsDisplayed()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { get ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    assertTrue(get(results))
                    val layout = results.single()
                    assertEquals(text, 1, layout.lineCount)
                    assertFalse("$text: size=${layout.size}, paragraphWidth=${layout.multiParagraph.width}", layout.hasVisualOverflow)
                }
        }
        val actions = compose.onNodeWithTag("floating-panel").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertEquals(5, actions.size)
        compose.runOnIdle { assertTrue(actions.first().action()) }
        assertTrue(moves.single().first < 0)
        assertEquals(1, ends)
        compose.runOnIdle { assertTrue(actions.last().action()) }
        assertEquals(listOf("hide"), events)
    }
}
