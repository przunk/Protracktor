// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/** History-origin playback preserves recency/counts, including manual requests over another source. */
object HistoryRecording {
    fun records(fromHistory: Boolean): Boolean = !fromHistory
}
