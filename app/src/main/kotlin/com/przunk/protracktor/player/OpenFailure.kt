// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.net.URLDecoder

/**
 * Why a track would not open — as a choice between four different things, not one apology.
 *
 * **This got it wrong twice in one day and both times it cost an hour.** `ice.pt2` failed to
 * download and the screen said nothing about the network; `&SFTDEMO.stc` was one file among 3,639
 * that ZXTune refuses, and the screen said *"Spectrum is a format Protracktor cannot play yet"* —
 * naming a whole platform, of which the app plays 95%. Both readings sent the search somewhere
 * useless.
 *
 * The rule that fixes it is short: **never blame the format for a file we claim.** If
 * `SupportedFormats` says the name is playable then a failure is about this file, and saying
 * otherwise is not a rough approximation, it is false.
 *
 * Android-free so the choice can be tested; the wording lives in string resources, because the
 * choice and the sentence are different problems.
 */
object OpenFailure {

    enum class Kind {
        /** The bytes never arrived. Nothing has been asked of any decoder yet. */
        NOT_FETCHED,

        /** No backend claims this name. The format is genuinely absent, and saying so is fair. */
        FORMAT_UNSUPPORTED,

        /**
         * An Amiga custom format, and the replay routines it needs are not on the phone. Neither
         * the file nor the format is at fault, and the fix is one download away, so the message
         * says where that download is instead of blaming the tracker decoder that happened to be
         * the last one asked.
         */
        NEEDS_AMIGA_PLAYERS,

        /**
         * A multifile song whose other half could not be fetched — TFMX's `smpl.` beside its
         * `mdat.`. UADE refuses the half it was given with the same words it uses for a file it
         * has never heard of, so this says which file was missing instead.
         */
        COMPANION_MISSING,

        /** We claim the format and a decoder gave a reason. Report the reason, about this file. */
        FILE_REFUSED_WITH_REASON,

        /** We claim the format and nothing said why. Still about this file, not the format. */
        FILE_REFUSED,
    }

    /**
     * @param fetched whether the bytes arrived at all.
     * @param claimed whether `SupportedFormats` says this name is playable — the load-bearing one.
     * @param reason what the decoder said, which is often empty and sometimes only meaningful to us.
     * @param needsPlayers whether the name is one only UADE plays and its replay routines are absent.
     * @param companionMissing whether the song needs a file beside it that could not be fetched.
     */
    fun kindOf(
        fetched: Boolean,
        claimed: Boolean,
        reason: String,
        needsPlayers: Boolean = false,
        companionMissing: Boolean = false,
    ): Kind = when {
        !fetched -> Kind.NOT_FETCHED
        !claimed -> Kind.FORMAT_UNSUPPORTED
        needsPlayers -> Kind.NEEDS_AMIGA_PLAYERS
        companionMissing -> Kind.COMPANION_MISSING
        reason.isNotBlank() -> Kind.FILE_REFUSED_WITH_REASON
        else -> Kind.FILE_REFUSED
    }

    /**
     * What to call the format in a message, given the catalogue's name for it and the file's own.
     *
     * The catalogue's is better where there is one — Modland's directory names a format precisely,
     * where an extension can stand for two unrelated ones. `.psm` is 90 files of Epic MegaGames
     * MASI and 51 of Pro Sound Maker, and only the directory knows which.
     */
    fun formatName(catalogueFormat: String?, fileName: String): String =
        catalogueFormat?.takeIf { it.isNotBlank() }
            ?: SupportedFormats.labelFor(fileName).ifBlank { "This" }

    /**
     * The format directory Modland files a track under, read from its address, or null for any
     * other file (`docs/BACKLOG.md` A51).
     *
     * **For a file we claim and a decoder still refused.** An index built from names cannot tell
     * that `Beaver Sweeper/Steffo/nokia.gtk` is not the Graoumf Tracker every other `.gtk` is; the
     * decoder's own "error loading file" does not say so either. Quoting the directory turns a
     * puzzling refusal into an explanation, from data already in the address. Modland only: ASMA's
     * first folder is `Composers` or `Games`, a grouping and not a format.
     */
    fun modlandFormatOf(url: String): String? {
        if (!url.startsWith(MODLAND_FILES)) return null
        val segment = url.removePrefix(MODLAND_FILES).substringBefore('/', "")
        // A literal `+` arrives escaped as %2B; URLDecoder would otherwise read a bare one as a space.
        return URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8").takeIf { it.isNotBlank() }
    }

    /**
     * [text] closed as a sentence, so a clause can follow it. The decoder's reason ends however
     * the decoder ends it -- "error loading file" with nothing -- and the message around it is ours.
     */
    fun sentence(text: String): String =
        text.trimEnd().let { if (it.isEmpty() || it.last() in ".!?") it else "$it." }

    private const val MODLAND_FILES = "https://modland.com/pub/modules/"
}
