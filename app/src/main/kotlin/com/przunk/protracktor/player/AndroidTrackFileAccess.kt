// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.przunk.protracktor.net.Lha
import com.przunk.protracktor.net.RemoteFiles

/** Blocking document/LHA operations stay on the caller's chosen IO, scan or archive dispatcher. */
internal class AndroidTrackFileAccess(
    private val resolver: ContentResolver,
    private val remoteFiles: RemoteFiles,
) : TrackFiles.Access {
    override suspend fun fetch(url: String) = remoteFiles.fetch(url)

    override suspend fun readArchive(catalogueId: String, entry: String) =
        remoteFiles.readFromArchive(catalogueId, entry)

    override fun readDocument(id: String): ByteArray? = runCatching {
        resolver.openInputStream(Uri.parse(id))?.use { it.readBytes() }
    }.getOrNull()

    override fun readDocumentCompanions(id: String): List<Pair<String, ByteArray>> =
        siblingDocuments(Uri.parse(id)).mapNotNull { (name, uri) ->
            runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { name to it }
        }

    override fun decodeFileName(name: String): String = Uri.decode(name)

    override fun extractLha(archive: ByteArray, member: String): ByteArray? = Lha.extract(archive, member)

    override fun reportMissingMember(archiveUrl: String, member: String) {
        android.util.Log.w("Protracktor", "UnExoticA: $member is not in ${archiveUrl}")
    }

    /**
     * The companions of a document in a granted folder, as (name, uri).
     *
     * **Best effort, and it says so.** The external-storage provider gives documents ids that are
     * paths — `primary:Music/TFMX/mdat.unicorn` — so the sibling is the same id with the last
     * segment renamed. Other providers give opaque ids with nothing to rename, and there this finds
     * nothing: the tune opens alone and UADE refuses it, rather than this walking a whole folder
     * tree on every play to look.
     */
    private fun siblingDocuments(uri: Uri): List<Pair<String, Uri>> = runCatching {
        val documentId = DocumentsContract.getDocumentId(uri)
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val tree = DocumentsContract.buildTreeDocumentUri(uri.authority, treeId)
        Companions.siblingPaths(documentId).map { siblingId ->
            siblingId.substringAfterLast('/').substringAfterLast(':') to
                DocumentsContract.buildDocumentUriUsingTree(tree, siblingId)
        }
    }.getOrDefault(emptyList())
}
