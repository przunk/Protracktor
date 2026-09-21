// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.engine

import android.content.Context
import com.przunk.protracktor.net.Sc68Replays
import com.przunk.protracktor.net.UadePlayers
import java.io.File

/**
 * Unpacks the data files the decoders need onto the filesystem.
 *
 * sc68 does not carry its replay routines inside the tunes it plays: SNDH and `.sc68` both reference
 * small 68k binaries that live in sc68's own data directory, and it opens them **by path**. Assets
 * inside an APK have no path, so they have to be copied out once.
 *
 * Without the copy an SNDH loads and plays silence: the tune arrives, the replay it names does
 * not.
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

    // --- UADE ---------------------------------------------------------------------------------

    private const val UADE_ROOT = "uade"

    /**
     * Returns UADE's data directory, unpacking on first use.
     *
     * Three files come out of the APK — `score`, the 68k program that runs inside the emulated
     * Amiga; `uaerc`, which configures the machine; and `eagleplayer.conf`, the table that says
     * which player a file needs. The 176 replay routines do not: they are downloaded
     * (`docs/LICENSES.md`) and copied in here, exactly as sc68's are and for the same reason —
     * this tree is rebuilt whenever the app version changes, and a download inside it would
     * vanish with it.
     */
    fun ensureUadeUnpacked(context: Context, versionMarker: String): File {
        val target = File(context.filesDir, UADE_ROOT)
        val marker = File(target, ".unpacked")

        if (marker.isFile && marker.readText() == versionMarker) {
            adoptDownloadedPlayers(context, target)
            return target
        }

        target.deleteRecursively()
        target.mkdirs()
        copyAssetTree(context, UADE_ROOT, target)
        adoptDownloadedPlayers(context, target)
        runCatching { marker.writeText(versionMarker) }
        return target
    }

    /**
     * Copies the downloaded replay routines and song database into the directory UADE reads.
     *
     * Also called straight after a download, so the Amiga formats work without restarting: UADE is
     * given a base directory once and reads what is under it each time a file is opened.
     *
     * Copied rather than linked, and only what is missing, so a repeated call costs a directory
     * listing.
     */
    fun adoptDownloadedPlayers(
        context: Context,
        target: File = File(context.filesDir, UADE_ROOT),
    ) {
        val store = UadePlayers.store(context)
        val downloaded = File(store, "players")
        if (downloaded.isDirectory) {
            // The whole tree, `ENV/EaglePlayer/` included: eleven player configurations live one
            // level down and are part of what upstream installs.
            downloaded.walkTopDown().filter { it.isFile }.forEach { source ->
                val destination = File(File(target, "players"), source.relativeTo(downloaded).path)
                if (!destination.exists()) {
                    runCatching {
                        destination.parentFile?.mkdirs()
                        source.copyTo(destination)
                    }
                }
            }
        }
        val songConf = File(store, "song.conf")
        val installed = File(target, "song.conf")
        if (songConf.isFile && !installed.exists()) runCatching { songConf.copyTo(installed) }
    }

    /**
     * The emulator binary, which is an executable shipped under a library's name.
     *
     * `lib/<abi>/` is the one directory an app may execute from since Android 10 — everywhere it
     * can write, it may not execute — which is why `app/build.gradle.kts` also asks for the native
     * libraries to be extracted at install. Without that they are mapped straight out of the APK
     * and this path names a file that does not exist.
     */
    fun uadeCore(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libuadecore.so")

    /**
     * Where a tune is written so that UADE has a path to open.
     *
     * In the cache directory, because every file in it is reconstructable and a scratch directory
     * is exactly what a cache is for. Each opening makes its own subdirectory and removes it
     * afterwards; this only has to exist.
     */
    fun uadeScratch(context: Context): File = File(context.cacheDir, "uade-scratch")

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
