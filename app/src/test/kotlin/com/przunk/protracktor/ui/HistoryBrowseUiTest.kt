// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
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
    private lateinit var historyWrites: HistoryWrites
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
        historyWrites = HistoryWrites(activity.get().lifecycleScope,
            record = { track -> stored = listOf(track) + stored.filterNot { it.id == track.id } },
            qualify = {}, erase = { stored = emptyList() }, failed = { failures += it })
        historyBrowser = HistoryBrowser(activity.get().lifecycleScope, browse, historyWrites.revision,
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
                    onClearHistory = {
                        val cleared = historyWrites.clear()
                        historyBrowser.clearAll({ cleared.await() }, { failures += it })
                    },
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

    // The words as the top bar's field hands them on (2026-11-12: the field moved to the top bar,
    // which this harness does not draw; `NavSlotsUiTest`'s shell tests press the field itself).
    private fun type(query: String) {
        compose.runOnIdle { historyBrowser.setQuery(query) }
    }

    private fun filter(query: String) {
        type(query)
        advanceFilter()
        compose.waitUntil(5_000) { !browse.value.loading }
        compose.waitForIdle()
    }

    @Test fun `live phrase filtering searches old title author and literal filename`() {
        show()
        filter("TRACK 150")
        compose.onNodeWithText("History track 150").assertIsDisplayed()
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
        compose.onNodeWithTag("history-older").performClick()
        compose.onNodeWithText("History track 189").assertIsDisplayed()
        filter("")
        compose.onNodeWithText("History track 0").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(200, browse.value.historyTotal)
            org.junit.Assert.assertEquals(0, browse.value.historyPage)
        }
    }

    @Test fun `while a phrase is pending only the newest one finishes`() {
        show()
        val gate = CompletableDeferred<Unit>()
        readGate = gate
        type("track")
        advanceFilter()
        compose.runOnIdle { org.junit.Assert.assertTrue(browse.value.loading) }
        type("track 150")
        advanceFilter()
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitUntil(5_000) { !browse.value.loading }
        compose.onNodeWithText("History track 150").assertIsDisplayed()
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
        holdClear(3_200)
        filter("")
        compose.onNodeWithText("Nothing played yet. Anything you play — from a playlist, from Random or from a search — turns up here.")
            .assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertTrue(stored.isEmpty()) }
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish no-match feedback and Clear all use the selected locale`() {
        show()
        compose.onNodeWithText("Wyczyść całą historię").assertIsDisplayed()
        filter("TRACK 150")
        compose.onNodeWithText("History track 150").assertIsDisplayed()
        filter("nie ma takiego utworu")
        compose.onNodeWithText("Brak wpisów historii pasujących do frazy.").assertIsDisplayed()
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
        compose.onNodeWithTag("history-older").performClick()
        compose.onNodeWithText("History track 100").assertIsDisplayed()
        scrollTo(150)
        compose.onNodeWithTag("history-newer").performClick()
        compose.onNodeWithText("History track 50").assertIsDisplayed()
        compose.runOnIdle { workflow.browseBack() }
        openHistory()
        compose.onNodeWithText("History track 0").assertIsDisplayed()
        compose.onNodeWithTag("history-older").performClick()
        compose.onNodeWithText("History track 100").assertIsDisplayed()
    }

    @Test fun `a committed new play appears while History stays open at the top`() {
        show()
        compose.runOnIdle { historyWrites.record(TrackRef("new", "New live tune"), historyWrites.generation) }
        compose.waitForIdle()
        compose.onNodeWithText("New live tune").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(201, browse.value.historyTotal)
            org.junit.Assert.assertFalse(browse.value.loading)
        }
    }

    @Test fun `a new play preserves a scrolled History anchor`() {
        show()
        scrollTo(50)
        compose.runOnIdle { historyWrites.record(TrackRef("new", "New live tune"), historyWrites.generation) }
        compose.waitForIdle()
        compose.onNodeWithText("History track 50").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals(201, browse.value.historyTotal) }
    }

    @Test fun `live writes respect the phrase and do not replace captured playback`() {
        show()
        compose.onNodeWithText("History track 0").performClick()
        val captured = playedRows
        filter("fresh")
        compose.runOnIdle {
            historyWrites.record(TrackRef("fresh", "Fresh live match"), historyWrites.generation)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Fresh live match").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertSame(captured, playedRows)
            org.junit.Assert.assertEquals(1, playCalls)
            org.junit.Assert.assertEquals("fresh", browse.value.historyQuery)
        }
    }

    // A hold as a finger makes it: down, the time passing, up. The clock is held meanwhile: left to
    // run by itself it would play out the whole hold while waiting for the screen to settle, and a
    // short hold would pass for a full one.
    private fun holdClear(millis: Long) {
        val clear = compose.onNodeWithTag("clear-history")
        compose.mainClock.autoAdvance = false
        clear.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(millis)
        clear.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun `a tap on Clear history clears nothing and says to hold it`() {
        show()
        // The clock held, so the hint is read before its two seconds run out.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("clear-history").performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("clear-history").assertTextContains("Hold it")
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithTag("clear-history").assertTextContains("Clear all history")
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { org.junit.Assert.assertFalse(stored.isEmpty()) }
        compose.onNodeWithText("History track 0").assertIsDisplayed()
    }

    @Test fun `a hold let go before three seconds clears nothing, a full one clears all`() {
        show()
        holdClear(1_500)
        compose.runOnIdle { org.junit.Assert.assertFalse(stored.isEmpty()) }
        compose.onNodeWithText("History track 0").assertIsDisplayed()
        holdClear(3_200)
        compose.onNodeWithText("Nothing played yet. Anything you play — from a playlist, from Random or from a search — turns up here.")
            .assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertTrue(stored.isEmpty()) }
    }

    @Test fun `TalkBack's click on Clear history asks first, and clears only on yes`() {
        show()
        compose.onNodeWithTag("clear-history").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithText("Clear History?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { org.junit.Assert.assertFalse(stored.isEmpty()) }
        compose.onNodeWithTag("clear-history").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onAllNodesWithText("Clear all history").filterToOne(hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertTrue(stored.isEmpty()) }
    }

    @Test fun `the foot holds the paging and Clear all, and stays put while the list scrolls`() {
        show()
        // Newer, the range, Older, then Clear all, in that order along the foot.
        compose.onNode(hasText("1–100 of 200") and hasAnyAncestor(hasTestTag("history-footer"))).assertIsDisplayed()
        val newer = compose.onNodeWithTag("history-newer").fetchSemanticsNode().boundsInRoot
        val range = compose.onNodeWithTag("history-range").fetchSemanticsNode().boundsInRoot
        val older = compose.onNodeWithTag("history-older").fetchSemanticsNode().boundsInRoot
        val clear = compose.onNodeWithTag("clear-history").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(newer.right <= range.left + 1f && range.right <= older.left + 1f && older.right <= clear.left + 1f)
        // The arrows are named for TalkBack.
        compose.onNodeWithContentDescription("Newer").assertExists()
        compose.onNodeWithContentDescription("Older").assertExists()
        // Under the list, and not moved by scrolling it.
        val foot = compose.onNodeWithTag("history-footer").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithText("History track 0").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(first.bottom <= foot.top)
        scrollTo(99)
        org.junit.Assert.assertEquals(foot, compose.onNodeWithTag("history-footer").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("history-older").performClick()
        compose.waitUntil(5_000) { !browse.value.loading }
        compose.onNode(hasText("101–200 of 200") and hasAnyAncestor(hasTestTag("history-footer"))).assertIsDisplayed()
    }

    @Test fun `one page says how many at the foot`() {
        show()
        filter("track 15")
        compose.onNode(hasText("11 entries") and hasAnyAncestor(hasTestTag("history-footer"))).assertIsDisplayed()
        compose.onNodeWithTag("history-newer").assertDoesNotExist()
    }
}
