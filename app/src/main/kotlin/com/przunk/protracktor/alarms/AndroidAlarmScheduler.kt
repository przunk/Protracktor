// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal sealed interface AlarmScheduleResult {
    data class Armed(val atMillis: Long, val revision: Long) : AlarmScheduleResult
    data object Idle : AlarmScheduleResult
    data object Disabled : AlarmScheduleResult
    data class Blocked(val remedies: List<AlarmAccess>) : AlarmScheduleResult
    data class Failed(val cause: SecurityException) : AlarmScheduleResult
    val armedAt: Long? get() = (this as? Armed)?.atMillis
}

/** A stored occurrence is a request; only a successful Android call produces Armed. */
internal object AndroidAlarmScheduler {
    private fun operation(context: Context, revision: Long = 0, at: Long = 0, create: Boolean = true): PendingIntent? =
        PendingIntent.getBroadcast(context, 7400, Intent(context, AlarmReceiver::class.java).setAction(AlarmPlatform.FIRE)
            .putExtra("revision", revision).putExtra("at", at),
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE)

    fun cancel(context: Context, retire: Boolean = false) {
        operation(context, create = false)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            if (retire) it.cancel()
        }
    }

    fun schedule(context: Context, book: AlarmBook): AlarmScheduleResult {
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { cancel(context, retire = true); return AlarmScheduleResult.Disabled }
        val at = AlarmPlanner.earliest(book.alarms)
        if (at == null) { cancel(context); return AlarmScheduleResult.Idle }
        val readiness = AlarmReadiness.inspect(context)
        if (!readiness.canSchedule) { cancel(context); return AlarmScheduleResult.Blocked(readiness.blockers) }
        val show = PendingIntent.getActivity(context, 7400, Intent(context, AlarmsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, show),
                checkNotNull(operation(context, book.revision, at)))
            AlarmScheduleResult.Armed(at, book.revision)
        } catch (failure: SecurityException) {
            cancel(context)
            AlarmScheduleResult.Failed(failure)
        }
    }
}
