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
    val enabled: Boolean get() = true

    fun openManager(context: Context) = context.startActivity(Intent(context, AlarmsActivity::class.java))

    /** Called on the lifecycle worker; waking an alarm must not create the ordinary player. */
    fun reconcile(context: Context): Boolean {
        val control = AlarmCoordinator.from(context)
        control.reconcile()
        return control.repository.book.ringing != null && !AlarmService.running
    }

    fun openRinging(context: Context) {
        context.startForegroundService(Intent(context, AlarmService::class.java))
        context.startActivity(Intent(context, AlarmRingingActivity::class.java))
    }

    suspend fun prepare(context: Context, selected: AlarmMusicSelection, load: suspend () -> AlarmMusicInput) {
        AlarmMusicPreparer.add(context, selected.ref, selected.subsong, load)
        withContext(Dispatchers.IO) { AlarmCoordinator.from(context).reconcile() }
    }

    fun isRinging(context: Context): Boolean = AlarmCoordinator.isRinging(context)

    fun playbackInterruption(context: Context): Flow<Boolean> = flow {
        val repository = withContext(Dispatchers.IO) { AlarmRepository.from(context) }
        emitAll(repository.state.map { it.ringing != null })
    }
}
