// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/** A source is playback ownership, not whichever screen happens to be visible. */
sealed interface QueueSource {
    data class Playlist(val id: Long, val name: String = "") : QueueSource
    data class Collection(val origin: SessionSource) : QueueSource
    data class Random(val session: Long) : QueueSource
    data object External : QueueSource
}

sealed interface QueueStep {
    data class Play(val queue: ActiveQueue) : QueueStep
    data object RandomNext : QueueStep
    data object RandomPrevious : QueueStep
    data object Replay : QueueStep
    data object Stop : QueueStep
    data object Unchanged : QueueStep
}

/** Immutable transport rules shared by screen, notification, headset and natural completion. */
data class ActiveQueue(val source: QueueSource, val order: PlayQueue) {
    val current: TrackRef? get() = order.current
    val fromHistory: Boolean get() = (source as? QueueSource.Collection)?.origin == SessionSource.History
    fun belongsTo(playlistId: Long): Boolean = (source as? QueueSource.Playlist)?.id == playlistId

    val hasNext: Boolean get() = when (source) {
        is QueueSource.Random -> true
        QueueSource.External -> false
        is QueueSource.Playlist, is QueueSource.Collection -> order.hasNext
    }
    val hasPrevious: Boolean get() = when (source) {
        is QueueSource.Random -> (order.currentIndex ?: 0) > 0
        QueueSource.External -> false
        is QueueSource.Playlist, is QueueSource.Collection -> order.hasPrevious
    }

    fun select(index: Int): ActiveQueue = copy(order = order.startAt(index))

    fun next(): QueueStep = when (source) {
        is QueueSource.Random -> QueueStep.RandomNext
        QueueSource.External -> QueueStep.Unchanged
        is QueueSource.Playlist, is QueueSource.Collection ->
            if (order.hasNext) QueueStep.Play(copy(order = order.next())) else QueueStep.Unchanged
    }

    fun previous(): QueueStep = when (source) {
        is QueueSource.Random -> if (hasPrevious) QueueStep.RandomPrevious else QueueStep.Unchanged
        QueueSource.External -> QueueStep.Unchanged
        is QueueSource.Playlist, is QueueSource.Collection ->
            if (order.hasPrevious) QueueStep.Play(copy(order = order.previous())) else QueueStep.Unchanged
    }

    fun ended(): QueueStep {
        if (order.repeat == RepeatMode.ONE && current != null) return QueueStep.Replay
        return when (source) {
            is QueueSource.Random -> QueueStep.RandomNext
            QueueSource.External -> QueueStep.Stop
            is QueueSource.Playlist, is QueueSource.Collection -> {
                val next = order.onTrackEnded()
                if (next == null) QueueStep.Stop else QueueStep.Play(copy(order = next))
            }
        }
    }
}
