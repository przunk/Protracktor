// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms.api

import android.content.Context
import android.content.Intent
import com.przunk.protracktor.alarms.AlarmCoordinator
import com.przunk.protracktor.alarms.AlarmMusicPreparer
import com.przunk.protracktor.alarms.AlarmRingingActivity
import com.przunk.protracktor.alarms.AlarmService
import com.przunk.protracktor.alarms.AlarmsActivity
import com.przunk.protracktor.data.SongDbMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal data class AlarmMusicInput(val bytes: ByteArray, val companions: List<Pair<String, ByteArray>>, val format: String,
    val metadata: SongDbMetadata.Entry? = null, val credits: List<SongDbMetadata.Credit> = emptyList())

/** Application boundary: ordinary playback never owns the alarm ledger or Android components. */
internal sealed interface AlarmPlaybackGate {
    data object Loading : AlarmPlaybackGate
    data object Clear : AlarmPlaybackGate
    data class Ringing(val token: String, val deadlineElapsed: Long) : AlarmPlaybackGate
}

internal object MusicAlarms {
    private val observations = java.util.IdentityHashMap<Context, com.przunk.protracktor.alarms.AlarmPlaybackObservation>()
    @Synchronized private fun observation(context: Context) = observations.getOrPut(context.applicationContext) {
        com.przunk.protracktor.alarms.AlarmPlaybackObservation(context.applicationContext)
    }
    fun warmUp(context: Context) { if (enabled) observation(context) }
    @Synchronized fun releasePlayback(context: Context) { observations.remove(context.applicationContext)?.close() }
    fun playbackGate(context: Context): AlarmPlaybackGate =
        if (enabled) observation(context).current() else AlarmPlaybackGate.Clear
    suspend fun awaitPlaybackGate(context: Context): AlarmPlaybackGate =
        if (enabled) observation(context).awaitReady() else AlarmPlaybackGate.Clear

    /** Interrupt only an existing ordinary player; delivery must not initialize its database. */
    fun interruptExistingPlayback() { com.przunk.protracktor.player.PlaybackController.pauseExistingForAlarm() }

    val enabled: Boolean get() = com.przunk.protracktor.BuildConfig.MUSIC_ALARMS_ENABLED

    fun openManager(context: Context) {
        if (!enabled) {
            android.widget.Toast.makeText(context, com.przunk.protracktor.R.string.alarm_feature_unavailable, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        context.startActivity(Intent(context, AlarmsActivity::class.java))
    }

    /** Called on the lifecycle worker; waking an alarm must not create the ordinary player. */
    suspend fun reconcile(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) { com.przunk.protracktor.alarms.AlarmFeatureLifecycle.deactivate(context); return@withContext false }
        val control = AlarmCoordinator.from(context)
        control.reconcile()
        control.readFailure == null && control.repository.book.ringing != null && !AlarmService.running
    }

    fun openRinging(context: Context) {
        if (!enabled) return
        context.startForegroundService(Intent(context, AlarmService::class.java))
        context.startActivity(Intent(context, AlarmRingingActivity::class.java))
    }

    /** Prepares [selected] into the alarm library and hands back the prepared tune's id (B62). */
    suspend fun prepare(context: Context, selected: AlarmMusicSelection, load: suspend () -> AlarmMusicInput): String {
        check(enabled) { context.getString(com.przunk.protracktor.R.string.alarm_feature_unavailable) }
        withContext(Dispatchers.IO) {
            val control = AlarmCoordinator.from(context)
            check(control.readFailure == null) { "Alarm data cannot be opened; it has been preserved" }
        }
        val song = AlarmMusicPreparer.add(context, selected.ref, selected.subsong, load)
        withContext(Dispatchers.IO) { AlarmCoordinator.from(context).reconcile() }
        return song.id
    }

    fun isRinging(context: Context): Boolean = playbackGate(context) is AlarmPlaybackGate.Ringing

    fun playbackInterruption(context: Context): Flow<Boolean> = if (!enabled) kotlinx.coroutines.flow.flowOf(false)
        else observation(context).state.map { it is AlarmPlaybackGate.Ringing }
}
