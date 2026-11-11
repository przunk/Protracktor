// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.BrowseDomain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Redesign E1: the navigation bar and More, in the real shell. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class ShellNavigationUiTest : PlaybackShellTest() {
    // Search reads the catalogues, which ask the native decoders what they play; the host has none.
    @org.junit.Before fun hostDecoders() {
        val catalogues = ReflectionHelpers.getField<com.przunk.protracktor.player.CatalogueBrowser>(controller, "catalogueBrowser")
        ReflectionHelpers.setField(catalogues, "backends", { "host-fixture" })
    }

    private fun tab(name: String) = compose.onNodeWithTag("nav-$name")
    private fun open(name: String) { tab(name).performClick(); frame(500) }
    private fun marked(name: String) = tab(name).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    private fun back() { compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }; frame(500) }
    private fun more(label: Int) = compose.onNode(hasText(application.getString(label)) and hasAnyAncestor(hasTestTag("more-sheet")))
    private fun atPlaylist() { compose.onNodeWithTag("playlist-chooser").assertIsDisplayed(); marked("playlist") }

    @Test fun `each tab opens its place and marks itself, and Back from it returns to the playlist`() {
        atPlaylist()
        open("browse")
        compose.onNodeWithText("Online catalogues").assertIsDisplayed()
        marked("browse")
        back()
        atPlaylist()

        open("search")
        compose.runOnIdle { assertEquals(BrowseDomain.SEARCH, browse.value.domain) }
        marked("search")
        // The bar above says where this is: Search, not Browse's own title.
        compose.onNode(hasText(application.getString(R.string.nav_search)) and !hasAnyAncestor(hasTestTag("shell-navigation")))
            .assertExists()
        compose.onNodeWithText(application.getString(R.string.browse_title)).assertDoesNotExist()
        // Search's top is the tab's top: Back leaves, rather than walking to Browse's start.
        back()
        atPlaylist()
        compose.onNodeWithText("Online catalogues").assertDoesNotExist()

        open("random")
        marked("random")
        compose.onNodeWithText("Double-tap the dice to reroll the list").assertExists()
        back()
        atPlaylist()
    }

    @Test fun `a tab replaces where you were, and Playlist leaves everything`() {
        open("browse")
        open("random")
        marked("random")
        open("playlist")
        atPlaylist()
        // Nothing left underneath: Back from the playlist does not reopen Random or Browse.
        back()
        compose.onNodeWithText("Double-tap the dice to reroll the list").assertDoesNotExist()
        compose.onNodeWithText("Online catalogues").assertDoesNotExist()
    }

    @Test fun `More holds Alarms, WEB and Settings, and opens again after one is used`() {
        open("more")
        // History and Saved places are Browse's, not More's.
        more(R.string.domain_history_title).assertDoesNotExist()
        more(R.string.saved_places_title).assertDoesNotExist()
        more(R.string.settings_title).performClick()
        frame(500)
        marked("more")
        back()
        atPlaylist()
        open("more")
        compose.onNodeWithTag("more-sheet").assertExists()
        more(R.string.settings_title).performClick()
        frame(500)
        back()
        atPlaylist()
    }

    @Test fun `History opened from Browse is Browse's, and Back returns to Browse's start`() {
        open("browse")
        compose.onNode(hasText(application.getString(R.string.domain_history_title)) and !hasAnyAncestor(hasTestTag("shell-navigation")))
            .performClick()
        frame(500)
        compose.runOnIdle { assertEquals(BrowseDomain.HISTORY, browse.value.domain) }
        marked("browse")
        back()
        compose.onNodeWithText("Online catalogues").assertIsDisplayed()
        marked("browse")
    }

    @Test fun `the top row has no Browse, WEB or Settings any more`() {
        listOf(R.string.action_send_to_browser, R.string.settings_title).forEach { id ->
            compose.onNode(hasText(application.getString(id)) and !hasAnyAncestor(hasTestTag("more-sheet")))
                .assertDoesNotExist()
        }
        // Browse is only the tab.
        compose.onAllNodesWithText(application.getString(R.string.action_browse)).assertCountEquals(1)
        compose.onNode(hasText(application.getString(R.string.action_browse)) and hasAnyAncestor(hasTestTag("shell-navigation")))
            .assertExists()
    }

    @Test fun `WEB in More sends the playlist, here saying there is nothing to send`() {
        open("more")
        more(R.string.action_send_to_browser).performClick()
        frame(500)
        compose.onNodeWithTag("more-sheet").assertDoesNotExist()
        compose.onNodeWithText(application.getString(R.string.notice_nothing_to_send)).assertExists()
    }

    @Test fun `holding WEB in More opens the scanner, which hides the bar, and Back closes it`() {
        open("more")
        more(R.string.action_send_to_browser).performTouchInput { longClick() }
        frame(800)
        compose.onNodeWithTag("shell-navigation").assertDoesNotExist()
        back()
        compose.onNodeWithTag("shell-navigation").assertExists()
        atPlaylist()
    }
}
