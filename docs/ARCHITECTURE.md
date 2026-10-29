# Architecture

## WEB library download lifetimes — branch candidate, 2026-10-29

LibraryDownloads is the common owner for the approved five-step plan, step locks, immutable
progress and the selected operation's AbortController. Individual actions use the same providers.
An already-running independent step is observed without borrowing its cancellation authority;
completion is reconciled only into the run that requested it. Retry retains successful rows and
restarts failed/pending ones. Stopping does not undo a completed atomic publication.

DownloadPickerView retains its controls across progress and live language changes. Closing the
overlay or pressing Escape returns to the original view/opener and ends presentation only.
Tab/Shift-Tab are scoped to the actual dialog, including Close. Source/FIFO/editor and the audio host have
independent lifetimes. dataset-download captures a previous installation before network/parse
work and publishes rows plus metadata through catalogue.replacePrefix's guarded transaction.
Quota/cancellation cannot leave an empty or partially replaced dataset. Explicit index downloads
wait for earlier local compatibility work before capturing their guard; that work remains shared
when one waiting download is cancelled.

The approved WEB plan contains Modland/favourites, complete ASMA, HVSC durations and songdb
credits only. Its 47 MiB estimate omits excluded songdb/UADE durations. WEB uses the temporarily
permitted canonical HVSC website file because native's byte-identical DeepSID copy lacks CORS.
No proxy, schema or dependency is added. [Sources, parity and verification](WEB_IMPLEMENTATION.md#w22--selected-library-downloads-2026-10-29).

## WEB speculative music ownership — branch candidate, 2026-10-29

PlaybackReadAhead owns completed, consume-on-open RAM buffers (4 MiB/file, 8 MiB total).
PlaybackController's native candidate rule is represented once by readAheadCandidates: distinct
pending manual FIFO, then source successors, at most three files. The app selects successors;
the model owns eviction, cancellation and publication generations. RemoteMusic owns shared
network work and retained bytes; retiring a speculative waiter cannot cancel an independent
foreground load. Random has no second byte cache. A weak memo of the next shuffled PlayOrder
lap lets prediction and actual Next share a permutation without mutating historical state.

FolderPrefetch owns a loaded Modland author-folder snapshot and its ordered three-worker,
100 MiB plan. Cached files do not spend that plan. Policy is rechecked before each start;
replacement rejects late progress and cancels waiters. Existing row slots show progress without
recreating the list. Queue overlays retain the underlying Browse lifetime; leaving Browse ends it.

CacheAheadPreferences persists only explicit Wi-Fi only / Always / Off changes. That choice gates
bulk folder work, not the next files of playback already selected. Android uses unmetered status;
the [browser specification](https://wicg.github.io/netinfo/) supplies no equivalent authority.
The adapter therefore reports unknown: Wi-Fi only visibly pauses folder work, Always opts in.
Physical bearer names or throughput classes never silently authorize data spending. Startup does
not fetch speculative music or write defaults. [Reference and checks](WEB_IMPLEMENTATION.md#w19--shared-playback-read-ahead-and-folder-policy-2026-10-29).

## WEB playback preferences — branch candidate, 2026-10-28

`PlaybackPreferences` reads shuffle, repeat and the existing play-all key synchronously before
constructing either workspace order. Unknown values fall back without writes; partial storage
refusal preserves other readable keys and returns its failure. Each explicit action persists only
its own key. A refused write leaves the in-memory choice intact and is shown in the snackbar.
The actual context owns transport modes independently of the visible playlist; these are not edits.

The Settings checkbox and Now Playing switch share `setPlayAllSubsongs`; `renderPlaybackModes`
updates both and the transport mode marks, accessible repeat name and One glyph. Restoring state
never invokes a click or decoder. The startup restore captures its own editor generation and uses
`setQueue` without persistence, avoiding initialization writes. A later editor selection wins over
late saved reads; existing explicit URL handoff follows restore and keeps its established policy.
No schema change, autoplay, phone-byte retention change or Android production modification.


## WEB subsong following — branch candidate, 2026-10-28

`SubsongStripView` keeps keyed numeric chips across current-only updates. Opening a file/count or
freshly opening Now Playing reveals its actual current. Later advances move only when the previous
chip was at least partly visible and the new one is outside the viewport. Manually browsed distant
regions stay put. Frames belong to a strip visit; closing, replacement, invalid/single counts and
new loads retire old work and controls. Direct alignment avoids an obsolete smooth scroll
continuing after a newer selection; it also respects reduced motion without another animation.

The pure follow/selection rules mirror native `SubsongStrip`/`PlaybackController.selectSubsong`.
Actual-current, hidden and retired chip actions are refused; marks come from decoder replies.
Information remains read-only rather than sending inspected-file indices to the audible decoder.
An independent description generation rejects late results/errors after leaving or reopening the
live view. Dock expansion uses the actual metadata/subsong snapshot, updated on live replies,
so inspecting another file cannot resurrect its controls or an earlier tune's metadata. Browser
system-metadata refresh remains W25; no new transport, storage, mode or Android behaviour is added.

## WEB Browse positions — branch candidate, 2026-10-28

`BrowseScroll` holds per-session level offsets and pending departed-row identities. Its native-
derived restore decisions are Nothing, Wait for loading/empty rows, Scroll to the exact current
identity, or Forget a genuinely missing row. There is no old-index fallback. The level key is
shared with Browse selection, including producing Search query/scope and History query/page.

`BrowseScrollView` snapshots before DOM replacement and applies only the latest completed visible
visit. Loading/clamped intermediate lists cannot overwrite a saved offset. Queue-hidden updates
wait for the same owner to become visible; same-level refresh restores its captured offset.
Folder/catalogue/domain/bookmark actions record stable identities through both pointer and
keyboard paths. Fresh Browse clears the session; fresh History/query clears only History state,
while paging keeps independent offsets. Browser restoration aligns the target directly; physical
scroll/animation feel remains a browser check.

A destination chooser opened from Browse preserves its underlying models and position. Saved
places starts its refresh before attempting restoration, so old membership cannot consume a
return marker before a newly inserted bookmark changes the row's index. This changes no playback,
playlist, manual-request, storage or source-navigation ownership.

## WEB dock identity — branch candidate, 2026-10-28

The identity card has independent title/Add and metadata/Tune rows. A permanent 48 by 32 px Add
slot prevents changes of source or subsong count from shifting the save target or narrowing the
title. Its ordinary browser minimum remains 64 px, with no extra top spacer; the footer can wrap
and the card can grow with text. Queue remains outside the card, using the shared transport.

The counter derives only from actual opened decoder state in the common playback presenter;
inspecting another row cannot overwrite it. A new load, failure or empty source hides it. Both
subsong modes display the full localized Tune N of M label. Existing metadata/marquee logic is
reused. Card/Keep/Queue have independent pointer and keyboard ownership, with the expanded state
published for assistive technology. This is presentation work, not a new transport state owner.

## WEB Browse bulk selection — branch candidate, 2026-10-28

`TrackSelection` owns selected URL identities independently of the playlist editor and playback.
Its owner identifies the producing folder, submitted Search query/scope or History query/page.
Membership refresh intersects IDs; a new owner clears them. Captures are immutable, in source
order, and borrow held buffers without transferring them. Native bulk operations use source
references rather than the learned-title presentation overlay.

`BrowseSelectionView` decorates stable playable rows with checkboxes inside their reserved leading
slot. Long press, row tap, checkbox, keyboard and Select all reach the same model. Context actions,
swipe and playback scroll-follow are suspended while selecting; first Back/Escape clears it.
Queue overlays preserve the underlying visit, while actual navigation retires it. Unavailable
phone-only placeholders and folder/action rows are not selectable in WEB. Selection never owns
transport. Button/field key handling precedes global transport shortcuts.

Bulk Add uses the existing editor draft, serialized other-playlist writes or fresh atomic creation;
Share uses portable references. The destination chooser captures its batch before clearing
selection. Reads, feedback and completion are scoped to its originating visit/request; failed
writes retain a retryable snapshot. From the phone stays protected. The same per-playlist writer
serializes Add with Save/Rename/Delete, including a fresh destination read before append.

Actually held local buffers from successful owned-playlist Save/Add/Save As and import are retained
for the tab lifetime, so switching editors does not strand newly saved references. Working drafts
keep their buffers; reference serialization removes them at the storage/share boundary. Phone
handoff autosaves do not retain every replaced phone payload. Nothing persists audio or adds a
scanner, bulk manual queue operation, export renderer, schema or dependency.

## WEB playlist-file transfer — branch candidate, 2026-10-28

`PlaylistTransfer` has no editor/transport ownership. It captures selected draft references for
active exports and reads persisted contents for another playlist, retaining the initial label.
Import resolves exact known ids, supported remote/archive identities and then a matching held
filename/optional positive size. It creates one new playlist atomically; found counts describe
resolved offerings before first-occurrence deduplication. Failed creation never replaces another
identity. The picker/download are adapters; cancellation and refresh failure are distinct from
unreadable, empty, partial and failed imports.

`playlist-file.js` owns native-compatible M3U8 metadata, signed 64-bit decimal sizes and reference
serialization. It preserves the wire format, except sanitizing metadata newlines so they cannot
inject extra entries. The reader corrects the native empty-artist delimiter bug: native writes
` - Title` but currently reads the delimiter back into the title. The common case records both
native and corrected WEB results; other shared format cases match. Android code is unchanged.
Browser imports of native `asma://` ids use the existing archive path encoder and canonical
HTTPS URLs; remote presentation uses the browser's author-folder metadata convention.

Held owned-playlist buffers live only for the tab lifetime. Search, list selection/discard and
startup share hydration from existing runtime owners plus those retained buffers. An enumerable private
symbol carries the original stored availability through immutable copies; reference serialization
keeps it and strips buffers/symbols. A derived missing-file flag must never silently rewrite a
protected phone list during a pending index save. With no held bytes, nonnetwork references are
visibly unavailable. Empty/detached buffers are discarded; persisted/local metadata alone never
proves playable bytes. No schema change, file scanner, filesystem grant or audio persistence.

## WEB live row reorder — branch candidate, 2026-10-28

`RowReorder` is shared by editable playlists and the pending manual block. Whole-row crossings
commit immediately, as native does; gesture cancellation stops further movement and retains
those edits. Incremental pointer/scroll deltas retain residual displacement across moves and
partial reversals. Pending requests clamp to their block; the playlist uses its actual `main`
scroll container. Frames bound edge scrolling to 480 pixels/s with a 50 ms frame cap.

Views freeze physical row order during a gesture while updating the logical order and applying
transforms, then synchronize on release/cancellation with pinned numeric scroll. They retain the
captured row/handle and consume its generated click. Changed membership, context or visibility
ends a stale gesture. Keyboard arrows and labelled menu actions reach the same domain move.
The playlist renderer reserves existing object occurrences before matching replacement metadata
by URL, using linear bags; legacy duplicate URLs keep independent handles.

`moveEditorTrack` applies an index permutation to the draft, actual history and source occurrence
IDs. It avoids URL-to-last-index collapse for existing pasted duplicates. A move marks the draft
and re-deals shuffle from the retained current occurrence. The active source changes only when
it belongs to that editor. No decoder command, pending-manual edit or playback restart occurs;
Save/Discard remain the selected draft's responsibility. Physical touch, capture and painting
still require a real browser check.

## WEB scoped library search — branch candidate, 2026-10-28

`SearchBrowser` owns explicit-submit query/scope state and request cancellation. An immutable
producing result stays separate from pending field/chip edits. Scope-kind changes clear stale
rows; same-kind chips retain them until Search. Back widens a narrowed scope before leaving;
reentry retains the answer. Unchanged results keep their DOM row nodes while the field and scope
presentation update. `SearchScopesView` owns controls, labels and verified availability, not
search execution. Selection/focus never triggers a lookup or changes transport.

`LibrarySearch` ports per-source 2000-row limits, saved-copy precedence, native matching fields
and source ordering. Browser-saved references supply the supported subset of Android's Local
library; the UI calls it Saved playlists and offers no folder scanner. Independent playlist
copies are canonicalized by latest saved copy before the cap and ordered by binary title, because
Android has one globally unique track row. Filtering never mutates playlists or drafts.
`SearchSelection` retains only the first 2000 globally sorted catalogue hits while counting all.
`visitTitleShards` reads current metadata and playable title shards in one readonly transaction;
word matches span title/author, platforms use the existing shared map. This avoids full-archive
materialization and mixed metadata/result snapshots. `readResult` shares completion/cancellation
handling with History, including cancellation after a transaction has already finished.

Source-route DTOs capture producing scope and summary alongside query/membership. Restoration
uses a local immutable DTO while model publication can invalidate the visible route; current
index/cache/download facts remain independent. Queue overlays preserve both Search and History
visits. Scope availability follows committed playlist/index changes and window focus, keeping
verified choices during refresh and explaining pending/error/empty states distinctly.

Saved local references recover bytes only from the existing in-session editor/context/manual
owners. Playback preserves that buffer; persistence and links retain references, never bytes.
Missing copies remain visible with a reason and no replay action. Approved browser catalogues
remain Modland/ASMA; no live archive, AMP, proxy, filesystem or new dependency is introduced.
[Contract and verification](WEB_IMPLEMENTATION.md#w11--search-scopelibrary-analysis-2026-10-28).

## WEB live History filter — branch candidate, 2026-10-28

`HistoryBrowser` owns raw query, matching page/count/rows and loading/failure state; it does not
own playback or recent general searches. Requests carry a generation and AbortController,
edits debounce 200 ms, fresh entries reset query/page/scroll, and source return restores the
producing snapshot. A Queue overlay preserves the underlying History visit. Current failures
permit retry; stale failures and rows cannot publish after replacement or navigation.

`played.read` waits for the ordered History writer. Blank queries obtain count/page in one
readonly transaction; filtered cursors match title/author/filename literally and retain only a
requested hundred plus the last partial page. Cancellation aborts active transactions and
refuses publication even if completion wins the abort race. No schema change or bulk History
allocation is introduced. `query-text.js` shares the precise Kotlin whitespace/simple-case rules
with recent searches, while `history-filter.js` owns phrase matching and bounded paging.
`HistoryFilterParityTest` and JS consume one declared TSV input. Android production is unchanged.
[Reference, checklist and evidence](WEB_IMPLEMENTATION.md#w10--live-history-search-analysis-2026-10-28).

## WEB recent searches — branch candidate, 2026-10-28

`RecentSearchHistory` owns bounded query history and serialized settings writes, separately from
playback History and Browse snapshots. `query-text.js` and `recent-searches.js` port Kotlin trimming, case and
prefix rules; a common TSV runs against both implementations. The existing settings store gains
one key, with no schema migration. Read failures are retried before writes; write failures retain
the last persisted presentation and leave the ordinary search usable.

`SearchSuggestions` binds existing input/submit/listbox nodes. It owns dropdown eligibility,
keyboard/pointer selection and available-height placement, but not searches, playback or storage.
Submitting closes focus before invoking the page; asynchronous updates only refresh eligible
views. `app.js` distinguishes typed text from an acknowledged submitted query so a redraw cannot
silently launch a newly typed lookup. Search-source snapshots keep the query that produced their
rows. The integrated Search icon follows the native field's accessible-label convention; the
options are plain text. Browser rendering/keyboard acceptance remains unchecked.

## Louder normalization target — accepted and merged, 2026-10-27

`VolumeNormalizer` uses a named -18 LUFS target on develop, merged as **641b875**; the released
policy remains -23 LUFS. Measurements and cached profiles are unchanged, so gain is recalculated
from existing input statistics without invalidation or a new schema. Retain partial/complete
boost caps, peak headroom, fixed-trim transitions and independent focus ducking. The change is
an accepted Android playback policy, with general listening approval and per-case checks still
unreported. It adds no global decoder boost or WEB parity implementation.
[Measurements and acceptance limits](VOLUME_NORMALIZATION.md#louder-target-trial--2026-10-27-unmerged).

## WEB playback ownership — branch implementation, 2026-10-27

W01-W02 and W04-W05 are implemented on `feature/web-feature-parity`; integration and real-browser acceptance
remain separate. [Scope and host evidence](WEB_IMPLEMENTATION.md).

`PlaybackWorkspace` owns the selected editor and an independent immutable `PlaybackContext`.
`PlayOrder` owns finite traversal, modes and actual shuffle history. `PlaybackSchedule` adds
one-shot, origin-aware manual occurrences without editing that source or its playlist document.
`PlaybackPlan` combines them; `playback-steps.js` interprets a domain step into state plus an
explicit decoder effect. Only the current occurrence supplies audio, metadata, History and retry
identity. Read-only transport availability cannot consume RNG state. Playlist persistence reads
the selected editor, with serialized document writes and content/revision guards.

`app.js` owns runtime jobs and executes effects; its legacy queue/index arrays are presentation
mirrors. Actual open-job ownership is distinct from schedule/speculative-Random ownership, so
Clear can preserve a loading file while retiring old speculation. Undo restores content/context,
retains live editor changes and never rewinds audio.

`QueueView` owns keyed DOM presentation only. `PendingReorder` now aliases the shared `RowReorder`,
which commits occurrence-ID moves at each full-row crossing within the pending block; it never moves source/current rows or owns transport.
Shared row gestures arbitrate horizontal intent before consumption, preserve vertical scrolling,
cancel additional pointers and suppress abandoned click/context-menu events. Menus and keyboard
actions use the same callbacks as pointer controls. Departures alone fade; remaining rows move
without a whole-list appearance animation. Exact visual/gesture behaviour still needs a browser.

This adds no Android production/schema/native changes or dependencies. W03 still needs W10/W11 route wiring; W06-W25
remain open; the old WEB plan's simplified queue is no longer the W01-W02 branch contract.

W03 adds `source-navigation.js` and an immutable route on `PlaybackContext`. The route holds
view/query/result fields, separately from actual decoder jobs and live catalogue/storage owners.
Header navigation follows the parked source, never its manual interruption. Guarded editor reads
preserve the active order; Discard authorizes abandoning a draft after destination validation,
rather than erasing it before an asynchronous read. Source, panel and editor generations retire
old completions. Browse result closures bind their original query, and older reads cannot append
to a restored route. This checkpoint passes the combined navigation
host gate; W10/W11 query/scope wiring remains pending. [Evidence](WEB_IMPLEMENTATION.md#w03-source-navigation-checkpoint--2026-10-27).

W04 separates the remembered Random record/view from the active source. DOM-free `RandomHistory`
ports the native heard boundary, cursor, serial sampling and generation/cancellation rules;
unheard preparation cannot enter the visible record. The active context remains transport owner
when another view is opened. Explicit row selection and a deliberate dice reset activate Random.
The shared source cursor is synchronized when Queue selects a heard occurrence. A reset retains
manual FIFO requests; Clear retains heard history and an already adopted byte promise while
retiring speculation. `random-dice.js` owns pointer/accessibility activation and reduced-motion
rotation only. Browse detours depend on the visited route rather than the audio source.
The combined navigation host gate passes; actual browser acceptance remains unchecked.

W05 adds the screen-owned `RandomFilterState` and a separate `RandomFiltersView`. Native platform
data is generated and checked against `Platforms.kt`; classification is shared by summaries and
scoped sampling. `random-catalogue.js` joins curated Modland paths with retained playable buckets,
keeps listed/matching counts separate, and keys availability to actual dataset identities.
`catalogue.replacePrefix` commits records and summaries together, optionally against a metadata
snapshot; playability stamps share that guarded write. Committed mutation notifications invalidate
availability/read-ahead without replacing the current playback job. Old indexes use bounded scans;
new indexes carry summaries. Scope preference/read/save ownership is independent of playback/view
ownership. This checkpoint passes the combined host gate; browser acceptance remains unchecked.

## B54 Floating controls — gated experiment, 2026-10-26

The phone trial was accepted for experimental integration only and merged as **afab610**, 2026-10-26.
`FloatingControls.ENABLED`
defaults to `false`: the dock action and permission flow are absent, service host construction
and its observer are omitted, and the command gateway ignores activation. This is a compile-time
decision, with no runtime preference. The following describes the retained enabled component.

[Interaction, lifecycle and acceptance](FLOATING_CONTROLS.md). `PlaybackService` owns the optional
`FloatingControlsWindow`; closing the window never pauses or replaces playback. Activation is
explicit and session-only. The window uses a display-specific context, a bounded non-focusable
application-overlay window, and its own disposable Compose lifecycle/saved-state owner.

`FloatingControlsState` projects existing transport availability and `PlaylistHeart`; only an
enabled panel subscribes to that projection. Its callbacks use the same controller as the media
session and notification. It adds no queue, engine, database schema or dependency.
`FloatingPanelPosition` stores normalized travel coordinates and chooses the nearest edge;
`FloatingPanelDrag` uses screen coordinates and touch slop. `FloatingDragContainer` observes native
events before Compose and cancels child clicks/holds once the whole panel starts moving.
The service host owns a temporary bounded, non-touchable dismissal indicator and a cancellable
edge animation. Dropping on the indicator hides without changing playback; cancellation only
ends the drag. Previous/Next holds use the dock's file operations and availability projection.
A hold on the dock Play/Pause control invokes the permission flow in the
application shell; its result launcher remains registered independently of Now Playing visibility.
EN/PL presentation follows the selected app language. Practical phone operation was reported
satisfactory; detailed device cases were not separately enumerated. Host checks do not establish
visibility over another app. Public availability remains deferred behind the disabled constant.

## Volume normalization — accepted and merged, 2026-10-23

Pure `LoudnessMeter`/`VolumeNormalizer` consume float stereo after the existing width mixer and
before independent focus ducking. `LoudnessPreparation` serializes optional budgeted prefix
analysis on worker threads using a separate backend. `LoudnessProfiles` stores bounded,
disposable content/subsong/mix/decoder-version statistics, never user state. Controller hooks
bind results to the current native track and capture longer listening measurements. Atomic
commands carry their measured stereo mode and preserve real-time ownership; a width change
resets statistics but retains the audible gain for a smooth transition. Cache snapshots skip
busy decoders rather than blocking the UI behind a seek. Off remains an exact bypass after a
live transition settles. There is no
new service, database schema or dependency. [Contract and verification](VOLUME_NORMALIZATION.md).
An opening decoder remains caller-private across optional analysis; `prepareOrClose` owns cleanup
on cancellation/failure, including a native allocation whose worker result was not delivered.
Only successful preparation adopts the handle and acquires audio focus. This keeps controls out
of the preparation window and prevents an old cancellation from closing a newer playing track.
Android metadata publishes the backend's actual initial subsong before its multiline message,
so normalization and the existing selection consumers cannot guess a different tune. Clean
source d68bfad passed 1163 host tests/full gate/lint (0 errors/73 unchanged warnings). Initial
non-default mix selection preserves full-measurement eligibility; only a change after samples
invalidates it. Merged as **4aab46e**, with the same tree as the checked source, after phone
feedback reported comparable loudness for a quiet/loud pair. Default Off and the fixed -23 LUFS
policy remain unchanged; Settings drops experimental wording and retains first-play/export
limits. Cleanup was integrated without an APK; the subsequent develop device build is recorded
in the contract. Earlier APK 1.3.0 (1297) and the unchanged native code's 53 ASan/UBSan checks
remain historical evidence in the contract. Individual device
checks and power/accessibility outcomes are not inferred from integration approval.

## Queue source navigation — accepted and merged, 2026-10-22

`QueueSourceNavigation` resolves typed destinations and restores only captured Browse route/result
fields over current shared resources. The controller retires obsolete Browse reads and routes
without entering playback or rerolling Random. Playlist returns go through `PlaylistSession`'s
guarded activation and successful-save callback. Because the selected source editor is live-linked,
`PlaylistSessionState` reveals the active order, marking membership/order differences from disk as
an unsaved draft rather than silently replacing playback or writing to storage. The UI reveals a
destination only after navigation succeeds. No new persistence, service or playback owner is added.
Source b225ff9 passed the full 1147-test host gate/lint; release APK 1.3.0 (1290) was accepted
after practical phone testing. Merged as **70e1a61**, with identical application sources; no new
build or test run for the unchanged merge tree. Granular checklist/TalkBack outcomes remain
unreported. [Contract and evidence](QUEUE_SOURCE_NAVIGATION.md).

## Manual playback queue — accepted and merged, 2026-10-21

Merged as **3bc5a98** after overall phone trial acceptance. Application sources equal the
verified APK source **1a1503f** (1096 tests/full gates/lint); only documentation differs.
[Integration and evidence](QUEUE_BRANCH_REVIEW.md#acceptance-and-integration--2026-10-21).
Earlier handoff-specific pending statements below remain historical, not the current merge state.

`feature/manual-playback-queue` extends the accepted context below. `ActiveQueue` now means source
identity/order/cursor; it is not always the file actually loading or sounding. Pure immutable
`PlaybackSchedule` owns pending requests (FIFO until explicitly reordered), stable occurrence IDs and one actual occurrence,
not a second backward/forward history. `PlayerUiState.current` projects that occurrence, falling back
to context when none is selected. An explicit absent source prevents a never-started editor playlist
from silently becoming continuation for manual playback.

Controller file transport and natural completion enter that scheduler after the existing subsong
layer. It chooses pending before source continuation; Repeat One retains the actual tune. Source
PlayQueue/RandomHistory own Previous, which returns from a manual request to the parked source.
Consumed manual rows never re-enter history. Explicit source Play replaces the context but preserves pending; mere
navigation or playlist selection resets neither. Retry binds to playback epoch and occurrence.
History recording uses actual origin through one pure policy, not whichever source is parked.

The third iteration projects one list: actual source order with a marked manual block immediately
after its parked position. Source occurrence IDs and an omission set support session-only removal
without filtering the editor's persistence data, including equal Random files. Left removes either
origin with Undo; right enqueues a copy, tap plays and row long-press opens Info.
The owner-approved follow-up adds an ID-based move only inside the waiting manual block, with
a trailing grip and accessible earlier/later actions. Current/source rows cannot move. A
gesture-local ID order bridges input events before Flow recomposition; membership/generation
changes cancel dragging. Edge scrolling stays within the manual block and begins only after
movement. Grip gestures latch out parent horizontal swipe actions. Saved source data, actual
playback, generation and request origin remain unchanged by reorder.
The presentation follow-up uses stable-key lazy-item fades/placement, not delayed scheduler
consumption; placement yields to active grip movement. Info requests shared View haptics and the
source header offers a labelled Help dialog instead of permanent instructions.
The scroll-first correction separates pure direction/distance intent from pointer consumption.
Movement stays available to the vertical parent until deliberate horizontal intent wins; a
latched vertical rejection cannot become a later swipe. Initial-pass abandoned-release handling
prevents an under-threshold drag from playing the row. [Host verification passed](QUEUE_SWIPE_INTENT.md);
device touch comfort is unchecked.
[Presentation verification](QUEUE_PRESENTATION_POLISH.md) passed full host gates/lint; phone
behaviour is unchecked.
Source actions carry their generation to reject stale UI events. All other track lists share the
right-enqueue gesture, disabled during selection/reorder. Only the actual occurrence is highlighted.
The animated panel occupies Scaffold content above the same operable dock, rather than a separate
modal window; the shared snackbar remains reachable. Underlying screen semantics are hidden while
the panel is open. Now Playing subtitle/actual-origin rules are shared with notification and History.

No new service/player, dependency, schema or persistence is introduced. Pending/omissions die with
the process; existing paused selected-playlist restoration remains. AMP/C111/C112/web queue work is
excluded. [Current contract/evidence](QUEUE_UNIFIED_LIST.md) supersedes the earlier two-tab design;
the [implementation report](MANUAL_QUEUE_IMPLEMENTATION.md) and
[second-iteration audit](MANUAL_QUEUE_SECOND_ITERATION.md) retain historical evidence only.
Third-iteration clean source `613b154` passed 1028 host tests/full gates and lint (0 errors,
72 unchanged warnings); minified test-signed APK 1.2.0 (1245) is ready. Host/artifact checks do not
establish phone behaviour or merge acceptance; [handoff](QUEUE_UNIFIED_LIST.md#final-verification-and-handoff).
Those counts/artifact precede the reorder/long-press follow-up. Its clean source `0082064` passed
1037 tests/full gates and lint (0 errors/72 unchanged warnings); APK 1.2.0 (1248) is ready,
unmerged and without phone acceptance. [Follow-up report](QUEUE_MANUAL_REORDER.md).
The presentation handoff is clean source **6043902**, APK **1.2.0 (1250)**, with **1043
tests / 136 suites**, full gates and lint (0 errors/72 unchanged warnings). Its predecessor counts
above remain historical. [Presentation handoff](QUEUE_PRESENTATION_POLISH.md#final-verification-and-apk);
phone checks and acceptance remain pending, with no merge. The scroll-first handoff is
source **f6a5ee4**, APK **1.2.0 (1252)**, with **1056 tests / 137 suites**, all host gates/lint
(0 errors/72 unchanged warnings) and artifact inspection passed.
[Swipe handoff](QUEUE_SWIPE_INTENT.md#final-verification-and-apk). The subsequently approved
[Clear queue](QUEUE_CLEAR.md) is implemented: a pure immutable `PlaybackPlan` detaches the source
and pending requests while retaining actual current identity as a one-shot. The controller
invalidates speculative intent without closing current audio; guarded Undo restores transport
state directly, never through a snapshot replacing editor tracks/position or restarting audio.
When Undo relinks the selected source playlist, its live editor mirrors only transport modes
so the active-source getter cannot revive the pre-Clear Repeat/Shuffle settings.
Explicit Play can draw after Undo restores Random before its first pick; Undo itself stays idle.
Historical Clear handoff: source **1aec1aa**, APK **1.2.0 (1255)**, **1080 tests / 139 suites**,
complete gates/lint (0 errors/72 unchanged warnings) and artifact checks passed.
[Clear handoff](QUEUE_CLEAR.md#final-verification-and-apk). Device behaviour and merge acceptance
remain unchecked.

C113/C114 keep standalone manual lists aligned: indent manual content only while source rows
exist, with highlight and tap/hold outside that inset. C115 keeps the playlist swipe subtree
mounted during a grip, gates competing swipes and moves/raises the outer lazy item rather than
its clipped child. A post-composition numeric viewport pin prevents keyed-item anchoring from
following the first reordered row; gesture-local order handles moves before recomposition.
No scheduler, transport or saved-data policy changes in that correction. Historical handoff: source
**aca3cd6**, APK **1.2.0 (1258)**, **1090 tests / 141 suites**, complete gates/lint (0 errors /
72 unchanged warnings) and artifact inspection passed. [Row-regression handoff](QUEUE_ROW_REGRESSIONS.md#final-verification-and-apk).
Device behaviour and merge acceptance remain unchecked.

The C116 follow-up disables Queue appearance fading: Compose's final scroll snap resets item
animation keys and can otherwise fade surviving rows as new arrivals. Placement and departure
remain animated; shared absolute-target following is unchanged. The complete branch audit also
corrects C117: omission traversal uses PlayQueue's retained-history-plus-one-lap budget, not
source length alone. EN/PL Help identifies manual requests independently of indentation.
Accepted APK source **1a1503f**, APK **1.2.0 (1261)**, passed **1096 tests / 142 suites**,
all forced gates/lint (0 errors/72 unchanged warnings) and artifact inspection.
[Audit and artifact](QUEUE_BRANCH_REVIEW.md#final-verification-and-apk). Overall phone trial
acceptance and integration are recorded above; granular device results remain unreported.

## Active playback queue — accepted and merged, 2026-10-19

Implemented on `feature/playback-queue-experiment`, `ActiveQueue` and exhaustive `QueueSource`/`QueueStep`
types own file-level transport decisions outside Android. `PlayQueue` retains finite-order,
repeat/shuffle/history rules; `RandomHistory` supplies its growing heard record and private
speculation. `PlaybackController` executes typed steps against the existing decoder/focus/adapters.

`PlayerUiState.queue` is the selected playlist's editable draft, not necessarily what is playing.
`playback` records the active source/order. `activePlayback` links to the draft only while that
same playlist is selected; selecting another playlist captures the old source's order and does
not stop audio. Derived current/mode/capability properties share this single effective owner.
`withPlayback` publishes explicit playback selection and synchronizes the source playlist cursor
only when that playlist is visible. Playlist persistence still snapshots the selected ID/draft.

Collection activation captures the loaded results and History origin; opening a folder or another
view cannot transfer transport. Random can remain remembered while a different source plays.
Playback intent epochs prevent a delayed Random query from stealing a newer source. Queue UI
uses the existing track/info presentation without selection editing, removal or reordering.

Device-feedback refinement (2026-10-19): Queue is a sibling of the expandable dock card, not a
child action. Its fresh list state starts at the active cursor; `KeepRowInView` still governs later
changes without resetting other lists on arrival. Shell Back routing follows the visible screen's
priority (scanner, Settings, Browse, Random), with one shared callback for Random's arrow/system
Back to Browse. This changes navigation only, never the playback owner.

Schema remains 23; temporary queues/Random records remain in memory. Startup still restores the
selected playlist paused, not every temporary source. The retained-but-unavailable AMP index guard
preserves the parallel trial's data and must be reconciled with AMP availability when that branch
is integrated. The temporary APK code-1221 exception has ended; new builds use the commit count.

The owner accepted queue behaviour on 2026-10-19. After normal-version cleanup and a fresh full gate,
it was merged into develop as `7cab210`; the merge tree equals tested source `069b238`.
This supersedes the older playback/session descriptions below. AMP remains separately deferred.
[Full contract and evidence](QUEUE_EXPERIMENT.md).

This document records what has been decided and **why**. A decision without its reason gets
re-litigated every few weeks, or worse, applied in a situation it was never meant for.

The application is built and released as 1.0.0. The numbered sections preserve the decisions made
as it grew; dated measurements and superseded designs are historical evidence, not a statement
that every original proposal is the current implementation. The following ownership map records
the current boundaries and the proposed post-1.0.0 extractions.

---

## Current ownership and proposed boundaries — 2026-10-05

Baseline: `1455422`, the accepted `develop` when `feature/architecture-stabilization` began.
The release remains `v1.0.0` at `2aaeeeb`. No behaviour, storage schema, decoder version or release
change is intended. Boundaries marked **extracted** are implemented on this branch; the others
are deferred proposals, not further work required on this branch. On 2026-10-05 the owner reduced
the workstream to closing out the existing extractions; the time cost did not justify the full
plan. See [Architecture closeout](ARCHITECTURE_CLOSEOUT.md) for the boundary assessment and handover.

| Area | Current owner and lifetime | Boundary and status |
| --- | --- | --- |
| Playback state and events | Process singleton `PlaybackController`; main-immediate supervisor scope; read-only flows exposed to UI and service | **Extracted:** `Message.kt`, `PlayerUiState.kt`, `BrowseState.kt`, `DownloadKeys.kt`; same package, definitions, defaults and derived rules; no change to state ownership |
| Native playback | Controller owns `NativeEngine.Track`, open/seek operations and focus; `stopPlayback` cancels opens/prefetch, closes the track and releases focus | Playback collaborator after the independent workflows have been extracted; retain operation order |
| Scanning | Controller owns the scan job, folder/probe adapters and two background-priority dispatchers; `ScanBatch` owns only per-run candidate/result bookkeeping | **Extracted:** testable scan execution with injected probe, append/replace persistence and progress callbacks; four safe workers and the existing serial lane, checkpoint timing and cancellation boundary unchanged |
| Playlist-session persistence | Existing stores own database access; `PlaylistSession` owns the persistence ID and both save jobs in the controller's scope; the working queue remains in the existing player flow | **Extracted:** restore, debounced settings/track writes, Save/Discard and create/rename/delete/switch. `PlaylistSessionState` applies narrow transitions to the same flows, including suspended list reads inside atomic retries; controller retains native stop and metadata callbacks. Queue editing remains separate |
| Playlist file transfer | `PlaylistTransfer` uses the controller's scope and an immutable export selection; controller owns the active queue, playlist list and share/message flows | **Extracted:** M3U import/export and matching order through existing stores and `PlaylistFile`; `AndroidPlaylistFiles` binds document reads, shared copies and intents, and `AndroidPlaylistText` maps typed notices. Imports do not activate or edit the current playlist |
| Catalogue browsing/search | Controller owns the Browse flow; `CatalogueBrowser` runs catalogue/format/author reads and overview/count queries in the same scope; UI retains sheet/scroll/selection state | **Extracted:** `CatalogueBrowseState` applies narrow transitions to the existing flow; `AndroidCatalogueStorage` reads existing cache/replay/database files and pairing preferences. `CatalogueTracks` maps rows for browsing, search and Random. `LibrarySearch` retains the four-source query, called by `BrowseWorkflow` |
| Browse interactions | `BrowseWorkflow` applies domain/Back/query/filter/result transitions to the existing Browse flow and scope; reads remain with their existing services | **Extracted:** a narrow actions port dispatches catalogue, local-folder and History reads; `BrowseNavigation` remains the pure rule owner. Controller retains playback digressions and Android notice rendering. C106 owns the submitted search job, not a new scope/state store |
| Local-folder browsing | `LocalFolderBrowser` reads existing grants/indexes and publishes into the controller's Browse flow using the same scope; controller retains the scan job and `BrowseWorkflow` routes domain/Back actions | **Extracted:** `AndroidFolderAccess` reuses SAF permission/label/path handling; `LocalTree` remains the tree rule owner. Scan snapshots and Back share the browser's tree/level rendering; no scan starts merely because a folder is opened |
| Track bytes and companion files | `RemoteFiles` retains its cache and shared fetch jobs; content streams are opened/closed per read; controller retains scan/archive dispatchers | **Extracted:** `TrackFiles` routes the existing sources; `AndroidTrackFileAccess` binds resolver, shared cache and LHA operations. Playback/share/prefetch use the same reader; scanning calls its synchronous document adapter on the existing scan lane |
| Reference-data downloads | `LibraryDownloads` owns the selected-run job in the controller's existing scope; controller owns Browse progress/counts through a narrow UI port | **Extracted:** catalogue, SID/songdb and favourite workflows, with `StoreDownloadSources` reusing existing stores/cache and `AndroidDownloadText` formatting typed notices at the original points |
| Native replay installation | `ReplayDownloads` runs in the existing controller scope; controller owns progress, native configuration and the background dispatcher | **Extracted:** guarded sc68 then UADE downloads, activation and combined notice. Native calls remain controller-bound, including startup/post-delete UADE configuration; construction performs no download or native initialization |
| Downloaded-data removal | `LibraryMaintenance` runs cache, catalogue index/archive, metadata and replay deletion in the controller's existing scope; stores/cache and native configuration retain their owners | **Extracted:** `LibraryMaintenanceState` publishes into the existing flows; `AndroidMaintenanceFiles` and `AndroidMaintenanceText` bind file operations and notices. No new deletion target, scope or state copy; playback and local user data remain outside this workflow |
| Random history (C97) | `RandomHistory` owns the heard boundary, cursor, speculative tail and generation; the controller owns catalogue IO and playback | Scope changes discard only unheard entries. Next walks history; explicit re-roll appends a fresh pick. Serialized fills reject obsolete results after each suspension, including the small-pool fallback. Background replenishment never publishes speculative rows |
| Playback read-ahead | `PlaybackReadAhead` owns the byte map and job in the controller's existing scope; controller selects upcoming candidates and consumes bytes when opening a track | **Extracted:** concurrent `TrackFiles.read` calls, wanted-ID eviction, 4 MB per-file/8 MB total limits, consume-on-open and stop/replacement cancellation. Preparation retires old work before checking whether all wanted bytes are held; late results also need the current generation |
| Browse cache/prefetch | `RemoteFiles` owns persistent cache and shared in-flight fetches; controller owns Browse cache marks and both observers, including cancellation and stale-result protection | **Extracted:** `BrowseCache` combines `OnPhone` source checks with `FolderPrefetch` planning/execution. Narrow file/progress operations reuse the same remote cache and Browse writer; no new scope, cache or policy copy |
| Android service | `PlaybackService` owns its coroutine scope, platform media session, notification and optional floating-controls window; observes the process controller | Keep this lifetime boundary; do not move playback ownership into screen composables |
| Compose | `ProtracktorApp`, `BrowseScreen`, `PlaylistScreen` mix wiring with sections, rows and dialogs | Cohesive UI components with explicit state/actions; no extra copies of domain state or altered effect keys |
| Web | `app.js` owns queue/playback/session/DOM state; `store.js` owns IndexedDB access; `catalogue.js` handles archive/index operations; worklet owns its engine handle | Startup/composition entry point and explicit workflow/UI modules with acyclic imports; keep messages and storage keys |
| Native engine | `engine.cpp` owns backend adapters and selection; `player_oboe.cpp` owns the locked audio host/JNI; `player_wasm.cpp` owns WASM handles | Adapter translation units behind the existing `Backend` contract; keep host ownership and probing order |

The dependency direction remains UI/service → orchestration → domain rules and data/native
adapters. Domain rules must not acquire an Android or UI dependency. An extraction must not turn
the controller into a shared mutable context passed to every collaborator.

State snapshots are values; jobs and resources are not. Moving a value type does not move its
writer or its lifetime. Scan cancellation retains partial index writes; a completed scan replaces
the folder snapshot. Read-ahead cancellation is distinct from cancelling another consumer of a
shared download. The audio callback uses a non-blocking decoder lock while seek/subsong work runs
on the caller thread. These are preservation constraints, not proposed changes.

The session persistence ID and the published active-playlist ID are deliberately not collapsed in
this extraction: the former changes before an asynchronous track read, the latter on publication.
The extraction preserved pending-write and Save-completion races; their historical controlled-store
reproductions are retained in `STATUS.md`.

**C103 follow-up, 2026-10-13, merged at `f4c9bf1`:** a track-write request captures a
detached track list and its published playlist ID together, rejecting pre-restore or mismatched
activation state. Metadata, explicit Save and automatic-add replacements share a session-owned
mutex. Save completion compares identity and persisted contents, not unrelated transport state.
Revert and Delete retire obsolete request revisions; deleted targets reject subsequent captures,
and Revert cannot publish its read into another playlist. Reset/read publication and invalidation
remain inside the write lock so old contents cannot be resurrected afterwards. Debounce timings,
controller scope and stores remain unchanged. This is a separately approved behaviour fix, not
an extraction guarantee or a global lock over every store caller. Owner-approved merge does not
establish individual playlist phone checks; no such results were supplied.

**C105 activation correction, owner accepted and merged at `557acbb`, 2026-10-15:** the latest
request generation owns playlist publication and follow-up effects. The destination track/list
reads happen before a non-suspending state update; persistence identity and queue are published
together. Newly requested track writes remain gated during activation, while already captured
C103 snapshots keep their owners. Settings writes are serialized separately and sampled inside
their lock. Superseded failures do not affect the current request; cancellation and current
failure propagation remain explicit. [Contract and verification](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C105_LATEST_PLAYLIST_ACTIVATION.md).

**C102 output recovery, 2026-10-13, merged at `69c42a2`:** each Oboe stream holds
its own shared `OutputError` callback, which atomically records an error and delegates stop/close
to the existing control thread. It carries no Player pointer; an old stream's delayed callback
cannot access a deleted Player or invalidate its replacement. The existing 200 ms Kotlin poll
requests native recovery only while playback is wanted. Disconnection reopens output without
rewinding/reopening the backend or restarting its background measurement. Other errors and failed
reopens become a paused UI with a notice; EOF still follows the existing end-of-track policy.
The data callback keeps its existing non-blocking decoder lock. No Bluetooth receiver broadcast,
new polling job, decoder copy or recovery thread was added. Host fake-device tests exercise the
actual Player/JNI implementation. The owner confirmed connection/disconnection while paused,
sound handoff during playback and Pause on disconnection; remaining device checks are in `TESTING.md`.

Verification and remaining gaps are recorded in `STATUS.md` under Architecture stabilization.
Long-term phone/browser acceptance and permission to merge or release remain the owner's.

**B6 stereo mix trial, owner-accepted and merged at `f4d304c`, 2026-10-13:** `StereoMix` maps stable preference
names to native widths 100 / 50 / 0. Existing preferences restore the global choice into player
state; Settings forwards changes to the controller, which publishes/persists them and updates the
current track. A newly opened track receives the latest choice before its first start, including
when the choice changed while opening was suspended. Playlist storage/schema are unchanged.

`StereoMixer` is Android-independent PCM processing used by the common Oboe callback after the
backend renders and before ducking gain. Original bypasses without rewriting samples; Narrow and
Mono use convex channel averages, not level boosting. The control thread only stores an atomic
target; callback-owned width ramps live changes over up to approximately 10 ms without allocations or
new locks. Recovery replaces output, not the mixer/decoder; EOF still zero-fills the unrendered
tail. Shared audio uses the separate rendering path and is intentionally unchanged, as is web.
This is narrowing/downmixing, not crossfeed or Dolby processing. The owner accepted the trial;
individual remaining device checks and long-term behaviour are not established by that acceptance.

**B48 notification heart, merged into develop on 2026-10-14 at `61886e7`:** `PlaylistHeart`
derives membership from the selected playlist with existing file identity, independently of the
playing Browse/Random queue. `PlaylistHeartSave` atomically accepts a track/playlist-bound request,
appends at most once and explicitly commits all working edits through `PlaylistSession.savePlaylist`.
Its completion hook confirms the serialized write or reports failure without dropping the working
addition. C103 snapshots/invalidation/dirty-completion guards remain authoritative. Playback is
not restarted, and normal additions do not acquire this full-save exception. New notification
labels and feedback resolve `AppLocale.wrap` on invocation: raw service/application contexts do
not inherit the activity's saved-language wrapper.

`PlaylistHeartActions` supplies both notification and session custom actions with PL/EN labels and
outline/filled icons. Identity is encoded in the command URI itself, including PendingIntent data:
old buttons cannot be retargeted by updated extras, and custom actions remain bound when a client
omits extras. Loading/unrestored/missing destinations and stale or overlapping presses are rejected;
pending commands remain present with stable URI identity. `PlaylistHeartSave` rejects overlapping
writes; removing the action would move neighbouring controls under a second tap. Action labels
provide accessibility metadata, not new visible captions. A text Toast plus the app notice provides
completion/failure feedback. `PlaybackMediaSession.create` reserves both
transport slots via session extras so Android 13+ cannot move the heart into a missing Previous
or Next slot. Availability still derives from the existing playback state. Host call-recording
evidence does not establish actual controller delivery or lock-screen UI.

`NowPlayingText` owns the dock/notification author-platform-year subtitle rule. The dock supplies
localised status strings; `PlaybackPresentation` resolves them through the selected app locale.
That factory builds the actual notification and session metadata/state, with bold title spans,
the shared display summary, unchanged artwork/duration and stable transport identity. Service
lifecycle and callbacks remain in `PlaybackService`. The small notification P uses the single
logo geometry, without adaptive-icon padding/background or decorative cells; tint is system-owned.

**B46 recent searches, merged into develop on 2026-10-14 at `7111c47`:** `RecentSearches` owns
trimming, case-insensitive deduplication, 20-entry retention and prefix filtering before the
three-suggestion limit. `RecentSearchHistory` publishes an immutable StateFlow after writing its
ordered JSON preferences. This state is separate from playback History and Browse snapshots;
malformed stored values are ignored without an automatic rewrite. No database migration is needed.

`BrowseWorkflow.runSearch` records explicit submissions at the common entry point. `searchFor`
updates the query and submits once in the existing scope. `SearchQueryField` filters locally while
typing, displays an editable anchored dropdown with plain text suggestions while focused and
dismisses it on submission. Owner trial feedback selected the original integrated magnifier and
text-only autocomplete as exceptions to the generic labelled-action convention; no extra heading
or inline button section changes the results layout. The editable anchor preserves typing focus.
Search button, IME and suggestion clicks share the existing workflow; typing does not issue searches.
Keyboard/layout behaviour on a phone remains unverified by host Compose tests.

Catalogue overview publishes held-data flags before running expensive counts. Platform counting is
a separately launched sibling, not a prerequisite for the overview. Random filters do not use
those cached Browse counters as answers: `rememberRandomFilterState`, composed outside the sheet,
reads `RandomFilterAvailability` through `CatalogueBrowser` and the existing stores. It preloads
while Random is visible, retaining known availability during refreshes and across sheet dismissal.
Sheet-only reopenings in the same Random view do not query. Catalogue/count/download changes
trigger refresh; progress percentages and the selected Random scope do not. Database preparation
and index deletion clear the cache immediately; changes while inactive invalidate it without
querying. Superseded reads cannot publish even if their loader ignores cancellation. Unknown/error
states are distinct from zero counts and offer checking text/Retry, not false download advice.
The sheet renders this UI state; no persistent cache or second database connection was introduced
(C99, 2026-10-06).
File reads retain their IO
blocks; pairing and the backend fingerprint remain on the caller context. The fingerprint is
passed as a lambda so wiring does not initialize `NativeEngine`, and it is still evaluated inside
the atomic Browse update, including retries. Catalogue selection is captured at the original
call/job boundaries. Overlapping reads still publish in completion order, even if an older result
arrives after navigation; adding stale-result protection would be a separate behaviour change.

Downloaded-data deletion keeps its optimistic publication before asynchronous removal. Catalogue
deletion reuses the download guard and clears both busy markers in non-cancellable cleanup.
Modland's favourite scope is read after clearing its favourites. Replay removal still tries all
three directories before reading the installed version and reconfiguring UADE on the background
lane. Cache notices are rendered inside atomic player-update retries; other notices are rendered
once before publication. These differences preserve existing timing, not a new error policy.

Local-folder reads retain their original launch boundaries. Remembering a grant persists it,
refreshes rows, launches opening and requests a scan without awaiting the index read. The supplied
scope controls nested-launch dispatch order. Folder-list reads remain inside atomic publication
retries; root-path derivation does too, while the native fingerprint is read once only after a
non-empty index result. Scan snapshots preserve the current tree level and the controller's
selection guard. Ordinary opening retains its pre-existing completion-order race (`STATUS.md`).

Browse domain entry publishes before invoking its refresh actions. Back invokes parent reads before
publishing its captured snapshot; a reentrant parent-read publication can therefore still be
overwritten. Catalogue and local-folder completion-order risks remain separate from search.

**C106 search correction, owner accepted and merged at `d74d339`, 2026-10-15:** `BrowseWorkflow`
owns one submitted request, its generation and cancellable job in the existing controller scope.
Input/domain matching alone cannot distinguish leaving and returning to the same search, so every
retirement advances the generation before cancelling. Results, live notices, failures and loading
cleanup require the current owner and matching inputs. An obsolete non-cancellable read therefore
cannot overwrite newer rows or clear another request's loading. The lazy job is assigned before
starting because `Main.immediate` may finish or reenter inline. These fields follow the existing
Main-scope interaction confinement; this is not a second state store or a new worker scope.

Query edits, filter changes, domain/Back navigation and controller snapshot restoration retire
pending searches. Explicit History/Playlist and author jumps use the same retirement entry point.
Typing/filter edits do not submit a new search. Completed results retain the existing navigation
contract: changing scope clears stale-result fields; catalogue/platform toggles do not. Current
exceptions end owned loading and require an error callback, localized by the controller; cancellation
and obsolete failures do not report errors. `LibrarySearch` still owns matching, ordering, caps and
source aggregation without changes. [Contract and evidence](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C106_SEARCH_REQUEST_OWNERSHIP.md).

### Original refactoring inventory — remaining extractions deferred on 2026-10-05

This ledger follows the seven starting files in local `GOAL.md`. Kotlin paths are relative to
`app/src/main/kotlin/com/przunk/protracktor/`. Line counts include comments and indicate the state
of the branch, not architectural quality. The owner accepted leaving the unfinished portions in
place for this closeout, not declaring them ideally structured. The last column records the former
plan, now deferred; final boundary review and phone checks are tracked in `ARCHITECTURE_CLOSEOUT.md`.
Detailed preservation evidence is in the dated checkpoints in `STATUS.md`.

| Starting file; baseline → current lines | Responsibilities and resulting owners | Checkpoint / evidence | Deferred plan / later checks |
| --- | --- | --- | --- |
| `player/PlaybackController.kt`; 5,287 → 3,270 | State contracts and independent scan/search/file/download/cache/playlist/catalogue/maintenance/local-folder/Browse workflows moved to the named owners above | `e48ccd8`–`b635b5f`, then Browse interactions: full gate 639 tests; all 23 new Browse checks mutation-sensitive | Library editing, sharing and playback ownership (including digressions); final dependency review and phone checks |
| `ui/BrowseScreen.kt`; 1,731 → 1,731 | Screen orchestration, panels, menus and dialogs still in the original owner | No extraction checkpoint | Coherent sections; preserve state/effect/list identity, Browse re-entry and Add to playlist; phone checks |
| `ui/ProtracktorApp.kt`; 939 → 939 | App wiring and screen/dialog composition still together | No extraction checkpoint | Separate composition responsibilities without changing launchers, collectors or navigation; phone checks |
| `ui/PlaylistScreen.kt`; 824 → 824 | Screen state/actions and presentation still together | No extraction checkpoint | Cohesive presentation sections with unchanged gestures, selection and scrolling; phone checks |
| `web/src/app.js`; 4,300 → 4,300 | Startup, playback, navigation, rendering and events still share the entry point | No extraction checkpoint; baseline DOM/storage checks only | Reconcile accepted web PRs before editing; explicit modules, real entry-point and packaged-import checks, browser checks |
| `web/src/catalogue.js`; 877 → 877 | Catalogue access/indexing and related navigation remain unchanged | No extraction checkpoint | Determine cohesive boundary; record any justified intact portion; test real consumers and packaged paths |
| `native/engine/engine.cpp`; 2,970 → 2,970 | Backend adapters, selection/probing and shared helpers still together | Fresh baseline WASM build and five-format playback probe; not audio equivalence | Adapter extraction with both source lists; Android/UADE and WASM metadata/refusal/PCM/subsong/seek comparisons |

The broad first-party comment pass and packaged-web handover are deferred with the untouched areas.
Current closeout requires the focused boundary/comment check, reconciled documentation, full gate,
release-variant test APK and candidate-specific manual checklist. No row grants merge or release
approval; long-term acceptance remains with the owner.

## 1. Platform

| Setting | Value | Reason |
| --- | --- | --- |
| Language | Kotlin | — |
| UI | Jetpack Compose, Material 3 Expressive | R8 |
| `minSdk` | 29 (Android 10) | Below this, scoped storage and the current media APIs both become a fight. The development device is API 36; 29 exists so other people can install it. |
| `targetSdk` | 36 (Android 16) | Owner's device generation. |
| Native | NDK 29.0.14206865, CMake 3.31.6 | Pinned. A native toolchain that changes underneath you produces failures nobody can reproduce. |

## 2. Licence: GPL-3.0-or-later

Decided 2026-08-31.

The decoders worth having are GPL — `libsidplayfp` (C64), UADE (Amiga custom formats), `sc68`
(Atari ST), ASAP (Atari 8-bit). Linking them makes the whole application GPL. That part is not a
choice.

Choosing **3** rather than 2 is a choice, and the reason is our own stack, not the decoders:
Jetpack Compose, AndroidX, Kotlin's standard library and Oboe are all **Apache-2.0**, which is
incompatible with GPL-2 and compatible with GPL-3. Under GPL-2 we would have a licence conflict
with our own UI framework. GPL-3 additionally carries an explicit patent grant that GPL-2 lacks.

This requires every GPL dependency to be "**or later**" so it can be taken to 3. That is verified
per dependency at integration time and recorded in `docs/LICENSES.md`. A GPL-2-only dependency
would invalidate this decision and must be raised rather than worked around.

Consequence accepted: when the repository goes public, sources must stay available.
F-Droid is the natural channel. Google Play does not object to GPL-3 (the App Store conflict people
remember is Apple's, and does not apply).

## 3. We do not fork ZXTune

ZXTune's decoding core is good and its format coverage is the reference. But it is a long-lived C++
codebase with its own framework, and its architecture is the source of most of what
`docs/REQUIREMENTS.md` complains about. Understanding it well enough to change it safely costs
about as much as writing a new shell, and leaves us owning its structure.

So: **new application, upstream decoders.** ZXTune is consulted for its format list and its
content-probing logic, not vendored.

## 4. The audio path stays native

```
  ┌──────────────── Kotlin ─────────────────┐   ┌────────── C/C++ ──────────┐
  │ MediaSessionService                     │   │                           │
  │   └─ SimpleBasePlayer implementation ───┼──▶│ engine facade (JNI)       │
  │        control, position, queue         │   │   ├─ backend registry     │
  │                                         │   │   ├─ libopenmpt  (trackers)│
  │ SQLite: index, playlists, cache (§9)    │   │   ├─ sc68        (SNDH/YM) │
  │ Compose UI                              │   │   ├─ libsidplayfp (SID)   │
  │                                         │   │   ├─ UADE        (Amiga)  │
  │                                         │   │   └─ game-music-emu (NSF…) │
  │                                         │   │                           │
  │                                         │   │ Oboe callback ──▶ speaker │
  └─────────────────────────────────────────┘   └───────────────────────────┘
```

**Rendering and output both live in native code; JNI carries control and metadata only.** The
alternative — rendering natively and pushing PCM buffers into an `AudioTrack` from Kotlin — crosses
the JNI boundary every few milliseconds on the one thread that must never be late. Keeping the
render callback and the decoders on the same side of the boundary removes that whole class of
glitch.

Oboe rather than raw `AudioTrack` because Oboe already solves device-specific buffer sizing and
stream recovery on Android, and it is Apache-2.0.

On the Kotlin side, Media3's `SimpleBasePlayer` looked like what makes this affordable: it lets a
custom player expose itself to `MediaSession`, so notification controls, audio focus,
becoming-noisy, Bluetooth and Android Auto all work without writing an ExoPlayer `Renderer` for a
synthesiser.

> **Superseded by §12.** The session was built on the platform's own `android.media.session`
> instead, and Media3 is not a dependency of this project. The paragraph above is left as the
> decision that was actually made at the time; §12 says what replaced it and why.

## 5. One facade, many backends

Every decoder sits behind a single native interface: probe, open, render, seek, subsong select,
metadata, close. Adding a format means adding a backend to a registry, not touching the player.

**Format is decided by content, not by file extension.** Extensions in this world are unreliable,
absent, or shared between unrelated formats. Probing is what ZXTune gets right and it is worth
copying the approach.

Capabilities differ per backend and must be *declared*, not assumed — `libopenmpt` and
game-music-emu can seek; `libsidplayfp`, UADE and `sc68` effectively cannot, because they are CPU
emulators with no way back except re-running from the start. The UI reflects what the current
backend can actually do rather than offering a control that silently does nothing (AGENTS.md §7:
a press that does nothing and says nothing is a defect).

**And where a backend can seek, the seek is unbounded work and must not happen on the audio
thread.** Every emulator here reaches a position by running forward to it, so seeking near the end
of a five-minute tune means emulating five minutes of a 6502 inside a callback that has
milliseconds to answer in. The stream starves, stops advancing, and the next `close()` blocks
waiting for a callback that will not return — which on `Dispatchers.Main.immediate` is a frozen
app. It was hit twice on 2026-09-10, both times by dragging the seek bar while a tune was
still loading.

So a seek runs on its caller's thread under the decoder lock, and the audio callback `try_lock`s:
failing to take the lock is not an error, it means somebody is seeking, and a buffer of silence is
the right thing to play meanwhile (`native/engine/player_oboe.cpp`).

## 6. Startup latency is an indexing problem, not a decoding one

R9's 5–30 second wait is not decode time — a module is kilobytes and renders in milliseconds. It is
re-opening the containing archive and re-probing the format on every single play.

Three mechanisms, in order of effect:

1. **Persistent index (SQLite, §9).** Path, container, offset, detected format, metadata, duration,
   subsong count — written once at scan, read instantly afterwards. This alone removes the probe.
2. **Extracted-file cache.** Entries pulled out of ZIP/LHA archives are kept on disk keyed by
   container identity, so playing the second track from an archive does not re-open it.
3. **Prepare-ahead.** While a track plays, the next one is opened and its first buffers rendered.

The index is also what makes R2 possible: a playlist that refers to index rows does not need the
filesystem to exist at launch, so opening the app cannot destroy the view.

## 7. Domain logic stays off Android

Shuffle order and history (R5), queue and cursor semantics, repeat scope within a playlist (R6),
duration policy — these are pure Kotlin, testable without an emulator, and they are the parts most
worth testing. There is no emulator in this environment (AGENTS.md §3), so anything that can only
be verified by running the UI is verified on a real phone, and everything else is
verified by tests that run here.

What has been pulled out this way, and why each earned it: `PlayQueue`, `SchemaSql`, `SongLengths`,
`CacheBudget`, `BrowseNavigation`, `BrowseScroll`, `TrackEditing`, `PlaylistFile`,
`SupportedFormats` and — since 2026-09-04 — `SubsongAdvance`.

It also made the correction cheap. When repeat-one was reported as still advancing, the fix was
one line in `SubsongAdvance` and a rewritten test that says why the old rule was wrong — rather than
a change inside a method nothing can exercise here.

**The rule for when to extract is not "when it is easy".** It is *when the decision has been wrong
before*. `SubsongAdvance` holds one question — what a file with several tunes in it does when one
ends — and that question has now been answered wrongly twice inside a method that needs a phone to
run: C11 raced through every subsong in silence, and C13 never consulted repeat-one at all. Sixty
lines of object and six tests is a cheap way to stop a third time.

## 8. Two kinds of source behind one index

Decided 2026-08-31, after the question of whether online catalogues with a local cache were
possible. They are, and the measurements below were taken rather than assumed.

| Source | Index | File |
| --- | --- | --- |
| Local folder (SAF) | produced by a scan | already present |
| Remote catalogue (Modland, HVSC) | downloaded once | fetched on demand, then cached |

Both produce rows in the same index, so **a playlist can mix local and remote entries** — it refers
to index rows, not to paths.

### What was verified on 2026-08-31

`https://modland.com/allmods.zip` is 5.75 MB and contains a single `allmods.txt` of **515,502**
lines, each `<size>\t<Format>/<Author>/<title>.<ext>`. A file is fetched at
`https://modland.com/pub/modules/` + the URL-encoded path; one was pulled and its length matched
the index exactly, with a real ProTracker header rather than an error page. The server answers
`Range` requests with `206 Partial Content`.

`https://www.hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5` is 5.2 MB and downloads
directly, which settles Q4: exact SID durations are available and do not have to be guessed. Used
since 2026-09-02 — re-checked that day, still 5,205,150 bytes; §14 records what it turned out to be.
**Since 2026-10-01 the app fetches the same file from DeepSID instead**,
`https://deepsid.chordian.net/music/_High%20Voltage%20SID%20Collection/DOCUMENTS/Songlengths.md5` --
byte-identical that day. HVSC asked apps not to use its website and allowed it only until something
else was built; DeepSID's author allowed his files (`docs/PLAN_CATALOGUES.md`). The web page still
reads HVSC's copy: DeepSID sends no CORS header.

`sndh.net` did not resolve from this machine. **Unverified** — whether the domain is gone or the
network here blocks it is unknown. Atari ST material is also in Modland, so nothing depends on it
yet.

### What follows from those numbers

- **Browsing Modland works entirely offline.** The index is one 5.75 MB file; in the database it
  gives browse-by-format and browse-by-author with no network. The network is first needed at the moment
  a track is played.
- **Range support means fetches resume**, and a partially fetched file can start playing.
- The one fetch measured took 4 s for 212 KB. That is an argument for fetching the next track
  ahead of time, not against remote catalogues.

### Offline behaviour

An entry whose file is not cached stays **visible and explicitly marked unavailable**. It is not
hidden and not silently skipped: a press that does nothing and says nothing is a defect
(AGENTS.md §7).

## 9. Storage: hand-written SQLite, not Room

> **One helper per process, since 2026-09-03.** Every store used to construct its own
> `SQLiteOpenHelper` on the same file. That class synchronises within an instance and not between
> them, so five of them meant five things that could independently run `onUpgrade` — and the
> migration statements are plain `CREATE TABLE`, which fail on a second run. [docs/review.md](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review.md) R3.


Decided 2026-09-01.

Room was the intention and is the obvious choice. It cannot be used here: Room's annotation
processor needs KSP, and **KSP has no release built against the Kotlin that AGP 9.2.1 embeds**. The
newest KSP is 2.3.11, built for Kotlin 2.3; AGP 9.2.1 carries Kotlin 2.4.0. There is no version pair
that works, and downgrading AGP to reach one would give up the whole toolchain this project is
pinned to.

So the schema is hand-written SQL executed by an `SQLiteOpenHelper`. The arrangement is deliberately
shaped so the move to Room stays mechanical if KSP catches up:

- **All SQL lives in `SchemaSql`, a plain Kotlin object with no Android imports.** That is what
  makes it testable at all here. A migration meets a phone holding somebody's data exactly once, and
  there is no emulator in this environment — so the statements are executed against a real SQLite
  engine (`org.xerial:sqlite-jdbc`, test scope) in unit tests that run on this machine.
- **Migrations are keyed by the version they produce**, and asking for a version that has none is an
  error rather than a silent skip.
- **`SchemaSqlTest` checks that a fresh install and a fully migrated database end up identical.**
  Updating `CREATE` after adding a migration is the step everyone forgets, and the symptom — a
  schema that differs between an upgraded phone and a new one — surfaces far from its cause.
- **Unsupported downgrades refuse without deleting data** (C108, merged on owner approval at
  `8f3ef56`, 2026-10-15). The previous recreation policy contradicted the current workspace
  rules and discarded user data. `onDowngrade` now throws before destructive SQL. A read-only
  `DatabaseAccess` version probe runs before the activity composes its player/ViewModel and before
  a cold playback service constructs playback. The activity checks on IO and shows PL/EN loading
  or a refusal with Retry/Close. External-open intents remain pending behind the gate. The service
  probes only the version (no migration), posts the same explanation linked to the activity and
  stops without transport/session initialization. Stale scan Stop does not instantiate playback.
  Preflight neither creates/migrates a file nor invokes Android's destructive default corruption
  handler. Fresh/current/older schemas retain the existing helper create/upgrade path and progress
  reporting. No schema bump, destructive reset UI or new dependency. Older installed binaries are
  not changed: do not install an old destructive build over newer-schema data.

### What is stored

`playlists`, `tracks`, `playlist_tracks` (ordered membership), `granted_folders` (SAF grants, so
browsing does not begin at a file picker every session), and a single-row `player_state`.

The schema already carries several playlists although the UI offers one, so R6 arrives without a
migration. `player_state` is one row enforced by a `CHECK` rather than five key-value rows: settings
that come as a set should not be able to exist half-written.

**Restoring makes a track current without starting it.** Coming back to the app is not a request to
make noise, and R2 asks for the view to be restored, not the playback. Resuming a *position* is
`docs/OPEN_QUESTIONS.md` Q6 and still open.

## 10. One player per process, and a service to keep it alive

Decided 2026-09-01.

Playback used to live in the ViewModel. That cannot work: a ViewModel dies with its screen, and
playback has to outlast it. Worse, a service with its own copy would mean two players agreeing by
accident.

So `PlaybackController` is a process-wide singleton and owns everything — the queue, the open
module, the store. The ViewModel forwards to it and `PlaybackService` holds the same instance. There
is one answer to "what is playing" no matter who asks.

### Why the service starts before playback

Android only permits a foreground service to be *started* while the app is itself in the foreground.
Waiting until the user leaves is waiting until it is no longer allowed, so the service starts on any
press that makes noise. That has a consequence worth stating: at start-up there is legitimately no
track yet, so "nothing playing" must not mean "stop the service" — only an empty state that follows
a non-empty one means playback is really over.

It also has a deadline. Android gives a started service roughly five seconds to call
`startForeground`, and the first track is still being read off disk.
So the notification is posted immediately with whatever state exists and updated afterwards.

### What is missing

**No `MediaSession`.** Lock-screen transport, Bluetooth and headphone buttons do not work; the
notification's own actions do. §4 picked Media3's `SimpleBasePlayer` for this — **§12 records what
was built instead** — and it was a separate step because it was a separate risk: doing it alongside
the service would have meant debugging two new things at once. Recorded in
`docs/BACKLOG.md`.

**No audio focus.** Another app starting playback will talk over us, and a phone call will not duck
us. Same step.

## 11. Browse has domains, and online catalogues live in the database

Decided 2026-09-01, after what was asked for was Browse to start with *where to look* rather than with
"add a folder".

Four domains: **local filesystem**, **online catalogues**, **random**, **search**. Below that it is
the same list-and-tick machinery whatever the source, which is why one component covers all four —
a folder of files and an author's page in an archive are the same problem once the rows exist.

### The index goes in the database, the files do not

A catalogue's index is downloaded once and stored, so **browsing an online archive needs no
network**. The network is next needed only when a track that is not cached is played. Modland's
whole index is a 5.75 MB zip of about half a million lines; kept as rows it is browsable by format
and author instantly and offline.

Only entries a backend might handle are indexed. Modland lists formats nothing here can play yet,
and indexing them would cost minutes and disk to browse a list of tracks that cannot be opened.
**Re-index after adding a backend** — recorded in `docs/BACKLOG.md`, because otherwise it is
discovered by wondering where the SIDs went.

### A remote track's identity is its URL

It has no document URI and never will. The URL is what the cache keys on and what the player opens,
so it is what `TrackRef.id` holds — the same field a local track keeps its `content://` URI in. The
player branches on the scheme and nothing else in the app has to know the difference.

### The cache is not an optimisation

A measured Modland fetch took four seconds for 212 KB, and a local library may sit on an
network, so "read the file" is a round trip either way. Fetched bytes are cached by a hash
of the URL — hashed rather than sanitised, because real Modland paths carry spaces, slashes, `@` and
`$`, and any escaping scheme would eventually collide. Downloads land through a temporary file, so
an interrupted one cannot leave a truncated file that looks valid forever after.

The eviction budget is still `docs/OPEN_QUESTIONS.md` Q5 and still open.

### Only Modland so far

Each archive publishes its contents differently, so parsing belongs to the catalogue rather than
being shared. `Catalogue` is a sealed class with one implementation; adding ASMA, AMP, Aminet or
ModArchive means writing its index parser and, in most cases, the backend that can play what it
holds. The UI says as much rather than showing archives that would open on nothing.

## 12. The media session is the platform's, not Media3's

Decided 2026-09-01, changing the plan recorded in §4.

§4 chose Media3's `SimpleBasePlayer` so a `MediaSession` could be had without writing an ExoPlayer
renderer for a synthesiser. By the time the session was actually needed, that reasoning had stopped
applying: `PlaybackController` already is the player, with its own queue, transport and state flow.
Media3 would have meant a dependency plus an adapter that translates our player into a `Player` so
Media3 can translate it back into a session.

`minSdk` is 29, so `android.media.session.MediaSession` is available directly. It gives the lock
screen, Bluetooth and headphone buttons, and `Notification.MediaStyle` binds the notification to it.
No dependency, no adapter.

Two details worth keeping:

- **Seeking is advertised only when the backend can do it.** sc68 emulates a 68000 and cannot seek;
  a lock screen offering a scrubber that does nothing is the same lie as an app that does.
- **A media button says which action it wants, so the controller has an explicit
  `togglePlayPauseTo(play)`.** Toggling on a stale idea of the state is how a headphone press ends
  up pausing something that was already paused.

If Media3 is ever wanted for something else — Android Auto's browse tree, say — this does not stand
in the way; the session is created in one method in `PlaybackService`.

## 13. Catalogues come in three shapes

Decided 2026-09-02, when ASMA and The Mod Archive each added a shape that did not fit the original.

**Indexed** (Modland): a downloadable list of what is in the archive, and files fetched individually
by URL. Browsing is offline because the index is; playing needs the network.

**Archive** (ASMA): the whole collection is one download — 20 MB holding 6,335 `.sap` files — so
there is no separate index at all. **The archive's own entry list is the index.** Downloading it
once makes browsing *and* playing work with no network, at the cost of taking everything whether you
wanted six files or six thousand.

`Catalogue.isArchive` says which, and two things follow from it:

- The downloaded bytes are **kept** rather than parsed and discarded, in `filesDir` rather than the
  cache — the system may clear a cache directory at any time, and losing 20 MB to a sweep would mean
  fetching it again.
- A track's id is `<catalogue>://<entry>` and the player reads it out of the stored zip. Which
  catalogue that is comes from asking the catalogue list, not from matching a name: the next archive
  catalogue would otherwise be added without anyone noticing that line existed.

ASMA's paths run `asma/<section>/<author>/<title>.sap`, and the section — Composers, Games,
Unknown, Misc, Groups — becomes the top browse level. Every file in it is a SAP, so browsing by
format would offer one choice; browsing by section is the useful hierarchy. Checked against the real
archive: 730 entries have no author folder and keep an empty author rather than being dropped.

**Live search** (The Mod Archive): the archive provides no monolithic index file to download offline.
`Catalogue.isOnlineOnly` marks it as search-driven: it participates directly in the Search domain filters
without requiring an index download, query results are fetched live via its web endpoint, and individual
modules are streamed and cached via direct HTTP URLs on demand.

## 14. Where a duration comes from

Most backends know how long the thing they are playing is, and are asked. Two do not, for opposite
reasons, and only one of them is fixable.

A **SID is a program**. It plays until stopped, and "how long is it" is a question the format cannot
answer — not one it answers badly. HVSC answers it by hand: somebody listened and wrote a number
down, for 61,157 tunes, published as `DOCUMENTS/Songlengths.md5`. So the rule is: **take the
backend's duration; when there is none, ask HVSC.** Never the other way round — a format that knows
its own length knows it better than a lookup on a hash could.

The lookup key is the **plain MD5 of the whole file**. That is worth writing down because it used
not to be: older HVSC releases keyed the database on a hash of selected header fields, which
libsidplayfp still exposes as `SidTune::createMD5`, and code written from the old documentation
would find nothing and look merely empty rather than wrong. Verified by fetching three tunes from
the collection and finding all three by file MD5.

The database is downloaded on request rather than with the first SID, because 5 MB is not something
to spend at the moment somebody presses play. It is not a catalogue — nothing in it is playable —
so it sits below the catalogue list rather than in it. All subsongs' lengths are stored, not just
the first, so the table survives subsongs becoming selectable (`docs/BACKLOG.md` A2).

**Position** for a SID is counted rather than asked, for the same reason: libsidplayfp is running a
machine and has no position to report. Frames handed to the output device is the same quantity by
another route, and it is the one the listener is hearing. **Seeking is still impossible** — the only
way to a position in a SID is to run the machine there — so the scrubber shows progress and does not
accept a drag.

The other backend without a duration is a tracker module with an unbounded pattern loop, where the
length genuinely depends on what the tune does. Nothing here fixes that.


### And what the duration is *for*

Until 2026-09-04 it was for the label and the progress bar, and nothing else. That left a hole the
owner walked into: a SID played for ever. `isFinished` is set by the engine when a backend hands
back a short buffer, and libsidplayfp never does — it is running a 6502 in a loop and has no idea
the music is over — so nothing ever ended the track and the playlist never moved on.

**So a known duration now ends the tune.** The position poll already had both numbers; reaching the
length calls the same `handleTrackEnded` a real end calls, so repeat, shuffle, subsongs and Random
all behave identically whether the tune ended by itself or by the clock. This is not SID-specific
and deliberately so — any backend that loops for ever now stops when something knows better.

**It trusts whatever supplied the number.** A wrong HVSC entry cuts a tune short; that is the trade
every player using these databases makes, and the alternative is the one being fixed.

**Per subsong, which is what made this more than one line.** HVSC stores a length for every tune in
the file and only the first was ever used. Switching subsongs cleared the duration and waited for a
backend that would never answer — so tune two onwards showed no length, and would have played for
ever again the moment the rest of this worked. The list is now kept for the open file and indexed by
subsong.

## 15. History is not a log

Added 2026-09-02 with `docs/WISHLIST.md` B8.

The question it exists to answer is **"that tune two days ago, what was it"**. Everything arguable
about the design follows from that being the question, and would be decided the other way if the
question were "audit this app":

- **One row per track, not one per play.** `played_at` moves and `play_count` rises. A true log
  fills with a repeat-one track fifty times over and buries the thing being looked for.
- **It is unbounded and paged.** The original 500-track pruning was removed on 2026-09-26;
  opening History reads a page, not the whole table.
- **The rows are self-contained** and reference nothing. A tune played from Random or from a search
  result is never added to a playlist and so has no row in `tracks` — and those are exactly the
  tunes this list exists for. A foreign key would have given history only for music you had already
  decided to keep, which is the opposite of the point.
- **Recorded after the metadata is read**, so it stores the name the tune calls itself rather than
  the filename it arrived under.

It is a browse domain rather than a screen of its own, which means playing from it and adding from
it are the same code as everywhere else, and cost nothing.

**C107 History entry scroll, owner accepted and merged at `d74d339`, 2026-10-15:** explicit
domain entry advances the in-memory `BrowseState.historyVisit`. The visit keys the `HistoryDomain`
subtree, which owns its scroll cache rather than using the enclosing Browse session's cache. Each
entry starts page zero at the top; page changes and row/loading updates retain positions within
the visit. No intermediate loading frame is required. Other domains keep their existing per-level
restoration. Keying only the cache is insufficient: the playing-row observer must treat the current
track as arrival, not a change that can immediately scroll the fresh list. This is UI navigation
state, not persisted history or a schema change.
[Report and verification](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C107_HISTORY_ENTRY_SCROLL.md).

**B49 live History search, pending `feature/history-live-search`, 2026-10-15:** `HistoryBrowser`
owns only the Browse flow's input/loading/result transitions. `HistoryReads` debounces edits by
200 ms and gates read/error publication by latest ticket, normalized phrase and visit/domain.
Its lazy job is assigned before starting, preserving ownership under immediate completion.
The production read waits for the ordered History writer to finish any clear before reading.

Blank input keeps the indexed SQL count/page path. Nonblank input streams the existing ordered
cursor on IO, uses literal Unicode-aware case-insensitive matching over stored title/author/filename
and counts matches while retaining only the requested page and last-page fallback. Cancellation is
checked per cursor row. Schema remains 23; no migration, wildcard meaning or accent-removal rule.
`HistoryDomain` keeps the input outside the query-keyed result subtree, preserving keyboard focus
while replacing rows. A query change resets scroll/follow state; paging within that query retains
positions. Errors have localized feedback and a labelled retry, not a false no-match result.
Only an explicit track tap captures a result-page queue; filtering does not alter the existing
playback queue, History recency/count policy or recent-query storage. [Contract](PLAN_HISTORY_SEARCH.md).

**One trap worth keeping written down.** The natural way to write "insert or bump the count" is
`INSERT ... ON CONFLICT ... DO UPDATE`. That is SQLite 3.24, and **API 29 ships 3.22** while
`minSdk` is 29 — so the natural way crashes on the oldest device supported. Nothing in this project
would have caught it: the JVM tests run against a current SQLite through `sqlite-jdbc`, and there is
no emulator here. The statement is written as `INSERT OR REPLACE` with the previous count read back
in the same statement, and it lives in `SchemaSql` where a test can reach it.

### B43 experiment: qualification is distinct from a History entry (2026-10-07)

On `feature/played-before`, schema 22 adds `play_history.listened`. The one-time migration marks
legacy rows as qualified; their duration cannot be reconstructed. New plays retain the existing
History-recording timing and qualify after five active seconds or a natural completion (C100,
user feedback of 2026-10-11). The existing upsert preserves
qualification; qualification itself changes neither recency nor count. Playback from History still
does not reorder the list. A repeated file does not acquire an extra History count merely because
the experimental timer restarts.

`ListeningSession` measures advancing decoder position bounded by monotonic elapsed time, with
explicit boundaries for pause, seek, subsong changes, new playback and clearing. It never uses an
absolute seek position as evidence of listening. Completion requires positive audio progress and
an occurrence without seeking; engine completion or the known tune duration qualifies, but the
unknown-length fallback timeout does not. The end is sampled before any automatic subsong/queue
advance or repeat restart. Clear still invalidates the occurrence, and qualification remains
one-shot. A seek keeps earned active time but disables the completion shortcut until a new
occurrence. `HistoryWrites` serializes record, qualify and
clear commands; generation tokens reject old queued work, and a running old write finishes before
the queued clear removes it. A failed clear is reported rather than presented as success.

`HistoryPresentationReads` queries only IDs in the current Browse list and Random record, in batches off
the main thread. Qualification/clear revisions refresh the results; cancelled or stale reads cannot
restore cleared marks. Status is held separately from saved Browse snapshots and projected into
the UI, so returning to an old navigation snapshot cannot restore an obsolete mark. The playlist
view never opts into row decoration. Random drawing, queue rules and prefetch are unchanged.

C101 (2026-10-11) extends that same read with titles already learned during playback and stored
in History. Record completion now invalidates the presentation too, before listened qualification.
Random/Browse render copied references with a nonblank learned title and original filename; their
source lists, catalogue sort keys, IDs and indices stay unchanged. Browse adds the filename to
its displayed source line only, never to `TrackRef.subtitle`: that field also supplies the fallback
author. Random includes the original filename in its secondary label. The owner explicitly chose
History's lifetime: restart/cache eviction retain names; Clear History removes the overlay.
No new table, cache decoding, schema migration or History recency change is involved.

The tiny history glyph is informational, with a localised accessibility description and explanation
in track details. Following the owner's second visual trial request (2026-10-07), it occupies a
fixed lower slot below Browse's cache indicator or Random's ordinal/play symbol. Missing cache
status leaves the upper slot empty; selection replaces both with the checkbox. Subtitles remain
plain text, and playlist rows retain their original leading slot. It does not alter row text
contrast or act as a button. Utility and appearance
remain subject to the owner's release-APK trial; an unhelpful experiment may be rejected. See
[B43 plan](PLAN_PLAYED_BEFORE.md). Do not roll a schema-22 phone back to an old schema-21 APK:
the old helper resets databases on downgrade. A rejected experiment needs a forward-compatible
build without its behaviour, not data deletion. This task does not rewrite old installed binaries.

### B42: saved catalogue places (2026-10-07)

On `feature/saved-places`, stacked on B43 with owner approval, schema 23 adds `saved_places`.
Identity is the exact `(catalogue_id, format, author)` tuple used by normal index browsing. It is
case-sensitive and never inferred from a display label. Modland author paths, ASMA sections and
UnExoticA game groups use the same existing route shape. Live searches and local folders do not
expose the action. Duplicate inserts preserve the original save time; newest saved places come first.

There is deliberately no foreign key to catalogue/index rows: deleting or rebuilding downloaded
data must not delete user bookmarks. The stored source label keeps a removed catalogue readable.
`SavedPlacesStore` handles I/O, while `SavedPlaces` serializes writes, refreshes state and cancels
obsolete opening requests. Its UI state is separate from Browse navigation snapshots. Catalogue
availability and exact current group contents are resolved through `CatalogueBrowser`, using the
same local queries and track conversion as ordinary browsing. No fuzzy fallback or network fetch
is added. Missing sources/indexes/groups remain distinct visible states, never empty success.

Opening publishes an Online author view with a one-step return to Saved places. Save/list/remove
cannot start playback, alter playlists or modify the Random record. Ordinary prefetch preferences
still apply after a folder opens. Clearing History does not clear bookmarks. The feature is
independent of B43's qualification logic, although this trial APK includes both schemas. Do not
revert by installing schema-22 or older binaries; use a forward-compatible build if a trial is
rejected. Neither feature is approved for merge by the combined trial.

Removal from Saved places keeps an immutable record in a one-shot snackbar action. Undo uses
the original timestamp and INSERT OR IGNORE, preserving ordering without overwriting an entry
saved again meanwhile. Writes are queued through the same mutex so an in-flight edit cannot
silently swallow Undo. `Message` may carry that action; `MessageSnackbar` invokes the displayed
notice's callback, with the existing playlist-removal fallback for legacy Undo notices. Message-ID
guarded dismissal cannot clear a newer notice. No undo record is persisted across app restarts.

## 16. Sharing has to copy

Added 2026-09-02 with `docs/BACKLOG.md` A10.

Neither place this app keeps a file can be handed to another app:

- A **local** file is a storage-access-framework document URI plus a permission grant belonging to
  *this* app, and a grant cannot be passed on. Sending the URI sends a string the receiver cannot
  read.
- A **downloaded** file is inside app-private storage, which nothing outside the app can see.

So a share copies the bytes into a cache directory that a `FileProvider` is allowed to vouch for,
and shares that. The provider is declared with `exported="false"` and a single path — the shared
directory and nothing else — because a share should expose the one file being shared and not the
library, the database or the 20 MB archive. Copies are swept an hour after they are made rather
than on the next share: the receiving app reads the file after the chooser closes, and deleting the
previous copy the moment a new share starts would sometimes pull it from under a slow reader.

**A shared catalogue link points at the file's public HTTP address.** Both Modland and ASMA
provide individual file URLs. ASMA's paths were measured on 2026-09-11, correcting the initial
2026-09-02 assumption that it only exposed a ZIP. Native playback still reads its retained
archive using internal `asma://` IDs; `fileUrlFor`/`webUrlFor` convert those paths for recipients.
WEB imports perform that conversion without duplicating the archive root. Internal archive IDs
are not links another application can fetch. The recipient decides how to handle the public file;
sharing a URL does not transfer the phone's private archive or permission grants.

## 17. How a list behaves

Settled on 2026-09-02, after list handling was named as the most irritating thing about
the app. The complaint that started it: **tapping a track selected it instead of playing it**, which
is a thing almost nobody does.

### The two modes

| | Normal | Selecting |
| --- | --- | --- |
| Entered by | default | **long-press** on a row |
| Leading slot | nothing | a checkbox |
| Tap a row | **plays it** | ticks or unticks it |
| Trailing slot | a three-dot menu | nothing — the checkbox is the affordance |
| Back | up one level, then out to the playlist | **leaves selecting, ticking nothing** |

Long-press to select and tap to act is the Android convention, and the app was the odd one out. The
cost is real and was accepted deliberately: adding several tracks now takes a long-press first,
where it used to take a tick. In exchange the common case — hear this one — drops from two taps to
one, and listening is more frequent than collecting.

**Back clears the selection to nothing.** Keeping the first or playing track was suggested
ticked; this does not, because *playing* and *selected* are different states and only one of them is
a mode. A cancel that leaves something behind reads as an app that did not quite listen.

### The back stack in Browse

Three jobs, in this order, each one step:

1. leave the selection, if there is one
2. up one browse level
3. out of Browse, back to the playlist

That is what makes back predictable, and it is also why there is a separate, always-present way out:
from four levels deep the stack is correct and still four taps. The header carries a labelled
**Playlist** action that leaves Browse in one press from any depth.

**A jump is not a descent.** "More from this author" puts you three levels deep without your passing
through any of them, so back there returns you to the playlist rather than climbing a hierarchy you
never climbed. Any ordinary navigation from the landing place clears the mark and the stack above
applies again.

### Nothing may move when selection starts

Rows keep a reserved leading slot and a floor on their height whether or not a checkbox is showing,
and the header keeps a fixed height whether or not the select-all button is there. Without that,
long-pressing a row made the list grow under the very finger that had pressed it.

### What is not numbered

Rows in Browse carry no ordinal. In the playlist the number answers "where am I in *my* list of
three hundred", which was defended and is right; in Browse it would only say "row n of
somebody else's archive". The space goes to the checkbox instead, so rows do not change width when
selection begins.

### Actions are icons with labels

See `AGENTS.md`. Every control that does something is an icon with its name underneath, through
`LabelledAction` — one shape, one implementation, so a row of them reads as a set of equals. Their
width comes from the caller as `weight(1f)` rather than a fixed size: a fixed cell only produces
equal columns on a screen wide enough to hold them, and on a phone the extras wrapped out of sight
and read as missing.

### One name for the panel: **Now Playing**

Settled 2026-09-04, because it had two. The composable is `NowPlaying`; the documentation said
"the expanded player", which describes how you reach it rather than what it is. One name now, in
code and in prose. It is the panel that opens upward from the dock and holds the seek bar, the
file's metadata, the actions and — when there is more than one tune in the file — the subsong strip.

### The subsong strip follows the music only while you are watching it

A file can hold 256 tunes and the strip is a row of 256 chips, so it moves itself to keep the
playing one in view. Following unconditionally is what that used to mean, and it makes the far end
of a long file unreadable: scroll out to tune 240 to see what is there, and the moment the current
tune ends the strip snaps back to tune 68.

**The signal for "am I watching the music" is the playing chip itself.** If the tune that just
finished was on screen, the user is looking at the playing area and following is what they want. If
it was not, they are reading somewhere else and are left there. No toggle, no timer, no tracking of
who scrolled last — the thing being followed answers the question by being visible or not.

Opening a file is the exception and always moves, because that is being *taken* somewhere rather
than *kept* somewhere — and it is the one moment the strip must move, since a KSS can open at tune
47 of 256 (`docs/PLAN_FORMATS.md` §2).

**And it moves the minimum.** `bringIntoView` puts an item at the *start* of the view, which is
right when you are being taken somewhere and wrong when you are being kept somewhere: following
with it pinned the playing tune to the left edge and put everything before it out of reach, on every
single advance. `keepInView` does nothing at all when the item is already visible.

### A row's own actions live behind its three dots

A track row in Browse and in the playlist puts everything you can do *to* that track behind an
overflow menu, and since 2026-09-04 a playlist row in the switcher does the same: **rename, export,
delete**. Before that the switcher had three shapes for the same idea — rename appeared only on the
active row, delete sat beside it as a bare icon, and export was a button at the bottom that silently
meant "whichever one is open". Naming the playlist in the menu you opened is also what makes export
answer the obvious question, "can I export one I am not listening to".

**Import is deliberately not in that menu.** It does not add to the row you opened; it creates a
playlist of its own, always. It sits next to *New playlist* instead, because those two are the only
things here that bring a playlist into existence — an "Import" among a row's own actions would
promise precisely what it refuses to do.

### Anatomy

Browse, search and history rows share one anatomy, and the playlist row is the same thing plus a
drag handle. That is the only genuine difference between them: the playlist has an order that
belongs to the user.

### The plus

Playing something from Browse used to offer no way to keep it — the dock's **+** existed only for
Random. It now appears whenever something outside the playlist is playing, which is the natural
moment to decide you want to keep a tune: just after hearing it.

**And it says so — as does every other way of adding, since 2026-09-04.** The rule used to be
"silent when the rows appear, spoken when they do not", and the silent half kept being wrong. Adding
from a local folder closes Browse and lands on a playlist whose visible part may not change at all:
the new rows are at the end. Scrolling to them is not something a person reads as an answer, and the
owner reported the silence as a fault twice, in two different places.

The reasoning behind the silence was that a notice covered the very rows it reported. That
collision was fixed separately when the snackbar became swipeable, so what remained was a rule
defending against a problem that no longer existed.

### Adding to other playlists

Every row menu allows adding to another playlist directly (`docs/WISHLIST.md` B18). Unlike the
active playlist whose modifications form a temporary draft waiting for Save or Discard, writes to
another playlist happen immediately on disk to avoid managing multiple concurrent dirty drafts in
memory. A snackbar explicitly names the target playlist to confirm the addition since the target
list is not currently on screen.

### Deliberately still open

- **A4** — the same gesture in the *playlist*, and the bulk actions it would need there. The gesture
  is agreed; which actions the playlist gets is not, and guessing them is not the job.
- Mis-tapping a remote track now costs a download rather than nothing. `docs/WISHLIST.md` B17.

## 18. The local library is scanned by opening, not by reading names

Added 2026-09-03 with `docs/BACKLOG.md` A6 and `docs/STATUS.md` C4.

A folder used to be listed by walking the tree and keeping files whose **name** looked playable. Two
things were wrong with that and both bit: a module named `readme.txt` was invisible, and a
photograph named `tune.mod` was added and refused only when the user pressed play. The scan now
lists everything and hands each file to the same `NativeEngine.open` that playback uses. **What a
file is, is decided by a decoder accepting it.**

`MediaScanner.worthReading` is the only filter, and it takes the display name as a parameter it
deliberately never reads. That is not an oversight to tidy: the parameter is there so that the day
somebody reaches for it, the change shows up in a diff and fails a test. Size is the one thing it
judges on, and size is content rather than a name — nothing here is above a few megabytes, and
reading a four-gigabyte film off a network share to learn it is not a SID helps nobody.

### It has to be stored, or it is unaffordable

Opening every file in a library is minutes on a network share, so the answer goes in `library_index`
and later launches read it. **Scanning is never a side effect**: not of launching, not of returning
to the app, not of opening a folder. The one moment it starts without a second press is when the
user grants a folder, because granting a folder *is* asking for it to be usable.

### An index made by decoders expires with them

Every row records which decoder set produced it. This is not theoretical: replacing sc68 2.2.1 with
3.0.0b on the same morning took `.sndh` from 14 of 30 to 30 of 30, so every "nothing can play this"
the old set had written down became wrong at once. A folder whose rows name a different set is
reported stale and offers a rescan. `docs/BACKLOG.md` A7 has the same problem for catalogue indexes
and solves it with a note asking a human to remember.

**Catalogue indexes are still filtered by name, and that is right rather than a shortcut** — a
catalogue index is a list of filenames on somebody else's server, and deciding by content would mean
downloading half a million files to find out.

**Which is why the recorded set is the decoders *and* the name list, since 2026-09-04.** Recording
only the decoders was half the truth, and the missing half bit the same day it was noticed: five
names were added to `SupportedFormats` for formats libopenmpt had always been able to play, and
every index in existence was instantly missing 5,558 Modland files while reporting itself perfectly
current — because no decoder had changed. `SupportedFormats.fingerprint` is folded into
`NativeEngine.backendsFingerprint`, so a name added to that list expires exactly the indexes it
would have changed, with nobody having to remember.

### Scanning while music plays

Measured rather than assumed, and the answer changed. sc68 **2.2.1** kept its 68000 emulator in
global state, so opening a second instance while one played clobbered it — which is why background
metadata resolution in this app waits for playback to stop, and why probing a whole library looked
blocked before it was started. sc68 **3.0.0b** is instance-based, and
`native/probe/sc68/probe_concurrency.c` runs four threads each creating, loading, playing and
destroying its own player twenty times: no failures, every thread producing audio. So a scan runs in
the background and the music keeps playing.

## 19. What the cache keeps

Added 2026-09-03, answering `docs/OPEN_QUESTIONS.md` Q5. Before this, nothing was ever deleted.

**512 MB, least recently used first.** These files are kilobytes to a few megabytes, so that is
thousands of tunes — a library rather than a cache. It is a chosen number rather than a measured
one, and exposing it as a setting is `docs/BACKLOG.md` A13.

Enforced **after every successful write** and **once at start-up**. The second is what makes an
installation that grew past the limit before the limit existed converge on it instead of sitting
over it forever.

### What counts

Only the fetched-file cache, and that is structural rather than a rule anybody has to remember —
the things that must survive live in different directories entirely:

| | where | counted |
| --- | --- | --- |
| fetched tracks | `cacheDir/remote` | **yes** |
| ASMA archive (20 MB) | `filesDir/catalogues` | no |
| HVSC song lengths, the library index | the database | no |
| copies made for sharing | `cacheDir/shared` | no |

### What is never evicted

- **Unfinished downloads.** A `.part` file is a fetch in progress. The budget neither charges for
  it nor deletes it: charging would bill the user for bytes that may never become a file, and
  deleting would corrupt a download that is still running.
- **Anything in use** — the track playing and the ones read ahead. A file deleted mid-read looks to
  the user exactly like a corrupt download.

A protected file still **counts**. It is real disk taken by real bytes, and excluding it would let
the cache sit above its ceiling while reporting that it does not. When protected files alone exceed
the ceiling, everything else goes and no more: deleting what is playing to satisfy an arithmetic
target would be the cache breaking the app in order to obey itself.

### Cache transactions and Browse ownership — 2026-10-16 trial

`RemoteFiles` retains transfer sharing and URL keys; `RemoteFileCache` owns the fetched directory's
read/touch/write/accounting/eviction transactions under one instance monitor. Network transfers
run outside that monitor. An eviction snapshot and its deletes cannot interleave with a cache
replacement or read. The application-scoped controller has one RemoteFiles/cache owner; this is
not cross-process coordination or protection from Android clearing cache files.
`AtomicFileWrite` gives each cache/archive writer its own `.part` sibling, renames only complete
bytes and removes only that writer's staging file on failure. Archives remain outside the budget.

Browse uses `navigationVisit` and a route-qualified `BrowseReadOwner`, rather than relying on
cancellation or route equality. Register selection before launching a read; check ownership inside
the atomic publication. A restored snapshot gets a new visit from current state. Back publishes
the parent transition before calling the parent reader, which can complete immediately.
Read-ahead separately retires its generation before cancelling work or accepting an empty/held
selection; late cancelled or obsolete completions cannot repopulate memory.
Merged into develop on 2026-10-18 with explicit owner authorization ahead of phone acceptance.
Device behaviour remains unchecked; merging does not close the phone trial.
[Contract and verification](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_BROWSE_DOWNLOAD_STABILITY.md).

### And what the user can throw away themselves

**Since 2026-09-04 everything the app stores has a delete next to it**, in a Storage section on the
Online catalogues screen: the fetched-music cache, each downloaded archive (ASMA's is 20 MB), each
catalogue index, and the HVSC song lengths (5.2 MB). Before that, the app took disk and said the
number without offering any way to give it back — which is better than taking it quietly and still
not good enough on a phone.

**A catalogue is one row, not two.** For an archive catalogue the downloaded zip *is* the index —
ASMA publishes a single file that is stored whole and parsed in place — so offering the file and the
rows as separate deletes offered two ways to break one thing. Taking either left the other
describing a catalogue that no longer worked.

**One rule holds the section together: everything on it is a copy.** Each item came from somewhere
that still has it, and each is re-fetched by a button already on that screen. The user's own things
are deliberately absent — playlists, granted folders and history are not storage to be reclaimed,
and a screen that mixes "this is a cached download" with "this is your work" teaches people to be
afraid of it.

The fetched cache deletes without asking, because it refills itself as music plays and a mistaken
tap costs one slower song. Everything else takes a real download to get back and asks first, naming
what stops working until it is fetched again.

**It lived in Browse until 2026-09-04 and has moved to Settings**, in one piece, which is what the
placeholder note here promised. Browse is for finding music; what the app is keeping on the phone
is not that.

### Why it is a separate object

`CacheBudget` has no Android imports and **no clock** — it is handed a list and returns names to
delete. Reading the directory and deleting is `RemoteFiles`'s job. That split is the whole reason
the rules have tests: Q5 stayed open for weeks partly because there was nowhere to write one. The
ordering has a name tie-break for the same reason — a rule that cannot be predicted cannot be
tested.

**A ceiling stops growth; it does not give the disk back.** Nothing in the app deletes the ASMA
archive or the song lengths (A13). The online screen at least says what is held, because an app that
takes disk quietly is worse than one that takes the same disk and says so.

## 19b. Settings, and the gear that opens it

`docs/BACKLOG.md` A13, built 2026-09-04.

**A full-screen modal, exactly like Browse.** Same shape, same back rule, same way out. That is the
whole reason for the choice: `docs/OPEN_QUESTIONS.md` Q1 describes a navigation model with one rule
— back descends one layer — and a second modal costs nothing to learn. A bottom sheet was the
alternative and fights the dock as soon as it scrolls.

**A labelled gear, not an overflow menu.** A menu holding a single item is worse than the button it
hides. The first version used an unlabelled gear; it was rejected on 2026-09-04, consistently
with the application's rule that an action has both an icon and its name. Browse sits on the left,
beside the playlist chooser with a 12 dp gap, while Settings stays on the right. This keeps the two
ways of choosing what plays together without making them look like one compound control.

**A choice you can see is better than a choice you have to open.** Language and theme are both
"pick one of three", and both were a dialog: a tap to find out what the options are, and then the
answer hidden again behind a summary line. They are segmented buttons now — every option and the
current answer visible at once, changed in one tap. `SettingChoice` is one component used twice,
because two controls that behave identically should be one piece of code or they drift.

**Changing either recreates the activity**, exactly as a language change already did. A theme
applied after Compose has drawn is a flash of the wrong colours, which is the visual equivalent of
the half-translated screen `AppLocale.wrap` exists to prevent — so both are read in
`attachBaseContext` and `setContent` rather than remembered in composition.

**The wallpaper palette is a separate switch, and absent below Android 12.** It is a different
question from light-or-dark — *where do the colours come from* rather than *how bright are they* —
and the platform below 12 has no palette to take, so offering a switch there would be a promise it
cannot keep. On by default where it exists, because that is what a Material app is expected to do.

**The app owns its theme colours (C123, integrated 2026-10-28).** The changes from
`fix/redmi-theme-action-alignment` declare platform Force Dark disabled and Xiaomi's documented
`force_dark_google=true` opt-out. Compose still chooses Light, Dark or System; OEM transformation
must not recolour an already selected scheme. MIUI effectiveness remains pending a phone test.
Its custom Light fallback uses cool neutral surfaces, dark ink and muted blue/teal accents in
`ui/theme/LightColors.kt`. Keep colour roles together rather than overriding individual widgets.
Android 12+ wallpaper colours still take precedence when System colour palette is enabled;
the Dark fallback remains unchanged. This is a candidate palette pending visual acceptance.

**System-bar icons use the same effective theme (2026-10-28 follow-up).** `AppTheme.isDark`
resolves the app override for both Compose and `enableAppEdgeToEdge`. The latter retains AndroidX
edge-to-edge setup and adjusts status/navigation icon appearances before the first content frame.
Following system mode independently made the clock unreadable with app Light and system Dark.

**One scrolling screen with sections, not a tree.** With this little in it a tree is ceremony, and
every extra tap stands between somebody and the thing they came to change.

**Nothing in it was invented for it.** Every switch is a setting the app already had somewhere less
findable — "play every tune in a file" was global all along and reachable only from inside Now
Playing, on a file that happened to have more than one tune. Storage moved from Browse. Language is
chosen inside the application from System, Polish and English on every supported Android version.
The choice is stored privately and applied to the activity context before Compose reads resources;
returning to System removes the override rather than copying today's system language into it.

**About is not decoration.** A GPL-3 app distributed through a store should be able to say what is
inside it: the version, the decoders and their versions — read from the engine's own fingerprint,
the same string an index records to know it is stale — and where the source is.

## 20. Where the time goes on screen

Changed 2026-09-03, to this layout:

```
0:36  ─────────●──────────────  2:20
```

Position on the left, length on the right, the bar between them. Before, both sat beside the title
as `0:36 / 2:20` — one string to decode, in a row that had become crowded once the tune count joined
it. Flanking the bar, the two numbers read as **where this line starts and where it ends** rather
than as a fraction to parse.

## 21. The dock is sized for a glance, not for a desk

Changed 2026-09-04, and the reason is the specification: *"when I am driving it is hard to hit
them."*

Material's 48dp touch target is the minimum that counts as reachable when you are sitting still and
looking at the screen. The transport is **56dp with a 30dp glyph**, and play is **64dp with 34**.
The glyph grows with the target deliberately — a large button around a small mark is harder to aim
at than a small button, because the eye aims at the mark.

The title moved from `titleSmall` to `titleMedium` and the author from `bodySmall` to `bodyMedium`,
and **the dock grew to fit** rather than the text being squeezed to preserve its old height. The
owner offered that trade before being asked for it, which is the right way round: the dock is on
every screen, and the thing it is for is being read at a glance.

### WEB live language ownership — W24, 2026-10-28

Language policy (`system`, `en`, `pl`) is independent of its resolved locale. Settings changes and
browser `languagechange` events refresh presentation in place; an explicit choice ignores later
system changes. A refused preference write leaves the session choice usable and explains that
it was not saved. The audio graph, decoder job, playback plan, draft and navigation are untouched.

`ui-message.js` represents explicit UI meanings with action-time argument snapshots.
`i18n.js` resolves those meanings and Polish plural forms at presentation time. `ui-text.js`
weakly binds individual text nodes and owned accessible attributes. Replacing a fallback with a
raw title retires its old meaning even if the letters are identical. Original markup is captured
once, including an initially English page; later user rows are never inferred to be translations.
State-derived labels use explicit pure formatters, while past action messages keep their original
arguments. No renderer retains a later mutable playlist merely to translate an earlier notice.

This separation preserves rows, icons, event handlers, input selection, focused controls and
pending Undo actions. File/title/author/playlist data and backend errors stay literal. Music-source
acknowledgements use their held bilingual table; read-only privacy prose is redrawn from the held
policy without another request or a scroll reset. Licence texts remain as distributed.

The page integration gate evaluates the actual ESM graph inside the existing host DOM/audio
fixture. Flattening imports had hidden a missing production `catalogue` import; real-module startup
rejects that defect. Node's VM flag belongs only to tests and adds no WEB runtime dependency.
Host DOM and audio-message evidence do not establish real-browser sound or device behaviour.

### WEB retained music — W20, 2026-10-28

Browser schema **6** adds `remoteMusic` and reserves `asmaArchives`. An upgrade preserves the
existing documents, indexes, bookmarks and History qualification; it neither downloads nor seeds
music. Permanent archive payloads have a separate lifetime and do not consume the fetched-file
budget. A future schema is refused without reset, and a blocked upgrade can be retried.

`cache-budget.js` ports native CacheBudget's 512 MiB ceiling, completed-only accounting and
LRU(time,name). The browser keeps original URL identities, with native SHA-256 URL names for the
same-timestamp tie-break. `store.js` publishes an immutable Blob and evicts in one transaction.
Actual positive payload size establishes presence; a metadata flag does not. Current/read-ahead
protection is sampled under the write transaction; protected bytes still count. Accounting reads
Blob handles/sizes, rather than allocating every cached ArrayBuffer. This is not a browser memory
or permanence guarantee. Failed publication or eviction rolls back the attempted change.

`RemoteMusic` owns shared transfers, persistence, membership and clear/publication generations;
UI cancellation retires a waiter, not another caller's transfer. `music-download.js` bounds the
browser's combined connect/header wait to 45 seconds (native connect15/read30 allowance) and
renews the native 30-second inactivity deadline for each body read. Fetch exposes no separate TCP
connection timer. Failed/partial transfers never publish a copy. Every decoder caller owns its
buffer, and retention/Blob/quota failure cannot withhold successfully fetched bytes.

The common app resolver serves playback, Random and file/Information/audio actions. Runtime
handoff bytes take precedence and are copied without persistent publication, even for HTTP URLs.
Only absolute HTTP(S) fetches enter retained storage; relative/local/data identities do not become
silent durable imports. Clear retires old publication tickets, removes only fetched copies and
keeps current audio, pending requests, drafts and user documents. The ticket guard is deliberately
stronger than native's directory clear followed by possible late background publication.

Browse/Search/History decorate the existing leading slot with native's phone/tick; selection owns
that slot and played-before stays below it. Playlist/Random/Queue numbers gain no extra cache UI.
`MusicCacheView` retains its controls while sizes/failures change and offers clear/retry. It reports
actual bytes and tiny frees honestly; the origin-wide quota estimate remains separate. In-process
subscriptions plus focus/visibility reads refresh membership; cross-tab durable playback leases
are not supplied. Browser quota/persistence and real offline playback still require acceptance.

### WEB complete ASMA archive — W21, 2026-10-28

The explicit ASMA action retains the compressed ZIP and derives its catalogue in schema 6's
reserved `asmaArchives` store. One transaction replaces or removes both archive and index;
quota, cancellation and corrupt input preserve the prior installation. A persisted revision
ticket also prevents an older download in another tab from restoring deleted or newer data.
Startup and migration never fetch the archive. Existing HTTPS playlist/History identities remain.

`zip-archive.js` validates directory/member agreement, bounds, paths, overlap, sizes and CRC before
publication. Browser bounds are 64 MiB per expanded member and 512 MiB expanded per archive;
unsupported encryption, ZIP64 or raw-DEFLATE capability produces an explicit refusal. The
compressed immutable Blob is retained rather than thousands of duplicate expanded cache entries.

`OfflineAsma` checks current storage and returns a fresh verified member buffer through the common
byte resolver, after live handoff/prepared bytes and before individual HTTP/fetched-cache access.
Damaged members remain unavailable for that archive revision even after metadata refresh; a new
installation can repair them. Permanent ASMA does not enter the fetched-music LRU, and clearing
that cache does not remove the archive or affect an already playing buffer.

`AsmaView` projects actual archive/index facts into existing catalogue and storage controls.
Generation/snapshot guards reject old reads; focus/visibility refreshes reflect other-tab changes
without replacing focused buttons. Index presence alone never establishes offline availability.
Confirmed deletion removes index and ZIP together while keeping documents, History and playback.
Host parsing/storage/DOM evidence and real-browser acceptance are recorded separately in the
[implementation ledger](WEB_IMPLEMENTATION.md#w21--complete-offline-asma-2026-10-28).


### WEB retained Modland addresses — W23, 2026-10-28

catalogue-urls.js owns the native-compatible segment encoder and the narrowly scoped legacy
unknown-author recovery rule. Catalogue URL construction and the bounded music reader use it
without importing one another. The shared native/WEB cases cover nested formats, filenames,
literal plus, Unicode and invalid or ambiguous routes.

Only an original HTTP404 from the exact Modland module root permits one request with the moved
author directory. Refused bodies are discarded, each attempt has a fresh header allowance, and
transport/access/server failures remain explicit. The alternate address never becomes an ID:
RemoteMusic retains complete bytes under the original URL, so playlist references, History,
sharing and offline replay remain stable. Cancellation and Clear use the existing transfer and
publication owners. Browser CORS and credential rules remain authoritative; no proxy or custom
Cookie/User-Agent machinery is introduced.


## WEB playback stereo mix — W14, 2026-10-29

The browser uses the same [StereoMixer](../native/engine/stereo_mix.h) as the native output.
Each WASM decoder handle owns its mixer and target width; `pt_render` processes only the frames
actually decoded, at the requested output rate. A fresh handle defaults to Original, so audio
exports and file descriptions remain independent of the live decoder. Original bypasses PCM
writes; Narrow and Mono use the existing convex channel matrix and up-to-10 ms width smoothing.
Zero/invalid render requests do not advance the decoder or initialize a ramp.

`stereo-mix.js` owns stable stored identifiers, width mapping and refusal-aware preferences.
Settings controls change the global in-memory selection and send a target to the existing
worklet without opening, seeking or resuming audio. The worklet retains that target across files
and applies it before the first rendered frame. Its constructor receives the latest selection
after asynchronous engine loading; later messages retain their order while compilation is pending.
Seek, rewind and subsong changes retain the target. Only a new decoder resets mixer initialization.

The shared [mode cases](rules/stereo-mix-cases.tsv) bind Kotlin and JS defaults/widths; the fixture
is an explicit native test input. Production PCM/worklet tests cover asymmetric, correlated and
opposite-phase input, multiple sample rates, retargeting, paused changes and independent handles.
The browser audio device and encoder require separate real-browser acceptance.
