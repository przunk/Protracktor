// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
@Config(qualifiers = "en-w420dp-h1000dp-xhdpi")
class BrowseSelectionUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val tracks = List(3) { TrackRef("https://modland.com/pub/modules/Protracker/Selection/$it.mod", "Selection $it",
        subtitle = "Modland/Protracker/Selection", fileName = "$it.mod") }
    private val state = MutableStateFlow(BrowseState(domain = BrowseDomain.HISTORY, tracks = tracks, historyTotal = 3, searched = true))
    private var added: List<TrackRef>? = null
    private var other: List<TrackRef>? = null
    private var shared: List<TrackRef>? = null
    private var played = 0
    private var backs = 0

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            val browse by state.collectAsState()
            MaterialTheme {
                BackHandler { backs++ }
                BrowseScreen(browse = browse, playlistName = "Selected", contentPadding = PaddingValues(),
                    onOpenDomain = {}, onPickFolder = {}, onPickFiles = {}, onOpenFolder = {},
                    onForgetFolder = {}, onScanFolder = {}, onIndexCatalogue = {}, onDownloadSongMetadata = {},
                    onDownloadReplayRoutines = {}, onOpenCatalogue = {}, onOpenGroup = {}, onRandom = {},
                    onChooseRandomScope = {}, onQueryChange = {}, onScope = {}, onToggleCatalogue = {},
                    onTogglePlatform = {}, onSearch = {}, recentQueries = emptyList(), onSearchSuggestion = {},
                    onClearHistory = {}, onHistoryQueryChange = {}, playingId = tracks[1].id, loadingId = null,
                    onShowNeighbours = {}, onShareFile = {}, onShareAudio = {}, onShareLink = {},
                    onSendToWeb = { shared = it }, onPlay = { played++ }, onAdd = { added = it },
                    onAddToOtherPlaylist = { other = it })
            }
        }
        compose.waitForIdle()
    }
    @After fun stop() { activity.get().setContent {}; compose.waitForIdle(); activity.close() }
    private fun hold(index: Int) { compose.onNodeWithText(tracks[index].title).performTouchInput { longClick() }; compose.waitForIdle() }
    private fun count() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size
    private fun selected(n: Int) = activity.get().resources.getQuantityString(R.plurals.browse_selected, n, n)

    @Test fun `long press and tap select without playing then Add uses source order and clears`() {
        val before = compose.onNodeWithTag("enqueue-row-0").fetchSemanticsNode().boundsInRoot
        hold(2)
        compose.onNodeWithText(tracks[0].title).performClick()
        compose.onNodeWithText(selected(2)).assertIsDisplayed()
        val after = compose.onNodeWithTag("enqueue-row-0").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 0.1f); assertEquals(before.height, after.height, 0.1f)
        assertEquals(3, count())
        val label = activity.get().resources.getQuantityString(R.plurals.browse_add_selected, 2, 2) + " → Selected"
        compose.onNodeWithText(label).performClick(); compose.waitForIdle()
        assertEquals(listOf(tracks[0], tracks[2]), added); assertEquals(0, played); assertEquals(0, count())
    }
    @Test fun `Select all and none and first Back only leave selection`() {
        hold(1)
        compose.onNodeWithText("Select all").performClick(); compose.onNodeWithText(selected(3)).assertIsDisplayed()
        compose.onNodeWithText("Select none").performClick(); compose.waitForIdle(); assertEquals(0, count())
        hold(0)
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle(); assertEquals(0, count()); assertEquals(0, backs); assertEquals(0, played)
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle(); assertEquals(1, backs)
    }
    @Test fun `other Add and Send capture source order while membership and query retire stale selections`() {
        hold(2); compose.onNodeWithText(tracks[0].title).performClick()
        compose.onNodeWithContentDescription(activity.get().getString(R.string.action_add_to_playlist)).performClick()
        compose.waitForIdle(); assertEquals(listOf(tracks[0], tracks[2]), other); assertEquals(0, count())
        hold(0); compose.onNodeWithText(tracks[2].title).performClick()
        compose.runOnIdle { state.update { it.copy(tracks = tracks.take(2), historyTotal = 2) } }
        compose.waitForIdle(); compose.onNodeWithText(selected(1)).assertIsDisplayed()
        compose.onNodeWithContentDescription(activity.get().getString(R.string.action_send_to_web)).performClick()
        compose.waitForIdle(); assertEquals(listOf(tracks[0]), shared); assertEquals(0, count())
        hold(0)
        compose.runOnIdle { state.update { it.copy(query = "another producing query") } }
        compose.waitForIdle(); assertEquals(0, count()); assertEquals(0, played)
        assertTrue(added == null)
    }
}
