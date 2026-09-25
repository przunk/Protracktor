// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.Modland
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
     * differently at each end (`docs/BACKLOG.md` A28).
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
                // **An MP3 never travels, whatever it is and wherever it came from.** The
                // arithmetic: this handoff rests on a tracker module being kilobytes, and one
                // four-minute MP3 exceeds the eight-megabyte budget for a *whole queue*
                // (`docs/PLAN_WEB.md` §8). A rule rather than a size check, so a short one cannot
                // surprise anybody.
                isMp3(track) ->
                    lines += PHONE_PREFIX + (track.title.trim().ifBlank { track.fileNameOrTitle })
                catalogue == null || path == null ->
                    lines += PHONE_PREFIX + (track.title.trim().ifBlank { track.fileNameOrTitle })
                // Modland is most of any real queue, so its rows lose the 38-byte prefix. Compression
                // would have removed most of that anyway; this makes the untruncated link shorter for
                // the small queues where the limit actually bites.
                catalogue.id == "modland" -> { lines += withTitle(path, track); playable++ }
                // The address a browser can fetch -- ASMA's own file, not the `asma://` this phone
                // reads it by -- and the reference as it stands where there is none.
                else -> { lines += withTitle(catalogue.fileUrlFor(path) ?: track.id, track); playable++ }
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

    /**
     * Whether this is an MP3, by name, from either half of what a track calls itself.
     *
     * By name and not by content, because the bytes are not here: `pack` is given a queue, not
     * files. A name is what the phone has and it is what the decoder itself goes on for four of the
     * other formats.
     */
    fun isMp3(track: TrackRef): Boolean =
        track.fileNameOrTitle.lowercase().endsWith(".mp3") ||
            track.title.lowercase().endsWith(".mp3")

    /**
     * Marks a link as **one tune to play** rather than a queue to take over.
     *
     * Share with Protracktor: a tune from any list, as a link that opens the page playing it.
     * A queue link replaces the list the page shows under "From the phone"; this one
     * must not — it is somebody being shown a tune, possibly somebody else entirely — so the page
     * plays it the way it plays a Browse result, beside whatever list is there. `:` because it is
     * not a base64url character, so no packed queue can ever start with it.
     */
    const val PLAY_PREFIX = "play:"

    /**
     * Whether [track] can go as a one-tune link: [pack]'s two refusals, and one more. A queue link
     * carries rows the page cannot play as greyed places in the list; a one-tune link to such a row
     * is a dead link. So only a tune with an address a browser can fetch ([Catalogue.fileUrlFor]):
     * Modland's and ASMA's, not UnExoticA's, whose tunes sit inside archives.
     */
    fun canSend(track: TrackRef): Boolean =
        !isMp3(track) && Catalogue.owning(track.id)?.let { c -> c.pathFrom(track.id)?.let(c::fileUrlFor) } != null

    /** The link that opens the page at [base] playing [track], or null when it cannot travel. */
    fun trackLink(base: String, track: TrackRef): String? = tracksLink(base, listOf(track))

    /**
     * The link that opens the page playing [tracks], or null when none of them can travel.
     *
     * The ones that cannot are left out rather than sent as placeholders: a queue link carries them
     * as greyed rows so the list numbers the same at both ends, and this is not a list anybody is
     * comparing -- it is a few tunes to hear, and a row that cannot play is only a dead one.
     */
    fun tracksLink(base: String, tracks: List<TrackRef>): String? {
        val sendable = tracks.filter(::canSend)
        if (sendable.isEmpty()) return null
        val packed = pack(sendable)
        return if (packed.sent == 0) null else linkTo(base, PLAY_PREFIX + packed.fragment)
    }

    /**
     * The page's permanent address (`docs/BACKLOG.md` A40): a link to it opens in the app when the
     * app is here. **The path with its capital P**, because GitHub Pages is case-sensitive and
     * `/protracktor/` is a 404 -- claiming it would take links that go nowhere.
     */
    const val PAGE_HOST = "przunk.github.io"
    const val PAGE_PATH = "/Protracktor/"

    /**
     * Where Share with Protracktor points, whatever this phone is paired with (the owner,
     * 2026-09-25). The tune is for somebody else, and a link to this person's own computer -- the
     * paired page, a tunnel, `localhost` -- opens nowhere on theirs; the public page opens anywhere,
     * and in the app where the app is. **`src/`, not the root**: the root forwards with a refresh,
     * and a refresh drops the fragment the tune travels in.
     */
    const val PUBLIC_BASE = "https://$PAGE_HOST${PAGE_PATH}src/"

    /** Share with Protracktor's link: [tracksLink] to the public page. */
    fun shareWithProtracktor(tracks: List<TrackRef>): String? = tracksLink(PUBLIC_BASE, tracks)

    /** Whether [url] is a queue or tune link to the page at its permanent address. */
    fun isPageLink(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(PAGE_HOST, ignoreCase = true) &&
            (uri.rawPath ?: "").startsWith(PAGE_PATH) &&
            !uri.rawFragment.isNullOrBlank()
    }

    /**
     * A link read back, the page's `fromFragment` on the phone (A40): [play] when it was one tune or
     * a few to hear (`#play:`), otherwise a queue; the tunes that can play; and how many rows were
     * files that stayed on the phone that sent them.
     */
    data class Opened(val play: Boolean, val tracks: List<TrackRef>, val stayed: Int)

    /** [url]'s fragment read back into tracks, or null when it is not a link this can read. */
    fun open(url: String): Opened? {
        val fragment = runCatching { java.net.URI(url).rawFragment }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val play = fragment.startsWith(PLAY_PREFIX)
        val lines = decode(if (play) fragment.removePrefix(PLAY_PREFIX) else fragment) ?: return null
        var stayed = 0
        val tracks = lines.mapNotNull { line ->
            if (line.startsWith(PHONE_PREFIX)) { stayed++; return@mapNotNull null }
            val address = line.substringBefore('\t')
            val title = line.substringAfter('\t', "").trim()
            // A Modland row travels as its path, and becomes the id the app's own index gives it, so
            // a tune from a link is the same tune everywhere else in the app.
            val id = if ("://" in address) address else Modland.urlFor(address)
            // The Mod Archive names the file only in the fragment: `downloads.php?moduleid=42#lotus.mod`.
            val last = address.substringAfterLast('/')
            val file = if ('#' in last) last.substringAfter('#') else last.substringBefore('?')
            val source = Catalogue.owning(id)?.let { catalogue ->
                catalogue.pathFrom(id)?.substringBeforeLast('/', "")?.let { folder ->
                    listOf(catalogue.displayName, folder).filter { it.isNotBlank() }.joinToString("/")
                }
            }.orEmpty()
            TrackRef(id = id, title = title.ifBlank { file }, subtitle = source, fileName = file)
        }
        return Opened(play, tracks, stayed)
    }

    /** [encode] undone: URL-safe base64, inflated, one row per line. Null when it is not that. */
    internal fun decode(fragment: String): List<String>? = runCatching {
        val packed = java.util.Base64.getUrlDecoder().decode(fragment)
        val inflater = java.util.zip.Inflater()
        inflater.setInput(packed)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) error("truncated")
            out.write(buffer, 0, n)
        }
        inflater.end()
        out.toString(Charsets.UTF_8.name()).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrNull()

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
    // **With the trailing slash**, and that is not cosmetic. `linkTo` trims and re-adds one, so a
    // generated link is the same either way -- but this string is what Settings shows, and it is
    // therefore the shape somebody copies when they type their own address. Ending it at `/src`
    // taught the pattern that broke: a page opened there loads at an address its own relative URLs
    // are wrong from (`docs/STATUS.md` C29).
    const val DEFAULT_BASE = "http://localhost:8173/src/"
}
