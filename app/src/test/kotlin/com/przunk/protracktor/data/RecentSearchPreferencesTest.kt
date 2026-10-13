// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.przunk.protracktor.player.RecentSearchHistory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RecentSearchPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("protracktor_preferences", Context.MODE_PRIVATE)
    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun `new installs have no history and reopening restores ordered unicode queries`() {
        assertTrue(RecentSearchPreferences(context).read().isEmpty())
        val history = RecentSearchHistory(RecentSearchPreferences(context))
        history.record("Żółw \"quoted\"")
        history.record("folder/name")
        val reopened = RecentSearchHistory(RecentSearchPreferences(context.createConfigurationContext(context.resources.configuration)))
        assertEquals(listOf("folder/name", "Żółw \"quoted\""), reopened.queries.value)
    }

    @Test fun `bounded deduplicated writes do not change other preferences`() {
        preferences.edit().putString("stereo_mix", "narrow").putString("cache_ahead", "all").commit()
        val history = RecentSearchHistory(RecentSearchPreferences(context))
        for (i in 0..25) history.record("query $i")
        history.record("QUERY 25")
        assertEquals(20, RecentSearchPreferences(context).read().size)
        assertEquals("QUERY 25", RecentSearchPreferences(context).read().first())
        assertEquals("narrow", preferences.getString("stereo_mix", null))
        assertEquals("all", preferences.getString("cache_ahead", null))
    }

    @Test fun `malformed or differently typed storage is read without rewriting it`() {
        preferences.edit().putString("recent_searches", "broken").commit()
        assertTrue(RecentSearchPreferences(context).read().isEmpty())
        assertEquals("broken", preferences.getString("recent_searches", null))
        preferences.edit().putInt("recent_searches", 123).commit()
        assertTrue(RecentSearchPreferences(context).read().isEmpty())
        assertEquals(123, preferences.getInt("recent_searches", 0))
    }

    @Test fun `non string values are ignored and initial reads normalize without writing`() {
        val encoded = "[\" first \",4,null,true,\"FIRST\",\"second\"]"
        preferences.edit().putString("recent_searches", encoded).commit()
        assertEquals(listOf("first", "second"), RecentSearchHistory(RecentSearchPreferences(context)).queries.value)
        assertEquals(encoded, preferences.getString("recent_searches", null))
    }
}
