// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
    val showBrowse: SharedFlow<Unit> get() = controller.showBrowse
    val share: SharedFlow<Intent> get() = controller.share

    fun addFolder(treeUri: Uri) = controller.addFolder(treeUri)
    fun addFiles(uris: List<Uri>) = controller.addFiles(uris)
    fun removeTrack(index: Int) = controller.removeTrack(index)

    fun removeTracks(indices: List<Int>) = controller.removeTracks(indices)
    fun moveTrack(from: Int, to: Int) = controller.moveTrack(from, to)
    fun dismissMessage() = controller.dismissMessage()
    fun undoRemoval() = controller.undoRemoval()
    fun savePlaylist() = controller.savePlaylist()
    fun discardChanges() = controller.discardChanges()
    fun seekTo(seconds: Double) = controller.seekTo(seconds)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()

    fun createPlaylist(name: String) = controller.createPlaylist(name)
    fun renamePlaylist(id: Long, name: String) = controller.renamePlaylist(id, name)
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

    fun downloadSongLengths() = controller.downloadSongLengths()
    fun downloadTrackMetadata() = controller.downloadTrackMetadata()
    fun downloadFavourites() = controller.downloadFavourites()
    fun sendQueueToBrowser() = controller.sendQueueToBrowser()
    fun pairWith(endpoint: String) = controller.pairWith(endpoint)
    fun forgetPairing() = controller.forgetPairing()
    fun sendQueueAsLink() = controller.sendQueueAsLink()
    fun rescan() = controller.rescan()
    val scan get() = controller.scan

    /** A file another app handed us. Starts the service first: this can arrive with nothing playing. */
    fun playExternal(uri: android.net.Uri, name: String? = null) {
        ensureServiceRunning()
        controller.playExternal(uri, name)
    }

    // Giving disk back, one copy at a time (`docs/ARCHITECTURE.md` §19).
    fun clearFetchedCache() = controller.clearFetchedCache()
    fun deleteCatalogueIndex(catalogueId: String) = controller.deleteCatalogueIndex(catalogueId)
    fun clearSongLengths() = controller.clearSongLengths()
    fun clearTrackMetadata() = controller.clearTrackMetadata()
    fun clearFavourites() = controller.clearFavourites()

    // The sc68 replay routines the app does not ship (`docs/LICENSES.md`).
    /** Re-reads what is stored. Settings shows those numbers and can be opened without Browse. */
    fun refreshCatalogues() = controller.refreshCatalogues()

    fun downloadReplays() = controller.downloadReplays()
    fun deleteReplays() = controller.deleteReplays()

    fun selectSubsong(index: Int) = controller.selectSubsong(index)

    fun toggleAllSubsongs() = controller.toggleAllSubsongs()
    fun setFallbackLength(seconds: Int) = controller.setFallbackLength(seconds)
    fun setWebPlayer(base: String) = controller.setWebPlayer(base)

    fun exportPlaylist(id: Long) = controller.exportPlaylist(id)

    fun importPlaylist(uri: Uri) = controller.importPlaylist(uri)

    fun clearHistory() = controller.clearHistory()

    fun scanFolder(folder: GrantedFolder) = controller.scanFolder(folder)

    fun showNeighboursOf(track: TrackRef) = controller.showNeighboursOf(track)

    fun shareFile(track: TrackRef) = controller.shareFile(track)

    fun shareLink(track: TrackRef) = controller.shareLink(track)
    fun sendToWeb(tracks: List<TrackRef>) = controller.sendToWeb(tracks)
    fun resumeDice() {
        // Comes back paused, and a paused transport in the notification is still the player: the
        // next press of play is on that tune, and it may well come from the notification itself.
        ensureServiceRunning()
        controller.resumeDice()
    }
    fun openCatalogue(summary: CatalogueSummary) = controller.openCatalogue(summary)
    fun openGroup(name: String) {
        // One handler for both levels: the format list and the author list look identical and the
        // state already knows which one is showing.
        if (controller.browse.value.openFormat == null) controller.openFormat(name)
        else controller.openAuthor(name)
    }
    // **Every door into playback opens the service first** (`docs/STATUS.md` C43). Music started
    // from Random or from a Browse list otherwise runs with no foreground service and therefore no
    // notification, and the system takes the process as soon as the app is left.
    fun playRandom() {
        ensureServiceRunning()
        controller.playRandom()
    }

    fun openRandom() {
        ensureServiceRunning()
        controller.openRandom()
    }

    fun playRandomAt(index: Int) {
        ensureServiceRunning()
        controller.playRandomAt(index)
    }
    fun removeRandomAt(index: Int) = controller.removeRandomAt(index)
    fun keepTransient() = controller.keepTransient()
    fun returnToPlaylist() = controller.returnToPlaylist()
    fun playFromResults(results: List<TrackRef>, index: Int) {
        ensureServiceRunning()
        controller.playFromResults(results, index)
    }
    fun setQuery(query: String) = controller.setQuery(query)
    fun setSearchScope(scope: SearchScope) = controller.setSearchScope(scope)
    fun setRandomScope(scope: RandomScope) = controller.setRandomScope(scope)
    fun toggleSearchPlatform(id: String) = controller.toggleSearchPlatform(id)
    fun toggleSearchCatalogue(id: String) = controller.toggleSearchCatalogue(id)
    fun runSearch() = controller.runSearch()
    fun addToPlaylist(tracks: List<TrackRef>) = controller.addToPlaylist(tracks)
    fun addToPlaylist(targetPlaylistId: Long, tracks: List<TrackRef>) =
        controller.addToPlaylist(targetPlaylistId, tracks)
    fun createPlaylistAndAdd(name: String, tracks: List<TrackRef>) =
        controller.createPlaylistAndAdd(name, tracks)

    fun playAt(index: Int) {
        ensureServiceRunning()
        controller.playAt(index)
    }

    fun togglePlayPause() {
        ensureServiceRunning()
        controller.togglePlayPause()
    }

    fun nextFile() {
        ensureServiceRunning()
        controller.nextFile()
    }

    fun previousFile() {
        ensureServiceRunning()
        controller.previousFile()
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
     * **Called by every entry point that can make a sound.** A tune playing without it has no
     * notification, no transport on the lock screen, and nothing telling the system this process is
     * doing something — so the process goes when the app leaves the screen (`docs/STATUS.md` C43).
     *
     * Android only allows a foreground service to be started while the app is itself in the
     * foreground. Waiting until the user leaves would be waiting until it is no longer permitted.
     */
    private fun ensureServiceRunning() {
        val application = getApplication<Application>()
        application.startService(Intent(application, PlaybackService::class.java))
    }
}
