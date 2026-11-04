// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.alarms.api.AlarmPlaybackGate
import com.przunk.protracktor.alarms.api.MusicAlarms
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmPlaybackObservationTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun `cold transport observation never reads a path on the caller and keeps bootstrap asynchronous`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val root = temp.newFolder()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getNoBackupFilesDir(): File {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Transport performed file IO" }
                entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); return root
            }
        }
        try {
            MusicAlarms.warmUp(context)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(AlarmPlaybackGate.Loading, MusicAlarms.playbackGate(context))
            release.countDown()
            assertEquals(AlarmPlaybackGate.Clear, runBlocking { MusicAlarms.awaitPlaybackGate(context) })
        } finally { release.countDown(); MusicAlarms.releasePlayback(context) }
    }
    @Test fun `one interruption rule retires stale boot rollback and elapsed deadline`() {
        val song = AlarmSong("song", "a".repeat(64), "Music", durationMillis = 1000)
        val active = RingingAlarm("token", setOf("alarm"), song, null, 100, 200, 7, backup = true)
        assertEquals(AlarmPlaybackGate.Ringing("token", 200), AlarmInterruption.gate(active, true, 199, 7))
        for ((available, elapsed, boot) in listOf(Triple(false, 150L, 7), Triple(true, 99L, 7),
            Triple(true, 200L, 7), Triple(true, 150L, 8))) {
            assertEquals(AlarmPlaybackGate.Clear, AlarmInterruption.gate(active, available, elapsed, boot))
        }
    }
    @Test fun `interruption stream expires without changing the durable ringing snapshot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AlarmRepository.from(context)
        val now = AlarmPlatform.clock(context)
        val song = AlarmSong("song", "a".repeat(64), "Music", durationMillis = 1000)
        repository.change { it.copy(songs = listOf(song), ringing = RingingAlarm("token", setOf("alarm"), song,
            null, now.elapsedMillis, now.elapsedMillis + 250, now.bootCount, backup = true)) }
        val observation = AlarmPlaybackObservation(context)
        try {
            assertTrue(runBlocking { observation.awaitReady() } is AlarmPlaybackGate.Ringing)
            val revision = repository.book.revision
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(251))
            val until = System.nanoTime() + 3_000_000_000L
            while (observation.state.value != AlarmPlaybackGate.Clear && System.nanoTime() < until) Thread.sleep(5)
            assertEquals(AlarmPlaybackGate.Clear, observation.state.value)
            assertEquals(revision, repository.book.revision)
            assertNotNull(repository.book.ringing)
        } finally { observation.close() }
    }
}
