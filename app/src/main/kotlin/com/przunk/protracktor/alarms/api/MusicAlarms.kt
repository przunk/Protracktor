// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms.api

import android.content.Context
import android.content.Intent
import com.przunk.protracktor.alarms.AlarmCoordinator
import com.przunk.protracktor.alarms.AlarmMusicPreparer
import com.przunk.protracktor.alarms.AlarmRepository
import com.przunk.protracktor.alarms.AlarmRingingActivity
import com.przunk.protracktor.alarms.AlarmService
import com.przunk.protracktor.alarms.AlarmsActivity
import com.przunk.protracktor.data.SongDbMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal data class AlarmMusicInput(val bytes: ByteArray, val companions: List<Pair<String, ByteArray>>, val format: String,
    val metadata: SongDbMetadata.Entry? = null, val credits: List<SongDbMetadata.Credit> = emptyList())

/** Application boundary: ordinary playback never owns the alarm ledger or Android components. */
internal object MusicAlarms {
    val enabled: Boolean get() = com.przunk.protracktor.BuildConfig.MUSIC_ALARMS_ENABLED

    fun openManager(context: Context) {
        if (!enabled) {
            android.widget.Toast.makeText(context, com.przunk.protracktor.R.string.alarm_feature_unavailable, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        context.startActivity(Intent(context, AlarmsActivity::class.java))
    }

    /** Called on the lifecycle worker; waking an alarm must not create the ordinary player. */
    fun reconcile(context: Context): Boolean {
        if (!enabled) { com.przunk.protracktor.alarms.AlarmFeatureLifecycle.deactivate(context); return false }
        val control = AlarmCoordinator.from(context)
        control.reconcile()
        return control.readFailure == null && control.repository.book.ringing != null && !AlarmService.running
    }

    fun openRinging(context: Context) {
        if (!enabled) return
        context.startForegroundService(Intent(context, AlarmService::class.java))
        context.startActivity(Intent(context, AlarmRingingActivity::class.java))
    }

    suspend fun prepare(context: Context, selected: AlarmMusicSelection, load: suspend () -> AlarmMusicInput) {
        check(enabled) { context.getString(com.przunk.protracktor.R.string.alarm_feature_unavailable) }
        withContext(Dispatchers.IO) {
            val control = AlarmCoordinator.from(context)
            check(control.readFailure == null) { "Alarm data cannot be opened; it has been preserved" }
        }
        AlarmMusicPreparer.add(context, selected.ref, selected.subsong, load)
        withContext(Dispatchers.IO) { AlarmCoordinator.from(context).reconcile() }
    }

    fun isRinging(context: Context): Boolean = enabled && AlarmCoordinator.isRinging(context)

    fun playbackInterruption(context: Context): Flow<Boolean> = if (!enabled) kotlinx.coroutines.flow.flowOf(false) else flow {
        val repository = withContext(Dispatchers.IO) { AlarmRepository.from(context) }
        emitAll(repository.state.map { it.ringing != null })
    }
}
