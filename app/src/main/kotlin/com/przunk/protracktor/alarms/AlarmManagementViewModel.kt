// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.protracktor.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class AlarmManagementState(val book: AlarmBook, val schedule: AlarmScheduleResult,
    val usableIds: Set<String>, val readiness: AlarmReadiness?, val dataError: Boolean)

/** Screen lifetime owns commands; projections never stat prepared files during composition. */
internal class AlarmManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val ready = CompletableDeferred<AlarmCoordinator>()
    private val commands = Mutex()
    private val refreshes = MutableStateFlow(0L)
    private val mutable = MutableStateFlow<AlarmManagementState?>(null)
    val state = mutable.asStateFlow()
    private val notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = notices.asSharedFlow()
    /** Where *Add music* finds tunes (B62). */
    val sources: AlarmMusicSources = StoredAlarmMusicSources(context)
    /**
     * Tunes picked in *Add music*, prepared through the player's loader: only the player can read a
     * tune's file and facts. Asking for the player here starts it when it is not running yet, which
     * loads its state and plays nothing.
     */
    val music = AlarmMusicQueue(viewModelScope,
        room = { AlarmLimits.MAX_SONGS - (mutable.value?.book?.songs?.size ?: 0) },
        prepare = { ref ->
            val player = com.przunk.protracktor.player.PlaybackController.get(context)
            com.przunk.protracktor.alarms.api.MusicAlarms.prepare(context, player.alarmSelection(ref)) { player.alarmMusicInput(ref) }
        })
    init {
        viewModelScope.launch {
            try {
                val control = withContext(Dispatchers.IO) {
                    AlarmNotifications.channel(context)
                    AlarmCoordinator.from(context).also { it.reconcile() }
                }
                ready.complete(control)
                launch(Dispatchers.IO) {
                    combine(control.repository.state, control.scheduling, refreshes) { book, schedule, refresh ->
                        Triple(book, schedule, refresh)
                    }.collect { (book, schedule, refresh) ->
                        val projected = project(control, book, schedule)
                        if (book.revision == control.repository.book.revision && schedule == control.scheduling.value && refresh == refreshes.value)
                            mutable.value = projected
                    }
                }
            } catch (cancelled: CancellationException) { ready.cancel(cancelled); throw cancelled }
            catch (failure: Exception) {
                log(failure); ready.completeExceptionally(failure)
                mutable.value = AlarmManagementState(AlarmBook(), AlarmScheduleResult.Idle, emptySet(), null, true)
            }
        }
    }
    private fun project(control: AlarmCoordinator, book: AlarmBook = control.repository.book,
        schedule: AlarmScheduleResult = control.scheduling.value) = AlarmManagementState(book, schedule,
        if (control.readFailure == null) book.songs.filter { control.usable(it, book) }.mapTo(mutableSetOf()) { it.id } else emptySet(),
        AlarmReadiness.inspect(context), control.readFailure != null)

    fun refresh() = viewModelScope.launch {
        try { commands.withLock { withContext(Dispatchers.IO) {
            val control = ready.await(); control.reconcile(); refreshes.value++
        } } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { log(failure); notices.tryEmit(R.string.alarm_failure) }
    }
    suspend fun save(alarm: MusicAlarm): AlarmSaveResult = saveCommand { it.save(alarm) }
    suspend fun restore(alarm: MusicAlarm): AlarmSaveResult = saveCommand { it.restoreAlarm(alarm) }
    private suspend fun saveCommand(action: (AlarmCoordinator) -> AlarmScheduleResult): AlarmSaveResult = commands.withLock {
        try { withContext(Dispatchers.IO) {
            val control = ready.await()
            val result = AlarmSaveResult.Saved(action(control))
            result
        } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { log(failure); AlarmSaveResult.Failed }
    }
    suspend fun delete(id: String): Boolean = commands.withLock {
        try { withContext(Dispatchers.IO) { ready.await().deleteAlarm(id) }; true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { log(failure); notices.tryEmit(R.string.alarm_failure); false }
    }
    fun removeMusic(id: String) = viewModelScope.launch {
        commands.withLock {
            try { withContext(Dispatchers.IO) { ready.await().deleteSong(id) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { log(failure); notices.tryEmit(R.string.alarm_failure) }
        }
    }
    fun cancelPreparation(id: String) {
        when (AlarmMusicPreparer.cancel(id)) {
            AlarmPreparationCancellation.CANCELLED -> Unit
            AlarmPreparationCancellation.CHANGED, AlarmPreparationCancellation.FINISHED -> notices.tryEmit(R.string.alarm_preparation_changed)
        }
    }
    private fun log(failure: Exception) { android.util.Log.w("Protracktor", "Alarm management command failed", failure) }
}
