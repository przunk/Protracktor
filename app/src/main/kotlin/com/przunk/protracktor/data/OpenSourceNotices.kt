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

    /** Every row of the table; comments and blank lines skipped, a short row refused. */
    fun parse(text: String): List<Component> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val cells = line.split('\t')
            if (cells.size < 5) return@mapNotNull null
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

    /** The repository paths a row names, as the build copies them. For the test. */
    fun sourcesOf(line: String): List<String> =
        line.split('\t').getOrNull(4)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
