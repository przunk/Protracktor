// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class QueueSwipeRowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    private var added = 0
    private var removed = 0
    private var played = 0
    private var touchSlop = 0f
    @After fun stop() { activity.close() }
    private fun show(enabled: Boolean = true, remove: Boolean = true) {
        compose.mainClock.autoAdvance = false
        activity.get().setContent { MaterialTheme {
            touchSlop = LocalViewConfiguration.current.touchSlop
            QueueSwipeRow({ added++ }, if (remove) ({ removed++ }) else null, enabled, Modifier.testTag("gesture-row")) {
                ListItem(headlineContent = { Text("Tune") }, modifier = Modifier.clickable { played++ })
            }
        } }
        measure()
    }

    private fun scrollable(first: Int = 0): LazyListState {
        compose.mainClock.autoAdvance = false
        lateinit var state: LazyListState
        activity.get().setContent { MaterialTheme {
            touchSlop = LocalViewConfiguration.current.touchSlop
            state = rememberLazyListState(initialFirstVisibleItemIndex = first)
            LazyColumn(state = state) {
                items((0..40).toList(), key = { it }) { index ->
                    QueueSwipeRow({ added++ }, { removed++ }, modifier = Modifier.testTag("scroll-row-$index")) {
                        ListItem(headlineContent = { Text("Tune $index") }, modifier = Modifier.clickable { played++ })
                    }
                }
            }
        } }
        measure()
        return state
    }

    private fun measure() {
        val view = activity.get().window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 720, 1600)
        compose.waitForIdle()
    }

    @Test fun `small horizontal movement does not reveal or move a row`() {
        show()
        val left = compose.onNodeWithText("Tune").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("gesture-row").performTouchInput {
            down(center); moveBy(Offset(touchSlop * 1.25f, 0f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-add-background").assertDoesNotExist()
        assertEquals(left, compose.onNodeWithText("Tune").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
        compose.onNodeWithTag("gesture-row").performTouchInput {
            down(center); moveBy(Offset(touchSlop * 1.25f, 0f)); moveBy(Offset(-touchSlop * 1.25f, 0f)); up()
        }
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
    }

    @Test fun `unsupported left swipe does not play and a subsequent physical tap still does`() {
        show(remove = false)
        compose.onNodeWithTag("gesture-row").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
        compose.onNodeWithTag("gesture-row").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, played) }
    }

    @Test fun `a right wobble before scrolling upward does not steal the list gesture`() {
        val state = scrollable()
        compose.onNodeWithTag("scroll-row-8").performTouchInput {
            down(center)
            moveBy(Offset(touchSlop * 1.25f, -2f))
            repeat(8) { moveBy(Offset(3f, -30f)) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-add-background").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) }
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
    }

    @Test fun `a left wobble before scrolling downward does not reveal removal`() {
        val state = scrollable(first = 12)
        compose.onNodeWithTag("scroll-row-20").performTouchInput {
            down(center)
            moveBy(Offset(-touchSlop * 1.25f, 2f))
            repeat(8) { moveBy(Offset(-3f, 30f)) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-remove-background").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex < 12) }
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
    }

    @Test fun `a diagonal gesture favours scrolling rather than a row action`() {
        val state = scrollable()
        compose.onNodeWithTag("scroll-row-8").performTouchInput {
            down(center); moveBy(Offset(200f, -160f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-add-background").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) }
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
    }

    @Test fun `vertical intent stays vertical even after a large horizontal drift`() {
        val state = scrollable()
        compose.onNodeWithTag("scroll-row-8").performTouchInput {
            down(center); moveBy(Offset(2f, -touchSlop * 2f)); moveBy(Offset(280f, -80f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-add-background").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) }
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed); assertEquals(0, played) }
    }

    @Test fun `deliberate horizontal movement tolerates a little vertical drift`() {
        show()
        val row = compose.onNodeWithTag("gesture-row")
        val travel = row.fetchSemanticsNode().boundsInRoot.width * 0.45f
        row.performTouchInput { down(center); moveBy(Offset(travel / 3, 5f)); moveBy(Offset(travel * 2 / 3, 20f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-add-background").assertExists()
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, added); assertEquals(0, removed); assertEquals(0, played) }
    }

    @Test fun `cancellation and a second finger cannot commit a swipe`() {
        show()
        val row = compose.onNodeWithTag("gesture-row")
        val travel = row.fetchSemanticsNode().boundsInRoot.width * 0.45f
        row.performTouchInput { down(center); moveBy(Offset(travel, 0f)); cancel() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed) }
        row.performTouchInput {
            down(0, center); moveBy(0, Offset(travel, 0f)); down(1, centerLeft); up(0); up(1)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed) }
    }

    @Test fun `right swipe adds once per release and retains the original for duplicate additions`() {
        show()
        val row = compose.onNodeWithTag("gesture-row")
        row.performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, added); assertEquals(0, removed); assertEquals(0, played) }
        compose.onNodeWithText("Tune").assertIsDisplayed()
        row.performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(2, added) }
    }

    @Test fun `direction reveals named action before release and only left commits removal`() {
        show()
        val row = compose.onNodeWithTag("gesture-row")
        // Deliberate swipes include the larger activation dead zone as well as the commit distance.
        val travel = row.fetchSemanticsNode().boundsInRoot.width * 0.45f
        row.performTouchInput { down(center); moveTo(center - Offset(travel, 0f), 300) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-remove-background").assertExists()
        compose.onNodeWithText("Remove").assertExists()
        compose.runOnIdle { assertEquals(0, removed) }
        row.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, removed); assertEquals(0, added) }
        row.performTouchInput { down(center); moveTo(center + Offset(travel, 0f), 300) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-add-background").assertExists()
        compose.onNodeWithText("Add to queue").assertExists()
        row.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, added) }
    }

    @Test fun `short reversed and vertical drags do not commit and tap still plays`() {
        show()
        val row = compose.onNodeWithTag("gesture-row")
        row.performTouchInput { down(center); moveTo(center + Offset(40f, 0f), 300); up() }
        compose.mainClock.advanceTimeBy(200)
        row.performTouchInput { down(center); moveTo(center + Offset(280f, 0f), 300); moveTo(center, 300); up() }
        compose.mainClock.advanceTimeBy(200)
        row.performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed) }
        val beforeTap = played
        compose.onNodeWithText("Tune").performClick()
        compose.runOnIdle { assertEquals(beforeTap + 1, played) }
    }

    @Test fun `accessible actions are equivalent and disabled selection row has no gesture action`() {
        show()
        val actions = compose.onNodeWithTag("gesture-row").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertEquals(listOf("Add to queue", "Remove"), actions.map { it.label })
            actions.forEach { assertTrue(it.action()) }
        }
        compose.runOnIdle { assertEquals(1, added); assertEquals(1, removed) }
    }

    @Test fun `selection disables row swipes`() {
        show(enabled = false)
        compose.onNodeWithTag("gesture-row").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(0, added); assertEquals(0, removed) }
    }
}
