// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { AlarmFeatureLifecycle.deactivate(context) }
                catch (failure: Exception) { android.util.Log.w("Protracktor", "Alarm shutdown could not finish", failure) }
                finally { pending?.finish() }
            }
            return
        }
        if (intent.action == AlarmPlatform.FIRE) {
            try { context.startForegroundService(Intent(context, AlarmService::class.java).setAction(AlarmPlatform.FIRE)
                .putExtra("revision", intent.getLongExtra("revision", -1)).putExtra("at", intent.getLongExtra("at", -1))) }
            catch (failure: RuntimeException) { android.util.Log.w("Protracktor", "Alarm service could not start", failure) }
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val coordinator = AlarmCoordinator.from(context)
                if (intent.action == AlarmPlatform.STOP) {
                    val token = intent.getStringExtra("token").orEmpty()
                    if (coordinator.repository.book.ringing?.token == token) {
                        try { coordinator.stop(token) } finally {
                            if (coordinator.repository.book.ringing?.token in listOf(null, token))
                                context.stopService(Intent(context, AlarmService::class.java))
                        }
                    }
                }
                else coordinator.reconcile(resetTime = intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED))
            } catch (failure: Exception) { android.util.Log.w("Protracktor", "Alarm reconciliation failed", failure) }
            finally { pending?.finish() }
        }
    }
}
