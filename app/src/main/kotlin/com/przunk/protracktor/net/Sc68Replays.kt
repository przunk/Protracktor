// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The sc68 replay routines the app does not ship, fetched from sc68 if the user asks.
 *
 * **Why they are not in the APK** is `docs/LICENSES.md`. sc68 does not carry these routines inside
 * the tunes: SNDH and `.sc68` name a small 68000 binary and sc68 opens it by path. 99 of them exist;
 * exactly one — `sndh_ice.bin`, sc68's own SNDH wrapper — is plainly sc68's own work and ships with
 * the app. The other 98 are named after commercial Atari ST games and after other people's players,
 * and sc68's `AUTHORS` says nothing about where they came from.
 *
 * So the app does not distribute them. If the user wants `.sc68` support, their device fetches the
 * files from sc68's own SourceForge, which leaves the distributing to SourceForge. **That is where
 * the safety comes from — not from asking the user to agree to something.** A consent screen does
 * not turn an infringing act into a non-infringing one; not being the distributor does.
 *
 * Measured before any of this was decided: shipping only `sndh_ice.bin` costs **nothing** for SNDH
 * (30 of 30 either way, 5,484 Modland files) and takes `.sc68` from 10 of 10 to 4 of 10.
 */
object Sc68Replays {

    /**
     * The revision, pinned to the one the vendored library was built from.
     *
     * The same number as `scripts/fetch-sc68-svn.py`. A tune references a replay by name and the
     * two have to agree about what that name means, so fetching whatever happens to be at HEAD
     * would be fetching a different library's data.
     */
    private const val REVISION = 713
    private const val LISTING =
        "https://svn.code.sf.net/p/sc68/code/file68/data68/Replay/?p=$REVISION"
    private const val FILE_BASE =
        "https://svn.code.sf.net/p/sc68/code/file68/data68/Replay"

    /** Ships with the app, so it is never fetched and never deleted. */
    const val BUNDLED = "sndh_ice.bin"

    /** Where downloads are kept, outside the tree that an app update wipes and re-unpacks. */
    fun store(context: Context): File = File(context.filesDir, "sc68-replays")

    fun count(context: Context): Int = store(context).listFiles()?.count { it.isFile } ?: 0

    fun bytes(context: Context): Long = store(context).listFiles().orEmpty().sumOf { it.length() }

    fun delete(context: Context): Boolean = store(context).deleteRecursively()

    /** What a listing looks like: `href="alteredbeast.bin?p=713"`, and `../` which is not a file. */
    private val ENTRY = Regex("""href="([^"?]+)\?p=$REVISION"""")

    /**
     * Fetches every replay but the bundled one.
     *
     * Ninety-eight small files rather than one archive, because sc68 publishes no archive of them —
     * 3.0.0b exists only in SVN, and its HTTP interface serves a directory listing and the files
     * under it. Slower than a zip and it is the only route that keeps somebody else the publisher.
     *
     * @return how many arrived, or null if the listing could not be read at all.
     */
    suspend fun download(context: Context, onProgress: (done: Int, total: Int) -> Unit): Int? =
        withContext(Dispatchers.IO) {
            val listing = fetch(LISTING)?.toString(Charsets.UTF_8) ?: return@withContext null
            val names = ENTRY.findAll(listing)
                .map { it.groupValues[1] }
                .filter { it != "../" && !it.endsWith("/") && it != BUNDLED }
                .toList()
            if (names.isEmpty()) return@withContext null

            val target = store(context).apply { mkdirs() }
            var done = 0
            names.forEach { name ->
                // A name from somebody else's server decides a filename here, so it is checked
                // rather than trusted: anything with a separator in it would write outside `target`.
                if (name.contains('/') || name.contains('\\') || name == "." || name == "..") {
                    return@forEach
                }
                fetch("$FILE_BASE/$name?p=$REVISION")?.let { bytes ->
                    runCatching { File(target, name).writeBytes(bytes) }.onSuccess { done++ }
                }
                onProgress(done, names.size)
            }
            done
        }

    private fun fetch(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytes() }
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
