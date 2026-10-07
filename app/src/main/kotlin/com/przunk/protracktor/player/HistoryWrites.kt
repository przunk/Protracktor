// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One ordered writer: delayed records/qualification cannot cross a History clear. Main-thread API. */
internal class HistoryWrites(
    scope: CoroutineScope,
    private val record: suspend (TrackRef) -> Unit,
    private val qualify: suspend (String) -> Unit,
    private val erase: suspend () -> Unit,
    private val failed: (Throwable) -> Unit,
) {
    data class Revision(val number: Long = 0, val clearing: Boolean = false)
    private val changes = MutableStateFlow(Revision())
    val revision = changes.asStateFlow()
    var generation: Long = 0
        private set
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) command()
        }
    }

    fun record(track: TrackRef, ticket: Long) = enqueue(ticket) { record(track) }

    fun qualify(result: ListeningSession.Qualified) = enqueue(result.historyGeneration) {
        qualify(result.trackId)
        changes.value = Revision(changes.value.number + 1, changes.value.clearing)
    }

    fun clear(): Deferred<Unit> {
        val ticket = ++generation
        changes.value = Revision(changes.value.number + 1, clearing = true)
        val done = CompletableDeferred<Unit>()
        commands.trySend {
            try {
                erase()
                done.complete(Unit)
            } catch (cancelled: CancellationException) {
                done.cancel(cancelled)
                throw cancelled
            } catch (error: Exception) {
                done.completeExceptionally(error)
                failed(error)
            } finally {
                if (ticket == generation) changes.value = Revision(changes.value.number + 1)
            }
        }.getOrThrow()
        return done
    }

    private fun enqueue(ticket: Long, write: suspend () -> Unit) {
        commands.trySend {
            if (ticket == generation) {
                try {
                    write()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failed(error)
                }
            }
        }.getOrThrow()
    }
}
