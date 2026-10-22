// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import android.app.Application
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.R
import com.przunk.protracktor.player.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class QueueSourceNavigationTest : PlaybackShellTest() {
    private fun seed(source: QueueSource, snapshot: BrowseState? = null) {
        compose.runOnIdle {
            ReflectionHelpers.setField(controller, "sessionBrowse", snapshot)
            val active = ActiveQueue(source, PlayQueue(listOf(tune)).startAt(0))
            player.value = player.value.withPlayback(active).copy(
                playing = false, positionSeconds = 12.5, durationSeconds = 123.0,
                metadata = mapOf("title" to tune.title),
                schedule = PlaybackSchedule().contextSelected(active, SchedulePlacement.REPLACE)
                    .enqueue(tune.copy(id = "manual", title = "Waiting manual request")),
            )
        }
        frame()
        compose.onNodeWithTag("queue-toggle").performClick()
        frame(600)
    }

    private fun unchanged(before: PlayerUiState) {
        assertEquals(before.activePlayback, player.value.activePlayback)
        assertEquals(before.schedule, player.value.schedule)
        assertEquals(before.playing, player.value.playing)
        assertEquals(before.positionSeconds, player.value.positionSeconds, 0.0)
        assertEquals(before.durationSeconds, player.value.durationSeconds, 0.0)
        assertEquals(before.metadata, player.value.metadata)
        assertEquals(before.randomPicks, player.value.randomPicks)
    }

    private fun clickSource(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("playback-panel")))
            .performTouchInput { click() }
        frame(600)
        compose.onNodeWithTag("playback-panel").assertDoesNotExist()
    }

    @Test fun `Random source opens its remembered list without playing or rerolling and Back opens Browse`() {
        compose.runOnIdle {
            ReflectionHelpers.setField(controller, "randomSession", 7L)
            browse.value = browse.value.copy(domain = BrowseDomain.SEARCH, query = "unrelated")
            player.value = player.value.copy(randomPicks = listOf(tune), randomIndex = 0)
        }
        seed(QueueSource.Random(7L))
        val before = player.value
        clickSource("Random")
        compose.onNodeWithText("Double-tap the dice to reroll the list").assertIsDisplayed()
        compose.runOnIdle { unchanged(before) }
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        frame(400)
        compose.onNodeWithText("Online catalogues").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(BrowseDomain.ROOT, browse.value.domain)
            unchanged(before)
        }
    }

    private fun collection(snapshot: BrowseState) {
        val saved = snapshot.copy(tracks = listOf(tune), knowsWhatIsHeld = true)
        seed(QueueSource.Collection(requireNotNull(SessionSource.of(saved))), saved)
        val before = player.value
        val label = when (val origin = requireNotNull(SessionSource.of(saved))) {
            SessionSource.History -> "History"
            is SessionSource.Search -> "Search: ${origin.query}"
            is SessionSource.Folder -> origin.path
        }
        clickSource(label)
        compose.runOnIdle {
            assertEquals(saved.domain, browse.value.domain)
            assertEquals(saved.tracks, browse.value.tracks)
            assertEquals(saved.query, browse.value.query)
            assertEquals(saved.searchScope, browse.value.searchScope)
            assertEquals(saved.openAuthor, browse.value.openAuthor)
            assertEquals(saved.openFormat, browse.value.openFormat)
            assertEquals(saved.openFolder, browse.value.openFolder)
            assertEquals(saved.folderAt, browse.value.folderAt)
            assertEquals(saved.historyQuery, browse.value.historyQuery)
            assertEquals(saved.historyPage, browse.value.historyPage)
            unchanged(before)
        }
    }

    @Test fun `Search source restores the captured query scope and results`() {
        collection(BrowseState(domain = BrowseDomain.SEARCH, query = "Current",
            searchScope = SearchScope.Online(setOf("modland")), searched = true))
    }

    @Test fun `History source restores its captured filter and page without recording playback`() {
        collection(BrowseState(domain = BrowseDomain.HISTORY, historyQuery = "Current", historyPage = 1, historyTotal = 201))
    }

    @Test fun `online source restores the exact catalogue format and author`() {
        collection(BrowseState(domain = BrowseDomain.ONLINE,
            openCatalogue = CatalogueSummary("modland", "Modland", 1, 1L),
            openFormat = "Protracker", openAuthor = "Known author"))
    }

    @Test fun `local source restores the selected subfolder not just Local`() {
        collection(BrowseState(domain = BrowseDomain.LOCAL,
            openFolder = GrantedFolder("content://navigation.test/tree/root", "Local music"),
            folderRoot = "Music", folderAt = "Music/Author/Album", folderAll = listOf("Music/Author/Album" to tune)))
    }

    private fun otherPlaylist(dirty: Boolean): Long {
        val original = player.value.activePlaylistId
        val store = ReflectionHelpers.getField<LibraryStore>(controller, "store")
        val other = runBlocking {
            store.replaceTracks(original, listOf(tune))
            store.createPlaylist("Other playlist").also { store.replaceTracks(it, listOf(tune.copy(id = "other", title = "Other tune"))) }
        }
        compose.runOnIdle { controller.switchToPlaylist(other) }
        compose.waitUntil(10_000) { frame(); player.value.activePlaylistId == other }
        seed(QueueSource.Playlist(original))
        if (dirty) compose.runOnIdle {
            player.value = player.value.copy(dirty = true,
                queue = player.value.queue.copy(tracks = listOf(tune.copy(id = "draft", title = "Unsaved edit", fileName = "draft.mod"))))
        }
        frame()
        return original
    }

    private fun sourceTap() {
        compose.onNodeWithTag("queue-source").performTouchInput { click() }
        frame(600)
    }

    @Test fun `playlist source opens the actual original playlist while keeping its playback context`() {
        val original = otherPlaylist(dirty = false)
        val before = player.value
        sourceTap()
        compose.waitUntil(10_000) { frame(); player.value.activePlaylistId == original }
        frame(600)
        compose.onNodeWithTag("playback-panel").assertDoesNotExist()
        compose.runOnIdle { unchanged(before) }
    }

    @Test fun `unsaved playlist switch Cancel keeps Queue and the selected draft`() {
        otherPlaylist(dirty = true)
        val before = player.value
        sourceTap()
        compose.onNodeWithText(application.getString(R.string.playlist_unsaved_title)).assertIsDisplayed()
        // Android delivers Back to the modal window before the activity underneath it.
        compose.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        frame(500)
        compose.onNodeWithTag("playback-panel").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(before.activePlaylistId, player.value.activePlaylistId)
            assertEquals(before.queue, player.value.queue)
            assertTrue(player.value.dirty)
            unchanged(before)
        }
    }

    @Test fun `unsaved playlist source Save persists the selected draft before returning`() {
        val original = otherPlaylist(dirty = true)
        val editedId = player.value.activePlaylistId
        val edited = player.value.queue.tracks
        val before = player.value
        sourceTap()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { frame(); player.value.activePlaylistId == original }
        frame(600)
        compose.onNodeWithTag("playback-panel").assertDoesNotExist()
        val store = ReflectionHelpers.getField<LibraryStore>(controller, "store")
        assertEquals(edited, runBlocking { store.tracksIn(editedId) })
        compose.runOnIdle { unchanged(before) }
    }

    @Test fun `unsaved playlist source Discard does not save the abandoned draft`() {
        val original = otherPlaylist(dirty = true)
        val editedId = player.value.activePlaylistId
        val before = player.value
        val store = ReflectionHelpers.getField<LibraryStore>(controller, "store")
        val stored = runBlocking { store.tracksIn(editedId) }
        sourceTap()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(10_000) { frame(); player.value.activePlaylistId == original }
        frame(600)
        assertEquals(stored, runBlocking { store.tracksIn(editedId) })
        compose.runOnIdle { unchanged(before) }
    }

    @Test fun `failed source Save keeps the draft and Queue and explains the failure`() {
        otherPlaylist(dirty = true)
        val before = player.value
        val session = ReflectionHelpers.getField<PlaylistSession>(controller, "playlistSession")
        val store = ReflectionHelpers.getField<PlaylistSession.Store>(session, "store")
        ReflectionHelpers.setField(session, "store", object : PlaylistSession.Store by store {
            override suspend fun replaceTracks(id: Long, tracks: List<TrackRef>) {
                throw java.io.IOException("Controlled persistence failure")
            }
        })
        sourceTap()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { frame(); player.value.message?.text == application.getString(R.string.queue_source_save_failed) }
        compose.onNodeWithTag("playback-panel").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(before.activePlaylistId, player.value.activePlaylistId)
            assertEquals(before.queue, player.value.queue)
            assertTrue(player.value.dirty)
            unchanged(before)
        }
        ReflectionHelpers.setField(session, "store", store)
    }

    @Test fun `successful Save after playback source changes neither navigates nor reports a false failure`() {
        otherPlaylist(dirty = true)
        val selected = player.value.activePlaylistId
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val session = ReflectionHelpers.getField<PlaylistSession>(controller, "playlistSession")
        val store = ReflectionHelpers.getField<PlaylistSession.Store>(session, "store")
        ReflectionHelpers.setField(session, "store", object : PlaylistSession.Store by store {
            override suspend fun replaceTracks(id: Long, tracks: List<TrackRef>) {
                entered.complete(Unit)
                release.await()
                store.replaceTracks(id, tracks)
            }
        })
        sourceTap()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { frame(); entered.isCompleted }
        compose.runOnIdle {
            val random = ActiveQueue(QueueSource.Random(7), PlayQueue(listOf(tune)).startAt(0))
            player.value = player.value.withPlayback(random).copy(message = null,
                schedule = player.value.schedule.contextSelected(random, SchedulePlacement.REPLACE))
            release.complete(Unit)
        }
        compose.waitUntil(10_000) { frame(); !player.value.dirty }
        frame(600)
        compose.onNodeWithTag("playback-panel").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(selected, player.value.activePlaylistId)
            assertEquals(QueueSource.Random(7), player.value.activePlayback.source)
            assertNull(player.value.message)
        }
        ReflectionHelpers.setField(session, "store", store)
    }

    @Test fun `a playing manual request still returns to the underlying History source`() {
        val snapshot = BrowseState(domain = BrowseDomain.HISTORY, historyTotal = 1, tracks = listOf(tune))
        seed(QueueSource.Collection(SessionSource.History), snapshot)
        compose.runOnIdle {
            val selected = player.value.schedule.next(player.value.activePlayback) as ScheduledStep.Select
            player.value = player.value.copy(schedule = selected.schedule, playing = true)
            ReflectionHelpers.setField(controller, "waitingBrowse", BrowseState(domain = BrowseDomain.SEARCH, query = "unrelated"))
        }
        frame()
        val before = player.value
        clickSource("History")
        compose.runOnIdle { unchanged(before) }
        repeat(2) {
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
            frame(500)
        }
        compose.onNodeWithText("Browse").assertIsDisplayed()
        compose.runOnIdle { unchanged(before) }
    }

    @Test fun `missing original playlist explains why Queue cannot open a source`() {
        seed(QueueSource.Playlist(Long.MAX_VALUE, "Deleted playlist"))
        val before = player.value
        sourceTap()
        compose.onNodeWithTag("playback-panel").assertIsDisplayed()
        compose.onNodeWithText(application.getString(R.string.queue_source_unavailable)).assertIsDisplayed()
        compose.runOnIdle { unchanged(before) }
    }

    @Test fun `manual-only and external queues have no misleading source action`() {
        seed(QueueSource.None)
        compose.onNodeWithTag("queue-source").assertDoesNotExist()
        compose.runOnIdle { player.value = player.value.withPlayback(player.value.activePlayback.copy(source = QueueSource.External)) }
        frame()
        compose.onNodeWithTag("queue-source").assertDoesNotExist()
    }
}
