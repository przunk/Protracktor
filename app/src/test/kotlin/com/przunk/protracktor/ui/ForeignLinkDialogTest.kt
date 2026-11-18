// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.robolectric.Robolectric
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** RV-B001: the question names the server, and only Play goes on. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class ForeignLinkDialogTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `the question says how many and where, and its buttons answer it`() {
        val calls = mutableListOf<String>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            MaterialTheme {
                ForeignLinkDialog(listOf("https://evil.example/x.mod", "https://evil.example/y.mod"),
                    onPlay = { calls += "play" }, onCancel = { calls += "cancel" })
            }
        }
        compose.onNodeWithTag("foreign-link-body").assertTextContains("2 tunes", substring = true)
            .assertTextContains("evil.example", substring = true)
        compose.onNodeWithText(activity.getString(R.string.action_cancel)).performClick()
        compose.onNodeWithText(activity.getString(R.string.link_foreign_play)).performClick()
        assertEquals(listOf("cancel", "play"), calls)
    }
}
