// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Intent
import android.net.Uri
import com.przunk.protracktor.net.RemoteFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidPlaylistFiles(
    private val documents: AndroidTrackFileAccess,
    private val remoteFiles: RemoteFiles,
) : PlaylistTransfer.Files {
    override suspend fun read(uri: String): String? = withContext(Dispatchers.IO) {
        documents.readDocument(uri)?.toString(Charsets.UTF_8)
    }

    override fun labelOf(uri: String): String = MediaScanner.labelOf(Uri.parse(uri))

    override fun shareableCopy(name: String, bytes: ByteArray): String? =
        remoteFiles.shareableCopy(name, bytes)?.toString()

    companion object {
        fun shareIntent(uri: String, subject: String): Intent = Intent(Intent.ACTION_SEND).apply {
            // The registered type for M3U. Other players offer to open it; anything else
            // treats it as the text file it is.
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
