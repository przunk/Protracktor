// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.player

import com.przunk.protracktor.data.SongDbMetadata

/** Decoder values win; the downloaded metadata fills fields the format cannot carry. */
internal object PlaybackMetadata {
    fun merge(described: Map<String, String>, found: SongDbMetadata.Entry?): Map<String, String> {
        if (found == null) return described
        val out = described.toMutableMap()
        fun fill(key: String, value: String) {
            if (value.isNotBlank() && out[key].isNullOrBlank()) out[key] = value
        }
        fill("artist", found.author)
        fill("album", found.album)
        fill("publisher", found.publisher)
        // Some decoders emit a nonblank placeholder such as 0 for a missing year.
        if (found.year.isNotBlank() && ReleaseYear.of(out["year"].orEmpty()).isBlank()) out["year"] = found.year
        return out
    }
}
