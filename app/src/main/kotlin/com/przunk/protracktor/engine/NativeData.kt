// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.engine

import android.content.Context
import com.przunk.protracktor.net.Sc68Replays
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
        adoptDownloadedReplays(context, target)
        runCatching { marker.writeText(versionMarker) }
        return target
    }

    /**
     * Copies any replay the user downloaded into the directory sc68 reads.
     *
     * **They are kept somewhere else and copied in, rather than living here.** This whole tree is
     * deleted and rebuilt whenever the app version changes — that is deliberate, so a stale replay
     * cannot survive an update — and downloaded files would go with it, silently, leaving `.sc68`
     * broken again with nothing to explain why. `Sc68Replays.store` sits outside that blast radius
     * and this puts its contents back afterwards.
     *
     * The app ships `sndh_ice.bin` and downloads the rest (`docs/LICENSES.md`), so the two sets
     * never overlap; if they ever did, the asset is the one to trust and is written first.
     *
     * Also called straight after a download, so the tunes work without restarting: sc68 is given a
     * *path* once and reads what is under it each time it opens a file, so new arrivals count
     * immediately.
     */
    fun adoptDownloadedReplays(context: Context, target: File = File(context.filesDir, ASSET_ROOT)) {
        val downloaded = Sc68Replays.store(context).listFiles().orEmpty().filter { it.isFile }
        if (downloaded.isEmpty()) return
        val replays = File(target, "Replay").apply { mkdirs() }
        downloaded.forEach { source ->
            val destination = File(replays, source.name)
            if (!destination.exists()) runCatching { source.copyTo(destination) }
        }
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
