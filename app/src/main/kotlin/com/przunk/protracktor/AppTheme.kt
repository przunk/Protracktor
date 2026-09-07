// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor

import android.content.Context
import android.os.Build

/** Light, dark, or whatever the phone is doing. */
enum class AppTheme(val stored: String?) {
    SYSTEM(null),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStored(value: String?): AppTheme = entries.firstOrNull { it.stored == value } ?: SYSTEM
    }
}

/**
 * How the app looks, kept beside how it speaks.
 *
 * The same preferences file as [AppLocale] and read the same way, because these are two answers to
 * one question — what this person wants the app to be — and splitting them across two stores would
 * be filing by implementation rather than by meaning.
 *
 * **Both are read before the first frame.** A theme applied after Compose has drawn is a flash of
 * the wrong colours, which is the visual equivalent of the half-translated screen `AppLocale`
 * exists to prevent.
 */
object Appearance {
    private const val PREFERENCES = "protracktor_preferences"
    private const val THEME = "app_theme"
    private const val DYNAMIC = "dynamic_colour"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun theme(context: Context): AppTheme = AppTheme.fromStored(prefs(context).getString(THEME, null))

    /** @return whether anything changed, so the caller knows whether to redraw. */
    fun selectTheme(context: Context, theme: AppTheme): Boolean {
        if (theme(context) == theme) return false
        prefs(context).edit().apply {
            if (theme.stored == null) remove(THEME) else putString(THEME, theme.stored)
        }.apply()
        return true
    }

    /**
     * Whether to take colours from the wallpaper.
     *
     * **Only Android 12 knows how**, so below that this is always false however it was stored —
     * a setting that is on and does nothing is worse than one that is not offered.
     *
     * On by default where it exists: it is what a Material app is expected to do, and somebody who
     * dislikes it turns it off once.
     */
    fun dynamicColour(context: Context): Boolean =
        supportsDynamicColour && prefs(context).getBoolean(DYNAMIC, true)

    fun selectDynamicColour(context: Context, enabled: Boolean): Boolean {
        if (!supportsDynamicColour || dynamicColour(context) == enabled) return false
        prefs(context).edit().putBoolean(DYNAMIC, enabled).apply()
        return true
    }

    val supportsDynamicColour: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
