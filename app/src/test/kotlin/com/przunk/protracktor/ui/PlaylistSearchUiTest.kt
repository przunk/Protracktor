// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.PlayQueue
import com.przunk.protracktor.player.TrackRef
import com.przunk.protracktor.player.PlayerViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class PlaylistSearchUiTest : PlaybackShellTest() {
    private val tracks = listOf(
        TrackRef("content://playlist.search/one.mod", "Space Ninja", author = "Barry"),
        TrackRef("content://playlist.search/two.it", "Studium Fałszu", author = "Jakim"),
        TrackRef("content://playlist.search/three.mod", "Space echoes", author = "Michał"),
    )

    private fun openSearch() {
        compose.runOnIdle {
            player.value = player.value.copy(queue = PlayQueue(tracks).startAt(0), playback = null,
                playing = false)
        }
        frame(300)
        compose.onNodeWithText(application.getString(R.string.playlist_search_action)).performClick()
        frame(350)
    }

    @Test fun `search replaces chooser at the same height and closing restores the full list`() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(tracks)) }
        frame(300)
        val height = compose.onNodeWithTag("playlist-search-header").fetchSemanticsNode().boundsInRoot.height
        openSearch()
        assertEquals(height, compose.onNodeWithTag("playlist-search-header").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.onNodeWithTag("playlist-search-field").performTextInput("space")
        frame(250)
        compose.onNodeWithTag("enqueue-row-0").assertExists()
        compose.onNodeWithTag("enqueue-row-1").assertDoesNotExist()
        compose.onNodeWithTag("enqueue-row-2").assertExists()
        assertEquals(tracks, player.value.queue.tracks)
        compose.onNodeWithContentDescription(application.getString(R.string.playlist_search_close)).performClick()
        frame(350)
        compose.onNodeWithTag("playlist-chooser").assertExists()
        compose.onNodeWithTag("enqueue-row-1").assertExists()
        compose.onNodeWithTag("playlist-search-dimming").assertDoesNotExist()
    }

    @Test fun `done restores chrome while keeping the query and refocusing dims again`() {
        openSearch()
        compose.onNodeWithTag("playlist-search-field").performTextInput("falszu")
        frame(250)
        compose.onNodeWithTag("playlist-search-dimming").assertExists()
        compose.onNodeWithTag("playlist-search-field").performImeAction()
        frame(250)
        compose.onNodeWithTag("playlist-search-dimming").assertDoesNotExist()
        compose.onNodeWithTag("playlist-search-field").assertTextContains("falszu")
        compose.onNodeWithTag("enqueue-row-1").assertExists()
        compose.onNodeWithTag("playlist-search-field").performClick()
        frame(250)
        compose.onNodeWithTag("playlist-search-dimming").assertExists()
    }

    @Test fun `filtered removal deletes the original occurrence and never its displayed index`() {
        openSearch()
        compose.onNodeWithTag("playlist-search-field").performTextInput("echoes")
        frame(250)
        compose.onNodeWithTag("playlist-search-field").performImeAction()
        frame(250)
        compose.onNodeWithContentDescription(application.getString(R.string.a11y_track_actions, tracks[2].title)).performClick()
        frame(250)
        compose.onNodeWithText(application.getString(R.string.action_delete)).performClick()
        frame(300)
        assertEquals(tracks.take(2), player.value.queue.tracks)
        compose.onNodeWithText(application.getString(R.string.playlist_search_no_matches)).assertExists()
        compose.onNodeWithContentDescription(application.getString(R.string.playlist_search_clear)).performClick()
        frame(300)
        compose.onNodeWithTag("enqueue-row-0").assertExists()
        compose.onNodeWithTag("enqueue-row-1").assertExists()
    }

    @Test fun `right swipe on a filtered row enqueues the selected track without replacing context`() {
        openSearch()
        compose.onNodeWithTag("playlist-search-field").performTextInput("echoes")
        frame(250)
        compose.onNodeWithTag("enqueue-row-2").performTouchInput { swipeRight() }
        frame(300)
        assertEquals(tracks[2], player.value.schedule.pending.single().track)
        assertEquals(tracks, player.value.queue.tracks)
    }

    @Test fun `closing a filter restores the original viewport even if playback moved outside the results`() {
        val many = (0..49).map { TrackRef("content://playlist.search/$it.mod", "Track $it") }
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(many).startAt(0), playback = null) }
        frame(300)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(12)
        frame(300)
        val top = compose.onNodeWithTag("enqueue-row-12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText(application.getString(R.string.playlist_search_action)).performClick()
        frame(350)
        compose.onNodeWithTag("playlist-search-field").performTextInput("Track 20")
        frame(300)
        compose.runOnIdle { player.value = player.value.copy(queue = player.value.queue.startAt(30)) }
        frame(300)
        compose.onNodeWithContentDescription(application.getString(R.string.playlist_search_close)).performClick()
        frame(350)
        assertEquals(top, compose.onNodeWithTag("enqueue-row-12").fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun `back ends editing first and then restores the normal playlist header`() {
        openSearch()
        compose.onNodeWithTag("playlist-search-field").performTextInput("space")
        frame(250)
        compose.runOnUiThread { activity.get().onBackPressedDispatcher.onBackPressed() }
        frame(250)
        compose.onNodeWithTag("playlist-search-dimming").assertDoesNotExist()
        compose.onNodeWithTag("playlist-search-field").assertTextContains("space")
        compose.runOnUiThread { activity.get().onBackPressedDispatcher.onBackPressed() }
        frame(350)
        compose.onNodeWithTag("playlist-chooser").assertExists()
        compose.onNodeWithTag("enqueue-row-1").assertExists()
    }

    @Test fun `closed playlist search does not clear focus when another destination opens`() {
        var cleared = 0
        compose.runOnUiThread {
            activity.get().setContent {
                MaterialTheme {
                    val actual = LocalFocusManager.current
                    val observed = remember(actual) {
                        object : FocusManager {
                            override fun clearFocus(force: Boolean) { cleared++; actual.clearFocus(force) }
                            override fun moveFocus(focusDirection: FocusDirection): Boolean = actual.moveFocus(focusDirection)
                        }
                    }
                    val model = remember { PlayerViewModel(application) }
                    CompositionLocalProvider(LocalFocusManager provides observed) { ProtracktorApp(viewModel = model) }
                }
            }
        }
        frame(350)
        val before = cleared
        compose.onNodeWithText(application.getString(R.string.settings_title)).performClick()
        frame(350)
        assertEquals("An inactive playlist search must leave the destination's focus alone", before, cleared)
        compose.onNodeWithContentDescription(application.getString(R.string.action_back)).performClick()
        frame(350)
        openSearch()
        compose.onNodeWithTag("playlist-search-field").performImeAction()
        frame(250)
        assertTrue("The observer must still see actual search focus dismissal", cleared > before)
    }
}
