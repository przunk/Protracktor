// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * The colours a track's generated cover is drawn in.
 *
 * **These formats have no cover art and never will**, and until now the player notification said so
 * by leaving a hole: since Android 12 a `MediaStyle` notification takes its colour from the artwork
 * it is given, reserves the tall media layout whether artwork arrives or not, and `setColorized`
 * does nothing without one. So the system drew a large grey player around an empty square
 * (`docs/WISHLIST.md` B21).
 *
 * What fills it is **the format name over a colour derived from the file**, and neither half would
 * do alone: the colour stops the bar being grey, the glyph stops it belonging to nothing. The glyph is a fact about the file —
 * what a `.sndh` is — while the colour is decoration, which is the right way round: a tune's
 * identity should not be something we invented.
 *
 * Android-free so the arithmetic can be tested, and the arithmetic is the part that can go quietly
 * wrong: a hue with poor contrast against the label is invisible on exactly the tunes that draw it.
 */
object TrackArtwork {

    /**
     * How saturated every generated cover is, and how bright it is *allowed* to be.
     *
     * Saturation is fixed so the set looks like one thing rather than a bag of colours. Lightness is
     * **not** fixed, and that is the whole trick: at one HSL lightness a yellow and a blue have
     * wildly different perceived brightness -- 2.6:1 and 12:1 against the same label -- so a single
     * value either makes the yellows illegible or the blues black. Instead each hue is given the
     * lightness that puts it at [TARGET_LUMINANCE], which makes the label read identically on all
     * 360 of them. `TrackArtworkTest` checks that, exhaustively rather than by sampling.
     */
    private const val SATURATION = 0.55f

    /**
     * The relative luminance every cover is drawn at.
     *
     * Chosen from the contrast it produces against [FOREGROUND], not by eye: it lands every hue at
     * 5.6:1, comfortably past WCAG AA's 4.5 for normal text, and the label here is large and bold.
     */
    private const val TARGET_LUMINANCE = 0.115f

    /** The label's colour: near-white, warm enough not to look like a system dialog. */
    val FOREGROUND: Int = 0xFFF4F1EA.toInt()

    /**
     * A hue in 0..359 for a seed, stable across runs and installs.
     *
     * **FNV-1a rather than `String.hashCode`.** Both are stable and specified; the difference is
     * distribution. Track names in one folder differ by a character or two — `mod.part 1`,
     * `mod.part 2` — and Java's polynomial hash maps those to adjacent integers, which after a
     * modulo puts them at adjacent hues. Neighbouring tracks are exactly the ones the user sees one
     * after another, so that is the case that must not collapse. FNV mixes every byte through the
     * whole word and does not.
     */
    fun hueOf(seed: String): Int {
        var hash = 0x811C9DC5u
        for (byte in seed.encodeToByteArray()) {
            hash = hash xor (byte.toUByte().toUInt())
            hash *= 0x01000193u
        }
        // A final avalanche, because FNV alone leaves the last byte's influence sitting in the low
        // bits -- and the low bits are what survives `% 360`. Without this, `part 1` and `part 3`
        // came out two degrees apart while `part 1` and `part 2` were fine: the alternation was
        // visible and the pattern was the hash's, not the music's.
        hash = hash xor (hash shr 15)
        hash *= 0x2545F491u
        hash = hash xor (hash shr 13)
        return (hash % 360u).toInt()
    }

    /** The cover's ground colour, as ARGB. */
    fun backgroundOf(seed: String): Int = backgroundForHue(hueOf(seed))

    /** The same, for a hue directly, so the contrast check can sweep all 360 without inverting a hash. */
    fun backgroundForHue(hue: Int): Int =
        hslToArgb(hue.toFloat(), SATURATION, lightnessFor(hue.toFloat()))

    /**
     * The HSL lightness that puts this hue at [TARGET_LUMINANCE].
     *
     * A bisection rather than a formula: inverting sRGB's gamma curve through the HSL construction
     * has no closed form worth writing, and this runs once per track change. Twenty-four halvings
     * settle far finer than a byte of output can show.
     */
    private fun lightnessFor(hue: Float): Float {
        var low = 0f
        var high = 1f
        repeat(24) {
            val mid = (low + high) / 2f
            if (relativeLuminance(hslToArgb(hue, SATURATION, mid)) < TARGET_LUMINANCE) {
                low = mid
            } else {
                high = mid
            }
        }
        return (low + high) / 2f
    }

    /**
     * The seed a track's colour is taken from.
     *
     * The **file name**, not the title. Titles are read out of the tune in the background and arrive
     * a moment after the row does, so seeding on one would change the notification's colour a second
     * after it appeared — a flicker with no meaning behind it. The file name is known before the
     * file is opened and does not change afterwards. Two copies of the same tune in two archives get
     * the same colour, which is right.
     */
    fun seedFor(fileName: String, title: String, id: String): String =
        fileName.ifBlank { title }.ifBlank { id }

    /**
     * Contrast ratio between two opaque colours, by WCAG's definition.
     *
     * Here so the test can state the requirement in the same terms the guideline does, rather than
     * eyeballing a screenshot once and trusting it forever.
     */
    fun contrastRatio(first: Int, second: Int): Float {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(argb: Int): Float {
        fun channel(shift: Int): Float {
            val v = ((argb shr shift) and 0xFF) / 255f
            return if (v <= 0.03928f) v / 12.92f else Math.pow(
                ((v + 0.055f) / 1.055f).toDouble(), 2.4
            ).toFloat()
        }
        return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
    }

    /** Hue in degrees, saturation and lightness in 0..1, to opaque ARGB. */
    private fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val h = hue / 60f
        val x = c * (1f - kotlin.math.abs(h.mod(2f) - 1f))
        val (r, g, b) = when {
            h < 1f -> Triple(c, x, 0f)
            h < 2f -> Triple(x, c, 0f)
            h < 3f -> Triple(0f, c, x)
            h < 4f -> Triple(0f, x, c)
            h < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = lightness - c / 2f
        fun byte(value: Float) = ((value + m).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }
}
