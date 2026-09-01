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
class RemoteFiles(context: Context) {

    private val cacheDir = File(context.cacheDir, "remote").apply { mkdirs() }

    /** Returns the bytes, from cache when possible. Null when the fetch failed. */
    suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val cached = fileFor(url)
        if (cached.exists() && cached.length() > 0) {
            return@withContext runCatching { cached.readBytes() }.getOrNull()
        }

        val downloaded = runCatching { download(url) }.getOrNull() ?: return@withContext null

        // Written through a temporary file: a download interrupted halfway would otherwise leave a
        // truncated file in the cache that looks valid forever after.
        runCatching {
            val temporary = File(cached.parentFile, cached.name + ".part")
            temporary.writeBytes(downloaded)
            temporary.renameTo(cached)
        }
        downloaded
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

    fun isCached(url: String): Boolean = fileFor(url).let { it.exists() && it.length() > 0 }

    /** Total bytes held. The eviction budget is still an open question (OPEN_QUESTIONS Q5). */
    fun cacheBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

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
