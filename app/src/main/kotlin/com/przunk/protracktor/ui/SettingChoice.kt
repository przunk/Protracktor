// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A short list of mutually exclusive answers, all of them visible.
 *
 * **A segmented button rather than a dialog**, which is what these settings were. A dialog for
 * three options costs a tap to find out what the options even are, and then hides the answer again
 * behind a summary line. Here the choice and the current answer are the same thing, and changing it
 * is one tap from anywhere on the screen.
 *
 * It is deliberately one component used twice. Language and theme are the same question — pick one
 * of a handful — and two controls that behave identically should be one piece of code, or they
 * drift.
 *
 * **Only for two to four options that fit on a phone.** Anything longer wants a list, and anything
 * whose labels do not fit will ellipsise them into nonsense.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingChoice(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val haptics = rememberHaptics()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    // **Only when it is a different answer.** Tapping the segment that is already
                    // lit changes nothing, and a buzz for it would be the phone agreeing with
                    // itself.
                    onClick = {
                        if (option != selected) haptics.toggle(on = true)
                        onSelect(option)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(
                        text = labelOf(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
