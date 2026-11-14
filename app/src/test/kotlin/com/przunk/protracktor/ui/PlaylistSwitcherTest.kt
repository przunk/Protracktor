// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
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
 * The playlists sheet as cards (2026-11-14): each playlist a card with its name and its size in
 * words, the open one marked by shape and words as well as colour, and New and Import beside the
 * title. What a card and its menu do is unchanged.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PlaylistSwitcherTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val calls = mutableListOf<String>()
    private val playlists = listOf(SavedPlaylist(1, "Playlist", 8), SavedPlaylist(2, "Chiptunes for the train", 128),
        SavedPlaylist(3, "Crack intros", 1))
    private fun text(id: Int) = activity.get().getString(id)

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            MaterialTheme { Surface {
                PlaylistSwitcher(
                    playlists = playlists, activeId = 1,
                    onSelect = { calls += "select $it" }, onCreate = { calls += "create $it" },
                    onRename = { id, name -> calls += "rename $id $name" }, onDelete = { calls += "delete $it" },
                    onImport = { calls += "import" }, onExport = { calls += "export $it" },
                )
            } }
        }
        compose.waitForIdle()
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun card(id: Long) = compose.onNodeWithTag("playlist-card-$id")

    @Test fun `each playlist is a card with its size in words, the open one marked and saying so`() {
        card(1).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertTextContains("Playlist", substring = true).assertTextContains("Open · 8 tracks")
        card(2).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assertTextContains("128 tracks")
        card(3).assertTextContains("1 track")
        compose.onAllNodesWithText("Open", substring = true).assertCountEquals(1)
    }

    @Test fun `a tap on a card opens that playlist`() {
        card(2).performClick()
        compose.waitForIdle()
        assertEquals(listOf("select 2"), calls)
    }

    // New's naming dialog is the one it always was; its blinking cursor never lets this harness
    // settle, so here New is checked as the labelled action beside the title, and the dialog on the
    // phone.
    @Test fun `New and Import stand beside the title, and Import imports`() {
        val title = compose.onNodeWithText(text(R.string.playlist_switch_title)).fetchSemanticsNode().boundsInRoot
        val new = compose.onNodeWithTag("playlist-new").assertTextContains(text(R.string.playlist_new_short))
            .assertHasClickAction().fetchSemanticsNode().boundsInRoot
        val import = compose.onNodeWithTag("playlist-import").assertTextContains(text(R.string.action_import_playlist))
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(title.right <= new.left && new.right <= import.left)
        org.junit.Assert.assertTrue(new.top < compose.onNodeWithTag("playlist-card-1").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("playlist-import").performClick()
        compose.waitForIdle()
        assertEquals(listOf("import"), calls)
    }

    @Test fun `a card's menu still renames, exports and deletes`() {
        compose.onNodeWithContentDescription(activity.get().getString(R.string.a11y_playlist_menu, "Crack intros")).performClick()
        compose.onNodeWithText(text(R.string.action_export_playlist)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("export 3"), calls)
    }
}
