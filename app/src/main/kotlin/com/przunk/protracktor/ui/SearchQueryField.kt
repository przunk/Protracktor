// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var allowExpanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val suggestions = remember(recentQueries, query) { RecentSearches.suggestions(recentQueries, query) }
    fun submit(action: () -> Unit) {
        allowExpanded = false
        keyboard?.hide()
        focus.clearFocus()
        action()
    }
    // Entering Search focuses the field once; reading the returned results must not reopen it.
    LaunchedEffect(Unit) {
        field.requestFocus()
        keyboard?.show()
    }
    val expanded = focused && allowExpanded && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { allowExpanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { allowExpanded = true; onQueryChanged(it) },
            singleLine = true,
            label = { Text(scopeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(onSearch) }),
            trailingIcon = {
                IconButton(onClick = { submit(onSearch) }) {
                    Icon(PlayerIcons.Search, stringResource(R.string.domain_search_title))
                }
            },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .focusRequester(field).onFocusChanged {
                    focused = it.isFocused
                    if (focused) allowExpanded = true
                },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { allowExpanded = false }) {
            suggestions.forEach { suggestion -> key(suggestion) {
                DropdownMenuItem(
                    text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { submit { onSuggestionChosen(suggestion) } },
                )
            } }
        }
    }
}
