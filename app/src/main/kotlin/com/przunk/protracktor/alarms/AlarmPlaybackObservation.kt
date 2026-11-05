// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.os.SystemClock
import com.przunk.protracktor.alarms.api.AlarmPlaybackGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** No file paths, settings reads or repository creation on a transport callback. */
internal class AlarmPlaybackObservation(context: Context) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private val mutable = MutableStateFlow<AlarmPlaybackGate>(AlarmPlaybackGate.Loading)
    val state = mutable.asStateFlow()
    @Volatile private var coordinator: AlarmCoordinator? = null
    @Volatile private var bootCount = -1
    init {
        scope.launch {
            try {
                bootCount = AlarmPlatform.clock(context).bootCount
                coordinator = AlarmCoordinator.from(context)
                ready.complete(Unit)
                checkNotNull(coordinator).repository.state.collectLatest {
                    val next = current()
                    mutable.value = next
                    if (next is AlarmPlaybackGate.Ringing) {
                        delay((next.deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                        mutable.value = current()
                    }
                }
            } catch (failure: kotlinx.coroutines.CancellationException) {
                ready.cancel(failure); throw failure
            } catch (failure: Exception) {
                android.util.Log.w("Protracktor", "Alarm playback state is unavailable", failure)
                mutable.value = AlarmPlaybackGate.Clear
                ready.complete(Unit)
            }
        }
    }
    fun current(): AlarmPlaybackGate {
        if (!ready.isCompleted) return AlarmPlaybackGate.Loading
        val control = coordinator ?: return AlarmPlaybackGate.Clear
        return AlarmInterruption.gate(control.repository.book.ringing, control.readFailure == null,
            SystemClock.elapsedRealtime(), bootCount)
    }
    suspend fun awaitReady(): AlarmPlaybackGate { ready.await(); return current() }
    override fun close() { scope.cancel() }
}

internal object AlarmInterruption {
    fun gate(active: RingingAlarm?, available: Boolean, elapsed: Long, boot: Int): AlarmPlaybackGate =
        if (available && active != null && !active.expired(elapsed, boot))
            AlarmPlaybackGate.Ringing(active.token, active.deadlineElapsed) else AlarmPlaybackGate.Clear
}
