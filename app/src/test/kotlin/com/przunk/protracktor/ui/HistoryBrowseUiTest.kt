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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.R
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
import org.robolectric.Shadows
import android.os.Looper
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
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
    private lateinit var historyBrowser: HistoryBrowser
    private var stored = rows(0) + rows(1)
    private var readGate: CompletableDeferred<Unit>? = null
    private var readFails = false
    private val failures = mutableListOf<Throwable>()
    private var playedRows: List<TrackRef>? = null
    private var playCalls = 0
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
        TrackRef("history-$number", "History track $number",
            fileName = if (number == 199) "special_%!.mod" else "$number.mod",
            author = if (number == 150) "Hülsbeck" else "")
    }

    private fun show() {
        historyBrowser = HistoryBrowser(activity.get().lifecycleScope, browse,
            read = { query, page ->
                readGate?.await()
                if (readFails) error("controlled History read failure")
                HistoryFilter.page(stored.asSequence().filter {
                    HistoryFilter.matches(query, it.title, it.author, it.fileName)
                }, page)
            }, clear = { stored = emptyList() }, onFailure = { failures += it })
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
            override fun openHistory() = historyBrowser.open()
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
                    onClearHistory = { historyBrowser.clearAll({ stored = emptyList() }, { failures += it }) },
                    onHistoryPage = historyBrowser::showPage, onHistoryQueryChange = historyBrowser::setQuery,
                    playingId = playingId, loadingId = null,
                    onShowNeighbours = {}, onShareFile = {}, onShareAudio = {}, onShareLink = {},
                    onSendToWeb = {}, onPlay = { playedRows = browse.value.tracks; playCalls++ }, onAdd = {})
            }
        }
        compose.waitForIdle()
        openHistory()
        compose.onNodeWithText("History track 0").assertIsDisplayed()
    }

    private fun scrollTo(index: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index % 100)
        compose.onNodeWithText("History track $index").assertIsDisplayed()
    }

    private fun openHistory() {
        compose.onNodeWithText(activity.get().getString(R.string.domain_history_title)).performClick()
    }

    private fun advanceFilter() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        compose.waitForIdle()
    }

    private fun filter(query: String) {
        compose.onNode(hasSetTextAction()).performClick().performTextReplacement(query)
        advanceFilter()
        compose.waitUntil(5_000) { !browse.value.loading }
        compose.waitForIdle()
    }

    @Test fun `live phrase filtering searches old title author and literal filename while keeping focus`() {
        show()
        filter("TRACK 150")
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        filter("HÜLSBECK")
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        filter("SPECIAL_%!")
        compose.onNodeWithText("History track 199").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, browse.value.historyTotal) }
    }

    @Test fun `filtered paging counts all matches and clearing resets to the full newest page`() {
        show()
        filter("track 1")
        compose.runOnIdle { org.junit.Assert.assertEquals(111, browse.value.historyTotal) }
        compose.onNodeWithText("Older").performClick()
        compose.onNodeWithText("History track 189").assertIsDisplayed()
        filter("")
        compose.onNodeWithText("History track 0").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(200, browse.value.historyTotal)
            org.junit.Assert.assertEquals(0, browse.value.historyPage)
        }
    }

    @Test fun `pending filtering retains the editable field and only the newest phrase finishes`() {
        show()
        val gate = CompletableDeferred<Unit>()
        readGate = gate
        compose.onNode(hasSetTextAction()).performClick().performTextReplacement("track")
        advanceFilter()
        compose.runOnIdle { org.junit.Assert.assertTrue(browse.value.loading) }
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextReplacement("track 150")
        advanceFilter()
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitUntil(5_000) { !browse.value.loading }
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, browse.value.historyTotal) }
    }

    @Test fun `typing does not call play again or replace the captured History page`() {
        show()
        compose.onNodeWithText("History track 0").performClick()
        val captured = playedRows
        filter("track 150")
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertNotNull(captured)
            org.junit.Assert.assertSame(captured, playedRows)
            org.junit.Assert.assertEquals(1, playCalls)
        }
    }

    @Test fun `read failure offers labelled retry instead of pretending there were no matches`() {
        show()
        readFails = true
        filter("track 150")
        compose.onNodeWithText("Could not load History. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("No History entries match this phrase.").assertDoesNotExist()
        readFails = false
        compose.onNodeWithText("Retry").performClick()
        advanceFilter()
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, failures.size) }
    }

    @Test fun `no match is distinct from an empty History and clear still removes all entries`() {
        show()
        filter("no such title")
        compose.onNodeWithText("No History entries match this phrase.").assertIsDisplayed()
        compose.onNodeWithText("Clear history").performClick()
        filter("")
        compose.onNodeWithText("Nothing played yet. Anything you play — from a playlist, from Random or from a search — turns up here.")
            .assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertTrue(stored.isEmpty()) }
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish live field and no-match feedback use the selected locale`() {
        show()
        compose.onNodeWithText("Szukaj w historii").assertIsDisplayed()
        filter("TRACK 150")
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        filter("nie ma takiego utworu")
        compose.onNodeWithText("Brak wpisów historii pasujących do frazy.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test fun `returning through Browse root opens History at the top`() {
        show()
        scrollTo(50)
        compose.runOnIdle { workflow.browseBack() }
        openHistory()
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
        openHistory()
        compose.onNodeWithText("History track 0").assertIsDisplayed()
        compose.onNodeWithText("Older").performClick()
        compose.onNodeWithText("History track 100").assertIsDisplayed()
    }
}
