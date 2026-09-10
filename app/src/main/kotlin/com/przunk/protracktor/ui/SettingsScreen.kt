// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.AppLanguage
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.engine.NativeEngine

/**
 * The things you set once and stop thinking about.
 *
 * **A full-screen modal, exactly like Browse** (`docs/OPEN_QUESTIONS.md` Q1). Same shape, same way
 * out, same rule for Back — so it adds a screen without adding a navigation concept. It is reached
 * by a gear in the top bar rather than from an overflow menu, because a menu holding one item is
 * worse than the button it hides.
 *
 * **One scrolling screen with sections, not a tree.** With this little in it a tree would be
 * ceremony, and every extra tap is a tap between somebody and the thing they came to change.
 *
 * **Nothing here is invented.** Every switch is a setting the app already had somewhere less
 * findable, and the storage section is lifted whole from Browse, where it lived as a stated
 * placeholder waiting for this screen (`docs/ARCHITECTURE.md` §19).
 */
@Composable
fun SettingsScreen(
    playAllSubsongs: Boolean,
    selectedLanguage: AppLanguage,
    cacheBytes: Long,
    archiveBytes: Map<String, Long>,
    databaseBytes: Long,
    replayCount: Int,
    replayBytes: Long,
    catalogues: List<CatalogueSummary>,
    webPlayer: String,
    onWebPlayerChanged: (String) -> Unit,
    songLengthCount: Int,
    trackMetadataCount: Int,
    favouriteCount: Int,
    contentPadding: PaddingValues,
    selectedTheme: AppTheme,
    dynamicColour: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onDynamicColourChanged: (Boolean) -> Unit,
    onToggleAllSubsongs: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onClearCache: () -> Unit,
    onDeleteIndex: (String) -> Unit,
    onClearSongLengths: () -> Unit,
    onClearTrackMetadata: () -> Unit,
    onClearFavourites: () -> Unit,
    onDeleteReplays: () -> Unit,
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    // Asked of the package manager rather than of `BuildConfig`, which this build does not
    // generate — and which would report what was compiled rather than what is installed.
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
            "${info.versionName} ($code)"
        }.getOrDefault("unknown")
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { Section(R.string.settings_playback) }

        // Global already, and until now reachable only from inside Now Playing on a file that
        // happens to have more than one tune -- so a setting that governs every file could only be
        // found through one of them.
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_play_all_subsongs)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_play_all_subsongs_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = playAllSubsongs,
                        onCheckedChange = { on -> haptics.toggle(on); onToggleAllSubsongs() },
                    )
                },
            )
        }

        // Two settings, one shape. Both are "pick one of a handful", both show every option and
        // the current answer at once, and both used to be a dialog -- which cost a tap to find out
        // what the options were and then hid the answer again behind a summary line.
        item {
            SettingChoice(
                label = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = selectedLanguage,
                labelOf = { it.label() },
                onSelect = onLanguageSelected,
            )
        }

        item { HorizontalDivider(); Section(R.string.settings_appearance) }

        item {
            SettingChoice(
                label = stringResource(R.string.settings_theme),
                options = AppTheme.entries,
                selected = selectedTheme,
                labelOf = { it.label() },
                onSelect = onThemeSelected,
            )
        }

        // Absent below Android 12 rather than present and dead: there is no wallpaper palette to
        // take, so a switch would be a promise the platform cannot keep.
        if (Appearance.supportsDynamicColour) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_colour)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_dynamic_colour_detail),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = dynamicColour,
                            onCheckedChange = { on -> haptics.toggle(on); onDynamicColourChanged(on) },
                        )
                    },
                )
            }
        }

        // **Its own group, below the palette**, on the owner's instruction (2026-09-09). It had
        // been sitting under Playback between the language picker and the appearance heading,
        // which is where it least belongs: it is not a preference about how music sounds, it is
        // the address of a second copy of the app.
        item { HorizontalDivider(); Section(R.string.settings_web_player_section) }

        // Text rather than a choice, because the answer is an address and there is no list of them.
        // It changes as the page moves -- a local server today, a tunnel next, a host eventually --
        // which is exactly why it is stored rather than compiled in (`docs/PLAN_HANDOFF.md` §3).
        item {
            // Keyed on the stored value, so an address that arrives from a pairing shows up here
            // instead of the field sitting on a stale copy of what it was built with.
            var editing by remember(webPlayer) { mutableStateOf(webPlayer) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_web_player)) },
                supportingContent = {
                    Column {
                        Text(
                            stringResource(R.string.settings_web_player_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = editing,
                            // **Committed on every keystroke**, and the first version was not: it
                            // saved only on the keyboard's Done, so typing an address and tapping
                            // away lost it silently — which is exactly what the owner met. A
                            // half-typed address costs nothing, because it is read when something
                            // is sent and not before.
                            onValueChange = { editing = it; onWebPlayerChanged(it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                },
            )
        }

        item {
            StorageSection(
                cacheBytes = cacheBytes,
                archiveBytes = archiveBytes,
                databaseBytes = databaseBytes,
                replayCount = replayCount,
                replayBytes = replayBytes,
                catalogues = catalogues,
                songLengthCount = songLengthCount,
                trackMetadataCount = trackMetadataCount,
                favouriteCount = favouriteCount,
                onClearCache = onClearCache,
                onDeleteIndex = onDeleteIndex,
                onClearSongLengths = onClearSongLengths,
                onClearTrackMetadata = onClearTrackMetadata,
                onClearFavourites = onClearFavourites,
                onDeleteReplays = onDeleteReplays,
            )
        }

        item { HorizontalDivider(); Section(R.string.settings_about) }

        // What is actually inside this build, said by the build rather than by a person. The
        // version name and the commit count come from Gradle; the decoder list comes from the
        // engine's own fingerprint, which is the same string an index records to know it is stale.
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = {
                    Text(
                        version,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_decoders)) },
                supportingContent = {
                    Text(
                        NativeEngine.backendsFingerprint().substringBefore(";names:")
                            .replace(";", "\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licence)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_licence_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
    }
}

@Composable
private fun AppTheme.label(): String = stringResource(
    when (this) {
        AppTheme.SYSTEM -> R.string.settings_theme_system
        AppTheme.LIGHT -> R.string.settings_theme_light
        AppTheme.DARK -> R.string.settings_theme_dark
    }
)

@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.POLISH -> R.string.settings_language_polish
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }
)

@Composable
private fun Section(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
