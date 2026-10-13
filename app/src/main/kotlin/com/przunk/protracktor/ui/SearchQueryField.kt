// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.RecentSearches

@Composable
internal fun SearchQueryField(
    query: String,
    scopeLabel: String,
    recentQueries: List<String>,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionChosen: (String) -> Unit,
) {
    val field = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val suggestions = remember(recentQueries, query) { RecentSearches.suggestions(recentQueries, query) }
    fun submit(action: () -> Unit) {
        keyboard?.hide()
        focus.clearFocus()
        action()
    }
    // Entering Search focuses the field once; reading the returned results must not reopen it.
    LaunchedEffect(Unit) {
        field.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query, onValueChange = onQueryChanged, singleLine = true,
            label = { Text(scopeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(onSearch) }),
            trailingIcon = { LabelledAction(PlayerIcons.Search, stringResource(R.string.domain_search_title),
                onClick = { submit(onSearch) }, slim = true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(field).onFocusChanged { focused = it.isFocused },
        )
        if (focused && suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.search_recent_queries), style = MaterialTheme.typography.bodySmall)
                suggestions.forEach { suggestion -> key(suggestion) {
                    LabelledAction(PlayerIcons.History, suggestion,
                        onClick = { submit { onSuggestionChosen(suggestion) } },
                        modifier = Modifier.fillMaxWidth(), slim = true)
                } }
            }
        }
    }
}
