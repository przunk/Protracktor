/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The screen's view of playback.
 *
 * Holds nothing. [PlaybackController] owns the player, because the service needs the same one and a
 * ViewModel does not outlive its screen. This forwards, and starts the service on anything that
 * makes noise so playback is already in the foreground before the app leaves it.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = PlaybackController.get(application)

    val state: StateFlow<PlayerUiState> get() = controller.state

    fun addFolder(treeUri: Uri) = controller.addFolder(treeUri)
    fun addFiles(uris: List<Uri>) = controller.addFiles(uris)
    fun removeTrack(index: Int) = controller.removeTrack(index)
    fun clearPlaylist() = controller.clearPlaylist()
    fun dismissMessage() = controller.dismissMessage()
    fun seekTo(seconds: Double) = controller.seekTo(seconds)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()

    fun playAt(index: Int) {
        ensureServiceRunning()
        controller.playAt(index)
    }

    fun togglePlayPause() {
        ensureServiceRunning()
        controller.togglePlayPause()
    }

    fun next() {
        ensureServiceRunning()
        controller.next()
    }

    fun previous() {
        ensureServiceRunning()
        controller.previous()
    }

    /**
     * Starts the service before playback rather than after.
     *
     * Android only allows a foreground service to be started while the app is itself in the
     * foreground. Waiting until the user leaves would be waiting until it is no longer permitted.
     */
    private fun ensureServiceRunning() {
        val application = getApplication<Application>()
        application.startService(Intent(application, PlaybackService::class.java))
    }
}
