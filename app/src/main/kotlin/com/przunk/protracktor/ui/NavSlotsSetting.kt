// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/**
 * The navigation bar's two chosen places, in Settings (redesign round 2, 2026-11-11).
 *
 * **The bar itself first**, as it will look, with the two places that can change outlined and
 * marked *Change*: what the choice does is seen before anything is chosen. Tapping one lists every
 * place it can hold, icon and name, the current one ticked; the one already in the other place says
 * so, and choosing it swaps the two. *Restore defaults*, small in the part's title row, puts Search
 * and Random back (2026-11-12: under the preview and full width it was a second, heavier heading).
 */
@Composable
fun NavSlotsSetting(slots: NavSlots, alarms: Boolean, onChange: (NavSlots) -> Unit) {
    var choosing by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_nav_bar), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            LabelledAction(PlayerIcons.Refresh, stringResource(R.string.settings_nav_restore), { onChange(NavSlots.DEFAULT) },
                Modifier.testTag("nav-restore"), enabled = slots != NavSlots.DEFAULT, slim = true)
        }
        Text(stringResource(R.string.settings_nav_bar_detail), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().height(76.dp).padding(4.dp).testTag("nav-preview"),
                verticalAlignment = Alignment.CenterVertically) {
                slots.bar.forEachIndexed { index, tab ->
                    val slot = (index - 2).takeIf { it == 0 || it == 1 }
                    PreviewItem(tab, slot, Modifier.weight(1f)) { if (slot != null) choosing = slot }
                }
            }
        }
    }
    choosing?.let { slot ->
        NavSlotChooser(slot, slots, NavSlots.available(alarms),
            onChoose = { place -> choosing = null; onChange(slots.choose(slot, place)) },
            onDismiss = { choosing = null })
    }
}

@Composable
private fun PreviewItem(tab: ShellTab, slot: Int?, modifier: Modifier, onClick: () -> Unit) {
    val (icon, label) = tabLook(tab)
    val change = stringResource(R.string.settings_nav_slot_change)
    val chosen = slot != null
    val description = slot?.let { stringResource(R.string.settings_nav_slot, it + 1, label) }
    Column(
        modifier
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (chosen) Modifier
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = change, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description.orEmpty() }
                .testTag("nav-slot-${slot!! + 1}")
            else Modifier.clearAndSetSemantics { }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        // Said in words as well as by the outline, so the mark is not colour or shape alone.
        if (chosen) Text(change, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
private fun NavSlotChooser(slot: Int, slots: NavSlots, available: List<ShellTab>, onChoose: (ShellTab) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_nav_choose_title, slot + 1)) },
        text = {
            Column(Modifier.testTag("nav-chooser")) {
                available.forEach { place ->
                    val (icon, label) = tabLook(place)
                    val current = place == slots.at(slot)
                    val inOther = place == slots.at(1 - slot)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.RadioButton) { if (current) onDismiss() else onChoose(place) }
                            .semantics { selected = current }
                            .testTag("nav-choice-${place.name.lowercase()}")
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(icon, null, Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            if (inOther) Text(stringResource(R.string.settings_nav_in_other), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current) Icon(PlayerIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
