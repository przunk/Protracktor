// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.StereoMix

@Composable
internal fun StereoMixSetting(selected: StereoMix, onSelect: (StereoMix) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_stereo_mix), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.settings_stereo_mix_detail), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup()) {
            StereoMix.entries.forEach { mode ->
                LabelledAction(
                    icon = when (mode) {
                        StereoMix.ORIGINAL -> PlayerIcons.StereoOriginal
                        StereoMix.NARROW -> PlayerIcons.StereoNarrow
                        StereoMix.MONO -> PlayerIcons.StereoMono
                    },
                    label = mode.label(),
                    onClick = { onSelect(mode) },
                    haptic = if (mode == selected) null else { { toggle(on = true) } },
                    modifier = Modifier.weight(1f).semantics { this.selected = mode == selected }
                        .then(if (mode == selected) Modifier.border(2.dp,
                            MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.shapes.medium)
                            else Modifier),
                )
            }
        }
        Text(stringResource(R.string.settings_stereo_mix_selected, selected.label()),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StereoMix.label(): String = stringResource(when (this) {
    StereoMix.ORIGINAL -> R.string.settings_stereo_mix_original
    StereoMix.NARROW -> R.string.settings_stereo_mix_narrow
    StereoMix.MONO -> R.string.settings_stereo_mix_mono
})
