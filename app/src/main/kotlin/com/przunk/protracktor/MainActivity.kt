// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.przunk.protracktor.ui.ProtracktorApp
import com.przunk.protracktor.ui.theme.ProtracktorTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // The locale has to be present before onCreate: changing it after Compose has already read
        // the resources leaves half the screen in the old language until another configuration
        // change happens to recreate it.
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Read here rather than remembered in Compose: a theme change recreates the activity,
            // exactly as a language change does, so this reads the new value on the way back in.
            // Recreating is heavier than recomposing and it is the honest way to change something
            // the window itself was built with.
            ProtracktorTheme(
                theme = Appearance.theme(this),
                dynamicColour = Appearance.dynamicColour(this),
            ) {
                ProtracktorApp(
                    selectedLanguage = AppLocale.selected(this),
                    onLanguageSelected = { language ->
                        if (AppLocale.select(this, language)) recreate()
                    },
                    selectedTheme = Appearance.theme(this),
                    onThemeSelected = { theme ->
                        if (Appearance.selectTheme(this, theme)) recreate()
                    },
                    dynamicColour = Appearance.dynamicColour(this),
                    onDynamicColourChanged = { enabled ->
                        if (Appearance.selectDynamicColour(this, enabled)) recreate()
                    },
                )
            }
        }
    }
}
