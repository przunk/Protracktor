// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.PlaylistFile
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.net.Catalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transfers playlist files without owning the working queue, its edits or the active playlist. */
internal class PlaylistTransfer(
    private val scope: CoroutineScope,
    private val store: Store,
    private val files: Files,
    private val ui: Ui,
) {
    data class ExportSelection(
        val activePlaylistId: Long,
        val tracks: List<TrackRef>,
        val playlists: List<SavedPlaylist>,
    )

    sealed interface Notice {
        data object EmptyPlaylist : Notice
        data object ExportFailed : Notice
        data object UnreadableFile : Notice
        data object NoTracks : Notice
        data class Imported(val found: Int, val offered: Int, val name: String) : Notice
    }

    interface Store {
        suspend fun tracksIn(id: Long): List<TrackRef>
        suspend fun allTracks(): List<TrackRef>
        suspend fun findByFile(name: String, size: Long): TrackRef?
        suspend fun createPlaylist(name: String): Long
        suspend fun replaceTracks(id: Long, tracks: List<TrackRef>)
    }

    interface Files {
        suspend fun read(uri: String): String?
        fun labelOf(uri: String): String
        fun shareableCopy(name: String, bytes: ByteArray): String?
    }

    interface Ui {
        fun exportSelection(): ExportSelection
        suspend fun refreshPlaylists()
        fun say(notice: Notice)
        fun share(uri: String, subject: String)
    }

    /**
     * Writes one playlist out and offers it to be shared.
     *
     * Through the same copy-and-share path a track uses (`docs/ARCHITECTURE.md` §16), because a
     * playlist is a file like any other and nothing else here can hand a file to another app.
     */
    fun exportPlaylist(id: Long) {
        scope.launch {
            val current = ui.exportSelection()
            // The open playlist is held in the queue, where reordering and removal have already
            // happened; only for any other one is the database the truth.
            val tracks =
                if (id == current.activePlaylistId) current.tracks else store.tracksIn(id)
            if (tracks.isEmpty()) {
                ui.say(Notice.EmptyPlaylist)
                return@launch
            }
            val label = current.playlists.firstOrNull { it.id == id }?.name ?: "playlist"
            val name = label.replace(Regex("[^\\w -]"), "_")
            val bytes = withContext(Dispatchers.Default) {
                PlaylistFile.write(label, tracks).toByteArray()
            }
            val uri = files.shareableCopy("$name.m3u8", bytes)
            if (uri == null) {
                ui.say(Notice.ExportFailed)
                return@launch
            }
            ui.share(uri, name)
        }
    }

    /**
     * Reads a playlist file into a new playlist of its own.
     *
     * **A new one, never the current one.** An import that appended to whatever happened to be open
     * would be an edit nobody asked for, and undoing it means finding which rows were new.
     *
     * Each line gets two chances. Its recorded id first, which is exact and works on the device that
     * wrote the file — a backup restored after a reinstall is the common case and deserves to be
     * perfect. Failing that, a match on filename and size against the scanned library, which is how
     * a list written on another phone finds the same tunes here. A catalogue URL needs neither: it
     * means the same everywhere.
     */
    fun importPlaylist(uri: String) {
        scope.launch {
            val text = files.read(uri)
            if (text.isNullOrBlank()) {
                ui.say(Notice.UnreadableFile)
                return@launch
            }

            val entries = withContext(Dispatchers.Default) { PlaylistFile.read(text) }
            if (entries.isEmpty()) {
                ui.say(Notice.NoTracks)
                return@launch
            }

            val known = store.allTracks().associateBy { it.id }
            // An M3U is a text file anybody can write and nothing stops it naming the same tune
            // twice. `LibraryStore.replaceTracks` is where that is dealt with, for every writer at
            // once; the count reported below is of what was found, which is what the file offered.
            val found = entries.mapNotNull { entry -> resolve(entry, known) }

            // The file's own name first. A filename is a poor second: `labelOf` reads a *tree*
            // document id, and handed the document URI of a picked file it returned things like
            // "primary:Download/Favorites" -- an identifier, not a title. Kept as the fallback for
            // files written by other players, which carry no name at all.
            val name = PlaylistFile.nameIn(text)
                ?: files.labelOf(uri).substringAfterLast('/').substringAfterLast(':')
                    .substringBeforeLast('.')
                    .ifBlank { "Imported" }
            val playlistId = store.createPlaylist(name)
            store.replaceTracks(playlistId, found)

            ui.refreshPlaylists()
            ui.say(Notice.Imported(found.size, entries.size, name))
        }
    }

    private suspend fun resolve(entry: PlaylistFile.Entry, known: Map<String, TrackRef>): TrackRef? {
        // A catalogue URL, or an id this device issued: exact either way.
        entry.id?.let { id ->
            known[id]?.let { return it }
            if (id.startsWith("http") || Catalogue.owning(id) != null) {
                return TrackRef(
                    id = id,
                    title = entry.title,
                    subtitle = entry.location.substringBeforeLast('/', ""),
                    sizeBytes = entry.sizeBytes,
                    fileName = entry.fileName,
                    author = entry.author,
                )
            }
        }
        if (entry.location.startsWith("http")) {
            return TrackRef(
                id = entry.location,
                title = entry.title,
                subtitle = entry.location.substringBeforeLast('/', ""),
                sizeBytes = entry.sizeBytes,
                fileName = entry.fileName,
                author = entry.author,
            )
        }
        return store.findByFile(entry.fileName, entry.sizeBytes)
    }
}
