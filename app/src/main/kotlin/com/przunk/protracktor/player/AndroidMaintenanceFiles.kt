// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import com.przunk.protracktor.net.RemoteFiles
import com.przunk.protracktor.net.Sc68Replays
import com.przunk.protracktor.net.UadePlayers
import java.io.File

/** Removes only the existing downloaded-copy locations; the workflow selects the dispatcher. */
internal class AndroidMaintenanceFiles(
    private val context: Context,
    private val remoteFiles: RemoteFiles,
) : LibraryMaintenance.Files {
    override fun cacheBytes() = remoteFiles.cacheBytes()
    override fun clearCache() = remoteFiles.clearCache()
    override fun deleteArchive(catalogueId: String) = remoteFiles.deleteArchive(catalogueId)
    override fun deleteSc68() = Sc68Replays.delete(context)
    override fun deleteUade() = UadePlayers.delete(context)
    override fun deleteAdoptedUade() = File(context.filesDir, "uade").deleteRecursively()
}
