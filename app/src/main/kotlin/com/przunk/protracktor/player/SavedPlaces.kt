// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bookmark operations have no playback or download capability; catalogue reads are local. */
class SavedPlaces(
    private val scope: CoroutineScope,
    private val store: Store,
    private val availability: suspend (List<SavedPlace>) -> List<SavedPlaceRow>,
    private val resolve: suspend (SavedPlaceKey) -> SavedPlaceDestination,
    private val show: (SavedPlaceKey, SavedPlaceDestination.Found) -> Unit,
    private val isShowingList: () -> Boolean,
    private val notice: (Notice) -> Unit,
    private val removed: (undo: (() -> Unit)?) -> Unit = { notice(Notice.REMOVED) },
) {
    interface Store {
        suspend fun save(place: SavedPlace): Boolean
        suspend fun list(): List<SavedPlace>
        suspend fun remove(key: SavedPlaceKey)
    }
    enum class Notice { SAVED, ALREADY_SAVED, REMOVED, FAILED, UNAVAILABLE }

    private val mutable = MutableStateFlow(SavedPlacesState())
    val state = mutable.asStateFlow()
    private val writes = Mutex()
    private var read: Job? = null
    private var opening: Job? = null
    private var openTicket = 0L
    private var pendingWrites = 0

    fun refresh() {
        read?.cancel()
        read = scope.launch {
            mutable.update { it.copy(loading = true, failed = false) }
            try {
                val rows = writes.withLock { availability(store.list()) }
                ensureActive()
                mutable.update { it.copy(rows = rows, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun save(place: SavedPlace) = write {
        notice(if (store.save(place)) Notice.SAVED else Notice.ALREADY_SAVED)
    }

    fun remove(key: SavedPlaceKey) {
        val offerUndo = isShowingList()
        write {
            val previous = store.list().firstOrNull { it.key == key }
            store.remove(key)
            var used = false
            val undo: (() -> Unit)? = if (offerUndo && previous != null) {
                {
                    if (!used) {
                        used = true
                        save(previous)
                    }
                }
            } else null
            removed(undo)
        }
    }

    private fun write(action: suspend () -> Unit) {
        // Undo must queue behind an in-flight edit, not disappear because the buttons are busy.
        pendingWrites++
        mutable.update { it.copy(writing = true) }
        scope.launch {
            try { writes.withLock { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice(Notice.FAILED) }
            finally {
                pendingWrites--
                mutable.update { it.copy(writing = pendingWrites > 0) }
                refresh()
            }
        }
    }

    fun cancelOpening() {
        openTicket++
        opening?.cancel()
        opening = null
        mutable.update { it.copy(opening = null) }
    }

    fun open(key: SavedPlaceKey) {
        cancelOpening()
        val ticket = openTicket
        mutable.update { it.copy(opening = key) }
        opening = scope.launch {
            try {
                val destination = resolve(key)
                ensureActive()
                if (!isShowingList()) return@launch
                when (destination) {
                    is SavedPlaceDestination.Found -> show(key, destination)
                    is SavedPlaceDestination.Unavailable -> {
                        mutable.update { state -> state.copy(rows = state.rows.map {
                            if (it.place.key == key) it.copy(availability = destination.reason) else it
                        }) }
                        notice(Notice.UNAVAILABLE)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice(Notice.FAILED) }
            finally {
                if (ticket == openTicket) mutable.update { it.copy(opening = null) }
            }
        }
    }
}
