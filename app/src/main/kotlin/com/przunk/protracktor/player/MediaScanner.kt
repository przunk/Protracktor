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

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Turns a folder the user granted into a list of playable references.
 *
 * The storage access framework rather than `MediaStore`, decided in `docs/OPEN_QUESTIONS.md` Q3:
 * Android's media scanner does not index `.mod`, `.sid` or `.sndh`, so most of a real collection
 * would simply be invisible to it.
 */
object MediaScanner {

    /**
     * Keeps read access to a granted tree across restarts. Without this the grant dies with the
     * process and R2 — coming back to what you had — is impossible for anything but this session.
     */
    fun persistPermission(context: Context, uri: Uri, isTree: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            if (isTree) {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } else {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
        }
    }

    /**
     * Where a granted tree sits, as something a person can read.
     *
     * Best effort: `primary:Andrzej/Music/Amiga` becomes `Andrzej/Music/Amiga`. There is no way to
     * get a real filesystem path out of the storage access framework, and for a network share there
     * is not one to get -- so this is a label, not an address, and it is treated as one.
     */
    private fun rootPathOf(treeUri: Uri): String =
        runCatching {
            DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':').trim('/')
        }.getOrNull()?.ifBlank { null } ?: labelOf(treeUri)

    /** A name for a granted tree that means something to a human. */
    fun labelOf(treeUri: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrNull()
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: treeUri.lastPathSegment.orEmpty()

    /** Walks a granted folder tree, depth first, and returns everything that looks playable. */
    suspend fun scanTree(context: Context, treeUri: Uri): List<TrackRef> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val found = mutableListOf<TrackRef>()

        // The path is accumulated as we descend rather than read back off each document id. Document
        // ids are the provider's business: the framework's own are readable paths, but a network
        // provider's are often short opaque handles -- which is why the owner saw only "AMIGA" where
        // he expected the whole path. Walking down, we always know where we are.
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri) to rootPathOf(treeUri)))

        while (pending.isNotEmpty()) {
            // A deep tree on a slow provider can take a while; cancelling the scan has to actually
            // stop it rather than let it run on in the background.
            coroutineContext.ensureActive()
            val (documentId, path) = pending.removeFirst()
            collectChildren(resolver, treeUri, documentId, path, found, pending)
        }
        found.sortedWith(compareBy({ it.subtitle }, { it.title }))
    }

    private fun collectChildren(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        parentPath: String,
        found: MutableList<TrackRef>,
        pending: ArrayDeque<Pair<String, String>>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0)
                val displayName = cursor.getString(1) ?: continue
                val mimeType = cursor.getString(2)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    pending.addLast(documentId to "$parentPath/$displayName")
                } else if (SupportedFormats.looksPlayable(displayName)) {
                    found += TrackRef(
                        id = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                        title = displayName,
                        // The whole path. One folder name does not say which library it came from,
                        // which is what the owner asked to be able to see.
                        subtitle = parentPath,
                        sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        fileName = displayName,
                    )
                }
            }
        }
    }


    /** Builds references for individually picked files. */
    fun fromDocuments(context: Context, uris: List<Uri>): List<TrackRef> = uris.map { uri ->
        persistPermission(context, uri, isTree = false)
        val (name, size) = describe(context, uri)
        val displayName = name ?: uri.lastPathSegment.orEmpty()
        TrackRef(
            id = uri.toString(),
            title = displayName,
            // An individually picked file has no tree walk behind it, so the document id is all
            // there is. Usually a readable path; when it is not, a short label beats a blank line.
            subtitle = runCatching {
                DocumentsContract.getDocumentId(uri).substringAfter(':').substringBeforeLast('/', "")
            }.getOrDefault(""),
            sizeBytes = size,
            fileName = displayName,
        )
    }

    private fun describe(context: Context, uri: Uri): Pair<String?, Long> =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { row ->
                if (row.moveToFirst()) {
                    row.getString(0) to (if (row.isNull(1)) 0L else row.getLong(1))
                } else {
                    null to 0L
                }
            }
        }.getOrNull() ?: (null to 0L)
}
