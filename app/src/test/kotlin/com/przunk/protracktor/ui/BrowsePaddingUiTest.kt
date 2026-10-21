// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.HeldSets
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
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
                MaterialTheme {
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
