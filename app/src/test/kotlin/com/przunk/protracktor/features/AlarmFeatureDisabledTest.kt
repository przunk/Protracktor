// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.features

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.BuildConfig
import com.przunk.protracktor.alarms.*
import com.przunk.protracktor.alarms.api.AlarmMusicSelection
import com.przunk.protracktor.alarms.api.MusicAlarms
import com.przunk.protracktor.player.TrackRef
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Executed only with -PprotracktorMusicAlarms=false against the actual generated manifest/code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmFeatureDisabledTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun `disabled artifact removes exclusive permissions and framework entry components`() {
        assertFalse(BuildConfig.MUSIC_ALARMS_ENABLED)
        assertFalse(MusicAlarms.enabled)
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        for (name in listOf("SCHEDULE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT", "RECEIVE_BOOT_COMPLETED", "WAKE_LOCK"))
            assertFalse("Unused alarm permission: $name", "android.permission.$name" in permissions)
        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" in permissions)
        for (type in listOf(AlarmsActivity::class.java, AlarmRingingActivity::class.java))
            assertThrows(PackageManager.NameNotFoundException::class.java) {
                context.packageManager.getActivityInfo(ComponentName(context, type), 0)
            }
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getServiceInfo(ComponentName(context, AlarmService::class.java), 0)
        }
        assertFalse(context.packageManager.getReceiverInfo(ComponentName(context, AlarmReceiver::class.java), 0).exported)
    }
    @Test fun `disabled API does not block music create alarm storage or invoke source preparation`() = runBlocking {
        val directory = AlarmFileStore.directory(context)
        assertFalse(directory.exists())
        assertFalse(MusicAlarms.isRinging(context))
        assertFalse(MusicAlarms.playbackInterruption(context).first())
        var sourceRead = false
        val failure = runCatching {
            MusicAlarms.prepare(context, AlarmMusicSelection(TrackRef("content://song.mod", "Song"), null)) {
                sourceRead = true; error("Disabled feature read the source")
            }
        }.exceptionOrNull()
        assertNotNull(failure)
        assertFalse(sourceRead)
        assertFalse(directory.exists())
        assertFalse(MusicAlarms.reconcile(context))
        assertFalse(directory.exists())
    }
    @Test fun `legacy explicit delivery reaches only compatibility cleanup and never starts audio`() {
        val operation = PendingIntent.getBroadcast(context, 7400, Intent(context, AlarmReceiver::class.java).setAction(AlarmPlatform.FIRE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, operation)
        context.sendBroadcast(Intent(context, AlarmReceiver::class.java).setAction(AlarmPlatform.FIRE))
        val deadline = System.nanoTime() + 5_000_000_000L
        // Inspection must not consume the capability that cleanup is expected to retire.
        while (shadowOf(alarms).peekNextScheduledAlarm() != null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        assertNull(shadowOf(alarms).peekNextScheduledAlarm())
        assertFalse(AlarmService.running)
        assertFalse(AlarmFileStore.directory(context).exists())
    }
}
