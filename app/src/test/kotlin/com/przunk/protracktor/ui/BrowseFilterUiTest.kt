// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Browse's Search, in the real shell: the bar's button and field, the level's list, and Back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class BrowseFilterUiTest : PlaybackShellTest() {
    private val amp = CatalogueSummary(id = "amp", displayName = "AMP", trackCount = 9, indexedAt = 1L)
    private val authors = listOf("Nork", "Nooze", "4-Mat").map { CatalogueGroup(it, 3) }
    private val tunes = listOf("MOD.Nk-SaveTheFunky", "MOD.Nk-Other", "MOD.Nk-Third").map { title ->
        TrackRef("amp://N/Nork/$title.gz", title, author = "Nork", fileName = "$title.gz")
    }
    private val title get() = application.getString(R.string.browse_title)

    private fun authorsOfMod() {
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame()
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.ONLINE, openCatalogue = amp, openFormat = "MOD",
                openAuthor = null, groups = authors, tracks = emptyList(), loading = false)
        }
        frame(300)
    }
    private fun back() {
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        frame(300)
    }

    @Test fun `Search narrows the authors, and Back drops the keyboard, then Search, then nothing else`() {
        authorsOfMod()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithTag("browse-search-action")
            .assert(hasText(application.getString(R.string.playlist_search_action), substring = false).or(
                hasAnyDescendant(hasText(application.getString(R.string.playlist_search_action)))))
            .performClick()
        frame(300)
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithTag("browse-search-action").assertDoesNotExist()
        compose.onNodeWithTag("browse-filter-field").assertIsFocused().performTextInput("NORK")
        frame(300)
        compose.onNodeWithText("Nork").assertExists()
        compose.onNodeWithText("Nooze").assertDoesNotExist()
        compose.onNodeWithText("4-Mat").assertDoesNotExist()

        back()
        compose.onNodeWithTag("browse-filter-field").assertIsNotFocused().assertTextContains("NORK")
        compose.onNodeWithText("Nooze").assertDoesNotExist()
        back()
        compose.onNodeWithTag("browse-filter-field").assertDoesNotExist()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText("Nooze").assertExists()
        compose.runOnIdle { assertEquals("MOD", browse.value.openFormat) }
    }

    @Test fun `each level keeps its own Search while Browse is open, and leaving Browse forgets it`() {
        authorsOfMod()
        compose.onNodeWithTag("browse-search-action").performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-field").performTextInput("no")
        frame(300)
        compose.onNodeWithText("4-Mat").assertDoesNotExist()

        // Into Nork: a level of its own, with Search closed.
        compose.runOnIdle { browse.value = browse.value.copy(openAuthor = "Nork", groups = emptyList(), tracks = tunes) }
        frame(300)
        compose.onNodeWithTag("browse-filter-field").assertDoesNotExist()
        compose.onNodeWithTag("browse-search-action").assertExists()
        compose.onNodeWithText("MOD.Nk-Other").assertExists()

        // Back out: the authors are still narrowed.
        compose.runOnIdle { browse.value = browse.value.copy(openAuthor = null, groups = authors, tracks = emptyList()) }
        frame(300)
        compose.onNodeWithTag("browse-filter-field").assertTextContains("no")
        compose.onNodeWithText("4-Mat").assertDoesNotExist()
        compose.onNodeWithText("Nooze").assertExists()

        // The way out of Browse, and back in: a fresh start.
        compose.onNodeWithText(application.getString(R.string.action_to_playlist)).performClick()
        frame(300)
        authorsOfMod()
        compose.onNodeWithTag("browse-filter-field").assertDoesNotExist()
        compose.onNodeWithText("4-Mat").assertExists()
    }

    @Test fun `an author's tunes narrow too, and nothing matching says so`() {
        authorsOfMod()
        compose.runOnIdle { browse.value = browse.value.copy(openAuthor = "Nork", groups = emptyList(), tracks = tunes) }
        frame(300)
        compose.onNodeWithTag("browse-search-action").performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-field").performTextInput("funky")
        frame(300)
        compose.onNodeWithText("MOD.Nk-SaveTheFunky").assertExists()
        compose.onNodeWithText("MOD.Nk-Other").assertDoesNotExist()
        compose.onNodeWithText(application.resources.getQuantityString(R.plurals.track_count, 1, 1)).assertExists()

        compose.onNodeWithTag("browse-filter-field").performTextClearance()
        compose.onNodeWithTag("browse-filter-field").performTextInput("zzz")
        frame(300)
        compose.onNodeWithTag("browse-filter-no-matches").assertExists()
        // The bar's arrow takes the same step as Back: Search first, the level stays.
        compose.onNodeWithContentDescription(application.getString(R.string.action_back)).performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-no-matches").assertDoesNotExist()
        compose.onNodeWithText("MOD.Nk-Other").assertExists()
        compose.runOnIdle { assertEquals("Nork", browse.value.openAuthor) }
    }

    @Test fun `no Search where there is no list to narrow`() {
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame(300)
        compose.runOnIdle { browse.value = browse.value.copy(domain = BrowseDomain.ROOT) }
        frame(300)
        compose.onNodeWithTag("browse-search-action").assertDoesNotExist()
        compose.runOnIdle { browse.value = browse.value.copy(domain = BrowseDomain.ONLINE, openCatalogue = null) }
        frame(300)
        compose.onNodeWithTag("browse-search-action").assertDoesNotExist()
        compose.runOnIdle { browse.value = browse.value.copy(domain = BrowseDomain.HISTORY) }
        frame(300)
        compose.onNodeWithTag("browse-search-action").assertDoesNotExist()
    }
}
