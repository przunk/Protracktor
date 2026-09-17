// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * How tall every action pill stands, one word or three.
 *
 * Tall enough for the icon, two lines of label and air above and below both, so a name that wraps
 * changes nothing about the row it is in.
 */
internal val ACTION_PILL_HEIGHT = 72.dp

/**
 * The same pill, for the top bar's row of actions.
 *
 * Shorter, and with a smaller icon: five of these stand across a phone with room to spare, which is
 * what the top bar needs and what a pill wide enough for an icon *beside* its label could not do.
 */
internal val ACTION_PILL_HEIGHT_SLIM = 46.dp

/** The icon in a slim pill. Material's default is 24dp, which leaves no room for the word under it. */
private val SLIM_ICON = 20.dp

/**
 * How narrow a slim pill may be, so a row of them reads as a row of equals.
 *
 * Width follows the label, and the labels are not the same length: "WEB" drew a button two thirds
 * the width of "Settings" beside it. This is the widest of them plus its padding, which makes the
 * short ones match rather than the long ones shrink — five at this width still cross a 320dp screen
 * with the seams between them.
 */
private val SLIM_MIN_WIDTH = 56.dp

/**
 * The gap a pill leaves on each side of itself, inside its own layout bounds.
 *
 * Named because it is invisible and it counts: anything lining a pill up with something in another
 * row has to know that the background starts this far in. It cancels out between the app bar and
 * the session heading, which both add it — see `TOP_BAR_ACTION_EDGE`.
 */
internal val ACTION_SEAM = 3.dp

/**
 * An action drawn as an icon with its name underneath.
 *
 * The shape every action in this app takes (`docs/BACKLOG.md` A16, A17, A23). An icon alone does
 * not say where it goes, and a word alone does not read as a control — the pair does both, and
 * several of them fit in one row where the same actions as full-width text buttons do not.
 *
 * Shared rather than repeated: a second copy inline in one screen's header would drift from this
 * one by the third caller.
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
     * The idiom the transport established: **press does the plain thing, hold does the qualified
     * one**. Both handlers are held stable, because a lambda rebuilt on every recomposition
     * restarts the gesture detector and a hold then fires repeatedly (`docs/STATUS.md` C17).
     */
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    /**
     * What the phone does when this is pressed, or nothing.
     *
     * **These are the app's function buttons**, and they answer firmly — Save and Discard among
     * them. So a press is the default and the exceptions declare themselves: the three that change
     * destination pass `null`, because the destination already buzzes on arrival and two buzzes
     * for one press reads as a stutter, not as emphasis.
     */
    haptic: (Haptics.() -> Unit)? = { press() },
    /**
     * Whether it can be pressed.
     *
     * Drawn dead rather than hidden, for the transport's reason (`PlayerDock`): a row that loses a
     * button when the state changes is a row whose other buttons move under the thumb.
     */
    enabled: Boolean = true,
    /** The top bar's row: shorter, with a smaller icon. Elsewhere the full-sized pill. */
    slim: Boolean = false,

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
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            // Mixed with the surface rather than made translucent, the same way the seek bar dims
            // its knob: a see-through container takes the colour of whatever is behind it.
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f)
                .compositeOver(MaterialTheme.colorScheme.surface)
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = modifier
            // A caller using weight still gets an equal grid, with a visible seam between buttons.
            .padding(horizontal = ACTION_SEAM)
            .defaultMinSize(minWidth = if (slim) SLIM_MIN_WIDTH else 48.dp)
            // **One height for every pill, whatever its name** (`docs/STATUS.md` C47), and it is a
            // plain number rather than anything that depends on the row around it. Two lines of
            // label forced on every pill push the words against the edges, and filling the row's
            // height turns the Random header into a window-tall banner, because that row is
            // offered the whole screen.
            .height(if (slim) ACTION_PILL_HEIGHT_SLIM else ACTION_PILL_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                enabled = enabled,
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
                tint = LocalContentColor.current,
                modifier = if (slim) Modifier.size(SLIM_ICON) else Modifier,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
