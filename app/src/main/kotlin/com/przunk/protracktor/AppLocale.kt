// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Persists and applies the in-app language before Android inflates any resources. */
object AppLocale {
    private const val PREFERENCES = "protracktor_preferences"
    private const val LANGUAGE = "app_language"

    fun selected(context: Context): AppLanguage = AppLanguage.fromStored(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE, null)
    )

    /** Returns true only when callers need to recreate their activity. */
    fun select(context: Context, language: AppLanguage): Boolean {
        if (selected(context) == language) return false
        val edit = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        if (language.languageTag == null) edit.remove(LANGUAGE) else edit.putString(
            LANGUAGE,
            language.languageTag,
        )
        edit.apply()
        return true
    }

    private class Localized(val app: Context, val tag: String?, val context: Context)

    @Volatile private var localizedApp: Localized? = null

    /**
     * The application's context in the app's own language, **for text made away from a screen**
     * (RV-B004): the player's notices, the download banners, the scan notification. The language is
     * applied by wrapping a context, and those were built from the bare application context, which
     * follows the phone's -- so with the app set to English on a Polish phone the screen was English
     * and the snackbars Polish. Made once per language and kept, since a wrap is not free.
     */
    fun localized(context: Context): Context {
        val app = context.applicationContext
        val tag = selected(app).languageTag
        localizedApp?.let { if (it.app === app && it.tag == tag) return it.context }
        return wrap(app).also { localizedApp = Localized(app, tag, it) }
    }

    fun wrap(context: Context): Context {
        val tag = selected(context).languageTag ?: return context
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
