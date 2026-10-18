// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.File

/** One owner's disk transactions; transfers stay outside the lock and archives outside the budget. */
internal class RemoteFileCache(private val directory: File) {
    @Synchronized
    fun read(name: String): ByteArray? {
        val cached = File(directory, name)
        if (!cached.exists() || cached.length() == 0L) return null
        runCatching { cached.setLastModified(System.currentTimeMillis()) }
        return runCatching { cached.readBytes() }.getOrNull()
    }

    @Synchronized
    fun store(name: String, bytes: ByteArray): Boolean {
        val target = File(directory, name)
        val stored = AtomicFileWrite.write(target, bytes)
        if (stored) enforceBudget(inUse = setOf(name))
        return stored
    }

    @Synchronized
    fun enforceBudget(inUse: Set<String> = emptySet(), ceilingBytes: Long = CacheBudget.DEFAULT_CEILING_BYTES) {
        val entries = directory.listFiles()?.map { CacheEntry(it.name, it.length(), it.lastModified()) } ?: return
        CacheBudget.evictions(entries, inUse, ceilingBytes).forEach { name ->
            runCatching { File(directory, name).delete() }
        }
    }

    @Synchronized
    fun contains(name: String): Boolean = File(directory, name).let { it.exists() && it.length() > 0 }

    @Synchronized
    fun bytes(): Long = CacheBudget.totalBytes(
        directory.listFiles().orEmpty().map { CacheEntry(it.name, it.length(), it.lastModified()) },
    )

    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }
}
