// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.GrantedFolder

/** Which part of Browse is on screen. Back moves one step towards [ROOT]. */
enum class BrowseDomain { ROOT, LOCAL, ONLINE, SEARCH, HISTORY, SAVED }

/**
 * Which downloadable sets are on the phone, **asked, not counted** (`docs/STATUS.md` C75).
 *
 * Each is one `EXISTS` or one directory listing, so this arrives within a frame of Online
 * catalogues opening. The counts under the rows arrive later, on the same pass that has always
 * produced them; the ticks no longer wait for them.
 */
data class HeldSets(
    val songLengths: Boolean = false,
    val trackMetadata: Boolean = false,
    val songDbLengths: Boolean = false,
    val replays: Boolean = false,
    val players: Boolean = false,
)

data class BrowseState(
    val domain: BrowseDomain = BrowseDomain.ROOT,
    val loading: Boolean = false,
    /** Distinguishes consecutive visits to the same route; never restored from a saved snapshot. */
    val navigationVisit: Long = 0,

    // Local files
    val folders: List<GrantedFolder> = emptyList(),
    val openFolder: GrantedFolder? = null,
    // The open folder as a tree (`LocalTree`): every tune in it with its directory, where the tree
    // starts, which level is on screen, and the folders on that level. `tracks` is the level's files.
    val folderAll: List<Pair<String, TrackRef>> = emptyList(),
    val folderRoot: String = "",
    val folderAt: String = "",
    val subfolders: List<LocalTree.Folder> = emptyList(),

    // Online catalogues
    val catalogues: List<CatalogueSummary> = emptyList(),
    val openCatalogue: CatalogueSummary? = null,
    val openFormat: String? = null,
    val openAuthor: String? = null,
    val groups: List<CatalogueGroup> = emptyList(),
    /**
     * What is downloading right now: a key per download, and a line to show for each.
     *
     * A map rather than one string: downloads run concurrently, so a single field would be
     * overwritten by the second and then cleared by whichever finished first, hiding the banner
     * while a download was still running.
     *
     * Keyed by [DownloadKeys] or a catalogue id, so a row can ask whether **it** is the one
     * downloading and show a spinner where its arrow was.
     */
    val indexing: Map<String, String> = emptyMap(),
    /** Non-null while a folder is being scanned: files probed so far, and how many there are. */
    val scanProgress: Pair<Int, Int>? = null,
    /**
     * True once the app has read what this phone holds: the catalogue summaries and the folders.
     *
     * **Nothing may be concluded from an empty [catalogues] before this is true.** Both are read
     * from the database after launch, so for the first moment of every session they are empty --
     * and a screen that asks "is anything indexed?" gets "no" from a question that has not been
     * answered yet. That showed as an offer to download an index, on a phone with half a million
     * tracks in it, for the two seconds before the list arrived.
     */
    val knowsWhatIsHeld: Boolean = false,

    /**
     * Whether [songLengthCount] and [trackMetadataCount] have been read, as opposed to still being
     * their defaults.
     *
     * Separate from [knowsWhatIsHeld] because they arrive separately: the summaries are read at
     * start-up, the counts only when Online catalogues opens. Until then both counts are zero, which
     * is also what "not downloaded" looks like -- so the download row appeared for the moment
     * between the screen opening and the counts arriving, and then vanished (`docs/STATUS.md` C71).
     */
    val heldCountsKnown: Boolean = false,

    /** What is on the phone, asked quickly; null until the first quick look (C75). */
    val held: HeldSets? = null,

    /** Catalogues whose index is being deleted right now: their row says so (C76). */
    val deleting: Set<String> = emptySet(),

    /** True when the open folder has never been scanned. */
    val folderUnscanned: Boolean = false,
    /** True when the open folder's index was built by a different set of decoders. */
    val folderStale: Boolean = false,
    /**
     * True when this level was reached by "more from this author" rather than by browsing down to
     * it. Back has to undo the jump instead of walking up a hierarchy the user never walked down.
     */
    val arrivedByJump: Boolean = false,
    /** Saved places opens a complete route in one step; Back returns to that list. */
    val returnToSavedPlaces: Boolean = false,
    /** How many SID tunes HVSC has given us a length for. Zero until the database is downloaded. */
    val songLengthCount: Int = 0,
    /** How many tunes the songdb metadata table describes. Zero until it is downloaded. */
    val trackMetadataCount: Int = 0,
    /** How many files songdb has lengths for (A52). Downloaded and deleted with the metadata. */
    val songDbLengthCount: Int = 0,
    /**
     * How many of Modland's favourites this device could play — listed **and** indexed.
     *
     * The playable count rather than the published 991, because it is what the dice would actually
     * draw from, and because it is zero in the two states the Favourites chip must be disabled in:
     * list not downloaded, and Modland not indexed. One number answers both.
     */
    val favouriteCount: Int = 0,
    /**
     * How many favourites the downloaded list names at all, indexed or not.
     *
     * Kept beside [favouriteCount] because zero has two causes and they need different sentences:
     * the list was never downloaded, or it was and Modland is not indexed. One number cannot tell
     * a user which of those to fix, and "download" is the wrong advice for the second.
     */
    val favouritesListed: Int = 0,
    /**
     * Whether a browser is paired.
     *
     * In state rather than read from preferences at the call site, because it decides an **icon**:
     * "to browser" shows a code when a press will open the camera, a link when it will send.
     */
    val pairedBrowser: Boolean = false,
    /**
     * Where the page is, for a shared link and for the field in Settings.
     *
     * **Here rather than read once where the screen is built**: a successful pairing changes it
     * (`Appearance.rememberPairing`), and a value captured at composition would leave Settings
     * showing the address from before the scan. A link asks `Appearance` at the moment it is made,
     * so sending is unaffected; only the screen would be behind.
     */
    val webPlayer: String = QueueLink.DEFAULT_BASE,
    /** Bytes in the fetched-file cache, and bytes in permanent downloads. */
    val storageBytes: Pair<Long, Long> = 0L to 0L,
    /** Bytes each downloaded catalogue archive holds, by catalogue id. Only what exists is listed. */
    val archiveBytes: Map<String, Long> = emptyMap(),
    /** Bytes the database file holds — playlists, history, and every index that is rows not a file. */
    val databaseBytes: Long = 0L,
    /** How many downloaded sc68 replay routines are present. Zero until the user fetches them. */
    val replayCount: Int = 0,
    /** Bytes those replays hold. */
    val replayBytes: Long = 0L,
    /**
     * How many of UADE's replay routines are present. Zero until the user fetches them, and while
     * it is zero the Amiga custom formats do not play at all -- 12 files in 300, measured.
     */
    val playerCount: Int = 0,
    /** Bytes those replay routines hold, with the song database that comes with them. */
    val playerBytes: Long = 0L,
    /** Which decoders this build has, for telling a stale catalogue index from a current one. */
    val backends: String = "",

    // What has been played: how much of it, and which hundred is on screen (`HistoryPages`)
    val historyTotal: Int = 0,
    val historyPage: Int = 0,
    /** A new explicit visit resets History scroll without resetting on page/data updates. */
    val historyVisit: Long = 0,
    val historyQuery: String = "",
    val historyReadFailed: Boolean = false,

    // Search
    val query: String = "",
    /** What the search covers, as one value shown in the field's own label. See [SearchScope]. */
    val searchScope: SearchScope = SearchScope.Everywhere,
    /**
     * How many indexed, playable tunes each platform holds, by platform id.
     *
     * Counted from the catalogue index, which contains only files this build claims, so a platform
     * missing from this map has nothing to offer and its chip is drawn disabled. Computed rather
     * than declared: a hard-coded "supported" list goes stale the moment a decoder is added.
     */
    val platformCounts: Map<String, Int> = emptyMap(),
    /** What the dice picks from. Not persisted: see [RandomScope]. */
    val randomScope: RandomScope = RandomScope.Everything,
    /**
     * Whether a search has been run for the scope now shown.
     *
     * An empty list means two different things, and the screen must not say the alarming one for
     * both: nothing typed yet is not the same as nothing out there.
     */
    val searched: Boolean = false,
    /**
     * True when the only source the scope could ask was the live one, and the query was blank.
     *
     * The Mod Archive has no index here to list, and asking it for nothing returns its
     * "Or perhaps enjoy some of these…" page rather than the archive. A blank query therefore
     * skips it, and the screen must not then say "nothing found" -- that is a claim about the
     * archive, when in truth nothing was asked.
     */
    val liveSearchNeededQuery: Boolean = false,
    /**
     * How many rows the two capped sources matched in total, or 0 when nothing is capped.
     *
     * "About", because the sources overlap and are de-duplicated afterwards — a tune that is both
     * in a playlist and in Modland is counted twice here and shown once. It is a number for
     * deciding whether to narrow the query, not for quoting.
     */
    val searchMatches: Int = 0,

    /** Whatever the current level lists, in the form the playlist takes. */
    val tracks: List<TrackRef> = emptyList(),

    /** Rows of the open folder being fetched ahead right now: a spinner each (A55). */
    val aheadFetching: Set<String> = emptySet(),
    /** Rows of the open folder already on the phone: a mark each, whoever fetched them (A55, D5). */
    val cachedHere: Set<String> = emptySet(),
    /** UI projection from History, not a change to the catalogue rows or their sort key. */
    val learnedTitles: Map<String, String> = emptyMap(),
    /** Qualified History entries among Browse rows and the Random record, never playlist decoration. */
    val listenedHere: Set<String> = emptySet(),
) {

    /**
     * Whether Browse leads anywhere yet: rows to walk, or a folder that was granted.
     *
     * **`CatalogueSummary.indexed` is the wrong question**, and answering it here was a defect: it
     * is `trackCount > 0 || isOnlineOnly`, and The Mod Archive is online-only, so `indexed` is true
     * for it on a phone that holds nothing at all. `any { it.indexed }` therefore said yes to every
     * install ever made, including the empty one this exists to detect.
     *
     * Rows, then — or a granted folder, so that somebody who only plays their own files is not
     * pushed towards a 49 MB download they do not want.
     *
     * The Mod Archive's live search is still reachable from Browse in the top bar; what this
     * decides is only what the *empty playlist* offers as the way on.
     */
    val hasSomethingToBrowse: Boolean
        get() = catalogues.any { it.trackCount > 0 } || folders.isNotEmpty()

    /**
     * Whether both halves of the songdb tick are here: the metadata and, since A52, the lengths.
     *
     * **Both, not either.** A phone that fetched the metadata before the lengths existed holds a
     * full metadata table and an empty lengths one, and asking only about the first would never
     * offer it the second -- the Amiga tunes on the phones that had downloaded the most would be
     * the ones left waiting for a measurement.
     */
    val songDbComplete: Boolean
        get() = held?.let { it.trackMetadata && it.songDbLengths } ?: false

    /**
     * Whether the whole song metadata button's worth is here: HVSC's SID lengths and both halves of
     * songdb. One button fetches the three (decided 2026-09-21), so one answer says whether it has.
     */
    val songMetadataComplete: Boolean
        get() = held?.songLengths == true && songDbComplete

    /** Whether both sets of replay routines are here, sc68's and UADE's: one button, one answer. */
    val replayRoutinesComplete: Boolean
        get() = held?.let { it.replays && it.players } ?: false
}
