// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class SubsongStripFollowUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private data class Strip(val count: Int = 256, val current: Int = 68, val key: String = "first")
    private val state = mutableStateOf(Strip())
    private var selected = -1

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            MaterialTheme {
                val strip = state.value
                SubsongStrip(strip.count, strip.current, false, onSelect = { selected = it }, onTogglePlayAll = {},
                    trackKey = strip.key)
            }
        }
        compose.waitForIdle()
    }
    @After fun stop() { activity.get().setContent {}; compose.waitForIdle(); activity.close() }
    private fun chip(n: Int) = compose.onNodeWithText(n.toString(), useUnmergedTree = true)
    private fun bounds(n: Int): Rect = chip(n).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    private fun update(next: Strip) { compose.runOnIdle { state.value = next }; compose.waitForIdle() }

    @Test fun `initial arbitrary current is visible and advancing inside its region does not scroll`() {
        val before = bounds(69)
        update(state.value.copy(current = 69))
        bounds(70)
        assertEquals(before.left, bounds(69).left, 0.5f)
    }

    @Test fun `advancing while a distant region is browsed leaves that region in place`() {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(248)
        compose.waitForIdle()
        val before = bounds(256)
        update(state.value.copy(current = 69))
        assertEquals(before, bounds(256))
        val viewport = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("70"), useUnmergedTree = true)
            .fetchSemanticsNodes().none { it.boundsInRoot.overlaps(viewport) })
    }

    @Test fun `an advance past the watched viewport reveals the actual new current`() {
        val before = bounds(69)
        update(state.value.copy(current = 77))
        assertEquals(before.left, bounds(78).left, 0.5f)
    }

    @Test fun `a different file or count opens at its own current even after distant browsing`() {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(248)
        compose.waitForIdle(); bounds(256)
        update(state.value.copy(current = 5, key = "second")); bounds(6)
        update(state.value.copy(count = 5, current = 0)); bounds(1)
    }

    @Test fun `actual chip selection works with play all off and does not select another chip`() {
        chip(70).performClick(); compose.waitForIdle()
        assertEquals(69, selected)
        assertEquals(68, state.value.current)
        bounds(69)
    }
}
