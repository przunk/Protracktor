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

import com.przunk.protracktor.player.TrackRef
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Mod Archive (modarchive.org) — the premier community tracker music collection.
 *
 * Unlike Modland or ASMA, The Mod Archive does not publish a single monolithic index file.
 * Instead, searching is conducted live via their web search endpoint, while direct file
 * downloads (MOD, XM, S3M, IT) are served cleanly via their download CDN without requiring
 * an API key.
 */
object ModArchive : Catalogue(
    id = "modarchive",
    displayName = "The Mod Archive",
    indexUrl = "",
) {
    const val DOWNLOAD_PREFIX = "https://api.modarchive.org/downloads.php?moduleid="
    private const val VIEW_PREFIX = "https://modarchive.org/index.php?request=view_by_moduleid&query="
    private const val SEARCH_URL =
        "https://modarchive.org/index.php?request=search&submit=Find&search_type=filename_or_songtitle&query="

    override val homeUrl: String = "https://modarchive.org/"

    override val isOnlineOnly: Boolean get() = true

    override fun urlFor(path: String): String =
        if (path.startsWith("http")) path else "$DOWNLOAD_PREFIX$path"

    override fun webUrlFor(path: String): String? {
        val moduleId = path.substringAfter("moduleid=").substringBefore('#').substringBefore('&')
        return if (moduleId.isNotEmpty() && moduleId.all { it.isDigit() }) {
            "$VIEW_PREFIX$moduleId"
        } else {
            homeUrl
        }
    }

    override fun pathFrom(id: String): String? =
        if (id.startsWith(DOWNLOAD_PREFIX)) id.removePrefix(DOWNLOAD_PREFIX) else null

    override fun parseIndex(bytes: ByteArray, keep: (String) -> Boolean): List<CatalogueEntry> = emptyList()

    /**
     * Searches The Mod Archive using their web search interface.
     *
     * Returns a list of playable [TrackRef] instances with download URLs and extracted titles.
     */
    suspend fun search(query: String): List<TrackRef> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val connection = (URL(SEARCH_URL + encoded).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Protracktor/0.2.0 (Android)")
            }
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseSearchResults(html)
        }.getOrDefault(emptyList())
    }

    private val ROW_PATTERN = Pattern.compile("<tr.*?</tr>", Pattern.DOTALL)
    private val MODULE_ID_PATTERN = Pattern.compile("moduleid=(\\d+)")
    private val FILENAME_PATTERN = Pattern.compile("downloads\\.php\\?moduleid=\\d+#([^\"\\s]+)")
    private val FORMAT_PATTERN = Pattern.compile("class=\"format-icon\">([^<]+)</span>")
    private val TITLE_PATTERN = Pattern.compile("<span class=\"module-listing\">\\s*([^<]+?)\\s*</span>")
    private val TITLE_ATTR_PATTERN = Pattern.compile("class=\"standard-link\"[^>]*title=\"([^\"]+)\"")

    internal fun parseSearchResults(html: String): List<TrackRef> {
        val results = mutableListOf<TrackRef>()
        val rowMatcher = ROW_PATTERN.matcher(html)

        while (rowMatcher.find()) {
            val row = rowMatcher.group()
            if (!row.contains("downloads.php")) continue

            val idMatcher = MODULE_ID_PATTERN.matcher(row)
            if (!idMatcher.find()) continue
            val moduleId = idMatcher.group(1) ?: continue

            val fnMatcher = FILENAME_PATTERN.matcher(row)
            val filename = if (fnMatcher.find()) fnMatcher.group(1) else "module_$moduleId"

            val fmtMatcher = FORMAT_PATTERN.matcher(row)
            val format = if (fmtMatcher.find()) fmtMatcher.group(1) else ""

            val titleMatcher = TITLE_PATTERN.matcher(row)
            val rawTitle = if (titleMatcher.find()) {
                titleMatcher.group(1)?.trim()
            } else {
                val attrMatcher = TITLE_ATTR_PATTERN.matcher(row)
                if (attrMatcher.find()) attrMatcher.group(1)?.trim() else null
            }

            val unescapedTitle = unescapeHtml(rawTitle?.ifBlank { null } ?: filename)

            val downloadUrl = "$DOWNLOAD_PREFIX$moduleId#$filename"
            val sub = listOfNotNull(
                "The Mod Archive",
                format.takeIf { it.isNotBlank() },
            ).joinToString("/")

            results.add(
                TrackRef(
                    id = downloadUrl,
                    title = unescapedTitle,
                    subtitle = sub,
                    fileName = filename,
                    author = "",
                )
            )
        }

        return results
    }

    private fun unescapeHtml(input: String): String =
        input.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
}
