// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.przunk.protracktor.permissions.AccessState
import com.przunk.protracktor.permissions.AppAccess
import com.przunk.protracktor.permissions.SystemAccess

internal enum class AlarmAccess { EXACT, NOTIFICATIONS, FULL_SCREEN, VOLUME }

internal data class AlarmReadiness(
    val exactAccess: AccessState,
    val notificationAccess: AccessState,
    val fullScreenAccess: AccessState,
    val channelReady: Boolean,
    val muted: Boolean,
) {
    val exact get() = exactAccess.allowed
    val notifications get() = notificationAccess.allowed && channelReady
    val fullScreen get() = fullScreenAccess.allowed
    val blockers: List<AlarmAccess> get() = buildList {
        if (!exact) add(AlarmAccess.EXACT)
        if (!notifications) add(AlarmAccess.NOTIFICATIONS)
        if (!fullScreen) add(AlarmAccess.FULL_SCREEN)
    }
    val canSchedule get() = blockers.isEmpty()
    val canPresent get() = notifications && fullScreen

    companion object {
        private fun channelReady(context: Context): Boolean {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(AlarmPlatform.CHANNEL)
            return channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
        }
        fun notifications(context: Context) = SystemAccess.allowed(context, AppAccess.NOTIFICATIONS) && channelReady(context)
        fun settings(context: Context, remedy: AlarmAccess): android.content.Intent = when (remedy) {
            AlarmAccess.EXACT -> SystemAccess.settings(context, AppAccess.EXACT_ALARMS)
            AlarmAccess.FULL_SCREEN -> SystemAccess.settings(context, AppAccess.FULL_SCREEN_ALARMS)
            AlarmAccess.VOLUME -> android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
            AlarmAccess.NOTIFICATIONS -> if (SystemAccess.allowed(context, AppAccess.NOTIFICATIONS))
                SystemAccess.notificationChannel(context, AlarmPlatform.CHANNEL) else SystemAccess.settings(context, AppAccess.NOTIFICATIONS)
        }
        fun inspect(context: Context) = AlarmReadiness(
            SystemAccess.inspect(context, AppAccess.EXACT_ALARMS),
            SystemAccess.inspect(context, AppAccess.NOTIFICATIONS),
            SystemAccess.inspect(context, AppAccess.FULL_SCREEN_ALARMS), channelReady(context),
            context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0,
        )
    }
}
