// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
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
class AlarmFeatureLifecycleTest {
    @get:Rule val temp = TemporaryFolder()
    private val stops = mutableListOf<String?>()
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
        private val root = temp.newFolder()
        override fun getApplicationContext(): Context = this
        override fun createDeviceProtectedStorageContext(): Context = this
        override fun getNoBackupFilesDir(): File = root
        override fun stopService(service: Intent): Boolean { stops += service.component?.className; return super.stopService(service) }
    }
    private fun stored(context: Context): AlarmBook {
        val song = AlarmSong("song", "a".repeat(64), "Music", platformId = "amiga", durationMillis = 1000)
        val alarm = MusicAlarm("alarm", 7, 30, "Morning", setOf(1, 5), listOf(song.id), true,
            System.currentTimeMillis() + 60_000, "2026-11-04", "2026-11-01")
        val book = AlarmBook(7, listOf(song), listOf(alarm))
        val files = AlarmFileStore.from(context)
        files.write(book); files.audioFile(song.id).writeBytes(byteArrayOf(1,2,3))
        return book
    }
    @Test fun `compatibility shutdown uses the original installed component identity`() {
        val context = context()
        val target = AlarmPlatform.serviceIntent(context).component!!
        assertEquals(context.packageName, target.packageName)
        assertEquals(AlarmService::class.java.name, target.className)
    }
    @Test fun `deactivation retires the original capability and own notifications without rewriting music or schedules`() {
        val context = context(); stored(context)
        val directory = AlarmFileStore.directory(context)
        val ledger = File(directory, "alarms-v1.json").readBytes()
        val audio = File(directory, "song.m4a").readBytes()
        val manager = context.getSystemService(NotificationManager::class.java)
        AlarmNotifications.channel(context)
        fun notice() = Notification.Builder(context, AlarmPlatform.CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).build()
        manager.notify(999, notice()); manager.notify(AlarmPlatform.NOTIFICATION, notice()); manager.notify(AlarmPlatform.STARTUP_NOTIFICATION, notice())
        val operation = PendingIntent.getBroadcast(context, 7400, Intent(context, AlarmReceiver::class.java).setAction(AlarmPlatform.FIRE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, operation)
        AlarmFeatureLifecycle.deactivate(context)
        assertNull(shadowOf(alarms).peekNextScheduledAlarm())
        assertEquals(listOf(AlarmService::class.java.name), stops)
        assertEquals(setOf(999), manager.activeNotifications.map { it.id }.toSet())
        assertArrayEquals(ledger, File(directory, "alarms-v1.json").readBytes())
        assertArrayEquals(audio, File(directory, "song.m4a").readBytes())
        assertTrue(File(directory, "suspended-by-build-v1").exists())
    }
    @Test fun `restoring the feature keeps settings and audio but requires explicit schedule rearming`() {
        val context = context(); val before = stored(context)
        AlarmFeatureLifecycle.deactivate(context)
        val control = AlarmCoordinator.from(context); control.reconcile()
        val after = control.repository.book
        assertEquals(before.songs, after.songs)
        assertEquals(before.alarms.single().copy(enabled = false, nextAtMillis = 0, occurrenceDate = null), after.alarms.single())
        assertArrayEquals(byteArrayOf(1,2,3), control.repository.files.audioFile("song").readBytes())
        assertNull(control.scheduling.value.armedAt)
        assertFalse(File(control.repository.files.directory, "suspended-by-build-v1").exists())
        control.save(after.alarms.single().copy(enabled = true))
        assertTrue(control.repository.book.alarms.single().enabled)
        assertNotNull(control.scheduling.value.armedAt)
    }
    @Test fun `newer data survives suspension and remains blocked when the feature returns`() {
        val context = context(); val directory = AlarmFileStore.directory(context).apply { mkdirs() }
        val ledger = File(directory, "alarms-v1.json").apply { writeText("{\"version\":99,\"keep\":\"future data\"}") }
        val bytes = ledger.readBytes()
        AlarmFeatureLifecycle.deactivate(context)
        val control = AlarmCoordinator.from(context); control.reconcile()
        assertNotNull(control.readFailure)
        assertNull(control.scheduling.value.armedAt)
        assertArrayEquals(bytes, ledger.readBytes())
        assertTrue(File(directory, "suspended-by-build-v1").exists())
    }
    @Test fun `unused feature shutdown creates neither alarm ledger nor suspension state`() {
        val context = context(); val directory = AlarmFileStore.directory(context)
        assertFalse(directory.exists())
        AlarmFeatureLifecycle.deactivate(context)
        assertFalse(directory.exists())
    }
}
