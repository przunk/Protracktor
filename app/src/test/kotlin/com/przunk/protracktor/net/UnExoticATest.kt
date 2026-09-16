// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three things that would be wrong silently.
 *
 * A catalogue whose address rule is wrong fetches 404s and reports "the download failed", which is
 * true and useless. A catalogue whose index parser takes the wrong column stores plausible rubbish.
 * Both were live risks here: `docs/PLAN_CATALOGUES.md` carried a *wrong* URL rule for a day, guessed
 * from the directory layout, and this index is a TSV of two different row shapes sharing a file.
 */
class UnExoticATest {

    private val row = listOf(
        "008d5fd32596cec1258ff91428f900db", "0", "166099", "player", "uade",
        "David Whittaker", "0", "33014", "ea8ddb03", "64810443",
        "Game/Whittaker_David/Total_Recall.lha/Total_Recall/mod.ingame_2",
    ).joinToString("\t")

    private fun parse(text: String) =
        UnExoticA.parseIndex(text.toByteArray())

    @Test
    fun `an eleven column row becomes a track and a four column row does not`() {
        // The four-column shape states how long a later subsong is. Both live in one file, and
        // taking every line would produce entries whose path is the word "silence".
        val entries = parse(row + "\n008d5fd32596cec1258ff91428f900db\t1\t5831\tsilence\n")
        assertEquals(1, entries.size)
        assertEquals("Game/Whittaker_David/Total_Recall.lha/Total_Recall/mod.ingame_2", entries[0].path)
        assertEquals("mod.ingame_2", entries[0].title)
        // Column eight, the uncompressed size -- not column three, which is a song length in
        // milliseconds and looks just as much like a number of bytes.
        assertEquals(33014L, entries[0].size)
    }

    @Test
    fun `the group names the game and then the composer`() {
        val entries = parse(row)
        assertEquals("Game", entries[0].format)
        // The game leads because the archive is organised around games. The composer follows
        // because this column is what search matches -- without it, "Phelan" found nothing.
        assertEquals("Total Recall · David Whittaker", entries[0].author)
    }

    @Test
    fun `a composer folder is reversed only when the convention is certain`() {
        fun groupOf(folder: String) = parse(
            row.replaceFirst("Whittaker_David", folder),
        )[0].author

        // 524 of 571 folders are Surname_Firstname, which reads backwards until it is turned round.
        assertEquals("Total Recall · Patrick Phelan", groupOf("Phelan_Patrick"))
        // A one-word handle has nothing to reverse.
        assertEquals("Total Recall · Rooster", groupOf("Rooster"))
        // Three words or more is where a rule would start guessing -- a Dutch surname or a group
        // name, and no reordering is right for both. Left alone, which reads oddly and is not wrong.
        assertEquals("Total Recall · van der Valk Paul", groupOf("van_der_Valk_Paul"))
        // 340 tunes say "Unknown", which is longer than saying nothing and means the same.
        assertEquals("Total Recall", groupOf("Unknown"))
    }

    @Test
    fun `a row is titled by its member name, not by its path`() {
        // This used to be about the `keep` filter, which judged by extension or prefix and would
        // have judged "Game" if handed the path. The filter is gone -- an index keeps every row now
        // (`docs/ROADMAP_FORMATS.md` step 0) -- and the property it protected matters more than
        // before: `ext` and `pre` are computed from this title, and they decide what is offered.
        assertEquals(listOf("mod.ingame_2"), parse(row).map { it.title })
    }

    @Test
    fun `a path splits into the archive and the member inside it`() {
        val (archive, member) =
            UnExoticA.split("Game/Whittaker_David/Total_Recall.lha/Total_Recall/mod.ingame_2")!!
        assertEquals("Game/Whittaker_David/Total_Recall.lha", archive)
        assertEquals("Total_Recall/mod.ingame_2", member)
        // Not an archive path at all: refused rather than turned into a fetch of something else.
        assertNull(UnExoticA.split("Game/Whittaker_David/Total_Recall/mod.ingame_2"))
    }

    @Test
    fun `the file address is a query value with its slashes encoded`() {
        // Read off the download link on ExoticA's own wiki page. Every directory-shaped form of
        // this address answers 404, so the encoding is the rule rather than decoration.
        assertEquals(
            "https://files.exotica.org.uk/?file=exotica/" +
                "media%2Faudio%2FUnExoticA%2FGame%2FWhittaker_David%2FTotal_Recall.lha",
            UnExoticA.archiveUrlFor(
                "Game/Whittaker_David/Total_Recall.lha/Total_Recall/mod.ingame_2",
            ),
        )
    }

    @Test
    fun `a reference survives the trip out to a URL and back`() {
        val path = "Demo/Unknown/Some Demo.lha/Some Demo/mod.tune"
        assertEquals(path, UnExoticA.pathFrom(UnExoticA.urlFor(path)))
        // And belongs to nobody else.
        assertNull(Modland.pathFrom(UnExoticA.urlFor(path)))
        assertNull(Asma.pathFrom(UnExoticA.urlFor(path)))
        assertNull(UnExoticA.pathFrom("asma://asma/Composers/Aki/Atari_Style.sap"))
    }

    @Test
    fun `the switch decides whether the catalogue exists at all`() {
        // Not a preference: it is the owner's answer to a question ExoticA have not answered.
        // Whatever it is set to, the list and the constant have to agree -- a catalogue that is
        // offered while disabled would fetch from an archive that asked not to be fetched from.
        assertEquals(UnExoticA.ENABLED, Catalogue.all.contains(UnExoticA))
        assertEquals(UnExoticA.ENABLED, Catalogue.byId("unexotica") != null)
        if (UnExoticA.ENABLED) {
            assertTrue(Catalogue.owning(UnExoticA.urlFor("Game/A/B.lha/B/mod.c")) === UnExoticA)
        }
    }
}
