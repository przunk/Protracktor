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
package com.przunk.protracktor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    cacheBytes: Long,
    archiveBytes: Map<String, Long>,
    databaseBytes: Long,
    replayCount: Int,
    replayBytes: Long,
    catalogues: List<CatalogueSummary>,
    songLengthCount: Int,
    contentPadding: PaddingValues,
    onToggleAllSubsongs: () -> Unit,
    onClearCache: () -> Unit,
    onDeleteIndex: (String) -> Unit,
    onClearSongLengths: () -> Unit,
    onDeleteReplays: () -> Unit,
) {
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
                    Switch(checked = playAllSubsongs, onCheckedChange = { onToggleAllSubsongs() })
                },
            )
        }

        // Android owns per-app language from 13 onwards and does it better than we would: it is in
        // the same place for every app, and the system remembers it across reinstalls. Below 13
        // there is no such screen, so there is nothing honest to offer and the row is absent
        // rather than present and dead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_language_detail),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null))
                            )
                        }
                    },
                )
            }
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
                onClearCache = onClearCache,
                onDeleteIndex = onDeleteIndex,
                onClearSongLengths = onClearSongLengths,
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
