/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
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
