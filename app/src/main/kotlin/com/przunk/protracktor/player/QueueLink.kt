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

    /**
     * How a local file travels: as a name and nothing else.
     *
     * **It cannot travel as music and it still has to travel.** A local file's id is a
     * storage-access grant valid on one phone, so the bytes stay here — but leaving the row out
     * renumbers the list, and two people cannot talk about a playlist that counts itself
     * differently at each end. The owner met that with an outside listener on 2026-09-09
     * (`docs/BACKLOG.md` A28): *"nasze listy nie są zgodne"*.
     *
     * A scheme rather than a flag, because the page already reads each line as an address.
     */
    private const val PHONE_PREFIX = "phone:"

    /**
     * The length past which the names of files that are not coming stop being worth their bytes.
     *
     * A link is a URL and about two thousand characters is what is safe everywhere; fifty real
     * tracks measure 1,992 (`docs/PLAN_HANDOFF.md` §3). **The real tracks always travel** — that is
     * not negotiable and this limit never touches them. The placeholders are the luxury, so they
     * are what goes when the link would otherwise get long, and they are counted as left behind
     * exactly as if they had never been offered.
     */
    private const val MAX_CHARS_WITH_PLACEHOLDERS = 2_000

    fun pack(tracks: List<TrackRef>): Packed {
        // Two lists in one pass: what the page can play, and where in the order the phone's own
        // files sat. The second is the whole of A28 -- a row that cannot play still has a position,
        // and the position is what the two people were disagreeing about.
        val lines = mutableListOf<String>()
        var playable = 0
        for (track in tracks) {
            val catalogue = Catalogue.owning(track.id)
            val path = catalogue?.pathFrom(track.id)
            when {
                catalogue == null || path == null ->
                    lines += PHONE_PREFIX + (track.title.trim().ifBlank { track.fileNameOrTitle })
                // Modland is most of any real queue, so its rows lose the 38-byte prefix. Compression
                // would have removed most of that anyway; this makes the untruncated link shorter for
                // the small queues where the limit actually bites.
                catalogue.id == "modland" -> { lines += withTitle(path, track); playable++ }
                else -> { lines += withTitle(track.id, track); playable++ }
            }
        }
        if (playable == 0) return Packed("", 0, lines.size)

        val withGhosts = encode(lines)
        if (withGhosts.length <= MAX_CHARS_WITH_PLACEHOLDERS) {
            return Packed(withGhosts, playable, 0)
        }
        // Too long with them. The real tracks go and the names do not, which is the same answer the
        // app gave before A28 -- and it is still reported rather than done quietly.
        val onlyReal = lines.filterNot { it.startsWith(PHONE_PREFIX) }
        return Packed(encode(onlyReal), playable, lines.size - onlyReal.size)
    }

    /** Deflate, then URL-safe base64. A fragment is not sent to a server, but it is still a URL. */
    private fun encode(lines: List<String>): String {
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.copyOf(total))
    }

    /**
     * The address, and the title after a tab when the address does not already say it.
     *
     * **The Mod Archive is why this exists.** It addresses a file as
     * `downloads.php?moduleid=123#tune.mod`, so the best a page can read out of the URL is the
     * filename — while the phone knows the module's own title, `L3_CD4-SpaceNinja` rather than
     * `lotus3_4.mod`. The queue is a list somebody reads before pressing anything, so it should say
     * what the phone said.
     *
     * Only when it adds something: a Modland row whose title is already its filename sends nothing
     * extra, which is most of a real queue. Compression would have shrunk the repetition anyway;
     * this keeps the untruncated link short, which is what the 2,000-character limit measures.
     */
    private fun withTitle(address: String, track: TrackRef): String {
        val fileName = address.substringAfterLast('/').substringBefore('#').substringBefore('?')
        val title = track.title.trim()
        return if (title.isBlank() || title.equals(fileName, ignoreCase = true)) address
        else "$address\t$title"
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
