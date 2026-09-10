// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
