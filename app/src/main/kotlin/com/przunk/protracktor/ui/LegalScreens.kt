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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.przunk.protracktor.R
import com.przunk.protracktor.data.LegalText
import com.przunk.protracktor.data.OpenSourceNotices

/**
 * The open-source licences, as the code brought them.
 *
 * Every component in `app/notices/components.tsv`, with its licence files copied into the APK by the
 * build -- the texts BSD, MIT and Apache ask to travel with the binary, and GPL's own (decided
 * 2026-09-21, release checklist §1). A full-screen window over Settings, so Back and the arrow both
 * close it and nothing in the app's own navigation has to know it exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val components = remember {
        runCatching {
            context.assets.open(OpenSourceNotices.TABLE_ASSET).bufferedReader().use { it.readText() }
        }.map(OpenSourceNotices::parse).getOrDefault(emptyList())
    }
    var open by remember { mutableStateOf<OpenSourceNotices.Component?>(null) }

    FullScreen(
        title = open?.name ?: stringResource(R.string.settings_notices),
        onBack = { if (open != null) open = null else onClose() },
    ) { modifier ->
        val shown = open
        if (shown == null) {
            LazyColumn(modifier, contentPadding = PaddingValues(bottom = END_SPACE)) {
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
            LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = END_SPACE)) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val polish = context.resources.configuration.locales[0].language == "pl"
    val blocks = remember(polish) {
        runCatching {
            context.assets.open(LegalText.PRIVACY_ASSET).bufferedReader().use { it.readText() }
        }.map { LegalText.privacyBlocks(it, polish) }.getOrDefault(emptyList())
    }
    FullScreen(title = stringResource(R.string.settings_privacy), onBack = onClose) { modifier ->
        LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = END_SPACE)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreen(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    // **Edge to edge, with the insets left to the Scaffold** (`docs/STATUS.md` C78). With the
    // dialog's own window fitting the system bars, the content was measured to the full screen and
    // its last lines lay under the navigation bar -- the privacy policy stopped at the first line of
    // "Changes" and would not scroll further. `decorFitsSystemWindows = false` hands the insets to
    // the Scaffold, which pads the top bar below the status bar and the content above the
    // navigation bar, as every other screen here does.
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(PlayerIcons.Back, stringResource(R.string.action_back))
                        }
                    },
                    title = { Text(title) },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                HorizontalDivider()
                content(Modifier.fillMaxSize())
            }
        }
    }
}

/** Space after the last line of a legal text, so it does not end against the screen's edge. */
private val END_SPACE = 32.dp
