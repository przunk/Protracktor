// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Draws the square the player notification puts where a cover would be.
 *
 * Separate from [TrackArtwork] because this half needs a device to run and that half does not, and
 * only one of them holds a decision worth testing. The colours are arithmetic; this is a Canvas.
 *
 * **Cached to one entry.** `PlaybackService` republishes the media session five times a second so
 * the position keeps moving, and drawing a 512-pixel bitmap at that rate would be absurd. The cache
 * key is the same seed the colour comes from, so it changes exactly when the picture would.
 */
object TrackArtworkBitmaps {

    /**
     * 512 pixels square.
     *
     * What `MediaStyle` wants: the shade draws it small and the lock screen large, and the platform
     * scales down far better than it scales up. One ARGB_8888 bitmap of this size is a megabyte,
     * and there is only ever one.
     */
    private const val SIZE = 512

    private var cachedSeed: String? = null
    private var cached: Bitmap? = null

    /** The cover for a track, drawn once per track and then handed back. */
    @Synchronized
    fun forTrack(fileName: String, title: String, id: String): Bitmap {
        val seed = TrackArtwork.seedFor(fileName, title, id)
        cached?.let { if (cachedSeed == seed) return it }
        val drawn = draw(TrackArtwork.backgroundOf(seed), SupportedFormats.labelFor(fileName))
        cachedSeed = seed
        cached = drawn
        return drawn
    }

    private fun draw(background: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)

        val text = label.ifBlank { "?" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TrackArtwork.FOREGROUND
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Measured rather than chosen. Labels run from two characters to five -- "AY" to "MMCMP" --
        // and a fixed size that fits the longest wastes half the square on the shortest. The text is
        // sized to fill a fixed fraction of the width whatever it says, so every cover looks like
        // the same design.
        paint.textSize = SIZE * 0.5f
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() > 0) {
            paint.textSize *= (SIZE * 0.62f) / bounds.width()
            paint.getTextBounds(text, 0, text.length, bounds)
        }

        // Centred on the glyphs' own box, not on the font's line box: the format labels are all
        // capitals, so the font's descent is empty space that would push the text visibly high.
        canvas.drawText(text, SIZE / 2f, SIZE / 2f + bounds.height() / 2f, paint)
        return bitmap
    }
}
