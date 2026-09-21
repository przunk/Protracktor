// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.data.LegalText
import com.przunk.protracktor.data.OpenSourceNotices

/**
 * The open-source licences, as the code brought them.
 *
 * Every component in `app/notices/components.tsv`, with its licence files copied into the APK by the
 * build -- the texts BSD, MIT and Apache ask to travel with the binary, and GPL's own (decided
 * 2026-09-21, release checklist §1). Shown in the place of the Settings list, with its padding.
 */
@Composable
fun LicencesScreen(contentPadding: PaddingValues, onClose: () -> Unit) {
    val context = LocalContext.current
    val components = remember {
        runCatching {
            context.assets.open(OpenSourceNotices.TABLE_ASSET).bufferedReader().use { it.readText() }
        }.map(OpenSourceNotices::parse).getOrDefault(emptyList())
    }
    var open by remember { mutableStateOf<OpenSourceNotices.Component?>(null) }

    // Back from a component's text returns to the list, and from the list to Settings.
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    Pane(
        title = open?.name ?: stringResource(R.string.settings_notices),
        contentPadding = contentPadding,
        onBack = { if (open != null) open = null else onClose() },
    ) { modifier, listPadding ->
        val shown = open
        if (shown == null) {
            LazyColumn(modifier, contentPadding = listPadding) {
                items(components, key = { it.id }) { component ->
                    ListItem(
                        headlineContent = { Text(component.name) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.notices_version_licence, component.version, component.licence),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = { Icon(PlayerIcons.Document, contentDescription = null) },
                        modifier = Modifier.clickable { open = component },
                    )
                }
            }
        } else {
            // Paragraph by paragraph rather than one Text: the GPL alone is 35 KB, and a lazy list
            // lays out only what is on screen.
            val paragraphs = remember(shown.id) {
                shown.files.flatMap { asset ->
                    val text = runCatching {
                        context.assets.open(asset).bufferedReader().use { it.readText() }
                    }.getOrDefault("")
                    listOf("## " + asset.substringAfterLast('/')) + text.split(Regex("\n\\s*\n"))
                }
            }
            LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = listPadding) {
                items(paragraphs) { paragraph ->
                    if (paragraph.startsWith("## ")) {
                        Text(
                            paragraph.removePrefix("## "),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    } else {
                        Text(
                            paragraph.trim('\n'),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The privacy policy, in the phone's language when that is Polish and in English otherwise.
 *
 * **The published file, not a copy of it** (`store/privacy-policy.md`, copied in by the build), so
 * what the app shows and what the store links to cannot say different things.
 */
@Composable
fun PrivacyPolicyScreen(contentPadding: PaddingValues, onClose: () -> Unit) {
    val context = LocalContext.current
    val polish = context.resources.configuration.locales[0].language == "pl"
    val blocks = remember(polish) {
        runCatching {
            context.assets.open(LegalText.PRIVACY_ASSET).bufferedReader().use { it.readText() }
        }.map { LegalText.privacyBlocks(it, polish) }.getOrDefault(emptyList())
    }
    Pane(title = stringResource(R.string.settings_privacy), contentPadding = contentPadding, onBack = onClose) { modifier, listPadding ->
        LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = listPadding) {
            items(blocks) { block ->
                when (block) {
                    is LegalText.Block.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    )
                    is LegalText.Block.Paragraph -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    is LegalText.Block.Bullet -> Text(
                        "•  " + block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * A legal page in the place of the Settings list: a header row with the way back, then the text.
 *
 * Takes Settings' own [contentPadding], which the app's Scaffold computes to clear the top bar,
 * the player dock and the system bars -- so the text ends where every other list here ends, with
 * [END_SPACE] more after its last line (C78).
 */
@Composable
private fun Pane(
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable (Modifier, PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(end = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(PlayerIcons.Back, stringResource(R.string.action_back))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
        content(
            Modifier.fillMaxSize(),
            PaddingValues(bottom = contentPadding.calculateBottomPadding() + END_SPACE),
        )
    }
}

/** Space after the last line of a legal text, so it does not end against the screen's edge. */
private val END_SPACE = 32.dp
