// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.net.CatalogueEntry
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.LocalTree
import com.przunk.protracktor.player.TrackRef
import org.junit.Assert.assertEquals
import org.robolectric.Shadows.shadowOf
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

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(5_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(50)
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The index one test writes is on disk, and the next test's playlist would offer it. */
    @org.junit.After fun forgetIndex() {
        kotlinx.coroutines.runBlocking { CatalogueStore(application).clearIndex(Modland.id) }
    }

    @Test fun `in a format, the authors that match and below them the tunes that do`() {
        val modland = CatalogueSummary(id = Modland.id, displayName = "Modland", trackCount = 2, indexedAt = 1L)
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame()
        // Indexed once Browse is open: an indexed archive adds a second Browse offer to the playlist.
        kotlinx.coroutines.runBlocking {
            CatalogueStore(application).replaceIndex(Modland, listOf(
                CatalogueEntry("Protracker/Nork/funky.mod", "Protracker", "Nork", "funky.mod", 42),
                CatalogueEntry("Protracker/Nooze/nothing.mod", "Protracker", "Nooze", "nothing.mod", 42),
            ), "test")
        }
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.ONLINE, openCatalogue = modland,
                openFormat = "Protracker", openAuthor = null, groups = listOf(CatalogueGroup("Nork", 1),
                    CatalogueGroup("Nooze", 1)), tracks = emptyList(), loading = false)
        }
        frame(300)
        compose.onNodeWithTag("browse-search-action").performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-field").performTextInput("funky")
        frame(300)
        waitFor(hasTestTag("browse-filter-below"))
        compose.onNodeWithText(application.getString(R.string.browse_filter_below)).assertExists()
        compose.onNodeWithText("Nork").assertDoesNotExist()
        compose.onNodeWithTag("below-row-0").assert(hasAnyDescendant(hasText("funky", substring = true)))
        compose.onNodeWithTag("below-row-1").assertDoesNotExist()
        compose.onNodeWithTag("browse-filter-no-matches").assertDoesNotExist()
        // No tune of the level's own, so no header above the folders: the count arrives with the
        // tunes, in their heading, and nothing already on screen moves down for it.
        val one = application.resources.getQuantityString(R.plurals.track_count, 1, 1)
        compose.onNodeWithTag("browse-tracks-header").assertDoesNotExist()
        compose.onNode(hasText(one) and hasAnyAncestor(hasTestTag("browse-filter-below"))).assertExists()
        // Ticking starts in that heading too: the one tune is all of them, so it offers none.
        compose.onNodeWithTag("below-row-0").performTouchInput { longClick() }
        frame(300)
        compose.onNode(hasText(application.getString(R.string.browse_select_none))
            and hasAnyAncestor(hasTestTag("browse-filter-below"))).assertExists()
        back()

        // An author's name finds the author and, below, the tunes filed under it.
        compose.onNodeWithTag("browse-filter-field").performTextClearance()
        compose.onNodeWithTag("browse-filter-field").performTextInput("nork")
        frame(300)
        waitFor(hasTestTag("below-row-0"))
        compose.onNodeWithText("Nork").assertExists()
        compose.onNodeWithText("Nooze").assertDoesNotExist()
        compose.onNodeWithTag("below-row-0").assert(hasAnyDescendant(hasText("funky", substring = true)))

        // Cleared, the list is the level again, with nothing below.
        compose.onNodeWithTag("browse-filter-field").performTextClearance()
        frame(300)
        compose.onNodeWithTag("browse-filter-below").assertDoesNotExist()
        compose.onNodeWithText("Nooze").assertExists()
    }

    @Test fun `in a folder, the tunes in its subfolders that match, which play as found`() {
        fun tune(name: String) = TrackRef("content://navigation.test/$name", name, fileName = name)
        val all = listOf(
            "Music" to tune("mod here.mod"),
            "Music" to tune("quiet.xm"),
            "Music/Atari" to tune("mod one.sap"),
            "Music/Atari/good" to tune("deep mod.sap"),
            "Music/Atari/good" to tune("other.sap"),
        )
        val level = LocalTree.level(all, "Music")
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame()
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.LOCAL,
                openFolder = GrantedFolder(uri = "content://navigation.test/tree", displayName = "Music"),
                folderAll = all, folderRoot = "Music", folderAt = "Music", subfolders = level.folders,
                tracks = level.files, loading = false)
        }
        frame(300)
        compose.onNodeWithTag("browse-search-action").performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-field").performTextInput("mod")
        frame(300)
        waitFor(hasTestTag("below-row-1"))
        compose.onNodeWithText("quiet.xm").assertDoesNotExist()
        compose.onNodeWithTag("enqueue-row-0").assert(hasAnyDescendant(hasText("mod here", substring = true)))
        compose.onNodeWithTag("below-row-0").assert(hasAnyDescendant(hasText("mod one", substring = true)))
        compose.onNodeWithTag("below-row-1").assert(hasAnyDescendant(hasText("deep mod", substring = true)))
        compose.onNodeWithText("other", substring = true).assertDoesNotExist()
        // The level's own tune is counted above, the two below in their heading.
        compose.onNodeWithText(application.resources.getQuantityString(R.plurals.track_count, 1, 1)).assertExists()
        compose.onNode(hasText(application.resources.getQuantityString(R.plurals.track_count, 2, 2))
            and hasAnyAncestor(hasTestTag("browse-filter-below"))).assertExists()

        // Select all takes every tune on screen, the level's and those below.
        compose.onNodeWithTag("below-row-0").performTouchInput { longClick() }
        frame(300)
        compose.onNodeWithText(application.getString(R.string.browse_select_all)).performClick()
        frame(300)
        compose.onNodeWithText(application.resources.getQuantityString(R.plurals.browse_selected, 3, 3)).assertExists()
        back()

        // A tune below plays the tunes found, from it, not the folder on screen.
        compose.onNodeWithTag("below-row-1").performClick()
        // The file cannot be fetched here, so no queue is kept; what was asked to play is still
        // the tune tapped, where the folder's second row would be quiet.xm.
        compose.waitUntil(5_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); player.value.current != null }
        assertEquals(all[3].second, player.value.current)
    }

    @Test fun `tunes found below are read from History like the level's own rows`() {
        val heard = TrackRef("https://modland.com/pub/modules/Protracker/Lotus/heard.mod", "heard.mod", fileName = "heard.mod")
        kotlinx.coroutines.runBlocking {
            val history = com.przunk.protracktor.data.HistoryStore(application)
            history.record(heard.id, "Lotus Heard", "Modland", heard.fileName, "Lotus", 42)
            history.qualify(heard.id)
        }
        compose.runOnIdle {
            browse.value = browse.value.copy(below = com.przunk.protracktor.player.TunesFound("any", "lotus", listOf(heard)))
        }
        compose.waitUntil(5_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            heard.id in player.value.listenedIds
        }
        compose.runOnIdle { assertEquals("Lotus Heard", player.value.learnedTitles[heard.id]) }
    }

    @Test fun `back from a folder found by Search leaves the list where it was`() {
        val modland = CatalogueSummary(id = Modland.id, displayName = "Modland", trackCount = 3, indexedAt = 1L)
        compose.onNodeWithText(application.getString(R.string.action_browse)).performClick()
        frame()
        kotlinx.coroutines.runBlocking {
            // Enough tunes below that the list is longer than the screen and could be moved.
            CatalogueStore(application).replaceIndex(Modland, listOf("Mona", "Monty", "Monty Two").flatMap { author ->
                (1..6).map { CatalogueEntry("Protracker/$author/$author $it.mod", "Protracker", author, "$author $it.mod", 42) }
            }, "test")
        }
        compose.runOnIdle {
            browse.value = browse.value.copy(domain = BrowseDomain.ONLINE, openCatalogue = modland,
                openFormat = "Protracker", openAuthor = null, groups = listOf("Mona", "Monty", "Monty Two").map {
                    CatalogueGroup(it, 1) }, tracks = emptyList(), loading = false)
        }
        frame(300)
        compose.onNodeWithTag("browse-search-action").performClick()
        frame(300)
        compose.onNodeWithTag("browse-filter-field").performTextInput("mon")
        frame(300)
        waitFor(hasTestTag("below-row-2"))
        compose.onNodeWithText("Mona").assertIsDisplayed()
        compose.onNodeWithText("Monty").performClick()
        compose.waitUntil(5_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeBy(50)
            browse.value.openAuthor == "Monty" && !browse.value.loading
        }
        frame(300)
        back()
        compose.waitUntil(5_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeBy(50)
            browse.value.openAuthor == null && !browse.value.loading
        }
        frame(600)
        // Monty was the second row: put at the top, Mona would have gone off the screen.
        compose.onNodeWithText("Mona").assertIsDisplayed()
        compose.onNodeWithTag("browse-filter-field").assertTextContains("mon")
    }
}
