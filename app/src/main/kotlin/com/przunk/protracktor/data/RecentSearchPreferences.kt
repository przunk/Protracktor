// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.data

import android.content.Context
import com.przunk.protracktor.player.RecentSearchHistory
import org.json.JSONArray
import org.json.JSONException

internal class RecentSearchPreferences(context: Context) : RecentSearchHistory.Store {
    private val preferences = context.getSharedPreferences("protracktor_preferences", Context.MODE_PRIVATE)

    override fun read(): List<String> = try {
        val encoded = preferences.getString("recent_searches", null)
        if (encoded == null) emptyList() else JSONArray(encoded).let { array ->
            (0 until array.length()).mapNotNull { array.opt(it) as? String }
        }
    } catch (_: JSONException) {
        emptyList()
    } catch (_: ClassCastException) {
        emptyList()
    }

    override fun write(queries: List<String>) {
        preferences.edit().putString("recent_searches", JSONArray(queries).toString()).apply()
    }
}
