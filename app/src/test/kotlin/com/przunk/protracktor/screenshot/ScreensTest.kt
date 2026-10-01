// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.ui.ScanProgress
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of pieces of the app at a phone's width, for me to look at before an APK goes out
 * (`docs/WISHLIST.md` B39). They are written to `app/build/screenshots/`; nothing is compared yet.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ScreensTest {

    private fun shot(name: String, heightDp: Int, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        render(720, heightDp * 2) {
            ProtracktorTheme(theme = if (dark) AppTheme.DARK else AppTheme.LIGHT, dynamicColour = false) {
                // The background and the text colour the app's Scaffold gives every screen -- a bare
                // background left the words black on dark. Not a Surface itself: it draws through a
                // layer of its own, which drawing the view straight onto a bitmap leaves out.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }.saveAs(name)
    }

    @Test
    fun `the scan's bar and its Stop`() {
        shot("scan-progress-dark", 110, dark = true) { ScanProgress(6550, 11582) {} }
        shot("scan-progress-light", 110, dark = false) { ScanProgress(0, 0) {} }
    }
}
