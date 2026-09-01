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

/** One entry of an online catalogue's index, before it reaches the database. */
data class CatalogueEntry(
    val path: String,
    val format: String,
    val author: String,
    val title: String,
    val size: Long,
)

/**
 * An online archive of retro music.
 *
 * Each of these publishes its contents differently, which is why parsing is part of the definition
 * rather than something shared. What they have in common is the shape the app needs: a downloadable
 * index, and a rule for turning an entry's path into a URL.
 */
sealed class Catalogue(
    val id: String,
    val displayName: String,
    val indexUrl: String,
) {
    /** The URL a track is fetched from. */
    abstract fun urlFor(path: String): String

    /** Turns the downloaded index into entries. Given the raw bytes; several ship them zipped. */
    abstract fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry>

    companion object {
        val all: List<Catalogue> = listOf(Modland)

        fun byId(id: String): Catalogue? = all.firstOrNull { it.id == id }
    }
}

/**
 * Modland — the big one, and the only one wired up so far.
 *
 * Its whole index is a single 5.75 MB zip holding one text file of about half a million lines, each
 * `<size>\t<Format>/<Author>/<title>`. That is why browsing works with no network at all: the index
 * is downloaded once and lives in the database. The network is first needed when a track is played.
 *
 * Measured on 2026-08-31: the file endpoint answers `Range` requests, and one 212 KB fetch took
 * four seconds — slow enough that fetching the next track ahead matters.
 */
object Modland : Catalogue(
    id = "modland",
    displayName = "Modland",
    indexUrl = "https://modland.com/allmods.zip",
) {
    private const val FILE_BASE = "https://modland.com/pub/modules/"

    override fun urlFor(path: String): String =
        FILE_BASE + path.split('/').joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    override fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry> {
        val text = java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            zip.nextEntry ?: return emptyList()
            zip.readBytes().toString(Charsets.UTF_8)
        }

        val entries = ArrayList<CatalogueEntry>(200_000)
        text.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val size = line.substring(0, tab).toLongOrNull() ?: return@forEach
            val path = line.substring(tab + 1)

            val title = path.substringAfterLast('/')
            if (!keep(title)) return@forEach

            // "Format/Author/title" is the convention, but coop releases and unknown authors give
            // deeper and shallower paths. Taking the first segment as the format and everything
            // between as the author keeps both readable instead of dropping them.
            val parts = path.split('/')
            val format = parts.firstOrNull().orEmpty()
            val author = when {
                parts.size >= 3 -> parts.subList(1, parts.size - 1).joinToString("/")
                else -> ""
            }
            entries += CatalogueEntry(path, format, author, title, size)
        }
        return entries
    }
}
