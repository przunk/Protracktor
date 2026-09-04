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
package com.przunk.protracktor.data

import com.przunk.protracktor.player.TrackRef

/**
 * A playlist as a file, and back.
 *
 * **No Android imports**, the same reason as [SchemaSql]: a format is exactly the thing to test on
 * the JVM rather than discover on a phone.
 *
 * ### Why M3U, and what is added to it
 *
 * `docs/BACKLOG.md` A25 asked whether an export is for **another copy of this app** or for **other
 * players**, and warned that serving both is how a format ends up serving neither. The answer here
 * is M3U with our own lines added as comments: every other player reads the standard part and
 * ignores the rest, and we read the rest and can restore exactly.
 *
 * ```
 * #EXTM3U
 * #EXTINF:154,4-Mat - elysium
 * #PROTRACKTOR:id=content://com.android.externalstorage.../elysium.mod
 * #PROTRACKTOR:size=104928
 * Protracker/4-Mat/elysium.mod
 * ```
 *
 * ### What travels and what does not
 *
 * The location line is the portable one. For a catalogue track it is the real URL and another
 * player can fetch it; for a local file it is the path the library knows, which means nothing to a
 * machine but a great deal to a person and to [importFrom] on another device.
 *
 * The **id** is the exact identity and travels nowhere: a local file's id is a
 * storage-access-framework document URI issued by a provider on one device, and the framework hands
 * out a different one for the same file reached a different way. It is written anyway because on
 * the *same* device it restores a playlist perfectly, which is the common case — a backup before
 * reinstalling, or moving a list between playlists.
 *
 * So import has two chances: the id, and failing that a match on filename and size, which is what
 * [TrackRef.sameFileAs] already uses to tell two references to one file apart.
 */
object PlaylistFile {

    private const val HEADER = "#EXTM3U"
    private const val INFO = "#EXTINF:"
    private const val OURS = "#PROTRACKTOR:"

    /**
     * What the list is called, written into the file rather than inferred from it.
     *
     * `#PLAYLIST:` is the de-facto extended-M3U tag for this, so other players understand it and
     * it is not another `#PROTRACKTOR:` line only we can read. It exists because the filename is a
     * bad guess: importing produced names like `primary:Download/Favorites` — a document id, not a
     * title — and even at its best a filename is what the user's *file manager* called it.
     */
    private const val NAME = "#PLAYLIST:"

    /** One line of an imported file, before anything has been matched against a library. */
    data class Entry(
        /** The exact id, when the file carried one. Meaningful only on the device that wrote it. */
        val id: String?,
        val title: String,
        val author: String,
        val sizeBytes: Long,
        /** The location line: a URL for a catalogue track, a path for a local file. */
        val location: String,
    ) {
        /** The filename, for matching against a library that holds the same tune elsewhere. */
        val fileName: String get() = location.substringAfterLast('/')
    }

    fun write(name: String, tracks: List<TrackRef>): String = buildString {
        appendLine(HEADER)
        if (name.isNotBlank()) appendLine("$NAME$name")
        tracks.forEach { track ->
            // Seconds are not known for most of these formats without opening the file, and M3U
            // takes -1 for "unknown" rather than requiring a lie.
            appendLine("$INFO-1,${track.displayAuthor} - ${track.title}")
            appendLine("${OURS}id=${track.id}")
            if (track.sizeBytes > 0) appendLine("${OURS}size=${track.sizeBytes}")
            appendLine(locationOf(track))
        }
    }

    /**
     * The location line: what another player would try to open.
     *
     * A catalogue track's id already is a URL, so it goes out as it is. A local file's id is a
     * document URI that means nothing anywhere else, so the path and filename go instead — which is
     * what a person reading the file would expect to see, and what a match on another device has to
     * work from.
     */
    private fun locationOf(track: TrackRef): String = when {
        track.id.startsWith("http") -> track.id
        track.subtitle.isNotBlank() -> "${track.subtitle.trim('/')}/${track.fileNameOrTitle}"
        else -> track.fileNameOrTitle
    }

    /**
     * Reads a file back.
     *
     * Anything it does not understand is skipped rather than rejected: a playlist written by another
     * program is still a list of locations, and refusing the whole file over one comment would be
     * the wrong trade. A line that is not a comment is a location, which is what M3U has always
     * meant.
     */
    /**
     * The name the file gives itself, or null when it does not.
     *
     * Separate from [read] so that reading a playlist and naming it stay separate questions — a
     * file written by another player has entries and no name, and that is not an error.
     */
    fun nameIn(text: String): String? = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith(NAME) }
        ?.removePrefix(NAME)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun read(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        var title = ""
        var author = ""
        var id: String? = null
        var size = 0L

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith(INFO) -> {
                    // "#EXTINF:154,Artist - Title". The seconds are ignored; we learn the real
                    // length by opening the file, and a number from elsewhere would be a guess.
                    val label = line.substringAfter(',', "")
                    val dash = label.indexOf(" - ")
                    if (dash > 0) {
                        author = label.take(dash).trim()
                        title = label.substring(dash + 3).trim()
                    } else {
                        title = label.trim()
                    }
                }
                line.startsWith("${OURS}id=") -> id = line.removePrefix("${OURS}id=").ifBlank { null }
                line.startsWith("${OURS}size=") ->
                    size = line.removePrefix("${OURS}size=").toLongOrNull() ?: 0L
                line.startsWith("#") -> Unit                       // any other comment, including #EXTM3U
                else -> {
                    entries += Entry(
                        id = id,
                        title = title.ifBlank { line.substringAfterLast('/') },
                        author = author,
                        sizeBytes = size,
                        location = line,
                    )
                    title = ""; author = ""; id = null; size = 0L
                }
            }
        }
        return entries
    }
}
