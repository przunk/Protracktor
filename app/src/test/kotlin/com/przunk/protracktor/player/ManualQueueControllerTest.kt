// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.data.ProtracktorDatabase
import com.przunk.protracktor.data.HistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
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
            val scope = ReflectionHelpers.getField<CoroutineScope>(controller, "scope")
            scope.cancel()
            await { scope.coroutineContext[Job]!!.isCompleted }
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

    @Test fun `Clear preserves paused transport and draft and Undo restores only the playback plan`() {
        controller.enqueueTrack(track("X"))
        val before = state.value
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        val generation = before.schedule.generation
        controller.clearQueue()
        val cleared = state.value
        assertEquals(QueueSource.None, cleared.activePlayback.source)
        assertEquals(before.queue, cleared.queue)
        assertEquals(before.dirty, cleared.dirty)
        assertEquals(before.playlists, cleared.playlists)
        assertEquals(before.current, cleared.current)
        assertEquals(before.metadata, cleared.metadata)
        assertEquals(12.5, cleared.positionSeconds, 0.0)
        assertFalse(cleared.playing)
        assertFalse(cleared.canGoPreviousFile)
        assertFalse(cleared.canGoNextFile)
        assertTrue(cleared.schedule.pending.isEmpty())
        assertTrue(ReflectionHelpers.getField<Long>(controller, "playbackEpoch") > epoch)
        controller.playContextEntry(before.activePlayback.entryIds.first(), generation)
        assertEquals(cleared, state.value)
        state.value = state.value.copy(positionSeconds = 17.0)
        state.value.message!!.action!!.invoke()
        assertEquals(before.activePlayback, state.value.activePlayback)
        assertEquals(before.schedule.pending, state.value.schedule.pending)
        assertEquals(17.0, state.value.positionSeconds, 0.0)
        assertEquals(before.queue, state.value.queue)
        assertFalse(state.value.playing)
    }

    @Test fun `Clear retains the actual loading job rather than reopening or cancelling it`() {
        val job = Job()
        ReflectionHelpers.setField(controller, "openJob", job)
        state.value = state.value.copy(loadingTrack = true)
        controller.clearQueue()
        assertSame(job, ReflectionHelpers.getField<Job>(controller, "openJob"))
        assertTrue(job.isActive)
        assertTrue(state.value.loadingTrack)
        assertEquals("B", state.value.current!!.title)
        state.value.message!!.action!!.invoke()
        assertSame(job, ReflectionHelpers.getField<Job>(controller, "openJob"))
        assertTrue(state.value.loadingTrack)
    }

    @Test fun `Clear preserves playing state and has no source previous after adding new requests`() {
        state.value = state.value.copy(playing = true)
        controller.clearQueue()
        assertTrue(state.value.playing)
        controller.enqueueTrack(track("X"))
        controller.nextFile()
        assertEquals("X", state.value.current!!.title)
        assertFalse(state.value.canGoPreviousFile)
        assertEquals(listOf("X"), state.value.schedule.rows(state.value.activePlayback).map { it.track.title })
        controller.previousFile()
        assertEquals("X", state.value.current!!.title)
        assertEquals(tracks, state.value.queue.tracks)
    }

    @Test fun `Clear during a Random successor draw does not leave a spinner or revive consumed current`() {
        val active = state.value.activePlayback.copy(source = QueueSource.Random(1))
        state.value = state.value.copy(playback = active, loadingTrack = true,
            schedule = state.value.schedule.contextSelected(active, SchedulePlacement.REPLACE).finishCurrent())
        assertNull(ReflectionHelpers.getField<Job?>(controller, "openJob"))
        controller.clearQueue()
        assertFalse(state.value.loadingTrack)
        assertFalse(state.value.playing)
        assertTrue(state.value.schedule.consumed)
        assertTrue(state.value.schedule.rows(state.value.activePlayback).isEmpty())
        controller.enqueueTrack(track("fresh"))
        controller.togglePlayPause()
        assertEquals("fresh", state.value.current!!.title)
    }

    @Test fun `Clear invalidates an in-flight random fill but preserves the heard record and navigation`() = runBlocking {
        val history = ReflectionHelpers.getField<RandomHistory>(controller, "randomHistory")
        history.fill(1, 1) { listOf(tracks[1]) }
        history.advance()
        ReflectionHelpers.setField(controller, "randomSession", 1L)
        state.value = state.value.copy(playback = state.value.activePlayback.copy(source = QueueSource.Random(1)))
        val result = kotlinx.coroutines.CompletableDeferred<List<TrackRef>>()
        val filling = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            history.fill(1, 1) { result.await() }
        }
        controller.clearQueue()
        result.complete(listOf(track("stale")))
        filling.join()
        assertTrue(history.upcoming(3).isEmpty())
        assertEquals(listOf(tracks[1]), history.playedPicks)
        controller.openRandom()
        assertEquals(QueueSource.None, state.value.activePlayback.source)
        assertEquals("B", state.value.current!!.title)
    }

    @Test fun `Clear refreshes failed occurrence retry while the stale retry cannot change playback`() {
        controller.enqueueTrack(track("X"))
        controller.nextFile()
        await { !state.value.loadingTrack }
        val stale = requireNotNull(ReflectionHelpers.getField<(() -> Unit)?>(controller, "pendingRetry"))
        controller.clearQueue()
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        val live = requireNotNull(ReflectionHelpers.getField<(() -> Unit)?>(controller, "pendingRetry"))
        stale()
        assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
        live()
        assertTrue(ReflectionHelpers.getField<Long>(controller, "playbackEpoch") > epoch)
        assertEquals("X", state.value.current!!.title)
        assertEquals(QueueSource.None, state.value.activePlayback.source)
    }

    @Test fun `expired Clear Undo reports changed playback instead of overwriting it`() {
        controller.clearQueue()
        val undo = state.value.message!!.action!!
        state.value = state.value.copy(schedule = state.value.schedule.finishCurrent())
        undo()
        assertEquals(QueueSource.None, state.value.activePlayback.source)
        assertEquals("Cannot undo: the playback plan has changed.", state.value.message!!.text)
        assertNull(state.value.message!!.action)
    }

    @Test fun `Undo Clear keeps newly selected repeat and shuffle when source relinks to the editor`() {
        val before = state.value
        controller.clearQueue()
        val undo = state.value.message!!.action!!
        controller.cycleRepeat()
        controller.toggleShuffle()
        assertEquals(RepeatMode.PLAYLIST, state.value.activePlayback.order.repeat)
        assertTrue(state.value.activePlayback.order.shuffle)
        undo()
        assertEquals(before.activePlayback.source, state.value.activePlayback.source)
        assertEquals(RepeatMode.PLAYLIST, state.value.activePlayback.order.repeat)
        assertTrue(state.value.activePlayback.order.shuffle)
        assertEquals(before.queue.tracks, state.value.queue.tracks)
        assertEquals(before.queue.currentIndex, state.value.queue.currentIndex)
        assertEquals(before.dirty, state.value.dirty)
        assertEquals(before.metadata, state.value.metadata)
        assertEquals(before.positionSeconds, state.value.positionSeconds, 0.0)
    }

    @Test fun `Clear Undo cannot roll back linked playlist edits or replace a newer snackbar`() {
        controller.clearQueue()
        val undo = state.value.message!!.action!!
        controller.addToPlaylist(listOf(track("new draft")))
        val draft = state.value.queue
        undo()
        assertEquals(draft, state.value.queue)
        assertEquals(QueueSource.None, state.value.activePlayback.source)
        controller.enqueueTrack(track("Y"))
        val message = state.value.message
        undo()
        assertEquals(message, state.value.message)
        assertEquals("Y", state.value.schedule.pending.single().track.title)
    }

    @Test fun `Play after manual-only exhaustion starts newly queued track instead of replaying the old one`() {
        controller.clearQueue()
        state.value = state.value.copy(schedule = state.value.schedule.finishCurrent())
        controller.enqueueTrack(track("new"))
        controller.togglePlayPause()
        assertEquals("new", state.value.current!!.title)
        assertTrue(state.value.schedule.pending.isEmpty())
        assertFalse(state.value.schedule.consumed)
    }

    @Test fun `Clear on idle draft empties transport without touching editor and repeated Clear is a no-op`() {
        state.value = state.value.copy(queue = PlayQueue(tracks), playback = null)
        controller.clearQueue()
        assertTrue(state.value.schedule.rows(state.value.activePlayback).isEmpty())
        assertNull(state.value.current)
        assertEquals(tracks, state.value.queue.tracks)
        val before = state.value
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        controller.clearQueue()
        assertEquals(before.copy(message = state.value.message), state.value)
        assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
        assertEquals("Queue is already clear.", state.value.message!!.text)
    }

    @Test fun `Play after Clear Undo of a first Random draw starts a new draw instead of doing nothing`() {
        ReflectionHelpers.setField(controller, "randomSession", 1L)
        state.value = state.value.preparingRandom(1)
        controller.clearQueue()
        assertFalse(state.value.loadingTrack)
        assertNull(state.value.current)
        state.value.message!!.action!!.invoke()
        assertEquals(QueueSource.Random(1), state.value.activePlayback.source)
        assertNull(state.value.current)
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        controller.togglePlayPause()
        assertTrue("Play must issue an intent for the restored empty Random source",
            ReflectionHelpers.getField<Long>(controller, "playbackEpoch") > epoch)
        await { !state.value.loadingTrack }
        assertNotNull("An empty catalogue must explain why no track starts", state.value.message)
    }

    @Test fun `reordering pending duplicates preserves source transport and next uses the new first occurrence`() {
        controller.enqueueTrack(track("X"), true)
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("Y"))
        val before = state.value
        val pending = before.schedule.pending
        val epoch = ReflectionHelpers.getField<Long>(controller, "playbackEpoch")
        controller.movePendingRequest(pending[2].id, pending[0].id)
        assertEquals(listOf(pending[2], pending[0], pending[1]), state.value.schedule.pending)
        assertEquals(before.copy(schedule = state.value.schedule), state.value)
        assertEquals(epoch, ReflectionHelpers.getField<Long>(controller, "playbackEpoch"))
        controller.nextFile()
        assertEquals(pending[2], state.value.schedule.current!!.request)
        val actual = state.value
        controller.movePendingRequest(pending[2].id, pending[0].id)
        assertEquals(actual, state.value)
    }

    @Test fun `real file Next drains requests and Previous never resurrects consumed manual rows`() {
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
        assertEquals("B", state.value.current!!.title)
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

    @Test fun `source Play preserves pending but replaces the actual source occurrence`() {
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("Y"))
        controller.nextFile()
        controller.playAt(0)
        assertEquals("A", state.value.current!!.title)
        assertFalse(state.value.manualCurrent)
        assertEquals(listOf("Y"), state.value.schedule.pending.map { it.track.title })
        assertEquals("A", state.value.schedule.current!!.track.title)
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

    @Test fun `Play resumes an existing paused source before requests even if its decoder was released`() {
        controller.enqueueTrack(track("X"))
        controller.togglePlayPause()
        assertEquals("B", state.value.current!!.title)
        assertEquals(listOf("X"), state.value.schedule.pending.map { it.track.title })
        assertFalse(state.value.manualCurrent)
    }

    @Test fun `starting manual playback from idle leaves an unstarted editor playlist out of context`() {
        val draft = PlayQueue(tracks)
        state.value = state.value.copy(queue = draft, playback = null)
        controller.enqueueTrack(track("X"))
        controller.togglePlayPause()
        assertEquals("X", state.value.current!!.title)
        assertEquals(QueueSource.None, state.value.activePlayback.source)
        assertFalse(state.value.canGoNextFile)
        assertEquals(draft, state.value.queue)
    }

    @Test fun `actual History write preserves request origin rather than suspended context origin`() {
        val writes = ReflectionHelpers.getField<HistoryWrites>(controller, "historyWrites")
        val history = HistoryStore(app)
        val manual = track("X")
        writes.record(manual, writes.generation)
        await { writes.revision.value.number == 1L }
        val original = runBlocking { history.page(0, 10) }.single()

        controller.enqueueTrack(manual, fromHistory = true)
        controller.nextFile()
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "recordPlayed",
            ReflectionHelpers.ClassParameter.from(TrackRef::class.java, manual),
            ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType!!, writes.generation))
        // A following accepted write is a barrier for the ordered writer; no arbitrary sleep.
        val barrier = track("Barrier")
        writes.record(barrier, writes.generation)
        await { runBlocking { history.count() } == 2 }
        assertEquals(original, runBlocking { history.page(0, 10) }.single { it.trackId == manual.id })

        state.value = state.value.copy(playback = state.value.activePlayback.copy(
            source = QueueSource.Collection(SessionSource.History)))
        controller.enqueueTrack(track("Outside History"))
        controller.nextFile()
        val outside = state.value.current!!
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "recordPlayed",
            ReflectionHelpers.ClassParameter.from(TrackRef::class.java, outside),
            ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType!!, writes.generation))
        await { runBlocking { history.count() } == 3 }
        assertEquals(1, runBlocking { history.page(0, 10) }.single { it.trackId == outside.id }.playCount)
    }

    @Test fun `context removal is session only and Undo cannot revive another source`() {
        val before = state.value.queue
        val id = state.value.activePlayback.entryIds.last()
        controller.removeContextEntry(id)
        val undo = state.value.message!!.action!!
        assertEquals(before, state.value.queue)
        assertEquals(listOf("A", "B"), state.value.activePlayback.visibleIndices.map { state.value.queue.tracks[it].title })
        undo()
        assertTrue(state.value.activePlayback.omitted.isEmpty())
        controller.removeContextEntry(id)
        val stale = state.value.message!!.action!!
        controller.playAt(0)
        stale()
        assertTrue(state.value.activePlayback.omitted.isEmpty())
        assertEquals("A", state.value.current!!.title)
    }

    @Test fun `removing current manual advances once and Undo restores it pending not playing`() {
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("Y"))
        controller.nextFile()
        val x = state.value.schedule.current!!.id
        controller.removePendingRequest(x)
        assertEquals("Y", state.value.current!!.title)
        val undo = state.value.message!!.action!!
        undo()
        assertEquals("Y", state.value.current!!.title)
        assertEquals(listOf("X"), state.value.schedule.pending.map { it.track.title })
    }

    @Test fun `removing current source selects pending before next source without editing playlist`() {
        val tracks = state.value.queue.tracks
        controller.enqueueTrack(track("X"))
        controller.removeContextEntry(state.value.activePlayback.currentEntryId!!)
        assertEquals("X", state.value.current!!.title)
        assertEquals(tracks, state.value.queue.tracks)
        controller.nextFile()
        assertEquals("C", state.value.current!!.title)
        controller.previousFile()
        assertEquals("A", state.value.current!!.title)
    }

    @Test fun `removing paused source selects next without opening audio until explicit Play`() {
        controller.enqueueTrack(track("X"))
        controller.removeContextEntry(state.value.activePlayback.currentEntryId!!)
        assertEquals("X", state.value.current!!.title)
        assertFalse(state.value.playing)
        assertFalse(state.value.loadingTrack)
        assertNull(ReflectionHelpers.getField<Job?>(controller, "openJob"))
        assertNull(ReflectionHelpers.getField<(() -> Unit)?>(controller, "pendingRetry"))
        assertTrue(state.value.metadata.isEmpty())
        assertEquals(0.0, state.value.positionSeconds, 0.0)
        controller.togglePlayPause()
        assertNotNull(ReflectionHelpers.getField<Job?>(controller, "openJob"))
    }

    @Test fun `removing paused manual selects pending without retrying or opening its decoder`() {
        controller.enqueueTrack(track("X"))
        controller.enqueueTrack(track("Y"))
        controller.nextFile()
        await { !state.value.loadingTrack && state.value.message?.text?.startsWith("Added to queue") == false }
        controller.removePendingRequest(state.value.schedule.current!!.id)
        assertEquals("Y", state.value.current!!.title)
        assertFalse(state.value.playing)
        assertFalse(state.value.loadingTrack)
        assertNull(ReflectionHelpers.getField<Job?>(controller, "openJob"))
        assertNull(ReflectionHelpers.getField<(() -> Unit)?>(controller, "pendingRetry"))
    }

    @Test fun `removing paused source without pending selects source successor without starting it`() {
        controller.removeContextEntry(state.value.activePlayback.currentEntryId!!)
        assertEquals("C", state.value.current!!.title)
        assertFalse(state.value.playing)
        assertFalse(state.value.loadingTrack)
        assertNull(ReflectionHelpers.getField<Job?>(controller, "openJob"))
        assertEquals(tracks, state.value.queue.tracks)
    }

    @Test fun `removing a playing source continues its successor rather than pausing`() {
        state.value = state.value.copy(playing = true)
        controller.removeContextEntry(state.value.activePlayback.currentEntryId!!)
        assertEquals("C", state.value.current!!.title)
        assertNotNull(ReflectionHelpers.getField<Job?>(controller, "openJob"))
    }

    @Test fun `Random menu pruning preserves remaining occurrence identities including equal files`() {
        val history = ReflectionHelpers.getField<RandomHistory>(controller, "randomHistory")
        val r = track("R")
        val s = track("S")
        runBlocking {
            history.fill(2, 1) { listOf(r, s) }
            history.advance()
            history.advance()
            history.fill(1, 1) { listOf(r) }
            history.advance()
        }
        ReflectionHelpers.setField(controller, "randomSession", 1L)
        val source = ActiveQueue(QueueSource.Random(1), PlayQueue(history.playedPicks).startAt(2)).omit(2)
        val draft = state.value.queue
        state.value = state.value.copy(playback = source,
            schedule = state.value.schedule.contextSelected(source, SchedulePlacement.REPLACE))
        controller.removeRandomAt(0)
        assertEquals(listOf(s, r), state.value.activePlayback.order.tracks)
        assertEquals(listOf(1L, 2L), state.value.activePlayback.entryIds)
        assertEquals(setOf(2L), state.value.activePlayback.omitted)
        assertEquals(1, state.value.activePlayback.order.currentIndex)
        assertEquals(r, state.value.current)
        assertEquals(draft, state.value.queue)
        controller.removeRandomAt(1)
        assertEquals(r, state.value.current)
        assertNull(state.value.sourcePlaybackIndex)
        assertTrue(state.value.schedule.rows(state.value.activePlayback).none { it.playing })
    }

    @Test fun `editing the linked draft never recycles an occurrence ID for a different track`() {
        controller.addToPlaylist(listOf(track("X")))
        val id = state.value.activePlayback.entryIds.last()
        val generation = state.value.schedule.generation
        controller.removeTrack(state.value.queue.tracks.lastIndex)
        controller.addToPlaylist(listOf(track("Y")))
        assertNotEquals(id, state.value.activePlayback.entryIds.last())
        controller.playContextEntry(id, generation)
        assertEquals("B", state.value.current!!.title)
    }

    @Test fun `Next consumes the manual row even when Random has no new candidate`() {
        ReflectionHelpers.setField(controller, "randomSession", 1L)
        state.value = state.value.copy(playback = state.value.activePlayback.copy(source = QueueSource.Random(1)))
        controller.enqueueTrack(track("X"))
        controller.nextFile()
        await { !state.value.loadingTrack && state.value.message?.text?.startsWith("Added to queue") == false }
        controller.nextFile()
        await { state.value.randomExhausted && !state.value.loadingTrack }
        assertTrue(state.value.schedule.consumed)
        assertTrue(state.value.schedule.rows(state.value.activePlayback).none { it is PlaybackRow.Manual })
    }
}
