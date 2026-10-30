// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModlandLegacyPathsTest {
    @Test fun `recovery recognizes a single exact unknown directory at different depths`() {
        for (prefix in listOf("YM", "Ad Lib/Screamtracker 3 AdLib", "Video Game Music/Console/Variant")) {
            val suffix = "Game (1990)/a+b #100%.s3m"
            assertEquals(
                Modland.urlFor("$prefix/_unknown/$suffix"),
                Modland.legacyUnknownAuthorUrl(Modland.urlFor("$prefix/- unknown/$suffix")),
            )
        }
        assertEquals(
            Modland.urlFor("Ad Lib/Screamtracker 3 AdLib/_unknown/- unknown.s3m"),
            Modland.legacyUnknownAuthorUrl(Modland.urlFor("Ad Lib/Screamtracker 3 AdLib/- unknown/- unknown.s3m")),
        )
    }

    @Test fun `format filenames lookalikes and ambiguous directories are not guessed`() {
        for (path in listOf(
            "- unknown/Composer/file.ym",
            "YM/Composer/- unknown",
            "Video Game Music/Sega Megadrive/Composer/- unknown",
            "Ad Lib/Screamtracker 3 AdLib/- unknown composer/file.s3m",
            "Video Game Music/Sega Megadrive/_unknown/file.vgz",
            "YM/- unknown/- unknown/file.ym",
        )) {
            assertNull(path, Modland.legacyUnknownAuthorUrl(Modland.urlFor(path)))
        }
    }

    @Test fun `invalid escapes and query or fragment addresses are not recovered`() {
        val old = Modland.urlFor("Ad Lib/Screamtracker 3 AdLib/- unknown/file.s3m")
        for (url in listOf("$old?download=yes", "$old#fragment", old.replace("file.s3m", "%ZZ.s3m"))) {
            assertNull(url, Modland.legacyUnknownAuthorUrl(url))
        }
    }

    @Test fun `encoded separators cannot turn part of a filename into an author directory`() {
        for (url in listOf(
            Modland.urlFor("Ad Lib/Screamtracker 3 AdLib/Composer/file") + "%2F-%20unknown%2Fother.s3m",
            Modland.urlFor("YM/Family") + "%2F-%20unknown/file.ym",
        )) {
            assertNull(url, Modland.legacyUnknownAuthorUrl(url))
        }
    }

    @Test fun `retained Modland identities match shared browser recovery cases`() {
        val root = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .first { java.io.File(it, "settings.gradle.kts").isFile }
        val lines = java.io.File(root, "docs/rules/modland-legacy-cases.tsv").readLines()
        val count = lines.first { it.startsWith("# cases: ") }.substringAfter(": ").toInt()
        val rows = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("case\turl\texpect", rows.first())
        require(count > 0)
        assertEquals("A shared legacy path case is missing", count, rows.size - 1)
        rows.drop(1).forEach { line ->
            val parts = line.split('\t')
            assertEquals(3, parts.size)
            assertEquals(parts[0], parts[2].takeUnless { it == "-" }, Modland.legacyUnknownAuthorUrl(parts[1]))
        }
    }
}
