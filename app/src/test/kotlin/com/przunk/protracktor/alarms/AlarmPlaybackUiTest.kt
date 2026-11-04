// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Application
import android.media.session.PlaybackState
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.PlaybackShellTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class AlarmPlaybackUiTest : PlaybackShellTest() {
    private val tracks = listOf(TrackRef("content://alarm.test/first.mod", "First", fileName = "first.mod"),
        TrackRef("content://alarm.test/second.mod", "Second", fileName = "second.mod"))
    private fun playlist() {
        compose.runOnIdle { player.value = player.value.copy(queue = PlayQueue(tracks).startAt(0), playback = null,
            playing = false, positionSeconds = 12.0) }
        frame(300)
    }
    @After fun removeAlarmFixture() {
        AlarmRepository.from(application).change { it.copy(ringing = null, alarms = emptyList(), songs = emptyList()) }
    }
    @Test fun `real playlist menu reaches preparation and reports an unreadable source without changing playback`() {
        playlist()
        val before = player.value.queue
        compose.onNodeWithContentDescription(application.getString(R.string.a11y_track_actions, tracks[1].title)).performClick()
        frame(250)
        compose.onNodeWithText(application.getString(R.string.alarm_use_music)).performClick()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            player.value.message?.text == application.getString(R.string.alarm_prepare_failed)
        }
        assertEquals(before, player.value.queue)
        assertTrue(AlarmRepository.from(application).book.songs.isEmpty())
    }
    @Test fun `real playlist tap and transport preserve context and position while an alarm owns audio`() {
        playlist()
        val now = AlarmPlatform.clock(application)
        val song = AlarmSong("alarm-song", "a".repeat(64), "Alarm", platformId = "amiga", durationMillis = 3000)
        val quiz = AlarmQuizFactory(Platforms.all.map { it.id to it.name }).create(song, listOf(song))!!
        AlarmRepository.from(application).change { it.copy(songs = listOf(song), ringing = RingingAlarm("active-alarm",
            setOf("alarm"), song, quiz, now.elapsedMillis, now.elapsedMillis + 600_000, now.bootCount)) }
        val before = player.value.queue
        compose.onNodeWithText(tracks[1].title).performClick()
        frame(100)
        compose.runOnIdle { controller.nextFile(); controller.previousFile() }
        assertEquals(before, player.value.queue)
        assertEquals(12.0, player.value.positionSeconds, .001)
        assertEquals(application.getString(R.string.alarm_finish_first), player.value.message?.text)
        assertFalse(player.value.playing)
    }
    @Test fun `alarm delivery retires a pending open even while the loading indicator is false`() {
        playlist()
        val before = player.value.queue
        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val work = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val pending = work.launch { release.await(); resumed.set(true) }
        org.robolectric.util.ReflectionHelpers.setField(controller, "openJob", pending)
        val control = AlarmCoordinator.from(application)
        AlarmNotifications.channel(application)
        val song = AlarmSong("alarm-song", "a".repeat(64), "Alarm", platformId = "amiga", durationMillis = 3000)
        control.repository.add(song, java.io.File(application.cacheDir, "alarm-audio").apply { writeBytes(byteArrayOf(1,2,3)) })
        control.preview("alarm", setOf(song.id))
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider { org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(3000, 1) }
        val service = org.robolectric.Robolectric.buildService(AlarmService::class.java).create()
        try {
            service.get().onStartCommand(android.content.Intent("reconcile"), 0, 1)
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); pending.isCancelled }
            release.complete(Unit)
            assertFalse(resumed.get())
            assertEquals(before, player.value.queue)
            assertEquals(12.0, player.value.positionSeconds, .001)
            assertFalse(player.value.playing)
        } finally {
            service.destroy(); pending.cancel(); work.cancel()
            org.robolectric.util.ReflectionHelpers.setField(controller, "openJob", null)
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }
    private fun withDelayedBootstrap(block: (java.util.concurrent.CountDownLatch) -> Unit) {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val root = application.createDeviceProtectedStorageContext().noBackupFilesDir
        val context = object : android.content.ContextWrapper(application) {
            override fun getApplicationContext(): android.content.Context = this
            override fun createDeviceProtectedStorageContext(): android.content.Context = this
            override fun getNoBackupFilesDir(): java.io.File {
                entered.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); return root
            }
        }
        com.przunk.protracktor.alarms.api.MusicAlarms.releasePlayback(application)
        val observation = AlarmPlaybackObservation(context)
        val cache = org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<android.content.Context, AlarmPlaybackObservation>>(
            com.przunk.protracktor.alarms.api.MusicAlarms::class.java, "observations")
        synchronized(com.przunk.protracktor.alarms.api.MusicAlarms) { cache[application] = observation }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            block(release)
        } finally {
            release.countDown()
            com.przunk.protracktor.alarms.api.MusicAlarms.releasePlayback(application)
        }
    }
    @Test fun `the latest playback intent waits for cold alarm state without losing the requested row`() {
        playlist()
        withDelayedBootstrap { release ->
            compose.runOnIdle { controller.playAt(0); controller.playAt(1) }
            assertTrue(player.value.loadingTrack)
            assertEquals(tracks[0], player.value.current)
            release.countDown()
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); player.value.current == tracks[1] }
            assertEquals(tracks[1], player.value.current)
        }
    }
    @Test fun `pause cancels a deferred play before cold alarm state completes`() {
        playlist()
        withDelayedBootstrap { release ->
            val before = player.value.queue
            compose.runOnIdle { controller.playAt(1); controller.pause() }
            release.countDown()
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle()
                com.przunk.protracktor.alarms.api.MusicAlarms.playbackGate(application) != com.przunk.protracktor.alarms.api.AlarmPlaybackGate.Loading }
            frame(100)
            assertEquals(before, player.value.queue)
            assertFalse(player.value.loadingTrack)
            assertFalse(player.value.playing)
        }
    }
    @Test fun `a deferred playlist tap retains its source when navigation opens another playlist`() {
        playlist()
        withDelayedBootstrap { release ->
            val source = QueueSource.Playlist(player.value.activePlaylistId, player.value.activePlaylistName.orEmpty())
            val other = listOf(TrackRef("content://other/first.mod", "Other first"), TrackRef("content://other/second.mod", "Other second"))
            compose.runOnIdle {
                controller.playAt(1)
                player.value = player.value.withPlayback(player.value.activePlayback).copy(activePlaylistId = 123L, queue = PlayQueue(other))
            }
            release.countDown()
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); !player.value.loadingTrack }
            assertEquals(tracks[1], player.value.current)
            assertEquals(source, player.value.activePlayback.source)
            assertEquals(other, player.value.queue.tracks)
            assertEquals(123L, player.value.activePlaylistId)
        }
    }
    @Test fun `deferred results retain their original return route when Browse navigates away`() {
        playlist()
        withDelayedBootstrap { release ->
            compose.runOnIdle {
                browse.value = browse.value.copy(domain = BrowseDomain.SEARCH, query = "original query", tracks = tracks)
                controller.playFromResults(tracks, 1)
                browse.value = browse.value.copy(domain = BrowseDomain.HISTORY, query = "", tracks = emptyList())
            }
            release.countDown()
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); !player.value.loadingTrack }
            assertEquals(tracks[1], player.value.current)
            assertEquals(QueueSource.Collection(SessionSource.Search("original query")), player.value.activePlayback.source)
            compose.runOnIdle { assertTrue(controller.returnToSession()) }
            assertEquals(BrowseDomain.SEARCH, browse.value.domain)
            assertEquals("original query", browse.value.query)
        }
    }
    @Test fun `an abandoned deferred tap cannot restore playlist rows removed while waiting`() {
        playlist()
        withDelayedBootstrap { release ->
            compose.runOnIdle { controller.playAt(1); controller.removeTrack(1) }
            assertEquals(listOf(tracks[0]), player.value.queue.tracks)
            release.countDown()
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); !player.value.loadingTrack }
            assertEquals(listOf(tracks[0]), player.value.queue.tracks)
            assertNotEquals(tracks[1], player.value.current)
            assertEquals(PlaybackController.UNDO, player.value.message?.actionLabel)
            assertEquals(application.getString(R.string.notice_pending_playlist_changed),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        }
    }
    @Test fun `a deferred selection adopts the latest transport settings`() {
        playlist()
        withDelayedBootstrap { release ->
            compose.runOnIdle { controller.playAt(1); controller.toggleShuffle() }
            assertTrue(player.value.transportQueue.shuffle)
            release.countDown()
            compose.waitUntil(5000) { shadowOf(Looper.getMainLooper()).idle(); !player.value.loadingTrack }
            assertEquals(tracks[1], player.value.current)
            assertTrue(player.value.transportQueue.shuffle)
        }
    }
    @Test fun `normal media session exposes only stop and an explanation during an alarm`() {
        playlist()
        val state = player.value
        val presented = PlaybackPresentation.playbackState(application, state, alarmRinging = true)
        assertEquals(PlaybackState.STATE_PAUSED, presented.state)
        assertEquals(PlaybackState.ACTION_STOP, presented.actions)
        assertEquals(application.getString(R.string.alarm_finish_first), presented.errorMessage.toString())
        assertEquals(state, player.value)
    }
}
