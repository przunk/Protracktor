// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Android catalogue backed by AMP's own daily module map; see docs/PLAN_AMP.md and C134.
 *
 * **Since 2026-11-09** the index is `amp_modules.json`, which AMP regenerates every day at 14:00 UTC,
 * and a file is downloaded as `/module/<id>`. AMP's new site (2026-11-03) retired the `/modules/<path>`
 * addresses the earlier songdb index led to. A tune's identity stays its path (`amp://<path>`), so
 * what users saved before still plays; the number is looked up from [AmpModuleIds] when it downloads.
 */
object Amp : Catalogue(
    id = "amp",
    displayName = "AMP (Amiga Music Preservation)",
    indexUrl = "https://amp.dascene.net/download/amp_modules.json",
) {
    // Withdrawal requires a new build; existing installations keep their compiled availability.
    // Off from 2026-11-08 to 2026-11-09 (C134) while AMP's new site had no way to a module number.
    const val ENABLED: Boolean = true
    internal val access = AmpAccess()
    override val homeUrl = "https://amp.dascene.net/"
    private const val MODULE_BASE = "https://amp.dascene.net/module/"
    // Above the largest module (55,414,299 bytes) and the 45 MB map, still bounded for malformed responses.
    private const val MAX_MODULE_BYTES = 64 * 1024 * 1024

    override fun urlFor(path: String): String = "amp://$path"
    override fun pathFrom(id: String): String? =
        id.removePrefix("amp://").takeIf { it != id && validPath(it) }

    override fun webUrlFor(path: String): String = homeUrl
    // Keep external sharing on the archive homepage; raw module transfer is Android-only.
    override fun fileUrlFor(path: String): String? = null

    /** Where one module downloads from: its number, as AMP's new site serves it. */
    fun downloadUrl(moduleId: Long): String? = moduleId.takeIf { it > 0 }?.let { MODULE_BASE + it }

    internal fun controls(url: String): Boolean = url == indexUrl || runCatching {
        val uri = java.net.URI(url)
        uri.host.equals("amp.dascene.net", ignoreCase = true)
    }.getOrDefault(false)

    internal fun validPath(path: String): Boolean =
        path.split('/').let { parts ->
            parts.size == 3 && parts.all { it.isNotBlank() && it != "." && it != ".." } &&
                path.none { it == '\\' || it.isISOControl() }
        }

    /**
     * The daily map, read as a stream: 45 MB of JSON is parsed field by field rather than built into
     * objects. Browsing keeps the shape the old index had -- the folder as author, the stored file
     * name without `.gz` as title, so the format prefix still decides the decoder -- and a path seen
     * twice (AMP lists 112 files under two numbers) keeps its first number.
     */
    override fun parseIndex(bytes: ByteArray): List<CatalogueEntry> = runCatching {
        val seen = HashSet<String>()
        buildList {
            android.util.JsonReader(java.io.InputStreamReader(bytes.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "modules") { reader.skipValue(); continue }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        moduleEntry(reader)?.takeIf { seen.add(it.path) }?.let(::add)
                    }
                    reader.endArray()
                }
                reader.endObject()
            }
        }
    }.getOrDefault(emptyList())

    private fun moduleEntry(reader: android.util.JsonReader): CatalogueEntry? {
        var id = 0L; var path = ""; var fileName = ""; var size = -1L
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "module_id" -> id = reader.nextLong()
                "path" -> path = reader.nextString()
                "file_name" -> fileName = reader.nextString()
                "file_size" -> size = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (id <= 0 || size < 0 || !validPath(path)) return null
        val title = (fileName.ifBlank { path.substringAfterLast('/') }).removeSuffix(".gz")
        if (title.isBlank()) return null
        val prefix = title.substringBefore('.').trim().uppercase(Locale.ROOT)
        return CatalogueEntry(
            path = path,
            // FTM also means FamiTracker; on AMP, an Amiga archive, it is always Face The Music.
            format = if (prefix == "FTM") "Face The Music" else prefix,
            author = path.substringAfter('/').substringBefore('/'),
            title = title,
            size = size,
            remoteId = id,
        )
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
