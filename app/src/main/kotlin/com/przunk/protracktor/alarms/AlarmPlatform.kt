// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.content.Intent
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
    private const val SERVICE_COMPONENT = "com.przunk.protracktor.alarms.AlarmService"
    // OFF removes the manifest entry, so R8 may rename/remove the implementation class.
    fun serviceIntent(context: Context) = Intent().setClassName(context.packageName, SERVICE_COMPONENT)
    fun clock(context: Context) = AlarmClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))

}
