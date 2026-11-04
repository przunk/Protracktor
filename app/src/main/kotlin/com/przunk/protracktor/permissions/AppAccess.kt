// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

internal enum class AppAccess { CAMERA, NOTIFICATIONS, OVERLAY, EXACT_ALARMS, FULL_SCREEN_ALARMS }
internal enum class AccessState {
    GRANTED, NOT_REQUIRED, REQUEST_NEEDED, SETTINGS_NEEDED, UNAVAILABLE;
    val allowed get() = this == GRANTED || this == NOT_REQUIRED
}

/** System access only. Feature readiness (channels, volume, files) belongs to its consumer. */
internal object SystemAccess {
    fun runtimePermission(access: AppAccess): String? = when (access) {
        AppAccess.CAMERA -> Manifest.permission.CAMERA
        AppAccess.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        AppAccess.OVERLAY, AppAccess.EXACT_ALARMS, AppAccess.FULL_SCREEN_ALARMS -> null
    }

    fun inspect(context: Context, access: AppAccess): AccessState {
        val state = when (access) {
            AppAccess.CAMERA -> if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                AccessState.GRANTED else AccessState.REQUEST_NEEDED
            AppAccess.NOTIFICATIONS -> {
                val runtime = runtimePermission(access)
                when {
                    runtime != null && ContextCompat.checkSelfPermission(context, runtime) != PackageManager.PERMISSION_GRANTED -> AccessState.REQUEST_NEEDED
                    !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() -> AccessState.SETTINGS_NEEDED
                    runtime == null -> AccessState.NOT_REQUIRED
                    else -> AccessState.GRANTED
                }
            }
            AppAccess.OVERLAY -> if (Settings.canDrawOverlays(context)) AccessState.GRANTED else AccessState.SETTINGS_NEEDED
            AppAccess.EXACT_ALARMS -> when {
                Build.VERSION.SDK_INT < 31 -> AccessState.NOT_REQUIRED
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() -> AccessState.GRANTED
                else -> AccessState.SETTINGS_NEEDED
            }
            AppAccess.FULL_SCREEN_ALARMS -> when {
                Build.VERSION.SDK_INT < 34 -> AccessState.NOT_REQUIRED
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() -> AccessState.GRANTED
                else -> AccessState.SETTINGS_NEEDED
            }
        }
        val declaration = when (access) {
            AppAccess.CAMERA -> Manifest.permission.CAMERA
            AppAccess.NOTIFICATIONS -> runtimePermission(access)
            AppAccess.OVERLAY -> Manifest.permission.SYSTEM_ALERT_WINDOW
            AppAccess.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.SCHEDULE_EXACT_ALARM else null
            AppAccess.FULL_SCREEN_ALARMS -> Manifest.permission.USE_FULL_SCREEN_INTENT
        }
        if (!state.allowed && declaration != null) {
            val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            if (declaration !in declared) return AccessState.UNAVAILABLE
        }
        return state
    }

    fun allowed(context: Context, access: AppAccess) = inspect(context, access).allowed

    fun settings(context: Context, access: AppAccess): Intent {
        val packageUri = "package:${context.packageName}".toUri()
        return when (access) {
            AppAccess.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            AppAccess.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            AppAccess.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            AppAccess.EXACT_ALARMS -> Intent(if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            AppAccess.FULL_SCREEN_ALARMS -> Intent(if (Build.VERSION.SDK_INT >= 34) Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
    }

    fun notificationChannel(context: Context, channel: String) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channel)
}
