// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SavedPlacesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val place = SavedPlace(SavedPlaceKey("modland", "Protracker", "Artist"), "Modland", 1)
    private val saved = mutableStateOf(SavedPlacesState(loading = false))
    private val browse = mutableStateOf(BrowseState())
    private val calls = mutableListOf<String>()

    @Before fun start() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() {
        try { activity.get().setContent {}; compose.waitForIdle() }
        finally { activity.close() }
    }
    private fun text(id: Int) = activity.get().getString(id)

    private fun show() {
        activity.get().setContent {
            MaterialTheme {
                BrowseScreen(
                    browse = browse.value, savedPlaces = saved.value, playlistName = "Playlist", contentPadding = PaddingValues(),
                    onOpenDomain = {
                        calls += "domain:$it"
                        browse.value = BrowseNavigation.enteringDomain(browse.value, it)
                        Snapshot.sendApplyNotifications()
                    },
                    onSavePlace = {
                        calls += "save"
                        saved.value = saved.value.copy(rows = listOf(SavedPlaceRow(place, SavedPlaceAvailability.AVAILABLE)))
                        Snapshot.sendApplyNotifications()
                    },
                    onRemovePlace = {
                        calls += "remove:$it"
                        saved.value = saved.value.copy(rows = emptyList())
                        Snapshot.sendApplyNotifications()
                    },
                    onOpenPlace = { calls += "open:$it" }, onRefreshPlaces = { calls += "refresh" },
                    onPickFolder = {}, onPickFiles = {}, onOpenFolder = {}, onForgetFolder = {}, onScanFolder = {},
                    onIndexCatalogue = { calls += "download" }, onDownloadSongMetadata = {}, onDownloadReplayRoutines = {},
                    onOpenCatalogue = {}, onOpenGroup = {}, onRandom = {}, onChooseRandomScope = {},
                    onQueryChange = {}, onScope = {}, onToggleCatalogue = {}, onTogglePlatform = {}, onSearch = {},
                    onClearHistory = {}, playingId = null, loadingId = null, onShowNeighbours = {}, onShareFile = {},
                    onShareAudio = {}, onShareLink = {}, onSendToWeb = {}, onPlay = { calls += "play" }, onAdd = { calls += "playlist" },
                )
            }
        }
        // Give the host a real viewport and deliver state writes explicitly, as in the other UI fixtures.
        val view = activity.get().window.decorView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 720, 1600)
    }

    @Test fun `Browse exposes Saved places and explains how to save the first folder`() {
        show()
        compose.onNodeWithText(text(R.string.saved_places_title)).performClick()
        compose.onNodeWithText(text(R.string.saved_places_empty)).assertExists()
        assertEquals(listOf("domain:SAVED"), calls)
    }

    @Test fun `folder save and remove are explicit actions independent of playback`() {
        browse.value = BrowseState(domain = BrowseDomain.ONLINE,
            openCatalogue = CatalogueSummary("modland", "Modland", 1, 1), openFormat = "Protracker", openAuthor = "Artist")
        show()
        compose.onNodeWithText(text(R.string.saved_place_save)).performClick()
        compose.onNodeWithText(text(R.string.saved_place_saved)).assertExists()
        compose.onNodeWithText(text(R.string.saved_place_remove)).performClick()
        compose.onNodeWithText(text(R.string.saved_place_save)).assertExists()
        assertEquals(listOf("save", "remove:${place.key}"), calls)
    }

    @Test fun `list opens the stored full route and removes only the bookmark`() {
        browse.value = BrowseState(domain = BrowseDomain.SAVED)
        saved.value = SavedPlacesState(listOf(SavedPlaceRow(place, SavedPlaceAvailability.AVAILABLE)), loading = false)
        show()
        compose.onNodeWithText("Modland › Protracker").assertExists()
        compose.onNodeWithText(text(R.string.saved_place_open)).performClick()
        compose.onNodeWithText(text(R.string.saved_place_remove)).performClick()
        assertEquals(listOf("open:${place.key}", "remove:${place.key}"), calls)
    }

    @Test fun `missing index explains disabled Open and routes to catalogues without downloading`() {
        browse.value = BrowseState(domain = BrowseDomain.SAVED)
        saved.value = SavedPlacesState(listOf(SavedPlaceRow(place, SavedPlaceAvailability.INDEX_MISSING)), loading = false)
        show()
        compose.onNodeWithText(text(R.string.saved_place_index_missing)).assertExists()
        compose.onNodeWithText(text(R.string.saved_place_open)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.saved_place_prepare)).performClick()
        assertEquals(listOf("domain:ONLINE"), calls)
    }

    @Test fun `disabled catalogue stays visible and removable without a false recovery action`() {
        browse.value = BrowseState(domain = BrowseDomain.SAVED)
        saved.value = SavedPlacesState(listOf(SavedPlaceRow(place, SavedPlaceAvailability.CATALOGUE_UNAVAILABLE)), loading = false)
        show()
        compose.onNodeWithText(text(R.string.saved_place_source_missing)).assertExists()
        compose.onNodeWithText(text(R.string.saved_place_prepare)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.saved_place_remove)).performClick()
        assertEquals(listOf("remove:${place.key}"), calls)
    }

    @Test fun `failed read explains retry rather than showing a false empty list`() {
        browse.value = BrowseState(domain = BrowseDomain.SAVED)
        saved.value = SavedPlacesState(loading = false, failed = true)
        show()
        compose.onNodeWithText(text(R.string.saved_places_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.saved_place_failed)).assertExists()
        compose.onNodeWithText(text(R.string.saved_places_retry)).performClick()
        assertEquals(listOf("refresh"), calls)
    }

    @Test @Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun `Polish Saved places is available from Browse`() {
        show()
        compose.onNodeWithText("Zapisane miejsca").performClick()
        compose.onNodeWithText(text(R.string.saved_places_empty)).assertExists()
        assertEquals(listOf("domain:SAVED"), calls)
    }
}
