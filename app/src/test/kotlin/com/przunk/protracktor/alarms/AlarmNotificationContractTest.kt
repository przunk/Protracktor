// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmNotificationContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val song = AlarmSong("song", "a".repeat(64), "Morning", platformId = "amiga", durationMillis = 3000)
    private val quiz = AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64")).create(song, listOf(song))!!
    private val active = RingingAlarm("token", setOf("alarm"), song, quiz, 0, 600000, 1)

    @Test fun `swiping the alarm notification cannot send a stop command`() {
        AlarmNotifications.channel(context)
        assertNull(AlarmNotifications.ringing(context, active).deleteIntent)
    }
    @Test fun `notification opens the challenge without bypassing it through a stop action`() {
        val notification = AlarmNotifications.ringing(context, active)
        assertTrue(notification.actions.isNullOrEmpty())
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.fullScreenIntent)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
    @Test fun `lock screen hides the notification while the full screen challenge owns presentation`() {
        assertEquals(Notification.VISIBILITY_SECRET, AlarmNotifications.ringing(context, active).visibility)
    }
    @Test fun `startup and ringing notifications have different identities so FSI is a new alert`() {
        val service = Robolectric.buildService(AlarmService::class.java).create()
        try {
            assertNotEquals(AlarmPlatform.NOTIFICATION, shadowOf(service.get()).lastForegroundNotificationId)
        } finally { service.destroy() }
    }
}
