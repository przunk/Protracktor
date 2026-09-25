// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Process
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.format.DateUtils
import com.przunk.protracktor.Appearance
import android.net.ConnectivityManager
import com.przunk.protracktor.R
import com.przunk.protracktor.data.CatalogueGroup
import com.przunk.protracktor.data.CatalogueStore
import com.przunk.protracktor.data.CatalogueSummary
import com.przunk.protracktor.data.CatalogueTrack
import com.przunk.protracktor.data.FavouriteStore
import com.przunk.protracktor.data.GrantedFolder
import com.przunk.protracktor.data.HistoryStore
import com.przunk.protracktor.data.IndexedFile
import com.przunk.protracktor.data.LibraryIndexStore
import com.przunk.protracktor.data.LibraryStore
import com.przunk.protracktor.data.PlaylistFile
import com.przunk.protracktor.data.SavedPlayerState
import com.przunk.protracktor.data.SavedPlaylist
import com.przunk.protracktor.data.SchemaSql
import com.przunk.protracktor.data.SearchTerms
import com.przunk.protracktor.data.SongLengthStore
import com.przunk.protracktor.data.DatabasePreparation
import com.przunk.protracktor.data.Md5
import com.przunk.protracktor.data.SongDbMetadata
import com.przunk.protracktor.data.SongLengths
import com.przunk.protracktor.data.TrackMetadataStore
import com.przunk.protracktor.engine.NativeData
import com.przunk.protracktor.engine.NativeEngine
import com.przunk.protracktor.net.CacheBudget
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.Lha
import com.przunk.protracktor.net.ModArchive
import com.przunk.protracktor.net.Modland
import com.przunk.protracktor.net.RemoteFiles
import com.przunk.protracktor.net.WebRemote
import com.przunk.protracktor.net.Sc68Replays
import com.przunk.protracktor.net.UadePlayers
import com.przunk.protracktor.net.UnExoticA
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Something to tell the user.
 *
 * Carries an id so that two identical texts in a row are two separate notices rather than one that
 * silently fails to reappear. [actionLabel] is the hook the undo work will hang on; nothing sets it
 * yet.
 */
data class Message(
    val text: String,
    val actionLabel: String? = null,
    val id: Long = nextMessageId(),
)

private var messageCounter = 0L

private fun nextMessageId(): Long = ++messageCounter

data class PlayerUiState(
    val queue: PlayQueue = PlayQueue(tracks = emptyList()),
    val playing: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    /**
     * Where a seek under way is going, or null (`SeekProgress`). A SID seeks by running its machine
     * there, for seconds; meanwhile the bar shows this, not where the engine still says it is.
     */
    val seekingTo: Double? = null,
    /** The seek has run long enough to say so: the spinner in place of the elapsed time. */
    val seekSlow: Boolean = false,
    /** Which tune inside the file is playing, counted from zero. */
    val subsong: Int = 0,
    /** How many tunes the file holds. One for a format that holds one. */
    val subsongCount: Int = 1,
    /**
     * Whether to play every tune inside a file rather than only the first.
     *
     * A playback mode like shuffle and repeat: global, remembered, and about what happens next
     * rather than about any one row. Off by default -- a file reporting 256 subsongs would
     * otherwise take over a listening session the first time one turned up.
     */
    val playAllSubsongs: Boolean = false,
    /**
     * How long to play a tune whose length nothing knows, in seconds (`docs/STATUS.md` C56).
     *
     * **Deliberately not folded into [durationSeconds].** That field is what the app *knows*, and a
     * guess written into it would be shown on screen as a fact and would make an unseekable tune
     * look seekable. This is the point at which the app stops listening, which is a different
     * statement from how long the tune is.
     */
    val fallbackLengthSeconds: Int = FallbackLength.DEFAULT_SECONDS,
    /** When an opened folder's tracks are fetched ahead (A55, D4). */
    val cacheAhead: CacheAhead = CacheAhead.DEFAULT,
    /** The database is being migrated or re-decided, and the screen says so (A60). */
    val preparingDatabase: Boolean = false,
    /** The longest a tune shared as audio runs, in minutes (A62). */
    val shareAudioMinutes: Int = AudioExport.DEFAULT_LIMIT_MINUTES,
    val scanning: Boolean = false,
    /** A track is being read. Shown, because on a network share this is seconds, not milliseconds. */
    val loadingTrack: Boolean = false,
    /**
     * Playing, but not part of the playlist.
     *
     * What Random produces: it plays first, and keeping it is a separate decision taken after
     * hearing it.
     */
    val transient: TrackRef? = null,
    /**
     * Whether [transient] came from outside the app — a file another app handed us.
     *
     * A transient track is not necessarily a random pick: a tune opened from a file manager also
     * plays outside the playlist. Next must not roll the dice for it, and the scrim over the
     * playlist must not say "Next picks another", because next picks nothing. One file arrived,
     * and that is all there is.
     */
    val externalOpen: Boolean = false,
    /**
     * Playing from search results rather than from the playlist.
     *
     * The results become the queue while you are in them: next and previous walk what you found,
     * and the playlist is left exactly as it was. Playing something you searched for must not
     * rewrite the list you were keeping.
     */
    val resultsQueue: PlayQueue? = null,
    /**
     * Whether [resultsQueue] is History's list (A56). Set where a results list is made -- tapping a
     * row, or walking into an author's folder -- and not where it is walked, so next and previous
     * keep it. Meaningless once [resultsQueue] is gone, and [HistoryRecording] never reads it then.
     */
    val resultsFromHistory: Boolean = false,
    /**
     * A Random session waiting under a digression (`docs/BACKLOG.md` A41).
     *
     * "More from this author" from the dice moves playback to that folder; the record and the
     * cursor stay where they are, and this says there is something to go back to. Back out of the
     * folder returns to it; a playlist, an external file or a link end it for good.
     */
    val diceWaiting: Boolean = false,
    /**
     * A search waiting under a digression: "More from this author" from its results, the owner's
     * "identically" to [diceWaiting] (2026-09-24). Back out of the author's folder returns to the
     * results, their words and their scope as they were.
     */
    val searchWaiting: Boolean = false,
    /**
     * Which list in Browse is playing, for the playlist's cover to name and lead back to (A61).
     * Meaningful while [searchMode] -- a list is what plays -- and set where a list starts playing.
     */
    val sessionSource: SessionSource? = null,
    /** Whether Random has anything behind it. Kept in state so the dock can grey the button. */
    val randomHasPrevious: Boolean = false,
    /**
     * What the dice has actually given this session, oldest first.
     *
     * **Only what has played.** `READ_AHEAD` picks stand past the cursor at all times so a tune can
     * be fetched before it is wanted, and those are a fetching strategy rather than a promise —
     * showing them would turn a record into a schedule, and a schedule the dice is free to discard
     * whenever the scope changes (`docs/PLAN_RANDOM.md`).
     */
    val randomPicks: List<TrackRef> = emptyList(),
    /** Which of [randomPicks] is playing, or -1. */
    val randomIndex: Int = -1,
    /**
     * True when the dice looked and found nothing to pick from.
     *
     * The message says why — nothing indexed, or nothing indexed for that platform — but a message
     * is a snackbar and goes. Without this the Random view would sit on "Rolling…" for ever, which
     * is a screen lying about what it is doing.
     */
    val randomExhausted: Boolean = false,
    val playlists: List<SavedPlaylist> = emptyList(),
    val activePlaylistId: Long = 0L,
    /** False until the stored state has been read. Saving before then would erase it. */
    val restored: Boolean = false,
    /**
     * The playlist on screen differs from the one on disk.
     *
     * Adding and removing edit a working copy; nothing is written until the user says so. Leaving
     * without saving therefore loses the edits, which the UI has to say out loud.
     */
    val dirty: Boolean = false,
    /** Shown to the user and cleared when acknowledged. Silence after a press is a defect. */
    val message: Message? = null,
) {
    val current: TrackRef? get() = transient ?: resultsQueue?.current ?: queue.current

    /**
     * Whether the current backend can move to a position at all.
     *
     * Asked, not assumed. sc68 emulates a 68000 and has no way back except running the machine
     * again from the start, so offering a slider there would be offering a control that cannot be
     * honoured (docs/ARCHITECTURE.md §5).
     */
    /**
     * How far the seek bar runs: the length, or where playback will stop when nothing knows the
     * length (`BarLength`, the owner's variant (a)).
     */
    val bar: BarLength.Bar
        get() = if (metadata.isEmpty()) {
            // Not yet described: nothing is known, not even that the length is unknown. Without
            // this the bar would flash the fallback for the moment a tune takes to open.
            BarLength.Bar(0.0, approximate = false)
        } else {
            BarLength.of(durationSeconds, metadata["ends_at"]?.toDoubleOrNull(), fallbackLengthSeconds.toDouble())
        }

    val seekable: Boolean get() = metadata["seekable"] != "0" && current != null && bar.seconds > 0.0

    /**
     * The year the tune was released, or empty when nothing in the file says.
     *
     * Derived rather than stored, like [seekable] above: it is a reading of the metadata that is
     * already here, and a copy would only be a second thing to keep in step. See [ReleaseYear] for
     * why it takes a parser -- five backends record the year five ways and only one of them
     * records a year.
     */
    val releaseYear: String get() = ReleaseYear.from(metadata)

    /**
     * What the transport can do right now.
     *
     * Asked of the mode rather than always of the queue: during Random the buttons walk the random
     * history, and a queue that happens to be empty must not grey them out.
     */
    /**
     * Whether **next** would do anything — a tune inside the file, or the next file.
     *
     * The subsong half was missing, and it made the transport disagree with itself: on the last
     * track of a playlist `next()` would step to subsong two and the button that calls it was
     * disabled, so a file with 256 tunes in it could only be walked from the Now Playing strip.
     * The notification reads the same value, so it was wrong there too.
     */
    val canGoNext: Boolean
        get() = (playAllSubsongs && subsong + 1 < subsongCount) || canGoNextFile

    val canGoPrevious: Boolean
        get() = (playAllSubsongs && subsong > 0) || canGoPreviousFile

    /** Whether there is another **file** — what a long press on next asks for. */
    val canGoNextFile: Boolean
        get() = when {
            externalMode -> false
            randomMode -> true
            searchMode -> resultsQueue?.hasNext == true
            else -> queue.hasNext
        }

    val canGoPreviousFile: Boolean
        get() = when {
            externalMode -> false
            randomMode -> randomHasPrevious
            searchMode -> resultsQueue?.hasPrevious == true
            else -> queue.hasPrevious
        }

    val activePlaylistName: String?
        get() = playlists.firstOrNull { it.id == activePlaylistId }?.name

    /** True while Random is driving playback rather than the playlist. */
    val randomMode: Boolean get() = transient != null && !externalOpen

    /** True while a file handed to us by another app is playing. */
    val externalMode: Boolean get() = transient != null && externalOpen

    /**
     * True while search results are driving playback rather than the playlist.
     *
     * **A queue left behind is not a queue playing.** Playing a dice pick does not clear the list
     * Browse was last played from, so `resultsQueue != null` alone answered yes while the dice was
     * the only source — and the Random view, which closes itself when results take over
     * (`docs/STATUS.md` C49), closed in the frame it opened.
     *
     * The condition is the one [nextFile] already applies: a transient tune owns the transport
     * unless the dice is waiting under a list, in which case the list owns it and this is a
     * digression (`docs/BACKLOG.md` A41).
     */
    val searchMode: Boolean get() = resultsQueue != null && (transient == null || diceWaiting)

    /** True whenever what is playing did not come from the active playlist. */
    val awayFromPlaylist: Boolean get() = transient != null || searchMode
}

/** Which part of Browse is on screen. Back moves one step towards [ROOT]. */
enum class BrowseDomain { ROOT, LOCAL, ONLINE, SEARCH, HISTORY }

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

    // Local files
    val folders: List<GrantedFolder> = emptyList(),
    val openFolder: GrantedFolder? = null,

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

    // What has been played
    val history: List<TrackRef> = emptyList(),

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

