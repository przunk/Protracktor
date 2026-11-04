// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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
class AlarmServiceDeliveryTest {
    @Test fun `claimed exact occurrence replaces the startup foreground entry with a fresh full screen alarm`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AlarmNotifications.channel(context)
        val control = AlarmCoordinator.from(context)
        val song = AlarmSong("song", "a".repeat(64), "Morning", platformId = "amiga", durationMillis = 3000,
            year = "1992", yearAlternatives = listOf("1991", "1993"))
        control.repository.add(song, java.io.File(context.cacheDir, "prepared").apply { writeBytes(byteArrayOf(1, 2, 3)) })
        val now = AlarmPlatform.clock(context)
        val date = java.time.Instant.ofEpochMilli(now.wallMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        control.repository.change { it.copy(alarms = listOf(MusicAlarm("alarm", 7, 30, songIds = listOf(song.id), enabled = true,
            nextAtMillis = now.wallMillis, occurrenceDate = date))) }
        val revision = control.repository.book.revision
        var players = 0
        ShadowMediaPlayer.setCreateListener { _, _ -> players++ }
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 1) }
        val service = Robolectric.buildService(AlarmService::class.java).create()
        try {
            val shadow = shadowOf(service.get())
            assertEquals(AlarmPlatform.STARTUP_NOTIFICATION, shadow.lastForegroundNotificationId)
            service.get().onStartCommand(Intent(AlarmPlatform.FIRE).putExtra("revision", revision).putExtra("at", now.wallMillis), 0, 1)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (shadow.lastForegroundNotificationId != AlarmPlatform.NOTIFICATION && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertEquals(AlarmPlatform.NOTIFICATION, shadow.lastForegroundNotificationId)
            assertNotNull(control.repository.book.ringing)
            assertNotNull(shadow.lastForegroundNotification.fullScreenIntent)
            assertEquals(AlarmPlatform.CHANNEL, shadow.lastForegroundNotification.channelId)
            assertNull(shadow.lastForegroundNotification.deleteIntent)
            assertTrue(shadow.lastForegroundNotification.actions.isNullOrEmpty())
            val initial = control.repository.book.ringing!!
            val quiz = initial.quiz!!
            assertFalse(control.answer(initial.token, quiz.choices.first { it.id != quiz.correctId }.id))
            shadowOf(Looper.getMainLooper()).idle()
            assertNotEquals(quiz.category, control.repository.book.ringing!!.quiz!!.category)
            assertEquals("Changing a recognition question must not reopen the audio player", 1, players)
        } finally { service.destroy(); ShadowMediaPlayer.resetStaticState() }
    }
}
