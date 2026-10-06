// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.data.SchemaSql
import com.przunk.protracktor.net.RemoteFiles
import com.przunk.protracktor.net.Sc68Replays
import com.przunk.protracktor.net.UadePlayers

/** Reads the existing cache, database file and replay stores; the caller selects the dispatcher. */
internal class AndroidCatalogueStorage(
    private val context: Context,
    private val remoteFiles: RemoteFiles,
) : CatalogueBrowser.Files {
    override fun replayCount() = Sc68Replays.count(context)
    override fun replayBytes() = Sc68Replays.bytes(context)
    override fun playersPresent() = UadePlayers.present(context)
    override fun playerCount() = UadePlayers.count(context)
    override fun playerBytes() = UadePlayers.bytes(context)
    override fun pairedBrowser() = Appearance.pairedEndpoint(context) != null
    override fun cacheBytes() = remoteFiles.cacheBytes()
    override fun permanentBytes() = remoteFiles.permanentBytes()
    override fun archiveBytes(catalogueId: String) = remoteFiles.archiveBytes(catalogueId)
    override fun databaseBytes() =
        context.getDatabasePath(SchemaSql.NAME).let { if (it.isFile) it.length() else 0L }
}
