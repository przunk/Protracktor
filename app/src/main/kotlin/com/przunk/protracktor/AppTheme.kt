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
    private const val WEB_PLAYER = "web_player_base"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun theme(context: Context): AppTheme = AppTheme.fromStored(prefs(context).getString(THEME, null))

    /**
     * Where the web player is served from, for "send this queue to the browser".
     *
     * Kept here with the other answers to "what does this person want the app to be", and stored
     * rather than hard-coded because the address is the one part of the handoff that moves: today it
     * is a local server, later a tunnel, eventually a host. The default is `localhost`, which reads
     * as wrong from a phone and is exactly right — **the link is opened on the machine running the
     * browser**, and that machine is where the page is served.
     */
    fun webPlayer(context: Context): String =
        prefs(context).getString(WEB_PLAYER, null)?.takeIf { it.isNotBlank() }
            ?: com.przunk.protracktor.player.QueueLink.DEFAULT_BASE

    fun selectWebPlayer(context: Context, base: String): Boolean {
        if (webPlayer(context) == base) return false
        prefs(context).edit().apply {
            if (base.isBlank()) remove(WEB_PLAYER) else putString(WEB_PLAYER, base.trim())
        }.apply()
        return true
    }

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
