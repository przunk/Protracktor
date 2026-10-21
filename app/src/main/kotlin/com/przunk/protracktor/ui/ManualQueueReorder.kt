// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import kotlinx.coroutines.isActive

/** Gesture-local ordering bridges input events arriving before the next Flow recomposition. */
internal class ManualQueueReorder(
    private val listState: LazyListState,
    private val pending: () -> List<Long>,
    private val onMove: (Long, Long) -> Unit,
    private val haptics: Haptics,
) {
    var draggedId: Long? by mutableStateOf(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var order = emptyList<Long>()
    private var height = 0f
    private var moved = false

    fun start(id: Long) {
        val ids = pending()
        if (ids.size < 2 || id !in ids) return
        order = ids
        draggedId = id
        offset = 0f
        height = 0f
        moved = false
        haptics.gestureStart()
    }

    fun stop(id: Long) {
        if (draggedId != id) return
        draggedId = null
        offset = 0f
        order = emptyList()
        haptics.gestureEnd()
    }

    fun cancelIfMembershipChanged(ids: List<Long>) {
        draggedId?.takeIf { ids.toSet() != order.toSet() }?.let(::stop)
    }

    fun drag(id: Long, delta: Float) {
        if (draggedId != id) return
        cancelIfMembershipChanged(pending())
        if (draggedId != id) return
        moved = moved || delta != 0f
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "manual:$id" }
            ?.size?.takeIf { it > 0 }?.let { height = it.toFloat() }
        if (height <= 0f) return
        val from = order.indexOf(id)
        val total = offset + delta
        val target = (from + (total / height).toInt()).coerceIn(order.indices)
        if (target != from) {
            val targetId = order[target]
            order = order.toMutableList().apply { add(target, removeAt(from)) }
            onMove(id, targetId)
            haptics.tick()
        }
        // The grip must not visually drag a request across the current/manual or source boundary.
        offset = (total - (target - from) * height).coerceIn(
            if (target == 0) 0f else -height,
            if (target == order.lastIndex) 0f else height,
        )
    }

    suspend fun scrollAtEdge(edge: Float, distance: Float) {
        val id = draggedId ?: return
        if (!moved) return
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == "manual:$id" } ?: return
        val centre = item.offset + offset + item.size / 2f
        val index = order.indexOf(id)
        val direction = when {
            centre < layout.viewportStartOffset + edge && index > 0 -> -1
            centre > layout.viewportEndOffset - edge && index < order.lastIndex -> 1
            else -> 0
        }
        if (direction != 0) drag(id, listState.scrollBy(direction * distance))
    }
}

@Composable
internal fun rememberManualQueueReorder(
    listState: LazyListState, pendingIds: List<Long>, generation: Long, onMove: (Long, Long) -> Unit,
): ManualQueueReorder {
    val liveIds by rememberUpdatedState(pendingIds)
    val liveMove by rememberUpdatedState(onMove)
    val haptics = rememberHaptics()
    val state = remember(listState, generation) { ManualQueueReorder(listState, { liveIds }, { id, target -> liveMove(id, target) }, haptics) }
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    val speed = with(LocalDensity.current) { 480.dp.toPx() }
    LaunchedEffect(pendingIds) { state.cancelIfMembershipChanged(pendingIds) }
    LaunchedEffect(state.draggedId, state) {
        if (state.draggedId == null) return@LaunchedEffect
        listState.stopScroll()
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            state.scrollAtEdge(edge, speed * ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f))
            previous = now
        }
    }
    return state
}

@Composable
internal fun ManualQueueGrip(
    id: Long, title: String, reorder: ManualQueueReorder, onEarlier: (() -> Unit)?, onLater: (() -> Unit)?,
) {
    val description = stringResource(R.string.a11y_reorder, title)
    val earlier = stringResource(R.string.queue_move_earlier)
    val later = stringResource(R.string.queue_move_later)
    Box(Modifier.size(48.dp).testTag("queue-grip-$id").semantics(mergeDescendants = true) {
        contentDescription = description
        customActions = buildList {
            onEarlier?.let { add(CustomAccessibilityAction(earlier) { it(); true }) }
            onLater?.let { add(CustomAccessibilityAction(later) { it(); true }) }
        }
    }.draggable(rememberDraggableState { reorder.drag(id, it) }, Orientation.Vertical,
        startDragImmediately = true, onDragStarted = { reorder.start(id) }, onDragStopped = { reorder.stop(id) }),
        contentAlignment = Alignment.Center) {
        Icon(PlayerIcons.DragHandle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
