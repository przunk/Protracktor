// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.player.BrowseState
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
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class ActionGroupAlignmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { activity.close() }

    private fun playlist(dirty: Boolean = false, width: Int = 360, scale: Float = 1f) {
        val clicks = mutableListOf<String>()
        activity.get().setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme {
                    Box(Modifier.width(width.dp)) {
                        PlaylistTopBar(
                            state = PlayerUiState(dirty = dirty,
                                queue = PlayQueue(listOf(TrackRef("id", "Tune", "Modland")))),
                            paired = false, onChoosePlaylist = {}, onBrowse = { clicks += "browse" },
                            onDiscard = { clicks += "discard" }, onSave = { clicks += "save" },
                            onSendToBrowser = { clicks += "web" }, onRescan = {}, onSettings = { clicks += "settings" },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        // Browse, WEB and Settings moved to the navigation bar and More (redesign E1): the row
        // holds the playlist's own actions only, and only while there is something to save.
        listOf(R.string.action_browse, R.string.action_send_to_browser, R.string.settings_title).forEach { id ->
            compose.onNode(hasClickAction() and hasText(activity.get().getString(id))).assertDoesNotExist()
        }
        val actions = buildList {
            if (dirty) { add(R.string.action_discard to "discard"); add(R.string.action_save to "save") }
        }
        val bounds = actions.map { (id, _) ->
            compose.onNode(hasClickAction() and hasText(activity.get().getString(id)))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        val density = activity.get().resources.displayMetrics.density
        bounds.forEach {
            assertEquals("Related actions have equal widths", bounds.first().width, it.width, 1f)
            assertEquals(bounds.first().height, it.height, 1f)
            assertTrue("Touch target remains at least 48dp", it.width / density >= 48f)
            assertTrue("Action stays inside the viewport", it.left >= 0 && it.right <= width * density + 1f)
        }
        bounds.zipWithNext().forEach { (a, b) -> assertTrue("Actions do not overlap", a.right <= b.left) }
        actions.forEach { (id, expected) ->
            compose.onNode(hasClickAction() and hasText(activity.get().getString(id))).performTouchInput { click() }
            assertEquals(expected, clicks.last())
        }
    }

    // One composition per test: setting content twice on one activity never settles in Polish.
    @Test fun `English playlist row has no way elsewhere`() = playlist()
    @Test fun `English Save and Discard match`() = playlist(dirty = true)
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish playlist row has no way elsewhere`() = playlist()
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Save and Discard match`() = playlist(dirty = true)
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Save and Discard fit a narrow viewport with large text`() = playlist(dirty = true, width = 320, scale = 2f)

    private fun random(scale: Float = 1f) {
        activity.get().setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        PlaylistReturnAction(onClick = {})
                        RandomScreen(
                            state = PlayerUiState(), browse = BrowseState(), listState = rememberLazyListState(),
                            onPlayAt = {}, onRemoveAt = {}, onFilter = {}, onShowNeighbours = {},
                            onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                            onAddToOtherPlaylist = {}, onAddSelectedToPlaylist = {}, contentPadding = PaddingValues(),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val bounds = listOf(R.string.action_to_playlist, R.string.random_filter).map {
            compose.onNode(hasClickAction() and hasText(activity.get().getString(it)))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals("Return and Filter share a column width across layouts", bounds[0].width, bounds[1].width, 1f)
        assertEquals(bounds[0].height, bounds[1].height, 1f)
    }

    @Test fun `English Random and return actions share a width`() = random()
    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Random and return actions share a width at large text`() = random(scale = 2f)
}
