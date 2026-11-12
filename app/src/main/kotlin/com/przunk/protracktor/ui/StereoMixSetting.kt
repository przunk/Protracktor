// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R
import com.przunk.protracktor.player.StereoMix

/**
 * The stereo mix as one three-part pill, like the language and the theme (B65, 2026-11-12): the
 * three answers and the current one in a single control, each with its icon.
 */
@Composable
internal fun StereoMixSetting(selected: StereoMix, onSelect: (StereoMix) -> Unit) {
    SettingChoice(
        label = stringResource(R.string.settings_stereo_mix),
        options = StereoMix.entries,
        selected = selected,
        labelOf = { it.label() },
        onSelect = onSelect,
        supporting = stringResource(R.string.settings_stereo_mix_detail),
        iconOf = { mode ->
            when (mode) {
                StereoMix.ORIGINAL -> PlayerIcons.StereoOriginal
                StereoMix.NARROW -> PlayerIcons.StereoNarrow
                StereoMix.MONO -> PlayerIcons.StereoMono
            }
        },
    )
}

@Composable
private fun StereoMix.label(): String = stringResource(when (this) {
    StereoMix.ORIGINAL -> R.string.settings_stereo_mix_original
    StereoMix.NARROW -> R.string.settings_stereo_mix_narrow
    StereoMix.MONO -> R.string.settings_stereo_mix_mono
})
