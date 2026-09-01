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
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder
import kotlinx.coroutines.flow.SharedFlow
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
    val browse: StateFlow<BrowseState> get() = controller.browse

    /** One-shot "scroll the playlist here" events. */
    val reveal: SharedFlow<Int> get() = controller.reveal

    fun addFolder(treeUri: Uri) = controller.addFolder(treeUri)
    fun addFiles(uris: List<Uri>) = controller.addFiles(uris)
    fun removeTrack(index: Int) = controller.removeTrack(index)
    fun moveTrack(from: Int, to: Int) = controller.moveTrack(from, to)
    fun dismissMessage() = controller.dismissMessage()
    fun undoRemoval() = controller.undoRemoval()
    fun savePlaylist() = controller.savePlaylist()
    fun discardChanges() = controller.discardChanges()
    fun seekTo(seconds: Double) = controller.seekTo(seconds)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()

    fun createPlaylist(name: String) = controller.createPlaylist(name)
    fun renameActivePlaylist(name: String) = controller.renameActivePlaylist(name)
    fun deletePlaylist(id: Long) = controller.deletePlaylist(id)
    fun switchToPlaylist(id: Long) = controller.switchToPlaylist(id)

    fun refreshFolders() = controller.refreshFolders()
    fun rememberFolder(treeUri: Uri) = controller.rememberFolder(treeUri)
    fun forgetFolder(uri: String) = controller.forgetFolder(uri)
    fun openFolder(folder: GrantedFolder) = controller.openFolder(folder)
    fun closeFolder() = controller.closeFolder()
    fun openDomain(domain: BrowseDomain) = controller.openDomain(domain)
    fun browseBack(): Boolean = controller.browseBack()
    fun indexCatalogue(id: String) = controller.indexCatalogue(id)
    fun openCatalogue(summary: CatalogueSummary) = controller.openCatalogue(summary)
    fun openGroup(name: String) {
        // One handler for both levels: the format list and the author list look identical and the
        // state already knows which one is showing.
        if (controller.browse.value.openFormat == null) controller.openFormat(name)
        else controller.openAuthor(name)
    }
    fun playRandom() = controller.playRandom()
    fun keepTransient() = controller.keepTransient()
    fun returnToPlaylist() = controller.returnToPlaylist()
    fun playFromResults(results: List<TrackRef>, index: Int) = controller.playFromResults(results, index)
    fun setQuery(query: String) = controller.setQuery(query)
    fun toggleSearchLocal() = controller.toggleSearchLocal()
    fun toggleSearchOnline() = controller.toggleSearchOnline()
    fun toggleSearchCatalogue(id: String) = controller.toggleSearchCatalogue(id)
    fun runSearch() = controller.runSearch()
    fun addToPlaylist(tracks: List<TrackRef>) = controller.addToPlaylist(tracks)

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
