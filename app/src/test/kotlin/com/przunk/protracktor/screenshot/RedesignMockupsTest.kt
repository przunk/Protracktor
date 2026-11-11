// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.player.*
import com.przunk.protracktor.ui.*
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mock-ups for the redesign (`docs/UI_REDESIGN.md`), drawn from the real playlist and dock with
 * the new parts sketched here. Pictures to decide from, not the implementation: nothing in the
 * app uses these composables.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pl-w360dp-h800dp-xhdpi")
class RedesignMockupsTest {
    private val tracks = listOf(
        TrackRef("one", "Battle of honour", "Modland", fileName = "battle of honour.it", author = "Treks"),
        TrackRef("two", "Studium Fałszu", "Modland", fileName = "studium falszu.mod", author = "Jakim"),
        TrackRef("three", "Space Ninja", "Modland", fileName = "space ninja.mod", author = "Lotus 3"),
        TrackRef("four", "Elysium", "Modland", fileName = "elysium.mod", author = "Jester"),
        TrackRef("five", "Stardust Memories", "Modland", fileName = "stardust.mod", author = "Jester"),
        TrackRef("six", "Hybris", "Modland", fileName = "hybris.mod", author = "Mr. Man"),
        TrackRef("seven", "Enigma", "Modland", fileName = "enigma.mod", author = "Phenomena"),
        TrackRef("eight", "Space Debris", "Modland", fileName = "space debris.mod", author = "Captain"),
        TrackRef("nine", "Guitar Slinger", "Modland", fileName = "guitar slinger.mod", author = "Jogeir Liljedahl"),
        TrackRef("ten", "Aquarium", "Modland", fileName = "aquarium.mod", author = "Lizardking"),
    )
    // Playing from Browse, so the dock offers + (add to this playlist).
    private val state = PlayerUiState(queue = PlayQueue(tracks).startAt(0), playing = true,
        durationSeconds = 200.0, positionSeconds = 64.0, subsongCount = 5,
        playback = ActiveQueue(QueueSource.Random(1L), PlayQueue(tracks).startAt(0)))

    private enum class Dock { MINI, FULL }
    private enum class Overlay { NONE, MORE, SELECTING }

    private fun picture(name: String, dock: Dock, overlay: Overlay = Overlay.NONE, dark: Boolean = false) {
        render(720, 1600) {
            ProtracktorTheme(if (dark) AppTheme.DARK else AppTheme.LIGHT, dynamicColour = false) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxSize()) {
                            Header()
                            PlaylistScreen(state, rememberLazyListState(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {},
                                onBrowse = {}, onPickDownloads = {}, canBrowse = true, stateKnown = true,
                                contentPadding = PaddingValues(), modifier = Modifier.weight(1f))
                            when (dock) {
                                Dock.MINI -> MiniDock()
                                Dock.FULL -> Column {
                                    Handle()
                                    PlayerDock(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                                }
                            }
                            if (overlay == Overlay.SELECTING) SelectionBar() else NavBar(selected = 0)
                        }
                        if (overlay == Overlay.MORE) MoreSheet()
                    }
                }
            }
        }.saveAs(name)
    }

    /** One row: the playlist, its count, and Filter. */
    @Composable private fun Header() {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Playlista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("10 utworów", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(PlayerIcons.DropDown, null)
                }
            }
            LabelledAction(PlayerIcons.Filter, "Filtruj", {}, slim = true)
        }
    }

    /** The grip that says the dock moves: drag or tap. */
    @Composable private fun Handle() {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(top = 6.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
        }
    }

    /** A subtle bar with a thumb big enough to hold: 3 dp track, 14 dp thumb, 32 dp to touch. */
    @Composable private fun SubtleSeek(fraction: Float) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            val full = maxWidth
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.width(full * fraction).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.offset(x = full * fraction - 7.dp).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }

    /** Collapsed: the tune, a subtle seek bar, Play/Pause, Queue; + on the right edge when away. */
    @Composable private fun MiniDock() {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
            Column {
                Handle()
                SubtleSeek(64f / 200f)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Battle of honour", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Treks · PC · 1:04 / 3:20", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    FilledIconButton({}, Modifier.size(52.dp)) { Icon(PlayerIcons.Pause, null) }
                    LabelledAction(PlayerIcons.Playlist, "Kolejka", {}, slim = true)
                    LabelledAction(PlayerIcons.Add, "Dodaj", {}, slim = true)
                }
            }
        }
    }

    @Composable private fun NavBar(selected: Int) {
        val items = listOf(PlayerIcons.Playlist to "Playlista", PlayerIcons.Cloud to "Przeglądaj",
            PlayerIcons.Search to "Szukaj", PlayerIcons.Dice to "Losuj", PlayerIcons.More to "Więcej")
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, (icon, label) -> NavItem(icon, label, i == selected, Modifier.weight(1f)) }
            }
        }
    }

    @Composable private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.secondaryContainer
                else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp)) {
                Icon(icon, null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    /** Selecting rows: the bar takes the navigation's place, so the list keeps its room. */
    @Composable private fun SelectionBar() {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("3 zaznaczone", Modifier.padding(start = 8.dp, top = 2.dp), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabelledAction(PlayerIcons.Close, "Anuluj", {}, Modifier.weight(1f), slim = true)
                    LabelledAction(PlayerIcons.SelectAll, "Wszystkie", {}, Modifier.weight(1f), slim = true)
                    LabelledAction(PlayerIcons.PlaylistAdd, "Dodaj do", {}, Modifier.weight(1f), slim = true)
                    LabelledAction(PlayerIcons.Share, "Udostępnij", {}, Modifier.weight(1f), slim = true)
                }
            }
        }
    }

    /** More: the rare places, each with its icon and name. */
    @Composable private fun MoreSheet() {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).padding(bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                    Text("Więcej", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val rows = listOf(
                        listOf(PlayerIcons.History to "Historia", PlayerIcons.Alarm to "Budzik", PlayerIcons.Bookmark to "Zapisane"),
                        listOf(PlayerIcons.QrCode to "WEB", PlayerIcons.Settings to "Ustawienia", PlayerIcons.Forum to "Opinie"),
                    )
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (icon, label) -> LabelledAction(icon, label, {}, Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) { NavBar(selected = 4) }
        }
    }

    @Test fun `collapsed and expanded`() {
        picture("ui-1-collapsed-light", Dock.MINI)
        picture("ui-2-expanded-light", Dock.FULL)
        picture("ui-1-collapsed-dark", Dock.MINI, dark = true)
        picture("ui-2-expanded-dark", Dock.FULL, dark = true)
    }

    @Test fun `More and selecting`() {
        picture("ui-3-more-light", Dock.FULL, Overlay.MORE)
        picture("ui-4-selecting-light", Dock.MINI, Overlay.SELECTING)
    }
}
