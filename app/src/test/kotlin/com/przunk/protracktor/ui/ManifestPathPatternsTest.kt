// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `pathPattern` in the manifest starts with a slash.
 *
 * Google Play's deep-link report rejects the ones that do not — *"Add a `/` to the beginning of the
 * android:path attribute"* — and it says so about a published build, which is a slow way to find
 * out. A path always begins with `/`: a `content://` one is `/document/…`, a `file://` one is
 * absolute, an `http(s)` one starts at the root.
 *
 * There are over a hundred of these, three per extension, because `pathPattern`'s `.*` does not
 * backtrack. One missing slash among them is not something anybody will see by reading.
 */
class ManifestPathPatternsTest {

    private val manifest: String by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        val file = File(here, "app/src/main/AndroidManifest.xml")
        assertTrue("${file.path} is missing", file.isFile)
        file.readText()
    }

    @Test
    fun `no path pattern is missing its leading slash`() {
        val patterns = Regex("""android:pathPattern="([^"]*)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("no pathPattern found at all — has the filter moved?", patterns.size > 50)
        assertEquals(
            "Play refuses a pattern that does not begin with a slash",
            emptyList<String>(),
            patterns.filterNot { it.startsWith("/") },
        )
    }

    @Test
    fun `no web filter goes without a host, or the app is offered as a browser`() {
        // Android ignores every path pattern in a filter that names no host, so an http(s) filter
        // without one claims every web address: the owner found Protracktor offered as a browser.
        val filters = Regex("""<intent-filter[^>]*>(.*?)</intent-filter>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest).map { it.groupValues[1] }.toList()
        val web = filters.filter { Regex("""android:scheme="https?"""").containsMatchIn(it) }
        assertTrue("no web filter found at all — has it moved?", web.isNotEmpty())
        assertEquals(
            "every http(s) filter names its hosts",
            emptyList<String>(),
            web.filterNot { "android:host=" in it }.map { it.trim().take(120) },
        )
    }
}

