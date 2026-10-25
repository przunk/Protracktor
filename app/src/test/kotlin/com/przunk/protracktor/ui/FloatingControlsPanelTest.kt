// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
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
    private val model = mutableStateOf(FloatingControlsState(true, true, false, true, true, heart))
    private val events = mutableListOf<String>()
    private val moves = mutableListOf<Pair<Int, Int>>()
    private var ends = 0

    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { activity.get().setContent { }; compose.waitForIdle(); activity.close() }

    private fun show(scale: Float = 1f) {
        activity.get().setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme {
                    FloatingControlsPanel(model.value,
                        { events += "previous" }, { events += "play" }, { events += "next" },
                        { events += "heart:${it.playlistId}:${it.trackId}" }, { events += "hide" },
                        { x, y -> moves += x to y }, { ends++ }, Modifier.width(320.dp))
                }
            }
        }
    }

    @Test fun `physical taps dispatch all actions without moving the panel`() {
        show()
        for (tag in listOf("previous", "play", "next", "heart")) {
            compose.onNodeWithTag("floating-$tag").assertHeightIsAtLeast(64.dp).assertWidthIsAtLeast(56.dp)
        }
        for (tag in listOf("previous", "play", "next", "heart", "hide")) {
            compose.onNodeWithTag("floating-$tag").performTouchInput { click() }
        }
        assertEquals(listOf("previous", "play", "next", "heart:7:song", "hide"), events)
        assertTrue(moves.isEmpty())
        assertEquals(0, ends)
    }

    @Test fun `only dragging the handle moves the panel and transport swipes do not click`() {
        show()
        compose.onNodeWithTag("floating-next").performTouchInput {
            swipe(center, center + Offset(0f, 90f))
        }
        assertTrue(events.isEmpty())
        assertTrue(moves.isEmpty())
        compose.onNodeWithTag("floating-drag").performTouchInput {
            swipe(center, center + Offset(70f, 60f))
        }
        assertTrue(moves.any { it.first != 0 || it.second != 0 })
        assertEquals(1, ends)
        assertTrue(events.isEmpty())
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
        for (text in listOf("Wstecz", "Pauza", "Dalej", "Zapisz", "Ukryj", "Przesuń")) {
            compose.onNodeWithText(text).assertIsDisplayed()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { get ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    assertTrue(get(results))
                    val layout = results.single()
                    assertEquals(text, 1, layout.lineCount)
                    assertFalse("$text: size=${layout.size}, paragraphWidth=${layout.multiParagraph.width}", layout.hasVisualOverflow)
                }
        }
        val actions = compose.onNodeWithTag("floating-drag").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertEquals(4, actions.size)
        compose.runOnIdle { assertTrue(actions.first().action()) }
        assertTrue(moves.single().first < 0)
        assertEquals(1, ends)
    }
}
