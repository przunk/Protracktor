// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scrollbar's bubble (2026-11-12): while the thumb is held it says where the list is -- the
 * position and how many, or a group's first letter -- and it goes when the thumb is let go. The
 * catalogue's group list has the scrollbar the tunes have.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class ScrollbarBubbleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val state = LazyListState()

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun list(rows: List<String>) {
        activity.get().setContent {
            MaterialTheme { Surface {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        items(rows) { Text(it, modifier = Modifier.testTag("row")) }
                    }
                    DraggableScrollbar(state, Modifier.align(Alignment.CenterEnd).testTag("scrollbar"),
                        bubble = positionBubble(activity.get()))
                }
            } }
        }
        compose.waitForIdle()
    }

    private fun bubbleText(): String = compose.onNodeWithTag("scrollbar-bubble", useUnmergedTree = true).onChild()
        .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()

    @Test fun `holding the thumb shows where the list is, and letting go hides it`() {
        list((1..2_000).map { "Tune $it" })
        compose.onNodeWithTag("scrollbar-bubble").assertDoesNotExist()
        compose.onNodeWithTag("scrollbar").performTouchInput {
            down(Offset(centerX, 8f))
            moveBy(Offset(0f, height / 2f))
        }
        compose.waitForIdle()
        // About half way: the bubble says the row at the top and how many there are.
        compose.onNodeWithTag("scrollbar-bubble").assertIsDisplayed()
        val said = bubbleText()
        assertTrue(said, Regex("""[\d,]+ / 2,000""").matches(said))
        assertTrue(state.firstVisibleItemIndex > 500)
        compose.onNodeWithTag("scrollbar").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("scrollbar-bubble").assertDoesNotExist()
    }

    @Test fun `a list that fits has no scrollbar`() {
        list((1..5).map { "Tune $it" })
        compose.onNodeWithTag("scrollbar").assertDoesNotExist()
    }

    @Test fun `a catalogue's authors have the scrollbar, with the first letter in its bubble`() {
        val authors = ('A'..'Z').flatMap { letter -> (1..40).map { CatalogueGroup("$letter author $it", 3) } }
        val browse = MutableStateFlow(BrowseState(domain = BrowseDomain.ONLINE,
            openCatalogue = CatalogueSummary("modland", "Modland", 3_000, 1L), openFormat = "Protracker",
            groups = authors))
        activity.get().setContent {
            val current by browse.collectAsState()
            MaterialTheme { Surface {
                BrowseScreen(
                    browse = current, playlistName = "Selected", contentPadding = PaddingValues(),
                    onOpenDomain = {}, onPickFolder = {}, onPickFiles = {}, onOpenFolder = {}, onForgetFolder = {},
                    onScanFolder = {}, onIndexCatalogue = {}, onDownloadSongMetadata = {},
                    onDownloadReplayRoutines = {}, onOpenCatalogue = {}, onOpenGroup = {},
                    onRandom = {}, onChooseRandomScope = {}, onQueryChange = {}, onScope = {},
                    onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = {},
                    recentQueries = emptyList(), onSearchSuggestion = {}, onClearHistory = {},
                    onHistoryQueryChange = {},
                    playingId = null, loadingId = null, onShowNeighbours = {}, onShareFile = {},
                    onShareAudio = {}, onShareLink = {}, onSendToWeb = {}, onPlay = {}, onAdd = {},
                )
            } }
        }
        compose.waitForIdle()
        val bar = compose.onNodeWithTag("browse-groups-scrollbar")
        bar.performTouchInput { down(Offset(centerX, 8f)); moveBy(Offset(0f, height * 0.6f)) }
        compose.waitForIdle()
        val letter = bubbleText()
        assertTrue("A single letter past the start, not $letter", Regex("[B-Z]").matches(letter))
        bar.performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("scrollbar-bubble").assertDoesNotExist()
    }
}
