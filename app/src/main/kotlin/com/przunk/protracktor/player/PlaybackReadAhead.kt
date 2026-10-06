// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns read-ahead bytes and work; the caller still decides which tracks are coming next. */
internal class PlaybackReadAhead(
    private val scope: CoroutineScope,
    private val read: suspend (TrackRef) -> ByteArray?,
) {
    private companion object {
        /** Four megabytes. Comfortably above any tracker module and below anything worth holding. */
        const val MAX_PREFETCH_BYTES = 4 * 1024 * 1024

        /** Total held for reading ahead. Three of the per-file limit would be twelve megabytes. */
        const val MAX_PREFETCH_TOTAL_BYTES = 8 * 1024 * 1024
    }

    /**
     * Tracks read ahead, keyed by reference id, in the order they were read.
     *
     * Read ahead because starting a track is dominated by **reading** it rather than by decoding
     * it: a module is kilobytes and decodes in milliseconds, while opening one on a network share
     * is a round trip measured in seconds (`docs/BACKLOG.md` R9). More than one entry because
     * Random reads several ahead (`docs/BACKLOG.md` A11), and a single slot could only ever hold
     * the very next one.
     */
    private val prefetched = LinkedHashMap<String, ByteArray>()
    private var prefetchJob: Job? = null

    fun take(id: String): ByteArray? = prefetched.remove(id)

    fun prepare(wanted: List<TrackRef>) {
        // The eviction rule, and it is exact rather than a budget: what is no longer coming is no
        // longer wanted. Pressing previous or switching playlists drops what was read for the path
        // not taken, at the moment it stops being the path.
        prefetched.keys.retainAll(wanted.mapTo(mutableSetOf()) { it.id })

        val missing = wanted.filter { it.id !in prefetched }
        if (missing.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // **Together, not one after another.** These fetches spend nearly all their time
            // waiting on a network, so reading three in turn makes the third arrive three round
            // trips late -- which is the wait this whole thing exists to remove. Serial reads here
            // are visible on a device without needing a measurement.
            missing.forEach { ref ->
                launch {
                    val bytes = read(ref) ?: return@launch
                    // A large file is not worth holding in memory to save a second; these formats
                    // are kilobytes and anything of this size is not one of them.
                    if (bytes.size > MAX_PREFETCH_BYTES) return@launch
                    // Back on the scope's dispatcher (main), so the budget check and the write
                    // cannot interleave with another fetch finishing. The second bound is on the
                    // total, because three files each under the per-file limit are not under it.
                    if (prefetched.values.sumOf { it.size } + bytes.size <= MAX_PREFETCH_TOTAL_BYTES) {
                        prefetched[ref.id] = bytes
                    }
                }
            }
        }
    }

    fun clear() {
        prefetchJob?.cancel()
        prefetched.clear()
    }
}
