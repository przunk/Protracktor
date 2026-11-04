// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.net.toUri
import com.przunk.protracktor.R

internal object AlarmNotifications {
    fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(AlarmPlatform.CHANNEL,
            context.getString(R.string.alarm_title), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        })
        manager.createNotificationChannel(NotificationChannel(AlarmPlatform.STARTUP_CHANNEL,
            context.getString(R.string.alarm_loading), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null); enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        })
    }
    fun ringing(context: Context, active: RingingAlarm?, announce: Boolean = false): Notification {
        val local = if (active != null && context.getSystemService(android.os.UserManager::class.java).isUserUnlocked)
            com.przunk.protracktor.AppLocale.wrap(context) else context
        val token = active?.token.orEmpty()
        fun open(action: String) = Intent(context, AlarmRingingActivity::class.java)
            .setAction(action).setData("protracktor-alarm://ring/$token".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val content = PendingIntent.getActivity(context, 7401, open(Intent.ACTION_VIEW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fullScreen = if (active != null && com.przunk.protracktor.permissions.SystemAccess.allowed(context, com.przunk.protracktor.permissions.AppAccess.FULL_SCREEN_ALARMS)) {
            // An explicit immutable alarm capability, handed only to Android's notification UI.
            val options = if (Build.VERSION.SDK_INT >= 35) ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle() else null
            PendingIntent.getActivity(context, 7403, open("com.przunk.protracktor.ALARM_FULLSCREEN"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options)
        } else null
        return Notification.Builder(context, if (active == null) AlarmPlatform.STARTUP_CHANNEL else AlarmPlatform.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_protracktor)
            .setContentTitle(local.getString(if (active == null) R.string.alarm_loading else R.string.alarm_ringing))
            .setContentText(local.getString(if (active?.backup == true) R.string.alarm_backup else R.string.alarm_open_challenge))
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(content).setOngoing(true).setAutoCancel(false).setLocalOnly(true)
            .setOnlyAlertOnce(!announce)
            .apply {
                if (fullScreen != null) setFullScreenIntent(fullScreen, true)
                if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }.build()
    }
}
