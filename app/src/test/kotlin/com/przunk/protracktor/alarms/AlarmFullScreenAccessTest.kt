// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [AlarmFullScreenAccessTest.AccessShadow::class])
class AlarmFullScreenAccessTest {
    @Implements(NotificationManager::class, minSdk = 34)
    class AccessShadow : ShadowNotificationManager() {
        companion object { var allowed = true }
        @Implementation fun canUseFullScreenIntent() = allowed
    }
    @get:Rule val temp = TemporaryFolder()
    @Before fun reset() { AccessShadow.allowed = true }
    private fun ready(): Pair<Context, AlarmCoordinator> {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            private val root = temp.newFolder()
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        AlarmNotifications.channel(context)
        val control = AlarmCoordinator.from(context)
        val song = AlarmSong("song", "a".repeat(64), "Morning", platformId = "amiga", durationMillis = 3000)
        control.repository.add(song, File(temp.newFolder(), "audio").apply { writeBytes(byteArrayOf(1, 2, 3)) })
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        return context to control
    }
    @Test fun `full screen denial keeps the saved alarm but does not claim it is armed`() {
        val (context, control) = ready()
        AccessShadow.allowed = false
        control.save(MusicAlarm("alarm", 7, 30, songIds = listOf("song"), enabled = true))
        assertTrue(control.repository.book.alarms.single().enabled)
        assertFalse(com.przunk.protracktor.permissions.SystemAccess.allowed(context, com.przunk.protracktor.permissions.AppAccess.FULL_SCREEN_ALARMS))
        assertNull(control.scheduling.value.armedAt)
        assertNull(shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
    }
    @Test fun `granting full screen access arms the preserved request through the production scheduler`() {
        val (context, control) = ready()
        AccessShadow.allowed = false
        control.save(MusicAlarm("alarm", 7, 30, songIds = listOf("song"), enabled = true))
        AccessShadow.allowed = true
        control.reconcile()
        assertNotNull(control.scheduling.value.armedAt)
        assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
        val song = control.repository.book.songs.single()
        val quiz = AlarmQuizFactory(listOf("amiga" to "Amiga", "pc" to "PC", "c64" to "C64")).create(song, listOf(song))!!
        assertNotNull(AlarmNotifications.ringing(context, RingingAlarm("token", setOf("alarm"), song, quiz, 0, 600000, 1)).fullScreenIntent)
    }
    @Test fun `a channel without high importance cannot silently stand in for a full screen alarm`() {
        val (context, control) = ready()
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(AlarmPlatform.CHANNEL).importance = NotificationManager.IMPORTANCE_DEFAULT
        control.save(MusicAlarm("alarm", 7, 30, songIds = listOf("song"), enabled = true))
        assertFalse(AlarmReadiness.notifications(context))
        assertNull(control.scheduling.value.armedAt)
    }
}
