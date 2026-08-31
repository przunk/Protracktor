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

    /** Walks a granted folder tree, depth first, and returns everything that looks playable. */
    suspend fun scanTree(context: Context, treeUri: Uri): List<TrackRef> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val found = mutableListOf<TrackRef>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri)))

        while (pending.isNotEmpty()) {
            // A deep tree on a slow provider can take a while; cancelling the scan has to actually
            // stop it rather than let it run on in the background.
            coroutineContext.ensureActive()
            collectChildren(resolver, treeUri, pending.removeFirst(), found, pending)
        }
        found.sortedWith(compareBy({ it.subtitle }, { it.title }))
    }

    private fun collectChildren(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        found: MutableList<TrackRef>,
        pending: ArrayDeque<String>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0)
                val displayName = cursor.getString(1) ?: continue
                val mimeType = cursor.getString(2)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    pending.addLast(documentId)
                } else if (SupportedFormats.looksPlayable(displayName)) {
                    found += TrackRef(
                        id = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                        title = displayName,
                        subtitle = parentDocumentId.substringAfterLast('/').substringAfterLast(':'),
                    )
                }
            }
        }
    }

    /** Builds references for individually picked files. */
    fun fromDocuments(context: Context, uris: List<Uri>): List<TrackRef> = uris.map { uri ->
        persistPermission(context, uri, isTree = false)
        TrackRef(id = uri.toString(), title = displayNameOf(context, uri) ?: uri.lastPathSegment.orEmpty())
    }

    private fun displayNameOf(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
}
