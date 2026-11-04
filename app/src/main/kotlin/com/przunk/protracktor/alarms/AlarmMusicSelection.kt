// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import com.przunk.protracktor.player.PlayerUiState
import com.przunk.protracktor.player.TrackRef

/** An Add tap owns the selected tune, even if playback changes during the download. */
internal data class AlarmMusicSelection(val ref: TrackRef, val subsong: Int?) {
    companion object {
        fun capture(ref: TrackRef, state: PlayerUiState): AlarmMusicSelection {
            val current = state.current?.takeIf { !state.loadingTrack && it.sameFileAs(ref) }
            return if (current == null) AlarmMusicSelection(ref, null)
                else AlarmMusicSelection(ref.copy(title = current.title, author = current.author), state.subsong)
        }
    }
}
