// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
     * What a live search actually did.
     *
     * Three outcomes, because they were one before and that is the whole of `docs/STATUS.md` C15:
     * a blocked request, a changed page and a tune the archive does not have all came back as an
     * empty list. The user was then told "nothing found" for all three, which is true of exactly
     * one of them.
     */
    sealed interface Outcome {
        /** The archive answered a search page. The list may still be empty, honestly. */
        data class Found(val tracks: List<TrackRef>) : Outcome

        /** Could not ask: no network, a refusal, a timeout. */
        data object NotReached : Outcome

        /** Answered with something that is not a search page. The markup has moved. */
        data object Unreadable : Outcome
    }

    /**
     * Searches The Mod Archive using their web search interface.
     *
     * There is no index to download here — this is scraped, live, from somebody else's HTML — so it
     * is the one source in the app that can break with nothing here changing. Which is why it now
     * reports [Outcome] rather than a list that means three different things.
     */
    suspend fun search(query: String): Outcome = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Outcome.Found(emptyList())
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val connection = (URL(SEARCH_URL + encoded).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                instanceFollowRedirects = true
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "search refused: HTTP $status for \"$query\"")
                connection.disconnect()
                return@withContext Outcome.NotReached
            }
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            when {
                html.contains(RESULTS_MARKER) -> Outcome.Found(parseSearchResults(html))
                // The archive's own way of saying it found nothing, and it is not a blank page: it
                // offers ten unrelated modules under "Or perhaps enjoy some of these...". Those
                // carry download links, so a parser that only looked for links would have returned
                // them as if they were results.
                html.contains(NO_RESULTS_MARKER) -> Outcome.Found(emptyList())
                else -> {
                    Log.w(TAG, "search page not recognised (${html.length} bytes) for \"$query\"")
                    Outcome.Unreadable
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "search failed for \"$query\"", e)
            Outcome.NotReached
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

    /** The heading a real result page carries. Verified against the live site on 2026-09-06. */
    private const val RESULTS_MARKER = "site-wide-page-head-title\">Search Results"

    /** And what it says instead when there are none. */
    private const val NO_RESULTS_MARKER = "perhaps enjoy"

    private val ROW_PATTERN = Pattern.compile("<tr.*?</tr>", Pattern.DOTALL)
    private val MODULE_ID_PATTERN = Pattern.compile("moduleid=(\\d+)")
    private val FILENAME_PATTERN = Pattern.compile("downloads\\.php\\?moduleid=\\d+#([^\"\\s]+)")
    private val FORMAT_PATTERN = Pattern.compile("class=\"format-icon\">([^<]+)</span>")
    private val TITLE_PATTERN = Pattern.compile("<span class=\"module-listing\">\\s*([^<]+?)\\s*</span>")
    private val TITLE_ATTR_PATTERN = Pattern.compile("class=\"standard-link\"[^>]*title=\"([^\"]+)\"")

    /**
     * The modules a search page lists, or nothing at all if it is not a search page.
     *
     * **The gate is not a formality.** The archive's "no results" answer carries ten unrelated
     * modules under "Or perhaps enjoy some of these…", each with a working download link — so a
     * parser that went looking for links alone returned all ten as matches. Checking the heading
     * first is what tells a page of answers from a page of consolation.
     */
    internal fun parseSearchResults(html: String): List<TrackRef> {
        if (!html.contains(RESULTS_MARKER)) return emptyList()
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
