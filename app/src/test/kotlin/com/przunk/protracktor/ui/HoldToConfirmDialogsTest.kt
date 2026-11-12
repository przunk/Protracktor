// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.SavedPlaylist
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

/**
 * Deleting a playlist and a catalogue's index (2026-11-12): the dialog says what goes, and its yes
 * has to be held for three seconds. A tap, or a hold let go early, deletes nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HoldToConfirmDialogsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val deleted = mutableListOf<String>()
    private fun text(id: Int) = activity.get().getString(id)

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun playlists() {
        activity.get().setContent {
            MaterialTheme { Surface {
                PlaylistSwitcher(
                    playlists = listOf(SavedPlaylist(1, "First", 3), SavedPlaylist(2, "Second", 5)),
                    activeId = 1, onSelect = {}, onCreate = {}, onRename = { _, _ -> },
                    onDelete = { deleted += "playlist $it" }, onImport = {}, onExport = {},
                )
            } }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_playlist_menu, "Second")).performClick()
        compose.onNodeWithText(text(R.string.action_delete)).performClick()
        compose.waitForIdle()
    }

    private fun storage() {
        activity.get().setContent {
            MaterialTheme { Surface {
                StorageSection(
                    cacheBytes = 0, archiveBytes = emptyMap(), databaseBytes = 4_000_000, replayCount = 0,
                    playerCount = 0, replayBytes = 0,
                    catalogues = listOf(CatalogueSummary("modland", "Modland", 120, 1L)),
                    songLengthCount = 0, trackMetadataCount = 0, songDbLengthCount = 0,
                    onClearCache = {}, onDeleteIndex = { deleted += "index $it" },
                    onDeleteSongMetadata = {}, onDeleteReplayRoutines = {},
                )
            } }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_delete_storage,
            activity.get().getString(R.string.storage_index, "Modland"))).performClick()
        compose.waitForIdle()
    }

    // The hold itself is HoldToConfirm's, held to the end and let go early in History's tests
    // (`HistoryBrowseUiTest`); here the yes of each dialog is that button, and a tap is not a yes.
    // Driving a held gesture by hand inside a dialog window loses the node in this harness, so the
    // hold in a dialog is the phone's to show.

    @Test fun `a playlist's yes is a hold, and a tap on it deletes nothing`() {
        playlists()
        compose.onNodeWithTag("confirm-hold").assertTextContains(text(R.string.action_delete)).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), deleted)
        compose.onNodeWithTag("confirm-hold").assertExists()
    }

    @Test fun `a catalogue index's yes is a hold, and a tap on it deletes nothing`() {
        storage()
        compose.onNodeWithTag("confirm-hold").assertTextContains(text(R.string.action_delete)).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), deleted)
        compose.onNodeWithTag("confirm-hold").assertExists()
    }

    @Test fun `in a dialog that has asked, TalkBack's click on the yes confirms`() {
        playlists()
        compose.onNodeWithTag("confirm-hold").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf("playlist 2"), deleted)
    }
}
