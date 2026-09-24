// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Fetching tracks from an online catalogue, and not fetching them twice.
 *
 * The cache is not an optimisation here. A Modland fetch took four seconds for 212 KB, and a local
 * library on an SMB share is a network round trip too, so "read the file" costs either way. R9 —
 * playback that starts immediately — is a caching problem before it is anything else.
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

        // **One download per address** (A55): a tap on a track that is being fetched ahead waits
        // for that fetch instead of starting a second, and a download keeps going when whoever
        // asked for it -- a folder that was left -- stops waiting.
        downloads.get(url) { downloadAndStore(url, cached) }
    }

    private val downloads = SharedFetches<ByteArray?>(CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun downloadAndStore(url: String, cached: File): ByteArray? {
        val downloaded = runCatching { download(url) }.getOrNull() ?: return null

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
        return downloaded
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
        val file = shareFile(fileName)
        file.writeBytes(bytes)
        shareUri(file)
    }.getOrNull()

    /**
     * Where a file to be shared is written: a fresh place in the share directory, for a caller that
     * writes the file itself rather than handing over bytes -- an encoder writing a tune rendered
     * to audio (`docs/BACKLOG.md` A62). [shareUri] then turns it into what the chooser takes.
     */
    fun shareFile(fileName: String): File {
        // Copies made for earlier shares, an hour old or more. Not "everything except this one":
        // the receiving app reads the file after the chooser closes, and deleting the previous
        // share the moment a new one starts would sometimes pull it out from under a slow reader.
        val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000
        shareDir.listFiles()?.forEach { if (it.lastModified() < hourAgo) it.delete() }

        // The name the other person sees. Separators would climb out of the directory, and a blank
        // name would produce a file called nothing at all.
        val named = fileName.ifBlank { "tune" }.replace('/', '_').replace('\\', '_')
        return File(shareDir, named)
    }

    fun shareUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
    }.getOrNull()

    /** What the fetched-file cache holds. Unfinished downloads do not count. */
    fun cacheBytes(): Long = CacheBudget.totalBytes(
        cacheDir.listFiles().orEmpty().map { CacheEntry(it.name, it.length(), it.lastModified()) }
    )

    /**
     * What the permanent downloads hold — the ASMA archive and anything like it.
     *
     * Reported separately because it is exempt from the ceiling by design, and because the number
     * is what the delete button sits next to.
     */
    fun permanentBytes(): Long = archiveDir.listFiles().orEmpty().sumOf { it.length() }

    /** The size of one archive, or zero when it has not been downloaded. */
    fun archiveBytes(catalogueId: String): Long =
        archiveFile(catalogueId).let { if (it.isFile) it.length() else 0L }

    /**
     * Throws away one downloaded archive.
     *
     * Safe because it is re-fetchable: the catalogue knows where it came from and downloading it
     * again is the same code path as downloading it the first time. That is the rule for
     * everything the storage screen offers to delete — nothing there may be the only copy.
     */
    fun deleteArchive(catalogueId: String): Boolean = archiveFile(catalogueId).delete()

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * One GET, and one retry when the answer was a doorman rather than the file.
     *
     * `files.exotica.org.uk` answers a client it has not seen with a 200 carrying a few hundred
     * bytes of *"Verifying your browser…"*, a `Set-Cookie`, and a script that reloads the page. A
     * plain fetch stores that HTML as if it were a module, and the failure arrives later as a
     * decoder refusing a file that downloaded fine. Sending the cookie back and asking again is
     * what the page itself does.
     *
     * **The condition is deliberately narrow**: a cookie was set, the type is HTML, and the body is
     * under two kilobytes. No index and no module here is any of those things, so this cannot fire
     * on a real answer -- and a host that fails this way twice is a host that is down, so there is
     * one retry rather than a loop.
     */
    private fun download(url: String): ByteArray? {
        val first = attempt(url, cookie = null) ?: return null
        if (!first.looksLikeAGate()) return first.body
        val cookie = first.cookie ?: return first.body
        return attempt(url, cookie)?.body
    }

    private class Attempt(val body: ByteArray, val contentType: String?, val cookie: String?) {
        fun looksLikeAGate(): Boolean =
            cookie != null && body.size < 2048 && contentType.orEmpty().startsWith("text/html")
    }

    private fun attempt(url: String, cookie: String?): Attempt? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            // The name and value only. A cookie's attributes are the browser's business and this is
            // not one; sending `path=/; HttpOnly` back would be sending the server its own notes.
            if (cookie != null) connection.setRequestProperty("Cookie", cookie)
            if (connection.responseCode !in 200..299) return null
            Attempt(
                body = connection.inputStream.use { it.readBytes() },
                contentType = connection.contentType,
                // **Found without caring about case**, because HTTP header names do not have one and
                // this map's ordering is somebody else's implementation detail. It very likely works
                // either way on Android -- `HttpURLConnection` is OkHttp underneath and its map is
                // case-insensitive -- but the failure would be silent: the retry never fires and a
                // "verifying your browser" page is stored as if it were a module
                // (`docs/review-round-8.md` R6).
                cookie = connection.headerFields.entries
                    .firstOrNull { it.key != null && it.key.equals("Set-Cookie", ignoreCase = true) }
                    ?.value?.firstOrNull()?.substringBefore(';')?.takeIf { it.isNotBlank() },
            )
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
