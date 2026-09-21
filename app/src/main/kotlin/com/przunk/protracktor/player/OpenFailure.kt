// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

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
}
