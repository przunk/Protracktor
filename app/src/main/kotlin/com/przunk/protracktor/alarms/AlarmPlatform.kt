// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

internal object AlarmPlatform {
    const val FIRE = "com.przunk.protracktor.ALARM_FIRE"
    const val TEST = "com.przunk.protracktor.ALARM_TEST"
    const val STOP = "com.przunk.protracktor.ALARM_STOP"
    const val CHANNEL = "music_alarms"
    const val NOTIFICATION = 7401
    const val STARTUP_NOTIFICATION = 7400
    const val STARTUP_CHANNEL = "music_alarm_preparation"
    fun clock(context: Context) = AlarmClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))

    fun notifications(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CHANNEL)
        return manager.areNotificationsEnabled() && (channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH)
    }
    fun exact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun fullScreen(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun muted(context: Context) = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0

    fun settings(context: Context, access: AlarmAccess): Intent {
        val packageUri = android.net.Uri.parse("package:${context.packageName}")
        return when (access) {
            AlarmAccess.EXACT -> Intent(if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            AlarmAccess.FULL_SCREEN -> Intent(if (Build.VERSION.SDK_INT >= 34) Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            AlarmAccess.VOLUME -> Intent(Settings.ACTION_SOUND_SETTINGS)
            AlarmAccess.NOTIFICATIONS -> {
                val manager = context.getSystemService(NotificationManager::class.java)
                // Main already offers the runtime prompt. Settings remains usable after a permanent denial.
                if (!manager.areNotificationsEnabled()) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
            }
        }
    }

    private fun operation(context: Context, revision: Long = 0, at: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(context, 7400, Intent(context, AlarmReceiver::class.java).setAction(FIRE)
            .putExtra("revision", revision).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Returns the actually armed time, never merely the requested next occurrence. */
    fun schedule(context: Context, book: AlarmBook): Long? {
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = AlarmPlanner.earliest(book.alarms)
        if (at == null || !exact(context) || !notifications(context) || !fullScreen(context)) {
            manager.cancel(operation(context)); return null
        }
        val show = PendingIntent.getActivity(context, 7400, Intent(context, AlarmsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation(context, book.revision, at)); at
        } catch (_: SecurityException) { manager.cancel(operation(context)); null }
    }
}
