// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.alarms.api.MusicAlarms
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.HeldSets
import com.przunk.protracktor.screenshot.saveAs
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class BrowsePaddingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val browse = MutableStateFlow(BrowseState(catalogues = listOf(
        CatalogueSummary("modland", "Modland", 0, null),
        CatalogueSummary("asma", "ASMA", 1, 1L, complete = true),
        CatalogueSummary("modarchive", "The Mod Archive", 0, null, isOnlineOnly = true),
    )))

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun show(fontScale: Float = 1f) {
        activity.get().setContent {
            val current by browse.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ProtracktorTheme(AppTheme.DARK, false) {
                    Surface {
                        BrowseScreen(
                            browse = current, playlistName = "Selected",
                            contentPadding = PaddingValues(start = 12.dp, top = 24.dp, end = 18.dp, bottom = 32.dp),
                            onOpenDomain = { browse.value = browse.value.copy(domain = it) },
                            onPickFolder = {}, onPickFiles = {}, onOpenFolder = {}, onForgetFolder = {},
                            onScanFolder = {}, onIndexCatalogue = {}, onDownloadSongMetadata = {},
                            onDownloadReplayRoutines = {}, onOpenCatalogue = {}, onOpenGroup = {},
                            onRandom = {}, onChooseRandomScope = {}, onQueryChange = {}, onScope = {},
                            onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = {},
                            recentQueries = emptyList(), onSearchSuggestion = {}, onClearHistory = {},
                            onHistoryQueryChange = {},
                            playingId = null, loadingId = null, onShowNeighbours = {}, onShareFile = {},
                            onShareAudio = {}, onShareLink = {}, onSendToWeb = {}, onPlay = {}, onAdd = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun leftOf(text: String): Float = compose.onNodeWithText(text, useUnmergedTree = true)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left

    private fun openOnline(): Float {
        val title = activity.get().getString(R.string.domain_online_title)
        val left = leftOf(title)
        compose.onNodeWithText(title).performClick()
        compose.waitForIdle()
        return left
    }

    private fun assertOnlineAlignment(left: Float) {
        listOf("Modland", "ASMA", "The Mod Archive",
            activity.get().getString(R.string.song_metadata_title),
            activity.get().getString(R.string.replay_routines_title)).forEach { title ->
            assertEquals("Text start for $title", left, leftOf(title), 0.5f)
        }
    }

    @Test fun `root and all online row types share the same text inset`() {
        show()
        assertOnlineAlignment(openOnline())
    }

    private fun assertRootOrder(locale: String) {
        show()
        val titles = buildList {
            add(R.string.domain_local_title)
            add(R.string.saved_places_title)
            add(R.string.domain_online_title)
            add(R.string.domain_random_title)
            add(R.string.domain_search_title)
            if (MusicAlarms.enabled) add(R.string.alarm_title)
            add(R.string.domain_history_title)
        }.map { activity.get().getString(it) }
        val actual = titles.sortedBy { title ->
            compose.onNodeWithText(title).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals("Browse destinations in visual order", titles, actual)
        capture("browse-destinations-$locale")
        if (MusicAlarms.enabled) {
            compose.onNodeWithText(activity.get().getString(R.string.alarm_title)).performClick()
            assertEquals("com.przunk.protracktor.alarms.AlarmsActivity",
                shadowOf(activity.get()).nextStartedActivity.component?.className)
        }
    }

    @Test fun `root shows the requested destination order and keeps Alarms reachable`() {
        assertRootOrder("en")
    }

    @Test
    @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish root keeps the same destination order`() {
        assertRootOrder("pl")
    }

    @Test
    @Config(qualifiers = "en-w360dp-h300dp-xhdpi")
    fun `a short root viewport scrolls by touch and opens the final destination`() {
        show()
        compose.onNodeWithText(activity.get().getString(R.string.domain_local_title)).assertIsDisplayed()
        capture("browse-destinations-short-top")
        val list = compose.onNode(hasScrollToIndexAction())
        repeat(4) {
            list.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        val history = activity.get().getString(R.string.domain_history_title)
        compose.onNodeWithText(history).assertIsDisplayed()
        capture("browse-destinations-short-bottom")
        compose.onNodeWithText(history).performClick()
        assertEquals(BrowseDomain.HISTORY, browse.value.domain)
    }

    private fun capture(name: String) {
        if (System.getProperty("protracktor.screenshots") == null) return
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val view = activity.get().window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
            it.saveAs(name)
        }
    }

    @Test fun `alignment survives larger text and held state updates`() {
        show(fontScale = 1.3f)
        val left = openOnline()
        assertOnlineAlignment(left)
        compose.runOnIdle {
            browse.value = browse.value.copy(
                catalogues = browse.value.catalogues.map { it.copy(trackCount = 2, complete = true) },
                held = HeldSets(true, true, true, true, true),
                heldCountsKnown = true,
                songLengthCount = 10,
            )
        }
        assertOnlineAlignment(left)
    }
}
