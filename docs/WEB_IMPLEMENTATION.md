# WEB feature implementation

Started: 2026-10-26. Branch: `feature/web-feature-parity`.
Baseline: `3183eeec7265cd88a88e09db4ee76706e6421d51` (accepted develop).

Implement the [selected scope](WEB_ANDROID_PARITY_2026-10-26.md#scope-selected-after-the-inventory--2026-10-26)
as separate feature/substage commits. The ignored local GOAL.md is the execution checklist.
Source inspection and host checks are not physical-browser acceptance. No automatic merge,
push, publication, APK, release version change or AMP integration is part of this work.

## Reference and storage allocation

### Resume — 2026-10-28

Resume the remaining scopes after integrating Android appearance/clock changes into develop
**c8209b1ab79976ec28d27e1da447055756c7ca29**. Use that accepted Android reference for new
comparisons. The WEB branch was rebased from **488d3a9** onto develop; original history remains
at `backup/web-parity-before-resume-20261028`. The duplicated report-only C122/C123/C124 commit
was omitted because develop already includes the reports and newer findings. Both documentation
insertions were retained during conflicts. The entire `web/` tree equals the preserved original.
Resumed head: **dc8c850229f1478bd686cf448d0ecad83413e0ad**. All **865 page checks** pass after
the rebase; this is not a new combined Android/WEB full-gate claim.

Android's accepted normalization target is now **-18 LUFS**, superseding the initial W15 plan's
-23 LUFS after the 2026-10-27 policy change. Retain its caps/peak guard and keep the slider/AMP
experiments separate. W01/W02/W04-W08 are retained; W03 Search-scope integration and W11-W25 remain
open. No WEB merge, deployment or additional APK is authorized by this resume.

The initial Android reference was the baseline above; resumed comparisons use **c8209b1**.
Read production code and existing Kotlin/native
tests for each scope; port their actual scenarios and expected results to WEB checks. Shared
fixtures are preferred where they express the same rule. Existing historical plans can describe
earlier states; current accepted code/tests govern behaviour unless an actual defect is recorded.

The browser database on develop is version 4. This branch implements the coordinated upgrades:
version 5 adds listened History flags and bookmarks; version 6 adds retained remoteMusic and the
reserved asmaArchives store. W20 enables fetched-music retention; W21 will enable the permanent
archive without another schema change. Startup/migration performs no full archive download.
Existing user stores, URL identities, playlist ownership and phone-handoff byte lifetime survive.
Android schema remains 23. No other worktree or shared toolchain is changed.

## Baseline checks

The preflight `./scripts/test-protracktor.sh --really` passed: **1217 tests / 159 suites**, zero
failures/errors/skips, **766 s** overall; 606 page checks, 49 engine checks and all existing
companions passed. Lint completed with zero errors in 126.6 s. This run started before the new
parity test compiled, so it does not claim execution of that additional class. Original Android
production code, decoder input paths and WEB entry point remained unchanged. Retained preflight
logs/JUnit results are under ignored `user/web-parity-verification/baseline/`.
Installed jsdom and the existing WASM are present. Baseline WASM SHA-256:
`ef14ae1a028aa36ca35c9425f7f2f3cee8c2fbdd5e298e715241377a814259ef`.

## Scope ledger

Each row needs implementation, a reference-to-WEB scenario comparison, meaningful regression
evidence and final integrated checks. A model-only substage does not complete a user feature.

| Scope | Feature | State |
| --- | --- | --- |
| W01 | Independent playback context and selected playlist | Implemented; integrated full host gate passes; browser/device acceptance unchecked |
| W02 | Full manual queue and list interactions | Implemented, including native live-drag timing completed in W12a; full host gate passes; browser/device acceptance unchecked |
| W03 | Return to playback source | Implemented including History and Search query/scope restoration; full host gate passes; browser acceptance unchecked |
| W04 | Remembered Random and deliberate reroll | Implemented; combined full host gate passes; browser acceptance unchecked |
| W05 | Random platform/Favourites filters | Implemented; full host gate passes; browser acceptance unchecked |
| W06 | Qualified played-before marks | Implemented; full host gate passes; browser acceptance unchecked |
| W07 | Saved catalogue places | Implemented; full host gate passes; browser acceptance unchecked |
| W08 | Learned list titles | Implemented; full host gate passes; browser acceptance unchecked |
| W09 | Recent-search suggestions | Implemented; full host gate passes; browser acceptance unchecked |
| W10 | Live History search | Implemented; full host gate passes; browser acceptance unchecked |
| W11 | Search scopes and saved-playlist coverage | Implemented; full host gate passes; browser acceptance unchecked |
| W12 | Playlist reorder and import/export | Implemented in W12a/W12b; full host gate passes; browser acceptance unchecked |
| W13 | Bulk Browse/Search/History actions | Implemented; full host gate passes; browser acceptance unchecked |
| W14 | Original/Narrow/Mono | Implemented; forced complete gate passed; browser acceptance unchecked |
| W15 | Playback volume normalization | Implemented; forced complete host gate passed; browser acceptance unchecked |
| W16 | Compact dock and subsong indication | Implemented; full host gate passes; browser acceptance unchecked |
| W17 | Browse position and subsong following | Implemented in W17a/W17b; full host gate passes; browser acceptance unchecked |
| W18 | Playback-mode/settings restoration | Implemented; forced full gate passed; browser acceptance unchecked |
| W19 | Read-ahead and prefetch controls | Implemented; forced complete gate passed; browser acceptance unchecked |
| W20 | Persistent music cache and offline marks | Implemented; forced full gate passed; browser acceptance unchecked |
| W21 | Complete offline ASMA | Implemented; forced full gate passed; browser acceptance unchecked |
| W22 | Grouped downloads and Stop | Implemented; full host gate passed, browser acceptance pending |
| W23 | Legacy Modland request recovery | Implemented; forced full gate passed; browser acceptance unchecked |
| W24 | Language changes without interrupting playback | Implemented; forced full gate passed |
| W25 | Browser Media Session and external controls | Implemented; forced complete gate passed; physical OS routing unchecked with the documented platform limitation |

## W01 analysis

Reference production paths: `player/PlayerUiState.kt` (`activePlayback`, `withEditorQueue`),
`PlaylistSessionState.kt` (`switchPlaylist`, `writeSnapshot`, `discard`), `ActiveQueue.kt`
and `PlaybackController.kt`. Reference tests include `ActiveQueueTest`, `TrackEditingTest`
and `PlaylistSessionTest`.

At baseline, WEB makes its `queue` both the transport source and the visible playlist. Random
and Browse park one playlist in a session stash. `choosePlaylist` ends that session before reading
the selected playlist, while `setQueue` changes the playback cursor and clears loaded state.
This explains why selecting a view can interrupt playback, and why merely changing a button
cannot fix ownership. Playlist saves must always snapshot the selected editor, while transport
and current metadata must keep referring to the active source until an explicit play selection.

Required comparison scenarios:

1. Play playlist A, select playlist B: A keeps playing and Next/Previous walk A; B has no active
   playback highlight. Tap B: B becomes the source at the tapped occurrence.
2. Play Browse/History/Random, show or select a playlist: playback and its source stay unchanged.
3. Edit/save/discard B while A or a collection plays: only B's draft/storage changes.
4. Play A while it is selected: accepted edits to that live draft retain Android's linkage.
5. A delayed playlist read or save cannot overwrite a newer selected draft or playback source.
6. New/delete/rename/Save as and protected phone-handoff paths must keep their intended document
   semantics without conflating selected playlist identity with playback identity.

### W01 model substage

`web/src/playback-context.js` supplies immutable finite order, source identity/occurrences and
selected-editor ownership. It ports the relevant PlayQueue/ActiveQueue/PlaylistSessionState rules,
including shuffle history, explicit Next versus Repeat One completion, source omissions and
protection from a late save clearing a newer edit. At that model-only checkpoint the module was
not connected to the page; that substage alone does not establish a completed W01 user feature.

The new `docs/rules/playback-context-cases.tsv` has 32 transport cases, consumed both by
`PlaybackContextParityTest` against production Kotlin ActiveQueue and by
`scripts/check-playback-context.mjs` against the WEB model. The Kotlin file input is registered
with Gradle. The complete JS model suite currently passes **66 checks**, including the shared
cases and ownership/immutability cases ported from existing Android tests. A deliberate mutation
replacing the active context when a different playlist is selected fails the ownership test;
restored code passes. The focused production-Kotlin parity test also passes all 32 fixture rows.
Page/event integration follows below. Logs and mutation evidence
are retained under ignored `user/web-parity-verification/`.

The advancing-during-save regression first failed: a transport copy unnecessarily allocated a
different immutable track array, so a valid pending save could not clear dirty state. Transport
copies now retain an already frozen content snapshot. The suite passes **66 checks** including
that correction. Changing an expected result in the shared fixture also fails the WEB guard;
the fixture was restored byte-for-byte. The same fixture mutation makes the Kotlin test fail
without `--rerun-tasks`, proving Gradle tracks the declared input; restored data passes again.
The new domain suite is registered in the complete gate. The test-runner's 14 fixture checks and
shell syntax validation pass. The forced full model-candidate gate passed **1218 tests / 160
suites**, zero failures/errors/skips, in **662 s**: all 31 unit-stage tasks executed. It includes
the Kotlin fixture test, 66 new domain checks, 606 existing page checks, 49 engine checks and all
other companions. Lint completed in 8.6 s using up-to-date analysis/report tasks: zero errors /
79 warnings. The preflight had executed lint analysis; this is not a claim of repeated analysis.
Exact input hashes in `user/web-parity-verification/w01-model-candidate.json` still match the
checked files. Full logs and JUnit/lint reports are retained under
`user/web-parity-verification/w01-model-full-gate/`. No bundle or browser/device result is claimed.

### W01 page integration — 2026-10-27

Editor/source integration: `0d3458c`, following the domain foundation `3676bc4`.

The page now uses the selected editor independently of its transport source. The playlist sheet,
Save, Discard, Save as, row removal/Undo, Add and the return from a collection all address the
selected document. Changing its view sends no decoder command; an explicit track choice transfers
playback. Author-folder navigation follows Android `PlaybackController.showNeighboursOf`: simply
opening the folder must not transfer Next/Previous ownership. The previous WEB-only Resume Random
path and its automatic source transfer are removed. Remembered Random entry remains W04.

The page preserves the existing engine boundary and a compatibility projection of finite/Random
transport into `PlaybackWorkspace`; W02 will integrate scheduling into this boundary. W01 does
not claim the manual queue, remembered Random, queue-source navigation or the completed transport
parity of W02. Android production code and the browser database version remain unchanged.

Save snapshots keep their document identity through asynchronous writes. Per-document write
serialization also covers Rename/Delete: a delayed save cannot restore old contents/name or
resurrect a deleted document. Failed Save leaves the draft dirty and the decision open; failed
Discard keeps the draft and reports the read error. An incoming full queue selects the phone
document before publishing its contents, cancels the previous decoder and waits for Play.
Its persisted form still excludes transferred phone bytes. Slow view reads and startup restoration
cannot replace a newer selected editor. Shared single-tune links retain their collection semantics.

Playback loads carry a request identity through fetch/metadata work and actual worklet replies.
An obsolete download cannot open a decoder; old `opened`, clock or failure replies cannot describe
the new source. This changes the JavaScript message envelope, not the native/WASM API or DSP.

Focused host page checks pass with the real IndexedDB adapter and actual DOM event handlers.
The worklet suite also invokes the actual processor against the existing WASM, proving queued
open identities, subsong/seek/clock identity and decoder-refusal identity. An initial version of
the new page checks observed the network-request log instead of the worklet port. That harness
error was corrected; a nonzero real-command assertion and isolated ownership/reply mutations now
prove the intended guards fail. Earlier incorrect-port results are not decoder-command evidence.
Regression logs, original failing Discard/link/document-write cases and corrected mutation logs
are retained in ignored `user/web-parity-verification/`.

The complete forced integration gate passed **1218 tests / 160 suites**, zero failures/errors/
skips, in **611 s**. Unit execution took 435.6 s; all unit-stage tasks executed. The gate includes
**642 page checks**, **52 engine/worklet checks**, 66 context-model checks and every existing
companion. Fresh lint analysis completed in 85.2 s with **zero errors / 79 warnings**. All eleven
application/check input hashes in `w01-page-candidate.json` still match. Logs/JUnit/lint XML are
retained under ignored `w01-page-first-full-gate/`.

An additional isolated IndexedDB check exposed a W01 regression: receiving a full queue selected
the phone document in memory without persisting that selection, so reload could restore an older
playlist. Its exact persistence assertion failed. Incoming queues and ordinary editor selection
now use one serialized selection writer; a delayed document save cannot change the selected ID.
The restored page suite passes **643 checks**.

The corrected W01 code and prepared W02 model passed another forced complete gate: **1218 tests /
160 suites**, zero failures/errors/skips, **610 s** (unit execution 433.7 s), with 643 page checks,
52 engine/worklet checks, 66 context cases, the 29 new schedule cases and all existing companions.
Fresh lint analysis: **zero errors / 79 warnings**, 85.5 s. All thirteen input hashes in
`w01-final-w02-model-candidate.json` match. Complete evidence is retained under ignored
`w01-final-w02-model-full-gate/`.

Core editor/source separation is checked on the host. At that checkpoint the dock's Add visibility
still used the old Random/collection predicate. W02's integration now offers it when playlist A
plays while selected playlist B is visible, matching Android `awayFromPlaylist`; the actual dock-
click regression passes. The final W02 integrated gate below subsequently covers this case and
closes W01's host implementation checks. Layout,
touch, real audio and background behaviour remain **unchecked in a real browser/device**. No bundle
is handed off and no merge or deployment occurs.

## W02 analysis and draft

Reference: Android `PlaybackSchedule.kt`, `PlaybackPlan.kt`, the two matching test classes,
`PlaybackController.applyScheduledStep`, `applyQueueStep`, `playContextEntry`,
`removePendingRequest`, `removeContextEntry` and `advanceAfterRemoval`. Accepted presentation
contracts are `QUEUE_UNIFIED_LIST.md`, `QUEUE_CLEAR.md`, `QUEUE_MANUAL_REORDER.md`,
`QUEUE_SWIPE_INTENT.md` and later accepted row/follow corrections, not the older tabbed proposal.

Keep a source order and a separate one-shot FIFO underneath one rendered list. Duplicate files
have distinct occurrence keys. Played manual requests disappear; Previous from manual playback
returns to the parked source, retaining pending requests. An explicit new source plays its chosen
row first, then retained FIFO requests. Repeat One handles natural completion before the FIFO;
explicit file Next advances it. Shuffle orders only source rows. Clear retains the actual track,
its origin and current open job, detaches its source and supports guarded Undo without rewinding.

Session-only source omissions never edit a playlist document. Removing the current occurrence
advances with the existing playing/loading intent; during pause it selects without opening or
recording the successor. Pending-only reorder cannot cross into source/current rows. Undo restores
content, never audio traversal. Stale source actions are generation-bound. A current manual track
must own metadata/History/transport independently of the source cursor; the W01 editor stays the
only playlist persistence owner.

An ignored draft first ported the production schedule/clear model and **29 Android test scenarios**.
The model now lives in `web/src/playback-schedule.js`, with the ported cases in
`scripts/check-playback-schedule.mjs`, registered in the full gate. It supplies one-shot scheduling,
stable source/manual row projection, pending-only reorder/removal/Undo, origin-aware occurrences
and Clear's guarded snapshot. This model-only substage did not supply the application's manual-queue UI;
the later transport/panel checkpoints do.

All 29 production-model cases pass. A production-model mutation replacing Previous-to-anchor with
ordinary source Previous fails its specific guard; restored code passes. The model/check inputs
are part of the thirteen-file candidate and the forced **1218-test / 160-suite** full pass recorded
above. No Android production/schema/native/dependency change is introduced. This checked model
substage must not be reported as a completed W02 feature.

A separate ignored draft first ported controller state-to-effect handling and removal intent,
including paused removal, actual History origin and deferred Random draws. Forcing autoplay on
paused removal failed its exact guard. That draft was subsequently promoted to production with
**14 controller-derived cases** in transport checkpoint `3d3c8d9`, described below.

At that model checkpoint, remaining work was playback integration including subsong/end/failure/cancellation paths, one panel
above the shared dock, stable/animated row updates, labelled
menu/keyboard equivalents, directional gestures and pending-only reorder with Android's vertical
priority. The following checkpoints record those substages and their integrated checks.

### W02 transport integration checkpoint — 2026-10-27

The page now publishes immutable source contexts instead of reconstructing shuffle history from
mutable UI arrays. Those arrays are presentation mirrors only. `playback-steps.js` ports Android's
state/effect boundary; opening the actual file is separate from advancing its parked source.
The current occurrence supplies decoder bytes/filename, dock, metadata, sharing, History origin
and retries. Explicit Next/Previous and natural completion route through the schedule. Existing
all-subsong controls remain ahead of file advancement; Repeat One rewinds the actual subsong.

Main/Browse/History track menus enqueue one-shot duplicates. Enqueue does not play or dirty a
playlist. The common dock Add targets the selected editor even while a different playlist plays.
Clear/Undo and removal preserve actual loading/paused intent, retain live linked-editor edits
and invalidate speculative Random work independently of the actual open job. A failed manual
file over Random is retained for explicit retry rather than discarded as a failed random draw.
Undo callbacks are bound to their snackbar generation and cannot execute after a newer notice.
The Queue panel, gestures and physical grip are the following presentation substage.

An integration regression found that inspecting Next could deal a repeat-all shuffle lap and
consume RNG state. The new read-only availability guard first failed (76 draws versus 4 at the
checkpoint); availability is now pure and only actual advancement deals the next permutation.
The old shuffle harness used a decoder helper as if it were Next. It now traverses through actual
Next/Previous button events while retaining the same shared fixture expectations.

**Partly checked:** focused host checks pass **666 page assertions**, **67 context cases**, **29
schedule cases**, **14 state/effect cases** and 14 runner fixtures. Fresh reference execution of
`ActiveQueueTest`, `PlaybackScheduleTest`, `PlaybackPlanTest`, `ManualQueueControllerTest` and
`ManualPlaybackStateTest` passes **81 tests / 5 suites**, zero failures/errors/skips, 116 s.
The new page cases exercise actual menu/dock/transport controls and real IndexedDB; they cover
duplicate FIFO, Previous anchor, Repeat One, History origin, Clear's snackbar Undo, manual-only
exhaustion and failed manual retry over Random. Disconnecting enqueue and substituting the source
cursor for the actual occurrence each fails its intended page guard. Logs and reference XML are
retained under ignored `user/web-parity-verification/`.

This checkpoint is **not** a complete W02 feature or a fresh full-gate claim. Full tests/lint are
still required on the integrated panel/gesture candidate, along with actual interaction guards.
Device/browser rendering, gesture feel, audio output and background controls remain unchecked.

### W02 panel and interaction checkpoint — 2026-10-27

Queue now replaces list content above the shared dock. Its labelled toggle stays outside the
Now Playing card; opening reveals/focuses the current row, and Close/Escape restores the underlying
Browse or playlist view without a transport command. Help is collapsed initially. One keyed list
projects the source and one-shot occurrences, with a manual mark and insertion-only indentation.
Clear preserves the actual row without that indentation. Current rows own their full-width colour.

All replayable main/Browse/History rows support right-add. Queue additionally supports left-remove,
row tap, long-press Information and mouse/keyboard menus. Pending grips have pointer, arrow-key
and menu equivalents. Preview stays within pending boundaries, swaps after a full row as in
Android and scrolls at 480 logical pixels/s near a 48-pixel edge. Generation/membership changes,
capture loss and additional pointers cancel it. Source/current rows have no grip.

Shared swipe arbitration waits for max(24 logical pixels, twice slop), requires 2:1 horizontal
dominance and latches vertical intent. It suppresses abandoned taps, handles reversed releases,
cancels an additional pointer anywhere and preserves later button/keyboard actions. A handled
long press suppresses the browser's extra context menu. Reveals use named icons/text with green
add and red remove backgrounds. Departures fade for 160 ms; remaining placement moves for 220 ms,
with no whole-list appearance fade. Reduced motion removes departures immediately. Exiting rows
are inert, not focusable/current; Undo can revive their node without an obsolete completion
removing it.

Reference comparison includes `QueueSwipeIntentTest`, `QueueSwipeRowTest`,
`ManualQueueReorderUiTest`, `QueueFollowUiTest`, `QueueClearUiTest` and the schedule/controller
tests already listed. Host guards enter real page callbacks, MessagePort and IndexedDB. A missing
panel, wrong grip activation distance, abandoned tap, outside-row second finger, extra context
menu and exit accessibility first failed their respective guards. Disconnecting the actual
right-add or physical-grip callbacks fails its page assertion while independent routes remain.
Clear during a controlled current download preserves its signal/job; completion and subsequent
Undo neither reattach a cleared source prematurely nor reopen the decoder.

**Checked on the host:** the final forced full gate passes **1218 tests / 160 suites**, zero
failures/errors/skips, **570 s**; all 31 unit-stage tasks executed (430.4 s). Companions include
**695 page assertions**, **52 engine/worklet checks**, **67 context**, **29 schedule**, **14 effects**,
**16 swipe**, **9 grip** and **7 keyed-presentation** cases. Lint reports **0 errors / 79 warnings**;
analysis was up to date (7.4 s), following fresh analysis in the first panel gate (86.0 s).
That earlier 652 s gate preceded the pointer/focus/load-race corrections and is retained separately.
The final thirteen application/test input hashes, logs, JUnit and lint reports are archived under
ignored `user/web-parity-verification/w02-panel-final-full-gate/`, based on `3d3c8d9`.

W01 and W02 are technically implemented; **real-browser layout, scrolling, touch feel, audio output
and background operation remain unchecked**. No real browser executable was available; host
geometry and animation hooks are not screenshots. Android production/schema/native/dependencies
are unchanged. No intermediate bundle, APK, merge, push or deployment is part of this checkpoint.
W03-W25 remain required. W03 currently has source analysis and an ignored domain/page draft only.

## W03 source navigation checkpoint — 2026-10-27

Reference: baseline Android `QueueSourceNavigation.kt`, `PlaybackController.openQueueSource`,
`QueueSourceNavigationRulesTest`, actual `QueueSourceNavigationTest` shell actions,
`QueueSourceHeaderTest` and `BrowseWorkflow` Back. The existing source label is an inline labelled
button; None and external sources retain a plain label. The browser's existing multi-track shared
links remain finite collection playback without a fabricated destination. The permanent PHONE
document is not a user-deletable playlist.

Header activation follows the parked source while a manual occurrence plays. Playlist return
settles the selected draft and uses guarded `preservePlayback` loading. Cancel/failure retains
Queue; Discard authorizes abandoning a draft only when its destination is actually read. A
deleted source is not recreated. A completed Save still persists its selected document when a
new source supersedes navigation, without then selecting an obsolete playlist or reporting a
false failure. Captured membership differing from disk is revealed as an unsaved draft;
metadata-only differences do not dirty it or overwrite newer stored titles.

An immutable context route retains exact folder components, submitted query/results and History
page/rows. It does not capture index/storage/download owners. Restoring a route invalidates older
Browse reads and search timers, retires author-jump detours and preserves current resource facts.
Result callbacks capture the query that produced their rows, not pending text typed later.
History starts a fresh top scroll visit. Search Back follows the current Everywhere native rule
without deleting the query; Random Back returns to Browse root. Focus moves to the visible
destination without focusing the Search input.

**Partly checked:** **732 page assertions** and **9 Android-derived/domain cases** pass on the
host. Actual header, Save/Discard/Cancel, paused/manual playback, missing sources, obsolete reads,
delayed successful Save, captured folder/History/Search results and Random-session destinations
are exercised through production handlers/store/MessagePort. Missing History scroll reset and
Search Back first fail their dedicated guards. Disconnecting the header or removing source/read
ownership checks fails its intended page assertion. Input/output errors interrupted one new-file
write; subsequent project-local write/fsync/read, prepared-source comparisons, syntax/diff checks
and successful reruns confirmed that the files were accessible again. No environment repair or Java/toolchain change occurred.

This is a scoped implementation checkpoint, **not full-gate readiness or browser acceptance**.
At this checkpoint the current complete gate identified W02's candidate. The W04 checkpoint below
subsequently validates the integrated context/navigation phase. W03's captured search-scope slot must be
connected to the real W11 scopes, and its History-query slot to W10; those later integrations
remain explicit requirements. Source snapshots are session-only. No Android production/schema,
dependency, APK, merge, push or deployment change.

## W04 remembered Random checkpoint — 2026-10-27

Reference: Android `RandomHistory`, its fifteen tests, `RandomIsNotSearchTest`, `RandomScreen`,
`RandomDice` and the controller's open/reset/selection/removal/read-ahead paths. First entry starts
Random; subsequent entry, even into an exhausted empty session, is navigation only. Choosing a
remembered row activates its session. Next/Previous always follow the actual source, including
an author playing while the remembered Random list is visible. Manual playback has no borrowed
Random highlight. All Random Back actions return to Browse root.

`RandomHistory` owns the immutable heard snapshot, independent cursor/heard boundary and hidden
prepared tail. A serialized fill uses generation checks after both initial sampling and small-pool
fallback. Cancelled producers cannot append late data or release another waiter's lock. Fill(1)
serves foreground draws; fill(3) prepares the invisible tail. Only active Random fetches upcoming
bytes. Clear invalidates speculation but retains the heard record and the actual adopted file
promise, including a Clear in the microtask gap before engine startup returns.

The page separates active `random`, retained `rememberedRandom` and visible `randomView`.
Visible row actions use that record and its positional snapshot, never an unrelated active source
or History origin. Duplicate files retain distinct source occurrence IDs. Source/Queue selection
synchronizes the Random cursor. Existing WEB failed-pick pruning is preserved. Read-ahead can
complete while inactive, but it cannot transfer transport ownership or make hidden rows visible.

The labelled dice uses double mouse/touch activation; keyboard/accessibility has one equivalent
action. Pointer compatibility events do not double-activate it. The icon rotates 360 degrees over
500 ms; reduced motion omits rotation. A reset creates a new session/heard list, retains waiting
manual requests and does not clear global History. It deliberately does not call the different
`RandomHistory.reroll` model operation. The visible hint uses the accepted Android wording.

Integration found three mechanisms: a synchronously adopted prepared pick could accept duplicate
completion signals, and Browse presentation still inferred an author detour from the active audio
source. Also, independent draws within a WEB batch allowed duplicates that native SQL sampling
without replacement does not. The sampler now uses a bounded partial shuffle of pool positions.
Completion coalescing stays asynchronous and blocks a second draw; author presentation is
now bound to the actual navigation jump. Their actual page guards fail before correction, including a fixed-RNG batch test.

**Checked on the host:** the combined forced gate passes **1218 tests / 160 suites**, zero
failures/errors/skips, **777 s**, with all 31 unit tasks executed (544.2 s). All companions pass,
including **760 page assertions** (28 new Random action/MessagePort checks), **52 engine/worklet**,
**67 context**, **29 schedule**, **14 effects**, **9 source-navigation**, **17 Random-history**,
**9 dice**, **16 swipe**, **9 grip** and **7 keyed-presentation** cases. Fresh lint reports
**0 errors / 79 warnings**, 94.7 s. The final 33 input hashes, complete logs, JUnit and lint reports
are archived under ignored `user/web-parity-verification/w04-context-navigation-full-gate/`,
based on `164311f`. WEB inputs were frozen before their companion stages; native sources remained
unchanged throughout the fresh run.

Disconnecting the real dice callback, starting on remembered navigation, leaking History origin
into a visible Random request and dropping an adopted prefetch each fails its specific page
assertion; the restored candidate passes. Mutations of the heard-boundary and fallback-generation
checks fail the ported native model scenarios. W04 is technically implemented. The W03 destinations
now share a passing integrated gate, while W10/W11 query/scope wiring remains required.
**Real browser appearance, physical touch and sound remain unchecked.**
No Android production/schema/native/dependency change, intermediate bundle, APK, merge or deployment.

## W05 Random filters checkpoint — 2026-10-27

Reference: native `RandomScope`, `RandomFilterState`, `RandomScopeSheet`, `Platforms`,
`ModlandFavourites`, `FavouriteStore`, catalogue sampling and the controller's scope-change paths.
`RandomScopeSheetTest` supplies the cold/warm/preloaded/inactive/deleting/error/retry and obsolete-
read cases. The screen owns verified availability independently of the disposable filter panel.
Everything stays selectable; platforms/Favourites need actual retained playable rows. Unknown
counts show checking, never a guessed missing-list message. Warm refresh/error keeps verified
chips usable. Resource changes while inactive invalidate old counters before the next entry.

The native platform declarations produce checked `web/src/platforms.js`; regenerate with
`node scripts/generate-web-platforms.mjs` after changing `Platforms.kt`. The normal gate compares
all declarations, names, directory mappings and chip order against the native source. Classification
is archive, directory, then filename; ASMA is Atari 8-bit regardless of its author sections.
Parentheses inside a directory name are parsed as string data, including `FM Sound Driver (FMP)`.
New indexes retain compact platform summaries; old bucket indexes are counted in batches of at
most 512 records. Dataset-specific revisions and metadata identities avoid repeated scans of
unchanged resources and retire obsolete reads. No catalogue array of half a million tracks is built.

Scope is one persisted native string: Everything, one platform, or Modland's curated Favourites.
Favourites never means playlist membership. Its eleven-column parser rejects shorter subsong rows;
listed paths and their playable Modland intersection have separate counts and missing/unindexed
explanations. Download/Retry remain in the same panel. Native proper names and EN/PL text are used,
with shape plus colour/accessible state for the single choice. Enabled actions do not inherit
hidden unavailable descriptions. Selecting a scope preserves heard rows/cursor/audio and replaces
only hidden preparation. New draws respect the new pool after the heard suffix; inactive Random
cannot take an author's playback. A late initial preference cannot replace an explicit choice,
including choosing the already displayed Everything. Only the latest failed save reports feedback.

Index installation and playability rebuild now replace data/summary atomically. A quota/clone/
cancellation failure retains the old index and unrelated playlists, History and settings. A metadata
snapshot guards a delayed rebuild from replacing a newer complete install; the capability stamp is
written in that same transaction. Mutation notifications describe committed datasets only. Deletion
blocks derived availability until it completes and restores it on failure. None of these changes
adds a store or raises IndexedDB version 4; reserved versions 5/6 remain for later scopes.

**Checked on the host:** **787 page assertions**, **24 scope/platform/availability cases**, **12 catalogue/
storage cases** and **4 startup/ownership checks** pass. Actual chips, pool playback, ten-row C97
continuation, same-panel Favourites download, Retry, deletion, inactive refresh, obsolete reads and
Polish presentation are exercised. Disconnecting chip/Retry routes or erasing heard history fails
its exact page assertion. Guards also fail before the hidden-description, initial-Everything,
parenthesized-platform, cancellation and stale-index-write corrections. The pre-atomic production
ASMA path loses its old index after the controlled real IndexedDB failure; the corrected path keeps it.

A separate host fetch verified the native public Favourites endpoint: HTTP 200, cross-origin
response enabled, 141,821 bytes and 991 distinct parsed paths. Its response and source SHA-256
are retained with the ignored host evidence. This is transport/parser evidence, not a browser
network/UI acceptance result. The forced full gate passes **1218 tests / 160 suites**, zero failures/errors/skips, **695 s**;
all 31 unit tasks executed (434.5 s). All companions ran and passed, including the new scope,
storage and startup checks, 52 engine checks and the existing context/queue/source/gesture checks.
Fresh lint: **0 errors / 79 warnings**, 84.8 s. The final 44 input hashes, complete logs, JUnit and
lint reports are archived under ignored `user/web-parity-verification/w05-random-filters-full-gate/`,
based on `e9959c2`. W05 is technically implemented.
Real-browser appearance, physical gestures, audio and background behaviour remain unchecked.
No Android production/schema/native/dependency change, merge, deployment or intermediate bundle.

## W06 played-before checkpoint — 2026-10-27

Android references: `ListeningSession` and its 16 tests; `HistoryWrites` and
`HistoryPresentationReads` with their ordering/late-read tests; `HistoryStore`, listened/migration
checks, `PlayedBefore`/`TrackLeadingStatus` and controller open, pause, seek, subsong, replay and
Clear paths. There is no new Random exclusion or settings toggle.

Qualification uses the same lesser of monotonic elapsed time and progressing audio position:
five earned seconds, or a confirmed natural end with positive earned time and no seek. Loading,
stalls, pause, failure, empty EOF and unknown-length timeout do not prove listening. Decoder EOF
now carries its real final position, including files shorter than a progress report. A subsong
reply carries the old position before resetting it so the file retains its earned listening time.

One ordered History writer owns record/qualification/Clear. Clear synchronously retires pending
generation tickets and presentation reads; active old writes finish before erase, then new plays
can persist. Capturing the ticket before loading prevents a late opened callback from recreating
cleared rows. Queue Clear retains the audible listening candidate. Explicit replay after History
Clear may start a new candidate; History-origin playback still leaves recency/count unchanged.

IndexedDB v5 backfills only existing pre-v5 History rows and preserves their counts, timestamps
and metadata. Fresh records start unqualified; qualification updates only existing rows and never
recency. Empty History stays empty, and later opens never seed it again. Other user stores survive.
Blocked/failed opens can retry, abandoned upgrades cannot leave a hidden connection behind,
version changes close old connections and newer schemas are refused without deleting data.
Read/modify/write is atomic across tabs and synchronous quota failures roll back cleanly.

Browse/search/History and Random reserve a small lower leading slot; qualification repaints that
slot without rebuilding list rows. Playlist rows and playlist-row Information opt out. Browse and
Queue Information can explain the known mark and the effect of History Clear. Labels and the
explanation are English/Polish; the mark has a shape and an accessible name as well as colour.
Learned-title payloads remain internal until W08; W06 does not change displayed titles.

Focused host checks currently cover the clock, serialized writes, late-read ownership, real
fake-indexedDB upgrades/rollback/retry/preservation, and actual page controls/MessagePort callbacks.
Deliberately broken copies verify threshold, Clear and playlist opt-out assertions; the old EOF
callback fails the short-file final-position assertion before the fix.

The forced full gate passes **1218 tests / 160 suites**, zero failures/errors/skips, **744 s**; all
31 unit tasks executed. All companions pass, including **818 page assertions**, **16 listening**,
**10 write/read-ownership**, **14 real storage/migration** and **3 worklet boundary** cases, plus
the retained context/queue/Random/engine/PWA checks. Fresh lint: **0 errors / 79 warnings**,
94.3 s. The 72 source-input hashes and reports are retained in ignored
`user/web-parity-verification/w06-played-before-full-gate/`. Two WEB inputs were completed during
the unchanged Android unit-test stage, before their WEB companions started; the archived
candidate records that revision, and all final hashes match. A separate documentation-only
commit records the Redmi reports and does not change checked application inputs.
Actual browser appearance, audio timing, touch feel and background behaviour remain unchecked.

## W07 Saved places checkpoint — 2026-10-27

Android reference: `SavedPlace`/`SavedPlaces` and all nine domain tests, `SavedPlacesStore` and its
real database tests, `SavedPlacesScreen`/UI tests, `CatalogueBrowser` local availability/resolution
and `CatalogueBrowseState` parent navigation. The accepted B42 behaviour governs.

Exact JSON tuple keys retain catalogue, format and author, including case, delimiters and empty
authors. Insert-if-absent keeps the original label/time; independent v5 storage survives index
and History deletion. Listing follows native saved-time/binary-key order. Ordered local writes,
one-shot Undo and owned/cancellable reads retain the original record and never overwrite a later
save. Undo queues behind another write. Saving/listing/removal has no decoder or network capability.

Availability reads local group summaries; Open resolves current exact contents and retries an
index generation change. Missing index/group/source states retain the bookmark, explain disabled
Open and offer only applicable explicit catalogue recovery. Browse root and author/game headers
expose labelled actions; the keyed Saved list keeps title/path left and equal Open/Bookmark controls
right. Header toggling removes directly; list removal offers its own Undo without playlist effects.
Back and Queue source snapshots retain the Saved places parent. Native English/Polish labels,
bookmark state, loading/error/Retry and keyboard root entry are covered on the host.

Focused checks pass: 13 domain, 9 storage, 8 catalogue/ownership, 8 DOM presentation and 30 actual
page/control assertions, with retained History regression checks. Six deliberately broken copies
fail their named guards: exact identity, cancelled Open, busy Undo, stale index, disconnected
Bookmark and incorrect snackbar dispatch. The complete page checks pass.

The forced full gate passes **1218 tests / 160 suites**, zero failures/errors/skips, **839 s**, all
31 unit tasks executed. All companions pass, including **848 page assertions**, the 13/9/8/8
bookmark domain/storage/catalogue/presentation checks and retained engine, History, queue, Random,
PWA and server checks. Fresh lint: **0 errors / 79 warnings**, 106.7 s. All 81 final checked inputs
match their archived hashes; reports are retained in ignored
`user/web-parity-verification/w07-saved-places-full-gate/`.
Actual browser appearance, touch, audio and background acceptance remain unchecked. No APK,
intermediate bundle, merge or deployment.

## W08 learned titles checkpoint — 2026-10-27

Reference: `HistoryPresentation.kt` (`LearnedTitles.display`/`sourceLine`), all three native rule
tests, `HistoryPresentationReads`/tests, `HistoryStoreListenedTest` and production Browse, Random,
Queue and controller call sites. Accepted C101 uses History as the sole retained title source.

The overlay applies before listened qualification. Display copies preserve original filename,
URL, metadata, order and occurrence identity; neither catalogue sort fields nor Random's heard
record is rewritten. Row text updates without rebuilding lists, moving scroll or altering the
playing context. Existing playlist labels and History recency metadata remain independent.
Source lines retain the filename once. Current display copies reach Add/enqueue/Info/share while
Play/Select/remove keep original references. The Queue adapter keeps existing node/request keys.
Stable file identity, rather than object-reference equality, preserves current-subsong audio
export when an action receives a displayed copy. No decoder cache scan is introduced.

Focused checks pass: five native-derived title rules, fifteen History storage/migration cases,
17 actual page/control checks and the existing queue presentation checks. Combined page checks
pass **865 assertions**. Six deliberate faults fail at named assertions: source mutation,
disconnected title refresh, copied selection identity, wrong export identity, delayed Clear and
wrong decoder filename. The forced full gate passes **1218 tests / 160 suites**, zero failures,
errors or skips, **838 s**; all 31 unit tasks executed. All companions pass, including **865 page
assertions**, five title rules, fifteen History storage cases and the retained engine, queue,
Random, bookmark, PWA and server checks. Fresh lint: **0 errors / 79 warnings**, 88.7 s. All 76
checked source inputs match their archived hashes; reports are retained in ignored
`user/web-parity-verification/w08-learned-titles-full-gate/`. Browser appearance, audio export
quality and background acceptance remain unchecked; worker request checks are host boundary
evidence. No APK, intermediate bundle, merge or deployment.

## Deferred or blocked work

No implementation point has been declared unresolvable. If one becomes blocked, record the
mechanism, attempts and independent work that can continue. Revisit every skipped item after the
remaining implementation; only then report the exact unresolved remainder. Browser capability
limits do not justify quietly changing a feature's contract or claiming completion.

## Delivery and verification

Record per-stage source SHA, Android reference cases, WEB tests, negative/regression guard,
full gate/lint results and manual-browser gaps here or in the corresponding feature contract.
At the end audit all branch commits and W01-W25 criteria, correct findings, rerun final checks,
and package the checked WEB bundle in dist with its SHA-256 and source identity. The final
browser checklist and pending device results remain separate from technical handoff.

## W09 — Recent searches, 2026-10-28

Android reference: **c8209b1ab79976ec28d27e1da447055756c7ca29**. Read production
`player/RecentSearches.kt`, `data/RecentSearchPreferences.kt`, `ui/SearchQueryField.kt` and
`BrowseWorkflow.setQuery/runSearch`; compare `RecentSearchesTest`, `RecentSearchPreferencesTest`
and `RecentSearchUiTest`. The first mutates only query state; explicit submission records a query
and runs the lookup. W09 therefore replaces WEB's typing-starts-search handler with the native
explicit Search/Enter action. Existing source-route checks now press the actual Search control,
retaining their original query/result/transport assertions.

Parity checklist and implementation:

- Retain twenty unique trimmed submitted strings, latest spelling first; blank submissions and
  unchanged state do not write. Prefix filtering ignores leading typed whitespace and case,
  preserves trailing whitespace/accents, and runs before the three-option limit. Kotlin whitespace
  and non-expanding Java-style case mappings avoid divergent BOM, Turkish-I and sharp-S results.
- `recent-searches.js` owns rules and serialized storage. The existing settings store gains one
  key without a schema bump. Publish only after a completed write; serialize rapid submissions,
  retain prior state on refusal, and retry an unavailable initial read before replacing unknown
  data. Clear playback History leaves this independent preference intact.
- `search-suggestions.js` binds the real field, integrated Search icon, plain text listbox and
  keyboard/pointer handlers. Arrow keys expose an active option; Enter submits it or the typed
  query; Escape/Tab dismiss. Submission closes and blurs before asynchronous work. Data/result
  refresh never focuses the field or reopens a submitted popup. Placement follows available
  viewport space and preserves scrolling when the keyboard limits room.
- Input edits retire obsolete reads without submitting or recording. A redraw may rerun only
  an acknowledged submitted query; it cannot silently submit new text. Selecting a suggestion
  uses the existing search scope/transport path rather than changing playback. W11 still owns
  adding the missing scope controls and wider library coverage.
- Errors reading/writing recent searches have localized feedback. Search remains usable after
  a preference-write refusal. PL/EN names, literal text rendering and accessible option state
  follow the native field convention; the integrated icon has an accessible label, while the
  suggestion list has no decorative icons or visible section heading.

`docs/rules/recent-search-cases.tsv` supplies **18 common cases** to production Kotlin and JS;
Gradle declares it as an input. The focused Kotlin run passes the shared test plus all eight
existing RecentSearches tests. JS passes **24 rule/storage-order cases**, **11 DOM event cases**,
**4 production IndexedDB cases**, and the complete page passes **883 assertions**. A wrong fixture
expectation fails both JS and Kotlin without forced Gradle execution; a disconnected actual
option click fails the DOM guard. Original inputs and callback were restored. The page harness
uses replacement callbacks when inlining the new modules so regex dollar characters remain
literal. Test fixtures open Search through its actual domain entry instead of focusing a hidden
panel. The privacy description now includes retained queries and catalogue bookmarks.

**Checked:** forced `./scripts/test-protracktor.sh --really` on source **5f239c9** passed
**1238 tests / 164 suites**, zero failures/errors/skips, **760 s**, and lint with zero errors.
All WEB companions ran, including the **883 page assertions**. All **598 recorded application,
test, rule and build input hashes** still match; the completion record changes documentation only.
Browser rendering, physical keyboard/touch and actual software-keyboard behaviour remain
unchecked. No WEB bundle, merge or deployment. Private full/focused/mutation evidence is under
ignored `user/web-parity-verification/w09/`.

## W10 — Live History search analysis, 2026-10-28

Current Android reference: **c8209b1ab79976ec28d27e1da447055756c7ca29**. Read
`HistoryFilter`, `HistoryReads`, `HistoryBrowser`, `data/HistoryStore`, `ui/HistoryQueryField`
and `HistoryDomain`; compare `HistoryFilterTest`, `HistoryBrowserTest`,
`HistoryStoreSearchTest` and `HistoryBrowseUiTest`. The accepted B49 contract is literal matching
across all History; C118's separate new-record refresh report is not an Android change here.

Parity checklist:

- Match a trimmed literal phrase separately in title, author or filename using the same Unicode
  ignore-case rule; preserve accents, punctuation and field boundaries. Recent-query matching
  and History share the narrow text-rule module, not persistence or UI state.
- Debounce edits by **200 ms**; clear visible results/counts and reset page zero without disposing
  or refocusing the field. Enter only dismisses the keyboard. History queries never enter recent
  general searches, alter playback, reorder History or replace a captured source list.
- Scan the newest-first cursor, retain at most the requested hundred plus the last partial page,
  count every match and clamp shrinking/enormous/negative pages. Blank queries use an indexed
  count and bounded cursor in one readonly transaction. No schema change or whole-History read.
- Give each read a generation and cancellation signal, including identical-input/new-visit
  races; stale rows/errors cannot replace another view. Current errors clear loading and offer
  a labelled retry. No-match differs from an empty History; Clear is available for a nonblank
  phrase even when it has no matches.
- Reads wait for the existing ordered writer. Clear retires the current candidate and queued
  read before erase, still erases after navigation, and reloads a cancelled filter after failure.
  Existing listened marks, metadata, recency, counts and unrelated stores survive filtering.
- A new History entry resets query/page/scroll; Queue-source return restores the producing
  phrase/page/membership rather than pending text or newer storage. Opening/closing Queue over
  History preserves its field/read ownership. Source navigation never changes the active plan.
- Keep EN/PL feedback, accessible field/results state and keyboard actions. Browser appearance,
  software keyboard and touch remain unverified by host checks.

Implementation and verification are in progress; W10 is not yet declared complete.

Focused evidence: **26 Android tests / 5 suites** pass (shared fixture plus the unchanged
HistoryFilter, HistoryBrowser, HistoryStoreSearch and actual HistoryBrowse UI tests), **136 s**.
JS passes **30 rule/ownership cases** and **6 production IndexedDB cases**. The integrated page
passes **912 assertions**, including **29 new real field/pager/source/Clear/Back cases**. The 10,000-row
fake-IndexedDB scan counts 5,000 matches in 129 ms on this host; this is not browser/phone latency.
Deliberately disconnecting the actual field fails the DOM guard; bypassing generation ownership
fails the identical-input late-read guard. A wrong common expectation fails both JS and the actual
Kotlin test without forcing Gradle, proving its declared fixture input. All mutations are restored.
The initial disconnected-field probe exposed an unbounded deferred await in the harness; the
bounded, fail-fast guard was added and the repeated probe completes normally with one failure.
Full forced gate/lint remain pending at this candidate checkpoint.

Final path audit found an existing Back handler consuming retained general-Search text before
leaving History. History now returns to Browse in one action without touching that independent
query. The actual Back regression fails with the original handler and passes after restoration;
the focused page loader is generated from the same production-loading harness. History also
names its domain immediately during loading. The first full-gate attempt on **3a20b78** was
intentionally interrupted after **159.2 s** to include this correction; it is not a passing gate.
A new clean candidate and full forced run follow. No Android production change or WEB delivery.

**Final forced gate checked:** clean source **e38f728** passes **1239 tests / 165 suites**,
zero failures/errors/skips, **755 s** overall. Unit tests actually execute (31 tasks executed,
444.9 s); all WEB companions run, including **912 page assertions**; lint completes with zero
errors in 84.3 s. All **640 recorded application/test/build/rule input hashes** still match.
This completion record changes documentation/commit message only. W10 and W03's History-query/
page/source integration are implemented; W03 still needs W11's Search-scope wiring. Browser
appearance, software keyboard, touch and physical background operation remain unchecked.
Full/focused/mutation logs, JUnit/lint XML and input receipt are retained under ignored
`user/web-parity-verification/w10/`. No WEB bundle, merge, push, deployment or APK.

## W11 — Search scope/library analysis, 2026-10-28

Android reference **c8209b1ab79976ec28d27e1da447055756c7ca29**: `SearchScope`, `LibrarySearch`,
`SearchResults`, `BrowseWorkflow`, `SearchScopePanel`, `SearchTerms`, `CatalogueStore.search`
and `LibraryStore.allTracks`; compare `LibrarySearchTest`, `SearchResultsTest`,
`SearchTermsTest`, `SearchUiTest`/scope UI tests and the shared `searchMatch` rule cases.

Parity checklist before implementation:

- Everywhere combines all saved browser playlists with indexed catalogues; Online excludes saved
  copies, and an empty catalogue selection means every held approved catalogue. Platform scope
  includes both supported stores and an empty machine selection means all. No local-folder,
  UnExoticA, live Mod Archive, AMP or proxy integration is added. The supported browser subset of
  native Local is labelled Saved playlists, never Local folders.
- Blank and one-character submissions are valid. AND folded words across native fields: catalogue
  title/author, saved title/filename. Keep accents/separator folding distinct from History's
  literal phrase rule. Use existing common cases and extend boundary cases where needed.
- Keep the native per-source cap **2000**, global catalogue title order, saved-copy precedence
  and stable URL deduplication. Browser playlists store separate copies where Android has one
  global track row: canonicalize duplicate saved IDs deterministically before the cap, preserving
  the latest saved metadata, then order by title. No mutation of stored playlists or drafts.
- Count catalogue matches coherently with the bounded sorted result selection; never silently
  truncate at WEB's former 300. Stream playable title shards rather than materializing all
  archive rows. Cancellation/stale results/errors must honor request and view ownership.
- Scope-kind changes clear stale rows; ticking within the same kind retains the old answer until
  Search. Choosing a scope never searches automatically. Back first widens a narrowed scope, then
  leaves Everywhere while retaining typed text and the existing answer.
- Capture the query/scope that produced rows independently of pending text/chips. Queue-source
  return restores those fields/results, preserving the transport plan and current resource state.
  Do not regress W09's producing-query fix to Android's stale-row snapshot limitation.
- Keep explicit storage failure/retry and missing-index explanations, EN/PL labels, plain recent
  suggestions in the current scope, mouse/keyboard actions and stable field focus. Missing local
  phone bytes remain unavailable with a reason; searching does not persist them.

Implementation and host/browser verification remain pending at this analysis checkpoint.

Implementation checkpoint: `LibrarySearch`/`SearchBrowser`, bounded sorted selection and coherent
production storage are wired to stable scope controls and the existing recent-query field.
All controls use actual click/input paths; current results carry their producing scope even when
text/chips change. Ordinary Back now follows native widening/exit semantics and cached Search
text cannot consume History/folder Back. The standalone scope check caught the platform tile
being disabled by a chip-only availability rule; the production rule now distinguishes tile/chip
keys. A controlled missing-scope probe exposed a mutable route read after synchronous model
publication; restoration now retains its local immutable DTO across that callback.

Reference comparison: **45 Kotlin tests / 5 suites** pass in **120 s**, including a new declared
URI-encoded fixture with **25 shared SearchTerms cases**, existing LibrarySearch/SearchResults/
SearchTerms tests and common rule cases. JS passes **42 domain cases**, **8 production IndexedDB
cases**, and **10 scope DOM cases**. Actual-page checks exercise explicit blank/short submissions,
all saved playlists, copies/absence, scope/chip actions, Back/reentry, producing source restore,
latest reads/errors/Retry, stable rows and in-session phone bytes. A wrong shared expectation
fails both JS and Kotlin without forcing Gradle. Disconnecting scope clicks, reversing copy
precedence, accepting old owners, omitting scope restoration, rebuilding pending rows and dropping
held bytes all fail their intended guards and are restored. The first missing-scope mutation
failed by a callback null dereference; after the local-DTO safeguard, its repeated probe fails
the intended scope-restore assertion. Full forced gate/lint remain pending; real browser UI,
keyboard/touch/headset behaviour remain unchecked. Private evidence is under ignored
`user/web-parity-verification/w11/`.

The integrated page passed **946 assertions** before the final unchanged-text optimization;
the current focused production-page loader passes its bootstrap plus **35 Search event checks**.
Pending edits preserve row nodes and unchanged live-region text, avoiding a 2000-row rebuild or
repeated identical announcements on every keystroke. Existing History cancellation/storage checks
still pass after sharing the transaction completion helper. The final candidate's forced gate
below must validate all page scenarios together; no bundle or browser acceptance is claimed.

**Final forced gate checked:** clean source **1182e66** passes **1240 tests / 166 suites**,
zero failures/errors/skips, **769 s** overall. Unit tests actually execute (31 tasks executed,
454.3 s); all WEB companions run, including **947 page assertions**, **42 library-search domain**,
**8 library storage** and **10 scope DOM cases**. Lint completes with zero errors in **83.3 s**.
All **650 recorded application/test/build/rule input hashes** still match; this completion
record changes documentation/commit message only. W11 and W03's Search-scope integration are
implemented. Browser appearance, software keyboard, touch and external/background playback
remain unchecked. Full/focused/mutation logs, JUnit/lint XML and input receipt are retained under
ignored `user/web-parity-verification/w11/`. No bundle, merge, push, deployment or additional APK.

### W12 preparation finding — live drag timing

Initial code comparison with `ManualQueueReorder`/`PlaylistScreen` found a W02 gap: native
updates the logical order on each full-row crossing, while the earlier WEB `PendingReorder`
committed only on release. Thus cancellation/mid-gesture playback semantics differ even though current host tests
passed. W12a below closes that gap with a shared queue/playlist gesture and native-derived
live-move/cancellation cases. Earlier implemented checkpoints
cover their recorded cases and do not establish this missing parity.

## W12a — Shared live drag and playlist reorder analysis, 2026-10-28

Android reference **c8209b1**: `PlaybackController.moveTrack`, `PlayerUiState.withEditorQueue`,
`PlayQueue.withTracks`, `PlaylistScreen.dragToReorder`, `ManualQueueReorder`, and production-shell
`PlaylistDragUiTest`/`ManualQueueReorderUiTest`. The preceding W02 timing gap is part of this shared
reorder substage. M3U8 transfer remains a separate W12b commit and gate.

Parity checklist before coding:

- A valid full-row crossing changes the logical order immediately, in both the pending-manual
  block and the editable playlist. Cancellation keeps already applied moves. Same/invalid moves
  are no-ops. Respect native residual-offset hysteresis and current/manual/source boundaries.
- Playlist moves mark a draft and wait for Save; Discard reloads that selected playlist. Keep
  current track, position, metadata, decoded buffer, manual FIFO and source occurrence IDs. An
  editor unrelated to the active source never changes its playback. Shuffle re-deals from the
  current track, preserving its actual history; ordinary Next follows the edited list.
- Existing WEB pasted lists can contain duplicate URLs, unlike native playlists. Use an explicit
  index permutation for legacy occurrences; do not collapse their current/history positions or
  source omission IDs through a URL-to-last-index map.
- Use one live gesture implementation for Queue's pending subset and playlist handles, with
  pointer/keyboard/menu paths, full-row steps, bounded auto-scroll and safe cancellation on owner/
  membership changes. Preserve stable handle nodes while dragging. Freeze their physical DOM
  order and preview via transforms while committing the model, then synchronize on completion;
  this avoids rebuilding the entire playlist renderer solely to add the feature.
- Keep numeric scroll fixed when the first row moves, draw the whole raised row over neighbours,
  retain capture/focus and swallow the generated click. Reorder only from the handle, not the
  vertically scrollable/swipeable row. No grips in protected From the phone or transient/Random
  views, or during multi-selection. Preserve existing gesture thresholds and action conventions.
- Tests must use actual pointer/key events, not only direct move methods, plus common native model
  cases and the existing shell scenarios. Native cancellation/live-move expectations replace
  older preview-on-release assertions; retain every boundary, click, multi-pointer and scroll guard.
  Browser touch/painting/capture behaviour remains unchecked until a real-browser trial.

Implementation is pending at this analysis checkpoint.


### W12a implementation / focused verification

One shared live `RowReorder` now serves pending Queue requests and playlist handles. The
`queue-reorder.js` compatibility export retains the previous module entry point. Physical row
nodes stay in place during pointer ownership; logical moves and dirty state update immediately.
Views synchronize on stop and restore viewport/focus without triggering decoder work. Leaving a
view cancels further movement. Source occurrence IDs and legacy repeated URLs use an explicit
positional permutation instead of ambiguous URL remapping. Protected/transient lists stay intact.
Keyboard arrows and labelled menu actions reach the same move as pointer gestures.

Focused reference tests pass **12 tests / 3 suites** (`PlaylistReorderParityTest`,
`PlaylistDragUiTest`, `ManualQueueReorderUiTest`) in **43 s**. Thirteen shared non-shuffle cases
cover current retention, Next/Previous, repeat, empty/single and invalid moves. WEB passes
**18 domain cases**, **13 real grip-event cases**, and **26 production-page cases** using synthetic
geometry. These are host checks; physical browser behaviour remains unchecked. Negative controls
for live commit, unrelated-source ownership and physical node retention fail at their guards and
are restored. Full forced gate/lint and final input receipt are pending at this checkpoint.

The first full run was deliberately interrupted during unit tests to add a CSS regression guard:
unavailable local references do not have the swipe wrapper that normally positions a row, so their
raised drag layer also needs `position: relative`. The host cascade guard fails with that missing
rule, then passes after restoration. This interrupted run is not full-gate evidence. The candidate
was corrected before restarting; the complete replacement gate is pending.


### W12a final gate

Clean candidate **53e437ade8c8ec13f789531022b8a5573aa8564c** passes
`./scripts/test-protracktor.sh --really`: **1241 tests / 167 suites**, zero failures/errors/skips,
**765 s** overall, unit task **444.9 s**, all 31 unit-build tasks executed. All companions ran,
including **973 page assertions**, actual WASM/processor/native audio, database upgrade, PWA and
server checks. Fresh lint finishes in **84.0 s**, **0 errors/fatal and 79 warnings**. All **656**
recorded application/test/build/rule input hashes match. Full/focused/negative logs and JUnit/lint
reports are retained in ignored `user/web-parity-verification/w12/`.

W02's live-drag parity gap and W12a are implemented at host level. W12b file transfer remains open.
This completion record changes documentation/commit message only. Physical browser painting,
touch, capture, software keyboard and background operation remain unchecked. No bundle, APK,
merge, push or deployment for this WEB stage.


## W12b — M3U8 transfer analysis, 2026-10-28

Android reference **c8209b1**, production `PlaylistTransfer`, `PlaylistFile`,
`LibraryStore.replaceTracks` and `LibraryIndexStore.findByFile`; read all `PlaylistFileTest` and
`PlaylistTransferTest` cases. W12a is verified/committed before starting this separate substage.

Parity checklist before coding:

- Export captures the selected draft when its playlist matches, otherwise persisted contents;
  retain the initial label across suspended reads. Do not Save, switch context or change dirty/
  position/FIFO. Empty export and read/download failure are distinct notices. Sanitize only the
  download filename; retain Unicode name/title/author and native M3U comment identities/sizes.
- Parse native extended M3U8 and ordinary location lists, unknown comments, ignored EXTINF duration,
  first artist separator, per-entry metadata reset, title/filename fallback, signed 64-bit size and
  embedded playlist name. Name fallback strips document path/prefix/last extension, then Imported.
- Import always creates a new playlist without selecting it. Nonempty but all-unresolved input
  still creates an empty list with 0/N notice. Count resolved offerings before first-occurrence
  deduplication, as native store does. Do not change legacy pasted-list duplicate rules.
- Resolve latest exact known id, supported internal archive id/remote id, remote location, then
  a held local filename with optional positive size. Browser has no scanner/SAF: local bytes must
  actually be held in this tab. Keep successful local matches transient for subsequent list
  loading; never put bytes into storage, M3U or share fragments. Missing bytes after restart are
  visibly unavailable. Native `asma://` ids map to existing canonical HTTPS archive URL rules;
  unsupported native-only schemes remain explicitly unresolved. Other individual HTTP(S) links
  stay supported; no new catalogue or AMP integration.
- Browser file picker/download adapt pure format/transfer rules. Preserve protected From the phone
  ownership, accessible icon/labels, EN/PL and errors. Cancelled picker does nothing; unreadable,
  no-entry, partial, storage and download outcomes are distinct. Fresh import ids cannot collide;
  delayed work never mutates the active editor/source or forces another view.
- Use common format cases in Kotlin/JS and port native transfer snapshot/resolution/duplicate/
  all-missing/failure cases. Drive real picker/menu events with IndexedDB and file/download hooks;
  assert ordering, playback/draft invariants and never-persisted held buffers. Demonstrate guards
  fail when broken, then restore and execute the forced full gate/lint. Real file-picker/download
  UI and phone-to-browser sharing remain unchecked until a browser trial.

Implementation and verification are pending at this checkpoint.


### W12b implementation and comparison findings

Pure format/transfer modules now drive actual Import/Export controls. Thirty shared cases cover
read, byte-for-byte write and native download filenames. Native comparison: **26 tests / 3 suites**
pass, **4 s** final focused (the earlier run took **13 s**). WEB currently passes **49 rule/transfer cases**, **37 production-page
scenarios** and **3 production IndexedDB cases**. All three fixture files were individually
mutated: both JS/Kotlin reject the wrong expectations, and Gradle reruns without forcing after a
clean restored baseline. Focused/negative logs remain under ignored
`user/web-parity-verification/w12b/`; the full forced gate and final input receipt are pending.

Transfers preserve the current editor, unsaved snapshot, playback position and FIFO. Successful
imports create fresh atomic identities, deduplicate only new membership and count resolved offered
entries; all-unresolved input creates an empty list with 0/N. Local resolution retains only
actually held tab buffers; they never enter IDB, playlist files or share payload. Selecting,
discarding and startup rehydrate from the same runtime owners used by Search. Missing/detached
bytes do not count as playable. Derived availability is tracked separately from saved flags,
including through immutable copies: the first page run exposed automatic saving of those derived
fields over the phone list, so that regression is covered without weakening its existing guard.

A real request-level constraint error exposed `transaction.error == null` before abort. The shared
transaction helper now takes the request's error when needed; collision tests fail before this
fix and pass after. Creation uses `add`, so a collision cannot overwrite an existing playlist.
Failed view refresh after a committed import reports success plus its separate refresh error.
Malformed/unreadable/no-entry inputs, read/storage/download failures and picker cancellation have
distinct outcomes; late completion does not take over another editor or view.

Intentional compatibility details:
- Signed Long size metadata uses canonical decimal strings where JS Number would lose precision.
- Native ASMA identities become canonical browser HTTPS archive URLs through the existing encoder;
  source presentation uses the browser's author-folder path. Unsupported native-only references
  are unresolved, and only actual HTTP(S) network schemes are accepted.
- Metadata newlines are flattened to prevent line injection; ordinary native write cases match
  byte for byte. This browser hardening does not alter Android.
- Native writes the artist separator even for an empty author, but its reader accepts only a
  separator after a nonempty artist. A new shared read case records native and corrected WEB
  results separately. The WEB reader accepts a separator at position zero; titles containing
  another separator round trip without a fabricated author or prefix. The writer keeps native
  wire compatibility. Android's reader defect is recorded, not modified in this WEB task.

Physical file-picker/download, touch, background and cross-device behaviour remain unchecked.
No schema/version change, native Android behaviour change, bundle, APK, merge or deployment.


### W12b final gate

Clean candidate **2bf2834ed263c6c584339ee4d011ce2d25299ca7** passes
`./scripts/test-protracktor.sh --really`: **1244 tests / 168 suites**, zero failures/errors/skips,
**774 s** overall, unit task **454.1 s**, all 31 unit-build tasks executed. All companions run,
including **1010 page assertions**, **49 transfer rules**, **3 creation-storage cases**, actual
WASM/processor/native audio, database upgrade, PWA and server checks. Fresh lint: **84.0 s**,
**0 errors/fatal / 79 warnings**. All **665** recorded application/test/build/rule hashes match.
Seven final negative controls cover entry metadata reset, export ownership, reference-only writes,
derived availability, atomic creation, real request errors and actual input ownership. All fail
at their expected guards and are restored. The empty-artist guard also fails before correction.
All three shared fixture files individually trigger uncached Kotlin execution without forcing.

W12a and W12b are implemented at host level. W13 is next. This completion record changes only
documentation/message; Android production sources and native engine are unchanged from accepted
develop. Actual picker/download, browser touch, background and cross-device acceptance remain
unchecked. No merge, bundle, APK, push, deployment or versionName change. Evidence and source/input
receipt are retained under ignored `user/web-parity-verification/w12b/`.


## W13 — Bulk Browse selection analysis, 2026-10-28

Reference **c8209b1**: read `BrowseScreen` TracksView/BrowseTrackRow, `AddToPlaylistDialog`,
`HistoryBrowseUiTest`, `BrowseState`, and current browser row/chooser/share presenters. W12b is
fully verified before beginning this separate scope.

Parity checklist:
- Long press begins id-based selection; taps/checkboxes toggle without playing. Select all/none
  works on the displayed eligible list. Keep source order when capturing batch actions, not
  selection order; retain selections only while their IDs belong to the same producing view.
- Reserve the checkbox/header space before entering selection. Hide contextual row actions and
  disable swipe while ticking; do not scroll-follow playback. Keep physical row nodes/focus and
  protect the generated long-press click. Keyboard/context-menu paths reach the same rules.
- First Back/Escape exits selection before navigation; subsequent Back follows the existing view
  stack. Pending Search text/chip edits keep its producing snapshot. New submissions/history
  phrases/pages and actual navigation reconcile/retire the old selection. Late results cannot
  select another list. Queue overlays preserve the underlying Browse visit; a true leave clears it.
- Batch Add to current edits the selected browser-owned draft without Save; other Add captures
  references before clearing/opening the existing chooser. Cancel does not resurrect selection;
  creating/selecting destinations retains existing dedup/save rules. Protected From the phone
  offers the chooser instead of direct append. Main-list selection stays a separate owner.
- Batch sharing is native Send to Web / browser Share with Protracktor, preserving reference order
  and current eligibility/refusal policy. No new relay, bulk audio renderer or manual-FIFO feature.
- Preserve existing browser capability boundaries: selectable rows are playable `.btrack` rows;
  folder/action rows and unavailable phone-only placeholders remain explanatory. Native local
  scanning/ownership is outside WEB. Bulk uses source references as native does; learned titles
  remain presentation data. Do not persist buffers or alter playback/FIFO from selection alone.
- Compare actual native BrowseScreen long presses, checkboxes, actions and Back with equivalent
  browser pointer/key/button/chooser/share cases, including geometry, membership and async races.
  Test the pure selection rules too, then demonstrate broken event/ownership/order guards fail.
  Finish with a forced full gate/lint and input receipt. Browser touch/visual acceptance stays open.

Implementation and verification are pending.

### W13 implementation checkpoint

Native `BrowseSelectionUiTest` exercises actual long presses, row/checkbox clicks, bottom actions,
reserved leading/header geometry, membership/query changes and system Back: **3 tests pass**,
**13 s** focused. WEB domain/presentation checks pass **6 / 7 cases**; actual-page checks cover
Browse, Search, History, Queue overlays, protected phone destinations and local-byte ownership.
Selection does not own playback; bulk capture follows source order, with no learned-title rewrite.

The implementation also closes defects encountered through these actions: working drafts must
keep local buffers until reference serialization, owned-list Save/Add/Save As must retain those
buffers across editor switches, destination reads/writes need visit ownership and retryable
snapshots, and focused buttons must not also invoke global Space transport. Two actual-page guards
fail before the stale-read/error and keyboard corrections, then pass after them. Existing Random
learned-title selection coverage now explicitly opens its main list before pressing Select; it
previously drove a hidden main action while Browse was visible. Its title/raw-identity guard is
unchanged. Native production behaviour remains unchanged.

Full forced checks, negative-control receipt and final source identity are pending. Physical
browser appearance, touch, file UI and background operation remain unchecked.

### W13 final gate

Clean candidate **679608413db5186d364fd50f9759a48f73026de3** passes the forced complete gate:
**1247 tests / 169 suites**, zero failures/errors/skips, **794 s**, unit task **459.0 s** with
all 31 tasks executed. All companions actually execute, including **1052 page assertions**,
**6 selection-model** and **7 presentation** cases, actual WASM/processor/native audio, database
upgrades, PWA and server checks. Fresh lint **83.6 s**, **0 errors/fatal / 79 warnings**.
All **671** recorded application/test/build/rule hashes match. The native production behaviour
and engine remain unchanged from accepted develop.

Six controlled regressions fail at their intended guards: source order, owner retirement,
checkbox bubbling, actual long-press binding, held working buffers and retained byte ownership
once other live owners are replaced. All are restored. The first byte mutation was recovered by
normal hydration and therefore was not a broken implementation; stripping the resulting working
buffer and replacing the other runtime owners supplies the meaningful negative controls. Two
actual-page guards also fail before the focused-button Space and obsolete chooser refusal fixes.
Evidence, JUnit/lint and input receipt are under ignored `user/web-parity-verification/w13/`.

W01-W13 are implemented at host level. Continue W16-W18/W24 in the planned dependency order.
This completion update changes documentation/message only. Actual browser layout/touch/file UI,
background and cross-device behaviour remain unchecked; no WEB bundle, APK, merge or deployment.

## W16 — Compact dock analysis, 2026-10-28

Reference **c8209b1**: `PlayerDock`, `NowPlayingText`, `NowPlaying`, seven
`DockIdentityLayoutTest` cases and existing browser card/subsong/marquee paths. W13 is fully
verified and committed before beginning this scope. Follow the suggested presentation-first
order; audio W14/W15 remains pending, with -18 LUFS retained for W15.

Parity checklist:
- Independent upper title/Add and lower metadata/full Tune N of M rows. Reserve Add's slot even
  when hidden, with the counter ending at the same right edge. Neither counter length (1/5/256)
  nor Add visibility changes the title width or Queue placement. Queue stays outside the card.
- Retain the browser's existing ordinary 64 px card, without another top spacer. Let the card
  and footer grow/wrap for large text/narrow layouts rather than overlap metadata/counter.
  Android's own ordinary card is 60 dp; browser dimensions remain an explicit presentation
  adaptation. Existing author/platform/year logic and reduced-motion marquee remain shared.
- The counter reflects the actual opened decoder in both subsong modes, including live changes;
  loading/error/empty transitions cannot leave an old file's count. Describing another row must
  not overwrite the sounding file's counter. Keep progress/status outside this identity policy.
- Add, card expansion and Queue are separate actions. Footer tap expands without saving; keyboard
  Enter/Space has the same ownership and must not invoke global audio shortcuts. Preserve
  transport, playback position, editor dirty state and manual FIFO merely by opening either panel.
- Port the native identity/action/count cases through actual browser events and computed CSS
  structure. Demonstrate missing/broken counter, reserved-slot and keyboard guards fail, then run
  the full forced gate/lint and input receipt. Host CSS evidence is not physical layout or touch
  acceptance; no screenshot is claimed without inspecting a generated artifact.

Implementation and verification are pending.

### W16 implementation checkpoint

Independent header/footer markup reserves Add's 48 by 32 px slot, removes the decorative chevron
and keeps Queue outside. The browser's ordinary minimum remains 64 px, without a fixed outer
height or extra top spacer; large text can wrap/grow. Existing title/marquee and metadata logic
are reused. Actual decoder state drives the localized full counter; loading/failure/single-tune
transitions hide stale counts. Card keyboard activation and expanded state are explicit.

The **24** focused production-page cases pass. Three deliberate faults are detected: a collapsed
reserved slot, a missing keyboard event and an inspected file overwriting the live counter. The
last negative control initially exposed a test setup omission: its decoder answer lacked success,
so it never rendered the inspected metadata. It now requires both the successful inspected title
and the unchanged live counter; the controlled fault fails and is restored. Native's seven
`DockIdentityLayoutTest` and two `NowPlayingSharedTextUiTest` cases are the comparison reference;
their fresh execution is included in the pending complete gate. No Android production changes.

Full forced gate/lint and final input receipt are pending; physical browser geometry, touch,
zoom/large text and assistive technology remain unchecked. No generated screenshot is claimed.

### W16 final gate

Clean candidate **37715ec8f8913a0bdce389b61a04d39352e1fde6** passes the forced full gate:
**1247 tests / 169 suites**, zero failures/errors/skips, **805 s**, unit task **466.5 s** with
all 31 tasks executed. All companions run, including **1076 page assertions**, actual WASM,
processor/native audio, migrations, PWA and server checks. Fresh lint **86.2 s**, **0 errors/fatal /
79 warnings**. All **672** recorded application/test/build/rule hashes match. Native's seven
identity-layout and two shared-text UI cases pass again in this run; Android production and the
engine are unchanged. Evidence, JUnit/lint, negative controls and input receipt are retained under
ignored `user/web-parity-verification/w16/`.

W16 is implemented at host level. This completion changes documentation/message only. Browser
layout, zoom, touch, reader behaviour and background acceptance remain unchecked. Continue W17a
Browse restoration and W17b subsong following separately; no WEB merge, bundle or deployment.

## W17a — Browse position analysis, 2026-10-28

Reference **c8209b1**: `BrowseScroll`, its eight tests, `ListScrolling`, BrowseScreen's
RestorePosition/group/catalogue/History paths and ProtracktorApp's view/overlay lifetimes.
W16 is verified and committed before this separate restoration substage; subsong following
remains W17b.

Parity checklist:
- Keep positions per producing Browse level for one session. Before entering a catalogue/group/
  author or saved place, remember the exact row identity. Back restores that row after real rows
  arrive, even when its index changed. A genuinely missing row consumes its marker and uses its
  own saved offset; never jump to an old numeric index. Loading/empty rows wait without consuming.
- Snapshot before DOM replacement, including overlapping asynchronous renders. An obsolete
  result cannot restore a newer view; an intermediate loading/empty list must not overwrite a
  saved offset with its clamped zero. Same-level refresh preserves position and input focus.
- Fresh Browse starts at the top; Queue and temporary destination choosers retain the underlying
  visit. Fresh History and changes of its phrase start at the top. History pages keep separate
  offsets within a query. Search positions belong to producing query/scope, not pending edits.
- Use stable URL/folder/catalogue/domain/bookmark identities, independent of translated labels.
  Pointer and keyboard folder activation reach the same departure recording. Preserve existing
  source-return/Random/digression navigation, playback, selection-first Back and manual FIFO.
- Port native restore decisions and drive actual nested Browse/Back, changed/missing index,
  delayed reads, fresh reentry, History paging/query and Queue cases. Demonstrate broken identity,
  generation and event guards fail, restore and run the full gate/lint. Physical browser scroll
  feel and touch remain unchecked; no scroll rendering claim comes from jsdom geometry hooks.

Implementation and verification are pending.

### W17a implementation checkpoint

The pure model ports native pending-return and per-level lifetime rules; the DOM adapter owns
capture-before-replacement and latest-visible-visit restoration. Stable folder/catalogue/domain/
URL/bookmark identities survive translated labels or index changes. Fresh Browse and History
phrases reset, History pages keep independent offsets, and producing Search rows retain their
position during pending edits. Folder keys use actual raw names, including an empty author.
Pointer and keyboard activation use the same departure recorder.

Temporary Add destination choosers now preserve their underlying Browse models rather than
implicitly starting History anew. An actual Saved places test exposed restoration against stale
membership: the first render consumed the bookmark marker before its refresh started. The guard
fails before changing refresh-before-restore ordering and passes afterwards. No Native production
change or new persistence. Browser restoration uses a direct target alignment instead of native
Compose's distance-dependent scroll animation; physical feel remains unchecked.

Focused WEB **6 rule / 7 presentation / 22 production-page cases** pass. Four deliberately broken
implementations are detected: identity lookup, visit ownership, intermediate-snapshot capture and
the actual folder activation recorder. Sources are restored. These cases port `BrowseScrollTest`
and exercise the equivalent real browser events; its eight native tests run again in the pending
full gate. Evidence is under ignored `user/web-parity-verification/w17/`. Full checks/lint and
final receipt are pending; W17b subsong following remains separate and unimplemented.

The first combined page run exposed a test setup dependency: the Search position case inherited
Saved playlists from the prior bulk-local fixture, so its catalogue rows were correctly absent.
It now chooses Everywhere through the actual scope control before asserting catalogue position;
production Search semantics and existing guards are unchanged. This test-only correction is made
before the forced gate reaches its WEB phase; all Android/native production and test inputs stay
identical to the candidate on which its fresh unit phase started.

### W17a final gate

The forced full gate passes **1247 tests / 169 suites**, zero failures/errors/skips, **813 s**;
fresh unit execution **461.4 s**, all 31 tasks executed. All companions actually execute,
including **1098 page assertions**, **6 Browse model / 7 presentation cases**, actual WASM,
processor/native audio, migrations, PWA and server checks. Fresh lint **84.5 s**, **0 errors/fatal /
79 warnings**. Native's eight `BrowseScrollTest` cases pass again; Android production is unchanged.
All **677** recorded final application/test/build/rule hashes match.

Source receipt is phase-specific: unit execution started at **834836dd2609ed62107346235c360f54223f8e53**.
Before the WEB phase, **7f1b1d375b9f0b2c9ff213a9045fdf972b771f1e** corrected only its new page test's
inherited Search-scope setup. Android/native production/test sources remain identical; WEB checks
execute the corrected test. The original and corrected receipts, logs, JUnit/lint and four
negative controls are retained under ignored `user/web-parity-verification/w17/`. Both independent
and complete-page runs pass after that test correction.

W17a is implemented at host level; W17b remains pending. This completion changes documentation/
message only. Real-browser scroll/animation, touch, focus and background behaviour remain unchecked.
No WEB merge, bundle, APK, push, deployment, schema or versionName change.

## W17b — Subsong strip analysis, 2026-10-28

Reference **c8209b1**: `SubsongStrip`, `ListScrolling`, `NowPlaying` and ProtracktorApp's conditional
Now Playing sheet. W17a is verified before this substage. Existing `QueueFollowUiTest` covers
vertical Queue following, not this strip; add a narrow actual-component native comparison.

Parity checklist:
- Show one horizontal row of 36 px numeric chips with a 6 px gap for counts above one. Opening a
  file/count or freshly opening Now Playing reveals the actual current chip. Same-key/current
  updates retain chip nodes and scroll/focus. Both play-all modes allow explicit chip selection.
- On later advances, follow only if the previous chip was at least partly visible, and do nothing
  when the new chip is already visible. Leave a deliberately distant region in place. A new file
  or count resets this decision; closing/reopening the native sheet is a fresh opening too.
- Keep visibility work owned by the current strip/file/count. Hidden panels defer measurement and
  cannot fake a visible previous chip; obsolete deferred work must not move a new strip. Preserve
  the current file's other metadata and in-page transport. Inspecting another file never starts it.
- Actual chip pointer/keyboard events send one subsong selection, without a global Play shortcut.
  Keep the existing numeric-chip icon exception, PL/EN descriptions and reduced-motion policy;
  no native haptic port or additional mode is included.
- Run equivalent actual `SubsongStrip` component cases in Robolectric and WEB geometry/event
  cases. Demonstrate broken watch/visibility/ownership/event guards fail, restore, compare back to
  native and run full checks/lint. Host visibility geometry is partial evidence; real browser
  scrolling, touch and layout remain unchecked.

Implementation and verification are pending.

W17b reference findings before implementation: four actual native `SubsongStrip` cases pass in
**24 s** focused, including initial/current visibility, distant-region retention, file/count reset
and a real chip click with play-all Off. `PlaybackController.selectSubsong` also refuses the
already-current index. Native `TrackInfoDialog` has no playable subsong strip. The existing browser
Information panel currently draws active inspected-file chips wired to the sounding decoder, and
a selected chip can redundantly restart its tune. Correct these within the strip adapter: keep
Information read-only, restore actual live content on a fresh dock expansion and ignore duplicate/
obsolete selections. Live subsong metadata must update its existing dock snapshot so reopening
cannot resurrect the preceding tune's title. System metadata refresh remains W25.

### W17b implementation checkpoint

The keyed strip retains numeric chip nodes and focus, uses native opening/previous-visible rules,
and owns deferred measurements by visit/file/count. A fresh panel opening follows current even
for the same file. New loads retire old controls immediately; selected, hidden and detached chips
cannot send an obsolete selection. Information stays read-only, late description replies cannot
replace a fresh live panel, and reentry uses current subsong metadata rather than the preceding
snapshot. System metadata remains W25. Browser alignment is direct rather than native animated
scrolling, avoiding an old animation competing with a later selection or manual movement.

Focused WEB **15 rule / 6 presentation / 21 production-page assertions** pass. The complete
standalone page suite passes too. All **five** actual native component comparisons pass after
adding the watched-region advance case. Six deliberate WEB faults fail at their expected guards;
forcing native watching both permanently On and permanently Off breaks the corresponding actual
component tests. Both restored native runs pass; native production bytes are unchanged. Receipts
and pre-fix event failures are under ignored `user/web-parity-verification/w17b/`.

Full forced checks/lint and their final source/input receipt remain pending. These are host
checks, not physical-browser layout, touch, scrolling or assistive-technology acceptance.

### W17b final gate

Clean candidate **7d75d8793f31f072e56aef4e604ff6cb6417d870** passes the forced complete gate:
**1252 tests / 170 suites**, zero failures/errors/skips, **825 s**. Unit execution **474.7 s**,
all 31 tasks executed; all five native strip comparisons pass again. All companions run, including
**1119 page assertions**, actual WASM and processor/native audio, migrations, PWA and server.
Fresh lint **86.5 s**, **0 errors/fatal / 79 warnings**. All **683** recorded application/test/
build/rule hashes match. Full logs, JUnit/lint, six WEB and two native negative controls and source
receipt are retained under ignored `user/web-parity-verification/w17b/`.

W17a and W17b are implemented at host level. This completion changes documentation/message only;
native production, engine, schema and versionName are unchanged. Real-browser layout, scrolling,
touch and assistive technology remain unchecked. Continue W18 restoration and W24 live language
before the remaining storage, downloads and DSP scopes. No WEB merge, package, push or deployment.

## W18 — Playback settings restoration analysis, 2026-10-28

Reference **c8209b1**: `PlaylistSessionState`, `PlaylistSession`, `LibraryStore` player-state reads,
`PlayQueue`, SettingsScreen and the corresponding restore/save tests in `PlaylistSessionTest`.
W17 is verified and committed before this scope. Defaults are shuffle Off, repeat Off and play-all
subsongs Off. The restored selected track is paused, not a request for a decoder or audio output.
Settings writes sample the actual transport; an independently visible editor is not playback.

Parity checklist:
- Read browser playback preferences synchronously before constructing editor and context orders.
  Preserve the existing play-all key; add shuffle/repeat keys without a schema migration. Missing
  or malformed values use safe defaults, including unknown repeat -> Off. Startup does not write
  defaults. A refused write keeps the in-memory choice and gives visible feedback.
- Render restored shuffle state and repeat Off/All/One glyph/accessibility from actual state, not
  a simulated button click. Mode changes are not playlist content edits and preserve manual FIFO,
  current track, runtime bytes and playback. Save only the changed preference.
- Add the native global play-all choice and supporting explanation to Settings. The checkbox and
  existing Now Playing switch share one setter, one preference and one state presentation.
  Both stay synchronized; Settings remains available for a single-tune file or before playback.
- Restore the saved playlist/current selection without autoplay or initialization writes. A later
  user playlist choice wins over delayed initialization; a full-list URL handoff wins over stored
  selection while remaining paused. Keep existing phone-byte and unavailable-reference policies.
- Exercise actual startup with preloaded production storage, each mode/default/invalid state,
  stored position, slow reads and explicit handoff. Drive real controls and verify persistence,
  error feedback, dirty state and queue/decoder continuity. Compare native restore/save cases,
  prove guards fail, restore and run full gate/lint. Physical browser acceptance is unchecked.

Pre-fix reproduction: a fresh page with a saved two-track playlist fails to select either that
playlist or its saved second track. The startup restore references `asked` without declaring it;
the unrelated AAC capability IIFE holds the only nearby capture. The outer catch mislabels this
ReferenceError as refused browser playlist storage. Both actual startup guards fail before any
repair. Startup `setQueue` also schedules playlist writes, contrary to the native no-write restore
contract. Capture restoration ownership locally and allow this publication path to suppress its
initialization writes. Evidence is retained under ignored `user/web-parity-verification/w18/`.

Implementation, post-fix checks and full gate are pending.

### W18 implementation checkpoint

Synchronous reads initialize both editor and context orders. Three independent browser keys retain
shuffle/repeat and the existing play-all choice; startup writes no defaults. Invalid values and
refused/partial reads are nonfatal. Explicit actions persist only their changed key; write refusal
is visible and does not revert the in-memory mode. Both subsong controls share one setter/render
path. Repeat names, glyph and pressed state come from actual state, including restored One.

The startup restore now owns its generation, publishes a saved selection paused without writes,
and refuses both stale data and stale failure feedback. The saved-playlist ReferenceError and
late-error feedback have actual failing pre-fix tests. Ten fresh-page scenarios cover defaults,
empty selection, malformed values, One/All, legacy subsongs, denied preference reads, an actually
captured old read followed by a newer choice, its late failure and explicit full-list handoff.
Blocking before reading the active ID would not exercise stale publication; the comparison uses
the held old playlist result. The empty-selection case independently protects initial context modes.

Focused checks pass: **10 preference cases**, **10 fresh-page scenarios / 194 bootstrap assertions**
(including their common page checks), and **16 actual mode-control assertions**. The complete page
suite also passes **1135 assertions** before the final stale-error guard and target-style additions;
those additions pass focused checks and remain included in the pending full gate. Six deliberately
broken implementations fail: initial context modes, initialization writes, stale result ownership,
actual Shuffle persistence, Settings event wiring and unknown repeat decoding. Both production
files are restored and all fresh-page/control/rule checks pass again. Native production is unchanged;
its 52 `PlaylistSessionTest` cases, including restore/save barriers and actual transport sampling,
passed in W17b and will run again in the W18 full gate.

Logs, pre-fix failures and negative controls are retained under ignored
`user/web-parity-verification/w18/`. Physical browser reload/touch/reader/storage behaviour remains
unchecked. Full forced gate/lint and final source receipt are pending. No schema/version change,
WEB merge, bundle, push or deployment.

### W18 final gate

Clean candidate **709fb71307bfd949c51207351b49a3e21cff7724** passes the forced complete gate:
**1252 tests / 170 suites**, zero failures/errors/skips, **917 s**; unit **473.5 s**, all 31
tasks executed. Its 52 native session comparisons pass again. All companions run, including the
ten fresh-page scenarios (**194 bootstrap assertions**, **92.4 s**), ten preference cases,
**1135 complete-page assertions**, actual WASM, processor/native audio, migrations, PWA and server.
Fresh lint **86.1 s**, **0 errors/fatal / 79 warnings**. All **688** recorded input hashes match.
Full logs, JUnit/lint, pre-fix failures, six controlled regressions and source receipt are retained
under ignored `user/web-parity-verification/w18/`.

W18 is implemented at host level. This completion changes documentation/message only; native
production, engine, schema and versionName are unchanged. Physical browser behaviour remains
unchecked. Continue W24 live language, then retained cache/downloads and shared DSP scopes.
No WEB merge, package, APK, push or deployment.

## W24 — Language without interrupting playback analysis, 2026-10-28

Reference **c8209b1**: `AppLanguage`, `AppLocale`, all four `AppLanguageTest` cases, MainActivity's
attach/onCreate/language callback and ProtracktorApp's saved destinations. Native language selection
rebuilds resources while retaining the application-scoped controller; C25 prevents replaying the
old external intent. Missing/obsolete choices follow System, explicit en/pl survive storage and
selecting the same policy is a no-op. Editable playlist names remain user data in their creation
language. W18 is verified and committed before this scope.

Parity checklist:
- Change en/pl/System in place, retain the selected policy separately from its resolved language,
  follow system-language changes only in System, and persist without page reload. A refused save
  explains the failure while the session's choice remains usable. Preserve the theme and panel.
- Keep AudioContext, decoder, elapsed/listening state, current file/subsong, playback context,
  manual occurrence IDs, playlist draft/selection and runtime bytes. Never replay a handoff intent,
  simulate transport clicks, submit a Search or refetch music merely to change text.
- Refresh static and dynamic labels, accessible attributes, menus, source headers, counters,
  metadata field names, statuses and visible Undo/error text. Keep raw titles/authors/filenames/
  editable playlist names and engine/network reasons intact, including strings identical to UI
  translation keys. Preserve focused controls, query values/ranges, rows and scroll ownership.
- Use canonical message keys with snapshots of action arguments, not reverse translation or
  callbacks over a later mutable playlist. Weak bindings belong to actual text nodes so replacing
  them with raw user data retires their UI meaning. Bound attribute ownership must be explicit.
  No global DOM prototype hooks, inferred translations or new external dependency.
- Exercise actual radio and System events with live audio, menus, inspected/live metadata, source
  routes, loading/error outcomes and asynchronous completions. Detect requested reloads explicitly:
  jsdom does not actually navigate, so unchanged fake audio references alone would miss one.
  Port native language cases, demonstrate broken semantic/ownership/continuity guards fail,
  restore and run full gate/lint. Real-browser playback/rendering/background acceptance stays open.

Implementation now uses explicit semantic messages and weak presentation bindings. Local editing analysis uses the parser already
embedded in Node, with no installed or shipped parser dependency; it is not a runtime feature.

### W24 module-integration finding

A new bootstrap probe loads the real ESM graph in the same jsdom context, using the existing
network/audio/storage stubs rather than inlining dependencies into the app's scope. It fails
before the fix: `catalogue` is referenced by application subscriptions but is not imported from
store.js. The earlier inlined page configuration supplied this name globally and could not detect
the missing production import. The import is now explicit. Source/negative logs are under ignored
`user/web-parity-verification/w24/`; post-fix module startup and full ESM interaction checks pass; the full project gate/lint are
pending. Earlier stage records remain evidence of their stated host paths, not real-module or
physical-browser acceptance. The VM module API is an existing Node test capability; no production
parser, dependency or runtime flag is shipped with WEB.

### W24 implementation checkpoint

Sixteen message/ownership cases and 46 actual-page language assertions pass through real ESM.
Visible Search selection/query/suggestions, Saved places explanations and Undo, Queue grips/menus,
source acknowledgements and policy prose update without replaying their actions. Raw names that
match canonical UI keys remain literal. The audio context, current file bytes, plan, editor,
duplicate request IDs and paused state are retained. Five seconds of qualifying playback can span
a language change. System policy and refused saves follow the native language contract.

Eleven controlled faults are rejected and restored: mutable message/composition arguments, recapturing
user rows, keeping a stale text owner, the missing production import, a requested browser reload,
a flattened Undo message, accepting a late author-store result after Back, an unbound Storage
label and restoring an old-language destination caption after a failed write. Earlier before-fix startup and mutable-argument failures are retained.
The integration gate now evaluates production modules instead of relying on flattened scopes; the
Browse stale-read fixture holds its actual store read rather than replacing a module export.
Full forced gate/lint are pending. Physical browser appearance/audio/keyboard/background behaviour
are unchecked. No Android production, schema, dependency, version, merge or deployment change.

The first forced gate on `895d112` was interrupted after 141 seconds of unit execution, before
completion, when review found the remaining Storage-label and failed-destination-caption writes.
It is not a passing gate. Both paths now have real-control checks and detected negative controls;
the final candidate requires a fresh complete run.

Final preflight also passes ten actual-module fresh starts (**204 bootstrap assertions**), all
**1182 page assertions**, and the affected ownership/presentation companions. The first complete
attempt on `0859fd4` passed **1252 native tests / 170 suites**, 31 tasks executed, 470 seconds, then
stopped at the denied-preference startup fixture. That fixture inspected snackbar text after its
visible lifetime; W24 now clears retired text, exposing the weak old assertion. It observes the
actual visible notice during bootstrap instead. Replacing the production snackbar with a status
message fails this stronger guard; restored code passes. This is not a passing full project gate.
A fresh forced gate and lint are required on the final test-corrected candidate.

### W24 completed host verification — 2026-10-28

Tested candidate **7d321acdce2f749a2284a1926f2e651394ddd944** passes the forced full gate:
**1252 tests / 170 suites**, zero failures/errors/skips, **922 seconds**. All 31 native tasks
execute (467.3 seconds); all companions run, including real-WASM/audio, migrations, ten actual-ESM
fresh starts (**204 bootstrap assertions**), **16 UI ownership cases**, **1182 page assertions**,
PWA and 11 server cases. Lint passes in **85.5 seconds**, **0 errors / 79 warnings**. All **694**
tracked app/test/build/rule input hashes match the recorded candidate. Evidence is kept locally
under `user/web-parity-verification/w24/`, including the earlier interrupted/failing attempts.

The 46 live-language page cases preserve playback/paused state, file buffers, independent editor
and source, duplicate requests, query/selection/focus, menus, Undo and held bilingual prose.
Eleven controlled faults are rejected and restored. Native comparison remains c8209b1; Android
production is unchanged. Real browser rendering, sound, touch, OS/background and accessibility
behaviour remain unchecked. W24 is implemented at host level; W01-W13/W16-W18/W24 total 17 of 25
scopes. Continue W20 retained music, then the remaining download and DSP stages. No WEB merge,
version change, package, push or deployment is part of this checkpoint.

## W20 — Retained music and availability analysis, 2026-10-28

Reference **c8209b1**: net/CacheBudget, RemoteFileCache, RemoteFiles/SharedFetches,
player/OnPhone and BrowseCache; full CacheBudgetTest, RemoteFileCacheTest, OnPhoneTest and
relevant BrowseCache cases. Native uses a 512 MiB completed-file budget, LRU(time,name),
SHA-256 URL filenames, atomic publication and in-use protection. Protected bytes still count.
Its fetched-cache clear leaves archives/user documents/audio alone and needs no confirmation.
Shared transfers survive individual caller cancellation, while each UI/playback intent stays guarded.

Parity checklist:
- Publish complete positive Blobs atomically with eviction/accounting; use actual payload size,
  current/read-ahead protection sampled under the transaction, and native digest tie-breaks.
  Keep index/History/bookmark/playlist/PWA storage and future permanent ASMA outside this budget.
- Reserve browser schema 6 for remoteMusic and asmaArchives; create no data or network downloads
  in migration. Preserve v3/v4 listening seeding, v5 records, blocked upgrade/retry and future-schema
  refusal without reset. Quota/request/eviction failure rolls back the whole attempted publication.
- One byte resolver supplies playback, Information, file/audio sharing and Random. Live handoff
  buffers are copied and reused without silent persistence, including transferred HTTP references.
  Shared fetched bytes cannot be detached by another waiter; stale results never activate playback.
- Browse/Search/History alone gain native-style available-copy glyphs above played-before, with
  checkbox ownership and row/focus/scroll retained. Native playlist/Random/Queue rows have no cache
  input; do not extend those views speculatively. Index/metadata presence is not an offline copy.
- Storage reports actual retained bytes and the 512 MiB limit, clear/retry/failure accurately.
  Clear keeps playing buffers/context/FIFO/drafts/documents and retires pre-clear publications.
  An old failed cleanup must not permanently poison later playback.
- Compare shared native/WEB budget cases, actual IDB rollback/migrations, production fetch/cancel/
  transfer paths and actual page gestures/actions. Demonstrate broken guards, restore and run
  full gate/lint. Real-browser offline/quota/rendering/sound remain unchecked until observed.

At the initial analysis checkpoint, integration and verification were still pending. Ignored
draft probes were preparation only and did not establish production or browser behaviour.
W20 does not enable a full archive.

### W20 implementation and preflight checkpoint

The production schema is now 6, with independent fetched-copy and reserved archive stores. The
coordinator supplies playback/Random/file actions through one byte resolver; runtime handoff bytes
bypass retention. It shares transfers, bounds header/body stalls, copies each consumer's buffer,
tracks truthful positive payloads and retires pre-clear writes. Storage exposes sizes, immediate
Clear and visible failures/Retry. Browse's reserved native-style leading slot updates without
rebuilding rows or replacing selection. No permanent archive is downloaded yet.

Preflight passes **13 shared budget cases + 7 freed-quantity cases** in WEB and two actual native
CacheBudgetParityTest methods; removing a shared row fails both and reruns native tests via the
registered input. Production checks pass **13 coordinator**, **8 store**, **3 migration/refusal**,
**5 real-stream/deadline** and **9 presentation** cases. Actual-page checks cover **26 cache cases**;
the current full ESM preflight passes **1212 assertions**, including the pre-existing application
regressions. Eleven cold starts pass **226 assertions**, including retained-byte playback from a
fresh app/module state without network or startup autoplay.

Ten controlled faults are rejected and restored: protected eviction, aliased transferred buffers,
late publication, non-remote persistence, metadata-only presence, non-atomic rollback, retired
write tickets, an old protected snapshot, overwritten checkboxes and persisted phone bodies.
Additional before-fix failures detect discarded quota state, non-remote retention and Blob
construction interrupting otherwise successful playback. The original startup document-write
guard remains active while permitting native-style cache-only budget maintenance.

The legacy page fixture used a never-resolving transfer tied to the old UI AbortSignal. Native
SharedFetches keeps a transfer independent of a waiting view; the fixture now releases that shared
response explicitly and checks that completion caches it without reopening a stopped occurrence.
Each transfer has its own signal, preserving the Queue Clear download guard. Object-URL cleanup
was missing from jsdom's platform fixture; it is supplied, and uncaught UI exceptions now fail the
page gate. No production fallback is added for the host limitation.

At preflight, the forced full gate/lint were pending; their completed result is recorded below.
Native production is unchanged; Android test inputs and comparisons are extended. Real-browser
storage, rendering, sound and background acceptance remain unchecked. No merge, package, version
change, push or deployment is authorized by this stage.

### W20 completed host verification — 2026-10-28

Tested source **2d9faae93300b7e7b2695ebfe0c7125d28e068ea** passes the forced complete project gate:
**1254 tests / 171 suites**, zero failures/errors/skips, **955 s** overall. All 31 native unit
tasks executed (476.6 s), every configured companion passed, including **1212 actual-ESM page
assertions**, eleven cold starts (226 assertions), native audio, WASM, migrations, PWA and eleven
server checks. Lint completed in **88.0 s**, **0 errors / 79 warnings**. All **708** source/test/
build-input hashes match the candidate. Logs, JUnit results and receipt were archived locally
before further test runs; a documentation-only amend leaves those tested inputs unchanged.

The shared fixture and controlled-fault evidence above demonstrate the new guards, including
positive byte presence, cancellation/publication ownership and atomic storage rollback. Native
production remains unchanged. Browser offline/quota behaviour, real rendering, sound, touch and
background operation remain unchecked. W20 is implemented at host level: **18 of 25 scopes**
(W01-W13, W16-W18, W20, W24). Continue W21 full ASMA, W23 recovery, W19/W22, W14/W15 and W25,
then the final requirements/commit audit and WEB package. No WEB merge, version change, push or
deployment has occurred.

## W21 — Complete offline ASMA, 2026-10-28

Reference **c8209b1ab79976ec28d27e1da447055756c7ca29**: net/Catalogue.Asma, RemoteFiles archive
access, LibraryDownloads and LibraryMaintenance, StorageSection, CatalogueTest,
LibraryDownloadsTest's archive-before-index/refused-save cases and archive-storage tests. Native
retains the compressed ZIP independently from fetched music, reads members by their exact paths
and deletes an archive catalogue's index and ZIP together. Cache Clear leaves it and playing
buffers alone. Existing WEB uses encoded HTTPS file IDs; retain those rather than rewriting
playlists/History into Android's internal scheme.

Parity checklist:
- Only the explicit ASMA download fetches the complete archive (currently about 20 MB); startup
  and schema migration never fetch it. An existing index remains browsable without claiming bytes.
- Retain the compressed immutable Blob and validated member descriptors in schema 6's reserved
  asmaArchives store. Verify members before publishing archive and derived index in one transaction.
  Cancellation, corrupt input, quota failure and invalid records preserve the previous installation.
- Use a persisted revision ticket in that transaction: an older download cannot restore data
  after another tab deletes or replaces it. Roll back the revision on failure too. No extra schema.
- Resolve held handoff buffers first, then prepared/archived/fetched copies through the same
  playback/Information/export route. Each archive read returns fresh verified bytes; no duplicate
  archive members enter the 512 MiB fetched-music LRU. Read current storage when resolving a member.
- Decorate only actual member presence, preserve selection/played-before ownership, expose archive
  size/status and one confirmed index+archive delete, and keep current playback/FIFO/editor/user
  documents intact. Preserve English/Polish language switching without restarting audio.
- Exercise native/shared cases, corruption, revision/rollback, cancellation, old indexes, real UI
  download/play/delete, cold startup and legacy/native import identities before the full gate.

Implementation uses zip-archive.js, OfflineAsma, AsmaView, the existing bounded downloader and the existing
reserved store. The ZIP reader supports single-disk stored/deflated members and data descriptors,
checks local/central bounds, names, overlap, size and CRC, and rejects encrypted/ZIP64/ambiguous
entries. Browser resource bounds are 64 MiB per expanded member and 512 MiB per expanded archive.
Raw DEFLATE support is required for compressed archive members; refusal is reported and preserves
old data. Existing individual HTTPS file playback remains the fallback when no readable archive
member is available. These are browser capability/resource limits, not new servers or dependencies.
Format references: [PKWARE ZIP specification](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT)
and [DecompressionStream constructor](https://developer.mozilla.org/en-US/docs/Web/API/DecompressionStream/DecompressionStream).

The actual existing import event had a separate identity defect: a native asma://asma/… reference
was passed to an HTTP root that already ended in /asma/, duplicating the root. W12's shortened
fixture hid this. The actual FileReader before-fix guard fails; conversion now accepts the native
root and retains compatibility with the shortened form. Ordinary browser addresses stay unchanged.

The shared six-case fixture includes native SAP/text download cases, authorless games, apostrophe/
plus, nested author paths and URL encoding. Native Asma.parseIndex and WEB consume it.
Native ZipInputStream reports unknown local-header sizes as zero for descriptor ZIPs; the current
published archive has known local sizes. The native fixture models those known headers, and WEB
also verifies descriptor members using their central-directory sizes. Native production is unchanged.

### W21 preflight and regression checkpoint

The production ZIP reader verifies all **6458 files** of the measured **20,136,268-byte** public
archive, and three buffers exactly match Python ZipFile (Quest_For_Cool, Robots and SimCity_2000).
This is host parsing/network evidence; CORS and audible browser playback remain unchecked.
Production checks pass sixteen archive/storage/coordinator cases, seven transfer/deadline cases,
shared native parsing and focused actual-page download/play/delete cases. Five deliberate faults
(checksum, stale publication, missing rollback, unavailable offline glyph and stale presentation) are detected and restored.
Removing a shared row fails WEB and native tests and reruns the Gradle task via its declared input;
restoration passes. Before-fix app guards also demonstrate missing offline bytes and the actual
native import defect. A new before-fix check caught corruption notices/availability being reset
by metadata refresh; known damaged members now remain quarantined for that archive revision.

Initial full-page preflight found two fixture issues: the Settings label still expected index-only
ASMA, and the new playback test left a saved ASMA document before a later blank platform search.
The label assertion follows the new joint storage item; the new stateful playback scenario now
runs after the existing Search/playlist checks. Their assertions remain intact. Final integrated
preflight passes **1240 actual-ESM page assertions**, with no uncaught UI exceptions, and **twelve
fresh starts / 248 assertions**, including retained ASMA playback without network/autoplay or
archive startup writes. The forced full project gate/lint are pending. W21 is not counted complete yet.
Real browser rendering, quota/persistence, CORS, touch, sound and background operation remain
unchecked. No merge, package, push, deployment, version change or APK is authorized by this stage.

### W21 live-presentation correction before the full gate

A further actual-page guard reproduced a stale claim after another connection deleted ASMA:
focus refreshed the byte snapshot to empty but left the visible archive caption and controls
unchanged. The initial forced gate on **26cbdc3** was deliberately stopped during native tests
(exit 143 after 357.1 s); it is an interrupted run, not a passing gate. No source edits were made
until its supervisor had stopped and cleaned up its own processes.

AsmaView now owns archive/index presentation for both catalogue and storage rows. It refreshes
existing captions, marks, labels, icons, action availability and folder-opening state from the
latest data without replacing focused controls. An old index read cannot restore facts after a
newer snapshot. Eight additional actual-page cases cover cross-connection removal/reinstallation,
focus, unavailable Delete with its reason, live controls, stale reads and playback continuity.
The new stale-presentation guard also detects an intentionally removed generation/snapshot check.

The optional legacy test adapter also exposed duplicate private helper names when flattening
modules. Its source adapter now isolates those names; production ESM module scopes are unchanged.
The legacy full-page path passes, and the final actual-ESM preflight passes **1240 assertions**;
twelve cold starts pass **248 assertions** with no new archive startup writes or network.
The replacement forced full gate/lint are pending; W21 remains uncounted until they pass.

### W21 copied-bootstrap dependency correction

The next forced run on **1fbe9ef** passed **1255 native tests / 172 suites** in 479.8 s,
twelve cold starts and the configured checks through the cache/History companions. It then
stopped at the older Random-startup fixture: that test copies the common bootstrap into a
temporary directory and resolved two historical imports, but not the new ZIP fixture import.
The failing module path is a test-adapter dependency error, not an application Random failure.
This attempt is archived as a failed full gate; lint had not run.

The copier now resolves static relative imports against the real scripts directory. All four
original persisted-scope/delayed-choice assertions pass unchanged. The remaining companion
checks, including actual WASM/audio, migration, icons, PWA and server checks, also pass. The
complete forced gate will be repeated. No application source changed
for this correction; the source/verification record will still identify the exact tested commit.


### W21 completed host gate

Tested source **57a189fb96e981f8e95afbba5f9da79428d362da** passes the forced complete project gate:
**1255 tests / 172 suites**, zero failures/errors/skips, **984 s** overall. All **31** native unit
tasks executed (488.5 s); every configured companion passed, including **1240 actual-ESM page
assertions**, **twelve fresh starts / 248 assertions**, actual WASM/audio, migrations, PWA and
eleven server checks. Lint completed in **86.5 s**, **0 errors / 79 warnings**. All **716**
source/test/build-input hashes match the candidate. Logs, JUnit XML, lint report and receipt were
archived before another test could replace them. The final amendment changes documentation only.

The architecture record now also corrects its obsolete claim that ASMA has no individual HTTP
file URLs; current native Catalogue already supplies them. Existing public identities and the
full offline archive are complementary. Native production remains unchanged. Browser CORS,
quota/persistence, rendering, touch, sound and background behaviour remain unchecked.

W21 is implemented at host level: **19 of 25 scopes** (W01-W13, W16-W18, W20-W21, W24).
Continue W23 recovery, W19/W22, W14/W15 and W25, followed by the final requirements/commit audit
and packaged WEB trial. No WEB merge, version change, push or deployment has occurred.


## W23 — Legacy Modland requests, 2026-10-28

Reference **c8209b1ab79976ec28d27e1da447055756c7ca29**: Modland.legacyUnknownAuthorUrl/pathSegments,
RemoteFiles.fetch/download/downloadAt/attempt, all four original ModlandLegacyPathsTest methods
and all ten RemoteFilesModlandTest methods. Native recovers a moved unknown-author directory
only after the original HTTP 404, then keeps the original playlist/cache identity.

Parity checklist:
- Only exact HTTPS Modland module-root URLs qualify. Decode per segment while preserving literal
  plus; exclude the format root/filename, queries, fragments, encoded separators, lookalikes and
  ambiguous multiple unknown directories. Nested format/game directories and exact encoding survive.
- An original 200 is final. Only 404 may try one canonical replacement; access/server errors and
  connection failures do not retry. A refused replacement reports its own failure, without a loop.
- Discard refused bodies. Give each request its own 45-second connect/header allowance and retain
  the 30-second body-inactivity bound, operation cancellation and independent shared waiters.
- Publish complete positive bytes under the original ID only. Reopen offline, shared callers,
  transfer ownership and Clear during fallback must retain W20's protections. Never rewrite
  playlists, History, source order or metadata IDs to the recovered address.
- Use the same resolver for real playlist playback and file/Information/export access; no private
  playback-only retry, new server, dependency, archive crawl or browser credential-policy change.

The narrow catalogue-urls.js extraction preserves the existing native-compatible path encoder
byte for byte and avoids a catalogue/downloader import cycle. downloadMusic owns the request-only
recovery; RemoteMusic still owns the original key, shared transfer and disk publication ticket.
The legacy test adapter resolves these modules explicitly; production ESM scopes stay separate.

A before-fix actual playlist click reported HTTP404, requested only the old URL and never opened
its decoder. New production checks cover the four reported YM/SPC/AdLib/VGZ files, retained IDs,
exact transferred bytes, History and offline replay. **23 shared URL cases** are consumed by
native and WEB; malformed UTF-8 is additionally declined by WEB rather than copying Java's
replacement-character decoding. Browser code does not set forbidden Cookie/User-Agent headers
or reproduce native gate-cookie retries; normal browser CORS/credentials policy remains in force.
This capability boundary does not trigger a proxy or a misleading retry on a transport refusal.

Preflight passes **13 production recovery cases**, the existing **seven transfer/deadline cases**,
**15 native tests / two suites**, **1256 actual-ESM page assertions** and the focused legacy
adapter path. Four deliberately broken guards are detected and restored: ambiguous paths,
non404 recovery, changed cache identity and the real row action without recovery. Removing a shared row also fails WEB and the native fixture guard (five tests run before the
configured fail-fast stop). Restoring it reruns and passes all fifteen native tests. Both native
commands match the positive preflight and use no rerun flag: the declared fixture input, rather
than a changed filter, invalidates the test task. The integrated full project gate/lint remain
pending; W23 is not yet counted complete. Real browser CORS,
rendering, offline persistence and audible playback remain unchecked.


### W23 completed host gate — 2026-10-29

Tested source **7b1743e6537889d8a2b53fc43144c2316def6df6** passes the forced complete project gate:
**1256 tests / 172 suites**, zero failures/errors/skips, **960 s** overall. All **31** native unit
tasks executed (473.9 s); every configured companion passed, including **1256 actual-ESM page
assertions**, twelve fresh starts / 248 assertions, thirteen production recovery cases, actual
WASM/audio, migrations, PWA and eleven server checks. Lint: **0 errors / 79 warnings**, **85.7 s**.
All **720** source/test/build-input hashes match. Full logs, JUnit, lint and receipt are archived;
the final amendment changes documentation only. Native production remains unchanged.

W23 is implemented at host level: **20 of 25 scopes** (W01-W13, W16-W18, W20-W21, W23-W24).
Browser CORS, offline permanence, rendering and audible playback remain unchecked. Continue W19,
W22, W14/W15 and W25, then the final requirements/commit audit and WEB package. No WEB merge,
version change, push or deployment has occurred.

## W19 — Shared playback read-ahead and folder policy, 2026-10-29

Reference **c8209b1**: PlaybackReadAhead, FolderPrefetch, BrowseCache and their existing tests;
PlaybackController's candidate selection, load, queue/mode changes and Browse lifetime; Settings
CacheAhead controls. Android playback code and database versions are unchanged; the shared
privacy source now describes retained music and speculative browser downloads in both languages.

The common owner replaces Random's separate byte promises. Upcoming candidates are the first
three distinct files from pending manual FIFO followed by the source's prepared successors
(one for a finite source, three for Random). Completed buffers are consume-on-open, limited to
4 MiB per file and 8 MiB total. Replacement cancels speculative waiters and rejects late results;
it does not abort a foreground caller sharing the transfer. Current/wanted files protect the
persistent LRU. Explicit queue edits, real opens and mode changes refresh prediction without
changing History, source selection, startup or inactive views.

A before-fix reproduction found that independently predicting and then advancing past a shuffled
Repeat All lap could deal different permutations. One prepared lap now belongs to the immutable
PlayOrder; metadata-only copies preserve it, while real order/mode changes invalidate it. This
preserves one shuffled traversal per actual lap rather than copying native's incidental random
number consumption on independent peeks.

Only a loaded Modland author's folder qualifies for bulk preparation: cached files are free,
planning stops at the first file exceeding the 100 MiB folder budget, and three workers start in
row order. Leaving cancels unstarted work; already shared transfers may finish into the cache.
The current folder, policy and generation own publication, including synchronous observer
reentrancy. Progress uses the existing leading slot without replacing selection, focus or rows.

The retained Wi-Fi only / Always / Off policy gates folders, independently of chosen-playback
read-ahead. The [Network Information specification](https://wicg.github.io/netinfo/) exposes
bearer/performance information, not Android's unmetered-network authority. Consequently the WEB
default pauses folder downloads with a visible explanation; Always explicitly enables them.
This is a documented capability fallback, not an inference from wifi/effectiveType/saveData.
Settings restore without startup writes; refused storage remains visible with a usable runtime
choice. No new server, dependency, schema, endpoint or user-data upload is introduced.

Focused evidence: **12 read-ahead cases, 15 folder/policy/lifetime cases, 70 playback-context
cases**, **24 native tests / two suites**, **1287 actual-ESM page assertions**, and **15 fresh
starts / 339 assertions**. Eight folder planning rows are shared with production Kotlin through
a declared Gradle input. Removing a row fails both readers; restoring it reruns the same native
command without a rerun flag. Seven controlled faults are detected and restored, including the
actual opened hook, unknown-network boundary and late hidden-folder publication. Observer and
shuffle-preview regressions fail before their fixes. Focused legacy page integration also passes.
At this preflight checkpoint the forced complete gate/lint were pending. Browser rendering,
touch, real network/CORS, quota, audible gaps and background behaviour remain unchecked.


### W19 completed host gate — 2026-10-29

Tested source **bc9796487fe79d4952a164aa9b04f339af599bf3** passes the forced complete project gate:
**1257 tests / 172 suites**, zero failures/errors/skips, **994 s** overall. All **31** native unit
tasks executed (477.7 s); all configured companions passed, including **1287 actual-ESM page
assertions**, **fifteen fresh starts / 339 assertions**, the new read-ahead/folder checks, actual
WASM/audio, migrations, PWA and eleven server checks. Lint: **0 errors / 79 warnings**, **84.6 s**.
All **728** source/test/build-input hashes match, including the shared privacy source. Full logs,
JUnit, lint and receipt were archived before further test work. The final amendment changes only
documentation; Android playback code, WASM, schema versions and release version remain unchanged.

W19 is implemented at host level: **21 of 25 scopes** (W01-W13, W16-W21, W23-W24). Continue W22,
W14/W15 and W25, then the requirements/commit/exclusion audit, any worthwhile fixes and the final
WEB package. No scope has been skipped. Browser CORS, quota, rendering, touch, sound and background
acceptance remain unchecked. No WEB merge, push, deployment or additional APK has occurred.

## W22 — Selected library downloads, 2026-10-29

Reference **c8209b1**: DownloadPlan, DownloadSizes, LibraryDownloads and DownloadPicker; all eight
original DownloadPlanTest, thirteen LibraryDownloadsTest and two DownloadSizesTest cases, plus
the shared privacy parser's five tests. Android playback code and database versions are unchanged.

Parity checklist:

- Offer Modland, ASMA and Song metadata; initially select only missing choices. Modland includes
  its favourites; metadata means HVSC durations and songdb credits. Excluded songdb/UADE lengths,
  replay routines and other catalogues are not offered or fetched.
- Keep the fixed native substep order, independent of checkbox order. The approximate WEB costs
  are 6 + 1 + 20 + 5 + 15 MiB, **47 MiB** together. Credits-only metadata deliberately excludes
  the separate duration table included in Android's larger estimate.
- Freeze selection during the run. Preserve checkbox/button identity, focus, the fixed progress
  slot, reserved status line and primary-button width across progress and Download/Stop changes.
  An empty selection is disabled with an explanation. Close, icon taps and Escape dismiss
  presentation, return to the originating view and restore its opener's focus without stopping work.
  Tab/Shift-Tab stay within the actual dialog, including its Close control.
- Stop cancels the selected operation and prevents later starts. Committed datasets survive;
  independent failures do not erase successes or prevent other chosen substeps. Retry only failed
  or pending substeps, without fetching their successful siblings again.
- Individual and grouped actions share step implementations and locks. Observe an already-busy
  independent action's eventual result without taking its cancellation ownership. Late results
  cannot describe a newer run, and Stop does not cancel another action.
- Replace a complete dataset and its counts in one guarded transaction. Quota, cancellation,
  parsing failure and a stale snapshot leave the old dataset and unrelated user data intact.
- Preserve current playback/source/FIFO/editor/audio context. Downloads do not open a decoder,
  record a play or silently start at initialization. Keep PL/EN and accessible labelled controls.

LibraryDownloads owns the plan, immutable progress and operation lifetimes. DownloadPickerView
owns stable controls; dataset-download supplies common cancellable setup waits and guarded atomic
publication. Existing catalogue readers and the bounded transfer path remain the data owners.
The actual empty-playlist offer opens missing choices; Online catalogues exposes the same picker.
Completion closes its presentation and gives one summary; reopening exposes unfinished Retry.

Before-fix host reproductions confirmed that metadata quota failure could erase the old dataset
and cancellation during parsing was ignored. Atomic replacement now protects index, favourites,
HVSC and credits. A real-button race also showed a finishing local compatibility rebuild could
invalidate a fresh index download; explicit installation now waits for that shared predecessor
before capturing its publication guard. Cancelling the waiter leaves shared compatibility work
alive. Overlay delegation now recognizes the Close button's SVG descendants; the actual icon
gesture failed before that correction. Android's existing absent stopped-notice test documents a
known native defect; WEB reports the required stopped/partial outcome rather than copying it.

### Browser source boundary

Android's DeepSID file has no CORS header on an actual Origin-bearing GET. The project's
[existing agreement](PLAN_CATALOGUES.md) retains HVSC's website copy
for WEB until a browser-compatible replacement exists. The old hvsc.c64.org alias currently
fails hostname validation; no TLS exception is introduced. The official
[www.hvsc.de file](https://www.hvsc.de/download/C64Music/DOCUMENTS/Songlengths.md5) returns HTTP200
and `Access-Control-Allow-Origin: *` for the normal GET, and supports ranged GET too. Its
**5,205,150 bytes** have SHA-256
`3f2bb5c14e3396fb20979cb017421386dc9e32162999974ad1e4f0a230dc397e`, exactly matching Android's
DeepSID copy. The production reader parses and atomically retains **61,157** entries from that
actual payload. Browser/native source addresses therefore differ while the dataset is identical.
The privacy source discloses the canonical WEB host in both languages. No relay, new service,
credential, archive crawl or maintainer contact is introduced. Headers are host evidence of
server capability, not proof of a real browser session.

Focused host evidence: **14 model cases**, **26 production dataset/waiter cases**, **17 component
DOM assertions** and **40 actual application gesture assertions** pass. Nine shared plan rows
run against production Kotlin; removing a row fails WEB and native (14 tests before fail-fast),
then restoration passes **29 native tests / four suites** with the same command and no rerun
flag. Fifteen deliberately broken planning, locking, Stop/Retry, stale publication, atomicity and
actual Download/Stop/dismiss/Close/focus/compatibility behaviours are detected and restored.
The old ASMA refusal guard now observes the visible common snackbar; all original playback,
draft and cross-tab continuity assertions remain. These focused checks precede the completed
project gate recorded below. Real browser rendering, touch, quota, CORS, audio
and background acceptance remain unchecked.


### W22 integrated preflight

The final actual-ESM page preflight passes **1327 assertions**, including every existing ASMA
continuity guard and the new origin/focus/keyboard scenarios. The focused legacy adapter passes
**40 gesture assertions** without a second locale state. Model/data/component checks pass
**14 / 26 / 17**, and all **fifteen** controlled source faults are detected with exact restoration.
A separate fresh-start preflight passed fifteen scenarios / 339 assertions. The subsequent forced
gate repeated startup against the frozen final source; its result is recorded below. Real browser
appearance, touch, quota, audible continuity and background lifetime remain unchecked.


### W22 complete project gate

Tested source **9546ba5df57d66d1b72f8c3e378146cf32d15090**, forced complete project gate:
**1258 tests / 172 native suites**, zero failures/errors/skips, **995 s**; all **31 unit tasks
executed**. Every companion ran, including **1327 actual-ESM page assertions**, fifteen fresh
starts / **339 assertions**, model/data/component **14 / 26 / 17**, real WASM/export checks,
archive/storage migrations, PWA and eleven local-server cases. Lint: **0 errors / 79 warnings**,
108.7 s. All **736 source/test/build input hashes and the temporary verification configuration**
match. Logs, JUnit, lint and identity receipts are retained locally.

The preceding normal-JIT attempt failed after host JVM SIGSEGV, with four interrupted cases;
that failed run is archived separately. The successful repeat used the same JDK17 and complete
test selection with temporary test-worker `-XX:TieredStopAtLevel=1`. Lint followed its ordinary
project path; no production or shared-environment configuration was changed.

W22 completes host verification for **22 of 25 scopes**: W01-W13 and W16-W24. Continue W14,
W15 and W25, then the final requirements/commit/exclusion audit and WEB package. No skipped
scope, merge, publication, APK or version change. Physical browser rendering, touch, quota,
CORS execution, audible continuity and background behaviour remain unchecked.


## W14 — Shared playback stereo mix, 2026-10-29

Reference **c8209b1**: StereoMix, Appearance, controller selection/asynchronous open, NativeEngine
and the common Player callback; the original three mode, four preference and three Compose Settings
tests. Existing native callback tests define matrices, first-frame application, pause, EOF,
unknown widths, granted-rate smoothing and retargeting. The new shared ten-row fixture adds one
native case and is declared as a Gradle input.

Parity checklist:

- Stable original/narrow/mono identifiers map to 100/50/0 percent. Original is the absent/unknown
  default; reading never rewrites a future value or writes startup defaults. Preserve unrelated
  settings, playlists and schema. Refused reads/writes give visible feedback and safe live state.
- Three labelled/icon Settings choices expose selected state and English/Polish descriptions.
  Selection is global and live; it preserves pause, clock/subsong, source/FIFO/editor and audio
  instance. A later successful action can persist after refusal; selecting the same mode is inert.
- Reuse the exact native StereoMixer after decoding. Each WASM handle owns its target and ramp;
  apply the selected mode before its first PCM, retain it across seek/rewind/subsong, and initialize
  each new file directly at target. Use actual render rate and actual decoded frames. Empty or
  invalid calls do not mutate output/decoder state. Original is a bit-preserving bypass.
- The worklet retains the latest mode even without an open file or during compilation. Constructor
  options read the current choice after asynchronous loading; ordered later messages update the
  existing decoder. A paused change does not start audio. No backend-specific or JS DSP fork.
- Export/description handles remain Original independently of live settings. No new stereo mode,
  crossfeed, normalization or target slider is introduced in this scope.

The production build now exports `pt_set_stereo_width` and uses the shared native header. The
worklet owns the live target; the pure preference module owns retained identity/defaults. Page
controls only publish that target. The standard build produces WASM SHA-256
`e3353252860f7573592ab09052d88d33ce4988f8ba7f7eaa6848c70a1276a778`, byte-identical to the isolated
candidate used before integration. Twelve Original PCM streams and two raw export PCM streams
match the previous engine exactly; exports leave the live decoder's position intact.

Integrated preflight passes **21 mode/persistence cases**, **46 actual-WASM PCM cases**,
**6 real-worklet/WASM cases**, **23 actual page gestures**, **1350 complete page assertions**,
**23 legacy adapter assertions** and **20 fresh starts / 485 assertions**. Native reference passes
**11 tests / three suites**. Removing the last shared row fails WEB and native (eight tests /
one failure before fail-fast); restoration with the same command, without rerun flags, executes
and passes eleven tests. Seven production UI/worklet/model faults and two compiled native
no-mixer/wrong-rate variants are detected; source and positive artifact hashes are restored or
unchanged. The browser autoplay precondition in the isolated gesture helper was corrected by
an actual body click and running-context assertion; production transport was not changed.

A full-gate attempt passed all **1259 native tests / 172 suites** and the earlier companions,
then exposed a fixed-20 ms assumption in the existing C121 Information filename guard. A controlled
read delay reproduced its premature assertion; the eventual production request retained the
correct filename. The guard now waits for the actual request and retains every filename assertion.
Five real guard cases pass with delayed reads; deliberately sending display titles still fails
all five. Application source is exactly restored after that control. The failed gate is archived
separately and is not a complete pass; lint had not run. The complete retry below includes the
corrected guard.

The forced complete retry on **e0a1f797e779c1cda65f4e3ce8fa3cd99a1675c5** passes **1259 tests /
172 suites**, zero failures/errors/skips, **1156 s**, all **31 native unit tasks executed** and
all companions. The actual-ESM page contributes **1350 assertions**; twenty fresh starts contribute
**485 assertions**. Lint passes with **0 errors / 79 warnings** in **150.9 s**. All **743
source/test/build inputs plus one temporary test-worker configuration** match frozen hashes;
logs, JUnit, lint and the receipt are archived. The same existing JDK17 uses process-scoped
`JDK_JAVA_OPTIONS=-XX:TieredStopAtLevel=1` and the earlier test-worker init after archived C2 JVM
crashes, without changing the full test selection, production or shared settings.

W14 closes host verification for **23/25 scopes** (W01-W14 and W16-W24). Continue W15, W25 and
the final requirements/commit/exclusion audit and WEB package. Browser appearance, touch, sound
quality, encoder, routing and background acceptance remain unchecked. No version/schema change,
APK, merge or publication.

## W15 — Shared playback normalization, implementation checkpoint, 2026-10-29

Reference **c8209b1**: LoudnessProfile/Profiles/Preparation, controller read-ahead and
prepare/remember/refresh paths, the physical Settings switch tests, NativeEngine/Player control
and callback paths, and shared loudness/analysis headers. The accepted -18 LUFS target supersedes
the initial plan's -23 LUFS. Native production DSP, Android preferences and schema are unchanged.

Parity checklist and implementation:

- Global opt-in Off/On, default Off, fixed -18 LUFS, English/Polish and visible persistence
  refusals. No startup writes/autoplay, target slider, crossfeed or changes to system volume.
- Use the exact shared meter, normalizer and prefix helper. Usable input requires finite
  seconds >=0.4, LUFS [-70,30] and positive finite peak. Preserve +6 dB partial / +12 dB complete
  boost caps, -36 dB attenuation floor, linked -2 dBFS sample-peak ceiling, conservative 0.5
  fallback and live probe/retry behaviour. The gain slew is 100 ms per unit gain; larger changes
  may take longer. Mixing precedes normalization; no second JS audio algorithm.
- A separate decoder in one cancellable module worker uses the native cooperative 200 ms /
  twenty-second prefix limits, with a separate five-second browser worker safety deadline.
  Worker/load/cache/hash failures fall back safely. Only selected playback/read-ahead prepares
  measurements; catalogue and folder caches do not scan audio. Off releases the analysis worker.
- All opens use decoded -> prepared -> opened, including Off. Measure the actual selected
  subsong rather than assuming zero; defer first PCM and History/listening publication until
  adoption. Reject stale request/index/width/configuration results on both transport sides;
  preserve pause during preparation and live changes. Caller/cache bytes are never detached.
- Best usable facts survive restarts in a CRC-checked 512-record cache. Atomic settings-store
  updates preserve concurrent tabs' better/independent measurements. Content identity includes
  length-prefixed main-file bytes/name, actual WASM revision, subsong and stereo width. Complete
  beats partial, otherwise longer wins; inferior writes do not promote records. History/music
  Clear preserves profiles; site-data removal clears them. No schema migration or companion
  decoding is introduced.
- Seek/restart retain established trim and input metering but invalidate completeness. A new
  subsong resets the old trim; live stereo changes reset measurement, preserving audible gain
  continuity. Only uninterrupted natural EOF can publish whole-song listening. Old measurements
  keep their own source identity after selection changes; progress snapshots do not rewrite
  disk continually. Fresh description/export handles remain Original/Off.

The normal project build produces WASM SHA-256
`b711abb3141a62db046e8e7123989a8713a1c235df2c94f2c2469c42770cbaff`, identical to the isolated
candidate. Focused public checks pass **35 profile/identity**, **11 preference**, **11 storage**,
**25 preparation**, **15 playback-ownership**, **6 real-worker**, **8 real-worklet** and **36
native-WASM PCM/policy cases**. Sixteen PCM cases compare exact decoded input with a standalone
native-header executable at 32/44.1/48/96 kHz (PCM tolerance 5e-6, loudness 1e-5 LU). Twelve
Original and two export streams remain byte-identical to the retained pre-stereo baseline.

Three shared fixtures add native validation, exact binary/CRC and UTF-8/content/variant identity
cases. The actual native reference runs **18 tests / three suites**. Removing one identity row
fails both consumers and invalidates Gradle; restoring and repeating the same command without
rerun flags executes/passes all eighteen. Logs/JUnit and source controls are retained locally.
Eight production-source faults and one compiled native bypass are detected and exactly restored
or isolated. Four actual-worklet controls detect broken prepared/request/width/pause safeguards.
Actual integration controls also reject missing Settings actions, early PCM, wrong
request/subsong identity and stale startup settings.

The real ESM app/worklet/worker/WASM bridge exercises actual row/Settings actions, nonzero GME
selection, first PCM, pause, Stop and late replies. Its real ten-second unanswered-open scenario
reproduced an existing deadline that reported failure without retiring the request/private handle.
The watchdog now cancels that attempt and closes its decoder; late delivery cannot start it or
enter History. A separate global initialization-failure guard reproduced retained private startup
ownership; failure without a request ID now cancels all pending starts, and late delivery cannot
publish preparation. Focused Settings passes **28 ESM / 27 legacy assertions**, and **25 fresh starts /
641 assertions** cover retained/refused/malformed choices and changes during engine loading.
A new pause assertion initially ran before the asynchronous transport completed; it now waits
for the paused state, preserving the original no-resume assertion.

**At this implementation checkpoint, the complete page/full project gate and lint were pending.**
The subsequent complete run below closes W15 at host level; the checkpoint count was **23/25**. Browser appearance, keyboard/touch feel, sound
quality, real-worker scheduling on phones, routing, encoder and background acceptance remain
unchecked. No version/schema change, APK, merge, deployment or publication.

The first complete-gate attempt on **ba92fd2** passed **1262 native tests / 172 suites**, zero
failures/errors/skips, then stopped at the Random startup helper. That helper relocates the page
bootstrap into a temporary directory and had anchored only static dependencies; the new literal
dynamic imports resolved relative to the temporary path. The archived failure preserves all
793 matching frozen input hashes and fresh logs/JUnit. Resolving both forms against the real
bootstrap restores the unchanged four Random startup/ownership assertions. Application source
and WASM are unchanged by this test-harness correction. A fresh complete gate/lint is required.

The following unchanged-source native run hit a Robolectric framework class-loading error
(`FrameworkStatsLog`, 1054 completed / 1 failed / 1 skipped); its failure is archived without a
hardware-cause claim. Repeating the identical source/configuration passed all **1262 tests / 172
suites**. That full attempt also passed **1368 page assertions**, all **25 fresh-start cases** and
the **26 real normalization bridge assertions**, but stopped in the older engine identity check.
It expected two immediate `opened` replies despite the decoded/prepared/opened protocol. The
updated check retains both decoded request IDs, verifies silence and zero position before
preparation, rejects preparation for the replaced request, then accepts only the current request.
The complete engine check passes; a process-local eager-preparation fault breaks the two relevant
guards without changing production files or WASM. This brings the controlled-fault inventory to
**19**. Remaining late-stage SAP/native-audio/database/PWA/icon/server preflights pass; a single
complete forced gate/lint on the corrected candidate is still required before W15 closure.

## W15 — Complete host verification, 2026-10-29

The corrected candidate **90467b4ffa083534f7df1de5aa7c8153ea5c6e89** passes one complete
forced project gate: **1262 tests / 172 suites**, zero failures/errors/skips, **1032 seconds**,
all **31 native unit tasks executed** and every companion. This includes **1368 actual-ESM page
assertions**, **25 cold starts / 641 assertions**, **26 real normalization bridge assertions** and
**55 engine checks**. All **793** frozen source/test/build/artifact inputs match. Current-run
logs, JUnit and lint are archived; no partial runs are combined into this result. After a tools
reconnection the process handle was unavailable; completion was observed through every phase's
exit-zero record and the script's final success summary, rather than an invented wrapper exit.

Lint has **0 errors / 79 warnings**. Its valid cached analyses were reused in the complete gate
(6.9 seconds); the preceding independent forced lint executed all 30 tasks in 207 seconds against
identical Android/lint inputs. The existing JDK17 uses process-scoped C1-only JIT, including test
workers; application and shared-tool settings are unchanged. Nineteen guarded source/compiled
faults and shared-fixture invalidation/restoration evidence remain retained.

W15 closes **24/25 scopes** (W01-W24) at host level. W25, the final every-commit/requirements/
exclusions audit, final integrated checks and WEB package remain. Real browser rendering, sound,
touch, routing, worker scheduling, encoder and background acceptance remain unchecked. No APK,
version/schema change, merge, push or publication.

## W25 — Browser system controls, 2026-10-29

Reference: accepted Android **c8209b1ab79976ec28d27e1da447055756c7ca29**. Compared
`PlaybackService` callbacks, `PlaybackMediaSession`, `PlaybackPresentation`, `TrackArtwork`,
`TrackArtworkBitmaps`, `PlaybackController` explicit play/pause/seek/subsong/EOF paths and their
existing session/presentation/artwork tests. Stop pauses and requests clearing system metadata,
position and declared playback state while retaining the loaded position and source, matching the
service's pause-and-dismiss intent. Whether the OS removes its surface belongs to the browser.
Browser-owned notification layout and an extra heart remain excluded.

`system-media.js` owns eight independent optional action registrations and platform presentation.
The page, keyboard and platform now call the same explicit Play/Pause/Stop and short Next/Previous
and seek entry points. Repeated Play preserves active/pending/resuming playback; Pause cancels
startup and invalidates delayed resume/first-touch work. Previous pointer-hold suppression cannot
consume a later system command. Source, manual FIFO and duplicate identity remain independent of
the visible playlist. Seek uses the existing coalescing path and an exact pending target outside
the range slider. Commands recheck current capability; an unsupported action or refused metadata,
state or position API cannot prevent other supported actions or ordinary playback.

System title and subtitle come from the actual decoded tune and existing dock summary, with the
source path as album. Loading, refusal and Stop clear stale metadata/position. Subsong and language
changes republish the common summary. Only finite positive known duration is published; unknown
length clears the position instead of fabricating duration from the fallback. Paused position uses
valid rate 1 with declared paused state. Play/Pause/restart/state recovery rebase the browser's
position timestamp even when the last decoder position is unchanged. An AudioContext state-change
observer updates presentation on suspension/interruption without issuing transport commands.

`track-artwork.js` generates one cached local 512-square PNG. Its UTF-8 filename/title/ID seed,
FNV/avalanche hue, Native Float colour calculation, luminance/contrast and suffix-before-prefix
format labels match Android. No cover service or new dependency is used; absent/refused canvas
falls back to the packaged icon. A shared 367-row TSV covers all 360 hues and seven seeds in both
Kotlin and JS. Browser font/rasterization remains a manual acceptance item.

Event-to-command review reproduced further ownership defects: a paused tune chip bypassed playback
intent, an old coalesced seek could move the new subsong, and queued EOF could advance after Pause.
All subsong selections now share one entry, clear old seek ownership, temporarily suspend seeking
and carry a unique selection ID through the processor. Rapid Next/Previous uses the requested
index; old ACKs cannot publish even after returning to the same index. Refusal preserves prior
intent, restores controls and explains failure. Late EOF while paused remembers that Play must
rewind without advancing or consuming FIFO requests. Successful selection refreshes position,
duration, title and seek availability; a later Pause still wins.

Two Native inconsistencies are explicit exceptions, not unreported parity claims. Its current
`togglePlayPauseTo(true)` can cancel an in-progress load through the toggle path; WEB explicit
Play keeps that load alive, with Pause/Stop as cancellation. Native `requestSubsong` restarts actual
audio when paused, while the Kotlin state update omits `playing=true`; WEB displays the restarted
state consistently. Android production is unchanged by this goal. Native's paused EOF polling
rule is retained: delayed messages cannot automatically advance a paused source.

Focused evidence: **27 system-policy cases**, **381 artwork cases**, **46 actual-ESM transport
checks** and **14 real app/worklet/native-WASM PCM checks** pass. The latter includes actual tune
chips selecting silent/audible NSF subsongs from Pause. Six Native artwork tests pass; removing
one shared row fails both runtimes (367 versus 366), and exact restoration reruns and passes the
same Gradle command without `--rerun-tasks`. Ten deliberate process-local defects break their
intended page guards: repeated Play toggle, late resume, DOM-click Next, forked subtitle, stale
first-touch, seek flooding, chip bypass, old seek, paused EOF and old same-index ACK. A further
real-WASM processor guard rejects invalid subsong selection without resuming Pause and checks
successful selection identity/restart; a process-local unconditional-restart fault breaks it.
The complete page preflight also passes after replacing three premature synthetic-open fixtures
with acknowledgements following real open commands. The original assertions are retained; no
application source change was used to accommodate these obsolete test setups. Controlled
faults never replace the production files. Full W25/project verification is still pending at this
checkpoint; **24/25 scopes** remain closed until that complete run passes.

### W25 complete host verification

Candidate **9af16b9ae5d2a951eea87e6a8350a7a4b90c3b34** passes the forced complete project
gate: **1263 tests / 173 suites**, zero failures/errors/skips, **1217 seconds**, all **31 native
unit tasks executed**, **1368 actual-ESM page assertions** and every companion. The gate includes
the system-event and real app/worklet/WASM PCM paths and **57 engine checks**. Lint completes in
**146.2 seconds**, with **0 errors / 79 warnings**; seven of thirty tasks execute, including all
three analysis tasks, while valid prerequisite outputs are reused. All **811** frozen inputs
match. Current-run logs, JUnit, lint and source hashes are archived locally; the outer process
also exits zero. The existing JDK17 and process-local C1 workaround are retained.

This closes **25/25 implementation scopes at host level**. Final requirement/commit/exclusion
review, its corrections, final integrated verification and a reproducible WEB package remain.
This is neither physical-browser acceptance nor a resolution of the routing boundary below.

### W25 platform boundary and C104

The [Media Session specification](https://www.w3.org/TR/mediasession/) assigns active-session and
hardware routing to the browser; declared playback state alone does not select a session.
[Current upstream Chromium](https://github.com/chromium/chromium/blob/main/content/browser/media/session/media_session_impl.cc)
inspection on 2026-10-29 shows Android ambient players do not request audio focus and its desktop
declared-state shortcut is not enabled on Android. Blink AudioContext publishes ambient audio.
This is development-source evidence, not a reproduction or a released-browser support matrix;
C104's reporting browser/device/build remains unspecified. Existing optional AudioSession playback
classification is preserved. No silent media, relay, lossy re-encoding, native focus imitation or
unapproved output-graph change is introduced to fabricate a controllable session.

A further read-only retry inspected
[Chromium's MediaStream player](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/modules/mediastream/web_media_player_ms.cc):
it reports one-shot content, which the same session controller excludes from controllability.
Routing the live graph through a MediaStream element therefore is not an evidenced fix for this
limitation. No output relay is added on that assumption.

The supported callback implementation is testable on the host. Actual lock-screen/notification,
headset/Bluetooth delivery, background lifetime and resuming after interruption remain unchecked
and may be unavailable in a browser that does not route WebAudio as a controllable session. Keep
C104 open for physical verification and retry this platform boundary during the final audit.

## Final audit corrections — in progress, 2026-10-29

W08/W25 text presentation reused JavaScript `trim`, which differs from Kotlin for separator
controls and the BOM. Learned display/source lines, History presentation reads and artwork seed
blankness now share the existing Kotlin-compatible text rule. Filename/title priority keeps the
original untrimmed seed. Before correction the new guards fail all three WEB paths; afterward
**6 learned-title**, **16 History storage** and **382 artwork cases** pass. The equivalent Native
cases pass **10 tests / 3 suites**, zero failures/errors/skips, without Android production changes.
The storage comparison surrounds its BOM with spaces so it tests trimming independently of the
SQLite string codec; an earlier leading-BOM fixture exposed that distinction and is archived.

These are focused correction checks, not a replacement for the final integrated gate. The final
audit, its remaining fixes and package remain in progress; no new candidate is ready for acceptance.

The PWA revision previously labelled a file list without checking delivered bytes. A real Node
Fetch reproduction against the generated worker installed an HTTP-200 older engine under the new
revision. Requests now carry generated SHA-256 integrity metadata, both during atomic installation
and on a cache-miss network fallback. Mismatches delete only the failed candidate cache, preserving
the previous version. Missing request-integrity support refuses the new offline installation.
The production-path test fails before each correction and passes afterward for older engine/JS
responses, matching responses, cache misses, failed updates and unsupported integrity. Existing
subpath, other-app cache, Range, pairing and remote-request isolation checks still pass. Cache
Storage is modelled in this host check; Fetch/network/integrity verification is real, not a claim
about physical-browser rendering or installation. Final integrated verification remains pending.
