// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-only presentation state; an app restart must not enable an overlay. */
internal object FloatingControls {
    const val SHOW = "com.przunk.protracktor.SHOW_FLOATING_CONTROLS"
    const val HIDE = "com.przunk.protracktor.HIDE_FLOATING_CONTROLS"
    private val active = MutableStateFlow(false)
    val visible = active.asStateFlow()

    fun setVisible(value: Boolean) { active.value = value }

    fun request(context: Context, show: Boolean) {
        try {
            val intent = Intent(context, PlaybackService::class.java).setAction(if (show) SHOW else HIDE)
            if (show) ContextCompat.startForegroundService(context, intent)
            else if (active.value) context.startService(intent)
        } catch (failure: IllegalStateException) {
            unavailable(context)
        } catch (failure: SecurityException) {
            unavailable(context)
        }
    }

    fun unavailable(context: Context) {
        Toast.makeText(AppLocale.wrap(context), R.string.floating_unavailable, Toast.LENGTH_LONG).show()
    }
}

internal data class FloatingControlsState(
    val hasTrack: Boolean,
    val playing: Boolean,
    val loading: Boolean,
    val previous: Boolean,
    val next: Boolean,
    val heart: PlaylistHeart?,
) {
    companion object {
        fun of(state: PlayerUiState) = FloatingControlsState(
            state.current != null, state.playing, state.loadingTrack,
            state.canGoPrevious, state.canGoNext, PlaylistHeart.of(state),
        )
    }
}
