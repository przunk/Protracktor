// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmServiceLifetimeTest {
    private fun await(test: () -> Boolean) {
        val until = System.nanoTime() + 5_000_000_000L
        while (!test() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1))
            Thread.sleep(5)
        }
        assertTrue("The controlled lifecycle step did not complete", test())
    }
    @Test fun `destroy retires pending IO without retaining or reposting alarm resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AlarmNotifications.channel(context)
        val control = AlarmCoordinator.from(context)
        val song = AlarmSong("song", "a".repeat(64), "Music", platformId = "amiga", durationMillis = 3000)
        control.repository.add(song, java.io.File(context.cacheDir, "prepared").apply { writeBytes(byteArrayOf(1,2,3)) })
        val now = AlarmPlatform.clock(context)
        val date = java.time.Instant.ofEpochMilli(now.wallMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        control.repository.change { it.copy(alarms = listOf(MusicAlarm("alarm", 7, 30, songIds = listOf(song.id),
            enabled = true, nextAtMillis = now.wallMillis, occurrenceDate = date))) }
        val created = AtomicInteger()
        ShadowMediaPlayer.setCreateListener { _, _ -> created.incrementAndGet() }
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 1) }
        val service = Robolectric.buildService(AlarmService::class.java).create()
        val release = CountDownLatch(1)
        var blocker: Thread? = null
        var destroyed = false
        val field = AlarmService::class.java.getDeclaredField("pendingCommands").apply { isAccessible = true }
        try {
            service.get().onStartCommand(Intent(AlarmPlatform.FIRE).putExtra("revision", control.repository.book.revision)
                .putExtra("at", now.wallMillis), 0, 1)
            await { created.get() == 1 && field.getInt(service.get()) == 0 }
            val held = CountDownLatch(1)
            blocker = thread(name = "alarm-lifecycle-fixture", isDaemon = true) {
                synchronized(control) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
            }
            assertTrue(held.await(5, TimeUnit.SECONDS))
            service.get().onStartCommand(Intent("reconcile"), 0, 2)
            await { Thread.getAllStackTraces().any { (worker, stack) ->
                worker.state == Thread.State.BLOCKED && stack.any { it.className.startsWith(AlarmService::class.java.name) }
            } }
            service.destroy(); destroyed = true
            assertEquals("The blocked IO must still be outstanding at destruction", 1, field.getInt(service.get()))
            assertNull("Destruction removes the alarm notification before IO completes",
                shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(AlarmPlatform.NOTIFICATION))
            val playersAfterClose = created.get()
            release.countDown(); blocker.join(5000)
            await { field.getInt(service.get()) == 0 }
            assertNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(AlarmPlatform.NOTIFICATION))
            assertEquals(playersAfterClose, created.get())
            assertFalse(AlarmService.running)
        } finally {
            release.countDown(); blocker?.join(5000)
            if (!destroyed) service.destroy()
            ShadowMediaPlayer.resetStaticState()
        }
    }
}
