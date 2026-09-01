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
package com.przunk.protracktor.engine

import android.content.Context
import java.io.File

/**
 * Unpacks the data files the decoders need onto the filesystem.
 *
 * sc68 does not carry its replay routines inside the tunes it plays: SNDH and `.sc68` both reference
 * small 68k binaries that live in sc68's own data directory, and it opens them **by path**. Assets
 * inside an APK have no path, so they have to be copied out once.
 *
 * This is why every SNDH in the owner's library loaded and then played silence: the tune arrived,
 * the replay it asked for did not.
 */
object NativeData {

    private const val ASSET_ROOT = "sc68"

    /**
     * Returns the directory to hand to the engine, unpacking on first use.
     *
     * Guarded by a marker naming the app version. Re-unpacking on every launch would be wasted work;
     * never re-unpacking would leave stale replays behind after an update, which is the kind of
     * fault that only shows up on one obscure format.
     */
    fun ensureUnpacked(context: Context, versionMarker: String): File {
        val target = File(context.filesDir, ASSET_ROOT)
        val marker = File(target, ".unpacked")

        if (marker.isFile && marker.readText() == versionMarker) return target

        target.deleteRecursively()
        target.mkdirs()
        copyAssetTree(context, ASSET_ROOT, target)
        runCatching { marker.writeText(versionMarker) }
        return target
    }

    private fun copyAssetTree(context: Context, assetPath: String, destination: File) {
        val children = runCatching { context.assets.list(assetPath) }.getOrNull().orEmpty()

        if (children.isEmpty()) {
            // A leaf: assets.list() returns nothing for a file, which is also what it returns for an
            // empty directory. Copying is the only way to tell them apart, so a failure here is
            // expected for the latter and not worth reporting.
            runCatching {
                context.assets.open(assetPath).use { input ->
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use(input::copyTo)
                }
            }
            return
        }

        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(destination, child))
        }
    }
}
