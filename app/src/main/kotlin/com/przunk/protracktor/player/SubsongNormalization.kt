// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A subsong switch while normalization is on: silent until the new tune's profile is ready.
 *
 * Switching straight away played the new tune at the unknown-material gain (-6 dB) until the live
 * meter had three seconds to go on, and then raised it -- the same audible jump a new file no
 * longer has, because a file is measured before it starts. A subsong is now measured the same way
 * (from the cache when it has been heard before), and the decoder is switched only with the
 * result in hand, so its first sample is already at its level.
 *
 * Play/pause pressed meanwhile is remembered rather than applied to the old tune, which is still
 * in the decoder. A newer switch or a closed track abandons the preparation.
 */
internal class SubsongNormalization(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    /** Stops the audio; the decoder keeps the previous tune until [switch]. */
    private val silence: () -> Unit,
    /** The profile for one tune at one mix width; null when it cannot be measured. */
    private val prepare: suspend (index: Int, width: Int) -> LoudnessProfile?,
    private val width: () -> Int,
    /** Configures normalization with [profile] and switches the decoder, which starts the audio. */
    private val switch: (index: Int, profile: LoudnessProfile?) -> Unit,
) {
    private var job: Job? = null
    private var play = true

    /** True while a switch waits for its measurement; nothing should judge the old tune then. */
    val pending: Boolean get() = job?.isActive == true

    fun request(index: Int) {
        job?.cancel()
        play = true
        silence()
        val measuredWidth = width()
        job = scope.launch {
            val profile = withContext(worker) { prepare(index, measuredWidth) }
            ensureActive()
            // A mix changed meanwhile makes the profile another mix's: start from the fallback.
            switch(index, profile.takeIf { width() == measuredWidth })
            if (!play) silence()
        }
    }

    /** Play or pause asked for while [pending]; applied when the switch lands. */
    fun intend(play: Boolean) {
        this.play = play
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
