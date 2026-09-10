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
