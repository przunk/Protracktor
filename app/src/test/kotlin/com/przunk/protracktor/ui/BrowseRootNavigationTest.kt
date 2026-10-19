// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.ProtracktorDatabase
import com.przunk.protracktor.player.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Real shell/VM/controller navigation with a preset playback state, not native audio playback. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class BrowseRootNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var controller: PlaybackController
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val player get() = ReflectionHelpers.getField<MutableStateFlow<PlayerUiState>>(controller, "_state")
    private val browse get() = ReflectionHelpers.getField<MutableStateFlow<BrowseState>>(controller, "_browse")
    private val tune = TrackRef("content://navigation.test/current.mod", "Current tune", author = "Known author")

    @Before fun start() {
        Appearance.selectCacheAhead(application, CacheAhead.OFF)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controller = PlaybackController.get(application)
        activity.get().setContent { MaterialTheme {
            ProtracktorApp(viewModel = PlayerViewModel(application))
        } }
        val view = activity.get().window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 720, 1600)
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            controller.state.value.restored && controller.browse.value.knowsWhatIsHeld
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @After fun stop() {
        if (::controller.isInitialized) {
            ReflectionHelpers.getField<CoroutineScope>(controller, "scope").cancel()
            controller.release()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "backgroundWork").close()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "scanWork").close()
            ReflectionHelpers.setStaticField(PlaybackController::class.java, "instance", null)
        }
        if (::activity.isInitialized) activity.close()
        ProtracktorDatabase.of(application).close()
    }

    private fun frame() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

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
            compose.onNodeWithText("Random").performClick()
            frame()
            compose.onNodeWithText("Online catalogues").assertDoesNotExist()
            compose.runOnIdle { assertPlaybackUnchanged(before) }
            back(toolbar = it % 2 == 0)
            rootIsDisplayed()
            compose.runOnIdle { assertPlaybackUnchanged(before) }
            back(toolbar = it % 2 != 0)
        }
    }
}
