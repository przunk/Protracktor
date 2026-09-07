// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

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
