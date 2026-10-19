// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.data.ProtracktorDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Real controller/SQLite, preset source and unavailable content URIs; no native audio decoding. */
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en")
class ManualQueueControllerTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var controller: PlaybackController
    private val state get() = ReflectionHelpers.getField<MutableStateFlow<PlayerUiState>>(controller, "_state")
    private fun track(name: String) = TrackRef("content://manual.queue.test/$name.mod", name, author = "Known author")
    private val tracks = listOf("A", "B", "C").map(::track)

    @Before fun start() {
        Appearance.selectCacheAhead(app, CacheAhead.OFF)
        controller = PlaybackController.get(app)
        await { controller.state.value.restored && controller.browse.value.knowsWhatIsHeld }
        val before = state.value
        val queue = PlayQueue(tracks).startAt(1)
        state.value = before.copy(queue = queue,
            playback = ActiveQueue(QueueSource.Playlist(before.activePlaylistId), queue),
            playing = false, positionSeconds = 12.5, metadata = mapOf("title" to "B"))
    }

    @After fun stop() {
        if (::controller.isInitialized) {
            ReflectionHelpers.getField<CoroutineScope>(controller, "scope").cancel()
            controller.release()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "backgroundWork").close()
            ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(controller, "scanWork").close()
            ReflectionHelpers.setStaticField(PlaybackController::class.java, "instance", null)
        }
        ProtracktorDatabase.of(app).close()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        fail("Controller did not reach the expected host state")
    }

    @Test fun `enqueue does not resume or replace source and accepts duplicate requests`() {
        val before = state.value
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("X"))
        assertEquals(before.current, state.value.current)
        assertEquals(before.activePlayback, state.value.activePlayback)
        assertEquals(before.queue, state.value.queue)
        assertEquals(before.metadata, state.value.metadata)
        assertEquals(12.5, state.value.positionSeconds, 0.0)
        assertFalse(state.value.playing)
        assertEquals(2, state.value.schedule.pending.size)
        assertNotEquals(state.value.schedule.pending[0].id, state.value.schedule.pending[1].id)
    }

    @Test fun `real file Next drains requests before advancing context and Previous replays`() {
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("Y"))
        controller.nextFile()
        assertEquals("X", state.value.current!!.title)
        assertEquals("B", state.value.activePlayback.current!!.title)
        controller.nextFile()
        assertEquals("Y", state.value.current!!.title)
        assertEquals("B", state.value.activePlayback.current!!.title)
        controller.nextFile()
        assertEquals("C", state.value.current!!.title)
        assertEquals("C", state.value.activePlayback.current!!.title)
        controller.previousFile()
        assertEquals("Y", state.value.current!!.title)
        assertEquals("B", state.value.activePlayback.current!!.title)
        controller.nextFile()
        assertEquals("C", state.value.current!!.title)
        assertTrue(state.value.schedule.pending.isEmpty())
    }

    @Test fun `failed manual file over Random is retained instead of auto-skipping`() {
        state.value = state.value.copy(playback = state.value.activePlayback.copy(source = QueueSource.Random(1)))
        controller.enqueueTrack(track("X"))
        controller.nextFile()
        await { !state.value.loadingTrack && state.value.message?.text?.startsWith("Added to queue") == false }
        assertEquals("X", state.value.current!!.title)
        assertTrue(state.value.manualCurrent)
        assertEquals("B", state.value.activePlayback.current!!.title)
        assertEquals(0, ReflectionHelpers.getField<Int>(controller, "failedRandomPicks"))
        assertNotNull(ReflectionHelpers.getField<(() -> Unit)?>(controller, "pendingRetry"))
    }

    @Test fun `stale retry cannot replace a newer manual occurrence`() {
        controller.enqueueTrack(track("X"))
        controller.nextFile()
        val oldRetry = requireNotNull(ReflectionHelpers.getField<(() -> Unit)?>(controller, "pendingRetry"))
        controller.enqueueTrack(track("Y"))
        controller.playPendingRequest(state.value.schedule.pending.single().id)
        val occurrence = state.value.schedule.current
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        oldRetry()
        assertEquals(occurrence, state.value.schedule.current)
        assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
        assertEquals("Y", state.value.current!!.title)
    }

    @Test fun `Remove Undo restores only its pending occurrence and stale snackbar action is ignored`() {
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("X"))
        val pending = state.value.schedule.pending
        val source = state.value.queue
        controller.removePendingRequest(pending[0].id)
        val stale = requireNotNull(state.value.message!!.action)
        controller.removePendingRequest(pending[1].id)
        stale()
        assertTrue(state.value.schedule.pending.isEmpty())
        state.value.message!!.action!!.invoke()
        assertEquals(listOf(pending[1]), state.value.schedule.pending)
        assertEquals(source, state.value.queue)
        assertEquals("B", state.value.current!!.title)
    }

    @Test fun `source Play preserves pending but replaces mixed traversal`() {
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("Y"))
        controller.nextFile()
        controller.playAt(0)
        assertEquals("A", state.value.current!!.title)
        assertFalse(state.value.manualCurrent)
        assertEquals(listOf("Y"), state.value.schedule.pending.map { it.track.title })
        assertEquals(listOf("A"), state.value.schedule.record.map { it.track.title })
    }

    @Test fun `Keep over a collection saves actual manual track and not suspended source track`() {
        state.value = state.value.copy(playback = state.value.activePlayback.copy(
            source = QueueSource.Collection(SessionSource.Search("query"))))
        controller.enqueueTrack(track("X"))
        controller.nextFile()
        controller.keepTransient()
        assertEquals(listOf("A", "B", "C", "X"), state.value.queue.tracks.map { it.title })
        assertEquals("B", state.value.activePlayback.current!!.title)
        assertEquals("X", state.value.current!!.title)
    }
}
