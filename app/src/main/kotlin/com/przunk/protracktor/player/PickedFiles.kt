// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

/**
 * Files picked one by one: only what the app can play goes into the playlist (C135, 2026-11-09).
 *
 * The document picker is opened with every type, because a `.mod` or a `.sid` has no MIME type a
 * provider knows, and filtering by type would grey out the very files people pick. So the names
 * decide, by the rule folder scans already use. Without it a tap in the picker's *Recent* -- photos,
 * on a phone -- put a camera JPG into a tester's playlist.
 */
internal object PickedFiles {
    data class Split(val accepted: List<TrackRef>, val refused: List<TrackRef>)

    fun split(picked: List<TrackRef>): Split =
        picked.partition { SupportedFormats.looksPlayable(it.fileNameOrTitle) }
            .let { (accepted, refused) -> Split(accepted, refused) }

    /** The names a refusal message lists before it stops; the count covers the rest. */
    const val NAMED = 3

    fun named(refused: List<TrackRef>): String {
        val names = refused.take(NAMED).joinToString(", ") { it.fileNameOrTitle }
        return if (refused.size > NAMED) "$names, …" else names
    }
}
