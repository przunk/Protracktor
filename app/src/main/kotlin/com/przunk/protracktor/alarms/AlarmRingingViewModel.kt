// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AlarmRingingState(val active: AlarmChallenge? = null, val waiting: Boolean = true,
    val failed: Boolean = false, val busy: Boolean = false, val completion: AlarmCompletion? = null,
    val wrongChoice: String? = null, val commandFailed: Boolean = false)

/** Acknowledgements follow durable command results; the screen never receives the correct ID. */
internal class AlarmRingingViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val ready = CompletableDeferred<AlarmCoordinator>()
    private val mutable = MutableStateFlow(AlarmRingingState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var coordinator: AlarmCoordinator? = null
    private var stopping: Pair<String, Long>? = null
    init {
        viewModelScope.launch {
            try {
                val control = withContext(Dispatchers.IO) { AlarmCoordinator.from(context).also { it.reconcile() } }
                coordinator = control
                ready.complete(control)
                if (control.readFailure != null) { mutable.value = mutable.value.copy(failed = true, waiting = false); return@launch }
                launch {
                    control.repository.state.collect { book ->
                        val before = mutable.value
                        val active = book.ringing?.presented()
                        // Keep the answer snapshot until its commit is acknowledged, but never hide a new session.
                        if (!before.busy || active != null && active.token != before.active?.token) {
                            if (before.busy) ++generation
                            mutable.value = before.copy(active = active, busy = false,
                                completion = if (active != null) null else before.completion ?: before.active?.let { AlarmCompletion.FINISHED },
                                wrongChoice = if (active?.token == before.active?.token) before.wrongChoice else null)
                        }
                    }
                }
                delay(2500)
                mutable.value = mutable.value.copy(waiting = false)
            } catch (cancelled: CancellationException) { ready.cancel(cancelled); throw cancelled }
            catch (failure: Exception) {
                log(failure); ready.completeExceptionally(failure)
                mutable.value = mutable.value.copy(waiting = false, failed = true)
            }
        }
    }
    fun answer(token: String, choice: String) {
        val before = mutable.value
        if (before.busy || before.active?.token != token) return
        val request = ++generation
        mutable.value = before.copy(busy = true, commandFailed = false)
        viewModelScope.launch {
            val result = try { withContext(Dispatchers.IO) { ready.await().answer(token, choice) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { log(failure); AlarmAnswerResult.FAILED }
            if (request != generation) return@launch
            val active = ready.await().repository.book.ringing?.presented()
            if (active != null && active.token != token) {
                mutable.value = AlarmRingingState(active = active, waiting = false)
                return@launch
            }
            mutable.value = mutable.value.copy(active = active, busy = false,
                completion = if (active != null) null else if (result == AlarmAnswerResult.CORRECT) AlarmCompletion.ANSWERED else AlarmCompletion.FINISHED,
                wrongChoice = choice.takeIf { result == AlarmAnswerResult.INCORRECT },
                commandFailed = result == AlarmAnswerResult.FAILED)
        }
    }
    fun stop(token: String) {
        if (mutable.value.active?.token != token || stopping?.first == token) return
        val request = ++generation
        stopping = token to request
        mutable.value = mutable.value.copy(busy = true, commandFailed = false)
        viewModelScope.launch {
            var failed = false
            try { withContext(Dispatchers.IO) { ready.await().stop(token) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { log(failure); failed = true }
            finally {
                if (stopping == token to request) stopping = null
                val current = coordinator?.repository?.book?.ringing
                if (current?.token in listOf(null, token)) context.stopService(Intent(context, AlarmService::class.java))
            }
            if (request == generation) {
                val active = ready.await().repository.book.ringing?.presented()
                mutable.value = mutable.value.copy(active = active, busy = false,
                    completion = if (active == null) AlarmCompletion.FINISHED else null, commandFailed = failed)
            }
        }
    }
    fun serviceFailed(failure: RuntimeException) {
        log(failure); mutable.value = mutable.value.copy(commandFailed = true)
    }
    private fun log(failure: Exception) { android.util.Log.w("Protracktor", "Alarm ringing command failed", failure) }
}
