// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Installs downloaded replays without owning the engine, its configuration or Browse state. */
internal class ReplayDownloads(
    private val scope: CoroutineScope,
    private val backgroundWork: CoroutineDispatcher,
    private val native: Native,
    private val ui: Ui,
    private val text: DownloadText,
) {
    interface Native {
        suspend fun downloadSc68(onProgress: (Int, Int) -> Unit): Int?
        suspend fun downloadUade(onProgress: (Int, Int) -> Unit): Int?
        fun adoptSc68()
        fun configureUade()
    }

    interface Ui {
        fun begin(key: String, label: String): Boolean
        fun end(key: String)
        fun progress(key: String, label: String)
        fun say(message: Message)
        fun refreshCatalogues()
    }

    private companion object {
        const val REPLAYS_LABEL = "Atari ST replay routines"
        const val PLAYERS_LABEL = "Amiga replay routines"
    }

    /**
     * sc68's replay routines as one step: how many arrived, or null when none did. Progress shows
     * under its own key; the message is the caller's to give. `docs/LICENSES.md` has why they are
     * fetched rather than shipped.
     */
    private suspend fun fetchReplays(): Int? {
        if (!ui.begin(DownloadKeys.REPLAYS, REPLAYS_LABEL)) return null
        try {
            val fetched = native.downloadSc68 { done, total ->
                ui.progress(DownloadKeys.REPLAYS, "$REPLAYS_LABEL $done/$total")
            }
            if (fetched == null || fetched == 0) return null
            // Put them where sc68 will look, now rather than at the next update. It is handed a
            // path once and reads what is under it each time it opens a tune, so this is enough.
            withContext(backgroundWork) { native.adoptSc68() }
            return fetched
        } finally {
            ui.end(DownloadKeys.REPLAYS)
        }
    }

    /** UADE's replay routines as one step: how many arrived, or null when none did. */
    private suspend fun fetchPlayers(): Int? {
        if (!ui.begin(DownloadKeys.PLAYERS, PLAYERS_LABEL)) return null
        try {
            val fetched = native.downloadUade { done, total ->
                ui.progress(DownloadKeys.PLAYERS, "$PLAYERS_LABEL $done/$total")
            }
            if (fetched == null || fetched == 0) return null
            withContext(backgroundWork) { native.configureUade() }
            return fetched
        } finally {
            ui.end(DownloadKeys.PLAYERS)
        }
    }

    /**
     * Both sets of replay routines, sc68's and UADE's, as one press with one message.
     *
     * **One button, because nobody should need to know which emulator a format runs on**
     * (decided 2026-09-21). The note beside it still says what these are -- other people's code,
     * fetched from the projects that publish it (`docs/LICENSES.md`).
     */
    fun download() {
        if (!ui.begin(DownloadKeys.REPLAY_ROUTINES, text.label(DownloadText.Label.REPLAY_ROUTINES))) return
        scope.launch {
            try {
                val sc68 = fetchReplays()
                val uade = fetchPlayers()
                ui.say(text.message(DownloadNotice.ReplaysDownloaded(sc68, uade)))
            } finally {
                withContext(NonCancellable) {
                    ui.end(DownloadKeys.REPLAY_ROUTINES)
                    ui.refreshCatalogues()
                }
            }
        }
    }
}
