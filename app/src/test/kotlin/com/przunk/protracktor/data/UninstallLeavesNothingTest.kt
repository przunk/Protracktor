// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uninstalling takes the database with it.
 *
 * Android's Auto Backup is **on unless a manifest says otherwise**, and it undoes an uninstall
 * silently: the app comes back from Google Drive with the old playlists, the old history and
 * whatever else fitted in the quota. That reached the owner as "I reinstalled and my old list was
 * still there".
 *
 * A manifest attribute has no unit test of its own, so this is one: it reads the manifest. Crude,
 * and the alternative is finding out on somebody's phone a second time.
 *
 * `res/xml/data_extraction_rules.xml` carries the reasoning; `allowBackup` alone does not cover
 * device-to-device transfer from Android 12 onwards, which is why both are checked.
 */
class UninstallLeavesNothingTest {

    private val manifest: String by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        val file = here?.let { File(it, "app/src/main/AndroidManifest.xml") }
        assertTrue("AndroidManifest.xml not found from ${File(".").absolutePath}", file?.isFile == true)
        file!!.readText()
    }

    @Test
    fun `cloud backup is off`() {
        assertTrue(
            "android:allowBackup must be false — Auto Backup restores the database over a fresh install",
            manifest.contains("""android:allowBackup="false""""),
        )
    }

    @Test
    fun `and so is device-to-device transfer`() {
        assertTrue(
            "android:dataExtractionRules must point at the rules that exclude every domain",
            manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""),
        )
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        val rules = File(here, "app/src/main/res/xml/data_extraction_rules.xml")
        assertTrue("${rules.path} is missing", rules.isFile)
        val text = rules.readText()
        listOf("file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue(
                "the $domain domain is not excluded from device transfer",
                text.contains("""<exclude domain="$domain" path="." />"""),
            )
        }
        assertTrue("there is no device-transfer section", text.contains("<device-transfer>"))
    }
}
