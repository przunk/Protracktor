// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

/**
 * The privacy policy as the app shows it: `store/privacy-policy.md`, one language of it, as plain
 * blocks.
 *
 * The file is Markdown because it is also the publication source, and only the handful of things it
 * actually uses are understood here: `###` headings, `-` bullets, paragraphs wrapped across lines,
 * and `**bold**`, backticks and `<links>` taken off. A renderer for the rest of Markdown would be a
 * dependency for a file that does not use it.
 */
object LegalText {

    const val PRIVACY_ASSET = "legal/privacy-policy.md"

    sealed interface Block {
        data class Heading(val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
    }

    /**
     * The blocks of the Polish section when [polish], of the English one otherwise, headed by the
     * effective date.
     */
    fun privacyBlocks(markdown: String, polish: Boolean): List<Block> {
        val lines = markdown.lines()
        val date = lines.firstOrNull { it.startsWith("Effective date:") }?.let { Block.Paragraph(clean(it)) }
        val sectionTitle = if (polish) "## Polski" else "## English"
        val start = lines.indexOfFirst { it.trim() == sectionTitle }
        if (start < 0) return listOfNotNull(date)
        val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }
            .let { if (it < 0) lines.size else start + 1 + it }

        val blocks = mutableListOf<Block>()
        date?.let(blocks::add)
        val pending = StringBuilder()
        var bullet = false
        fun flush() {
            if (pending.isNotEmpty()) {
                val text = clean(pending.toString())
                blocks += if (bullet) Block.Bullet(text) else Block.Paragraph(text)
                pending.clear()
            }
            bullet = false
        }
        for (line in lines.subList(start + 1, end)) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flush()
                trimmed.startsWith("### ") -> {
                    flush(); blocks += Block.Heading(clean(trimmed.removePrefix("### ")))
                }
                trimmed.startsWith("- ") -> {
                    flush(); bullet = true; pending.append(trimmed.removePrefix("- "))
                }
                else -> {
                    if (pending.isNotEmpty()) pending.append(' ')
                    pending.append(trimmed)
                }
            }
        }
        flush()
        return blocks
    }

    /** Markdown's emphasis, code and angle-bracket links taken off; the words stay. */
    fun clean(text: String): String = text
        .replace("**", "")
        .replace("`", "")
        .replace(Regex("<(https?://[^>]+)>"), "$1")
        .trim()
}
