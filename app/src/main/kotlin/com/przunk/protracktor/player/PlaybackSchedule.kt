// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/** Identity belongs to a deliberate occurrence, never to its file. */
data class ManualRequest(val id: Long, val track: TrackRef, val fromHistory: Boolean = false)

data class PlaybackOccurrence(
    val id: Long, val track: TrackRef, val context: ActiveQueue, val request: ManualRequest? = null,
) {
    val manual: Boolean get() = request != null
    val fromHistory: Boolean get() = request?.fromHistory ?: context.fromHistory
}

enum class SchedulePlacement { REPLACE, APPEND, PREPEND }
data class PendingRemoval(val id: Long, val request: ManualRequest, val index: Int, val before: Long?, val after: Long?)

sealed interface ScheduledStep {
    data class Select(val schedule: PlaybackSchedule, val occurrence: PlaybackOccurrence) : ScheduledStep
    data class Context(val step: QueueStep, val placement: SchedulePlacement) : ScheduledStep
}

sealed interface PlaybackRow {
    val track: TrackRef
    val key: String
    val playing: Boolean
    data class Source(val id: Long, val index: Int, override val track: TrackRef, override val playing: Boolean) : PlaybackRow {
        override val key: String get() = "source:$id"
    }
    data class Manual(val request: ManualRequest, override val playing: Boolean) : PlaybackRow {
        override val track: TrackRef get() = request.track
        override val key: String get() = "manual:${request.id}"
    }
}

/** One-shot manual requests; backward traversal belongs exclusively to the source context. */
data class PlaybackSchedule(
    val pending: List<ManualRequest> = emptyList(),
    val current: PlaybackOccurrence? = null,
    val consumed: Boolean = false,
    val nextId: Long = 1,
    val generation: Long = 0,
    val removal: PendingRemoval? = null,
) {
    fun enqueue(track: TrackRef, fromHistory: Boolean = false) =
        copy(pending = pending + ManualRequest(nextId, track, fromHistory), nextId = nextId + 1)

    /** Only waiting occurrences move; neither source positions nor the actual file participate. */
    fun moveRequest(id: Long, targetId: Long): PlaybackSchedule {
        val from = pending.indexOfFirst { it.id == id }
        val target = pending.indexOfFirst { it.id == targetId }
        if (from < 0 || target < 0 || from == target) return this
        return copy(pending = pending.toMutableList().apply { add(target, removeAt(from)) })
    }

    fun resetTraversal() = copy(current = null, consumed = false, generation = generation + 1)

    fun contextSelected(context: ActiveQueue, placement: SchedulePlacement): PlaybackSchedule = copy(
        current = context.current?.let { PlaybackOccurrence(nextId, it, context) }, consumed = false,
        nextId = nextId + 1, generation = generation + if (placement == SchedulePlacement.REPLACE) 1 else 0,
    )

    fun finishCurrent() = copy(consumed = true)
    fun resumeCurrent() = copy(consumed = false)

    fun next(context: ActiveQueue, completed: Boolean = false): ScheduledStep {
        if (completed && context.order.repeat == RepeatMode.ONE && (current != null || context.currentAvailable))
            return ScheduledStep.Context(QueueStep.Replay, SchedulePlacement.APPEND)
        pending.firstOrNull()?.let { return playRequest(it.id, context)!! }
        return ScheduledStep.Context(if (completed) context.ended() else context.next(), SchedulePlacement.APPEND)
    }

    fun previous(context: ActiveQueue): ScheduledStep = ScheduledStep.Context(
        if (current?.manual == true && context.currentAvailable) QueueStep.Play(context) else context.previous(),
        SchedulePlacement.PREPEND,
    )

    fun playRequest(id: Long, context: ActiveQueue): ScheduledStep.Select? {
        val request = pending.firstOrNull { it.id == id } ?: return null
        val anchor = if (current == null && context.current == null && context.source !is QueueSource.Random)
            ActiveQueue(QueueSource.None, PlayQueue(emptyList(), shuffle = context.order.shuffle, repeat = context.order.repeat))
        else context
        val occurrence = PlaybackOccurrence(request.id, request.track, anchor, request)
        return ScheduledStep.Select(copy(pending = pending.filterNot { it.id == id }, current = occurrence, consumed = false), occurrence)
    }

    fun hasNext(context: ActiveQueue): Boolean = pending.isNotEmpty() || context.hasNext
    fun hasPrevious(context: ActiveQueue): Boolean = current?.manual == true && context.currentAvailable || context.hasPrevious

    /** A pruned sounding occurrence must not lend its highlight to the new parked cursor. */
    fun sourcePlaybackIndex(context: ActiveQueue): Int? = context.order.currentIndex?.takeIf {
        context.currentAvailable && (current == null || !current.manual && current.context.source == context.source &&
            current.context.currentEntryId == context.currentEntryId)
    }

    fun rows(context: ActiveQueue): List<PlaybackRow> {
        val actualIndex = sourcePlaybackIndex(context)
        val order = context.playbackIndices
        val positions = order.withIndex().associate { it.value to it.index }
        val anchor = order.indexOf(context.order.currentIndex)
        val rows = context.visibleIndices.map { index -> PlaybackRow.Source(context.entryIds[index], index,
            context.order.tracks[index], index == actualIndex) }
        val block = buildList {
            current?.request?.takeUnless { consumed }?.let { add(PlaybackRow.Manual(it, true)) }
            pending.forEach { add(PlaybackRow.Manual(it, false)) }
        }
        val insertion = rows.count { positions.getValue(it.index) <= anchor }
        return rows.take(insertion) + block + rows.drop(insertion)
    }

    fun remove(id: Long): PlaybackSchedule {
        val index = pending.indexOfFirst { it.id == id }
        if (index < 0) return this
        return copy(pending = pending.filterNot { it.id == id }, nextId = nextId + 1,
            removal = PendingRemoval(nextId, pending[index], index, pending.getOrNull(index - 1)?.id, pending.getOrNull(index + 1)?.id))
    }

    fun undo(id: Long): PlaybackSchedule {
        val token = removal?.takeIf { it.id == id } ?: return this
        if (pending.any { it.id == token.request.id } || current?.id == token.request.id) return copy(removal = null)
        val after = pending.indexOfFirst { it.id == token.after }
        val before = pending.indexOfFirst { it.id == token.before }
        val index = when { after >= 0 -> after; before >= 0 -> before + 1; else -> token.index.coerceIn(0, pending.size) }
        return copy(pending = pending.take(index) + token.request + pending.drop(index), removal = null)
    }

    fun withLearnedTrack(track: TrackRef): PlaybackSchedule {
        fun improve(ref: TrackRef) = if (ref.id == track.id) track else ref
        fun request(item: ManualRequest) = item.copy(track = improve(item.track))
        return copy(pending = pending.map(::request), current = current?.let { it.copy(track = improve(it.track),
            request = it.request?.let(::request), context = it.context.withOrder(it.context.order.copy(
                tracks = it.context.order.tracks.map(::improve)))) }, removal = removal?.let { it.copy(request = request(it.request)) })
    }

    fun contextForSelection(entry: PlaybackOccurrence, live: ActiveQueue): ActiveQueue =
        if (entry.context.source == QueueSource.None) entry.context.withOrder(entry.context.order
            .withRepeat(live.order.repeat).withShuffle(live.order.shuffle)) else live
}
