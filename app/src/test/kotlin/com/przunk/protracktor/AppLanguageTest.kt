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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `no stored override follows the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored(null))
        assertNull(AppLanguage.SYSTEM.languageTag)
    }

    @Test
    fun `Polish and English survive storage`() {
        assertEquals(AppLanguage.POLISH, AppLanguage.fromStored("pl"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStored("en"))
    }

    @Test
    fun `every explicit language round trips through its tag`() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach { language ->
            assertEquals(language, AppLanguage.fromStored(language.languageTag))
        }
    }

    @Test
    fun `an obsolete stored language cannot trap the settings screen`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored(""))
    }
}
