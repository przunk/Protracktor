// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.player.PlaybackController
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings → Music sources, read from the table the build copies in (`docs/WISHLIST.md` B41). */
class MusicSourcesTest {

    private val table: String by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        File(here, "app/notices/sources.tsv").readText()
    }

    @Test
    fun `every archive the app offers is credited`() {
        // A catalogue added without a row would take music from somebody the app never names.
        val ids = MusicSources.parse(table, polish = false).map { it.id }
        for (catalogue in Catalogue.all) assertTrue(catalogue.id, catalogue.id in ids)
    }

    @Test
    fun `the host the song lengths come from is credited`() {
        val host = URI(PlaybackController.SONG_LENGTHS_URL).host
        val hosts = MusicSources.parse(table, polish = false).map { URI(it.url).host }
        assertTrue(host, host in hosts)
    }

    @Test
    fun `each row says something in both languages, and links over https`() {
        val english = MusicSources.parse(table, polish = false)
        val polish = MusicSources.parse(table, polish = true)
        assertEquals(english.map { it.id }, polish.map { it.id })
        for ((en, pl) in english.zip(polish)) {
            assertTrue(en.id, en.text.isNotBlank() && pl.text.isNotBlank())
            assertNotEquals(en.id, en.text, pl.text)
            assertTrue(en.url, en.url.startsWith("https://"))
        }
    }

    @Test
    fun `the page's rows are its own`() {
        // The page offers no Mod Archive and fetches nothing from DeepSID yet.
        val web = MusicSources.parse(table, polish = false, build = OpenSourceNotices.WEB).map { it.id }
        assertTrue("modland" in web && "hvsc" in web)
        assertTrue("modarchive" !in web && "deepsid" !in web)
    }
}
