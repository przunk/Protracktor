// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Catalogue
import java.util.Base64
import java.util.zip.Deflater

/**
 * A playlist, packed small enough to travel as a link.
 *
 * **The finding this whole feature rests on is that what crosses is a name, not a sound**
 * (`docs/PLAN_HANDOFF.md` §1). A catalogue track's identity is its URL, and the browser at the far
 * end may fetch those URLs itself — Modland, ASMA and HVSC all permit it. So sending a playlist is
 * sending a few kilobytes of text: measured against Modland's index, fifty tracks compress to
 * **1,992 characters**, against a megabyte of audio.
 *
 * **It goes in the URL's fragment**, after the `#`, and that is not a detail: a fragment is never
 * sent to the server by any browser, so the page's own host never learns what is in the list. That
 * is what lets the first handoff design need no server at all.
 *
 * Android-free on purpose, so the packing can be tested on the JVM against the unpacking the page
 * does — `deflate` here, `DecompressionStream('deflate')` there, `Base64` URL-safe on both sides.
 */
object QueueLink {

    /**
     * What could and could not travel.
     *
     * `left` counts the tracks with no portable identity — local files, whose id is a grant to one
     * app on one phone. **They are counted rather than dropped quietly**: a handoff that shortens a
     * playlist and says nothing is the failure `docs/PLAN_WEB.md` §8 names as worse than refusing.
     */
    data class Packed(val fragment: String, val sent: Int, val left: Int)

    /** Paths are relative to Modland's file base, which the page expands; anything else goes whole. */
    private const val MODLAND_BASE = "https://modland.com/pub/modules/"

    fun pack(tracks: List<TrackRef>): Packed {
        val lines = mutableListOf<String>()
        var left = 0
        for (track in tracks) {
            val catalogue = Catalogue.owning(track.id)
            val path = catalogue?.pathFrom(track.id)
            when {
                catalogue == null || path == null -> left++
                // Modland is most of any real queue, so its rows lose the 38-byte prefix. Compression
                // would have removed most of that anyway; this makes the untruncated link shorter for
                // the small queues where the limit actually bites.
                catalogue.id == "modland" -> lines += path
                else -> lines += track.id
            }
        }
        if (lines.isEmpty()) return Packed("", 0, left)

        val raw = lines.joinToString("\n").toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val buffer = ByteArray(raw.size + 64)
        var total = 0
        while (!deflater.finished() && total < buffer.size) {
            total += deflater.deflate(buffer, total, buffer.size - total)
        }
        deflater.end()

        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(buffer.copyOf(total))
        return Packed(encoded, lines.size, left)
    }

    /** The whole address, given where the page is served from. */
    fun linkTo(base: String, fragment: String): String =
        base.trimEnd('/') + "/#" + fragment

    /**
     * Where the page lives.
     *
     * Defaulted to a local address because that is where it lives today (`docs/PLAN_WEB.md` §13 S3)
     * and because a link is opened on the machine that runs the browser: from the phone's point of
     * view `localhost` is meaningless, and from the desktop's it is exactly right.
     */
    const val DEFAULT_BASE = "http://localhost:8173/src"
}
