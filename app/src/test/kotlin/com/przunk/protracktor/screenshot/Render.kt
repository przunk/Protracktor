// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import java.io.File
import java.time.Duration
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Composes [content] at a fixed size and draws it to a bitmap, with no window in the way
 * (`docs/WISHLIST.md` B39) -- the way Kratkoza does it.
 *
 * The compose test rule's `captureToImage` asks the platform to redraw a window and waits for the
 * frame, and there is no window here to draw one; drawing the view straight onto a bitmap asks for
 * the same pixels without one. Time is advanced by a stated amount rather than run to idle: a screen
 * with an endless animation -- a progress bar, a spinner -- is never idle.
 */
internal fun render(
    width: Int,
    height: Int,
    atMillis: Long = 0,
    content: @Composable () -> Unit,
): Bitmap {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    activity.setContent(content = content)
    val view: View = activity.window.decorView
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(atMillis.coerceAtLeast(FIRST_FRAME_MILLIS)))
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}

/** Where the pictures go: the build directory, never the repository. */
internal fun Bitmap.saveAs(name: String): File =
    File(System.getProperty("protracktor.screenshots") ?: "build/screenshots", "$name.png").also { file ->
        file.parentFile?.mkdirs()
        file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

/** Enough for composition and one layout pass; less and nothing has been drawn yet. */
private const val FIRST_FRAME_MILLIS = 32L
