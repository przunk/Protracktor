// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.File

/** Each writer owns its staging file; only a complete download replaces the destination. */
internal object AtomicFileWrite {
    fun write(target: File, bytes: ByteArray): Boolean {
        val directory = target.parentFile ?: return false
        var temporary: File? = null
        return try {
            temporary = File.createTempFile(target.name + ".", CacheBudget.PARTIAL_SUFFIX, directory)
            temporary.writeBytes(bytes)
            temporary.renameTo(target)
        } catch (_: Exception) {
            false
        } finally {
            temporary?.let { runCatching { it.delete() } }
        }
    }
}
