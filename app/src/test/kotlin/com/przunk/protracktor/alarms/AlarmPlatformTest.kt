// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmPlatformTest {
    @get:Rule val temp = TemporaryFolder()
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
        private val root = temp.newFolder()
        override fun getApplicationContext(): Context = this
        override fun createDeviceProtectedStorageContext(): Context = this
        override fun getFilesDir(): File = File(root, "ordinary")
        override fun getNoBackupFilesDir(): File = File(root, "no_backup")
    }
    private fun ready(context: Context): Pair<AlarmCoordinator, AlarmSong> {
        AlarmNotifications.channel(context)
        val control = AlarmCoordinator.from(context)
        val song = AlarmSong("music", "a".repeat(64), "Test", platformId = "amiga", durationMillis = 3000)
        val file = File(temp.newFolder(), "excerpt").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        control.repository.add(song, file)
        return control to song
    }
    @Test fun `production coordinator arms one earliest explicit alarm and removal cancels it`() {
        val context = context()
        val (control, song) = ready(context)
        control.save(MusicAlarm("later", 22, 30, songIds = listOf(song.id), enabled = true))
        control.save(MusicAlarm("earlier", 6, 30, songIds = listOf(song.id), enabled = true))
        val manager = context.getSystemService(AlarmManager::class.java)
        val armed = shadowOf(manager).nextScheduledAlarm
        assertNotNull(armed)
        val intent = shadowOf(armed!!.operation).savedIntent
        assertEquals(AlarmReceiver::class.java.name, intent.component!!.className)
        assertEquals(AlarmPlanner.earliest(control.repository.book.alarms), intent.getLongExtra("at", -1))
        assertEquals(control.repository.book.revision, intent.getLongExtra("revision", -1))
        assertEquals(intent.getLongExtra("at", -1), control.armedAt.value)
        control.deleteAlarm("later"); control.deleteAlarm("earlier")
        assertNull(shadowOf(manager).nextScheduledAlarm)
        assertNull(control.armedAt.value)
    }
    @Test @Config(sdk = [31]) fun `exact access denial and grant reconcile the saved request`() {
        val context = context()
        val (control, song) = ready(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        control.save(MusicAlarm("morning", 7, 0, songIds = listOf(song.id), enabled = true))
        assertNull(control.armedAt.value)
        assertTrue(control.repository.book.alarms.single().enabled)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        control.reconcile()
        assertNotNull(control.armedAt.value)
        assertNotNull(shadowOf(manager).nextScheduledAlarm)
    }
    @Test fun `notification denial preserves requested enablement but never claims armed`() {
        val context = context()
        val (control, song) = ready(context)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        control.save(MusicAlarm("morning", 7, 0, songIds = listOf(song.id), enabled = true))
        assertTrue(control.repository.book.alarms.single().enabled)
        assertNull(control.armedAt.value)
        assertNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
        assertThrows(IllegalStateException::class.java) { control.preview("morning", setOf(song.id)) }
    }
    @Test fun `removing music disables orphan schedules and retains an active audio file`() {
        val context = context()
        val (control, song) = ready(context)
        control.save(MusicAlarm("morning", 7, 0, songIds = listOf(song.id), enabled = true))
        val planned = control.repository.book.alarms.single().nextAtMillis
        control.preview("morning", setOf(song.id))
        assertEquals(planned, control.repository.book.alarms.single().nextAtMillis)
        val token = control.repository.book.ringing!!.token
        control.deleteSong(song.id)
        assertFalse(control.repository.book.alarms.single().enabled)
        assertTrue(control.repository.files.audioFile(song.id).exists())
        control.stop("obsolete-token")
        assertEquals(token, control.repository.book.ringing!!.token)
        control.stop(token); control.reconcile()
        assertFalse(control.repository.files.audioFile(song.id).exists())
    }
    @Test fun `notification action opens app settings after denial and channel settings when app access exists`() {
        val context = context()
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        val denied = AlarmPlatform.settings(context, AlarmAccess.NOTIFICATIONS)
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, denied.action)
        assertEquals(context.packageName, denied.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
        shadowOf(manager).setNotificationsEnabled(true)
        val channel = AlarmPlatform.settings(context, AlarmAccess.NOTIFICATIONS)
        assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel.action)
        assertEquals(AlarmPlatform.CHANNEL, channel.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
    }
    @Test fun `receiver starts only the explicit foreground ringing path for delivered alarms`() {
        val context = context()
        var start: Intent? = null
        val intercepted = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): android.content.ComponentName? {
                start = service; return service.component
            }
        }
        AlarmReceiver().onReceive(intercepted, Intent(AlarmPlatform.FIRE).putExtra("revision", 7L).putExtra("at", 1000L))
        assertEquals(AlarmService::class.java.name, start?.component?.className)
        assertEquals(7L, start?.getLongExtra("revision", -1))
        assertEquals(1000L, start?.getLongExtra("at", -1))
    }
    @Test fun `Undo restores a deleted alarm without forgetting its consumed date or overwriting another row`() {
        val context = context(); val (control, song) = ready(context)
        control.save(MusicAlarm("morning", 7, 30, "Morning", setOf(1, 2, 3), listOf(song.id), true))
        val original = control.repository.book.alarms.single().copy(consumedDate = "2026-11-04")
        control.deleteAlarm(original.id)
        control.restoreAlarm(original)
        val restored = control.repository.book.alarms.single()
        assertEquals(original.id, restored.id)
        assertEquals(original.label, restored.label)
        assertEquals(original.weekdays, restored.weekdays)
        assertEquals(original.songIds, restored.songIds)
        assertEquals(original.consumedDate, restored.consumedDate)
        assertTrue(restored.nextAtMillis > System.currentTimeMillis())
        assertThrows(IllegalArgumentException::class.java) { control.restoreAlarm(original) }
        assertEquals(restored, control.repository.book.alarms.single())
    }

}
