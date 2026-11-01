// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R

/** Both states occupy the existing chooser row; the list never moves down to make room for search. */
@Composable
internal fun PlaylistSearchHeader(
    name: String,
    count: Int,
    searchOpen: Boolean,
    query: String,
    onChoosePlaylist: () -> Unit,
    onSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onBounds: (Rect) -> Unit,
) {
    val type = MaterialTheme.typography
    val headerHeight = with(LocalDensity.current) {
        maxOf(56.dp,
            type.titleMedium.lineHeight.toDp() + (if (count > 0) type.labelSmall.lineHeight.toDp() else 0.dp) + 16.dp,
            type.bodyLarge.lineHeight.toDp() + 32.dp)
    }
    AnimatedContent(
        targetState = searchOpen,
        modifier = Modifier.fillMaxWidth().testTag("playlist-search-header"),
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { direction * it / 10 }) togetherWith
                (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -direction * it / 10 })
        },
        label = "playlist-search",
    ) { searching ->
        if (searching) {
            val focus = remember { FocusRequester() }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.playlist_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                leadingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(PlayerIcons.Back, stringResource(R.string.playlist_search_close))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(PlayerIcons.Close, stringResource(R.string.playlist_search_clear))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = headerHeight)
                    .testTag("playlist-search-field")
                    .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                    .focusRequester(focus).onFocusChanged { onFocus(it.isFocused) },
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onChoosePlaylist,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = headerHeight)
                    .testTag("playlist-chooser"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (count > 0) Text(pluralStringResource(R.plurals.track_count, count, count),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Icon(PlayerIcons.DropDown, stringResource(R.string.a11y_choose_playlist))
                }
            }
            Spacer(Modifier.width(4.dp))
            LabelledAction(PlayerIcons.Search, stringResource(R.string.playlist_search_action), onSearch,
                slim = true, modifier = Modifier.width(slimActionWidth(stringResource(R.string.playlist_search_action))))
        }
    }
}

/** Consume the first press on dimmed chrome. The list and the field keep their normal hit targets. */
@Composable
internal fun PlaylistSearchShield(editing: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box {
        content()
        if (editing) Box(Modifier.matchParentSize().testTag("playlist-search-shield")
            .clickable(onClick = onDismiss).clearAndSetSemantics { })
    }
}

/** The field and results stay bright; the drawing-only scrim never intercepts row actions. */
@Composable
internal fun PlaylistSearchDimming(editing: Boolean, field: Rect?, results: Rect?) {
    val alpha by animateFloatAsState(if (editing) 0.42f else 0f, tween(180), label = "playlist-search-dim")
    if (alpha <= 0f || field == null) return
    Canvas(Modifier.fillMaxSize().testTag("playlist-search-dimming")) {
        withTransform({
            clipRect(field.left, field.top, field.right, field.bottom, ClipOp.Difference)
            results?.let { clipRect(it.left, it.top, it.right, it.bottom, ClipOp.Difference) }
        }) {
            drawRect(Color.Black.copy(alpha = alpha))
        }
    }
}
