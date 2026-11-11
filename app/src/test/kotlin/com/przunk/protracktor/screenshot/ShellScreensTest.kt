// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import android.app.Application
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.PlaybackShellTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** The redesigned shell as the app draws it -- the real playlist, dock and navigation -- to look at. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [29], application = Application::class, qualifiers = "en-w360dp-h800dp-xhdpi")
class ShellScreensTest : PlaybackShellTest() {
    private val tracks = listOf("Battle of honour" to "Treks", "Studium Fałszu" to "Jakim", "Space Ninja" to "Lotus 3",
        "Elysium" to "Jester", "Stardust Memories" to "Jester", "Hybris" to "Mr. Man", "Enigma" to "Phenomena",
        "Space Debris" to "Captain").mapIndexed { i, (title, author) ->
        TrackRef("content://shell.screens/$i.mod", title, author = author, fileName = "$i.mod")
    }

    private fun seed() {
        val catalogues = ReflectionHelpers.getField<CatalogueBrowser>(controller, "catalogueBrowser")
        ReflectionHelpers.setField(catalogues, "backends", { "host-fixture" })
        compose.runOnIdle {
            player.value = player.value.copy(queue = PlayQueue(tracks).startAt(0), playback = null, playing = true,
                positionSeconds = 64.0, durationSeconds = 200.0, metadata = mapOf("title" to tracks[0].title))
        }
        frame(400)
    }

    private fun shot(name: String) {
        frame(400)
        val view = activity.get().window.decorView
        android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            .also { view.draw(android.graphics.Canvas(it)) }.saveAs(name)
    }

    private fun pictures(suffix: String) {
        seed()
        shot("shell-playlist-$suffix")
        compose.onNodeWithTag("nav-browse").performClick(); shot("shell-browse-$suffix")
        compose.onNodeWithTag("nav-more").performClick(); shot("shell-more-$suffix")
    }

    @Test fun english() = pictures("en")
    @Test @Config(sdk = [29], application = Application::class, qualifiers = "pl-w360dp-h800dp-xhdpi")
    fun polish() = pictures("pl")
}
