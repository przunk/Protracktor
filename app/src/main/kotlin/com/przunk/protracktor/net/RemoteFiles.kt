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
package com.przunk.protracktor.net

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetching tracks from an online catalogue, and not fetching them twice.
 *
 * The cache is not an optimisation here. A measured Modland fetch took four seconds for 212 KB, and
 * the owner's own local library sits on an SMB share, so "read the file" is a network round trip
 * either way. R9 — playback that starts immediately — is a caching problem before it is anything
 * else.
 */
class RemoteFiles(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "remote").apply { mkdirs() }

    // filesDir rather than cacheDir: an archive is expensive to fetch and the system may clear a
    // cache directory at any time. Losing 20 MB to a cache sweep would mean downloading it again.
    private val archiveDir = File(context.filesDir, "catalogues").apply { mkdirs() }

    // Must match res/xml/file_paths.xml, which is what the provider is allowed to hand out.
    private val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }

    /** Returns the bytes, from cache when possible. Null when the fetch failed. */
    suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val cached = fileFor(url)
        if (cached.exists() && cached.length() > 0) {
            // Touched on the way past, which is what makes the budget's ordering mean "least
            // recently used" rather than "least recently downloaded". Without it, a tune played
            // every day would be evicted ahead of one fetched once and never heard again.
            runCatching { cached.setLastModified(System.currentTimeMillis()) }
            return@withContext runCatching { cached.readBytes() }.getOrNull()
        }

        val downloaded = runCatching { download(url) }.getOrNull() ?: return@withContext null

        // Written through a temporary file: a download interrupted halfway would otherwise leave a
        // truncated file in the cache that looks valid forever after. The budget ignores `.part`
        // files in both directions -- it neither charges for them nor deletes them.
        val stored = runCatching {
            val temporary = File(cached.parentFile, cached.name + CacheBudget.PARTIAL_SUFFIX)
            temporary.writeBytes(downloaded)
            temporary.renameTo(cached)
        }.getOrDefault(false)

        // After a successful write, not before: the file that just arrived is the newest and would
        // survive anyway, and enforcing before writing would leave the cache briefly over budget
        // exactly when it is easiest to fix.
        if (stored) enforceBudget(inUse = setOf(cached.name))
        downloaded
    }

    /**
     * Deletes least-recently-used files until the cache is under its ceiling.
     *
     * Called after every successful write and once at start-up, so an installation that grew past
     * the limit before this existed converges on it rather than staying over forever.
     *
     * [inUse] names files that must survive whatever the arithmetic says -- the track playing and
     * the ones read ahead. Deleting a file mid-read would look to the user like a corrupt download.
     */
    fun enforceBudget(inUse: Set<String> = emptySet(), ceilingBytes: Long = CacheBudget.DEFAULT_CEILING_BYTES) {
        val files = cacheDir.listFiles() ?: return
        val entries = files.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        CacheBudget.evictions(entries, inUse, ceilingBytes).forEach { name ->
            runCatching { File(cacheDir, name).delete() }
        }
    }

    /**
     * An index download, deliberately not cached.
     *
     * The point of re-indexing is to get what changed; serving the old copy back would make the
     * button do nothing while appearing to work.
     */
    suspend fun fetchIndex(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { download(url) }.getOrNull()
    }

    /** Where a catalogue that ships as one archive keeps it. */
    fun archiveFile(catalogueId: String): File = File(archiveDir, "$catalogueId.zip")

    /** Stores a downloaded archive whole, so its contents can be read without the network again. */
    suspend fun storeArchive(catalogueId: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = archiveFile(catalogueId)
            val temporary = File(target.parentFile, target.name + ".part")
            temporary.writeBytes(bytes)
            temporary.renameTo(target)
        }.getOrDefault(false)
    }

    /**
     * Reads one entry out of a stored archive.
     *
     * Opened and closed per read rather than kept open: these are played one at a time, and a file
     * handle held across a whole listening session is a handle held for no reason.
     */
    suspend fun readFromArchive(catalogueId: String, entry: String): ByteArray? = withContext(Dispatchers.IO) {
        val archive = archiveFile(catalogueId)
        if (!archive.isFile) return@withContext null
        runCatching {
            java.util.zip.ZipFile(archive).use { zip ->
                val found = zip.getEntry(entry) ?: return@use null
                zip.getInputStream(found).use { it.readBytes() }
            }
        }.getOrNull()
    }

    fun isCached(url: String): Boolean = fileFor(url).let { it.exists() && it.length() > 0 }

    /** Total bytes held. The eviction budget is still an open question (OPEN_QUESTIONS Q5). */
    /**
     * A copy of [bytes] that another app is allowed to read, as a `content://` URI.
     *
     * A copy is unavoidable. What the library holds for a local file is a storage-access-framework
     * document URI plus a permission grant belonging to **this** app, and a grant cannot be passed
     * on; what it holds for a downloaded file is a path inside app-private storage that nothing
     * else can see. Either way the receiving app cannot read what we have, so it gets its own copy
     * through the provider declared in the manifest.
     *
     * Returns null rather than throwing when the copy cannot be made; a failed share is a message,
     * not a crash.
     */
    fun shareableCopy(fileName: String, bytes: ByteArray): Uri? = runCatching {
        // Copies made for earlier shares, an hour old or more. Not "everything except this one":
        // the receiving app reads the file after the chooser closes, and deleting the previous
        // share the moment a new one starts would sometimes pull it out from under a slow reader.
        val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000
        shareDir.listFiles()?.forEach { if (it.lastModified() < hourAgo) it.delete() }

        // The name the other person sees. Separators would climb out of the directory, and a blank
        // name would produce a file called nothing at all.
        val named = fileName.ifBlank { "tune" }.replace('/', '_').replace('\\', '_')
        val file = File(shareDir, named)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
    }.getOrNull()

    /** What the fetched-file cache holds. Unfinished downloads do not count. */
    fun cacheBytes(): Long = CacheBudget.totalBytes(
        cacheDir.listFiles().orEmpty().map { CacheEntry(it.name, it.length(), it.lastModified()) }
    )

    /**
     * What the permanent downloads hold — the ASMA archive and anything like it.
     *
     * Reported separately because it is exempt from the ceiling by design and the user has no way
     * to delete it (`docs/BACKLOG.md` A13). A number they can see is not a delete button, but it is
     * the difference between an app that takes disk and an app that takes disk quietly.
     */
    fun permanentBytes(): Long = archiveDir.listFiles().orEmpty().sumOf { it.length() }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun download(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    // Hashed rather than sanitised: these paths carry spaces, slashes and characters like @ and $
    // that appear in real Modland filenames, and any escaping scheme would eventually collide.
    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) })
    }
}
