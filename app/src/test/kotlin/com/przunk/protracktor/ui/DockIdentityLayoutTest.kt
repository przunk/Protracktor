// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.ActiveQueue
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.QueueSource
import com.przunk.protracktor.player.TrackRef
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
class DockIdentityLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val track = TrackRef("song", "Battle of honour", fileName = "battle.it", author = "Treks")
    private val order = PlayQueue(listOf(track)).startAt(0)
    private val state = mutableStateOf(PlayerUiState(
        playback = ActiveQueue(QueueSource.Random(1L), order),
        subsongCount = 5, metadata = mapOf("year" to "1998"),
    ))
    private var kept = 0
    private var expanded = 0
    private var queued = 0
    private val keepLabel get() = activity.get().getString(R.string.a11y_keep_track)
    private val expandLabel get() = activity.get().getString(R.string.a11y_expand_player)
    private fun countLabel() = activity.get().getString(
        R.string.subsongs_position, state.value.subsong + 1, state.value.subsongCount)

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        Settings.Global.putFloat(activity.get().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun show(fontScale: Float = 1f) {
        activity.get().setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    PlayerDock(state.value, onSeek = {}, onKeep = { kept++ }, onExpand = { expanded++ },
                        onBrowse = {}, onPlayPause = {}, onPrevious = {}, onPreviousFile = {},
                        onNext = {}, onNextFile = {}, onShuffle = {}, onRepeat = {}, onQueue = { queued++ })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun textBounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    private fun keepBounds() = compose.onNodeWithContentDescription(keepLabel)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    private fun cardBounds() = compose.onNodeWithContentDescription(expandLabel)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    private fun queueBounds() = compose.onNodeWithTag("queue-toggle")
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    private fun assertTwoRows() {
        val title = textBounds(track.title)
        val subtitle = textBounds("Treks · PC · 1998")
        val keep = keepBounds()
        val count = textBounds(countLabel())
        assertTrue("Title must have its own upper row", title.bottom <= subtitle.top)
        assertTrue("Keep must be above the counter, without a shared hit area", keep.bottom <= count.top)
        assertTrue("Counter must be below the title", title.bottom <= count.top)
        assertTrue("Metadata and counter must not overlap",
            subtitle.right <= count.left || subtitle.bottom <= count.top)
        assertEquals("Right-hand content shares an end alignment", keep.right, count.right, 0.5f)
        val density = activity.get().resources.displayMetrics.density
        assertTrue("Keep target is at least 48dp wide", keep.width >= 48f * density)
        assertTrue("Compact Keep target is at least 32dp high", keep.height >= 32f * density)
        assertTrue("Queue stays outside the card", queueBounds().left >= cardBounds().right)
    }

    @Test fun `normal dock retains original card height and balanced identity margins`() {
        show()
        val card = cardBounds()
        val title = textBounds(track.title)
        val subtitle = textBounds("Treks · PC · 1998")
        val density = activity.get().resources.displayMetrics.density
        assertEquals("Original identity-card height", 60f * density, card.height, 0.5f)
        assertEquals("Original title top inset", 8f * density, title.top - card.top, 0.5f)
        assertEquals("Original metadata bottom inset", 8f * density, card.bottom - subtitle.bottom, 0.5f)
        assertEquals("Original consecutive text lines", title.bottom, subtitle.top, 0.5f)
    }

    @Test fun `tapping just below keep opens the player rather than saving through an expanded target`() {
        show()
        val card = cardBounds()
        val keep = keepBounds()
        val density = activity.get().resources.displayMetrics.density
        compose.onNodeWithContentDescription(expandLabel).performTouchInput {
            click(Offset(keep.center.x - card.left, keep.bottom - card.top + density))
        }
        assertEquals("A footer tap must never save the track", 0, kept)
        assertEquals(1, expanded)
    }

    @Test fun `subsong count changes neither title width nor keep and Queue positions`() {
        show()
        val title = textBounds(track.title)
        val keep = keepBounds()
        val card = cardBounds()
        val queue = queueBounds()
        listOf(1, 5, 256).forEach { count ->
            compose.runOnIdle { state.value = state.value.copy(subsongCount = count, subsong = count - 1) }
            assertEquals(title, textBounds(track.title))
            assertEquals(keep, keepBounds())
            assertEquals(card, cardBounds())
            assertEquals(queue, queueBounds())
            if (count > 1) assertTwoRows()
        }
    }

    @Test fun `title and metadata sit left with keep above the complete tune label on the right`() {
        show()
        assertTwoRows()
    }

    @Test fun `hiding keep for the active playlist retains title width and dock geometry`() {
        show()
        val title = textBounds(track.title)
        val card = cardBounds()
        val queue = queueBounds()
        compose.runOnIdle { state.value = state.value.copy(queue = order, playback = null) }
        compose.onNodeWithContentDescription(keepLabel).assertDoesNotExist()
        assertEquals(title, textBounds(track.title))
        assertEquals(card, cardBounds())
        assertEquals(queue, queueBounds())
    }

    @Test
    @Config(qualifiers = "pl-w320dp-h800dp-xhdpi")
    fun `narrow Polish dock grows for double size text without overlapping touch and text regions`() {
        compose.runOnIdle { state.value = state.value.copy(subsong = 255, subsongCount = 256) }
        show(fontScale = 2f)
        assertTwoRows()
        val card = cardBounds()
        listOf(textBounds(track.title), textBounds("Treks · PC · 1998"), keepBounds(), textBounds(countLabel()))
            .forEach { bounds ->
                assertTrue("All identity content stays within the card: $bounds in $card", contains(card, bounds))
            }
    }

    @Test fun `physical taps keep expand and open Queue as separate actions`() {
        show()
        compose.onNodeWithContentDescription(keepLabel).performTouchInput { click(center) }
        assertEquals(1, kept)
        assertEquals(0, expanded)
        compose.onNodeWithContentDescription(expandLabel).performTouchInput { click(Offset(24f, 24f)) }
        assertEquals(1, expanded)
        compose.onNodeWithText(countLabel(), useUnmergedTree = true).performTouchInput { click(center) }
        assertEquals(2, expanded)
        assertEquals(1, kept)
        compose.onNodeWithTag("queue-toggle").performTouchInput { click(center) }
        assertEquals(1, queued)
        assertEquals(2, expanded)
    }

    private fun contains(outer: Rect, inner: Rect) = inner.left >= outer.left && inner.top >= outer.top &&
        inner.right <= outer.right && inner.bottom <= outer.bottom
}
