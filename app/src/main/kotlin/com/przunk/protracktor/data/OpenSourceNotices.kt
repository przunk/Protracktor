// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * The third-party components the app ships, read from `app/notices/components.tsv`.
 *
 * The same table the build reads: `app/build.gradle.kts` copies each component's licence files into
 * the assets under `notices/<id>/`, and this turns a row into the asset paths to show. Pure, so the
 * table's shape is tested on the JVM (`NoticesCoverTheBuildTest`).
 */
object OpenSourceNotices {

    /** Where the build puts the table and the files. */
    const val TABLE_ASSET = "notices/components.tsv"

    data class Component(
        val id: String,
        val name: String,
        val version: String,
        val licence: String,
        /** Asset paths of the licence files, in the order the table lists them. */
        val files: List<String>,
    )

    /**
     * The rows of the table that [build] carries -- `app` for this one; comments and blank lines
     * skipped, a short row refused. The table serves the web page as well (its last column says
     * which build carries a row), and the app must not list the page's Emscripten runtime.
     */
    fun parse(text: String, build: String = APP): List<Component> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val cells = line.split('\t')
            if (cells.size < 6) return@mapNotNull null
            if (build !in buildsOf(line)) return@mapNotNull null
            val id = cells[0].trim()
            Component(
                id = id,
                name = cells[1].trim(),
                version = cells[2].trim(),
                licence = cells[3].trim(),
                files = cells[4].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .map { "notices/$id/${it.substringAfterLast('/')}" },
            )
        }
        .toList()

    const val APP = "app"
    const val WEB = "web"

    /** Which builds carry a row: its last column, comma-separated. */
    fun buildsOf(line: String): Set<String> =
        line.split('\t').getOrNull(5)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** The repository paths a row names, as the build copies them. For the test. */
    fun sourcesOf(line: String): List<String> =
        line.split('\t').getOrNull(4)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
