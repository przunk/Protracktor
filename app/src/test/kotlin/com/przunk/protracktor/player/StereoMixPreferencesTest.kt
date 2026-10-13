// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.przunk.protracktor.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class StereoMixPreferencesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("protracktor_preferences", Context.MODE_PRIVATE)

    @Before fun resetMix() { preferences.edit().remove("stereo_mix").commit() }

    @Test fun `missing setting defaults to Original without writing preferences`() {
        assertEquals(StereoMix.ORIGINAL, Appearance.stereoMix(context))
        assertFalse(preferences.contains("stereo_mix"))
    }

    @Test fun `all modes are stored and a new reader recovers them`() {
        listOf(StereoMix.NARROW, StereoMix.MONO, StereoMix.ORIGINAL).forEach { mode ->
            assertTrue(Appearance.selectStereoMix(context, mode))
            assertEquals(mode.stored, preferences.getString("stereo_mix", null))
            assertEquals(mode, Appearance.stereoMix(ContextWrapper(context)))
        }
    }

    @Test fun `selecting the same mode reports no change and preserves unrelated preferences`() {
        preferences.edit().putString("app_theme", "dark").putString("cache_ahead", "off").commit()
        assertTrue(Appearance.selectStereoMix(context, StereoMix.MONO))
        assertFalse(Appearance.selectStereoMix(context, StereoMix.MONO))
        assertEquals("dark", preferences.getString("app_theme", null))
        assertEquals("off", preferences.getString("cache_ahead", null))
    }

    @Test fun `an unknown future setting falls back without destroying its stored value`() {
        preferences.edit().putString("stereo_mix", "future-mode").commit()
        assertEquals(StereoMix.ORIGINAL, Appearance.stereoMix(context))
        assertEquals("future-mode", preferences.getString("stereo_mix", null))
        assertTrue(Appearance.selectStereoMix(context, StereoMix.NARROW))
        assertEquals(StereoMix.NARROW, Appearance.stereoMix(context))
    }
}
