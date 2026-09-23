// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of four things to say when a track will not open.
 *
 * Written after the single message got it wrong twice in one day, and the tests are those two days
 * turned into assertions: a file that never downloaded must not be reported as an unsupported
 * format, and one bad file must not be reported as an unsupported platform.
 */
class OpenFailureTest {

    /**
     * `ice.pt2` — the download failed and the app said nothing about the network.
     *
     * The decoder never saw the bytes, so nothing about the format has been established. It played
     * on the second attempt, which is what a transient fetch failure looks like from the sofa.
     */
    @Test
    fun `a file that never arrived is not a format problem`() {
        assertEquals(
            OpenFailure.Kind.NOT_FETCHED,
            OpenFailure.kindOf(fetched = false, claimed = true, reason = ""),
        )
        // Even for a name nothing claims: we still did not get far enough to say so.
        assertEquals(
            OpenFailure.Kind.NOT_FETCHED,
            OpenFailure.kindOf(fetched = false, claimed = false, reason = "no backend"),
        )
    }

    /**
     * `&SFTDEMO.stc` — one file of 3,639, reported as the whole platform being unsupported.
     *
     * This build plays 95% of Modland's `.stc`. Claiming the format and then blaming it is not a
     * rough approximation; it is false, and it sent an hour of searching in the wrong direction.
     */
    @Test
    fun `a claimed format is never blamed for one file`() {
        assertEquals(
            OpenFailure.Kind.FILE_REFUSED,
            OpenFailure.kindOf(fetched = true, claimed = true, reason = ""),
        )
        assertEquals(
            OpenFailure.Kind.FILE_REFUSED_WITH_REASON,
            OpenFailure.kindOf(fetched = true, claimed = true, reason = "sc68 refused it"),
        )
    }

    /** And where the app really has no decoder, saying so is fair and useful. */
    @Test
    fun `an unclaimed name is a format we do not play`() {
        assertEquals(
            OpenFailure.Kind.FORMAT_UNSUPPORTED,
            OpenFailure.kindOf(fetched = true, claimed = false, reason = ""),
        )
        // A reason does not change it: no backend claimed the name in the first place.
        assertEquals(
            OpenFailure.Kind.FORMAT_UNSUPPORTED,
            OpenFailure.kindOf(fetched = true, claimed = false, reason = "libopenmpt refused"),
        )
    }

    /**
     * The catalogue's name for a format beats the file's extension, where there is one.
     *
     * Modland's directory names a format precisely; an extension can stand for two unrelated ones.
     * `.psm` is 90 files of Epic MegaGames MASI and 51 of Pro Sound Maker.
     */
    @Test
    fun `the format is named as precisely as the sources allow`() {
        assertEquals("Pro Sound Maker", OpenFailure.formatName("Pro Sound Maker", "x.psm"))
        assertEquals("PSM", OpenFailure.formatName(null, "x.psm"))
        assertEquals("PSM", OpenFailure.formatName("  ", "x.psm"))
        // Nothing to go on at all still has to read as a sentence.
        assertEquals("This", OpenFailure.formatName(null, "README"))
    }

    @Test
    fun `an Amiga custom tune without its replay routines says what to download`() {
        // Before this case existed the last decoder asked was libopenmpt, so a TFMX file on a phone
        // without the download was reported as refused by the tracker decoder.
        assertEquals(
            OpenFailure.Kind.NEEDS_AMIGA_PLAYERS,
            OpenFailure.kindOf(fetched = true, claimed = true, reason = "libopenmpt refused it", needsPlayers = true),
        )
        assertTrue(SupportedFormats.needsUade("mdat.turrican") && SupportedFormats.needsUade("alfred chicken.dw"))
        assertTrue(!SupportedFormats.needsUade("elysium.mod") && !SupportedFormats.needsUade("x.sid"))
    }

    @Test
    fun `and a file that did not arrive is still reported as not arriving`() {
        assertEquals(
            OpenFailure.Kind.NOT_FETCHED,
            OpenFailure.kindOf(fetched = false, claimed = true, reason = "", needsPlayers = true),
        )
    }

    @Test
    fun `a TFMX song whose samples did not arrive says so, not that UADE is puzzled`() {
        assertEquals(
            OpenFailure.Kind.COMPANION_MISSING,
            OpenFailure.kindOf(
                fetched = true, claimed = true,
                reason = "the Amiga decoder (UADE) does not recognise it",
                companionMissing = true,
            ),
        )
    }

    @Test
    fun `a clause after a decoder's reason follows a full stop, and only one`() {
        // "error loading file" is how libopenmpt ends it; our own sentences already have a stop.
        assertEquals("nokia.gtk would not open: error loading file.", OpenFailure.sentence("nokia.gtk would not open: error loading file"))
        assertEquals("x.mod would not open. So it is this one.", OpenFailure.sentence("x.mod would not open. So it is this one."))
    }
}