class PlaybackController private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: PlaybackController? = null

        fun get(context: Context): PlaybackController =
            instance ?: synchronized(this) {
                instance ?: PlaybackController(context.applicationContext).also { instance = it }
            }

        // Fast enough that a progress bar does not visibly step, slow enough to be free.
        private const val POLL_INTERVAL_MS = 200L
        private const val SAVE_DEBOUNCE_MS = 400L

        /** How often the background metadata pass looks to see whether the user has stopped. */
        private const val IDLE_CHECK_MS = 500L

        /** Between files, so resolving hundreds does not saturate a network share. */
        private const val RESOLVE_GAP_MS = 120L

        /**
         * How long the track list waits before being written.
         *
         * Longer than [RESOLVE_GAP_MS] by an order of magnitude, so a run of resolutions collapses
         * into one write rather than one write each.
         */
        private const val TRACK_WRITE_DEBOUNCE_MS = 1500L

        /** Recognised by the UI, which turns it into the localised label on the snackbar action. */
        const val UNDO = "undo"

        /**
         * Which archive catalogue a reference belongs to, if any.
         *
         * Asked of the catalogue list rather than matched against one name: every archive catalogue
         * uses its own id as the scheme, so a hard-coded prefix here would need editing for each new
         * one -- and the one after that would be added without anybody noticing this line existed.
         */
        /**
         * Where HVSC's song lengths come from -- the collection's own site, re-checked 2026-09-02
         * and still serving the 5,205,150 bytes `docs/ARCHITECTURE.md` recorded on 2026-08-31.
         *
         * HVSC is distributed through mirrors and this address may one day stop answering;
         * `hvsc.brona.dk/HVSC/C64Music/DOCUMENTS/Songlengths.md5` served a byte-identical copy on
         * the same day and is the first place to look if it does. A dead URL here costs a message
         * about a failed download, not a crash and not a wrong duration.
         */
        private const val SONG_LENGTHS_URL =
            "https://www.hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5"

        /**
         * The songdb metadata table: author, publisher, album and year for 380,282 hashes.
         *
         * Fetched from GitHub's raw host rather than from any of the ~400 archives the database was
         * built from -- it is one file, versioned, and the project that assembles it is the one
         * asking to be credited. GPL-2.0-or-later (`docs/LICENSES.md`).
         */
        private const val TRACK_METADATA_LABEL = "Track metadata and lengths"

        /**
         * The songdb revision both files come from, **pinned** (`docs/PLAN_SONGDB_LENGTHS.md` D2).
         *
         * The metadata followed `master` until A52. The repository's README says its author
         * "reserve[s] the right to change the format or location … at any time", and a parser
         * pointed at a moving target breaks on somebody's phone the day it moves. `1bad3e8` is
         * 2026-08-22, the commit A52 was measured against; moving it is a deliberate act.
         */
        private const val SONGDB_REVISION = "1bad3e8"
        private const val SONGDB_BASE =
            "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/$SONGDB_REVISION/tsv/pretty/md5/"
        private const val TRACK_METADATA_URL = SONGDB_BASE + "metadata.tsv"

        /**
         * songdb's song lengths: every subsong of 476,919 files, the Amiga formats among them
         * (A52). Fetched with the metadata, under the same tick -- one idea, "what songdb knows
         * about a file" (D1). 11.9 MB.
         */
        private const val SONGDB_LENGTHS_URL = SONGDB_BASE + "songlengths.tsv"

        /**
         * Modland's favourites, as `audacious-uade-tools` republishes them.
         *
         * The same repository and licence as the metadata table above (GPL-2.0-or-later), chosen
         * over scraping modland.com for the same reason: it is one file, versioned, and already
         * parsed into a shape somebody maintains. 142 KB, which is why this one download asks for
         * no warning about its size.
         */
        private const val FAVOURITES_LABEL = "Modland favourites"
        private const val FAVOURITES_URL =
            "https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/" +
                "songdb/sources/site/modland_favourites.tsv"

        private const val SONG_LENGTHS_LABEL = "SID song lengths"
        private const val REPLAYS_LABEL = "Atari ST replay routines"
        private const val PLAYERS_LABEL = "Amiga replay routines"

        private fun archiveCatalogueOf(id: String): Catalogue? =
            Catalogue.all.firstOrNull { it.isArchive && id.startsWith("${it.id}://") }

        /** Four megabytes. Comfortably above any tracker module and below anything worth holding. */
        private const val MAX_PREFETCH_BYTES = 4 * 1024 * 1024

        /** Total held for reading ahead. Three of the per-file limit would be twelve megabytes. */
        private const val MAX_PREFETCH_TOTAL_BYTES = 8 * 1024 * 1024

        /**
         * How far ahead Random decides and fetches.
         *
         * Three rather than "a few": each one is a file fetched for a tune that may never be
         * played, and on a metered connection that is the cost of the feature. Three covers the
         * gap between tracks at the speeds measured (`docs/ARCHITECTURE.md` §8) without turning one
         * listen into a handful of downloads.
         */
        private const val READ_AHEAD = 3

        /**
         * How many times over to draw, so repeats can be dropped and enough still remain.
         *
         * Four is not measured, and does not need to be: the cost is a `LIMIT 12` where a `LIMIT 3`
         * would do, against a query that already sorts the whole scope by a generated key
         * (`docs/review-round-8.md` R7). If the pool is wide enough for repeats to be rare the
         * extra rows are thrown away, and if it is narrow enough for them to be common this is what
         * stops the dice looping over the same handful.
         */
        private const val OVERDRAW = 4
    }

    // Main.immediate so a press and the state change it causes land in the same frame; the work
    // itself moves to IO where it belongs.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * For work nobody asked for: identifying tracks in the background, and scanning a library.
     *
     * **`Dispatchers.IO` is the wrong tool for this.** Its threads run at default priority, so
     * opening a decoder — which is real CPU work, and for sc68 means building a 68000 emulator —
     * competes with the UI thread on equal terms. On a phone that is a list which stutters for as
     * long as the work runs — the first twenty seconds after launch, when a library is scanned.
     *
     * A single thread at `THREAD_PRIORITY_BACKGROUND` puts this in Android's background cgroup,
     * where it gets a small share of the processor and *cannot* starve drawing however long it
     * takes. One thread rather than a pool, because these tasks are sequential by nature and two of
     * them would only contend with each other.
     */
    private val backgroundWork = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "protracktor-background")
    }.asCoroutineDispatcher()

    private val _state = MutableStateFlow(PlayerUiState(
        cacheAhead = Appearance.cacheAhead(context),
        shareAudioMinutes = Appearance.shareAudioMinutes(context),
    ))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * "Look here" — a row index the playlist should scroll to.
     *
     * An event rather than state: scrolling somewhere is something that happens once, and a value
     * left sitting in state would scroll again on every recomposition that touched it. Extra buffer
     * capacity with DROP_OLDEST so an emit never suspends and never queues a stale destination.
     */
    private val _reveal = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val reveal: SharedFlow<Int> = _reveal.asSharedFlow()

    /**
     * Asks the UI to show Browse at whatever [BrowseState] now says.
     *
     * An event rather than a flag, because "show it" happens once. The browse sheet normally resets
     * to the top when it opens; this exists so a jump can put it somewhere first.
     */
    private val _showBrowse = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val showBrowse: SharedFlow<Unit> = _showBrowse.asSharedFlow()

    /**
     * A share the UI should put in front of the user.
     *
     * Emitted rather than started here: choosing an app is an activity, and this class holds the
     * application context. Preparing what is shared is work with a network fetch in it, and belongs
     * on this side; showing the chooser does not.
     */
    private val _share = MutableSharedFlow<Intent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val share: SharedFlow<Intent> = _share.asSharedFlow()

    /** Asks the UI for a pairing code. Emitted when "to browser" is pressed and none is stored. */
    private val _scan = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val scan: SharedFlow<Unit> = _scan.asSharedFlow()

    /**
     * Whether a browser is paired, read once at start-up.
     *
     * It decides an icon, which has to be right from the first frame. An in-memory flag set only
     * once something has been sent would leave a *paired* app showing a QR code until the first
     * press. Reading the stored pairing here costs one preference lookup at construction.
     */
    private val pairedAtStart = Appearance.pairedEndpoint(context) != null

    private val _browse = MutableStateFlow(
        BrowseState(pairedBrowser = pairedAtStart, webPlayer = Appearance.webPlayer(context)),
    )
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    /** The open module. Owned here because native memory is invisible to the garbage collector. */
    private var track: NativeEngine.Track? = null

    /**
     * HVSC's lengths for the open file, one per tune, or empty when it did not supply any.
     *
     * Kept because the backend cannot be asked twice. libsidplayfp reports no length at all — the
     * database is the only source — so a subsong switch must not clear the duration and wait for a
     * backend that will never answer: tune two onwards would show no length and play for ever.
     */
    private var openSongLengths: List<Double> = emptyList()

    /** The open file's MD5, for recording a length learnt while it plays (A50). */
    private var openMd5: String = ""

    /**
     * What the metadata database said about the open **file**, kept for a subsong switch.
     *
     * Its fields are keyed on the file's hash, so they go on applying when the tune inside changes;
     * the tune's own description is re-read and wins wherever it has something to say.
     */
    private var openMetadata: com.przunk.protracktor.data.SongDbMetadata.Entry? = null

    /**
     * Whether the audio path has already been complained about this session.
     *
     * Once is a diagnosis; once per track is a nuisance, and the answer cannot change while the app
     * runs — it is a property of the device's audio stack, not of the tune.
     */
    private var reportedSampleRate = false

    /** Set by a subsong switch, cleared by the poll that re-reads the new tune's description. */
    private var describeAgain = false

    /**
     * The in-flight open. Cancelled before a new one starts, because two quick presses of next
     * would otherwise run two opens, and the second would overwrite `track` without closing the
     * first — leaving a stream playing with nobody holding it.
     */
    private var openJob: Job? = null

    /**
     * Tracks read ahead, keyed by reference id, in the order they were read.
     *
     * Read ahead because starting a track is dominated by **reading** it rather than by decoding
     * it: a module is kilobytes and decodes in milliseconds, while opening one on a network share
     * is a round trip measured in seconds (`docs/BACKLOG.md` R9). More than one entry because
     * Random reads several ahead (`docs/BACKLOG.md` A11), and a single slot could only ever hold
     * the very next one.
     */
    private val prefetched = LinkedHashMap<String, ByteArray>()
    private var prefetchJob: Job? = null

    /** The run of downloads the offer started, kept so that [cancelDownloads] has something to stop. */
    private var selectedDownloads: Job? = null
    private var scanJob: Job? = null

    /** The background metadata pass. Cancelled and restarted whenever the track list changes. */
    private var resolveJob: Job? = null

    /**
     * What the first playlist is called, and what an unnamed one falls back to.
     *
     * Read from resources rather than held as a constant, because it is the first word a new
     * install shows and it was English on a Polish phone. Only ever used when a name is *created*:
     * the name is then the user's data, and switching the app's language does not rename what
     * somebody may have renamed themselves.
     */
    private val defaultPlaylistName: String get() = context.getString(R.string.playlist_default_name)

    private val store = LibraryStore(context)
    private val catalogues = CatalogueStore(context)
    private val remoteFiles = RemoteFiles(context)
    private val songLengths = SongLengthStore(context)
    private val songDbLengths = com.przunk.protracktor.data.SongDbLengthStore(context)
    private val learnedLengths = com.przunk.protracktor.data.LearnedLengthStore(context)
    private val trackMetadata = TrackMetadataStore(context)
    private val favourites = FavouriteStore(context)

    private val history = HistoryStore(context)
    private val libraryIndex = LibraryIndexStore(context)

    private val audioFocus = AudioFocus(
        context = context,
        onPause = { pause() },
        onDuck = { gain -> track?.setGain(gain) },
    )

    /**
     * What Random has played, and where in it we are.
     *
     * Random needs its own history: "previous" during Random has to mean the previous random pick,
     * not the previous row of a playlist the user is not listening to.
     */
    private val randomHistory = mutableListOf<TrackRef>()
    private var randomCursor = -1

    /**
     * Which Random session the work in flight belongs to.
     *
     * **The record and the cursor are fields, and the work that reads them suspends** — filling the
     * queue asks the database. Start a session again, or leave for the playlist, while an advance
     * is waiting, and it would resume against a record that is not its own — the cursor back at -1
     * and the new session's first pick already in the list, which reads the list out of bounds.
     * Raised wherever a session begins or ends; checked after every wait.
     */
    private var randomSession = 0

    /**
     * Copies the played part of the history into the state, for the Random view to draw.
     *
     * Called from every place that moves the cursor or edits the record. A single writer would be
     * better; there is no single place the history changes, and inventing one would mean routing
     * `advanceRandom`, `randomPrevious` and the list's own edits through a funnel that does nothing
     * else.
     */
    private fun publishRandomPicks() {
        val played = randomHistory.take(randomPlayed + 1)
        _state.update { it.copy(randomPicks = played, randomIndex = randomCursor) }
    }

    /**
     * How far into [randomHistory] has actually been played. Anything past it was picked ahead and
     * never heard -- which is the difference between history and speculation, and the dice needs it.
     */
    private var randomPlayed = -1

    /** Which playlist is being edited. Resolved during [restore]; there is only one so far. */
    private var playlistId: Long = 0L

    /** Debounces writes. Every transport press changes state; the disk does not need each one. */
    private var saveJob: Job? = null
    private var trackWriteJob: Job? = null

    init {
        // Before anything can be opened: sc68 reads its replay binaries from a path, and an asset
        // inside an APK does not have one.
        // Once at start-up, so an installation that grew past the ceiling before the limit existed
        // converges on it instead of staying over forever. Nothing is in use yet, which is exactly
        // why this is the cheapest moment to do it.
        scope.launch(Dispatchers.IO) { runCatching { remoteFiles.enforceBudget() } }

        // **An opened Modland folder fetches its tracks ahead** (A55). One place decides, from the
        // folder on screen and the setting: a new folder, a left one, or a changed setting cancels
        // the last run and starts the next, which is what makes "leaving stops the rest" true.
        // **What is on the phone, in whatever Browse lists** (the owner, 2026-09-24): a folder of any
        // catalogue, search results, History. Asked again whenever the rows change; a download that
        // lands asks it again too (`refreshOnPhone`).
        scope.launch {
            _browse.map { browse -> browse.tracks.map { it.id } }
                .distinctUntilChanged()
                .collectLatest { ids -> val here = onPhoneOf(ids); _browse.update { it.copy(cachedHere = here) } }
        }
        scope.launch {
            combine(_browse.map(::aheadKeyOf), _state.map { it.cacheAhead }) { key, mode -> key to mode }
                .distinctUntilChanged()
                .collectLatest { (key, mode) -> fetchFolderAhead(key, mode) }
        }

        // **What is on this phone, before anybody opens Browse.** The empty playlist has to
        // choose between offering Browse and offering the download sheet, and it cannot ask a
        // screen that has never been opened. Two small reads -- the `catalogues` table is one row
        // per catalogue and `granted_folders` is a handful -- rather than `refreshCatalogues()`,
        // which also counts platforms across half a million rows and has no business running at
        // start-up.
        scope.launch {
            val summaries = catalogues.summaries()
            val granted = store.grantedFolders()
            _browse.update {
                it.copy(catalogues = summaries, folders = granted, knowsWhatIsHeld = true)
            }
        }

        // A catalogue that is no longer offered leaves its rows behind, and rows
        // nothing lists are rows in every global search. Once at start-up, next to the cache sweep
        // and for the same reason.
        scope.launch(Dispatchers.IO) { runCatching { catalogues.pruneUnknownCatalogues() } }

        // **What a format added since the last run costs: one statement, and no network**
        // (`docs/ROADMAP_FORMATS.md` step 0). An index holds every row the archive lists, so a
        // change to `SupportedFormats` is a question the stored rows can already answer —
        // 228ms over 516,107 of them, measured — where it used to mean re-downloading Modland's
        // 40 MB on every device.
        //
        // Run only when the stamp actually moved. Recomputing on every start would be 228ms of
        // nothing, every time, for a list that changes with a release.
        // Collected first, so a migration the next lines set off is on screen while it runs.
        scope.launch {
            DatabasePreparation.active.collect { running -> _state.update { it.copy(preparingDatabase = running > 0) } }
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val current = NativeEngine.backendsFingerprint()
                if (catalogues.summaries().any { it.indexedAt != null && it.backends != current }) {
                    // Said on screen while it runs (A60): half a million rows re-decided is the
                    // other moment, beside a migration, that the lists wait for after an update.
                    DatabasePreparation.during {
                        catalogues.refreshPlayable()
                        // Re-stamped only where the index is whole. A partial one -- written before
                        // the index stopped being a function of the format list -- is missing rows
                        // no recompute can conjure, and has to go on saying it needs fetching again.
                        catalogues.restampComplete(current)
                    }
                    _browse.update { it.copy(catalogues = catalogues.summaries()) }
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val version = context.packageManager
                    .getPackageInfo(context.packageName, 0).longVersionCode.toString()
                NativeEngine.setDataPath(
                    com.przunk.protracktor.engine.NativeData.ensureUnpacked(context, version).absolutePath
                )
                configureUade(version)
            }
        }

        restore()
        // One loop drives both the progress bar and end-of-track handling. The native side flags
        // completion rather than calling back, so something has to look; since the progress bar
        // needs a tick anyway, this is that tick.
        scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val open = track ?: continue
                if (!_state.value.playing) continue

                // Nothing is decided about a track that is being replaced. Every skip sets
                // `playing = false` before the load starts, so the check above already covers this
                // -- **this line was added on a wrong diagnosis of C17 and kept as belt and
                // braces**, not because it fixed anything. The real cause was in the gesture, not
                // in the player: see `PlayerDock.TransportButton`.
                if (openJob?.isActive == true) continue

                if (open.isFinished()) {
                    handleTrackEnded()
                    continue
                }

                // A subsong switch is applied on the audio thread, so the new tune's name -- like
                // its length -- does not exist until it has been. One re-read, on the first tick
                // after the switch, and never in the steady state.
                if (describeAgain) {
                    describeAgain = false
                    // The database's fields are keyed on the *file*, not the tune, so they still
                    // apply -- and the file's own new fields win over them, which `merged` already
                    // arranges by filling only what the description left blank.
                    _state.update { it.copy(metadata = merged(open.describe(), openMetadata)) }
                }

                val position = open.positionSeconds()
                val before = _state.value
                _state.update {
                    it.copy(
                        // Not while a seek is under way: the engine publishes the new place only
                        // when it is there, and until then this would pull the bar back (Q11).
                        positionSeconds = SeekProgress.shownPosition(position, it.seekingTo),
                        // Picked up here because a subsong switch is applied on the audio
                        // thread: the new tune's length does not exist until it has been.
                        durationSeconds = if (it.durationSeconds <= 0.0) {
                            open.durationSeconds()
                        } else {
                            it.durationSeconds
                        },
                    )
                }
                rememberLearntLength(before, _state.value)

                // **A tune that never ends still ends when we know how long it is.**
                //
                // `isFinished` is set by the engine when a backend renders a short buffer, and some
                // never do: libsidplayfp is running a 6502 in a loop and has no idea the music is
                // over, so without this a SID plays until the user presses something. Where HVSC
                // supplies a length the app knows the answer, and this acts on it — for every
                // format, not just SID.
                //
                // Whatever supplied the duration is trusted to be right: HVSC for SID, sc68's
                // database for SNDH, the file itself elsewhere. A wrong entry cuts a tune short,
                // which is the same trade every player using these databases makes, and the
                // alternative is the one being fixed — playing for ever.
                //
                // `handleTrackEnded` and not something of its own, so repeat, shuffle, subsongs and
                // Random all behave exactly as they do at a real end of tune.
                // **A length nothing knows still ends** (`docs/BACKLOG.md` C56). A tune with no
                // duration at all -- a SID with no HVSC entry, a `.sndh` sc68's database has never
                // heard of -- would otherwise play until somebody noticed. The fallback comes from
                // Settings, and is applied here rather than to `durationSeconds` so that what the
                // screen reports stays what the app actually knows.
                val snapshot = _state.value
                val known = snapshot.durationSeconds.takeIf { it > 0.0 }
                    ?: snapshot.fallbackLengthSeconds.toDouble()
                if (known > 0.0 && position >= known) handleTrackEnded()
            }
        }
    }

    /**
     * Keeps a length UADE worked out while the tune played, if no database had it (A50).
     *
     * Only UADE's: every other backend states its length when it opens, or after a subsong switch
     * from the file itself, and storing that would be writing down what the file says anyway. And
     * only a length that *arrived* -- zero a tick ago, a figure now -- for a subsong nothing knew.
     */
    private fun rememberLearntLength(before: PlayerUiState, after: PlayerUiState) {
        if (before.durationSeconds > 0.0 || after.durationSeconds <= 0.0) return
        if (before.subsong != after.subsong || before.current?.id != after.current?.id) return
        if (after.metadata["format"]?.endsWith("(UADE)") != true) return
        val subsong = after.subsong
        if ((openSongLengths.getOrNull(subsong) ?: 0.0) > 0.0 || openMd5.isEmpty()) return
        val md5 = openMd5
        val seconds = after.durationSeconds
        openSongLengths = LengthSource.learn(openSongLengths, subsong, seconds) ?: openSongLengths
        scope.launch { learnedLengths.remember(md5, subsong, seconds) }
    }

    // --- persistence --------------------------------------------------------------------------

    private fun restore() {
        scope.launch {
            val saved = store.loadPlayerState()
            // Read once and used twice. `defaultPlaylistId` can create one, so the list is read
            // again only when it did; asking the database the same question twice at launch is a
            // query nobody needs on the path A48 is about.
            val known = store.playlists()
            // The stored active playlist, unless it has since been deleted.
            val stored = known.firstOrNull { it.id == saved?.activePlaylistId }?.id
            playlistId = stored ?: store.defaultPlaylistId(defaultPlaylistName)
            val playlists = if (stored != null) known else store.playlists()
            val tracks = store.tracksIn(playlistId)

            _state.update { current ->
                var queue = current.queue
                    .withTracks(tracks)
                    .withRepeat(saved?.repeat ?: RepeatMode.OFF)
                if (saved?.shuffle == true) queue = queue.withShuffle(true)

                // The track it was on is made current but NOT started. Coming back to the app is
                // not a request to make noise, and R2 asks for the view to be restored, not the
                // playback (docs/OPEN_QUESTIONS.md Q6 is still open on resuming a position).
                val index = tracks.indexOfFirst { it.id == saved?.currentTrackId }
                if (index >= 0) queue = queue.startAt(index)

                current.copy(
                    queue = queue,
                    playlists = playlists,
                    activePlaylistId = playlistId,
                    playAllSubsongs = saved?.playAllSubsongs ?: false,
                    fallbackLengthSeconds =
                        FallbackLength.fromStored(saved?.fallbackLengthSeconds ?: 0),
                    restored = true,
                )
            }
            // The dice's scope, kept between runs now that the Random view shows what is set.
            _browse.update { it.copy(randomScope = storedRandomScope(saved?.randomScope)) }
            resolveMetadataInBackground()
        }
    }

    /**
     * Writes the track list, shortly.
     *
     * **Debounced, and it has to be.** `replaceTracks` deletes every row of the playlist and
     * reinserts it — two inserts per track — and background metadata resolution identifies a
     * track every 120 ms. Undebounced, a playlist of three hundred means some six hundred inserts
     * eight times a second, into the same database the list is being read from, for as long as the
     * resolution runs, and the list stutters for as long as it does.
     *
     * The state still updates per track, so titles appear as they are learned. It is only the disk
     * that waits.
     */
    private fun scheduleTrackWrite() {
        trackWriteJob?.cancel()
        trackWriteJob = scope.launch {
            delay(TRACK_WRITE_DEBOUNCE_MS)
            store.replaceTracks(playlistId, _state.value.queue.tracks)
            refreshPlaylists()
        }
    }

    /**
     * The switcher counts what each playlist holds, so every write that changes a count says so.
     *
     * `docs/STATUS.md` C44: making, renaming and deleting a playlist already re-read the list;
     * the writes that change only its **contents** did not, so "Add to playlist…" wrote the track
     * and left the number on screen as it was until the playlist was opened.
     */
    private suspend fun refreshPlaylists() {
        val known = store.playlists()
        _state.update { it.copy(playlists = known) }
    }

    /**
     * Writes the current state, shortly.
     *
     * Debounced because every press of shuffle, repeat, next and previous changes something worth
     * keeping, and none of them is worth a disk write on its own.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    /**
     * Writes the settings, never the track list.
     *
     * Shuffle, repeat and where playback got to are the app's own business and are saved as they
     * change. The track list belongs to the user's editing session and waits for [savePlaylist].
     */
    private suspend fun saveNow() {
        if (!_state.value.restored) return // never overwrite stored state with an empty start-up one
        val snapshot = _state.value
        store.savePlayerState(
            SavedPlayerState(
                activePlaylistId = playlistId,
                // The queue's track, never a transient one: a random pick is not where the user
                // was, and restoring into it would be restoring somewhere they never chose to be.
                currentTrackId = snapshot.queue.current?.id,
                shuffle = snapshot.queue.shuffle,
                repeat = snapshot.queue.repeat,
                playAllSubsongs = snapshot.playAllSubsongs,
                fallbackLengthSeconds = snapshot.fallbackLengthSeconds,
                randomScope = _browse.value.randomScope.stored(),
            )
        )
    }

    /** Commits the edited list. The diskette button. */
    fun savePlaylist() {
        scope.launch {
            val snapshot = _state.value
            store.replaceTracks(playlistId, snapshot.queue.tracks)
            // Saved silently. The Save button disappearing is the confirmation, and it is on
            // screen already.
            _state.update { it.copy(dirty = false) }
        }
    }

    /** Throws the edits away and goes back to what is on disk. */
    fun discardChanges() {
        scope.launch {
            val stored = store.tracksIn(playlistId)
            _state.update {
                it.copy(queue = it.queue.withTracks(stored), dirty = false, message = Message(context.getString(R.string.notice_changes_discarded)))
            }
        }
    }

    // --- playlists ----------------------------------------------------------------------------

    fun createPlaylist(name: String) {
        scope.launch {
            val id = store.createPlaylist(name.ifBlank { defaultPlaylistName })
            _state.update { it.copy(playlists = store.playlists()) }
            switchToPlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        scope.launch {
            store.renamePlaylist(id, name)
            _state.update { it.copy(playlists = store.playlists()) }
        }
    }

    /**
     * Deletes a playlist — or empties it, when it is the only one there is. The tracks stay in the
     * library; only the list goes.
     *
     * **The last playlist is not refused, it is reset.** Refusing it means the user presses Delete,
     * confirms, and nothing happens — and the notice explaining why loses a race with the sheet the
     * button lives in, so not even the explanation arrives.
     *
     * Somebody deleting their only playlist wants it gone, and the closest thing to gone that can
     * exist is empty and called what a new one would be called. There is nothing to undo that the
     * confirmation did not already ask about.
     */
    fun deletePlaylist(id: Long) {
        scope.launch {
            if (store.playlists().size <= 1) {
                store.replaceTracks(id, emptyList())
                store.renamePlaylist(id, defaultPlaylistName)
                stopPlayback()
                _state.update {
                    it.copy(
                        queue = PlayQueue(tracks = emptyList(), shuffle = it.queue.shuffle, repeat = it.queue.repeat),
                        playlists = store.playlists(),
                        playing = false,
                        dirty = false,
                        metadata = emptyMap(),
                        positionSeconds = 0.0,
                        durationSeconds = 0.0,
                        message = Message(context.getString(R.string.notice_playlist_emptied)),
                    )
                }
                return@launch
            }
            store.deletePlaylist(id)
            val remaining = store.playlists()
            _state.update { it.copy(playlists = remaining) }
            if (id == playlistId) switchToPlaylist(remaining.first().id)
        }
    }

    /**
     * Makes another playlist the active one.
     *
     * Playback stops. The queue *is* the active playlist, so carrying a playing track into a list
     * that does not contain it would leave next and previous disagreeing with the screen -- the
     * exact fault that made the transport feel random before.
     */
    /**
     * Makes another playlist the active one, abandoning any unsaved edits to this one.
     *
     * The UI asks first when there are edits to lose; this method is what it calls after the answer.
     */
    fun switchToPlaylist(id: Long) {
        scope.launch {
            saveNow()
            stopPlayback()
            playlistId = id
            val tracks = store.tracksIn(id)
            _state.update {
                it.copy(
                    queue = PlayQueue(tracks = tracks, shuffle = it.queue.shuffle, repeat = it.queue.repeat),
                    playlists = store.playlists(),
                    activePlaylistId = id,
                    playing = false,
                    dirty = false,
                    metadata = emptyMap(),
                    positionSeconds = 0.0,
                    durationSeconds = 0.0,
                )
            }
            scheduleSave()
            resolveMetadataInBackground()
        }
    }

    // --- browsing -----------------------------------------------------------------------------

    fun openDomain(domain: BrowseDomain) {
        _browse.update { BrowseNavigation.enteringDomain(it, domain) }
        when (domain) {
            BrowseDomain.LOCAL -> refreshFolders()
            BrowseDomain.ONLINE -> refreshCatalogues()
            BrowseDomain.SEARCH -> {
                refreshCatalogues()
                refreshPlatformCounts()
            }
            BrowseDomain.HISTORY -> openHistory()
            // The root has a consumer too: Random's scope sheet is opened from here and draws the
            // same chips. Without the counts every platform reads as "nothing indexed" and the
            // whole sheet is disabled.
            //
            // Only when they are absent. This is a grouped scan of every catalogue row, the root is
            // returned to on every step back out of a folder, and the answer only changes when an
            // index does.
            BrowseDomain.ROOT -> {
                if (_browse.value.platformCounts.isEmpty()) refreshPlatformCounts()
                // And the favourite count, for the same sheet and the same reason: it is set by
                // `refreshCatalogues`, which the root does not call, so without this the Favourites
                // chip reads as "not downloaded" for anyone who has not visited the catalogue list
                // this session. Unguarded, because unlike the
                // platform counts it is one `COUNT` over a thousand rows rather than a grouped scan
                // of half a million, and because zero is a real answer here rather than "not asked
                // yet" -- a guard on emptiness could never tell the two apart.
                refreshFavouriteCount()
            }
        }
    }

    /** One step back up the browse hierarchy. Returns false when already at the top. */
    fun browseBack(): Boolean {
        val current = _browse.value

        // A jump is one step, not a descent. "More from this author" puts you three levels deep
        // without your having passed through any of them, so back should return you to where you
        // actually were -- the playlist -- rather than making you climb out of a hierarchy you
        // never climbed into, one press per level.
        if (current.arrivedByJump) {
            _browse.update { it.copy(arrivedByJump = false) }
            return false
        }

        // Inside search there is one level, and it is the scope. The filter row and the results are
        // the same screen -- filters above, list below -- so there is nothing to navigate between;
        // the only thing that can be undone is having narrowed the search. Back widens it, which is
        // visible in the field's label going from `Amiga` to `Everywhere`, and a second press
        // leaves.
        //
        // What back must never do here is destroy the typed query or the results. The rest of this
        // method clears `tracks` on the way out of a domain, which is right for a folder you walked
        // out of and wrong for a search you may be coming straight back to. See `docs/WISHLIST.md`
        // B23: back always meaning "leave" is only safe because leaving costs nothing.
        if (current.domain == BrowseDomain.SEARCH && current.searchScope != SearchScope.Everywhere) {
            _browse.update { it.copy(searchScope = SearchScope.Everywhere).withoutStaleResults() }
            return true
        }

        val next = when {
            current.openAuthor != null -> current.copy(openAuthor = null, tracks = emptyList())
                .also { openFormat(current.openFormat.orEmpty()) }
            current.openFormat != null -> current.copy(openFormat = null, tracks = emptyList())
                .also { current.openCatalogue?.let(::openCatalogue) }
            current.openCatalogue != null -> current.copy(openCatalogue = null, groups = emptyList())
            current.openFolder != null -> current.copy(openFolder = null, tracks = emptyList())
            // Search keeps its results and its query; every other domain drops them.
            current.domain == BrowseDomain.SEARCH -> current.copy(domain = BrowseDomain.ROOT)
            current.domain != BrowseDomain.ROOT -> current.copy(domain = BrowseDomain.ROOT, tracks = emptyList())
            else -> return false
        }
        _browse.update { next }
        return true
    }

    fun refreshFolders() {
        scope.launch { _browse.update { it.copy(folders = store.grantedFolders()) } }
    }

    fun rememberFolder(treeUri: Uri) {
        scope.launch {
            MediaScanner.persistPermission(context, treeUri, isTree = true)
            val folder = GrantedFolder(treeUri.toString(), MediaScanner.labelOf(treeUri))
            store.rememberFolder(folder)
            _browse.update { it.copy(folders = store.grantedFolders()) }
            openFolder(folder)
            // Granting a folder *is* the user asking for it to be usable, so this is the one moment
            // a scan starts without a second press. Nothing else starts one: not launching, not
            // returning to the app, not opening the folder again.
            scanFolder(folder)
        }
    }

    fun forgetFolder(uri: String) {
        scope.launch {
            store.forgetFolder(uri)
            // The index goes with the grant. Keeping rows for a tree we can no longer read would
            // leave a browsable list of files that cannot be opened.
            libraryIndex.forgetFolder(uri)
            _browse.update { it.copy(folders = store.grantedFolders(), openFolder = null, tracks = emptyList()) }
        }
    }

    /**
     * Shows what a folder holds, from the index rather than by walking it again.
     *
     * Walking the tree again would be seconds of waiting on a network share for an answer that
     * has not changed, and would have to guess what is playable from filenames
     * (`docs/STATUS.md` C4). The scan happens once, on purpose, and this reads its result.
     */
    fun openFolder(folder: GrantedFolder) {
        scope.launch {
            _browse.update {
                it.copy(
                    openFolder = folder, loading = true, tracks = emptyList(),
                    folderUnscanned = false, folderStale = false,
                )
            }
            val found = libraryIndex.tracksIn(folder.uri)
            val stale = found.isNotEmpty() &&
                libraryIndex.isStale(folder.uri, NativeEngine.backendsFingerprint())
            _browse.update {
                it.copy(
                    tracks = found,
                    loading = false,
                    folderUnscanned = found.isEmpty(),
                    folderStale = stale,
                )
            }
        }
    }

    /**
     * Scans a folder by opening every file in it.
     *
     * **Always an explicit action.** Nothing calls this on launch, on returning to the app, or on
     * opening a folder: it reads every file in the tree, which on a network share is minutes, and
     * doing that because somebody switched back to the app would be indefensible.
     *
     * **It decides by content, not by name** (`docs/BACKLOG.md` A6, `docs/STATUS.md` C4). Each file
     * is handed to the same `NativeEngine.open` that playback uses, and what comes back -- the
     * backend that claimed it, the tune's own title and author, its length and subsong count -- is
     * what gets stored. A file called `.txt` that is really a module is indexed; a file called
     * `.mod` that is really a photograph is not.
     *
     * Playing is not interrupted, which depends on the backends being re-entrant: sc68 2.2.1 kept
     * its emulator in global state, so opening a second instance while one played clobbered it.
     * 3.0.0b is instance-based and was measured safe across four concurrent threads
     * (`native/probe/sc68/probe_concurrency.c`), which is what makes this a background job rather
     * than something the user has to stop the music for.
     */
    fun scanFolder(folder: GrantedFolder) {
        scanJob?.cancel()
        scanJob = scope.launch {
            _browse.update { it.copy(scanProgress = 0 to 0, folderUnscanned = false, folderStale = false) }
            val candidates = withContext(Dispatchers.IO) {
                MediaScanner.listFiles(context, Uri.parse(folder.uri))
            }
            _browse.update { it.copy(scanProgress = 0 to candidates.size) }

            val fingerprint = NativeEngine.backendsFingerprint()
            val indexed = mutableListOf<IndexedFile>()
            var done = 0
            for (candidate in candidates) {
                ensureActive()
                withContext(backgroundWork) { probe(candidate, folder.uri) }?.let(indexed::add)
                done++
                // Every file would be a state update per file and a recomposition per file; every
                // twenty-fifth is still movement on screen and costs almost nothing.
                if (done % 25 == 0 || done == candidates.size) {
                    _browse.update { it.copy(scanProgress = done to candidates.size) }
                }
            }

            libraryIndex.replaceFolder(folder.uri, indexed, fingerprint)
            _browse.update { current ->
                current.copy(
                    scanProgress = null,
                    tracks = if (current.openFolder?.uri == folder.uri) {
                        indexed.map(::toTrackRef)
                    } else {
                        current.tracks
                    },
                    folderUnscanned = false,
                    folderStale = false,
                )
            }
            _state.update {
                it.copy(
                    message = Message(
                        context.getString(
                            R.string.notice_scanned,
                            folder.displayName, indexed.size, candidates.size,
                        )
                    )
                )
            }
        }
    }

    /**
     * Asks the decoders what one file is. Null when none of them claims it.
     *
     * The bytes are read in full because that is what opening a module means -- these formats are
     * not streamed and a header is not enough to know a backend will accept the rest.
     */
    private fun probe(candidate: MediaScanner.Candidate, folderUri: String): IndexedFile? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(Uri.parse(candidate.uri))?.use { it.readBytes() }
        }.getOrNull() ?: return null

        val companions = siblingDocuments(Uri.parse(candidate.uri)).mapNotNull { (name, uri) ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { name to it }
        }
        val opened = NativeEngine.open(bytes, candidate.fileName, companions).track ?: return null
        return try {
            val described = opened.describe()
            IndexedFile(
                uri = candidate.uri,
                folderUri = folderUri,
                path = candidate.path,
                fileName = candidate.fileName,
                sizeBytes = if (candidate.sizeBytes > 0) candidate.sizeBytes else bytes.size.toLong(),
                backend = described["format"].orEmpty(),
                format = described["format"].orEmpty(),
                title = described["title"]?.trim().orEmpty(),
                author = (described["artist"]?.trim()?.ifBlank { null }
                    ?: described["composer"]?.trim()).orEmpty(),
                durationMs = (opened.durationSeconds() * 1000).toLong(),
                subsongs = described["subsongs"]?.toIntOrNull() ?: 1,
            )
        } finally {
            opened.close()
        }
    }

    private fun toTrackRef(entry: IndexedFile): TrackRef = TrackRef(
        id = entry.uri,
        title = entry.title.ifBlank { entry.fileName },
        subtitle = entry.path,
        sizeBytes = entry.sizeBytes,
        fileName = entry.fileName,
        author = entry.author,
        subsongs = entry.subsongs,
    )

    fun closeFolder() = _browse.update { it.copy(openFolder = null, tracks = emptyList()) }

    /**
     * The format an online catalogue files a track under, if it came from one.
     *
     * Read from the index rather than guessed from the filename — the filename is what made this
     * confusing in the first place.
     */
    private suspend fun catalogueFormatOf(ref: TrackRef): String? {
        val catalogue = Catalogue.owning(ref.id) ?: return null
        val path = catalogue.pathFrom(ref.id) ?: return null
        return catalogues.locate(catalogue.id, path)?.format?.takeIf { it.isNotBlank() }
    }

    // --- playlists as files -----------------------------------------------------------------

    /**
     * Writes one playlist out and offers it to be shared.
     *
     * Through the same copy-and-share path a track uses (`docs/ARCHITECTURE.md` §16), because a
     * playlist is a file like any other and nothing else here can hand a file to another app.
     */
    fun exportPlaylist(id: Long) {
        scope.launch {
            val current = _state.value
            // The open playlist is held in the queue, where reordering and removal have already
            // happened; only for any other one is the database the truth.
            val tracks =
                if (id == current.activePlaylistId) current.queue.tracks else store.tracksIn(id)
            if (tracks.isEmpty()) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_playlist_empty))) }
                return@launch
            }
            val label = current.playlists.firstOrNull { it.id == id }?.name ?: "playlist"
            val name = label.replace(Regex("[^\\w -]"), "_")
            val bytes = withContext(Dispatchers.Default) {
                PlaylistFile.write(label, tracks).toByteArray()
            }
            val uri = remoteFiles.shareableCopy("$name.m3u8", bytes)
            if (uri == null) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_playlist_failed))) }
                return@launch
            }
            _share.tryEmit(
                Intent(Intent.ACTION_SEND).apply {
                    // The registered type for M3U. Other players offer to open it; anything else
                    // treats it as the text file it is.
                    type = "audio/x-mpegurl"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    /**
     * Reads a playlist file into a new playlist of its own.
     *
     * **A new one, never the current one.** An import that appended to whatever happened to be open
     * would be an edit nobody asked for, and undoing it means finding which rows were new.
     *
     * Each line gets two chances. Its recorded id first, which is exact and works on the device that
     * wrote the file — a backup restored after a reinstall is the common case and deserves to be
     * perfect. Failing that, a match on filename and size against the scanned library, which is how
     * a list written on another phone finds the same tunes here. A catalogue URL needs neither: it
     * means the same everywhere.
     */
    fun importPlaylist(uri: Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.toString(Charsets.UTF_8)
            }
            if (text.isNullOrBlank()) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_file_unreadable))) }
                return@launch
            }

            val entries = withContext(Dispatchers.Default) { PlaylistFile.read(text) }
            if (entries.isEmpty()) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_file_no_tracks))) }
                return@launch
            }

            val known = store.allTracks().associateBy { it.id }
            // An M3U is a text file anybody can write and nothing stops it naming the same tune
            // twice. `LibraryStore.replaceTracks` is where that is dealt with, for every writer at
            // once; the count reported below is of what was found, which is what the file offered.
            val found = entries.mapNotNull { entry -> resolve(entry, known) }

            // The file's own name first. A filename is a poor second: `labelOf` reads a *tree*
            // document id, and handed the document URI of a picked file it returned things like
            // "primary:Download/Favorites" -- an identifier, not a title. Kept as the fallback for
            // files written by other players, which carry no name at all.
            val name = PlaylistFile.nameIn(text)
                ?: MediaScanner.labelOf(uri).substringAfterLast('/').substringAfterLast(':')
                    .substringBeforeLast('.')
                    .ifBlank { "Imported" }
            val playlistId = store.createPlaylist(name)
            store.replaceTracks(playlistId, found)

            _state.update { it.copy(playlists = store.playlists()) }
            _state.update {
                it.copy(
                    message = Message(
                        if (found.size == entries.size) {
                            context.resources.getQuantityString(
                                R.plurals.notice_imported, found.size, found.size, name,
                            )
                        } else {
                            // Said, not swallowed. A playlist that silently arrived shorter than
                            // the file it came from is worse than one that explains itself.
                            context.getString(
                                R.string.notice_imported_partial, found.size, entries.size, name,
                            )
                        }
                    )
                )
            }
        }
    }

    private suspend fun resolve(entry: PlaylistFile.Entry, known: Map<String, TrackRef>): TrackRef? {
        // A catalogue URL, or an id this device issued: exact either way.
        entry.id?.let { id ->
            known[id]?.let { return it }
            if (id.startsWith("http") || Catalogue.owning(id) != null) {
                return TrackRef(
                    id = id,
                    title = entry.title,
                    subtitle = entry.location.substringBeforeLast('/', ""),
                    sizeBytes = entry.sizeBytes,
                    fileName = entry.fileName,
                    author = entry.author,
                )
            }
        }
        if (entry.location.startsWith("http")) {
            return TrackRef(
                id = entry.location,
                title = entry.title,
                subtitle = entry.location.substringBeforeLast('/', ""),
                sizeBytes = entry.sizeBytes,
                fileName = entry.fileName,
                author = entry.author,
            )
        }
        return libraryIndex.findByFile(entry.fileName, entry.sizeBytes)
    }

    // --- sharing ------------------------------------------------------------------------------

    /**
     * Sends the file itself.
     *
     * Works for anything playable, local or downloaded, because it fetches the bytes the same way
     * playing does — a track from a catalogue is fetched now if it is not already cached. These
     * formats are kilobytes, which is what makes sending one a reasonable thing to do at all.
     */
    fun shareFile(ref: TrackRef) {
        scope.launch {
            val bytes = loadBytes(ref)
            if (bytes == null) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_track_unreadable, ref.title))) }
                return@launch
            }
            val uri = remoteFiles.shareableCopy(ref.fileNameOrTitle, bytes)
            if (uri == null) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_share_failed, ref.title))) }
                return@launch
            }
            _share.tryEmit(
                Intent(Intent.ACTION_SEND).apply {
                    // Not audio/*: no chat app can play a .mod, and claiming an audio type invites
                    // the receiving end to try and fail. These are files, and octet-stream is what
                    // a file with no registered type is.
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, ref.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    /** The one tune being turned into audio; a second press while it runs says so instead. */
    private var sharingAudio: Job? = null

    /**
     * Sends the tune as sound -- an `.m4a` a chat app plays -- rather than as the file only this
     * kind of player can (`docs/BACKLOG.md` A62).
     *
     * **One subsong**: the one playing when [ref] is the current tune, else the one the file opens
     * at. **As long as the tune says** -- from the decoder or the length databases the player asks
     * -- when that is within the owner's setting; a longer tune, or one that states nothing, such
     * as a looping SID, is cut at the setting and fades (`AudioExport.plan`). Rendered by a decoder
     * of its own, beside whatever is playing, which goes on playing.
     */
    fun shareAsAudio(ref: TrackRef) {
        if (sharingAudio?.isActive == true) {
            say(Message(context.getString(R.string.notice_share_audio_busy)))
            return
        }
        sharingAudio = scope.launch {
            say(Message(context.getString(R.string.notice_share_audio_preparing, ref.title)))
            val bytes = loadBytes(ref)
            if (bytes == null) {
                say(Message(context.getString(R.string.notice_track_unreadable, ref.title)))
                return@launch
            }
            val companions = loadCompanions(ref)
            val snapshot = _state.value
            val playingSubsong = snapshot.subsong.takeIf { snapshot.current?.sameFileAs(ref) == true }
            val minutes = snapshot.shareAudioMinutes
            val uri = withContext(Dispatchers.Default) {
                runCatching { renderToM4a(ref, bytes, companions, playingSubsong, minutes) }
                    .onFailure { android.util.Log.w("Protracktor", "share as audio failed for ${ref.id}", it) }
                    .getOrNull()
            }
            if (uri == null) {
                say(Message(context.getString(R.string.notice_share_audio_failed, ref.title)))
                return@launch
            }
            say(Message(context.getString(R.string.notice_share_audio_ready, ref.title)))
            _share.tryEmit(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, ref.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    /** The rendering itself, off the main thread. Null when no decoder took the file. */
    private suspend fun renderToM4a(
        ref: TrackRef,
        bytes: ByteArray,
        companions: List<Pair<String, ByteArray>>,
        playingSubsong: Int?,
        limitMinutes: Int,
    ): Uri? {
        val (rendering, reason) = NativeEngine.openRendering(bytes, ref.fileNameOrTitle, companions)
        if (rendering == null) {
            android.util.Log.w("Protracktor", "share as audio: no decoder for ${ref.id}: $reason")
            return null
        }
        rendering.use { r ->
            if (playingSubsong != null && playingSubsong != r.currentSubsong()) r.selectSubsong(playingSubsong)
            val subsong = r.currentSubsong()

            // The same lengths, in the same order, the player asks when it opens a tune.
            val md5 = Md5.of(bytes)
            val hvsc = songLengths.forMd5(md5).orEmpty()
            val songdb = if (hvsc.any { it > 0.0 }) emptyList() else songDbLengths.forMd5(md5)
            val known = LengthSource.fill(LengthSource.known(hvsc, songdb), learnedLengths.forMd5(md5))
            val plan = AudioExport.plan(LengthSource.forSubsong(r.durationSeconds(), known, subsong), limitMinutes)

            val rate = r.preferredSampleRate().takeIf { it in 8_000..96_000 } ?: AudioExport.SAMPLE_RATE
            val total = (plan.seconds * rate).toLong()
            val fadeFrames = if (plan.fade) (AudioExport.FADE_SECONDS * rate).toLong() else 0L
            val file = remoteFiles.shareFile(AudioExport.fileName(ref.title, ref.author))
            val pcm = ShortArray(4096 * 2)
            var done = 0L
            // A half-written file is deleted rather than left for the next share's sweep: nothing
            // will ever send it, and it is megabytes.
            try { M4aWriter(file, rate).use { writer ->
                while (done < total) {
                    currentCoroutineContext().ensureActive()
                    val want = minOf(4096L, total - done).toInt()
                    // The native side renders as many frames as the array holds, so the last,
                    // shorter piece gets an array of its own size.
                    val chunk = if (want == pcm.size / 2) pcm else ShortArray(want * 2)
                    val got = r.render(rate, chunk)
                    if (got < 0) error("decoder failed at frame $done")
                    if (got == 0) break
                    if (fadeFrames > 0 && done + got > total - fadeFrames) {
                        for (i in 0 until got) {
                            val g = AudioExport.gainAt(done + i, total, fadeFrames)
                            chunk[2 * i] = (chunk[2 * i] * g).toInt().toShort()
                            chunk[2 * i + 1] = (chunk[2 * i + 1] * g).toInt().toShort()
                        }
                    }
                    writer.write(chunk, got)
                    done += got
                    if (got < want) break
                }
                writer.finish()
            } } catch (failure: Throwable) {
                file.delete()
                throw failure
            }
            if (done == 0L) {
                file.delete()
                return null
            }
            return remoteFiles.shareUri(file)
        }
    }

    /**
     * Sends a link instead of a file, so the other person fetches it themselves.
     *
     * **Catalogue tracks only**, and what is sent depends on what the catalogue publishes. Modland
     * serves every file over HTTP, so the link is the file. ASMA publishes one archive and no
     * per-file address at all, so the link is the collection and the path inside it — which is a
     * real thing to act on, and better than an `asma://` reference that means nothing off this
     * device. Deciding that was the point: the alternative was an action that looked like it
     * worked.
     */
    fun shareLink(ref: TrackRef) {
        val catalogue = Catalogue.owning(ref.id)
        val path = catalogue?.pathFrom(ref.id)
        if (catalogue == null || path == null) {
            _state.update {
                it.copy(message = Message(context.getString(R.string.notice_link_online_only)))
            }
            return
        }
        val text = catalogue.webUrlFor(path)
            ?: "${ref.title} — ${catalogue.displayName}, at $path — ${catalogue.homeUrl}"
        _share.tryEmit(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, ref.title)
            }
        )
    }

    /**
     * Hands the whole queue to a browser, as a link.
     *
     * **No server is involved in this at all**, which is the point of it (`docs/PLAN_HANDOFF.md` §3
     * H1). The queue becomes a few hundred characters in a URL fragment, sent by whatever channel
     * the user already has, and the page at the other end fetches the music from Modland directly.
     * A fragment never reaches a server, so even the page's own host does not learn what is on the
     * list.
     *
     * **It says what it could not send.** A local file's identity is a grant to one app on one
     * phone and means nothing in a browser, so those rows cannot travel — and a handoff that
     * silently shortens a playlist is the failure mode `docs/PLAN_WEB.md` §8 calls worse than
     * refusing outright.
     */
    /**
     * Sends the queue to a paired browser, or asks for a code if there is not one yet.
     *
     * **Scanning happens once**, not once per playlist: the address is remembered, and the page
     * keeps its room across reloads so an ordinary refresh does not break the pairing. When the
     * address stops answering — a different machine, a server that was stopped — this falls back to
     * the link, which needs nothing and always works (`docs/PLAN_HANDOFF.md` §3).
     */
    fun sendQueueToBrowser() {
        val tracks = _state.value.queue.tracks
        if (tracks.isEmpty()) {
            _state.update { it.copy(message = Message(context.getString(R.string.notice_nothing_to_send))) }
            return
        }
        val paired = Appearance.pairedEndpoint(context)
        if (paired == null) {
            _scan.tryEmit(Unit)
            return
        }
        postQueue(paired, tracks, remember = false)
    }

    /**
     * Uses a scanned code, and remembers it **only if it worked**.
     *
     * Storing it before trying keeps an address that may never have worked — a firewall still
     * closed, say — and every later press then uses a pairing that has never once succeeded, falls
     * back to the link, and leaves no way back to the scanner. A remembered pairing is a claim that
     * a browser is reachable, and the only evidence for it is having reached one.
     */
    fun pairWith(endpoint: String) {
        val tracks = _state.value.queue.tracks
        if (tracks.isEmpty()) {
            Appearance.rememberPairing(context, endpoint)
            _browse.update { it.copy(pairedBrowser = true, webPlayer = Appearance.webPlayer(context)) }
            _state.update { it.copy(message = Message(context.getString(R.string.notice_paired_empty))) }
            return
        }
        postQueue(endpoint, tracks, remember = true)
    }

    /** Stores an address typed in Settings, and publishes it so the field shows what was stored. */
    fun setWebPlayer(base: String) {
        Appearance.selectWebPlayer(context, base)
        _browse.update { it.copy(webPlayer = Appearance.webPlayer(context)) }
    }

    fun forgetPairing() {
        Appearance.rememberPairing(context, null)
        _browse.update { it.copy(pairedBrowser = false) }
        _state.update { it.copy(message = Message(context.getString(R.string.notice_pairing_forgotten))) }
    }

    /**
     * The bytes of the tracks a browser cannot fetch for itself.
     *
     * **This is the half `docs/PLAN_WEB.md` §8 said could not travel, travelling.** A local file's
     * identity is a grant to one app on one phone, so no URL can carry it — but the phone is
     * *present* at the moment of transfer and can simply hand over the file. That is the difference
     * between a live pairing and an account sync, and why the accountless design is the more
     * capable one.
     *
     * Budgeted, and what does not fit is reported rather than dropped.
     */
    private suspend fun localBytesFor(tracks: List<TrackRef>): Pair<Map<String, ByteArray>, Int> {
        val packed = mutableMapOf<String, ByteArray>()
        var left = 0
        var used = 0
        for (track in tracks) {
            // **An MP3 never travels**, by arithmetic: the budget for a whole queue is eight
            // megabytes and one four-minute recording is more than that. It is
            // marked instead, and arrives as a greyed row naming the file (`docs/BACKLOG.md` A29).
            if (QueueLink.isMp3(track)) continue
            // **Skipped only when the browser can fetch it for itself.** Modland and ASMA serve
            // every file over HTTP, so their rows travel as a URL and cost nothing here.
            // UnExoticA's `unexotica://`
            // means something on this phone and nothing anywhere else, so it travels as bytes or
            // arrives dead.
            val catalogue = Catalogue.owning(track.id)
            if (catalogue?.pathFrom(track.id)?.let(catalogue::fileUrlFor) != null) continue
            if (used >= WebRemote.LOCAL_BYTES_BUDGET) { left++; continue }
            val bytes = loadBytes(track)
            if (bytes == null || used + bytes.size > WebRemote.LOCAL_BYTES_BUDGET) { left++; continue }
            packed[track.id] = bytes
            used += bytes.size
        }
        return packed to left
    }

    /**
     * Sends a queue to an address, and reports what happened.
     *
     * @param remember whether a success should store this address. A scan asks for that; a send to
     * an address already stored does not need to re-store it.
     */
    private fun postQueue(endpoint: String, tracks: List<TrackRef>, remember: Boolean) {
        scope.launch {
            val index = _state.value.queue.currentIndex ?: 0
            val (localFiles, leftBehind) = localBytesFor(tracks)
            when (val outcome = WebRemote.send(endpoint, tracks, index, localFiles)) {
                is WebRemote.Outcome.Delivered -> {
                    if (remember) Appearance.rememberPairing(context, endpoint)
                    _browse.update {
                        it.copy(pairedBrowser = true, webPlayer = Appearance.webPlayer(context))
                    }
                    _state.update {
                        it.copy(
                            message = Message(
                                if (leftBehind == 0) {
                                    context.resources.getQuantityString(
                                        R.plurals.notice_sent_to_browser, tracks.size, tracks.size,
                                    )
                                } else {
                                    context.getString(
                                        R.string.notice_sent_some_too_big,
                                        tracks.size - leftBehind, leftBehind,
                                    )
                                }
                            )
                        )
                    }
                }
                // The address answered, so the pairing is sound and the page is simply closed. Kept
                // for the same reason: "open the page" and "scan again" are different instructions,
                // and forgetting here would send somebody back to the camera for nothing.
                is WebRemote.Outcome.NoOneListening -> {
                    if (remember) Appearance.rememberPairing(context, endpoint)
                    _browse.update {
                        it.copy(pairedBrowser = true, webPlayer = Appearance.webPlayer(context))
                    }
                    _state.update {
                        it.copy(message = Message(context.getString(R.string.notice_page_not_open)))
                    }
                }
                is WebRemote.Outcome.Unreachable -> {
                    // **Forgotten, so the next press opens the camera instead of failing again.**
                    // An address that cannot be reached is not a pairing, and a stored one with no
                    // way back to the scanner is a dead end.
                    Appearance.rememberPairing(context, null)
                    _browse.update { it.copy(pairedBrowser = false) }
                    _state.update {
                        it.copy(
                            message = Message(
                                context.getString(R.string.notice_browser_unreachable, outcome.reason)
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Opens the camera whatever is paired already.
     *
     * A hold is for "not the usual thing", and the usual thing here is sending to the browser
     * already known. What is not usual is **a different browser** — a tunnel that restarted under a
     * new name, a second machine, a page reopened somewhere else. Without this, re-pairing would
     * mean making a send fail first.
     */
    fun rescan() = _scan.tryEmit(Unit)

    /** The link, for when there is no camera or no reaching the browser. Offered by the scanner. */
    fun sendQueueAsLink() {
        val tracks = _state.value.queue.tracks
        if (tracks.isEmpty()) {
            _state.update { it.copy(message = Message(context.getString(R.string.notice_nothing_to_send))) }
            return
        }
        shareQueueAsLink(tracks)
    }

    private fun shareQueueAsLink(tracks: List<TrackRef>) {
        val packed = QueueLink.pack(tracks)
        if (packed.sent == 0) {
            _state.update {
                it.copy(
                    message = Message(context.getString(R.string.notice_link_none_sendable))
                )
            }
            return
        }
        val link = QueueLink.linkTo(Appearance.webPlayer(context), packed.fragment)
        // **Said only when a name was dropped too.** Since A28 the files on this phone travel as
        // greyed rows in their own places, so the list at the other end matches this one and there
        // is nothing to warn about; `left` now means the link was long enough that even their names
        // had to go, and then the numbering really does differ.
        if (packed.left > 0) {
            _state.update {
                it.copy(
                    message = Message(
                        context.getString(R.string.notice_link_truncated, packed.sent, packed.left)
                    )
                )
            }
        }
        _share.tryEmit(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
                putExtra(Intent.EXTRA_SUBJECT, "Protracktor queue")
            }
        )
    }

    /**
     * Share with Protracktor: one tune as a link that opens the web player playing it.
     *
     * Through the share sheet, like the queue's link, because where it goes is the person's choice
     * -- their own browser, a message to somebody else. It points at the page this phone knows
     * ([Appearance.webPlayer]), so it opens only where that address can be reached from.
     */
    fun sendToWeb(tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        val sendable = tracks.filter(QueueLink::canSend)
        val link = QueueLink.tracksLink(Appearance.webPlayer(context), tracks)
        if (link == null) {
            val one = tracks.singleOrNull()
            _state.update {
                it.copy(
                    message = Message(
                        when {
                            one != null && QueueLink.isMp3(one) ->
                                context.getString(R.string.notice_send_mp3)
                            one != null ->
                                context.getString(R.string.notice_send_one_local)
                            else ->
                                context.getString(R.string.notice_send_none)
                        }
                    )
                )
            }
            return
        }
        val subject = sendable.singleOrNull()?.let { "${it.title} — Protracktor web" }
            ?: "${sendable.size} tunes — Protracktor web"
        _share.tryEmit(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        )
        // Said only when some were left behind, and then it says how many: a link that quietly
        // carries four of six tunes is the failure `docs/PLAN_WEB.md` §8 calls worse than refusing.
        if (sendable.size < tracks.size) {
            _state.update {
                it.copy(
                    message = Message(
                        context.getString(
                            R.string.notice_send_partial,
                            sendable.size, tracks.size - sendable.size,
                        )
                    )
                )
            }
        }
    }

    /**
     * Opens Browse where a track came from: the author's folder in its catalogue.
     *
     * "Something played at random, it was good, what else did they write" -- and until now the only
     * way to ask was to remember the name and go and search for it.
     *
     * **Catalogue tracks only, and that is a real limit rather than an oversight.** A local file's
     * neighbours would be the directory it sits in, and the local browser does not list directories
     * at all: it lists a whole granted tree, flat. Jumping to "the folder" would mean building
     * directory-level browsing first, which is its own piece of work — `docs/WISHLIST.md` B2 says
     * so rather than leaving the action to do nothing on half the library.
     */
    fun showNeighboursOf(ref: TrackRef) {
        scope.launch {
            // Which catalogue it is from is asked of the catalogues, each of which recognises its
            // own references and no others.
            val from = Catalogue.owning(ref.id)
            if (from == null) {
                _state.update {
                    it.copy(message = Message(context.getString(R.string.notice_online_only_action)))
                }
                return@launch
            }

            // **A live-search catalogue has no author to go to.** The Mod Archive publishes no
            // index -- searching it is a request to their site, and the result rows carry a title,
            // a format and a module id and no artist at all. So there is nothing to look up and
            // nothing to search by. The action is hidden for these, and this is the backstop.
            if (from.isOnlineOnly) {
                _state.update {
                    it.copy(
                        message = Message(
                            context.getString(R.string.notice_no_author_live, from.displayName)
                        )
                    )
                }
                return@launch
            }

            // **Two failures, and they must not share a sentence.** A track that came from a
            // catalogue but is not in the index today -- deleted to save room, or added to a
            // playlist before an index was rebuilt -- is not the same as a track from no catalogue
            // at all: the fix here is to index, and the message has to say so. Where the jump goes
            // is read from the index, so there is nothing to do without one.
            val located = from.pathFrom(ref.id)?.let { path -> catalogues.locate(from.id, path) }
            if (located == null) {
                _state.update {
                    it.copy(
                        message = Message(
                            context.getString(R.string.notice_not_in_index, from.displayName)
                        )
                    )
                }
                return@launch
            }

            val summary = catalogues.summaries().firstOrNull { it.id == from.id } ?: return@launch
            // Read before Browse moves: the search this jump leaves, if it leaves one.
            val search = BrowseNavigation.searchToReturnTo(_browse.value)
            _browse.update {
                it.copy(
                    domain = BrowseDomain.ONLINE,
                    openCatalogue = summary,
                    openFormat = located.format,
                    openAuthor = located.author,
                    groups = emptyList(),
                    tracks = emptyList(),
                    loading = true,
                    arrivedByJump = true,
                )
            }
            _showBrowse.tryEmit(Unit)

            val found = catalogues.tracks(located.catalogueId, located.format, located.author)
                .map(::toTrackRef)
            _browse.update { it.copy(tracks = found, loading = false) }

            // **The transport follows the folder you walked into.** A digression leaves the dice's
            // tune playing, and next would otherwise roll another one while the author's list is on
            // screen. The queue is set here **without playing anything**, pointing at the tune the
            // jump was made from, so next and previous walk the author. The dice keeps its record
            // and its cursor and is still what Back returns to.
            if (_state.value.randomMode || _state.value.diceWaiting) {
                val at = found.indexOfFirst { it.sameFileAs(ref) }.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        resultsQueue = PlayQueue(tracks = found).startAt(at),
                        resultsFromHistory = false,
                        diceWaiting = true,
                    )
                }
            } else if (search != null) {
                // **The same for a search** (the owner, 2026-09-24). The results wait with their
                // words and scope, and the heading says whose folder this is. Only when a result is
                // what is playing does the transport move to the author -- a jump made while the
                // playlist plays is a look, and must not take the music somewhere else.
                val playingResults = _state.value.searchMode
                waitingSearch = WaitingSearch(search, _state.value.resultsQueue)
                val at = found.indexOfFirst { it.sameFileAs(ref) }.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        resultsQueue = if (playingResults) PlayQueue(tracks = found).startAt(at) else it.resultsQueue,
                        searchWaiting = true,
                    )
                }
            }
        }
    }

    // --- what has been played ------------------------------------------------------------------

    /**
     * Notes that a tune was played.
     *
     * Every play, wherever it came from -- the playlist, Random, a search result -- **except one
     * History itself started** (A56). Random is the reason this matters most — it is the only place that plays music nobody chose, and "what was
     * that" is a question you can only ask afterwards.
     */
    private fun recordPlayed(ref: TrackRef) {
        // A play History started leaves History as it is: no new time, no new place, no count
        // (A56, `HistoryRecording`).
        val now = _state.value
        if (!HistoryRecording.records(now.searchMode, now.resultsFromHistory)) return
        scope.launch {
            history.record(
                trackId = ref.id,
                title = ref.title,
                subtitle = ref.subtitle,
                fileName = ref.fileNameOrTitle,
                author = ref.author,
                sizeBytes = ref.sizeBytes,
            )
            // **Not refreshed while it is open** (A56). It used to be, so a row tapped in History
            // jumped to the top under the finger that tapped it. History shows what it showed when
            // it was opened, and the next visit reads it afresh.
        }
    }

    fun openHistory() {
        scope.launch {
            _browse.update { it.copy(domain = BrowseDomain.HISTORY, loading = true, tracks = emptyList()) }
            refreshHistory()
        }
    }

    private suspend fun refreshHistory() {
        val played = history.recent().map { entry ->
            TrackRef(
                id = entry.trackId,
                title = entry.title,
                // When, then where. "That tune two days ago" is the question history exists to
                // answer (`docs/ARCHITECTURE.md` §15); the ordering alone only says "before that
                // other one". Chosen over reordering the list on a replay (`docs/BACKLOG.md` A18).
                //
                // It borrows the source line rather than adding a third. Day headings might read
                // better still and are the open half of `docs/WISHLIST.md` B15; this is the part
                // that costs nothing.
                subtitle = listOf(whenPlayed(entry.playedAt), entry.subtitle)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                sizeBytes = entry.sizeBytes,
                fileName = entry.fileName,
                author = entry.author,
            )
        }
        // Also the `tracks` list, because that is what the add-to-playlist machinery reads and
        // there is no reason history should be the one list you cannot add from.
        _browse.update { it.copy(history = played, tracks = played, loading = false) }
    }

    /**
     * "yesterday, 21:14", in the user's language and clock format.
     *
     * `DateUtils` rather than a pattern of our own: it says "yesterday" where a date would read
     * worse, switches to a date once that stops being useful, and is translated by the system into
     * languages this project does not ship.
     */
    private fun whenPlayed(millis: Long): String =
        DateUtils.getRelativeDateTimeString(
            context,
            millis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0,
        ).toString()

    fun clearHistory() {
        scope.launch {
            history.clear()
            _browse.update { it.copy(history = emptyList(), tracks = emptyList()) }
            _state.update { it.copy(message = Message(context.getString(R.string.notice_history_cleared))) }
        }
    }

    // --- online catalogues --------------------------------------------------------------------

    /**
     * Also drops the platform counts, so the next screen that needs them recounts.
     *
     * Every path that changes what is indexed ends here — indexing a catalogue, deleting one, and
     * the storage screen's clearing. The counts are derived from exactly that, so surviving one of
     * these would leave a chip disabled after the index that would have lit it, for the rest of the
     * session.
     */
    fun refreshCatalogues() {
        scope.launch {
            // **What is here, before how much** (C75). Five yes-or-no questions that stop at the
            // first row or the first file, published at once; the counts below walk hundreds of
            // thousands of rows and queue behind the platform counts, and the ticks waited seconds
            // for them.
            val held = HeldSets(
                songLengths = songLengths.any(),
                trackMetadata = trackMetadata.any(),
                songDbLengths = songDbLengths.any(),
                replays = withContext(Dispatchers.IO) { Sc68Replays.count(context) > 0 },
                players = withContext(Dispatchers.IO) { UadePlayers.present(context) },
            )
            _browse.update { it.copy(held = held) }

            // Recounted rather than kept: this runs whenever what is indexed has changed, and the
            // platform counts are derived from exactly that.
            refreshPlatformCounts()
            val summaries = catalogues.summaries()
            val lengths = songLengths.count()
            val metadataRows = trackMetadata.count()
            val lengthRows = songDbLengths.count()
            // Counted here, with the index, and not where it is downloaded: it is a fact about the
            // list *and* the Modland index together, so indexing Modland changes it as surely as
            // downloading the list does.
            val favouriteRows = favourites.playableCount()
            val favouriteRowsListed = favourites.count()
            val paired = Appearance.pairedEndpoint(context) != null
            val storage = withContext(Dispatchers.IO) {
                remoteFiles.cacheBytes() to remoteFiles.permanentBytes()
            }
            val archives = withContext(Dispatchers.IO) {
                Catalogue.all.associate { it.id to remoteFiles.archiveBytes(it.id) }
                    .filterValues { it > 0 }
            }
            // Measured as a file, because that is the only honest measurement available. Only an
            // archive catalogue is a file of its own; Modland's index is rows in this database,
            // sharing its pages, its indexes and its free list with the playlists and the history.
            // Splitting that number between catalogues would be a guess presented as a size.
            val database = withContext(Dispatchers.IO) {
                context.getDatabasePath(SchemaSql.NAME).let { if (it.isFile) it.length() else 0L }
            }
            val replays = withContext(Dispatchers.IO) {
                Sc68Replays.count(context) to Sc68Replays.bytes(context)
            }
            val players = withContext(Dispatchers.IO) {
                UadePlayers.count(context) to UadePlayers.bytes(context)
            }
            _browse.update { current ->
                current.copy(
                    catalogues = summaries,
                    songLengthCount = lengths,
                    trackMetadataCount = metadataRows,
                    songDbLengthCount = lengthRows,
                    favouriteCount = favouriteRows,
                    favouritesListed = favouriteRowsListed,
                    pairedBrowser = paired,
                    storageBytes = storage,
                    archiveBytes = archives,
                    databaseBytes = database,
                    replayCount = replays.first,
                    replayBytes = replays.second,
                    playerCount = players.first,
                    playerBytes = players.second,
                    heldCountsKnown = true,
                    backends = NativeEngine.backendsFingerprint(),
                )
            }
        }
    }

    // --- giving the disk back -----------------------------------------------------------------

    /**
     * Everything here deletes something the app can fetch again.
     *
     * That is the rule, not a coincidence: the storage screen may only offer to delete what is a
     * copy. The user's playlists, their granted folders and their history are not on it, and the
     * one thing that looks like a copy but is not — a local library index, which is a record of
     * files only that phone has seen — is only ever *rebuilt*, never dropped.
     *
     * Each of these ends in `refreshCatalogues()` so the numbers the user is looking at become
     * true immediately. A delete that leaves the old size on screen reads as a delete that failed.
     */
    fun clearFetchedCache() {
        scope.launch {
            val freed = withContext(backgroundWork) {
                val before = remoteFiles.cacheBytes()
                remoteFiles.clearCache()
                before - remoteFiles.cacheBytes()
            }
            // ASMA's tunes are still here; what went was the cache.
            refreshOnPhone()
            _state.update { it.copy(message = Message(freedMessage(freed))) }
            refreshCatalogues()
        }
    }

    /**
     * Throws away one catalogue's index.
     *
     * **Playback is deliberately left alone.** A queued catalogue track carries its own id, and
     * the URL to fetch it is derived
     * from that id by `Catalogue.urlFor` rather than read from `catalogue_tracks` — so a track that
     * is playing keeps playing, and one further down the playlist still plays when it is reached.
     * What actually degrades is the format label (`catalogueFormatOf` finds nothing and returns
     * null) and "more from this author". Both are cosmetic and both come back with the index.
     *
     * **An archive catalogue's index and its archive are one thing**, so this deletes both. ASMA
     * publishes a single zip that `indexCatalogue` stores whole and parses in place; removing the
     * removing the rows and keeping the zip would leave a catalogue reporting its full track
     * count, browsing normally and playing nothing.
     */
    fun deleteCatalogueIndex(catalogueId: String) {
        // **The row answers at once** (`docs/STATUS.md` C76). Deleting Modland is half a million
        // rows and their indexes -- 5.1 s on a desktop, measured, and 10 to 15 on a phone -- and
        // for all of it the confirmed delete looked like a press that had done nothing. So the
        // screen is told first: the catalogue shows as not indexed, its storage row goes, and its
        // Browse row turns into "deleting…", which also refuses a new download until this is done.
        if (!beginDownload(catalogueId, context.getString(R.string.browse_deleting_short))) return
        // **Said now, with the row going, not when the database has finished** (C77). Several
        // deletes in a row queue on the one database and used to finish -- and speak -- almost
        // together, seconds after their rows had gone. The size is known before anything is
        // deleted, so the message can be the true one. Only a failure speaks again later.
        val displayName = _browse.value.catalogues.firstOrNull { it.id == catalogueId }?.displayName ?: catalogueId
        val archived = _browse.value.archiveBytes[catalogueId] ?: 0L
        say(Message(if (archived > 0L) freedMessage(archived) else context.getString(R.string.notice_index_deleted)))
        _browse.update { current ->
            current.copy(
                deleting = current.deleting + catalogueId,
                catalogues = current.catalogues.map {
                    if (it.id == catalogueId) it.copy(trackCount = 0, indexedAt = null, archiveCount = 0) else it
                },
                archiveBytes = current.archiveBytes - catalogueId,
            )
        }
        scope.launch {
            try {
                runCatching { catalogues.clearIndex(catalogueId) }.onFailure {
                    say(Message(context.getString(R.string.notice_delete_failed, displayName)))
                    return@launch
                }
                if (catalogueId == Modland.id) dropFavourites()
                withContext(backgroundWork) {
                    if (Catalogue.byId(catalogueId)?.isArchive == true) remoteFiles.deleteArchive(catalogueId)
                }
                // Rows of that archive on screen are no longer on the phone.
                refreshOnPhone()
                _browse.update { current ->
                    current.copy(
                        // A deleted catalogue drops out of an online scope that named it. Left in, the
                        // label would go on claiming a source that no longer exists.
                        searchScope = (current.searchScope as? SearchScope.Online)
                            ?.let { SearchScope.Online(it.catalogueIds - catalogueId) }
                            ?: current.searchScope,
                        openCatalogue = current.openCatalogue?.takeIf { it.id != catalogueId },
                    )
                }
                refreshCatalogues()
            } finally {
                withContext(NonCancellable) {
                    endDownload(catalogueId)
                    _browse.update { it.copy(deleting = it.deleting - catalogueId) }
                }
            }
        }
    }

    /**
     * Modland's favourites go with Modland's index: they are Modland paths, meaningless without it.
     * Random leaves that scope, since a scope whose contents are gone would draw nothing -- and the
     * chip that set it is then disabled, leaving no way back but a restart.
     */
    private suspend fun dropFavourites() {
        favourites.clear()
        if (_browse.value.randomScope is RandomScope.Favourites) setRandomScope(RandomScope.Everything)
    }

    /** The wording for what [CacheBudget.describeFreed] worked out. */
    private fun freedMessage(bytes: Long): String =
        when (val freed = CacheBudget.describeFreed(bytes)) {
            CacheBudget.Freed.NOTHING -> context.getString(R.string.notice_freed_nothing)
            CacheBudget.Freed.LESS_THAN_A_MEGABYTE -> context.getString(R.string.notice_freed_under_a_megabyte)
            is CacheBudget.Freed.Megabytes -> context.getString(R.string.notice_freed_megabytes, freed.count)
        }

    /**
     * Marks [key] as downloading, or answers false if it already was.
     *
     * **The guard is the point.** Every download here is an independent coroutine, so without it
     * a second tap on the same row starts a second identical download, and a tap on a *different*
     * row blanks the first one's label. Neither cancels anything, though from outside both look
     * like cancellation. One map, one entry per download, and a row that can ask about itself.
     */
    /** What one step of [downloadEverything] did: whether it landed, and what it would have said. */
    private data class Fetched(val ok: Boolean, val message: Message)

    /** Puts a notice in front of the user. The single-item downloads all end this way. */
    private fun say(message: Message) {
        _state.update { it.copy(message = message) }
    }

    private fun beginDownload(key: String, label: String): Boolean {
        if (_browse.value.indexing.containsKey(key)) return false
        _browse.update { it.copy(indexing = it.indexing + (key to label)) }
        return true
    }

    private fun endDownload(key: String) {
        _browse.update { it.copy(indexing = it.indexing - key) }
    }

    /**
     * Downloads a catalogue's index and stores it.
     *
     * Only entries whose filename a backend might handle are kept. Modland lists about half a
     * million files across formats nothing here can play yet; indexing them all would cost minutes
     * and disk to browse a list of tracks that cannot be opened. Re-run this after adding a backend
     * -- noted in docs/BACKLOG.md so it is not discovered by wondering where the SIDs went.
     */
    fun indexCatalogue(id: String) {
        val catalogue = Catalogue.byId(id) ?: return
        scope.launch {
            val fetched = fetchCatalogue(catalogue) ?: return@launch
            // **Modland brings its favourites** (decided 2026-09-21): Modland's own list of Modland
            // paths, one idea with the archive. Their failure is said only if the index landed.
            if (catalogue.id == Modland.id && fetched.ok) {
                val list = fetchFavourites()
                if (list != null && !list.ok) {
                    say(list.message)
                    return@launch
                }
            }
            say(fetched.message)
        }
    }

    /**
     * One catalogue, fetched and indexed, reporting what happened rather than announcing it.
     *
     * Split from [indexCatalogue] so that [downloadEverything] can run it as one step of several:
     * a combined download must not raise six notices, and it has to know which steps landed.
     * Returns null when this catalogue is already downloading, which is not a failure.
     */
    private suspend fun fetchCatalogue(catalogue: Catalogue): Fetched? {
        if (!beginDownload(catalogue.id, catalogue.displayName)) return null
        run {
            val bytes = remoteFiles.fetchIndex(catalogue.indexUrl)
            if (bytes == null) {
                endDownload(catalogue.id)
                return Fetched(false, Message(context.getString(R.string.notice_index_download_failed, catalogue.displayName)))
            }
            // An archive catalogue's "index" IS the archive, so it is kept rather than parsed and
            // discarded -- afterwards both browsing and playing work with no network at all.
            if (catalogue.isArchive && !remoteFiles.storeArchive(catalogue.id, bytes)) {
                endDownload(catalogue.id)
                return Fetched(
                    false,
                    Message(context.getString(R.string.notice_archive_store_failed, catalogue.displayName)),
                )
            }

            val entries = withContext(Dispatchers.Default) {
                // **Everything the archive lists, and nothing decided here**
                // (`docs/ROADMAP_FORMATS.md` step 0). What this build can play is written beside
                // each row as it is stored and re-decided locally when the format list changes, so
                // the index is no longer a function of the decoders — and adding a format no longer
                // costs every user the whole 40 MB again.
                catalogue.parseIndex(bytes)
            }
            val playable =
                catalogues.replaceIndex(catalogue, entries, NativeEngine.backendsFingerprint())
            endDownload(catalogue.id)
            _browse.update { it.copy(catalogues = catalogues.summaries()) }
            // **Both numbers, because there are now two** (`docs/ROADMAP_FORMATS.md` step 0).
            // The index keeps everything the archive lists and the app offers what it can open, so
            // saying only the first makes the count on the catalogue's own row look wrong -- a
            // snackbar saying 500,000-odd over a row saying 341,842.
            return Fetched(
                true,
                Message(
                    if (playable >= entries.size) {
                        context.resources.getQuantityString(
                            R.plurals.notice_indexed_all, entries.size, entries.size,
                            catalogue.displayName,
                        )
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.notice_indexed_partly, entries.size, entries.size,
                            catalogue.displayName, playable,
                        )
                    }
                ),
            )
        }
    }

    /**
     * HVSC's SID lengths and songdb's credits and lengths, as one press with one message.
     *
     * **One button, because nobody should need to know which database says what** about a file.
     * HVSC's lengths rode with Modland until now; they answer "how long is this SID" about tunes
     * from anywhere, a granted folder included, so they belong with the other facts about files
     * rather than with one catalogue (decided 2026-09-21).
     */
    fun downloadSongMetadata() {
        if (!beginDownload(DownloadKeys.SONG_METADATA, context.getString(R.string.song_metadata_title))) return
        scope.launch {
            try {
                val results = listOfNotNull(fetchSongLengths(), fetchTrackMetadata())
                val failed = results.firstOrNull { !it.ok }
                say(failed?.message ?: Message(context.getString(R.string.notice_song_metadata_done)))
            } finally {
                withContext(NonCancellable) {
                    endDownload(DownloadKeys.SONG_METADATA)
                    refreshCatalogues()
                }
            }
        }
    }

    /**
     * Throws away every downloaded fact about files: HVSC's lengths, songdb's credits and lengths.
     *
     * **What this phone learnt by playing stays** (`learned_lengths`, A50): it was not downloaded,
     * cannot be downloaded again, and costs next to nothing. The button deletes what it fetched.
     *
     * The row goes and the message comes at the press; the tables empty behind them (C77).
     */
    fun deleteSongMetadata() {
        say(Message(context.getString(R.string.notice_song_metadata_deleted)))
        _browse.update {
            it.copy(
                held = it.held?.copy(songLengths = false, trackMetadata = false, songDbLengths = false),
                songLengthCount = 0,
                trackMetadataCount = 0,
                songDbLengthCount = 0,
            )
        }
        scope.launch {
            runCatching {
                songLengths.clear()
                trackMetadata.clear()
                songDbLengths.clear()
            }.onFailure {
                say(Message(context.getString(R.string.notice_delete_failed, context.getString(R.string.song_metadata_title))))
            }
            refreshCatalogues()
        }
    }

    /** HVSC's lengths, as one step. See [fetchCatalogue] for why the steps are shaped like this. */
    private suspend fun fetchSongLengths(): Fetched? {
        if (!beginDownload(DownloadKeys.SONG_LENGTHS, SONG_LENGTHS_LABEL)) return null
        val bytes = remoteFiles.fetchIndex(SONG_LENGTHS_URL)
        if (bytes == null) {
            endDownload(DownloadKeys.SONG_LENGTHS)
            return Fetched(false, Message(context.getString(R.string.notice_song_lengths_failed)))
        }
        val entries = withContext(Dispatchers.Default) {
            SongLengths.parse(bytes.toString(Charsets.ISO_8859_1))
        }
        if (entries.isEmpty()) {
            endDownload(DownloadKeys.SONG_LENGTHS)
            return Fetched(false, Message(context.getString(R.string.notice_song_lengths_empty)))
        }
        songLengths.replaceAll(entries)
        endDownload(DownloadKeys.SONG_LENGTHS)
        _browse.update { it.copy(songLengthCount = entries.size) }
        return Fetched(
            true,
            Message(
                context.resources.getQuantityString(
                    R.plurals.notice_song_lengths_done, entries.size, entries.size,
                )
            ),
        )
    }

    /** The songdb table, as one step. See [fetchCatalogue]. */
    private suspend fun fetchTrackMetadata(): Fetched? {
        if (!beginDownload(DownloadKeys.TRACK_METADATA, TRACK_METADATA_LABEL)) return null
        run {
            val bytes = remoteFiles.fetchIndex(TRACK_METADATA_URL)
            if (bytes == null) {
                endDownload(DownloadKeys.TRACK_METADATA)
                return Fetched(false, Message(context.getString(R.string.notice_track_metadata_failed)))
            }
            // Parsed straight into the table rather than into a list first. Fifteen megabytes of
            // this becomes 1.9 million strings, and holding them alongside the download peaks near
            // 150 MB -- fine on the JVM these tests run on, an out-of-memory crash on a phone.
            val written = trackMetadata.replaceAllFrom(bytes)
            if (written == 0) {
                endDownload(DownloadKeys.TRACK_METADATA)
                return Fetched(false, Message(context.getString(R.string.notice_track_metadata_empty)))
            }
            // The lengths, second and on the same tick (D1). A failure here does not undo the
            // metadata that did arrive: the two are useful apart, and the message says which half
            // is missing rather than calling the whole download a failure.
            val lengthBytes = remoteFiles.fetchIndex(SONGDB_LENGTHS_URL)
            val lengths = if (lengthBytes == null) 0 else songDbLengths.replaceAllFrom(lengthBytes)
            endDownload(DownloadKeys.TRACK_METADATA)
            _browse.update { it.copy(trackMetadataCount = written, songDbLengthCount = lengths) }
            if (lengths == 0) {
                return Fetched(false, Message(context.getString(R.string.notice_songdb_lengths_failed)))
            }
            return Fetched(
                true,
                Message(
                    context.resources.getQuantityString(
                        R.plurals.notice_track_metadata_done, written, written,
                    )
                ),
            )
        }
    }

    /** Recounts the favourites. Cheap: two `COUNT`s over about a thousand rows. */
    private fun refreshFavouriteCount() {
        scope.launch {
            val playable = favourites.playableCount()
            val listed = favourites.count()
            _browse.update { it.copy(favouriteCount = playable, favouritesListed = listed) }
        }
    }

    /**
     * Plays a file another app handed us — a tap in a file manager, a share, a link.
     *
     * **The playing half was already here**: `loadBytes` reads a `content://` through the resolver
     * and an `http(s)` through the network, and `playTransient` plays something that is in no
     * playlist. All this adds is a name and a flag (`docs/WISHLIST.md` B24).
     *
     * The name matters more than it looks. Every backend that identifies a file by extension is
     * handed [TrackRef.fileNameOrTitle], and a `content://` URI usually has no extension anywhere
     * in it — so without asking the provider for `DISPLAY_NAME` a perfectly good `.sndh` arrives as
     * a nameless blob and the four backends that need the name to choose a loader all decline it.
     *
     * **The read grant lives as long as this intent.** The bytes are read immediately, which is
     * fine, but "try again" on a failure later may find the grant gone -- one of the few places in
     * this app where retrying is genuinely not the same operation.
     */
    fun playExternal(uri: Uri, suppliedName: String? = null) {
        scope.launch {
            val name = suppliedName?.takeIf { it.isNotBlank() }
                ?: withContext(Dispatchers.IO) { displayNameOf(uri) }
            playTransient(
                TrackRef(
                    id = uri.toString(),
                    title = name,
                    subtitle = "",
                    fileName = name,
                ),
                external = true,
            )
        }
    }

    /**
     * A link to the page at its permanent address, opened here instead (`docs/BACKLOG.md` A40).
     *
     * **Played, not filed**, the way the page plays a tune sent to it: the tunes become what next and
     * previous walk, as a search's results do, and no playlist is written. A queue link is treated
     * the same way -- on the page it replaces "From the phone", which the app has no counterpart of,
     * and a list somebody sent is still something to hear before deciding to keep.
     *
     * **Never silent**: a link that cannot be read, or that holds nothing playable, says so.
     */
    fun openLink(url: String) {
        val opened = QueueLink.open(url)
        if (opened == null || opened.tracks.isEmpty()) {
            _state.update {
                it.copy(message = Message(context.getString(
                    if (opened == null) R.string.notice_link_unreadable else R.string.notice_link_nothing_playable,
                )))
            }
            return
        }
        playFromResults(opened.tracks, 0)
        val count = opened.tracks.size
        val said = context.resources.getQuantityString(R.plurals.notice_link_opened, count, count) +
            if (opened.stayed > 0) " " + context.resources.getQuantityString(R.plurals.notice_link_stayed, opened.stayed, opened.stayed) else ""
        _state.update { it.copy(message = Message(said)) }
    }

    /**
     * What to call a file somebody handed us.
     *
     * `OpenableColumns.DISPLAY_NAME` first, because a document provider knows the real name and the
     * URI often does not carry it. Falling back to the last path segment covers `file://` and every
     * `http(s)` link.
     */
    private fun displayNameOf(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { row ->
                    val column = row.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && row.moveToFirst()) return row.getString(column).orEmpty()
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    /**
     * Modland's favourites on their own. **Not a row any more** -- they come with Modland -- but
     * the Random scope sheet still offers them when the favourites scope is empty: a phone that
     * indexed Modland before the two travelled together has the index and not the list, and a
     * whole Modland re-download to fix that would be six megabytes for 142 KB.
     */
    fun downloadFavourites() {
        scope.launch { fetchFavourites()?.let { say(it.message) } }
    }

    /** Modland's favourites, as one step. See [fetchCatalogue]. */
    private suspend fun fetchFavourites(): Fetched? {
        if (!beginDownload(DownloadKeys.FAVOURITES, FAVOURITES_LABEL)) return null
        val bytes = remoteFiles.fetchIndex(FAVOURITES_URL)
        if (bytes == null) {
            endDownload(DownloadKeys.FAVOURITES)
            return Fetched(false, Message(context.getString(R.string.notice_favourites_failed)))
        }
        val written = favourites.replaceAllFrom(bytes)
        if (written == 0) {
            endDownload(DownloadKeys.FAVOURITES)
            return Fetched(false, Message(context.getString(R.string.notice_favourites_empty)))
        }
        val playable = favourites.playableCount()
        endDownload(DownloadKeys.FAVOURITES)
        _browse.update {
            it.copy(favouriteCount = playable, favouritesListed = written)
        }
        return Fetched(
            true,
            Message(
                if (playable > 0) {
                    context.getString(R.string.notice_favourites_playable, playable, written)
                } else {
                    // The list arrived and reaches nothing. Said plainly, because the alternative
                    // is a Favourites chip that stays disabled after a download that reported
                    // success.
                    context.getString(R.string.notice_favourites_need_index, written)
                }
            ),
        )
    }

    /**
     * Everything an empty install needs, in one press.
     *
     * **Why a button and not a background job at first launch.** It is 46 MB (measured 2026-09-17:
     * Modland 5.5, ASMA 19.2, UnExoticA 1.7, HVSC 5.0, songdb 14.1, favourites 0.1), and an app
     * that spends that on somebody's mobile data four seconds after it is installed has taken a
     * decision that was not its to take. Pressed, with the size on the button, it is the user's
     * decision and `Data safety`'s "fetched on demand" stays true.
     *
     * **Sequential, and the order is the point.** Modland is the smallest useful thing here and the
     * largest catalogue, so somebody who gives up after twenty seconds already has half a million
     * tracks to browse; the 14 MB metadata table, which only improves what is written under a
     * title, goes last. In parallel they would fight for the same connection and write to the same
     * database, and nothing would finish sooner.
     *
     * **Each step keeps its own row spinner** (its own [DownloadKeys] entry), so the screen shows
     * what is happening rather than one opaque bar. A step already running is skipped rather than
     * started twice, which is what makes pressing this while one catalogue downloads harmless.
     *
     * **Partial success is reported as partial.** Six downloads from five hosts will not all land
     * every time, and "done" over a failed ASMA would be a lie the user finds out about later.
     */
    fun downloadSelected(ids: Set<String>) {
        val steps = DownloadPlan.stepsFor(ids)
        if (steps.isEmpty()) return
        if (!beginDownload(DownloadKeys.EVERYTHING, context.getString(R.string.download_all_running))) return
        var landed = 0
        var failed = 0
        selectedDownloads = scope.launch {
            try {
                for (step in steps) {
                    val done = runDownloadStep(step) ?: continue
                    if (done.ok) landed++ else failed++
                }
                say(
                    Message(
                        if (failed == 0) {
                            context.getString(R.string.notice_all_downloaded)
                        } else {
                            context.getString(R.string.notice_all_downloaded_partly, landed, failed)
                        }
                    )
                )
            } finally {
                // **`NonCancellable`, or stopping leaves the screen mid-download for ever.** The
                // spinners are state, not a side effect of the coroutine: cancelled without this,
                // every row the run had reached keeps spinning until the app is restarted, and the
                // offer cannot be pressed again because its own key is still there.
                withContext(NonCancellable) {
                    steps.forEach { endDownload(it) }
                    endDownload(DownloadKeys.EVERYTHING)
                    selectedDownloads = null
                    refreshCatalogues()
                    refreshPlatformCounts()
                    if (!isActive) {
                        say(Message(context.getString(R.string.notice_downloads_stopped, landed)))
                    }
                }
            }
        }
    }

    /** Everything a fresh install needs, which is every box ticked. */
    fun downloadEverything() = downloadSelected(DownloadPlan.choices().toSet())

    /**
     * Stops a run of downloads.
     *
     * **What is already on this phone stays.** Each step writes its own table when it finishes, so
     * stopping after Modland leaves Modland indexed and the rest untouched -- which is the whole
     * reason the steps are sequential and separate rather than one transaction. What is lost is at
     * most the file being fetched at the moment of the press.
     *
     * A blocking read cannot always be interrupted where it stands, so the step in flight may run
     * to its end; nothing after it will start.
     */
    fun cancelDownloads() {
        selectedDownloads?.cancel()
    }

    /** One step of [downloadSelected], by the key `DownloadPlan` named it with. */
    private suspend fun runDownloadStep(step: String): Fetched? = when (step) {
        DownloadKeys.SONG_LENGTHS -> fetchSongLengths()
        DownloadKeys.FAVOURITES -> fetchFavourites()
        DownloadKeys.TRACK_METADATA -> fetchTrackMetadata()
        else -> Catalogue.byId(step)?.let { fetchCatalogue(it) }
    }

    /**
     * sc68's replay routines as one step: how many arrived, or null when none did. Progress shows
     * under its own key; the message is the caller's to give. `docs/LICENSES.md` has why they are
     * fetched rather than shipped.
     */
    private suspend fun fetchReplays(): Int? {
        if (!beginDownload(DownloadKeys.REPLAYS, REPLAYS_LABEL)) return null
        try {
            val fetched = Sc68Replays.download(context) { done, total ->
                _browse.update {
                    it.copy(indexing = it.indexing + (DownloadKeys.REPLAYS to "$REPLAYS_LABEL $done/$total"))
                }
            }
            if (fetched == null || fetched == 0) return null
            // Put them where sc68 will look, now rather than at the next update. It is handed a
            // path once and reads what is under it each time it opens a tune, so this is enough.
            withContext(backgroundWork) { NativeData.adoptDownloadedReplays(context) }
            return fetched
        } finally {
            endDownload(DownloadKeys.REPLAYS)
        }
    }

    /**
     * Tells the engine where UADE's three paths are.
     *
     * Called at start-up and again after a download, because until the replay routines are there
     * the backend answers "not set up" and the Amiga formats are simply absent — asked afresh on
     * every open, so this is enough and no restart is needed.
     */
    private fun configureUade(version: String) {
        runCatching {
            val base = NativeData.ensureUadeUnpacked(context, version)
            NativeEngine.setUadePaths(
                NativeData.uadeCore(context).absolutePath,
                base.absolutePath,
                NativeData.uadeScratch(context).absolutePath,
            )
        }
    }

    /** UADE's replay routines as one step: how many arrived, or null when none did. */
    private suspend fun fetchPlayers(): Int? {
        if (!beginDownload(DownloadKeys.PLAYERS, PLAYERS_LABEL)) return null
        try {
            val fetched = UadePlayers.download(context) { done, total ->
                _browse.update {
                    it.copy(indexing = it.indexing + (DownloadKeys.PLAYERS to "$PLAYERS_LABEL $done/$total"))
                }
            }
            if (fetched == null || fetched == 0) return null
            withContext(backgroundWork) {
                val version = context.packageManager
                    .getPackageInfo(context.packageName, 0).longVersionCode.toString()
                configureUade(version)
            }
            return fetched
        } finally {
            endDownload(DownloadKeys.PLAYERS)
        }
    }

    /**
     * Both sets of replay routines, sc68's and UADE's, as one press with one message.
     *
     * **One button, because nobody should need to know which emulator a format runs on**
     * (decided 2026-09-21). The note beside it still says what these are -- other people's code,
     * fetched from the projects that publish it (`docs/LICENSES.md`).
     */
    fun downloadReplayRoutines() {
        if (!beginDownload(DownloadKeys.REPLAY_ROUTINES, context.getString(R.string.replay_routines_title))) return
        scope.launch {
            try {
                val sc68 = fetchReplays()
                val uade = fetchPlayers()
                say(
                    Message(
                        when {
                            sc68 != null && uade != null ->
                                context.resources.getQuantityString(
                                    R.plurals.notice_replay_routines_done, sc68 + uade, sc68 + uade,
                                )
                            sc68 == null && uade == null ->
                                context.getString(R.string.notice_replay_routines_failed)
                            else -> context.getString(R.string.notice_replay_routines_partly)
                        }
                    )
                )
            } finally {
                withContext(NonCancellable) {
                    endDownload(DownloadKeys.REPLAY_ROUTINES)
                    refreshCatalogues()
                }
            }
        }
    }

    /**
     * Throws both sets of replay routines away: `.sc68` goes back to mostly silent and the Amiga
     * custom formats stop playing; SNDH does not care. One delete, as there is one download.
     *
     * The size is known before anything goes, so the true message is said at the press (C77).
     */
    fun deleteReplayRoutines() {
        val now = _browse.value
        say(Message(freedMessage(now.replayBytes + now.playerBytes)))
        _browse.update {
            it.copy(
                held = it.held?.copy(replays = false, players = false),
                replayCount = 0,
                replayBytes = 0L,
                playerCount = 0,
                playerBytes = 0L,
            )
        }
        scope.launch {
            val removed = withContext(backgroundWork) {
                val sc68 = Sc68Replays.delete(context)
                val uade = UadePlayers.delete(context)
                // The copies UADE actually reads go too, or it would go on playing from them with
                // nothing on the storage screen saying they were there.
                java.io.File(context.filesDir, "uade").deleteRecursively()
                sc68 && uade
            }
            if (!removed) {
                say(Message(context.getString(R.string.notice_delete_failed, context.getString(R.string.replay_routines_title))))
            }
            val version = context.packageManager
                .getPackageInfo(context.packageName, 0).longVersionCode.toString()
            withContext(backgroundWork) { configureUade(version) }
            refreshCatalogues()
        }
    }

    fun openCatalogue(summary: CatalogueSummary) {
        scope.launch {
            _browse.update {
                it.copy(
                    openCatalogue = summary, openFormat = null, openAuthor = null,
                    loading = true, tracks = emptyList(), arrivedByJump = false,
                )
            }
            val formats = catalogues.formats(summary.id)
            _browse.update { it.copy(groups = formats, loading = false) }
        }
    }

    fun openFormat(format: String) {
        val catalogueId = _browse.value.openCatalogue?.id ?: return
        scope.launch {
            _browse.update {
                it.copy(
                    openFormat = format, openAuthor = null, loading = true,
                    tracks = emptyList(), arrivedByJump = false,
                )
            }
            val authors = catalogues.authors(catalogueId, format)
            _browse.update { it.copy(groups = authors, loading = false) }
        }
    }

    fun openAuthor(author: String) {
        val current = _browse.value
        val catalogueId = current.openCatalogue?.id ?: return
        val format = current.openFormat ?: return
        scope.launch {
            _browse.update {
                it.copy(openAuthor = author, loading = true, tracks = emptyList(), arrivedByJump = false)
            }
            val found = catalogues.tracks(catalogueId, format, author).map(::toTrackRef)
            _browse.update { it.copy(tracks = found, loading = false) }
        }
    }

    /**
     * Opens a Random session: a fresh record, and a tune playing without a second press.
     *
     * **A new list every time.** What this throws away is the record, not the tunes — everything
     * that played is in History.
     *
     * Separate from [playRandom] because the dice button inside a running session means "re-roll,
     * keeping what I have heard", and entering the view means "start".
     */
    fun openRandom() {
        scope.launch {
            randomSession++
            randomHistory.clear()
            randomCursor = -1
            randomPlayed = -1
            failedRandomPicks = 0
            _state.update {
                it.copy(
                    randomPicks = emptyList(),
                    randomIndex = -1,
                    randomExhausted = false,
                    diceWaiting = false,
                    searchWaiting = false,
                    // Whatever was the source before this is not the source now. The list Browse
                    // was played from stays on screen there, but it has stopped driving playback,
                    // and leaving it in the state left two answers to "what is playing".
                    resultsQueue = null,
                    externalOpen = false,
                )
            }
            advanceRandom()
        }
    }

    /**
     * Plays one of the picks already made, chosen by hand from the list.
     *
     * Only backwards into the record — [randomPlayed] is the edge of what has been heard, and the
     * picks past it are speculation nobody has been shown.
     */
    fun playRandomAt(index: Int) {
        if (index < 0 || index > randomPlayed || index > randomHistory.lastIndex) return
        randomCursor = index
        playTransient(randomHistory[index])
        publishRandomPicks()
    }

    /**
     * Drops a pick from the record.
     *
     * Not a queue edit — the order is the dice's and there is nothing past the cursor to disturb.
     * It is pruning what you are looking at before keeping the rest. Removing the one playing is
     * allowed and leaves it playing: stopping the music because a row was tidied away would be a
     * surprise, and the dock still says what it is.
     */
    fun removeRandomAt(index: Int) {
        if (index < 0 || index > randomPlayed || index > randomHistory.lastIndex) return
        randomHistory.removeAt(index)
        if (index <= randomCursor) randomCursor--
        randomPlayed--
        publishRandomPicks()
    }

    /**
     * The dice: a tune nobody here has heard, now. Picks at random from the indexed catalogues and
     * plays it, without adding it to anything.
     *
     * Picks read ahead but never played are **speculation**, and the dice means "surprise me", so
     * they go and everything actually played stays. Stepping back through the history and then
     * pressing the dice therefore re-rolls, while pressing next -- which means forward -- walks
     * into the queue as it should. Truncating at the cursor would throw away real history as well;
     * keeping [randomPlayed] is what lets it throw away only the guesses.
     */
    fun playRandom() {
        scope.launch {
            while (randomHistory.lastIndex > randomPlayed) {
                randomHistory.removeAt(randomHistory.lastIndex)
            }
            randomCursor = randomPlayed
            advanceRandom()
        }
    }

    private fun randomNext() {
        scope.launch { advanceRandom() }
    }

    /** How many picks in a row have refused to open. Reset by the first that plays. */
    private var failedRandomPicks = 0

    /** How many the dice may walk past before it gives up and says why. */
    private val maxFailedRandomPicks = 8

    /**
     * Moves the dice past a pick that would not open.
     *
     * Only in Random, and only for a *pick* — a track the listener chose stops where it is, because
     * being told which file is broken is the useful answer there.
     */
    private fun skipFailedRandomPick() {
        failedRandomPicks += 1
        if (failedRandomPicks > maxFailedRandomPicks) {
            failedRandomPicks = 0
            _state.update {
                it.copy(message = Message(context.getString(R.string.notice_random_gave_up)))
            }
            return
        }
        randomNext()
    }

    private fun randomPrevious() {
        if (randomCursor <= 0) return
        randomCursor--
        playTransient(randomHistory[randomCursor])
        publishRandomPicks()
    }

    /**
     * Moves Random forward one, having decided what comes after it first.
     *
     * The order is the point of the whole item: picking at the moment the button is pressed leaves
     * nothing to fetch in advance, so the read-ahead has nothing to work on however well it works.
     * Deciding early is what makes the wait go.
     */
    private suspend fun advanceRandom() {
        val session = randomSession
        // **Forward walks the record, and rolls only at its end.** With a list on screen, next
        // means the next row: rolling from the middle of the record would leave a gap between what
        // you are hearing and what you are looking at. The dice button is what always rolls.
        fillRandomQueue()
        if (session != randomSession) return
        if (randomCursor >= randomHistory.lastIndex) {
            // Which sentence depends on the scope, because "nothing is indexed" is only true of
            // the unnarrowed dice. The chips are disabled when they would draw nothing, so a
            // narrowed dice that comes back empty means the index went away underneath it -- and
            // being told to index a catalogue, having just indexed one, teaches nothing.
            val empty = when (_browse.value.randomScope) {
                is RandomScope.Everything -> context.getString(R.string.notice_random_nothing_indexed)
                is RandomScope.OnPlatform -> context.getString(R.string.notice_random_nothing_for_platform)
                is RandomScope.Favourites -> context.getString(R.string.notice_random_no_favourites)
            }
            _state.update { it.copy(message = Message(empty), randomExhausted = true) }
            return
        }

        randomCursor++
        randomPlayed = maxOf(randomPlayed, randomCursor)
        _state.update { it.copy(randomExhausted = false) }
        publishRandomPicks()
        // Topped up before playing rather than after: `load` reads ahead when it finishes, and it
        // can only read what has already been decided.
        fillRandomQueue()
        if (session != randomSession) return
        // Read rather than indexed: the checks above say this session is still the one running, and
        // this says the record still has the row it is pointing at.
        val pick = randomHistory.getOrNull(randomCursor) ?: return
        playTransient(pick)
    }

    /** Tops the queue up so [READ_AHEAD] picks stand past the cursor. */
    private suspend fun fillRandomQueue() {
        val session = randomSession
        val short = READ_AHEAD - (randomHistory.lastIndex - randomCursor)
        if (short <= 0) return
        // The scope is read here rather than captured when Random started, so changing it takes
        // effect on the next pick instead of at the next session. Picks already read ahead keep the
        // scope they were drawn under, which is why the row says what is set rather than what is
        // playing.
        val scope = _browse.value.randomScope
        val platforms = when (scope) {
            is RandomScope.Everything, is RandomScope.Favourites -> emptySet()
            is RandomScope.OnPlatform -> setOf(scope.platformId)
        }
        // **Drawn wide and filtered, because the query cannot exclude anything.** `randomSample`
        // is `ORDER BY RANDOM() LIMIT n` over the whole scope every time, so nothing stops it
        // handing back a tune this session has already played -- which reads exactly like the dice
        // replaying history, and is likelier the narrower the scope: Favourites is 991 tunes, not
        // half a million. Asking for more than is needed and dropping the repeats is one query and
        // no schema.
        val already = randomHistory.mapTo(mutableSetOf()) { it.id }
        val drawn = catalogues.randomSample(
            short * OVERDRAW,
            platforms = platforms,
            favouritesOnly = scope is RandomScope.Favourites,
        ).map(::toTrackRef).filter { already.add(it.id) }.take(short)
        // A pool smaller than the session can exhaust honestly — forty favourites cannot fill an
        // evening without repeating. Sooner than repeat silently or stop dead, the dice repeats,
        // which is what it did before any of this and what a small pool means.
        // Drawing asked the database; by now the session may be another one, and these picks belong
        // to nobody.
        if (session != randomSession) return
        randomHistory += drawn.ifEmpty {
            catalogues.randomSample(
                short,
                platforms = platforms,
                favouritesOnly = scope is RandomScope.Favourites,
            ).map(::toTrackRef)
        }
    }

    /**
     * Narrows what the dice picks from, and throws away the picks read ahead under the old scope.
     *
     * Without the discard, choosing "Amiga" would still play three C64 tunes first — the read-ahead
     * exists so a pick can be fetched before it is needed, and it is exactly what makes a scope
     * change look ignored.
     */
    fun setRandomScope(scope: RandomScope) {
        _browse.update { it.copy(randomScope = scope) }
        // A setting, so it is written like one.
        scheduleSave()
        if (_state.value.randomMode) {
            randomHistory.subList(randomCursor + 1, randomHistory.size).clear()
        }
    }

    /**
     * Plays a search result, without adding it to anything.
     *
     * The point of a search is finding out what something is, which must not require adding it to
     * the playlist first. The results become the queue while you are in them, so next and previous
     * walk what you found and the playlist is untouched.
     */
    fun playFromResults(results: List<TrackRef>, index: Int) {
        if (index !in results.indices) return
        // Where the list came from is known only here, from the screen it was tapped on (A56).
        val fromHistory = _browse.value.domain == BrowseDomain.HISTORY
        // **Where it plays from, remembered as it looks** (A61): the cover names it, and its way
        // back returns to it -- the same words, the same folder -- rather than to a fresh Browse.
        sessionBrowse = _browse.value
        _state.update { it.copy(resultsFromHistory = fromHistory, sessionSource = SessionSource.of(_browse.value)) }
        playFromResultsQueue(PlayQueue(tracks = results).startAt(index))
    }

    private fun playFromResultsQueue(results: PlayQueue) {
        val ref = results.current ?: return
        _state.update {
            it.copy(
                resultsQueue = results,
                transient = null,
                externalOpen = false,
                randomHasPrevious = false,
                playing = false,
                positionSeconds = 0.0,
                // Entered from the dice, the dice waits rather than ending; walking the results
                // with next keeps it waiting.
                diceWaiting = it.randomMode || it.diceWaiting,
            )
        }
        load(ref)
    }

    /**
     * Back to the dice that was waiting under a digression (`docs/BACKLOG.md` A41).
     *
     * **Paused, on the pick it was on**, rather than starting it: coming back from a digression
     * should not put music on unasked. Nothing is loaded, so the next press of play goes through
     * [pendingRetry] and starts that tune; next rolls a new one. What played during the digression
     * is in the history, as everything played here is.
     */
    /**
     * Back to the search a digression came from -- [resumeDice]'s counterpart for results.
     *
     * The results come back as they were: words, scope, rows, and where the list was. **What was
     * playing is left alone when it is still the results' tune** -- a look at the author and back
     * does not stop the music. When something from the author's folder was chosen meanwhile, it
     * stops and the results' tune waits paused, as the dice's does, because a queue whose current
     * tune is not the one sounding is a transport that lies.
     */
    /**
     * Browse back on the list that is playing, as it was when it started (A61) -- the playlist
     * cover's way back, and what the Browse button opens while a list plays. Answers false when no
     * list is playing, and Browse then opens as it always has, at its top.
     *
     * **A search opened from the uncovered playlist starts fresh; returning to one that plays keeps
     * it** -- the owner's refinement, 2026-09-25: the reset was for a new search, not for coming back.
     */
    fun returnToSession(): Boolean {
        val playingFrom = sessionBrowse ?: return false
        if (!_state.value.searchMode) return false
        _browse.value = BrowseNavigation.returningTo(playingFrom)
        return true
    }

    fun resumeSearch() {
        val waiting = waitingSearch ?: return
        waitingSearch = null
        _browse.value = BrowseNavigation.returningTo(waiting.browse)
        val results = waiting.results
        val sounding = _state.value.current?.id
        if (results != null && _state.value.searchMode && sounding != results.current?.id) {
            stopPlayback()
            pendingRetry = { playFromResultsQueue(results) }
            _state.update {
                it.copy(
                    resultsQueue = results, searchWaiting = false, playing = false,
                    positionSeconds = 0.0, durationSeconds = 0.0, metadata = emptyMap(),
                )
            }
        } else {
            _state.update { it.copy(resultsQueue = results ?: it.resultsQueue, searchWaiting = false) }
        }
    }

    fun resumeDice() {
        if (!_state.value.diceWaiting) return
        val pick = randomHistory.getOrNull(randomCursor) ?: return
        stopPlayback()
        pendingRetry = { playTransient(pick) }
        _state.update {
            it.copy(
                resultsQueue = null,
                transient = pick,
                diceWaiting = false,
                randomHasPrevious = randomCursor > 0,
                playing = false,
                positionSeconds = 0.0,
                durationSeconds = 0.0,
                metadata = emptyMap(),
            )
        }
    }

    /**
     * Keeps what is playing, without leaving Random.
     *
     * Deliberately separate from leaving Random: adding a track you like should not end the
     * sequence you are listening through.
     */
    fun keepTransient() {
        // Anything playing that is not the playlist: a Random pick, a search result, a track tapped
        // while browsing. All three are "I am hearing something I did not choose to keep", and the
        // moment just after hearing it is when a person decides — so this is not Random's alone,
        // even though that is where the idea came from.
        val ref = _state.value.transient ?: _state.value.resultsQueue?.current ?: return
        // Its own wording, because this one knows the track's name and the general notice does not.
        //
        // Not `addToPlaylist` with the message overwritten afterwards: that would depend on
        // `appendTracks` happening to be synchronous, and would put a second notice into the flow
        // on the way. Saying it once is both simpler and true regardless of ordering.
        appendTracks(listOf(ref)) { added, _ ->
            if (added > 0) {
                Message(context.getString(R.string.notice_added_track, ref.title))
            } else {
                Message(context.getString(R.string.notice_track_already_here, ref.title))
            }
        }
    }

    /**
     * Leaves whatever is playing outside the playlist — Random or a search — and goes back to it.
     *
     * Playback stops. The playlist is exactly where it was left, which is the whole point of never
     * having written to it.
     */
    fun returnToPlaylist() {
        if (!_state.value.awayFromPlaylist) return
        stopPlayback()
        randomSession++
        randomHistory.clear()
        randomCursor = -1
        randomPlayed = -1
        _state.update {
            it.copy(
                transient = null,
                externalOpen = false,
                resultsQueue = null,
                diceWaiting = false,
                searchWaiting = false,
                randomHasPrevious = false,
                randomPicks = emptyList(),
                randomIndex = -1,
                randomExhausted = false,
                playing = false,
                metadata = emptyMap(),
                positionSeconds = 0.0,
                durationSeconds = 0.0,
            )
        }
    }

    // --- search -------------------------------------------------------------------------------

    fun setQuery(query: String) = _browse.update { it.copy(query = query) }

    /** Ticks one catalogue inside an online scope. Only meaningful while that is the scope. */
    fun toggleSearchCatalogue(id: String) = _browse.update {
        val scope = it.searchScope as? SearchScope.Online ?: return@update it
        val ids = if (id in scope.catalogueIds) scope.catalogueIds - id else scope.catalogueIds + id
        it.copy(searchScope = SearchScope.Online(ids))
    }

    /** Ticks one platform. Same shape, and the same rule: empty means all of them. */
    fun toggleSearchPlatform(id: String) = _browse.update {
        val scope = it.searchScope as? SearchScope.ByPlatform ?: return@update it
        val ids = if (id in scope.platformIds) scope.platformIds - id else scope.platformIds + id
        it.copy(searchScope = SearchScope.ByPlatform(ids))
    }

    /**
     * Results belong to the *kind* of search that produced them.
     *
     * Left on screen after switching between local, online and by-platform they are a lie the app
     * tells with a straight face: switching to `Online` and seeing local files sitting there makes
     * them look like an answer. The label changing on its own makes it worse rather than better --
     * two things on one screen disagreeing about what you are looking at.
     *
     * **Ticking one more catalogue or platform does not do this.** It
     * widens the same question rather than asking a different one, so throwing the answer away
     * because Modland has been joined by ASMA reads as the app losing your place. The results are
     * incomplete until you search again, which is the ordinary state of a filter you are still
     * adjusting.
     *
     * This is the counterpart to back keeping results, not a contradiction of it. What must survive
     * is **leaving and returning**; what must not is a list produced by a different kind of search.
     */
    private fun BrowseState.withoutStaleResults() =
        copy(tracks = emptyList(), searchMatches = 0, searched = false, liveSearchNeededQuery = false)

    /**
     * Runs the search the scope describes.
     *
     * **An empty query is a question, not a mistake.** `%%` matches every row, so "everything on
     * the Amiga" is a scope with nothing typed, which is the natural way to ask it and must not
     * return silently. The per-source cap and the count beside it are built for exactly this shape
     * of answer.
     */
    fun runSearch() {
        val current = _browse.value
        val searching = current.searchScope
        // The two narrowings the scope implies, worked out once. A platform scope with nothing
        // ticked means every platform, which is why an empty set has to become an empty filter
        // rather than an empty result -- the label says "All platforms" and the search must agree.
        val platformIds = (searching as? SearchScope.ByPlatform)?.platformIds.orEmpty()

        scope.launch {
            _browse.update { it.copy(loading = true, tracks = emptyList()) }

            // The scanned library first: it knows tunes by their real titles, and it knows files
            // nobody has added to any playlist. Then the playlists, for anything indexed folders do
            // not cover -- an individually picked file, or a folder whose grant is gone.
            val fromIndex = if (searching.searchesLocal) {
                libraryIndex.search(current.query, SearchResults.PER_SOURCE_LIMIT, platformIds)
            } else {
                emptyList()
            }

            val fromLocal = if (searching.searchesLocal) {
                // The library, meaning every playlist's tracks -- searching only the active one
                // would answer a question nobody asked.
                // Capped like the other two. It never was, because a typed query is its own limit
                // -- but an empty query matches every track in every playlist, and this is the one
                // source that would have handed back all of them.
                store.allTracks().asSequence().filter {
                    // The same rule the two SQL searches use (`SearchTerms`), so a query that finds
                    // a tune in the index does not miss the copy of it sitting in a playlist.
                    SearchTerms.matchesAny(current.query, it.title, it.fileName) &&
                        (platformIds.isEmpty() || Platforms.matches(it.fileName, platformIds))
                }.take(SearchResults.PER_SOURCE_LIMIT).toList()
            } else {
                emptyList()
            }

            // Explicit. "No catalogue ticked means all of them" was the earlier rule and it made
            // the filter look broken: unticking Modland searched Modland anyway. It is safe again
            // here for the one reason it was not then -- the label states the scope out loud, so
            // "Online" plainly covers everything and "Online: Modland" plainly does not.
            val chosen = (searching as? SearchScope.Online)?.catalogueIds.orEmpty()
            val indexed = current.catalogues.filter { it.indexed }.map { it.id }.toSet()
            val wanted = (if (chosen.isEmpty()) indexed else chosen)
            val dbCatalogues = wanted.filter { it != com.przunk.protracktor.net.ModArchive.id }.toSet()
            val fromOnline = if (searching.searchesOnline && dbCatalogues.isNotEmpty()) {
                catalogues.search(
                    current.query, dbCatalogues, SearchResults.PER_SOURCE_LIMIT, platformIds,
                ).map(::toTrackRef)
            } else {
                emptyList()
            }

            // The Mod Archive is searched live and has no index to narrow, so a platform scope
            // cannot reach it at the query -- its results are filtered here instead of being
            // dropped, which would have made "Amiga" quietly mean "Amiga except The Mod Archive".
            // The Mod Archive is the one source an empty query cannot ask: it is a live search
            // against somebody else's server, with no index here to list. Skipped rather than sent
            // an empty query it would answer badly or refuse.
            val liveInScope = searching.searchesOnline && ModArchive.id in wanted
            val live = if (liveInScope && current.query.isNotBlank()) {
                ModArchive.search(current.query)
            } else {
                ModArchive.Outcome.Found(emptyList())
            }
            // Said out loud, because the difference between "the archive does not have it", "we
            // could not ask" and "it answered with something we cannot read" is the difference
            // between a fact and two different faults. C15 was reported as the first and the app
            // had no way of telling anyone it might be one of the others.
            when (live) {
                is ModArchive.Outcome.NotReached ->
                    _state.update { it.copy(message = Message(context.getString(R.string.notice_modarchive_unreachable))) }
                is ModArchive.Outcome.Unreadable ->
                    _state.update { it.copy(message = Message(context.getString(R.string.notice_modarchive_unreadable))) }
                is ModArchive.Outcome.Found -> Unit
            }
            val fromModArchive = (live as? ModArchive.Outcome.Found)?.tracks.orEmpty().filter {
                platformIds.isEmpty() || Platforms.matches(it.fileName, platformIds)
            }

            // De-duplicated across **all four**, not just the first two. The old code guarded
            // index-against-playlists and then concatenated the catalogues, which crashed the app
            // the moment somebody added an online track to a playlist and searched for it again --
            // see `SearchResults`.
            val results = SearchResults.combine(
                fromIndex = fromIndex,
                fromPlaylists = fromLocal,
                fromCatalogues = fromOnline,
                fromLiveSearch = fromModArchive,
            )

            // Asked only when a cap was actually reached. A COUNT over a couple of hundred thousand
            // catalogue rows is cheap but not free, and a search that fits does not need it.
            val capped = fromIndex.size == SearchResults.PER_SOURCE_LIMIT ||
                fromOnline.size == SearchResults.PER_SOURCE_LIMIT
            val matches = if (!capped) 0 else {
                (if (searching.searchesLocal) libraryIndex.countMatches(current.query) else 0) +
                    (if (searching.searchesOnline && dbCatalogues.isNotEmpty()) {
                        catalogues.countMatches(current.query, dbCatalogues, platformIds)
                    } else {
                        0
                    })
            }

            _browse.update {
                it.copy(
                    tracks = results,
                    searchMatches = matches,
                    searched = true,
                    // Only worth saying when it is the whole story: with Modland also in scope, the
                    // live source contributing nothing to a blank search is unremarkable.
                    liveSearchNeededQuery = liveInScope && current.query.isBlank() &&
                        dbCatalogues.isEmpty() && !searching.searchesLocal,
                    loading = false,
                )
            }
        }
    }

    /**
     * Changes what the search covers.
     *
     * Does not re-run the search. Choosing a scope is setting up a question, not asking it -- and
     * re-running on every tick of a platform chip would fire a query per tap while somebody picks
     * three of them.
     */
    fun setSearchScope(scope: SearchScope) {
        _browse.update { it.copy(searchScope = scope).withoutStaleResults() }
    }

    /** Counts the platform chips from the catalogue index, which is what makes a dead chip honest. */
    private fun refreshPlatformCounts() {
        scope.launch {
            // Counted by the column each row carries (C89), so ASMA counts towards Atari 8-bit.
            val byPlatform = catalogues.platformCounts()
            _browse.update { it.copy(platformCounts = byPlatform) }
        }
    }

    /**
     * What to tell the user when a track will not open.
     *
     * **The four cases are genuinely different**, and one message for all of them misleads: a
     * file that failed to *download* reads as a format we cannot play, and one `.stc` among 3,639
     * reads as "Spectrum is a format Protracktor cannot play yet" — a whole platform, of which
     * this build plays 95%.
     *
     * The choice is made by [OpenFailure], where it can be tested; this supplies what only the
     * controller knows. The decoder's own reason is kept where there is one — it is true, and
     * occasionally it is the only thing that says *which* backend gave up — but it is now attached
     * to the file rather than offered as a verdict on the format.
     */
    private suspend fun describeFailure(
        ref: TrackRef,
        fetched: Boolean,
        reason: String = "",
        missingCompanions: List<String> = emptyList(),
    ): String {
        val name = ref.fileNameOrTitle
        val claimed = SupportedFormats.looksPlayable(name)
        val needsPlayers = SupportedFormats.needsUade(name) &&
            withContext(Dispatchers.IO) { !UadePlayers.present(context) }
        return when (OpenFailure.kindOf(fetched, claimed, reason, needsPlayers, missingCompanions.isNotEmpty())) {
            OpenFailure.Kind.COMPANION_MISSING ->
                context.getString(R.string.open_failed_companion, ref.title, missingCompanions.joinToString())
            OpenFailure.Kind.NOT_FETCHED ->
                context.getString(R.string.open_failed_not_fetched, ref.title)
            OpenFailure.Kind.NEEDS_AMIGA_PLAYERS ->
                context.getString(R.string.open_failed_needs_players, ref.title)
            OpenFailure.Kind.FORMAT_UNSUPPORTED ->
                context.getString(
                    R.string.open_failed_format,
                    OpenFailure.formatName(catalogueFormatOf(ref), name),
                )
            OpenFailure.Kind.FILE_REFUSED_WITH_REASON -> {
                val refused = context.getString(R.string.open_failed_file_because, ref.title, reason)
                OpenFailure.modlandFormatOf(ref.id)
                    ?.let { context.getString(R.string.open_failed_modland_lists, OpenFailure.sentence(refused), it) } ?: refused
            }
            OpenFailure.Kind.FILE_REFUSED -> {
                val refused = context.getString(R.string.open_failed_file, ref.title)
                OpenFailure.modlandFormatOf(ref.id)
                    ?.let { context.getString(R.string.open_failed_modland_lists, OpenFailure.sentence(refused), it) } ?: refused
            }
        }
    }

    private fun toTrackRef(track: CatalogueTrack): TrackRef {
        val catalogue = Catalogue.byId(track.catalogueId)
        return TrackRef(
            // The URL is the identity. A catalogue track has no document URI and never will, and
            // the URL is what both the cache and the player key on.
            id = catalogue?.urlFor(track.path) ?: track.path,
            title = track.title,
            // The source, as a path, the same shape a local file's is: "Modland/Protracker/4-Mat".
            // A path rather than "format · author", which reads well in a row and badly everywhere
            // else -- the information panel has to say where a file came from, and a search result
            // has to answer "which one of these is it". The author keeps its own field, so nothing
            // is lost.
            subtitle = listOf(catalogue?.displayName, track.path.substringBeforeLast('/', ""))
                .filter { !it.isNullOrBlank() }
                .joinToString("/"),
            sizeBytes = track.size,
            fileName = track.title,
            // Catalogues file by author, so this is known before the file is ever opened.
            author = track.author,
        )
    }

    /** Adds a chosen set to the active playlist. Duplicates are ignored rather than doubled. */
    fun addToPlaylist(tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        appendTracks(tracks, ::describeAdded)
    }

    // `addToPlaylistAndSay` lived here: it added to the current playlist and announced it, for the
    // row menu that stayed in Browse (C7). That menu item is gone -- one "Add to playlist..." opens
    // the picker instead (`docs/BACKLOG.md` A17) -- and the picker's own path already reports back
    // and names the target, which is strictly better. Removed rather than kept for a caller that no
    // longer exists.

    fun addToPlaylist(targetPlaylistId: Long, tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        if (targetPlaylistId == playlistId) {
            addToPlaylist(tracks)
            return
        }
        scope.launch {
            val targetName = store.playlists().firstOrNull { it.id == targetPlaylistId }?.name
                ?: return@launch
            val existing = store.tracksIn(targetPlaylistId).toMutableList()
            var added = 0
            tracks.forEach { candidate ->
                if (existing.none { it.sameFileAs(candidate) }) {
                    existing.add(candidate)
                    added++
                }
            }
            if (added > 0) {
                store.replaceTracks(targetPlaylistId, existing)
                refreshPlaylists()
            }
            val message = when {
                tracks.size == 1 && added == 1 ->
                    Message(context.getString(R.string.notice_added_to_named_playlist, tracks.first().title, targetName))
                tracks.size == 1 && added == 0 ->
                    Message(context.getString(R.string.notice_already_in_playlist, tracks.first().title, targetName))
                added > 0 ->
                    Message(context.resources.getQuantityString(R.plurals.notice_added_count_to_playlist, added, added, targetName))
                else ->
                    Message(context.getString(R.string.notice_all_already_in_playlist, targetName))
            }
            _state.update { it.copy(message = message) }
        }
    }

    /**
     * Creates a new playlist and immediately adds [tracks] to it (B18).
     */
    fun createPlaylistAndAdd(name: String, tracks: List<TrackRef>) {
        if (tracks.isEmpty()) return
        scope.launch {
            val finalName = name.ifBlank { defaultPlaylistName }
            val newId = store.createPlaylist(finalName)
            store.replaceTracks(newId, tracks)
            val updated = store.playlists()
            _state.update { it.copy(playlists = updated) }
            val message = if (tracks.size == 1) {
                Message(context.getString(R.string.notice_added_to_named_playlist, tracks.first().title, finalName))
            } else {
                Message(context.resources.getQuantityString(R.plurals.notice_added_count_to_playlist, tracks.size, tracks.size, finalName))
            }
            _state.update { it.copy(message = message) }
        }
    }

    // --- library ------------------------------------------------------------------------------

    /**
     * Grants a folder and adds everything playable in it to the playlist.
     *
     * It scans first and adds what the scan found, rather than adding every file whose name looked
     * promising. That is the difference A6 is about: the list you get is what the decoders accepted,
     * so nothing in it refuses when you press play.
     */
    fun addFolder(treeUri: Uri) {
        scope.launch {
            _state.update { it.copy(scanning = true) }
            MediaScanner.persistPermission(context, treeUri, isTree = true)
            val folder = GrantedFolder(
                uri = treeUri.toString(),
                displayName = MediaScanner.labelOf(treeUri),
            )
            store.rememberFolder(folder)
            _browse.update { it.copy(folders = store.grantedFolders()) }

            scanFolder(folder)
            scanJob?.join()

            _state.update { it.copy(scanning = false) }
            appendTracks(libraryIndex.tracksIn(folder.uri), ::describeAdded)
        }
    }

    fun addFiles(uris: List<Uri>) {
        scope.launch {
            val found = withContext(Dispatchers.IO) { MediaScanner.fromDocuments(context, uris) }
            appendTracks(found, ::describeAdded)
        }
    }

    /**
     * Adds tracks to the playlist being edited, and commits them unless an edit is already pending.
     *
     * **Adding does not need saving** (`docs/OPEN_QUESTIONS.md` Q8). It arrives from somewhere else
     * -- a search result, a folder, another playlist -- and nothing about it is provisional: you
     * asked for a tune to be in the list and it is. Save belongs to the edits you make *inside* the
     * list, where a wrong drag or a mistaken removal is a real risk and undoing it matters.
     *
     * **Unless something is already unsaved.** Writing the list to disk writes all of it, so an
     * auto-saved add made while a removal is pending would quietly commit the removal too --
     * turning a convenience into a way of losing tracks without pressing anything. When an edit is
     * in progress the add joins it, and one Save covers both. That is the one case where this rule
     * bends, and it bends towards the user keeping what they have.
     */
    private fun appendTracks(found: List<TrackRef>, describe: (added: Int, skipped: Int) -> Message?) {
        var added = 0
        var skipped = 0
        var firstAdded = 0
        val hadPendingEdit = _state.value.dirty
        _state.update { current ->
            // Rebuilding the queue rather than mutating it keeps the play history meaningful: the
            // indices it holds must keep pointing at the same tracks.
            val merged = current.queue.tracks.toMutableList()
            found.forEach { candidate ->
                if (merged.any { it.sameFileAs(candidate) }) {
                    skipped++
                } else {
                    if (added == 0) firstAdded = merged.size
                    merged += candidate
                    added++
                }
            }
            current.copy(
                queue = current.queue.withTracks(merged),
                scanning = false,
                // Left alone when the add is about to be written; raised only when it is joining
                // an edit that was already unsaved.
                dirty = current.dirty || (added > 0 && hadPendingEdit),
                // Only replaces the current message when there is something to say; a null must
                // not silently clear a message the user has not read.
                message = describe(added, skipped) ?: current.message,
            )
        }
        resolveMetadataInBackground()

        if (added > 0 && !hadPendingEdit) {
            // Written from the state rather than from `found`, so it stores exactly the list the
            // screen is showing -- including whatever the de-duplication above decided.
            scope.launch {
                store.replaceTracks(playlistId, _state.value.queue.tracks)
                refreshPlaylists()
            }
        }

        // Adding appends to the end, so without this nothing visibly happens -- which matters more
        // now that the confirming message was deliberately removed.
        if (added > 0) _reveal.tryEmit(firstAdded)
    }

    /**
     * What to say after adding — now always something.
     *
     * **Silence on success is not enough.** The argument for it — the rows appearing *is* the
     * confirmation, and a notice repeating the screen is noise — holds only when the rows are
     * visible. Adding from a local folder closes Browse and lands on a playlist that may not
     * visibly change at all: the new rows are at the end, and scrolling to them is not something a
     * person registers as an answer.
     *
     * The objection that a snackbar covers the rows it reports is dealt with separately: it is
     * swipeable (`SwipeableSnackbar`).
     */
    private fun describeAdded(added: Int, skipped: Int): Message? {
        // Named where there is a name to use, and a separate sentence rather than a suffix glued
        // on: " to X" reads as English word order and would have to be re-glued for every other
        // language.
        val where = _state.value.activePlaylistName
        return when {
            added == 0 && skipped == 0 -> Message(context.getString(R.string.notice_nothing_playable))
            added == 0 -> Message(context.getString(R.string.notice_all_already_here))
            skipped == 0 && where != null -> Message(
                context.resources.getQuantityString(
                    R.plurals.notice_added_count_to_playlist, added, added, where,
                )
            )
            skipped == 0 -> Message(
                context.resources.getQuantityString(R.plurals.notice_added_count, added, added)
            )
            where != null -> Message(
                context.getString(R.string.notice_added_some_already_named, added, where, skipped)
            )
            else -> Message(context.getString(R.string.notice_added_some_already, added, skipped))
        }
    }

    /**
     * What [undoRemoval] would put back, as (position, track) pairs. Cleared once its notice is gone.
     *
     * A list rather than one pair, because a bulk delete is **one edit, not twenty**
     * (`docs/BACKLOG.md` A4). Undoing it has to restore the whole selection: putting back one row of
     * twenty is not an undo, it is a second surprise.
     */
    private var lastRemoval: List<TrackEditing.Removed> = emptyList()

    /**
     * Drops one track.
     *
     * Removing what is playing stops playback rather than jumping somewhere: silently starting a
     * different track because the user deleted this one is a surprise, and there is no reading of
     * "remove" that asks for it.
     *
     * No confirmation. Undo is the better answer for one row -- it costs nothing when the user meant
     * it, and a dialog on every delete is a toll paid by the people who did.
     */
    fun removeTrack(index: Int) = removeTracks(listOf(index))

    /**
     * Drops any number of tracks as one edit.
     *
     * Removing what is playing stops playback rather than jumping somewhere: silently starting a
     * different track because the user deleted this one is a surprise, and there is no reading of
     * "remove" that asks for it.
     *
     * No confirmation, for the same reason as before: undo costs nothing when the user meant it,
     * and a dialog on every delete is a toll paid by the people who did. What changed for a group
     * is that undo has to bring **all** of it back.
     */
    fun removeTracks(indices: List<Int>) {
        val currentState = _state.value
        val (kept, removed) = TrackEditing.remove(currentState.queue.tracks, indices)
        if (removed.isEmpty()) return
        val wasPlaying = removed.any { it.index == currentState.queue.currentIndex }

        if (wasPlaying) stopPlayback()
        lastRemoval = removed

        _state.update {
            it.copy(
                queue = it.queue.withTracks(kept),
                playing = if (wasPlaying) false else it.playing,
                metadata = if (wasPlaying) emptyMap() else it.metadata,
                positionSeconds = if (wasPlaying) 0.0 else it.positionSeconds,
                durationSeconds = if (wasPlaying) 0.0 else it.durationSeconds,
                dirty = true,
                message = Message(
                    text = if (removed.size == 1) {
                        context.getString(R.string.notice_removed_one, removed.first().track.title)
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.notice_removed_count, removed.size, removed.size,
                        )
                    },
                    actionLabel = UNDO,
                ),
            )
        }
    }

    /**
     * Moves a track to another position.
     *
     * An edit like any other, so it waits for Save. History survives because [PlayQueue.withTracks]
     * remaps by track identity rather than by index — written for removal, and it covers this for
     * free.
     */
    fun moveTrack(from: Int, to: Int) {
        if (from == to) return
        _state.update { current ->
            val tracks = current.queue.tracks
            if (from !in tracks.indices || to !in tracks.indices) return@update current
            val reordered = tracks.toMutableList().apply { add(to, removeAt(from)) }
            current.copy(queue = current.queue.withTracks(reordered), dirty = true)
        }
    }

    /** Puts everything the last removal took back where it was. */
    fun undoRemoval() {
        val removed = lastRemoval
        if (removed.isEmpty()) return
        lastRemoval = emptyList()
        _state.update {
            it.copy(
                queue = it.queue.withTracks(TrackEditing.restore(it.queue.tracks, removed)),
                dirty = true,
                message = null,
            )
        }
    }

    fun dismissMessage() {
        lastRemoval = emptyList()
        _state.update { it.copy(message = null) }
    }

    // --- transport ----------------------------------------------------------------------------

    fun playAt(index: Int) = openAndPlay(_state.value.queue.startAt(index))

    fun next() {
        val now = _state.value
        // A tune inside the file first, but **only in "play all"**. Making next mean something
        // different depending on the *file* would be the transport behaving differently for
        // reasons the user did not choose. Tied to a mode they set, it is predictable.
        if (now.playAllSubsongs && now.subsong + 1 < now.subsongCount) {
            selectSubsong(now.subsong + 1)
            return
        }
        nextFile()
    }

    /**
     * Straight to the next file, past whatever is left inside this one.
     *
     * A long press on the transport. `aleste 2.kss` holds 256 tunes, so leaving it with the
     * ordinary next would mean 256 presses. Pressing is for the tune you are on; holding is for
     * the file.
     *
     * Deliberately **not** on the notification or a headset button. Those have no long press, and
     * inventing a double-tap for them would be a second vocabulary for one idea.
     */
    fun nextFile() {
        val now = _state.value
        if (now.externalMode) return
        // The dice waiting under a digression does not own the transport; the folder on screen does.
        if (now.transient != null && !now.diceWaiting) return randomNext()
        now.resultsQueue?.let { results ->
            if (results.hasNext) playFromResultsQueue(results.next())
            return
        }
        if (!now.queue.hasNext) return
        openAndPlay(now.queue.next())
    }

    fun previous() {
        val now = _state.value
        if (now.playAllSubsongs && now.subsong > 0) {
            selectSubsong(now.subsong - 1)
            return
        }
        previousFile()
    }

    /** Straight to the previous file, past whatever is left inside this one. See [nextFile]. */
    fun previousFile() {
        val now = _state.value
        if (now.externalMode) return
        if (now.transient != null && !now.diceWaiting) return randomPrevious()
        now.resultsQueue?.let { results ->
            if (results.hasPrevious) playFromResultsQueue(results.previous())
            return
        }
        if (!now.queue.hasPrevious) return
        openAndPlay(now.queue.previous())
    }

    /**
     * Moves the playing position.
     *
     * **Off the main thread, because a seek is not quick.** Every emulator here reaches a position
     * by running forward to it, so asking for the end of a five-minute SID is minutes of emulated
     * 6502. Run inside the audio callback that work wedges the app, so it happens on the caller's
     * thread instead — which must therefore never be the thread drawing the screen
     * (`docs/ARCHITECTURE.md` §5).
     *
     * The slider is moved first and does not wait for the decoder to agree. A slider that springs
     * back to where it was until the seek lands reads as a control that did not work — and it now
     * has whole seconds in which to read that way.
     */
    fun seekTo(seconds: Double) {
        val open = track ?: return
        val generation = ++seekGeneration
        _state.update { it.copy(positionSeconds = seconds, seekingTo = seconds, seekSlow = false) }
        scope.launch {
            // The spinner only for a seek that takes long enough to be seen waiting (`SeekProgress`).
            val slow = launch {
                delay(SeekProgress.SPINNER_AFTER_MS)
                if (generation == seekGeneration) _state.update { it.copy(seekSlow = true) }
            }
            withContext(Dispatchers.IO) { runCatching { open.seekTo(seconds) } }
            slow.cancel()
            // Only the latest seek, on the tune it was made on, may say it has finished: a second
            // drag, or another tune, has already taken the bar over.
            if (generation == seekGeneration && track === open) {
                _state.update { it.copy(seekingTo = null, seekSlow = false, positionSeconds = open.positionSeconds()) }
            }
        }
    }

    /** Which seek is the latest; an earlier one finishing must not end a later one's spinner. */
    private var seekGeneration = 0

    /**
     * Plays a tune inside the current file.
     *
     * Always available, whatever the mode: the mode governs what *next* does and whether one tune
     * runs into the next, not whether the user may choose one by hand.
     */
    fun selectSubsong(index: Int) {
        val open = track ?: return
        val now = _state.value
        if (index < 0 || index >= now.subsongCount || index == now.subsong) return
        open.selectSubsong(index)
        // **Re-read once the switch has landed**, which is the audio thread's business and takes a
        // buffer. A GBS names each of its tunes and a SNDH often does; the phone went on showing the
        // first one's name whatever was playing (`docs/review-round-8.md` R4). The web player was
        // forced to get this right by having to answer a message; nothing forced it here.
        describeAgain = true
        _state.update {
            it.copy(
                subsong = index,
                positionSeconds = 0.0,
                // The length is per tune. Backends that know their own only know it after the
                // switch has been applied on the audio thread, so this is cleared rather than left
                // showing the previous tune's and the position poll puts the real one back --
                // except where the backend never knows, and then HVSC's list for this file does.
                durationSeconds = openSongLengths.getOrNull(index) ?: 0.0,
            )
        }
    }

    /**
     * Turns "play every tune in the file" on or off.
     *
     * Global and remembered, like shuffle and repeat, because it is about what plays next rather
     * than about one row.
     */
    fun toggleAllSubsongs() {
        _state.update { it.copy(playAllSubsongs = !it.playAllSubsongs) }
        scheduleSave()
    }

    /**
     * How long to play a tune whose length nothing knows (`docs/STATUS.md` C56).
     *
     * Clamped rather than validated: the slider cannot produce anything outside the range, so a
     * value that is outside it came from a stored setting or a newer build, and refusing it would
     * leave the app with no answer at all. Remembered like the toggle above, for the same reason —
     * it is about what happens next rather than about one row.
     *
     * **It applies from the next poll**, including to whatever is playing now. A tune already past
     * the new limit ends within the second, which is the behaviour somebody dragging the slider
     * down is asking for.
     */
    fun setCacheAhead(mode: CacheAhead) {
        if (!Appearance.selectCacheAhead(context, mode)) return
        _state.update { it.copy(cacheAhead = mode) }
    }

    fun setShareAudioMinutes(minutes: Int) {
        val wanted = AudioExport.limitFromStored(minutes)
        if (!Appearance.selectShareAudioMinutes(context, wanted)) return
        _state.update { it.copy(shareAudioMinutes = wanted) }
    }

    fun setFallbackLength(seconds: Int) {
        val wanted = FallbackLength.snap(seconds)
        if (wanted == _state.value.fallbackLengthSeconds) return
        _state.update { it.copy(fallbackLengthSeconds = wanted) }
        scheduleSave()
    }

    /**
     * How to try the last thing the user asked for again, kept until something opens.
     *
     * Without it, play after a failure starts the playlist: a failed open leaves nothing loaded,
     * and "nothing loaded" is also what a fresh restart looks like. From the sofa that reads as the
     * app ignoring you and playing something of its own choosing.
     *
     * **A closure rather than a `TrackRef`**, because there are two ways to be playing a track and
     * they are not interchangeable: a playlist track goes through `openAndPlay` and a search result
     * or random pick through `playTransient`, which marks the detour. Retrying the first as though
     * it were the second would quietly move the user off their playlist -- the exact class of
     * surprise this is fixing.
     *
     * **Retried whatever the failure was**, including a format nothing here plays. Pressing play
     * then gives the same message a second time, which is honest; the alternative is an app that
     * sometimes obeys and sometimes substitutes, and unpredictable is worse than useless.
     */
    /** The search a digression came from, and the results queue that was playing then (C-search). */
    private data class WaitingSearch(val browse: BrowseState, val results: PlayQueue?)

    /** Browse as it was when the list now playing started: where the cover's way back leads (A61). */
    private var sessionBrowse: BrowseState? = null
    private var waitingSearch: WaitingSearch? = null

    private var pendingRetry: (() -> Unit)? = null

    fun togglePlayPause() {
        // **A track being fetched can be called off.** Pressing play on something not cached
        // starts a download that can take ten seconds, and the button shows "play" throughout, so
        // without this the second press falls into the branch below and starts the *same* track
        // over with no way out but waiting. Cancelling the open job is the honest answer: the
        // second press is the one that means "not now".
        if (_state.value.loadingTrack) {
            openJob?.cancel()
            openJob = null
            pendingRetry = null
            _state.update { it.copy(loadingTrack = false, playing = false) }
            return
        }
        val open = track
        if (open == null) {
            pendingRetry?.let { retry ->
                retry()
                return
            }
            // Nothing loaded -- which is the normal state after a restart, where the queue already
            // knows which track it was on. Resume that one, not the top of the list.
            val queue = _state.value.queue
            if (queue.tracks.isNotEmpty()) playAt(queue.currentIndex ?: 0)
            return
        }
        if (_state.value.playing) {
            open.stop()
            audioFocus.release()
            _state.update { it.copy(playing = false) }
        } else {
            // Asking first, and not starting if refused: a player that talks over a phone call is
            // worse than one that does nothing.
            if (!audioFocus.acquire()) {
                _state.update { it.copy(message = Message(context.getString(R.string.notice_audio_busy))) }
                return
            }
            // **Asked of both meanings of "finished".** The engine knows when a backend stopped
            // producing audio; it does not know when the app stopped a tune at the length HVSC
            // supplied, which is how most SIDs end. `PlayFromEnd` has the case that was missed.
            val atEnd = PlayFromEnd.shouldRestart(
                engineSaysFinished = open.isFinished(),
                positionSeconds = _state.value.positionSeconds,
                durationSeconds = _state.value.durationSeconds,
            )
            val restarted = if (atEnd) open.restart() else open.start()
            _state.update {
                it.copy(playing = restarted, positionSeconds = if (atEnd) 0.0 else it.positionSeconds)
            }
        }
    }

    /**
     * Play or pause explicitly, rather than toggling.
     *
     * A media button says which it wants; toggling on a stale idea of the state is how a headphone
     * press ends up pausing something that was already paused.
     */
    fun togglePlayPauseTo(play: Boolean) {
        if (play == _state.value.playing) return
        togglePlayPause()
    }

    /** Stops playing without forgetting what was loaded. Used by the notification's stop action. */
    fun pause() {
        val open = track ?: return
        open.stop()
        audioFocus.release()
        _state.update { it.copy(playing = false) }
    }

    fun toggleShuffle() {
        _state.update { it.copy(queue = it.queue.withShuffle(!it.queue.shuffle)) }
        scheduleSave()
        // Both of these change what comes next, so anything read ahead is now the wrong track.
        prefetchUpcoming()
    }

    fun cycleRepeat() {
        _state.update { it.copy(queue = it.queue.withRepeat(it.queue.repeat.next())) }
        scheduleSave()
        prefetchUpcoming()
    }

    // --- internals ----------------------------------------------------------------------------

    private fun handleTrackEnded() {
        // A file with more tunes in it runs on into the next one, but only in "play all". Off, a
        // 256-subsong file behaves like any other track and the playlist keeps moving, which is
        // why off is the default. The rule itself, including what repeat-one means here, is in
        // `SubsongAdvance` where it can be tested.
        val now = _state.value
        when (
            val next = SubsongAdvance.after(
                playAll = now.playAllSubsongs,
                subsong = now.subsong,
                subsongCount = now.subsongCount,
                repeatOne = now.queue.repeat == RepeatMode.ONE,
            )
        ) {
            is SubsongAdvance.Next.Subsong -> {
                selectSubsong(next.index)
                return
            }
            SubsongAdvance.Next.FileFinished -> Unit
        }

        _state.value.resultsQueue?.let { results ->
            val advanced = results.onTrackEnded()
            when {
                advanced == null -> {
                    track?.stop()
                    audioFocus.release()
                    _state.update { it.copy(playing = false, positionSeconds = it.durationSeconds) }
                }
                // Repeat-one hands back the identical queue.
                advanced == results -> {
                    val playing = track?.restart() ?: false
                    _state.update { it.copy(playing = playing, positionSeconds = 0.0) }
                }
                else -> playFromResultsQueue(advanced)
            }
            return
        }

        // A transient track is a Random pick or a file another app handed us; a search result sets
        // `transient` to null on its way through `playFromResultsQueue` above. The external one
        // stops when it ends -- there is no next, because one file arrived and that was all of it.
        //
        // Random does **not** stop here. The guard worth keeping is against rolling on into the
        // *playlist*, which answers a question nobody asked by pressing Random; going to the next
        // random pick is the question they did ask. Stopping after every tune would make Random
        // something you operate rather than something you listen to.
        if (_state.value.transient != null) {
            // Repeat-one is the one setting that means "keep playing this", and it says so on the
            // dock while Random is running. Skipping to another tune under it would be the app
            // contradicting its own button.
            if (_state.value.queue.repeat == RepeatMode.ONE) {
                val playing = track?.restart() ?: false
                _state.update { it.copy(playing = playing, positionSeconds = 0.0) }
            } else if (!_state.value.externalOpen) {
                // **Not playing any more, said before the next pick is chosen, not after.**
                //
                // The poll that called this runs every 200 ms and skips only while `playing` is
                // false or a load is running. The playlist's path satisfies that at once -- it
                // starts loading before it returns. Random did not: `randomNext` launches, moves
                // the cursor, publishes the row, and then *waits on a database query* for the next
                // read-ahead pick before `playTransient` sets `playing = false` and starts the load.
                // That query sorts the whole scope by a generated key (`docs/review-round-8.md`
                // R7), and under a platform filter it can take several ticks. Every tick in that
                // gap found the same finished track, still "playing", and advanced again.
                //
                // What that looks like: a tune ends, five picks appear on the record at once and
                // the sixth plays. Only at a natural end, because a pressed next leaves a track
                // that is not finished, and only sometimes, because it depends on whether the
                // query beats the next tick -- it does once SQLite has the pages cached.
                _state.update { it.copy(playing = false) }
                randomNext()
            } else {
                _state.update { it.copy(playing = false) }
            }
            return
        }

        val queue = _state.value.queue
        val advanced = queue.onTrackEnded()

        when {
            advanced == null -> {
                // End of the playlist with repeat off. Stop, but leave the track loaded so the
                // screen still says what was playing. Focus goes back: holding it while silent
                // would keep other apps ducked for no reason.
                track?.stop()
                audioFocus.release()
                _state.update { it.copy(playing = false, positionSeconds = it.durationSeconds) }
            }
            // Repeat-one hands back the identical queue: same track, from the top, without a new
            // history entry to step back through.
            advanced == queue -> {
                val playing = track?.restart() ?: false
                _state.update { it.copy(playing = playing, positionSeconds = 0.0) }
            }
            else -> openAndPlay(advanced)
        }
    }

    /** Plays something that is not in the playlist. */
    private fun playTransient(ref: TrackRef, external: Boolean = false) {
        pendingRetry = { playTransient(ref, external) }
        _state.update {
            it.copy(
                transient = ref,
                externalOpen = external,
                // A file from another app is not a digression to come back from.
                diceWaiting = if (external) false else it.diceWaiting,
                searchWaiting = if (external) false else it.searchWaiting,
                randomHasPrevious = if (external) false else randomCursor > 0,
                playing = false,
                positionSeconds = 0.0,
            )
        }
        load(ref)
    }

    private fun openAndPlay(queue: PlayQueue) {
        val ref = queue.current ?: return
        pendingRetry = { openAndPlay(queue) }

        // The queue advances NOW, not inside the coroutine. Two quick presses of next both read the
        // old queue otherwise, and both advance to the same track.
        // Playing from the playlist proper leaves any detour behind.
        _state.update {
            it.copy(
                queue = queue,
                transient = null,
                externalOpen = false,
                resultsQueue = null,
                randomHasPrevious = false,
                playing = false,
                positionSeconds = 0.0,
            )
        }
        scheduleSave()
        load(ref)
    }

    private fun load(ref: TrackRef) {

        openJob?.cancel()
        // A seek belongs to the tune it was made on; a new tune ends it.
        seekGeneration++
        _state.update { it.copy(seekingTo = null, seekSlow = false) }
        openJob = scope.launch {
            track?.close()
            track = null

            // Already read while the previous track was playing, which is the whole point.
            // Removed rather than left: it is about to be the current track, and holding a second
            // copy of it helps nobody.
            val ready = prefetched.remove(ref.id)

            val bytes = ready ?: run {
                _state.update { it.copy(loadingTrack = true) }
                loadBytes(ref)
            }
            _state.update { it.copy(loadingTrack = false) }
            ensureActive()

            if (bytes == null) {
                _state.update { it.copy(message = Message(describeFailure(ref, fetched = false))) }
                if (_state.value.randomMode && !_state.value.externalOpen) skipFailedRandomPick()
                return@launch
            }

            val companions = loadCompanions(ref)
            val result = withContext(Dispatchers.IO) {
                NativeEngine.open(bytes, ref.fileNameOrTitle, companions)
            }
            val opened = result.track
            if (opened == null) {
                val arrived = companions.map { it.first.lowercase() }.toSet()
                val missing = Companions.namesFor(ref.fileNameOrTitle.substringAfterLast('/'))
                    .filter { it.lowercase() !in arrived }
                _state.update {
                    it.copy(
                        message = Message(
                            describeFailure(ref, fetched = true, reason = result.error, missingCompanions = missing)
                        )
                    )
                }
                // **Random walks past a file it cannot open.** A dice roll can land on a `.ym`,
                // which nothing here opens (`docs/STATUS.md` C20), and stopping dead on it turns
                // "surprise me" into "surprise me and then come back to the phone". A playlist
                // stops on purpose, because that file is one the listener chose; a random pick is
                // one nobody chose.
                //
                // Bounded, and the bound is the point: an index full of unplayable rows would
                // otherwise spin through them all. After a few in a row it stops and says so.
                if (_state.value.randomMode && !_state.value.externalOpen) skipFailedRandomPick()
                return@launch
            }
            // A pick that played resets the run of failures.
            failedRandomPicks = 0
            // Something opened, so there is nothing left to try again.
            pendingRetry = null

            // Cancelled while the file was being read: throw away what was opened instead of
            // starting a stream nobody asked for and nobody will stop.
            if (!isActive) {
                opened.close()
                throw CancellationException()
            }

            if (!audioFocus.acquire()) {
                opened.close()
                _state.update { it.copy(message = Message(context.getString(R.string.notice_audio_busy))) }
                return@launch
            }

            track = opened
            // **Described before it starts.** `describe()` reads the decoder's own metadata, and
            // libopenmpt says an object must be touched from one thread at a time -- asking it
            // after the audio callback has begun is the same race the position poll had. Before
            // `start()` there is no other thread to race with.
            val described = opened.describe()

            // **The lengths before the start**, now that one of them decides what the start does
            // (A52). Hashed once for both databases -- HVSC keys on all of the MD5, songdb on its
            // first twelve characters -- and asked in the order `LengthSource` gives: HVSC for a
            // SID, songdb for the rest. What they know is handed to the engine, and UADE works out
            // only the subsongs they do not.
            val md5 = Md5.of(bytes)
            val hvsc = songLengths.forMd5(md5).orEmpty()
            val songdb = if (hvsc.any { it > 0.0 }) emptyList() else songDbLengths.forMd5(md5)
            // And the gaps from what this phone learnt by playing, last: a database's figure is a
            // considered one, ours is a run to the end (A50).
            openSongLengths = LengthSource.fill(LengthSource.known(hvsc, songdb), learnedLengths.forMd5(md5))
            openMd5 = md5
            opened.knownLengths(openSongLengths)

            val started = opened.start()

            // **Said out loud, once, because a listener cannot read logcat.** Backends that
            // synthesise at a fixed rate ask Oboe for it and Oboe is meant to resample; if it
            // declines, every one of those tunes plays sharp with nothing on screen to say so —
            // which is how "this SPC sounds fast" becomes a fact instead of a suspicion. Empty is
            // the normal answer.
            if (started && !reportedSampleRate) {
                val note = runCatching { opened.sampleRateNote() }.getOrDefault("")
                if (note.isNotEmpty()) {
                    reportedSampleRate = true
                    _state.update { it.copy(message = Message(note)) }
                }
            }

            // What the file cannot say about itself, from the database keyed on its hash. A plain
            // `.mod` has nowhere to record a year and no room for an author beyond the sample names
            // people traditionally abuse for it, so for the largest part of this library the fields
            // the app shows are simply empty (`docs/reference/songdb.md`).
            //
            // **The file wins every field it fills.** A lookup on a hash is a good guess about a
            // tune; what the tune says about itself is not a guess at all. So this fills gaps and
            // never overwrites — which also makes a stale or wrong row harmless rather than
            // authoritative.
            val fromDatabase = trackMetadata.forMd5(md5)
            openMetadata = fromDatabase
            // The file first, then the databases, **for the subsong it opened at** -- not the
            // first: a HES opens at its first track with sound, an Amiga tune past a leading
            // subsong of silence (`LengthSource`).
            val openedAt = described["subsong"]?.toIntOrNull() ?: 0
            val duration = LengthSource.forSubsong(opened.durationSeconds(), openSongLengths, openedAt)
            _state.update {
                it.copy(
                    playing = started,
                    metadata = merged(described, fromDatabase),
                    // Where the backend actually opened, which is not always the beginning. A HES
                    // or KSS file often has nothing at track 0, so `GmeBackend` starts at the first
                    // track with sound in it and says so here; assuming zero would leave the
                    // subsong strip pointing at silence while music played.
                    subsong = described["subsong"]?.toIntOrNull() ?: 0,
                    subsongCount = opened.subsongCount().coerceAtLeast(1),
                    durationSeconds = duration,
                    positionSeconds = 0.0,
                    message = if (started) it.message else Message(context.getString(R.string.notice_audio_device_failed)),
                )
            }
            // Recorded with the name the tune calls itself rather than the filename it arrived
            // under, which is why this waits until after the metadata has been read.
            recordPlayed(adoptTitleFrom(described, ref))

            prefetchUpcoming()
        }
    }

    /**
     * The decoder's own metadata, with the database's filling only what it left blank.
     *
     * Kept separate from the read and the write so the rule is one function and one test rather
     * than a condition buried in a state update: **the file wins every field it fills.**
     */
    private fun merged(
        described: Map<String, String>,
        found: com.przunk.protracktor.data.SongDbMetadata.Entry?,
    ): Map<String, String> {
        if (found == null) return described
        val out = described.toMutableMap()
        fun fill(key: String, value: String) {
            if (value.isNotBlank() && out[key].isNullOrBlank()) out[key] = value
        }
        fill("artist", found.author)
        fill("album", found.album)
        fill("publisher", found.publisher)
        // The year needs a different test for "the file said nothing". sc68 emits `year` for every
        // tune and writes `0` or `unknown` when it does not know, which is not blank -- so a blank
        // check let those files block a year the database had. `ReleaseYear` already knows what
        // counts as a year; asking it is the only way the two stay in step.
        if (found.year.isNotBlank() && ReleaseYear.of(out["year"].orEmpty()).isBlank()) {
            out["year"] = found.year
        }
        return out
    }

    /**
     * Takes the tune's real name and author from its metadata, once we have them.
     *
     * These formats carry a title inside, and it is usually better than the filename -- but there is
     * no way to know it without opening the file, and opening every file during a scan is exactly
     * the wait R9 exists to remove. So names improve as tracks are played, which costs nothing and
     * is the only affordable moment.
     *
     * The filename is kept either way, so identity does not shift under a playlist and the metadata
     * view can still say where a track came from.
     */
    private fun adoptTitleFrom(described: Map<String, String>, ref: TrackRef): TrackRef {
        val realTitle = described["title"]?.trim().orEmpty()
        // Backends disagree on which key carries it, so both are asked before giving up.
        val realAuthor = described["artist"]?.trim()?.ifBlank { null }
            ?: described["composer"]?.trim().orEmpty()

        val newTitle = realTitle.ifBlank { ref.title }
        val newAuthor = realAuthor.ifBlank { ref.author }
        val improved = ref.copy(title = newTitle, author = newAuthor, fileName = ref.fileNameOrTitle)
        if (newTitle == ref.title && newAuthor == ref.author) return improved

        _state.update { current ->
            val index = current.queue.tracks.indexOfFirst { it.id == ref.id }
            if (index < 0) return@update current
            val renamed = current.queue.tracks.toMutableList().apply {
                this[index] = this[index].copy(
                    title = newTitle,
                    author = newAuthor,
                    fileName = this[index].fileNameOrTitle,
                )
            }
            current.copy(queue = current.queue.withTracks(renamed))
        }
        // Written straight away rather than waiting for the user to press Save: this is not one of
        // their edits, it is the app learning something, and losing it would mean relearning it on
        // every launch.
        scheduleTrackWrite()
        return improved
    }

    /**
     * Fills in titles and authors for tracks nobody has played yet.
     *
     * These formats carry their real names inside, and until now a list of three hundred stayed a
     * list of filenames until each had been heard. This opens them in the background instead.
     *
     * **It runs only while nothing is loaded**, and that is a hard requirement rather than
     * politeness: sc68 keeps its 68000 emulator in global state, so opening a second instance while
     * one is playing would clobber the one you are listening to. Waiting for idle is also the
     * lowest priority this work should ever have. The consequence — adding a folder mid-playback
     * resolves nothing until you stop — is real and accepted.
     */
    private fun resolveMetadataInBackground() {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            // Only the ones that would learn something. A track whose title already differs from its
            // filename has been resolved before.
            val pending = _state.value.queue.tracks.filter {
                it.author.isBlank() && it.title == it.fileNameOrTitle
            }
            if (pending.isEmpty()) return@launch

            for (ref in pending) {
                ensureActive()
                // Anything the user asked for outranks this, indefinitely.
                while (track != null || _state.value.loadingTrack) delay(IDLE_CHECK_MS)
                delay(RESOLVE_GAP_MS)

                val startedAt = SystemClock.elapsedRealtime()
                val bytes = loadBytes(ref) ?: continue
                ensureActive()
                if (track != null) continue // something started while the file was being read

                // describe() and close() were on the caller's thread, which is the main one --
                // close() destroys a decoder, and for sc68 that is an emulator being torn down.
                // All of it belongs on the background thread, not just the open.
                val companions = loadCompanions(ref)
                val described = withContext(backgroundWork) {
                    val opened = NativeEngine.open(bytes, ref.fileNameOrTitle, companions).track
                        ?: return@withContext null
                    val text = opened.describe()
                    opened.close()
                    text
                } ?: continue

                // Logged rather than guessed at: when this pass is blamed for stutter, the first
                // explanation offered is rarely the right one, and a number settles it.
                android.util.Log.d(
                    "Protracktor",
                    "resolved ${ref.fileNameOrTitle} in ${SystemClock.elapsedRealtime() - startedAt} ms",
                )
                adoptTitleFrom(described, ref)
            }
        }
    }

    /**
     * Which of [ids] are on this phone, each asked its archive's way ([OnPhone]). An archive
     * downloaded whole is looked at once, not once per row.
     */
    private suspend fun onPhoneOf(ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val archives = mutableMapOf<String, Boolean>()
        ids.filterTo(HashSet()) { id ->
            when (val check = OnPhone.checkFor(id)) {
                is OnPhone.Check.Cached -> remoteFiles.isCached(check.url)
                is OnPhone.Check.Archive -> archives.getOrPut(check.catalogueId) {
                    remoteFiles.archiveFile(check.catalogueId).let { it.exists() && it.length() > 0 }
                }
                OnPhone.Check.Local -> true
                OnPhone.Check.Never -> false
            }
        }
    }

    /**
     * The marks of the rows on screen, asked again: after a tune arrives -- an UnExoticA tune brings
     * its whole game with it, so its neighbours change too -- and after a cache or an archive goes.
     */
    private fun refreshOnPhone() {
        scope.launch {
            val ids = _browse.value.tracks.map { it.id }
            val here = onPhoneOf(ids)
            _browse.update { if (it.tracks.map { t -> t.id } == ids) it.copy(cachedHere = here) else it }
        }
    }

    /** The folder fetching ahead is about: a Modland author's folder with its rows, or nothing. */
    private data class AheadKey(val tracks: List<TrackRef>)

    private fun aheadKeyOf(browse: BrowseState): AheadKey? {
        // Only a folder, only when its rows are in, and only Modland: ASMA is whole on the phone, an
        // UnExoticA folder is one archive fetched when its first tune plays, and The Mod Archive has
        // no folders (`docs/PLAN_ROUND_13.md` A55).
        if (browse.openAuthor == null || browse.loading || browse.tracks.isEmpty()) return null
        if (browse.openCatalogue?.id != Modland.id) return null
        return AheadKey(browse.tracks)
    }

    /**
     * Marks what the open folder already holds, then, if the setting and the network allow it,
     * fetches the rest [FolderPrefetch.PARALLEL] at a time. Cancelled by leaving the folder.
     */
    private suspend fun fetchFolderAhead(key: AheadKey?, mode: CacheAhead) {
        if (key == null) {
            // The marks are not this function's to clear: they follow whatever Browse lists.
            _browse.update { it.copy(aheadFetching = emptySet()) }
            return
        }
        val cached = onPhoneOf(key.tracks.map { it.id })
        _browse.update { it.copy(aheadFetching = emptySet()) }
        if (!mode.allows(metered = networkIsMetered())) return

        val wanted = FolderPrefetch.plan(
            key.tracks.map { FolderPrefetch.Candidate(it.id, it.sizeBytes) },
            cached,
        )
        try {
            FolderPrefetch.run(
                urls = wanted,
                fetch = { url -> remoteFiles.fetch(url) != null },
                // Asked before each start, so walking off Wi-Fi stops what has not begun.
                mayContinue = { mode.allows(metered = networkIsMetered()) },
                onStart = { url -> _browse.update { it.copy(aheadFetching = it.aheadFetching + url) } },
                onDone = { url, ok ->
                    _browse.update {
                        it.copy(
                            aheadFetching = it.aheadFetching - url,
                            cachedHere = if (ok) it.cachedHere + url else it.cachedHere,
                        )
                    }
                },
            )
        } finally {
            _browse.update { it.copy(aheadFetching = emptySet()) }
        }
    }

    /**
     * Whether the network is one the user may pay for by the byte. Asked of the system, which counts
     * a phone's hotspot as metered too -- the right answer for "Wi-Fi only", which means "not on my
     * data".
     */
    private fun networkIsMetered(): Boolean =
        runCatching {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        }.getOrElse { error ->
            // Taken as metered, which spends nothing -- but **said**, because a swallowed exception here
            // was the whole of the first build's defect: without ACCESS_NETWORK_STATE this threw every
            // time, and "Wi-Fi only" quietly meant "never".
            android.util.Log.w("Protracktor", "cannot tell whether the network is metered", error)
            true
        }

    /** Reads a track's bytes, from wherever it lives. */
    private suspend fun loadBytes(ref: TrackRef): ByteArray? =
        if (UnExoticA.ENABLED && UnExoticA.pathFrom(ref.id) != null) {
            // The one catalogue whose unit of download is not the tune: every UnExoticA file lives
            // inside its game's `.lha`, so this fetches the archive -- cached like any other
            // download, so the rest of that soundtrack is free -- and unpacks the one member.
            // `docs/PLAN_UNEXOTICA.md` has the shape and the reason it is only three lines here.
            loadFromUnExoticA(UnExoticA.pathFrom(ref.id).orEmpty())?.also { refreshOnPhone() }
        } else if (archiveCatalogueOf(ref.id) != null) {
            // "<catalogue>://<entry>" -- read out of the archive that catalogue shipped as, which is
            // already on disk. No network, which is why an archive catalogue is worth its download.
            remoteFiles.readFromArchive(ref.id.substringBefore("://"), ref.id.substringAfter("://"))
        } else if (ref.id.startsWith("http")) {
            remoteFiles.fetch(ref.id)?.also { refreshOnPhone() }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openInputStream(Uri.parse(ref.id))?.use { it.readBytes() }
                }.getOrNull()
            }
        }

    /**
     * The other files [ref]'s song needs, read from wherever [ref] itself came from.
     *
     * Empty for every single-file format, which is nearly all of them, and that costs one string
     * comparison. For TFMX it is `smpl.name` beside `mdat.name` (`Companions`), and the source
     * decides how "beside" is reached: the next URL in the same Modland directory, the next member
     * of the same UnExoticA archive, the next document in the same granted folder.
     *
     * **A companion that cannot be found is not an error here.** The song is still opened without
     * it, and UADE says it does not recognise it, which is the truth; failing before the decoder is
     * asked would hide which half was missing.
     */
    private suspend fun loadCompanions(ref: TrackRef): List<Pair<String, ByteArray>> {
        if (Companions.namesFor(ref.fileNameOrTitle.substringAfterLast('/')).isEmpty() &&
            Companions.siblingPaths(ref.id).isEmpty()
        ) {
            return emptyList()
        }
        val unexotica = if (UnExoticA.ENABLED) UnExoticA.pathFrom(ref.id) else null
        return when {
            unexotica != null -> {
                val archiveUrl = UnExoticA.archiveUrlFor(unexotica) ?: return emptyList()
                val member = UnExoticA.split(unexotica)?.second ?: return emptyList()
                val archive = remoteFiles.fetch(archiveUrl) ?: return emptyList()
                withContext(backgroundWork) {
                    Companions.siblingPaths(member).mapNotNull { sibling ->
                        Lha.extract(archive, sibling)?.let { sibling.substringAfterLast('/') to it }
                    }
                }
            }
            ref.id.startsWith("http") ->
                Companions.siblingPaths(ref.id).mapNotNull { url ->
                    remoteFiles.fetch(url)?.let { bytes ->
                        Uri.decode(url.substringAfterLast('/')) to bytes
                    }
                }
            ref.id.startsWith("content:") -> withContext(Dispatchers.IO) {
                siblingDocuments(Uri.parse(ref.id)).mapNotNull { (name, uri) ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()?.let { name to it }
                }
            }
            else -> emptyList()
        }
    }

    /**
     * The companions of a document in a granted folder, as (name, uri).
     *
     * **Best effort, and it says so.** The external-storage provider gives documents ids that are
     * paths — `primary:Music/TFMX/mdat.unicorn` — so the sibling is the same id with the last
     * segment renamed. Other providers give opaque ids with nothing to rename, and there this finds
     * nothing: the tune opens alone and UADE refuses it, rather than this walking a whole folder
     * tree on every play to look.
     */
    private fun siblingDocuments(uri: Uri): List<Pair<String, Uri>> = runCatching {
        val documentId = DocumentsContract.getDocumentId(uri)
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val tree = DocumentsContract.buildTreeDocumentUri(uri.authority, treeId)
        Companions.siblingPaths(documentId).map { siblingId ->
            siblingId.substringAfterLast('/').substringAfterLast(':') to
                DocumentsContract.buildDocumentUriUsingTree(tree, siblingId)
        }
    }.getOrDefault(emptyList())

    /**
     * A tune out of an UnExoticA game archive.
     *
     * Two steps and both can fail for reasons worth telling apart in a log: the archive did not
     * arrive, or it arrived and does not hold what the index said it holds. The second means the
     * index and the archive have drifted -- songdb's snapshot is not ExoticA's live tree -- and it
     * is the failure worth watching for once real listening starts.
     */
    private suspend fun loadFromUnExoticA(path: String): ByteArray? {
        val archiveUrl = UnExoticA.archiveUrlFor(path) ?: return null
        val member = UnExoticA.split(path)?.second ?: return null
        val archive = remoteFiles.fetch(archiveUrl) ?: return null
        return withContext(backgroundWork) {
            Lha.extract(archive, member) ?: run {
                android.util.Log.w("Protracktor", "UnExoticA: $member is not in ${archiveUrl}")
                null
            }
        }
    }

    /**
     * Starts reading whatever comes next.
     *
     * Cancelled and restarted whenever the queue moves, because a read of a track the user has
     * already skipped past is a read competing with the one they are waiting for.
     */
    /**
     * Reads ahead whatever is coming, so pressing next costs nothing.
     *
     * In Random that is several tracks, because Random is where the wait was worst: every tune came
     * off the network and nothing had been decided early enough to fetch it in advance. Deciding
     * early is [fillRandomQueue]'s job; this only fetches what has been decided.
     */
    private fun prefetchUpcoming() {
        val wanted = if (_state.value.transient != null) {
            // Nothing is coming after a file handed to us, so nothing is read ahead.
            if (_state.value.externalOpen) emptyList()
            else randomHistory.drop(randomCursor + 1).take(READ_AHEAD)
        } else {
            listOfNotNull(_state.value.queue.upcoming)
        }

        // The eviction rule, and it is exact rather than a budget: what is no longer coming is no
        // longer wanted. Pressing previous or switching playlists drops what was read for the path
        // not taken, at the moment it stops being the path.
        prefetched.keys.retainAll(wanted.mapTo(mutableSetOf()) { it.id })

        val missing = wanted.filter { it.id !in prefetched }
        if (missing.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // **Together, not one after another.** These fetches spend nearly all their time
            // waiting on a network, so reading three in turn makes the third arrive three round
            // trips late -- which is the wait this whole thing exists to remove. Serial reads here
            // are visible on a device without needing a measurement.
            missing.forEach { ref ->
                launch {
                    val bytes = loadBytes(ref) ?: return@launch
                    // A large file is not worth holding in memory to save a second; these formats
                    // are kilobytes and anything of this size is not one of them.
                    if (bytes.size > MAX_PREFETCH_BYTES) return@launch
                    // Back on the scope's dispatcher (main), so the budget check and the write
                    // cannot interleave with another fetch finishing. The second bound is on the
                    // total, because three files each under the per-file limit are not under it.
                    if (prefetched.values.sumOf { it.size } + bytes.size <= MAX_PREFETCH_TOTAL_BYTES) {
                        prefetched[ref.id] = bytes
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        // **The open in flight goes too.** Everything that calls this is the user moving on —
        // leaving Random for the playlist, say — and a fetch already running would finish
        // afterwards and start the tune nobody is asking for any more, over a screen naming a
        // different one.
        openJob?.cancel()
        openJob = null
        prefetchJob?.cancel()
        prefetched.clear()
        track?.close()
        track = null
        // The retry goes with it. Everything that calls this is the user moving on -- switching
        // playlist, closing the player -- and a retry that outlived the move would fire the next
        // time they pressed play, substituting a track they had abandoned for the one they were
        // asking for. That is the surprise `pendingRetry` was written to remove, arriving from the
        // other side.
        pendingRetry = null
        audioFocus.release()
    }

    /** Releases the module. The process is going away; nothing owns native memory after this. */
    fun release() {
        resolveJob?.cancel()
        openJob?.cancel()
        stopPlayback()
    }

}
