// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An action pill is tall enough for what is drawn inside it.
 *
 * This was wrong twice. First the height was fixed in dp while the label is in sp, so it clipped at
 * a larger system text size (`docs/STATUS.md` C62). Then the height scaled but the *base* was still
 * 46dp against contents of 50dp, so `Settings` lost the tail of its **g** on an ordinary phone at
 * the ordinary text size — which is what the owner saw in a build that was supposed to have fixed
 * it.
 *
 * So the arithmetic is a test rather than a comment. It reproduces what Compose stacks up inside
 * the pill; if somebody changes the icon, the padding or the type scale, this fails before a phone
 * does.
 *
 * Not a layout test — there is no Compose in this suite. It is the sum that the layout obeys.
 */
class ActionPillFitsTest {

    /** What `LabelledAction` puts in a pill, in device-independent pixels. */
    private fun contents(slim: Boolean, fontScale: Float): Float {
        val padding = if (slim && fontScale > 1.25f) 3f + 3f else 6f + 6f
        val icon = if (slim) 20f else 24f
        val gapUnderIcon = 2f
        val lines = if (slim) 1 else 2
        // `labelSmall` is 11sp on a 16sp line, and a line box is what a descender needs.
        val label = 16f * fontScale * lines
        return padding + icon + gapUnderIcon + label
    }

    private fun pillHeight(slim: Boolean, fontScale: Float): Float {
        val scale = fontScale.coerceIn(1f, 2f)
        return if (slim) minOf(SLIM_BASE * scale, SLIM_MAX) else FULL_BASE * scale
    }

    @Test
    fun `a slim pill holds its label at every text size`() {
        forEachScale { scale ->
            val room = pillHeight(slim = true, fontScale = scale)
            val needed = contents(slim = true, fontScale = scale)
            assertTrue(
                "at ${scale}x a slim pill offers $room dp for $needed dp of contents",
                room >= needed,
            )
        }
    }

    @Test
    fun `and so does a full one`() {
        forEachScale { scale ->
            val room = pillHeight(slim = false, fontScale = scale)
            val needed = contents(slim = false, fontScale = scale)
            assertTrue(
                "at ${scale}x a pill offers $room dp for $needed dp of contents",
                room >= needed,
            )
        }
    }

    @Test
    fun `a slim pill still fits the bar it lives in`() {
        // `TopAppBar` is 64dp and clips what will not fit, so growing past it moves the defect one
        // level out rather than fixing it.
        forEachScale { scale ->
            assertTrue(
                "at ${scale}x a slim pill is ${pillHeight(true, scale)} dp",
                pillHeight(slim = true, fontScale = scale) <= TOP_APP_BAR_HEIGHT,
            )
        }
    }

    private fun forEachScale(check: (Float) -> Unit) {
        // Android offers up to 2x in Settings, and every step between is somebody's phone.
        listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f).forEach(check)
    }

    private companion object {
        /** The values in `LabelledAction`. Kept here as numbers, so a change there fails here. */
        const val SLIM_BASE = 56f
        const val SLIM_MAX = 64f
        const val FULL_BASE = 76f
        const val TOP_APP_BAR_HEIGHT = 64f
    }
}
