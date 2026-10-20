// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/** A source is playback ownership, not whichever screen happens to be visible. */
sealed interface QueueSource {
    data object None : QueueSource
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
data class ActiveQueue(
    val source: QueueSource,
    val order: PlayQueue,
    val entryIds: List<Long> = order.tracks.indices.map(Int::toLong),
    val nextEntryId: Long = order.tracks.size.toLong(),
    val omitted: Set<Long> = emptySet(),
) {
    val current: TrackRef? get() = order.current
    val currentEntryId: Long? get() = order.currentIndex?.let(entryIds::getOrNull)
    val currentAvailable: Boolean get() = current != null && currentEntryId !in omitted
    val playbackIndices: List<Int> get() = if (source is QueueSource.Random) order.tracks.indices.toList() else order.playbackIndices
    val visibleIndices: List<Int> get() = playbackIndices.filter { entryIds[it] !in omitted }
    val fromHistory: Boolean get() = (source as? QueueSource.Collection)?.origin == SessionSource.History
    fun belongsTo(playlistId: Long): Boolean = (source as? QueueSource.Playlist)?.id == playlistId

    val hasNext: Boolean get() = when (source) {
        is QueueSource.Random -> true
        QueueSource.None, QueueSource.External -> false
        is QueueSource.Playlist, is QueueSource.Collection -> finiteMove(forward = true) != null
    }
    val hasPrevious: Boolean get() = when (source) {
        is QueueSource.Random -> (0 until (order.currentIndex ?: 0)).any { entryIds[it] !in omitted }
        QueueSource.None, QueueSource.External -> false
        is QueueSource.Playlist, is QueueSource.Collection -> finiteMove(forward = false) != null
    }

    fun select(index: Int): ActiveQueue = copy(order = order.startAt(index))

    fun jump(index: Int): ActiveQueue = copy(order = order.jumpTo(index))
    fun omit(id: Long): ActiveQueue = if (id in entryIds) copy(omitted = omitted + id) else this
    fun restore(id: Long): ActiveQueue = copy(omitted = omitted - id)

    /** Match occurrences FIFO, not a last-index-by-file map; Random may repeat one file. */
    fun withOrder(next: PlayQueue): ActiveQueue {
        if (order === next) return this
        if (order.tracks === next.tracks) return copy(order = next)
        if (order.tracks.map { it.id } == next.tracks.map { it.id }) return copy(order = next)
        val oldIds = order.tracks.indices.groupBy { order.tracks[it].id }
            .mapValues { (_, indices) -> ArrayDeque(indices.map { entryIds[it] }) }
        var fresh = nextEntryId
        val ids = next.tracks.map { oldIds[it.id]?.removeFirstOrNull() ?: fresh++ }
        return copy(order = next, entryIds = ids, nextEntryId = fresh, omitted = omitted.intersect(ids.toSet()))
    }

    private fun finiteMove(forward: Boolean): ActiveQueue? {
        if (order.tracks.indices.none { entryIds[it] !in omitted }) return null
        var at = order
        repeat(order.tracks.size + 1) {
            if (forward && !at.hasNext || !forward && !at.hasPrevious) return null
            at = if (forward) at.next() else at.previous()
            if (entryIds[at.currentIndex!!] !in omitted) return copy(order = at)
        }
        return null
    }

    fun next(): QueueStep = when (source) {
        is QueueSource.Random -> QueueStep.RandomNext
        QueueSource.None, QueueSource.External -> QueueStep.Unchanged
        is QueueSource.Playlist, is QueueSource.Collection ->
            finiteMove(forward = true)?.let(QueueStep::Play) ?: QueueStep.Unchanged
    }

    fun previous(): QueueStep = when (source) {
        is QueueSource.Random -> if (hasPrevious) QueueStep.RandomPrevious else QueueStep.Unchanged
        QueueSource.None, QueueSource.External -> QueueStep.Unchanged
        is QueueSource.Playlist, is QueueSource.Collection ->
            finiteMove(forward = false)?.let(QueueStep::Play) ?: QueueStep.Unchanged
    }

    fun ended(): QueueStep {
        if (order.repeat == RepeatMode.ONE && currentAvailable) return QueueStep.Replay
        return when (source) {
            is QueueSource.Random -> QueueStep.RandomNext
            QueueSource.None, QueueSource.External -> QueueStep.Stop
            is QueueSource.Playlist, is QueueSource.Collection -> {
                finiteMove(forward = true)?.let(QueueStep::Play) ?: QueueStep.Stop
            }
        }
    }
}
