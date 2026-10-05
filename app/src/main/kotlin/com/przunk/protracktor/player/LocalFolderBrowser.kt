// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.GrantedFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Browses the existing local index; the controller retains scan and navigation ownership. */
internal class LocalFolderBrowser(
    private val scope: CoroutineScope,
    private val store: Store,
    private val access: Access,
    private val browse: MutableStateFlow<BrowseState>,
    private val backendFingerprint: () -> String,
    private val startScan: (GrantedFolder) -> Unit,
) {
    interface Store {
        suspend fun grantedFolders(): List<GrantedFolder>
        suspend fun rememberFolder(folder: GrantedFolder)
        suspend fun forgetFolder(uri: String)
        suspend fun forgetIndex(uri: String)
        suspend fun tracksIn(uri: String): List<TrackRef>
        suspend fun isStale(uri: String, fingerprint: String): Boolean
    }

    interface Access {
        fun grant(uri: String): GrantedFolder
        fun rootPath(uri: String): String
    }

    fun refreshFolders() {
        scope.launch { browse.update { it.copy(folders = store.grantedFolders()) } }
    }

    fun rememberFolder(treeUri: String) {
        scope.launch {
            val folder = access.grant(treeUri)
            store.rememberFolder(folder)
            browse.update { it.copy(folders = store.grantedFolders()) }
            openFolder(folder)
            // Granting a folder *is* the user asking for it to be usable, so this is the one moment
            // a scan starts without a second press. Nothing else starts one: not launching, not
            // returning to the app, not opening the folder again.
            startScan(folder)
        }
    }

    fun forgetFolder(uri: String) {
        scope.launch {
            store.forgetFolder(uri)
            // The index goes with the grant. Keeping rows for a tree we can no longer read would
            // leave a browsable list of files that cannot be opened.
            store.forgetIndex(uri)
            browse.update { it.copy(folders = store.grantedFolders(), openFolder = null, tracks = emptyList()) }
        }
    }

    /**
     * Shows what a folder holds, from the index rather than by walking it again.
     *
     * Walking the tree again would be seconds of waiting on a network share for an answer that
     * has not changed, and would have to guess what is playable from filenames
     * (`docs/STATUS.md` C4). The scan happens once, on purpose, and this reads its result.
     */
    fun openFolder(folder: GrantedFolder) {
        scope.launch {
            browse.update {
                it.copy(
                    openFolder = folder, loading = true, tracks = emptyList(),
                    folderUnscanned = false, folderStale = false,
                )
            }
            val found = store.tracksIn(folder.uri)
            val stale = found.isNotEmpty() &&
                store.isStale(folder.uri, backendFingerprint())
            browse.update {
                treeOf(it, folder, found).copy(
                    loading = false,
                    folderUnscanned = found.isEmpty(),
                    folderStale = stale,
                )
            }
        }
    }

    /**
     * The open folder as a tree, at its top (the owner, 2026-09-29): its folders, then its own
     * files, rather than every file in it in one list. `tracksIn` carries each file's directory in
     * its subtitle.
     */
    fun treeOf(
        state: BrowseState,
        folder: GrantedFolder,
        found: List<TrackRef>,
        keepLevel: Boolean = false,
    ): BrowseState {
        val all = found.map { it.subtitle to it }
        val root = LocalTree.root(all.map { it.first }, access.rootPath(folder.uri))
        // **Where the reader is, kept** while a scan fills the tree in (the owner, 2026-09-29): the
        // level on screen stays on screen as long as it is still inside the tree.
        val at = state.folderAt.takeIf {
            keepLevel && state.folderRoot == root && (it == root || it.startsWith("$root/"))
        } ?: root
        return levelOf(state.copy(folderAll = all, folderRoot = root), at)
    }

    fun levelOf(state: BrowseState, at: String): BrowseState {
        val level = LocalTree.level(state.folderAll, at)
        return state.copy(folderAt = at, subfolders = level.folders, tracks = level.files)
    }

    /** Into a folder inside the open one. */
    fun openSubfolder(path: String) {
        browse.update { if (it.openFolder == null) it else levelOf(it, path) }
    }

    fun closeFolder() = browse.update { it.copy(openFolder = null, tracks = emptyList()) }
}
