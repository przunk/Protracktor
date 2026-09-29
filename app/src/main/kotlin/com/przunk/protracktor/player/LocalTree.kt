// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * A scanned folder shown as the folders it has, not as one flat list (the owner, 2026-09-29:
 * `Music { Atari { good, bad }, Amiga { xray }, C64 }` should be walked as `Music/Atari/good`, each
 * folder holding its own files).
 *
 * Built from what the scan already stored -- every file's directory, as a path from the granted
 * folder's own name (`MediaScanner.listFiles`) -- so a tree needs no second scan. Pure, so a JVM test
 * holds it.
 */
object LocalTree {
    /** A folder inside the one on screen: its name, its whole path, and how many tunes are under it. */
    data class Folder(val name: String, val path: String, val tunes: Int)

    /** What one level shows: the folders in it, then its own files. */
    data class Level(val folders: List<Folder>, val files: List<TrackRef>)

    /**
     * The level at [at], from [all] tunes, each with the directory it was found in. Folders are
     * named by their next path segment and count every tune below them, however deep.
     */
    fun level(all: List<Pair<String, TrackRef>>, at: String): Level {
        val inside = "$at/"
        val folders = all.filter { it.first.startsWith(inside) }
            .groupBy { it.first.removePrefix(inside).substringBefore('/') }
            .map { (name, rows) -> Folder(name, inside + name, rows.size) }
            .sortedBy { it.name.lowercase() }
        return Level(folders, all.filter { it.first == at }.map { it.second })
    }

    /**
     * Where the tree starts: the granted folder's own path where the scan found files under it,
     * else the deepest directory every file shares -- a label the scan could not turn into a path
     * must not leave the whole tree invisible.
     */
    fun root(paths: List<String>, granted: String): String {
        if (paths.isEmpty() || paths.all { it == granted || it.startsWith("$granted/") }) return granted
        var common = paths.first().split('/')
        for (path in paths) {
            val parts = path.split('/')
            common = common.zip(parts).takeWhile { (a, b) -> a == b }.map { it.first }
        }
        return common.joinToString("/")
    }

    /** One level up from [at], or null at [root]. */
    fun parent(at: String, root: String): String? =
        if (at == root || !at.startsWith("$root/")) null else at.substringBeforeLast('/')
}
