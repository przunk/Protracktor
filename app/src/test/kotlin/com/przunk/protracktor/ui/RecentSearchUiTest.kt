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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.net.ModArchive
import com.przunk.protracktor.player.*
import kotlinx.coroutines.flow.MutableStateFlow
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
class RecentSearchUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val browse = MutableStateFlow(BrowseState(domain = BrowseDomain.SEARCH, searchScope = SearchScope.Local))
    private val requests = mutableListOf<String>()
    private var persisted = emptyList<String>()
    private lateinit var history: RecentSearchHistory

    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { activity.get().setContent { }; compose.waitForIdle(); activity.close() }

    private fun show(seed: List<String>) {
        persisted = seed
        history = RecentSearchHistory(object : RecentSearchHistory.Store {
            override fun read() = persisted
            override fun write(queries: List<String>) { persisted = queries }
        })
        val source = object : LibrarySearch.Sources {
            override suspend fun searchLocal(query: String, limit: Int, platforms: Set<String>): List<TrackRef> {
                requests += query
                return emptyList()
            }
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
            override fun openHistory() {}
            override fun openSavedPlaces() {}
            override fun openFormat(format: String) {}
            override fun openCatalogue(summary: CatalogueSummary) {}
            override fun localLevel(state: BrowseState, at: String) = state
        }
        val workflow = BrowseWorkflow(activity.get().lifecycleScope, browse, LibrarySearch(source), actions,
            onSubmitted = history::record,
            onSearchFailed = { throw AssertionError("Unexpected search failure", it) }, onLiveResult = {})
        activity.get().setContent {
            val current by browse.collectAsState()
            val recent by history.queries.collectAsState()
            MaterialTheme {
                BrowseScreen(browse = current, playlistName = "Selected", contentPadding = PaddingValues(),
                    onOpenDomain = workflow::openDomain, onPickFolder = {}, onPickFiles = {},
                    onOpenFolder = {}, onForgetFolder = {}, onScanFolder = {}, onIndexCatalogue = {},
                    onDownloadSongMetadata = {}, onDownloadReplayRoutines = {}, onOpenCatalogue = {},
                    onOpenGroup = {}, onRandom = {}, onChooseRandomScope = {},
                    onQueryChange = workflow::setQuery, onScope = workflow::setSearchScope,
                    onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = workflow::runSearch,
                    recentQueries = recent, onSearchSuggestion = workflow::searchFor,
                    onClearHistory = {}, onHistoryQueryChange = {}, playingId = null, loadingId = null,
                    onShowNeighbours = {}, onShareFile = {}, onShareAudio = {}, onShareLink = {},
                    onSendToWeb = {}, onPlay = {}, onAdd = {})
            }
        }
        compose.waitForIdle()
    }

    @Test fun `actual Browse field filters the entire history and a suggestion submits once in current scope`() {
        show(listOf("alpha", "beta", "gamma", "delta one", "delta two", "delta three", "delta four"))
        compose.onNodeWithText("alpha").assertIsDisplayed()
        compose.onNodeWithText("beta").assertIsDisplayed()
        compose.onNodeWithText("gamma").assertIsDisplayed()
        compose.onNodeWithText("delta one").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("dEl")
        compose.onNodeWithText("delta one").assertIsDisplayed()
        compose.onNodeWithText("delta two").assertIsDisplayed()
        compose.onNodeWithText("delta three").assertIsDisplayed()
        compose.onNodeWithText("delta four").assertDoesNotExist()
        compose.onNodeWithText("alpha").assertDoesNotExist()
        assertTrue(requests.isEmpty())
        compose.onNodeWithText("delta two").performClick()
        compose.waitForIdle()
        assertEquals(listOf("delta two"), requests)
        assertEquals("delta two", browse.value.query)
        assertEquals(SearchScope.Local, browse.value.searchScope)
        assertEquals("delta two", persisted.first())
        compose.onNode(isPopup()).assertDoesNotExist()
    }

    @Test fun `IME Search records and submits once while blank Search remains a valid scope query`() {
        show(emptyList())
        compose.onNode(hasSetTextAction()).performTextReplacement("typed query")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("typed query"), requests)
        assertEquals(listOf("typed query"), persisted)
        compose.onNode(hasSetTextAction()).performClick().performTextReplacement("")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("typed query", ""), requests)
        assertEquals(listOf("typed query"), persisted)
    }

    @Test fun `refocusing after a submitted suggestion restores suggestions without another search`() {
        show(listOf("one", "two", "three", "four"))
        compose.onNodeWithText("two").performClick()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performClick().performTextReplacement("")
        compose.onNode(isPopup()).assertIsDisplayed()
        compose.onNodeWithText("two").assertIsDisplayed()
        compose.onNodeWithText("one").assertIsDisplayed()
        compose.onNodeWithText("three").assertIsDisplayed()
        compose.onNodeWithText("four").assertDoesNotExist()
        assertEquals(listOf("two"), requests)
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `polish text suggestions keep the query and immediate submission`() {
        show(listOf("Żółw", "żaba"))
        compose.onNode(isPopup()).assertIsDisplayed()
        compose.onNodeWithText("Ostatnie wyszukiwania").assertDoesNotExist()
        compose.onNodeWithText("Żółw").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Żółw"), requests)
        assertEquals("Żółw", browse.value.query)
    }

    @Test fun `suggestions use an anchored popup without a heading or a visible Search label`() {
        show(listOf("one", "two", "three"))
        compose.onNode(isPopup()).assertIsDisplayed()
        compose.onNodeWithText("Recent searches").assertDoesNotExist()
        compose.onNodeWithText("Search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        assertTrue(requests.isEmpty())
    }

    @Test fun `integrated magnifier submits once and dismisses the dropdown`() {
        show(listOf("typed", "older"))
        compose.onNode(hasSetTextAction()).performTextReplacement("typed")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitForIdle()
        assertEquals(listOf("typed"), requests)
        assertEquals("typed", persisted.first())
        compose.onNode(isPopup()).assertDoesNotExist()
    }
}
