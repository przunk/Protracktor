// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * UADE's replay routines, fetched if the user asks.
 *
 * **Why they are not in the APK** is the same shape as sc68's, and was settled with UADE's own
 * maintainers on 2026-09-04 (`docs/LICENSES.md`). UADE's code is GPL; the 178 binaries under
 * `players/` are the original 68000 replay routines extracted from commercial and shareware Amiga
 * music programs, and their licensing is not something UADE can grant. Upstream's answer was to
 * download rather than redistribute, and that is what this does: the device fetches them, which
 * leaves the distributing to the server that already publishes them.
 *
 * Without them UADE plays 12 files in 300. With them it reaches the music this project is closest
 * to — TFMX, Hippel, David Whittaker, Sonic Arranger, Delta Music — and a lower bound of 5,799
 * Modland files that nothing else here can open (`docs/PLAN_FORMATS.md` §4).
 *
 * **From GitLab rather than from zakalwe.fi**, which is the page upstream publishes for this and
 * the one `docs/LICENSES.md` names first. The reason is a codec: zakalwe.fi serves `.tar.bz2` and
 * Android has no bzip2 decoder, so honouring that would mean adding a decompression library to the
 * APK to read 726 KB once. GitLab serves the same revision as gzip, which `java.util.zip` already
 * reads, and it is upstream's own repository rather than a mirror.
 */
object UadePlayers {

    /**
     * The revision, pinned to the one the emulator was built from.
     *
     * The same string as `scripts/fetch-uade.py`. A replay routine and the emulator that runs it
     * are one program in two halves; fetching whatever is at HEAD would pair them with a different
     * UADE than the one in the APK.
     */
    private const val REVISION = "d40dcc7"

    /** Just the `players/` subtree of that revision, as gzip. 726 KB, measured 2026-09-19. */
    private const val ARCHIVE =
        "https://gitlab.com/uade-music-player/uade/-/archive/$REVISION/" +
            "uade-$REVISION.tar.gz?path=players"

    /**
     * The song database, which is not UADE's and is fetched separately.
     *
     * UADE cannot tell from a filename which player a Hippel or TFMX variant needs — the
     * collections disagree about prefixes and suffixes — so one of UADE's maintainers keeps a
     * table of md5 overrides for the Audacious plugin. It is the difference between 196 of 300 and
     * 206 of 300 on the probe corpus, and between 0 of 12 and 11 of 12 on Modland's Hippel ST COSO.
     *
     * `conf/song.conf` and **not** `conf/songdb` beside it: the first is GPL-2.0-or-later and the
     * second is CC BY-NC-SA 4.0, which could never ship in a store app and is not fetched either.
     */
    private const val SONG_CONF =
        "https://raw.githubusercontent.com/mvtiaine/audacious-uade/master/conf/song.conf"

    /** Where downloads are kept, outside the tree that an app update wipes and re-unpacks. */
    fun store(context: Context): File = File(context.filesDir, "uade-players")

    fun count(context: Context): Int =
        File(store(context), "players").listFiles()?.count { it.isFile } ?: 0

    fun bytes(context: Context): Long =
        store(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun delete(context: Context): Boolean = store(context).deleteRecursively()

    /** Whether there is enough here for the Amiga formats to play at all. */
    fun present(context: Context): Boolean = count(context) > 0

    /**
     * Fetches the replay routines and the song database.
     *
     * @return how many replay routines arrived, or null if the archive could not be read at all.
     */
    suspend fun download(context: Context, onProgress: (done: Int, total: Int) -> Unit): Int? =
        withContext(Dispatchers.IO) {
            val target = store(context).apply { mkdirs() }
            val players = File(target, "players").apply { mkdirs() }

            val written = runCatching {
                openStream(ARCHIVE)?.use { stream ->
                    GZIPInputStream(stream).use { unpacked -> unpackPlayers(unpacked, players, onProgress) }
                }
            }.getOrNull() ?: return@withContext null
            if (written == 0) return@withContext null

            // A best effort, and deliberately not fatal. Without it several Hippel and TFMX
            // variants are identified as the wrong player, which looks like the format not
            // working; without the replay routines nothing works at all. One is worth failing for
            // and the other is not.
            openStream(SONG_CONF)?.use { stream ->
                runCatching { File(target, "song.conf").writeBytes(stream.readBytes()) }
            }
            written
        }

    /**
     * Reads a tar stream and writes the files under `players/` into [into].
     *
     * A tar reader rather than a library: the format is a 512-byte header followed by the file
     * rounded up to 512, and the alternative is a compression dependency in the APK for one
     * download. Only the three header fields that matter are read — name, size and type — and
     * anything that is not a plain file under `players/` is skipped.
     */
    internal fun unpackPlayers(
        tar: InputStream,
        into: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val header = ByteArray(BLOCK)
        var written = 0
        while (true) {
            if (!tar.readFully(header)) break
            // Two empty blocks end the archive; one is enough to stop reading.
            if (header.all { it == 0.toByte() }) break

            val name = String(header, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
            val size = String(header, 124, 12, Charsets.US_ASCII)
                .trim { it <= ' ' || it == '\u0000' }
                .toLongOrNull(radix = 8) ?: 0L
            val isFile = header[156] == '0'.code.toByte() || header[156] == 0.toByte()

            // `/players/`, with both slashes. The archive's top directory is `uade-<rev>-players/`,
            // so looking for `players/` alone matches that too and every file in the archive's root
            // is treated as a replay routine -- which is how `README` was written the first time.
            val leaf = name.substringAfterLast("/players/", missingDelimiterValue = "")
            // A name from somebody else's archive decides a filename here, so it is checked rather
            // than trusted: anything with a separator left in it would write outside `into`.
            val safe = isFile && leaf.isNotEmpty() &&
                !leaf.contains('/') && !leaf.contains('\\') && leaf != "." && leaf != ".."

            if (safe) {
                val bytes = ByteArray(size.toInt())
                if (!tar.readFully(bytes)) break
                runCatching { File(into, leaf).writeBytes(bytes) }.onSuccess { written++ }
                onProgress(written, EXPECTED)
            } else {
                tar.skipFully(size)
            }
            // Every entry is padded to a whole number of blocks.
            tar.skipFully((BLOCK - size % BLOCK) % BLOCK)
        }
        return written
    }

    private const val BLOCK = 512L.toInt()

    /** What the pinned revision holds, used only to make the progress bar mean something. */
    private const val EXPECTED = 178

    private fun InputStream.readFully(into: ByteArray): Boolean {
        var read = 0
        while (read < into.size) {
            val got = read(into, read, into.size - read)
            if (got < 0) return false
            read += got
        }
        return true
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) return
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun openStream(url: String): InputStream? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                null
            } else {
                // Closed by the caller, which also disconnects: the stream owns the connection
                // from here, and closing it twice is harmless.
                connection.inputStream
            }
        } catch (error: Exception) {
            connection.disconnect()
            null
        }
    }
}
