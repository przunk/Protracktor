// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Settings → About → Feedback: what each labelled action actually hands to another app. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class FeedbackRowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    @Before fun start() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent { ProtracktorTheme(AppTheme.DARK, false) { Surface { FeedbackRow("1.6.0 (1467)") } } }
    }

    @After fun stop() {
        activity.get().setContent { }
        compose.waitForIdle()
        activity.close()
    }

    private fun started(): Intent = shadowOf(activity.get()).nextStartedActivity

    @Test fun `Email opens a message to me with the version in its subject and body`() {
        compose.onNodeWithText("Feedback").assertExists()
        compose.onNodeWithText("Email").performClick()
        val intent = started()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:", intent.dataString)
        assertArrayEquals(arrayOf("przunk@gmail.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Protracktor 1.6.0 (1467) — feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(body, "Protracktor 1.6.0 (1467)" in body && "Android" in body && "(API " in body)
    }

    @Test fun `Group opens the testers' group`() {
        compose.onNodeWithText("Group").performClick()
        val intent = started()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://groups.google.com/g/protracktor", intent.dataString)
    }
}
