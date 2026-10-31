// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** New History rows are visible at the top; elsewhere LazyColumn retains the visible row's key. */
@Composable
internal fun KeepHistoryAtTop(listState: LazyListState, rowKeys: List<String>) {
    var previous by remember(listState) { mutableStateOf(rowKeys) }
    val pinTop = previous != rowKeys && listState.firstVisibleItemIndex == 0 &&
        listState.firstVisibleItemScrollOffset == 0 && !listState.isScrollInProgress
    SideEffect {
        // Stable keys would keep the old first row at the viewport edge and hide the new one.
        if (pinTop) listState.requestScrollToItem(0)
        previous = rowKeys
    }
}
