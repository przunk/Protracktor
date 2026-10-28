// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.przunk.protracktor.AppTheme

/** AndroidX defaults follow the phone; explicit app Light/Dark must also own the clock/icons. */
internal fun ComponentActivity.enableAppEdgeToEdge(theme: AppTheme) {
    enableEdgeToEdge()
    val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val light = !theme.isDark(systemDark)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = light
        isAppearanceLightNavigationBars = light
    }
}
