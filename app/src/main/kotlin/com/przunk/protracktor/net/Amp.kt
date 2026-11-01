// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.net.URLEncoder
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Android catalogue backed by the pinned songdb path index; see docs/PLAN_AMP.md. */
object Amp : Catalogue(
    id = "amp",
    displayName = "AMP (Amiga Music Preservation)",
    indexUrl = "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/" +
        "ead6d2d72f31c805f24966bd6dbe6f45ed9d2e0f/songdb/sources/site/amp.tsv",
) {
    // Withdrawal requires a new build; existing installations keep their compiled availability.
    const val ENABLED: Boolean = true
    internal val access = AmpAccess()
    override val homeUrl = "https://amp.dascene.net/"
    private const val FILE_BASE = "https://amp.dascene.net/modules/"
    // Above the largest pinned entry (55,414,299 bytes), still bounded for malformed responses.
    private const val MAX_MODULE_BYTES = 64 * 1024 * 1024

    override fun urlFor(path: String): String = "amp://$path"
    override fun pathFrom(id: String): String? =
        id.removePrefix("amp://").takeIf { it != id && validPath(it) }

    override fun webUrlFor(path: String): String = homeUrl
    // Keep external sharing on the archive homepage; raw module transfer is Android-only.
    override fun fileUrlFor(path: String): String? = null

    fun downloadUrl(path: String): String? = path.takeIf(::validPath)?.let {
        FILE_BASE + it.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    internal fun controls(url: String): Boolean = url == indexUrl || runCatching {
        val uri = java.net.URI(url)
        uri.host.equals("amp.dascene.net", ignoreCase = true)
    }.getOrDefault(false)

    internal fun validPath(path: String): Boolean =
        path.split('/').let { parts ->
            parts.size == 3 && parts.all { it.isNotBlank() && it != "." && it != ".." } &&
                path.none { it == '\\' || it.isISOControl() }
        }

    override fun parseIndex(bytes: ByteArray): List<CatalogueEntry> = buildList {
        bytes.toString(Charsets.UTF_8).lineSequence().forEach { line ->
            val cells = line.trimEnd('\r').split('\t')
            // Four-column continuation rows describe subsongs, not additional files.
            if (cells.size != 11) return@forEach
            val path = cells[10]
            if (!validPath(path)) return@forEach
            val title = path.substringAfterLast('/').removeSuffix(".gz")
            val size = cells[7].toLongOrNull()?.takeIf { it >= 0 } ?: return@forEach
            if (title.isBlank()) return@forEach
            add(CatalogueEntry(
                path = path,
                // FTM also means FamiTracker; retain the source's certain format for platform inference.
                format = if (cells[5] == "FaceTheMusic") "Face The Music"
                    else title.substringBefore('.').trim().uppercase(Locale.ROOT),
                author = path.substringAfter('/').substringBefore('/'),
                title = title,
                size = size,
            ))
        }
    }

    /** Two indexed files are uncompressed; reject HTML and cap expanded gzip data. */
    internal fun moduleBytes(bytes: ByteArray): ByteArray? = runCatching {
        val gzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val result = if (gzip) GZIPInputStream(bytes.inputStream()).use(::readBounded) else bytes
        result.takeIf {
            val text = it.take(256).toByteArray().toString(Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
            it.isNotEmpty() && it.size <= MAX_MODULE_BYTES &&
                listOf("<!doctype html", "<html", "<head", "<body").none(text::startsWith)
        }
    }.getOrNull()

    /** Applies to both the pinned index and module bodies, before a response can exhaust memory. */
    internal fun readBounded(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_MODULE_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
