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

import android.util.Log
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
     * **Returns null when the search could not be made**, and an empty list when it was made and
     * found nothing. Those are different facts and this used to report both as "no results":
     * `runCatching { … }.getOrDefault(emptyList())` swallowed every failure, so a blocked request,
     * a changed page or a dead network all looked exactly like a tune that is not in the archive
     * (`docs/STATUS.md` C15). The caller can now say which happened.
     *
     * The HTTP status is checked rather than assumed. `inputStream` on a 4xx or 5xx throws, which
     * the old code caught and discarded; reading it deliberately means the log says *what* the
     * server said instead of only that something went wrong.
     */
    suspend fun search(query: String): List<TrackRef>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val connection = (URL(SEARCH_URL + encoded).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "search refused: HTTP $status for \"$query\"")
                connection.disconnect()
                return@withContext null
            }
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val results = parseSearchResults(html)
            // A page that came back but yielded nothing is worth distinguishing in the log from a
            // page that came back empty-handed honestly: the markup here is scraped, so "parsed
            // zero out of a page this big" is the shape a silent breakage takes.
            if (results.isEmpty() && html.contains("downloads.php")) {
                Log.w(TAG, "page has download links but nothing parsed -- markup may have changed")
            }
            results
        } catch (e: Exception) {
            Log.w(TAG, "search failed for \"$query\"", e)
            null
        }
    }

    private const val TAG = "ModArchive"

    /**
     * Named for the app rather than the version it was written in.
     *
     * The old one said 0.2.0 forever, which is the sort of thing that makes a server's logs lie
     * about which build is calling it.
     */
    private const val USER_AGENT = "Protracktor (Android; +https://github.com/przunk/protracktor)"

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
