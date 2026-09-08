// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.przunk.protracktor.ui.ProtracktorApp
import com.przunk.protracktor.ui.theme.ProtracktorTheme

class MainActivity : ComponentActivity() {

    /**
     * The file another app asked us to open, until it has been handed on.
     *
     * State rather than a field read once: the same activity is reused when a second file is
     * tapped, so this has to be able to change after `setContent` has already run. `onNewIntent`
     * writes it; the composition reads it and clears it.
     */
    private var pendingOpen by mutableStateOf<Uri?>(null)
    override fun attachBaseContext(newBase: Context) {
        // The locale has to be present before onCreate: changing it after Compose has already read
        // the resources leaves half the screen in the old language until another configuration
        // change happens to recreate it.
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    /**
     * The URI in an intent, whether it came as a tap or as a share.
     *
     * `ACTION_VIEW` puts it in the data; `ACTION_SEND` puts it in `EXTRA_STREAM`, which is the
     * share sheet's shape and the reason a share needs its own line rather than falling through.
     */
    private fun openableUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The activity is `singleTask`, so a second tune tapped while this one runs arrives here
        // rather than in a new activity. Without this it would be ignored entirely.
        setIntent(intent)
        openableUri(intent)?.let { pendingOpen = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpen = openableUri(intent)
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
                    webPlayer = Appearance.webPlayer(this),
                    onWebPlayerChanged = { Appearance.selectWebPlayer(this, it) },
                    externalOpen = pendingOpen,
                    onExternalOpened = { pendingOpen = null },
                )
            }
        }
    }
}
