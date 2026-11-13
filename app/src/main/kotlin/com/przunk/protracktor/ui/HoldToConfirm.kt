// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.przunk.protracktor.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a hold takes, and what a fraction of it means. Pure, so the JVM holds the rule. */
internal object HoldRule {
    const val HOLD_MILLIS = 3_000L

    /** How far a hold begun [elapsedMillis] ago has come, 0 to 1. */
    fun fraction(elapsedMillis: Long, holdMillis: Long = HOLD_MILLIS): Float =
        (elapsedMillis.toFloat() / holdMillis).coerceIn(0f, 1f)
}

/**
 * **An action that cannot be undone, done by holding** (2026-11-12): clearing History first, and the
 * rule for every such action. A tap does nothing but say how; the press has to be held for three
 * seconds while the pill fills from the left, with light ticks on the way and a firm one at the
 * end; letting go before that cancels and the fill runs back. A dialog is a second tap people learn
 * to press blindly; a hold is deliberate by its nature.
 *
 * The time is measured from frame times, not by an animation, so a phone with animations turned off
 * still waits the three seconds rather than finishing at once.
 *
 * TalkBack cannot hold: for it the same action is a click that asks first, in a dialog -- or, inside
 * a dialog that has already asked, a click that confirms.
 *
 * In a confirmation dialog it is the confirm button (deleting a playlist, a catalogue's index, song
 * data, replay routines): the dialog says what will go, the hold makes the yes deliberate.
 */
@Composable
internal fun HoldToConfirm(
    icon: ImageVector,
    label: String,
    onConfirm: () -> Unit,
    confirmTitle: String = "",
    confirmText: String = "",
    modifier: Modifier = Modifier,
    /**
     * Whether TalkBack's click asks first, in a dialog of its own. Not where the button already
     * stands in a dialog that has said what will go: there the click confirms (2026-11-12).
     */
    askFirst: Boolean = true,
    enabled: Boolean = true,
    slim: Boolean = false,
    holdMillis: Long = HoldRule.HOLD_MILLIS,
) {
    val fill = remember { mutableFloatStateOf(0f) }
    var hinting by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val confirm by rememberUpdatedState(onConfirm)
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val hint = stringResource(R.string.hold_to_confirm_hint)
    val askLabel = stringResource(R.string.hold_to_confirm_ask, label)

    // The hint stays a moment, then the name comes back.
    LaunchedEffect(hinting) { if (hinting) { delay(HINT_MILLIS); hinting = false } }

    // One node with its label, as a clickable pill is, so TalkBack reads the name with the button.
    val gesture = Modifier
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (!enabled) disabled()
            else if (askFirst) onClick(label = askLabel) { asking = true; true }
            else onClick(label = label) { confirm(); true }
        }
        .pointerInput(enabled, holdMillis) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                // The name back at once, not two seconds into the next hold: a label changing
                // under the finger resized the pill, and inside a dialog the button row was laid
                // out anew and the hold was lost.
                hinting = false
                haptics.grab()
                val holding = scope.launch {
                    var start = -1L
                    var quarter = 0
                    while (true) {
                        val now = withFrameNanos { it }
                        if (start < 0) start = now
                        val f = HoldRule.fraction((now - start) / 1_000_000, holdMillis)
                        fill.floatValue = f
                        if (f >= 1f) break
                        val q = (f * 4).toInt()
                        if (q > quarter) { quarter = q; haptics.scrub() }
                    }
                    haptics.gestureEnd()
                    confirm()
                }
                tryAwaitRelease()
                if (holding.isActive) {
                    holding.cancel()
                    hinting = true
                    val from = fill.floatValue
                    scope.launch { animate(from, 0f, animationSpec = tween(RUN_BACK_MILLIS)) { v, _ -> fill.floatValue = v } }
                } else {
                    fill.floatValue = 0f
                }
            })
        }

    LabelledAction(
        icon = icon,
        label = if (hinting) hint else label,
        onClick = {},
        // One width for both words, so the hint does not resize the pill or move its neighbours.
        modifier = if (slim) Modifier.width(slimActionWidth(label, hint)).then(modifier) else modifier,
        haptic = null,
        enabled = enabled,
        slim = slim,
        holdFill = { fill.floatValue },
        holdGesture = gesture,
    )

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmText) },
            confirmButton = { TextButton(onClick = { asking = false; confirm() }) { Text(label) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private const val HINT_MILLIS = 2_000L
private const val RUN_BACK_MILLIS = 250
