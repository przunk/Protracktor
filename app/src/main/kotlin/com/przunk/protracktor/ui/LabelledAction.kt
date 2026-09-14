// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * How tall every action pill stands, one word or three.
 *
 * Tall enough for the icon, two lines of label and air above and below both, so a name that wraps
 * changes nothing about the row it is in.
 */
internal val ACTION_PILL_HEIGHT = 62.dp

/**
 * An action drawn as an icon with its name underneath.
 *
 * The owner's shape, asked for twice: once for the way out of Browse (`docs/BACKLOG.md` A16) and
 * again for the actions on a track and the way in (`A17`, `A23`). An icon alone does not say where
 * it goes, and a word alone does not read as a control — the pair does both, and several of them
 * fit in one row where the same actions as full-width text buttons did not.
 *
 * Shared rather than repeated, because the version that existed was inline in the Browse header and
 * a second copy would have drifted from it by the third caller.
 *
 * **Width comes from the caller.** A row of these should be a grid of equals, and the way to get
 * that is `Modifier.weight(1f)` so they share the space that is actually there. A fixed width was
 * tried and was worse than ragged: at 84dp five of them are 420dp, a phone gives about 310, and the
 * last two wrapped to a second line — which reads as actions having gone missing rather than as a
 * grid. In a top bar, where there is no row to share, no width is right.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LabelledAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The qualified version of the same action, if there is one.
     *
     * The idiom the transport established and the owner kept: **press does the plain thing, hold
     * does the qualified one**. Both handlers are held stable, because a lambda rebuilt on every
     * recomposition restarts the gesture detector and a hold then fires repeatedly — the defect in
     * `docs/STATUS.md` C17, which cost an evening and is not worth meeting twice.
     */
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    /**
     * What the phone does when this is pressed, or nothing.
     *
     * **These are the app's function buttons**, and the owner asked for a firm answer from them —
     * Save and Discard by name (2026-09-10). So a press is the default and the exceptions declare
     * themselves: the three that change destination pass `null`, because the destination already
     * buzzes on arrival and two buzzes for one press reads as a stutter, not as emphasis.
     */
    haptic: (Haptics.() -> Unit)? = { press() },
) {
    val haptics = rememberHaptics()
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    // Held stable like the callbacks around it, and for the same reason (`docs/STATUS.md` C17): a
    // lambda rebuilt each recomposition restarts the gesture detector, and a hold in progress then
    // fires again and again.
    val currentHaptic by rememberUpdatedState(haptic)
    val click = remember { { currentHaptic?.invoke(haptics); currentClick() } }
    val hasLongClick = onLongClick != null
    // A long press with no answer feels like a press that missed — the reason `docs/BACKLOG.md` A8
    // put haptics on long presses in the first place.
    val longClick = remember { { haptics.gestureEnd(); currentLongClick?.invoke(); Unit } }

    // **`combinedClickable` on a plain Surface**, rather than the clickable Surface overload, which
    // takes an `onClick` and nothing else. The shape has to be clipped explicitly then, or the
    // ripple is a rectangle behind a rounded button.
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            // A caller using weight still gets an equal grid, with a visible seam between buttons.
            .padding(horizontal = 3.dp)
            .defaultMinSize(minWidth = 48.dp)
            // **One height for every pill, whatever its name** (`docs/STATUS.md` C47). Sizing to
            // the label made a one-word action shorter than a two-word one; two lines of label
            // forced on every pill fixed the heights and pushed the words to the edges instead.
            .height(ACTION_PILL_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = label,
                onLongClickLabel = longClickLabel,
                onLongClick = if (hasLongClick) longClick else null,
                onClick = click,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            // The same quieter pair as the follow-track navigation button. Using the named
            // content colour with its container keeps contrast intact for dynamic colour schemes.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
