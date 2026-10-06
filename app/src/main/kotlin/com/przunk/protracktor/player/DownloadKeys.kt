// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * What the Browse screen is looking at.
 *
 * Separate from [PlayerUiState] because it is a different lifetime: browsing comes and goes while
 * playback does not, and folding it in would mean every scan tick recomposing the player.
 */
/**
 * Keys for the downloads that are not a catalogue.
 *
 * A catalogue's key is its own id. These four have none, and a display label is the wrong thing to
 * key on: it is translated, and `BrowseState.indexing` would then hold different keys in Polish.
 */
object DownloadKeys {
    const val SONG_LENGTHS = "songlengths"
    const val TRACK_METADATA = "trackmetadata"
    const val FAVOURITES = "favourites"
    const val REPLAYS = "replays"

    /** UADE's replay routines. A separate key from sc68's: different archives, different sizes. */
    const val PLAYERS = "players"

    /** The one press that fetches the lot. Its own key, so the offer can show its own spinner. */
    const val EVERYTHING = "everything"

    /**
     * Everything the app knows about a file that the file cannot say -- HVSC's SID lengths,
     * songdb's credits and lengths -- as one press. Nobody has to know which database says what.
     */
    const val SONG_METADATA = "songmetadata"

    /** sc68's and UADE's replay routines, as one press. Other people's code, fetched from them. */
    const val REPLAY_ROUTINES = "replayroutines"
}
