// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The privacy policy as the app shows it, read from the published file itself. */
class LegalTextTest {

    private val policy: String by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, "settings.gradle.kts").isFile) here = here.parentFile
        File(here, "store/privacy-policy.md").readText()
    }

    @Test
    fun `each language shows its own section and not the other`() {
        val english = LegalText.privacyBlocks(policy, polish = false)
        val polish = LegalText.privacyBlocks(policy, polish = true)
        assertTrue(english.any { it is LegalText.Block.Heading && it.text == "Summary" })
        assertTrue(polish.any { it is LegalText.Block.Heading && it.text == "Podsumowanie" })
        assertFalse(english.any { it is LegalText.Block.Heading && it.text == "Podsumowanie" })
    }

    @Test
    fun `it opens with the effective date`() {
        val first = LegalText.privacyBlocks(policy, polish = false).first()
        assertTrue(first is LegalText.Block.Paragraph && (first as LegalText.Block.Paragraph).text.startsWith("Effective date:"))
    }

    @Test
    fun `every host the policy names is a bullet, with no markdown left in it`() {
        // The hosts are what the 0.7.0 update was about; if they stop reading as a list, the screen
        // has stopped showing what the policy says.
        val bullets = LegalText.privacyBlocks(policy, polish = false)
            .filterIsInstance<LegalText.Block.Bullet>().map { it.text }
        for (host in listOf("modland.com", "gitlab.com", "raw.githubusercontent.com", "svn.code.sf.net")) {
            assertTrue(host, bullets.any { it.startsWith(host) })
        }
        assertFalse(bullets.any { '`' in it || "**" in it })
    }

    @Test
    fun `wrapped lines join into one paragraph`() {
        val blocks = LegalText.privacyBlocks(
            "Effective date: x\n\n## English\n\n### Title\n\nOne line\nand its wrap.\n\n- a bullet\n  and its wrap\n",
            polish = false,
        )
        assertEquals(
            listOf(
                LegalText.Block.Paragraph("Effective date: x"),
                LegalText.Block.Heading("Title"),
                LegalText.Block.Paragraph("One line and its wrap."),
                LegalText.Block.Bullet("a bullet and its wrap"),
            ),
            blocks,
        )
    }
}
