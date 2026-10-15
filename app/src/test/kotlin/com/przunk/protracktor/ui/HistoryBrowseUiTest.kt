// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.ModArchive
import com.przunk.protracktor.player.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
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
class HistoryBrowseUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var workflow: BrowseWorkflow
    private val browse = MutableStateFlow(BrowseState())
    private val playing = MutableStateFlow<String?>("history-0")

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun rows(page: Int) = (0 until 100).map { index ->
        val number = page * 100 + index
        TrackRef("history-$number", "History track $number", fileName = "$number.mod")
    }

    private fun show() {
        val source = object : LibrarySearch.Sources {
            override suspend fun searchLocal(query: String, limit: Int, platforms: Set<String>) = emptyList<TrackRef>()
            override suspend fun playlistTracks() = emptyList<TrackRef>()
            override suspend fun searchCatalogues(query: String, ids: Set<String>, limit: Int, platforms: Set<String>) = emptyList<TrackRef>()
            override suspend fun searchLive(query: String) = ModArchive.Outcome.Found(emptyList())
            override suspend fun countLocal(query: String) = 0
            override suspend fun countCatalogues(query: String, ids: Set<String>, platforms: Set<String>) = 0
        }
        val actions = object : BrowseWorkflow.Actions {
            override fun refreshFolders() {}
            override fun refreshCatalogues() {}
            override fun refreshPlatformCounts() {}
            override fun refreshFavouriteCount() {}
            override fun openHistory() {
                // Complete within the same UI turn: no loading frame may be required for reset.
                browse.update { it.copy(loading = true, historyPage = 0, tracks = emptyList()) }
                browse.update { it.copy(loading = false, historyTotal = 200, tracks = rows(0)) }
            }
            override fun openSavedPlaces() {}
            override fun openFormat(format: String) {}
            override fun openCatalogue(summary: CatalogueSummary) {}
            override fun localLevel(state: BrowseState, at: String) = state
        }
        workflow = BrowseWorkflow(activity.get().lifecycleScope, browse, LibrarySearch(source), actions,
            onSearchFailed = { throw AssertionError("Unexpected search failure", it) }, onLiveResult = {})
        activity.get().setContent {
            val current by browse.collectAsState()
            val playingId by playing.collectAsState()
            MaterialTheme {
                BrowseScreen(browse = current, playlistName = "Selected", contentPadding = PaddingValues(),
                    onOpenDomain = workflow::openDomain, onPickFolder = {}, onPickFiles = {},
                    onOpenFolder = {}, onForgetFolder = {}, onScanFolder = {}, onIndexCatalogue = {},
                    onDownloadSongMetadata = {}, onDownloadReplayRoutines = {}, onOpenCatalogue = {},
                    onOpenGroup = {}, onRandom = {}, onChooseRandomScope = {},
                    onQueryChange = workflow::setQuery, onScope = workflow::setSearchScope,
                    onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = workflow::runSearch,
                    recentQueries = emptyList(), onSearchSuggestion = workflow::searchFor,
                    onClearHistory = {}, onHistoryPage = { page ->
                        browse.update { it.copy(historyPage = page, tracks = rows(page)) }
                    }, playingId = playingId, loadingId = null,
                    onShowNeighbours = {}, onShareFile = {}, onShareAudio = {}, onShareLink = {},
                    onSendToWeb = {}, onPlay = {}, onAdd = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("History track 0").assertIsDisplayed()
    }

    private fun scrollTo(index: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index % 100)
        compose.onNodeWithText("History track $index").assertIsDisplayed()
    }

    @Test fun `returning through Browse root opens History at the top`() {
        show()
        scrollTo(50)
        compose.runOnIdle { workflow.browseBack() }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("History track 0").assertIsDisplayed()
    }

    @Test fun `reopening History without an intermediate frame resets its position`() {
        show()
        scrollTo(50)
        compose.runOnIdle { workflow.openDomain(BrowseDomain.HISTORY) }
        compose.onNodeWithText("History track 0").assertIsDisplayed()
    }

    @Test fun `a simultaneous playing change cannot override a new visit starting at the top`() {
        show()
        scrollTo(50)
        compose.runOnIdle {
            playing.value = "history-80"
            workflow.openDomain(BrowseDomain.HISTORY)
        }
        compose.onNodeWithText("History track 0").assertIsDisplayed()
    }

    @Test fun `paging keeps positions until a new History visit`() {
        show()
        scrollTo(50)
        compose.onNodeWithText("Older").performClick()
        compose.onNodeWithText("History track 100").assertIsDisplayed()
        scrollTo(150)
        compose.onNodeWithText("Newer").performClick()
        compose.onNodeWithText("History track 50").assertIsDisplayed()
        compose.runOnIdle { workflow.browseBack() }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("History track 0").assertIsDisplayed()
        compose.onNodeWithText("Older").performClick()
        compose.onNodeWithText("History track 100").assertIsDisplayed()
    }
}
