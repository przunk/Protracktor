// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.View
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.DownloadKeys
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.RandomFilterAvailability
import com.przunk.protracktor.player.RandomScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class RandomScopeSheetTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val browse = mutableStateOf(BrowseState())
    private val preparing = mutableStateOf(false)
    private val visible = mutableStateOf(true)
    private val randomOpen = mutableStateOf(false)
    private var result = RandomFilterAvailability(mapOf(Platforms.AMIGA.id to 42), 2, 3)
    private var reads = 0
    private var picked: RandomScope? = null
    private var load: suspend () -> RandomFilterAvailability = { result }
    private var download: () -> Unit = {}

    private fun change(action: () -> Unit) {
        compose.runOnIdle {
            action()
            // Flush writes made outside composition; Robolectric does not deliver every frame.
            Snapshot.sendApplyNotifications()
        }
    }

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun stop() { activity.close() }

    private fun show() {
        activity.get().setContent {
            MaterialTheme {
                Text("Random screen")
                val filters = rememberRandomFilterState(
                    active = randomOpen.value || visible.value,
                    browse = browse.value,
                    preparingDatabase = preparing.value,
                    loadAvailability = { reads++; load() },
                )
                if (visible.value) {
                    RandomScopeSheet(
                        browse = browse.value,
                        filters = filters,
                        onPick = { picked = it; visible.value = false },
                        onDownloadFavourites = { download() },
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }
        val view = activity.get().window.decorView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 720, 1600)
    }

    @Test fun `unread counters do not tell the user to download favourites`() {
        load = { awaitCancellation() }
        show()
        compose.onNodeWithText("Checking available filters…").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText("Everything").assertIsEnabled()
    }

    @Test fun `opening reads fresh availability instead of empty Browse counters`() {
        show()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Favourites").assertIsEnabled()
        compose.onNodeWithText(Platforms.SHARP.name).assertIsNotEnabled()
        compose.onNodeWithText(Platforms.SONY.name).assertIsNotEnabled()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Amiga").performClick()
        compose.runOnIdle { assertEquals(RandomScope.OnPlatform(Platforms.AMIGA.id), picked) }
    }

    @Test fun `warm reopening keeps known chips usable during a delayed refresh`() {
        show()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { visible.value = false }
        compose.waitForIdle()
        change { load = { awaitCancellation() }; visible.value = true }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Favourites").assertIsEnabled()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test fun `download refresh does not erase previously known availability`() {
        show()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change {
            load = { awaitCancellation() }
            browse.value = browse.value.copy(indexing = mapOf(DownloadKeys.FAVOURITES to "0%"))
        }
        compose.onNodeWithText("Checking available filters…").assertExists()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Favourites").assertIsEnabled()
    }

    @Test fun `Random preloads once and reopening its sheet does not recount`() {
        randomOpen.value = true
        visible.value = false
        show()
        compose.runOnIdle { assertEquals(1, reads) }
        change { visible.value = true }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { visible.value = false }
        compose.waitForIdle()
        change {
            visible.value = true
            browse.value = browse.value.copy(randomScope = RandomScope.OnPlatform(Platforms.AMIGA.id))
        }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test fun `inactive Random does not query availability`() {
        visible.value = false
        show()
        compose.runOnIdle { assertEquals(0, reads) }
        change { browse.value = browse.value.copy(favouriteCount = 2) }
        compose.runOnIdle { assertEquals(0, reads) }
        change { randomOpen.value = true }
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test fun `deleting an index invalidates known counts before a delayed replacement`() {
        show()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { browse.value = browse.value.copy(deleting = setOf("modland")) }
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText("Checking available filters…").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
        val fresh = CompletableDeferred<RandomFilterAvailability>()
        change { load = { fresh.await() }; browse.value = browse.value.copy(deleting = emptySet()) }
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        change { fresh.complete(RandomFilterAvailability(emptyMap(), 0, 0)) }
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText("Download").assertIsEnabled()
    }

    @Test fun `data changed while inactive cannot reuse the old enabled snapshot`() {
        show()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { visible.value = false }
        compose.waitForIdle()
        change { browse.value = browse.value.copy(favouriteCount = 1) }
        compose.waitForIdle()
        change { load = { awaitCancellation() }; visible.value = true }
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText("Checking available filters…").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test fun `refresh error retains known counts and offers a working Retry`() {
        show()
        change {
            load = { error("database unavailable") }
            browse.value = browse.value.copy(indexing = mapOf(DownloadKeys.FAVOURITES to "0%"))
        }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Could not check available filters. Try again.").assertExists()
        compose.runOnIdle { load = { RandomFilterAvailability(emptyMap(), 0, 0) } }
        compose.onNodeWithText("Retry").performClick()
        Snapshot.sendApplyNotifications()
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test fun `obsolete non-cancellable read cannot overwrite a newer catalogue result`() {
        val old = CompletableDeferred<RandomFilterAvailability>()
        load = { withContext(NonCancellable) { old.await() } }
        show()
        compose.onNodeWithText("Checking available filters…").assertExists()
        change {
            load = { result }
            browse.value = browse.value.copy(platformCounts = mapOf(Platforms.AMIGA.id to 42))
        }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { old.complete(RandomFilterAvailability(emptyMap(), 0, 0)) }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test fun `reopening discards old counts even when Browse retains them`() {
        browse.value = browse.value.copy(platformCounts = result.platformCounts, favouriteCount = 2)
        show()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { visible.value = false }
        compose.waitForIdle()
        change {
            result = RandomFilterAvailability(emptyMap(), 0, 0)
            visible.value = true
        }
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText("Favourites").assertIsNotEnabled()
        compose.onNodeWithText("Download").assertIsEnabled()
        compose.runOnIdle { assertEquals(2, reads) }
    }

    @Test fun `favourites download refreshes all chips without closing the sheet`() {
        result = result.copy(favouriteCount = 0, favouritesListed = 0)
        download = { browse.value = browse.value.copy(indexing = mapOf(DownloadKeys.FAVOURITES to "0%")) }
        show()
        compose.onNodeWithText("Favourites").assertIsNotEnabled()
        compose.onNodeWithText("Download").performClick()
        Snapshot.sendApplyNotifications()
        compose.onNodeWithText("Downloading…").assertIsNotEnabled()
        val afterStart = compose.runOnIdle { reads }
        change {
            browse.value = browse.value.copy(indexing = mapOf(DownloadKeys.FAVOURITES to "50%"))
        }
        compose.waitForIdle()
        change {
            assertEquals("progress must not recount", afterStart, reads)
            result = RandomFilterAvailability(mapOf(Platforms.AMIGA.id to 42, Platforms.C64.id to 7), 2, 3)
            browse.value = browse.value.copy(indexing = emptyMap())
        }
        compose.onNodeWithText("Favourites").assertIsEnabled()
        compose.onNodeWithText(Platforms.C64.name).assertIsEnabled()
        compose.onNodeWithText("Random picks from").assertExists()
    }

    @Test fun `database preparation waits and its completion refreshes an open sheet`() {
        preparing.value = true
        show()
        compose.onNodeWithText("Checking available filters…").assertExists()
        change { assertEquals(0, reads); preparing.value = false }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        change { preparing.value = true }
        compose.onNodeWithText("Checking available filters…").assertExists()
        change {
            result = result.copy(platformCounts = mapOf(Platforms.C64.id to 1))
            preparing.value = false
        }
        compose.onNodeWithText("Amiga").assertIsNotEnabled()
        compose.onNodeWithText(Platforms.C64.name).assertIsEnabled()
        compose.runOnIdle { assertEquals(2, reads) }
    }

    @Test fun `read failure offers retry rather than a misleading download`() {
        load = { error("database unavailable") }
        show()
        compose.onNodeWithText("Could not check available filters. Try again.").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.runOnIdle { load = { result } }
        compose.onNodeWithText("Retry").performClick()
        Snapshot.sendApplyNotifications()
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test fun `listed but unindexed favourites do not offer another list download`() {
        result = RandomFilterAvailability(emptyMap(), 0, 3)
        show()
        compose.onNodeWithText("None of the favourites are in your Modland index. Index Modland first.").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Favourites").assertIsNotEnabled()
    }

    @Test fun `closing while reading cannot publish into the next opening`() {
        val oldRead = CompletableDeferred<RandomFilterAvailability>()
        load = { oldRead.await() }
        show()
        compose.onNodeWithText("Checking available filters…").assertExists()
        change { visible.value = false }
        compose.waitForIdle()
        change { load = { result }; visible.value = true }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.runOnIdle { oldRead.complete(RandomFilterAvailability(emptyMap(), 0, 0)) }
        compose.onNodeWithText("Amiga").assertIsEnabled()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }
}
