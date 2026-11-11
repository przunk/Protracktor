// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.player.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class BrowseRootNavigationTest : PlaybackShellTest() {
    @Test fun `Browse opens root after leaving playing History without replacing playback`() {
        reopen(BrowseState(domain = BrowseDomain.HISTORY, historyTotal = 1), playing = true, backs = 2)
    }

    @Test fun `Browse opens root after leaving paused Search without restoring its query`() {
        reopen(BrowseState(domain = BrowseDomain.SEARCH, query = "old query", searched = true),
            playing = false, backs = 2)
    }

    @Test fun `Browse opens root after walking out of an online author`() {
        reopen(authorFolder(), playing = true, backs = 5)
    }

    @Test fun `Browse opens root after walking out of a paused local folder`() {
        reopen(BrowseState(domain = BrowseDomain.LOCAL,
            openFolder = GrantedFolder("content://navigation.test/tree/root", "Local folder"),
            folderRoot = "", folderAt = ""), playing = false, backs = 3)
    }

    private fun authorFolder() = BrowseState(domain = BrowseDomain.ONLINE,
        openCatalogue = CatalogueSummary("modland", "Modland", 1, 1L),
        openFormat = "Protracker", openAuthor = "Known author")

    private fun seed(snapshot: BrowseState, source: QueueSource, playing: Boolean) {
        compose.runOnIdle {
            // Preset the state produced by selecting a row; never decode audio or fetch a file.
            ReflectionHelpers.setField(controller, "sessionBrowse", snapshot)
            player.value = player.value.copy(
                queue = PlayQueue(listOf(tune.copy(id = "content://navigation.test/playlist.mod"))),
                playback = ActiveQueue(source, PlayQueue(listOf(tune,
                    tune.copy(id = "content://navigation.test/next.mod", title = "Next tune")),
                    repeat = RepeatMode.PLAYLIST, shuffle = true).startAt(0)),
                playing = playing, positionSeconds = 12.5, durationSeconds = 123.0,
                metadata = mapOf("title" to "Current tune"),
            )
            browse.value = snapshot
        }
        frame()
    }

    private fun assertPlaybackUnchanged(before: PlayerUiState) {
        assertEquals(before.activePlayback, player.value.activePlayback)
        assertEquals(before.queue, player.value.queue)
        assertEquals(before.playing, player.value.playing)
        assertEquals(before.positionSeconds, player.value.positionSeconds, 0.0)
        assertEquals(before.durationSeconds, player.value.durationSeconds, 0.0)
        assertEquals(before.metadata, player.value.metadata)
        assertEquals(before.randomPicks, player.value.randomPicks)
        assertEquals(before.randomIndex, player.value.randomIndex)
    }

    private fun rootIsDisplayed() {
        compose.runOnIdle {
            assertEquals(BrowseDomain.ROOT, browse.value.domain)
            assertNull(browse.value.openCatalogue)
            assertNull(browse.value.openFolder)
            assertNull(browse.value.openAuthor)
            assertEquals("", browse.value.query)
        }
        compose.onNodeWithText("Online catalogues").assertIsDisplayed()
    }

    private fun back(toolbar: Boolean) {
        if (toolbar) compose.onNodeWithContentDescription("Back").performClick()
        else compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        frame()
    }

    private fun reopen(route: BrowseState, playing: Boolean, backs: Int) {
        compose.onNodeWithText("Browse").performClick()
        frame()
        val snapshot = route.copy(tracks = listOf(tune), knowsWhatIsHeld = true)
        seed(snapshot, QueueSource.Collection(requireNotNull(SessionSource.of(snapshot))), playing)
        val before = player.value
        repeat(backs) { back(toolbar = it % 2 == 0) }
        compose.runOnIdle { assertPlaybackUnchanged(before) }
        repeat(2) {
            // Exercise the actual shell action, not a standalone callback wired only by the test.
            compose.onNodeWithText("Browse").performClick()
            frame()
            rootIsDisplayed()
            compose.runOnIdle { assertPlaybackUnchanged(before) }
            back(toolbar = it % 2 == 0)
        }
    }

    @Test fun `explicit Browse destination event still opens an author and Back undoes the jump`() {
        val destination = authorFolder().copy(arrivedByJump = true, tracks = listOf(tune), knowsWhatIsHeld = true)
        seed(destination, QueueSource.Collection(SessionSource.History), playing = true)
        val before = player.value
        // The producer's catalogue lookup is outside this test; exercise its shell event contract.
        compose.runOnIdle {
            assertTrue(ReflectionHelpers.getField<MutableSharedFlow<Unit>>(controller, "_showBrowse").tryEmit(Unit))
        }
        frame()
        compose.onNodeWithText("Add music").assertIsDisplayed()
        compose.onNodeWithText("Online catalogues").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(destination.domain, browse.value.domain)
            assertEquals(destination.openCatalogue, browse.value.openCatalogue)
            assertEquals(destination.openFormat, browse.value.openFormat)
            assertEquals(destination.openAuthor, browse.value.openAuthor)
            assertEquals(destination.tracks, browse.value.tracks)
            assertTrue(browse.value.arrivedByJump)
            assertPlaybackUnchanged(before)
        }
        back(toolbar = true)
        compose.onNodeWithText("Browse").assertIsDisplayed()
        compose.onNodeWithText("Browse").performClick()
        frame()
        rootIsDisplayed()
        compose.runOnIdle { assertPlaybackUnchanged(before) }
    }

    @Test fun `Random Back and repeated Browse preserve the remembered playing record`() {
        randomNavigation(playing = true)
    }

    @Test fun `Random Back and repeated Browse do not resume a paused record`() {
        randomNavigation(playing = false)
    }

    private fun randomNavigation(playing: Boolean) {
        seed(BrowseState(knowsWhatIsHeld = true), QueueSource.Random(1), playing)
        compose.runOnIdle {
            ReflectionHelpers.setField(controller, "randomSession", 1L)
            player.value = player.value.copy(randomPicks = listOf(tune), randomIndex = 0)
        }
        frame()
        val before = player.value
        repeat(2) {
            compose.onNodeWithText("Browse").performClick()
            frame()
            rootIsDisplayed()
            // Browse's own Random row: the navigation bar has a Random tab too (redesign E1).
            compose.onNode(androidx.compose.ui.test.hasText("Random") and
                !androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("shell-navigation"))).performClick()
            frame()
            compose.onNodeWithText("Online catalogues").assertDoesNotExist()
            compose.runOnIdle { assertPlaybackUnchanged(before) }
            back(toolbar = it % 2 == 0)
            rootIsDisplayed()
            compose.runOnIdle { assertPlaybackUnchanged(before) }
            back(toolbar = it % 2 != 0)
        }
    }

    /** C138: leaving an author reached by a jump puts Browse back where it was before the jump. */
    @Test fun `leaving an author reached from Random returns Browse to where it was`() {
        val modland = com.przunk.protracktor.net.Modland
        kotlinx.coroutines.runBlocking {
            com.przunk.protracktor.data.CatalogueStore(application).replaceIndex(modland, listOf(
                com.przunk.protracktor.net.CatalogueEntry("Protracker/Nork/funky.mod", "Protracker", "Nork", "funky.mod", 42)), "test")
        }
        try {
            // Browse at its start, as when Random was opened from it.
            compose.runOnIdle { browse.value = BrowseState(domain = BrowseDomain.ROOT, knowsWhatIsHeld = true) }
            val nork = TrackRef(modland.urlFor("Protracker/Nork/funky.mod"), "funky.mod", author = "Nork")
            compose.runOnIdle { controller.showNeighboursOf(nork) }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                browse.value.openAuthor == "Nork" && !browse.value.loading
            }
            compose.runOnIdle { assertTrue(browse.value.arrivedByJump) }
            // Back from the author leaves Browse (for Random, underneath) ...
            compose.runOnIdle { assertFalse(controller.browseBack()) }
            // ... and Browse is where it was, so Random's Back opens its start, not the author.
            compose.runOnIdle {
                assertEquals(BrowseDomain.ROOT, browse.value.domain)
                assertNull(browse.value.openAuthor)
                assertNull(browse.value.openCatalogue)
                assertFalse(browse.value.arrivedByJump)
            }
            // A second Back from there leaves, rather than walking up the author's archive.
            compose.runOnIdle { assertFalse(controller.browseBack()) }
        } finally {
            kotlinx.coroutines.runBlocking { com.przunk.protracktor.data.CatalogueStore(application).clearIndex(modland.id) }
        }
    }
}
