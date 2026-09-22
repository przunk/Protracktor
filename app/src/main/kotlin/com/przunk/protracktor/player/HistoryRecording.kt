// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * Whether a play goes into History (`docs/BACKLOG.md` A56).
 *
 * The owner, 2026-09-21: *"clicking a track in history should not move it to the top; playing it
 * outside history should update when it was last played."* Decided 2026-09-22: a play that
 * History itself started leaves its entry alone entirely -- no new time, no new place, no count.
 * History says what was played **elsewhere**, and does not rearrange itself under the person
 * reading it.
 *
 * "Started by History" covers the tracks after the tapped one too, reached with next or at the end
 * of a tune: they are the same walk through the same list. It ends when the list does -- the
 * playlist, Random, Browse or a search taking over playback again.
 */
object HistoryRecording {

    /**
     * @param walkingResults whether a list of results is what is playing (the controller's
     *   `searchMode`) rather than the playlist or a single track.
     * @param resultsFromHistory whether that list was History's.
     */
    fun records(walkingResults: Boolean, resultsFromHistory: Boolean): Boolean =
        !(walkingResults && resultsFromHistory)
}
