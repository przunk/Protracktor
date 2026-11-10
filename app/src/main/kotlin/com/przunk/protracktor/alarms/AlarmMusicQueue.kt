// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.R
import com.przunk.protracktor.player.TrackRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one tune picked in *Add music* has got to. */
internal sealed interface AlarmPickState {
    data object Waiting : AlarmPickState
    data object Preparing : AlarmPickState
    data class Added(val songId: String) : AlarmPickState
    /** [reason] is a string resource: said, never silent. */
    data class Failed(val reason: Int) : AlarmPickState
}

internal data class AlarmPick(val ref: TrackRef, val state: AlarmPickState)

/**
 * The tunes picked in *Add music*, prepared one at a time (B62): the preparer renders audio and
 * takes one job at once, so a second tap waits its turn instead of being refused. Each pick keeps
 * its state for the screen, and a failed one can be picked again.
 *
 * [room] is how many more tunes the library takes; at none, a pick fails with the limit's reason
 * rather than rendering for nothing. [prepare] returns the prepared tune's id.
 */
internal class AlarmMusicQueue(
    private val scope: CoroutineScope,
    private val room: () -> Int,
    private val prepare: suspend (TrackRef) -> String,
) {
    private val mutable = MutableStateFlow<List<AlarmPick>>(emptyList())
    val picks = mutable.asStateFlow()
    private var worker: Job? = null

    /** Queues [ref], unless it is waiting, being prepared or already added. */
    fun add(ref: TrackRef) {
        val known = mutable.value.firstOrNull { it.ref.id == ref.id }
        if (known != null && known.state !is AlarmPickState.Failed) return
        mutable.update { picks -> picks.filterNot { it.ref.id == ref.id } + AlarmPick(ref, AlarmPickState.Waiting) }
        if (worker?.isActive != true) worker = scope.launch { drain() }
    }

    private suspend fun drain() {
        while (true) {
            val next = mutable.value.firstOrNull { it.state == AlarmPickState.Waiting } ?: return
            if (room() <= 0) { set(next.ref, AlarmPickState.Failed(R.string.alarm_music_full)); continue }
            set(next.ref, AlarmPickState.Preparing)
            val outcome = try {
                AlarmPickState.Added(prepare(next.ref))
            } catch (timeout: TimeoutCancellationException) {
                AlarmPickState.Failed(R.string.alarm_prepare_failed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("Protracktor", "Alarm music preparation failed", failure)
                AlarmPickState.Failed(R.string.alarm_prepare_failed)
            }
            set(next.ref, outcome)
        }
    }

    private fun set(ref: TrackRef, state: AlarmPickState) =
        mutable.update { picks -> picks.map { if (it.ref.id == ref.id) it.copy(state = state) else it } }
}
