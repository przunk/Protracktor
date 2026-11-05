// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R

@Composable internal fun AlarmMenuItem(onAdd: (() -> Unit)?, onDone: () -> Unit) {
    if (com.przunk.protracktor.alarms.api.MusicAlarms.enabled && onAdd != null) DropdownMenuItem(text = { Text(stringResource(R.string.alarm_use_music)) },
        leadingIcon = { Icon(PlayerIcons.Alarm, null) }, onClick = { onDone(); onAdd() })
}
