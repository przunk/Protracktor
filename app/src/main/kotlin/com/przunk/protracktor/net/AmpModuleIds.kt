// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * AMP module numbers by path, kept beside the catalogue index (C134, 2026-11-09).
 *
 * AMP's new site serves a file only as `/module/<id>`, while a tune's identity here stays its path
 * (`amp://<path>`): playlists, History and bookmarks saved before the change keep working, and the
 * numbers -- which AMP says can change -- are only looked up at the moment of download. So the
 * numbers live in a file of their own, sorted by path and searched in place: 177,000 rows never
 * have to sit in memory, and the database schema does not change.
 *
 * One line per module, `path<TAB>id`, ordered by the path's UTF-8 bytes.
 */
internal class AmpModuleIds(private val file: File) {

    /** Writes the whole table; false when it could not be written. A path seen twice keeps its first id. */
    fun write(pairs: List<Pair<String, Long>>): Boolean {
        val rows = pairs.filter { (path) -> '\n' !in path && '\t' !in path }
            .map { (path, id) -> path.toByteArray(Charsets.UTF_8) to id }
            .sortedWith { a, b -> compareBytes(a.first, b.first) }
        val out = ByteArrayOutputStream()
        var last: ByteArray? = null
        for ((path, id) in rows) {
            if (last != null && compareBytes(last, path) == 0) continue
            out.write(path); out.write('\t'.code); out.write(id.toString().toByteArray()); out.write('\n'.code)
            last = path
        }
        return AtomicFileWrite.write(file, out.toByteArray())
    }

    /** The module number for [path], or null when the table has none (or does not exist yet). */
    fun idFor(path: String): Long? {
        if (!file.isFile) return null
        val wanted = path.toByteArray(Charsets.UTF_8)
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                var low = 0L
                var high = raf.length()
                // Invariant: every line starting before [low] sorts before [wanted]; the answer, if
                // any, is the line starting in [low, high).
                while (low < high) {
                    val mid = (low + high) / 2
                    val start = lineStartAtOrAfter(raf, mid)
                    if (start >= high) { high = mid; continue }
                    val (key, id, next) = readLine(raf, start) ?: break
                    val order = compareBytes(key, wanted)
                    when {
                        order == 0 -> return@use id
                        order < 0 -> low = next
                        else -> high = start
                    }
                }
                // [low] may sit mid-line after the last step; check the line that starts there.
                if (low < raf.length()) readLine(raf, lineStartAtOrAfter(raf, low))
                    ?.takeIf { compareBytes(it.first, wanted) == 0 }?.second else null
            }
        }.getOrNull()
    }

    // Read in chunks rather than byte by byte: a lookup is about eighteen probes, and the on-phone
    // marker asks for every visible AMP row, so per-byte reads (nearly a millisecond a lookup when
    // measured on the full map) would add up to seconds on a long list.
    private val chunk = ByteArray(1024)

    private fun lineStartAtOrAfter(raf: RandomAccessFile, position: Long): Long {
        if (position == 0L) return 0L
        var at = position - 1
        while (at < raf.length()) {
            raf.seek(at)
            val read = raf.read(chunk)
            if (read <= 0) break
            for (i in 0 until read) if (chunk[i] == '\n'.code.toByte()) return at + i + 1
            at += read
        }
        return raf.length()
    }

    private fun readLine(raf: RandomAccessFile, start: Long): Triple<ByteArray, Long, Long>? {
        if (start >= raf.length()) return null
        val line = ByteArrayOutputStream()
        var at = start
        var end = -1L
        while (end < 0 && at < raf.length()) {
            raf.seek(at)
            val read = raf.read(chunk)
            if (read <= 0) break
            var i = 0
            while (i < read && chunk[i] != '\n'.code.toByte()) i++
            line.write(chunk, 0, i)
            if (i < read) end = at + i + 1
            at += read
        }
        val bytes = line.toByteArray()
        val tab = bytes.lastIndexOf('\t'.code.toByte()).takeIf { it >= 0 } ?: return null
        val id = String(bytes, tab + 1, bytes.size - tab - 1).toLongOrNull() ?: return null
        return Triple(bytes.copyOf(tab), id, if (end < 0) raf.length() else end)
    }

    companion object {
        fun compareBytes(a: ByteArray, b: ByteArray): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
                if (d != 0) return d
            }
            return a.size - b.size
        }
    }
}
