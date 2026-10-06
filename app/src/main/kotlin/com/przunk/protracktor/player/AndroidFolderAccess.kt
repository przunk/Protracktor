// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import android.net.Uri
import com.przunk.protracktor.data.GrantedFolder

internal class AndroidFolderAccess(private val context: Context) : LocalFolderBrowser.Access {
    override fun grant(uri: String): GrantedFolder {
        val treeUri = Uri.parse(uri)
        MediaScanner.persistPermission(context, treeUri, isTree = true)
        return GrantedFolder(treeUri.toString(), MediaScanner.labelOf(treeUri))
    }

    override fun rootPath(uri: String): String = MediaScanner.rootPathOf(Uri.parse(uri))
}
