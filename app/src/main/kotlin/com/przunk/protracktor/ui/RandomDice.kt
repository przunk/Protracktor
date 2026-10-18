// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import kotlinx.coroutines.launch

/** Touch requires a double tap; accessibility activation performs the equivalent single action. */
@Composable
internal fun RandomDice(onReroll: () -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onReroll)
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.random_reroll_list)
    val roll = {
        latest()
        scope.launch { rotation.snapTo(0f); rotation.animateTo(360f, tween(500)) }
        Unit
    }
    Box(
        modifier.size(56.dp)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { roll() }) }
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) { roll(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PlayerIcons.Dice, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = rotation.value })
    }
}
