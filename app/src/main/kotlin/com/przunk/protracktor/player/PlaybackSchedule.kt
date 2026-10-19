// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/** A deliberate request, not a file identity: the same file may be requested repeatedly. */
data class ManualRequest(val id: Long, val track: TrackRef, val fromHistory: Boolean = false)

/** The source cursor is parked while a manual file plays; replay must restore that anchor. */
data class PlaybackOccurrence(
    val id: Long,
    val track: TrackRef,
    val context: ActiveQueue,
    val request: ManualRequest? = null,
) {
    val manual: Boolean get() = request != null
    val fromHistory: Boolean get() = request?.fromHistory ?: context.fromHistory
}

enum class SchedulePlacement { REPLACE, APPEND, PREPEND }

data class PendingRemoval(
    val id: Long, val request: ManualRequest, val index: Int,
    val before: Long?, val after: Long?,
)

sealed interface ScheduledStep {
    data class Select(val schedule: PlaybackSchedule, val occurrence: PlaybackOccurrence) : ScheduledStep
    data class Context(val step: QueueStep, val placement: SchedulePlacement) : ScheduledStep
}

/** Pure file scheduling; subsongs, audio, persistence and Android remain caller-owned. */
data class PlaybackSchedule(
    val pending: List<ManualRequest> = emptyList(),
    val record: List<PlaybackOccurrence> = emptyList(),
    val cursor: Int = -1,
    val nextId: Long = 1,
    val removal: PendingRemoval? = null,
) {
    init {
        require(cursor in -1..record.lastIndex)
        require(record.isEmpty() == (cursor == -1))
    }

    val current: PlaybackOccurrence? get() = record.getOrNull(cursor)
    val forward: PlaybackOccurrence? get() = record.getOrNull(cursor + 1)

    fun enqueue(track: TrackRef, fromHistory: Boolean = false): PlaybackSchedule =
        copy(pending = pending + ManualRequest(nextId, track, fromHistory), nextId = nextId + 1)

    /** Explicit source choice abandons traversal, not deliberate pending requests. */
    fun resetTraversal(): PlaybackSchedule = copy(record = emptyList(), cursor = -1)

    fun contextSelected(context: ActiveQueue, placement: SchedulePlacement): PlaybackSchedule {
        val track = context.current ?: return if (placement == SchedulePlacement.REPLACE) resetTraversal() else this
        val entry = PlaybackOccurrence(nextId, track, context)
        return when (placement) {
            SchedulePlacement.REPLACE -> copy(record = listOf(entry), cursor = 0, nextId = nextId + 1)
            SchedulePlacement.APPEND -> copy(record = record.take(cursor + 1) + entry + record.drop(cursor + 1),
                cursor = cursor + 1, nextId = nextId + 1)
            SchedulePlacement.PREPEND -> copy(record = listOf(entry) + record, cursor = 0, nextId = nextId + 1)
        }
    }

    private fun seeded(context: ActiveQueue): PlaybackSchedule =
        if (current == null && context.current != null) contextSelected(context, SchedulePlacement.REPLACE) else this

    fun next(context: ActiveQueue, completed: Boolean = false): ScheduledStep {
        val state = seeded(context)
        if (completed && context.order.repeat == RepeatMode.ONE && state.current != null)
            return ScheduledStep.Context(QueueStep.Replay, SchedulePlacement.APPEND)
        state.pending.firstOrNull()?.let { return state.playRequest(it.id, context)!! }
        if (state.forward != null) {
            val advanced = state.copy(cursor = state.cursor + 1)
            return ScheduledStep.Select(advanced, requireNotNull(advanced.current))
        }
        return ScheduledStep.Context(if (completed) context.ended() else context.next(), SchedulePlacement.APPEND)
    }

    fun previous(context: ActiveQueue): ScheduledStep {
        val state = seeded(context)
        if (state.cursor > 0) {
            val previous = state.copy(cursor = state.cursor - 1)
            return ScheduledStep.Select(previous, requireNotNull(previous.current))
        }
        return ScheduledStep.Context(context.previous(), SchedulePlacement.PREPEND)
    }

    /** Selecting one request never discards earlier requests or the heard forward suffix. */
    fun playRequest(id: Long, context: ActiveQueue): ScheduledStep.Select? {
        val state = seeded(context)
        val request = state.pending.firstOrNull { it.id == id } ?: return null
        // An editor's never-started list is not a source chosen for automatic continuation.
        val anchor = if (state.record.isEmpty() && context.current == null && context.source !is QueueSource.Random)
            ActiveQueue(QueueSource.None, PlayQueue(emptyList(), shuffle = context.order.shuffle, repeat = context.order.repeat))
        else context
        val entry = PlaybackOccurrence(request.id, request.track, anchor, request)
        val next = state.copy(pending = state.pending.filterNot { it.id == id },
            record = state.record.take(state.cursor + 1) + entry + state.record.drop(state.cursor + 1),
            cursor = state.cursor + 1)
        return ScheduledStep.Select(next, entry)
    }

    fun hasNext(context: ActiveQueue): Boolean = pending.isNotEmpty() || forward != null || context.hasNext
    fun hasPrevious(context: ActiveQueue): Boolean = cursor > 0 || context.hasPrevious

    fun remove(id: Long): PlaybackSchedule {
        val index = pending.indexOfFirst { it.id == id }
        if (index < 0) return this
        val token = PendingRemoval(nextId, pending[index], index,
            pending.getOrNull(index - 1)?.id, pending.getOrNull(index + 1)?.id)
        return copy(pending = pending.filterNot { it.id == id }, removal = token, nextId = nextId + 1)
    }

    fun undo(id: Long): PlaybackSchedule {
        val token = removal?.takeIf { it.id == id } ?: return this
        if (pending.any { it.id == token.request.id } || record.any { it.id == token.request.id })
            return copy(removal = null)
        val after = pending.indexOfFirst { it.id == token.after }
        val before = pending.indexOfFirst { it.id == token.before }
        val index = when {
            after >= 0 -> after
            before >= 0 -> before + 1
            else -> token.index.coerceIn(0, pending.size)
        }
        return copy(pending = pending.take(index) + token.request + pending.drop(index), removal = null)
    }

    fun withLearnedTrack(track: TrackRef): PlaybackSchedule {
        fun improve(ref: TrackRef) = if (ref.id == track.id) track else ref
        fun request(item: ManualRequest) = item.copy(track = improve(item.track))
        return copy(
            pending = pending.map(::request),
            record = record.map { entry -> entry.copy(track = improve(entry.track),
                request = entry.request?.let(::request),
                context = entry.context.copy(order = entry.context.order.copy(tracks = entry.context.order.tracks.map(::improve)))) },
            removal = removal?.let { it.copy(request = request(it.request)) },
        )
    }

    /** Replays cannot overwrite newer edits or mode choices with an old anchor snapshot. */
    fun contextForReplay(entry: PlaybackOccurrence, live: ActiveQueue): ActiveQueue {
        val anchor = entry.context
        if (anchor.source == QueueSource.None) return anchor.copy(order = anchor.order
            .withRepeat(live.order.repeat).withShuffle(live.order.shuffle))
        val sameSource = when {
            anchor.source is QueueSource.Playlist && live.source is QueueSource.Playlist ->
                anchor.source.id == live.source.id
            else -> anchor.source == live.source
        }
        if (!sameSource) return live
        return live.copy(order = live.order.restoringCursorFrom(anchor.order))
    }
}
