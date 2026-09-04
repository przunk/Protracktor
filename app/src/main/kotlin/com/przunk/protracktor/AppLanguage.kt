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

/** A language the app can apply itself. `null` means to leave Android's locale untouched. */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    POLISH("pl"),
    ENGLISH("en");

    companion object {
        /** Unknown stored values are treated as System so a stale preference cannot trap the UI. */
        fun fromStored(languageTag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag == languageTag } ?: SYSTEM
    }
}
