// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PlayedBeforeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val track = TrackRef("song", "A long title that must remain readable", "Modland / Artist", fileName = "song.mod")
    private val heard = mutableStateOf(setOf("song"))
    private val cached = mutableStateOf(setOf("song"))
    private val titles = mutableStateOf(emptyMap<String, String>())

    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() {
        // Dispose selection's animated content before destroying the host activity.
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun show(screen: String, dark: Boolean = false) {
        activity.get().setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val state = PlayerUiState(queue = PlayQueue(listOf(track)), listenedIds = heard.value,
                    playback = if (screen == "random") com.przunk.protracktor.player.randomPlayback(track) else null,
                    randomPicks = listOf(track), randomIndex = 0, learnedTitles = titles.value)
                when (screen) {
                    "playlist" -> PlaylistScreen(
                        state = state, listState = rememberLazyListState(),
                        onPlayAt = {}, onRemoveAt = {}, onMove = { _, _ -> }, onShowNeighbours = {},
                        onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                        onBrowse = {}, onPickDownloads = {}, canBrowse = true, stateKnown = true,
                        contentPadding = PaddingValues(),
                    )
                    "random" -> RandomScreen(
                        state = state, browse = BrowseState(), listState = rememberLazyListState(),
                        onPlayAt = {}, onRemoveAt = {}, onFilter = {}, onShowNeighbours = {},
                        onShareFile = {}, onShareAudio = {}, onShareLink = {}, onSendToWeb = {},
                        onAddToOtherPlaylist = {}, onAddSelectedToPlaylist = {}, contentPadding = PaddingValues(),
                    )
                    else -> BrowseScreen(
                        browse = BrowseState(domain = BrowseDomain.HISTORY, tracks = listOf(track),
                            historyTotal = 1, listenedHere = heard.value, cachedHere = cached.value,
                            learnedTitles = titles.value),
                        playlistName = "Saved playlist", contentPadding = PaddingValues(),
                        onOpenDomain = {}, onPickFolder = {}, onPickFiles = {}, onOpenFolder = {},
                        onForgetFolder = {}, onScanFolder = {}, onIndexCatalogue = {},
                        onDownloadSongMetadata = {}, onDownloadReplayRoutines = {}, onOpenCatalogue = {},
                        onOpenGroup = {}, onRandom = {}, onChooseRandomScope = {}, onQueryChange = {},
                        onScope = {}, onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = {},
                        recentQueries = emptyList(), onSearchSuggestion = {},
                        onClearHistory = {}, onHistoryQueryChange = {}, playingId = null, loadingId = null,
                        onShowNeighbours = {}, onShareFile = {}, onShareAudio = {}, onShareLink = {},
                        onSendToWeb = {}, onPlay = {}, onAdd = {},
                    )
                }
            }
        }
    }

    @Test fun `playlist hides the mark even when its track qualifies`() {
        show("playlist")
        compose.onNodeWithText(track.title).assertExists()
        compose.onNodeWithContentDescription("Played before", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `random learns a title without moving the row and keeps the filename visible`() {
        show("random")
        val before = compose.onNodeWithText(track.title).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { titles.value = mapOf("song" to "Decoded title") }
        assertEquals(before.top, compose.onNodeWithText("Decoded title").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        compose.onNodeWithText("song.mod", substring = true).assertExists()
        compose.runOnIdle { titles.value = emptyMap() }
        compose.onNodeWithText(track.title).assertExists()
    }

    @Test fun `browse learns a title and shows its original filename in the source path`() {
        show("browse")
        compose.runOnIdle { titles.value = mapOf("song" to "Decoded title") }
        compose.onNodeWithText("Decoded title").assertExists()
        compose.onNodeWithText("Modland / Artist/song.mod").assertExists()
        compose.runOnIdle { titles.value = emptyMap() }
        compose.onNodeWithText(track.title).assertExists()
        compose.onNodeWithText(track.subtitle).assertExists()
    }

    @Test fun `random shows the mark even for a track that is also in the playlist`() {
        show("random", dark = true)
        compose.onNodeWithContentDescription("Played before", useUnmergedTree = true).assertExists()
        compose.runOnIdle { heard.value = emptySet() }
        compose.onNodeWithContentDescription("Played before", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `browse updates qualification without closing or changing the row title`() {
        heard.value = emptySet()
        show("browse")
        compose.onNodeWithContentDescription("Played before", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { heard.value = setOf("song") }
        compose.onNodeWithContentDescription("Played before", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(track.title).assertExists()
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `polish random exposes a localised status description`() {
        show("random")
        compose.onNodeWithContentDescription("Już odsłuchane", useUnmergedTree = true).assertExists()
    }

    @Test fun `random track details explain the mark and its history reset`() {
        show("random")
        compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_track_actions, track.title))
            .performClick()
        compose.onNodeWithText(activity.get().getString(R.string.action_info)).performClick()
        compose.onNodeWithText(activity.get().getString(R.string.played_before_explanation)).assertExists()
    }

    @Test fun `browse keeps the mark below the phone independently of cache and selection`() {
        show("browse")
        val mark = compose.onNodeWithContentDescription("Played before", useUnmergedTree = true)
        val bounds = mark.fetchSemanticsNode().boundsInRoot
        val phone = compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_cached_here),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithText(track.subtitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText(track.title).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= phone.bottom)
        assertEquals(phone.center.x, bounds.center.x, 0.5f)
        assertTrue(bounds.right < subtitle.left)
        compose.runOnIdle { cached.value = emptySet() }
        assertEquals(bounds, mark.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { heard.value = emptySet() }
        assertEquals(row, compose.onNodeWithText(track.title).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { heard.value = setOf("song") }
        compose.onNodeWithText(track.title).performTouchInput { longClick() }
        mark.assertDoesNotExist()
    }

    @Test fun `random keeps the mark below transport without moving the row or selection`() {
        show("random")
        val mark = compose.onNodeWithContentDescription("Played before", useUnmergedTree = true)
        val bounds = mark.fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_now_playing_row),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(track.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText(track.title).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= play.bottom)
        assertEquals(play.center.x, bounds.center.x, 0.5f)
        assertTrue(bounds.right < title.left)
        compose.runOnIdle { heard.value = emptySet() }
        assertEquals(play, compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_now_playing_row),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        assertEquals(row, compose.onNodeWithText(track.title).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { heard.value = setOf("song") }
        compose.onNodeWithText(track.title).performTouchInput { longClick() }
        mark.assertDoesNotExist()
    }
}
