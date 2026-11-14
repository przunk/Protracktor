// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import kotlinx.coroutines.launch

/** One action in a [ScrollingActionRow]: its short label shown, its full name said to TalkBack. */
internal class RowAction(
    val shortLabel: String,
    val fullLabel: String,
    val content: @Composable (label: String, said: String, modifier: Modifier) -> Unit,
)

/**
 * **One row of small actions, all the same width, that scrolls when they do not fit** (Now Playing,
 * 2026-11-14). Two rows of full-size buttons took about 156 dp of a sheet that is read for its
 * metadata. Each is a slim pill, icon above a short label; the width is the longest label's, so the
 * row reads as one set in either language.
 *
 * When they do not fit, the edge they continue past fades out and carries an arrow that moves the
 * row that way -- an arrow that did nothing when pressed would be a control that lies. When they
 * fit, there is neither, and they stand side by side.
 */
@Composable
internal fun ScrollingActionRow(
    actions: List<RowAction>,
    modifier: Modifier = Modifier,
    /** What the row stands on: a cut edge fades into it. */
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    if (actions.isEmpty()) return
    val width = slimActionWidth(*actions.map { it.shortLabel }.toTypedArray())
    BoxWithConstraints(modifier.fillMaxWidth().testTag("action-row")) {
        val needed = width * actions.size
        if (needed <= maxWidth) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                actions.forEach { it.content(it.shortLabel, it.fullLabel, Modifier.width(width)) }
            }
            return@BoxWithConstraints
        }
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val step = with(LocalDensity.current) { (width * 2).toPx() }
        val before by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, label = "fade-before")
        val after by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "fade-after")
        val fade = with(LocalDensity.current) { EDGE_FADE.toPx() }
        Row(
            Modifier
                .fillMaxWidth()
                // Before the scroll, so it covers the window onto the row, not the row: the edge it
                // continues past fades into what the row stands on, and what is cut reads as more.
                // Drawn over in the background's colour rather than masked: a layer mask came out
                // as a pale block where the row was cut.
                .drawWithContent {
                    drawContent()
                    val reach = fade.coerceAtMost(size.width / 2f)
                    if (before > 0f) drawRect(
                        Brush.horizontalGradient(0f to background.copy(alpha = before), 1f to Color.Transparent,
                            startX = 0f, endX = reach),
                        size = size.copy(width = reach),
                    )
                    if (after > 0f) drawRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 1f to background.copy(alpha = after),
                            startX = size.width - reach, endX = size.width),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width - reach, 0f),
                        size = size.copy(width = reach),
                    )
                }
                .horizontalScroll(scroll)
                .testTag("action-row-scroll"),
        ) {
            actions.forEach { it.content(it.shortLabel, it.fullLabel, Modifier.width(width)) }
        }
        if (scroll.canScrollBackward) EdgeArrow(PlayerIcons.ChevronLeft, stringResource(R.string.a11y_scroll_back),
            Modifier.align(Alignment.CenterStart).testTag("action-row-back")) {
            scope.launch { scroll.animateScrollBy(-step) }
        }
        if (scroll.canScrollForward) EdgeArrow(PlayerIcons.ChevronRight, stringResource(R.string.a11y_scroll_forward),
            Modifier.align(Alignment.CenterEnd).testTag("action-row-forward")) {
            scope.launch { scroll.animateScrollBy(step) }
        }
    }
}

@Composable
private fun EdgeArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, said: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
        modifier = modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, said, Modifier.size(20.dp)) }
    }
}

/** How far into the row the fade at a cut edge reaches. */
private val EDGE_FADE: Dp = 40.dp
