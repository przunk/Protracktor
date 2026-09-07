// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
    // `scanTree` lived here: it walked a tree and kept files whose *name* looked playable. It is
    // gone rather than deprecated, because leaving it would leave the defect it embodies within
    // reach -- `listFiles` plus a real decoder is the replacement (`docs/BACKLOG.md` A6), and there
    // is no case where the old behaviour is the right answer for a local folder.

    /** One file as the tree listing found it, before anything has been opened. */
    data class Candidate(
        val uri: String,
        val path: String,
        val fileName: String,
        val sizeBytes: Long,
    )

    /**
     * Every file in a tree, with **no filter on its name**.
     *
     * The scan this replaced kept only names a backend might handle -- `docs/STATUS.md` C4: a misnamed
     * file is skipped and a misleadingly named one is added and refuses only when played. Deciding
     * by content means opening the file, and opening the file means listing it first -- so this
     * lists everything and lets the decoder decide.
     *
     * The one thing it does filter on is **size**, and that is content rather than a name: these
     * formats are kilobytes to a few megabytes, and reading a four-gigabyte film off a network
     * share to discover it is not a SID helps nobody.
     */
    suspend fun listFiles(
        context: Context,
        treeUri: Uri,
        maxBytes: Long = MAX_PROBE_BYTES,
    ): List<Candidate> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val found = mutableListOf<Candidate>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri) to rootPathOf(treeUri)))

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (documentId, path) = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
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
                    val childId = cursor.getString(0)
                    val displayName = cursor.getString(1) ?: continue
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.addLast(childId to "$path/$displayName")
                        continue
                    }
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    if (!worthReading(displayName, size, maxBytes)) continue
                    found += Candidate(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString(),
                        path = path,
                        fileName = displayName,
                        sizeBytes = size,
                    )
                }
            }
        }
        found.sortedWith(compareBy({ it.path }, { it.fileName }))
    }

    /**
     * Whether a scan should read this file at all.
     *
     * **The name is a parameter and is deliberately unused.** That is the rule this function exists
     * to hold: a scan decides what a file is by opening it, never by what it is called
     * (`docs/BACKLOG.md` A6, `docs/STATUS.md` C4). It takes the name so that the day somebody
     * reaches for it here, the change is visible in a diff and fails a test, instead of quietly
     * restoring the behaviour this replaced.
     *
     * Size is content, not a name, and is the one thing it does judge on.
     */
    fun worthReading(
        @Suppress("UNUSED_PARAMETER") displayName: String,
        sizeBytes: Long,
        maxBytes: Long = MAX_PROBE_BYTES,
    ): Boolean = sizeBytes <= maxBytes

    /**
     * The largest file worth reading to find out what it is.
     *
     * Everything this app plays is far below it -- a big VGM is a few megabytes and a SID is
     * kilobytes -- so the ceiling costs no real music and saves reading films, disk images and
     * archives off a network share.
     */
    const val MAX_PROBE_BYTES: Long = 32L * 1024 * 1024

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
