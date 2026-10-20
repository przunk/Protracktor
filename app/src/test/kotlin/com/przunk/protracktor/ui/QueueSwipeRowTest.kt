// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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
    @After fun stop() { activity.close() }
    private fun show(enabled: Boolean = true) {
        compose.mainClock.autoAdvance = false
        activity.get().setContent { MaterialTheme {
            QueueSwipeRow({ added++ }, { removed++ }, enabled, Modifier.testTag("gesture-row")) {
                ListItem(headlineContent = { Text("Tune") }, modifier = Modifier.clickable { played++ })
            }
        } }
        val view = activity.get().window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 720, 1600)
        compose.waitForIdle()
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
        row.performTouchInput { down(center); moveTo(center - Offset(280f, 0f), 300) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("swipe-remove-background").assertExists()
        compose.onNodeWithText("Remove").assertExists()
        compose.runOnIdle { assertEquals(0, removed) }
        row.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, removed); assertEquals(0, added) }
        row.performTouchInput { down(center); moveTo(center + Offset(280f, 0f), 300) }
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
