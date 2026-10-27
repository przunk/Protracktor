# WEB feature implementation

Started: 2026-10-26. Branch: `feature/web-feature-parity`.
Baseline: `3183eeec7265cd88a88e09db4ee76706e6421d51` (accepted develop).

Implement the [selected scope](WEB_ANDROID_PARITY_2026-10-26.md#scope-selected-after-the-inventory--2026-10-26)
as separate feature/substage commits. The ignored local GOAL.md is the execution checklist.
Source inspection and host checks are not physical-browser acceptance. No automatic merge,
push, publication, APK, release version change or AMP integration is part of this work.

## Reference and storage allocation

Android reference is the baseline commit above. Read production code and existing Kotlin/native
tests for each scope; port their actual scenarios and expected results to WEB checks. Shared
fixtures are preferred where they express the same rule. Existing historical plans can describe
earlier states; current accepted code/tests govern behaviour unless an actual defect is recorded.

The current browser database is version 4. Reserve version 5 on this branch for the coordinated
discovery/History/bookmark upgrade and version 6 for retained remote music/ASMA storage, if those
schema changes are needed. Reservations are not migrations already implemented. Existing user
stores, URL identities, playlist ownership and the phone-handoff byte lifetime must survive.
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
| W02 | Full manual queue and list interactions | Implemented in model, transport and keyed panel; full host gate passes; browser/device acceptance unchecked |
| W03 | Return to playback source | Pending |
| W04 | Remembered Random and deliberate reroll | Pending |
| W05 | Random platform/Favourites filters | Pending |
| W06 | Qualified played-before marks | Pending |
| W07 | Saved catalogue places | Pending |
| W08 | Learned list titles | Pending |
| W09 | Recent-search suggestions | Pending |
| W10 | Live History search | Pending |
| W11 | Search scopes and saved-playlist coverage | Pending |
| W12 | Playlist reorder and import/export | Pending |
| W13 | Bulk Browse/Search/History actions | Pending |
| W14 | Original/Narrow/Mono | Pending |
| W15 | Playback volume normalization | Pending |
| W16 | Compact dock and subsong indication | Pending |
| W17 | Browse position and subsong following | Pending |
| W18 | Playback-mode/settings restoration | Pending |
| W19 | Read-ahead and prefetch controls | Pending |
| W20 | Persistent music cache and offline marks | Pending |
| W21 | Complete offline ASMA | Pending |
| W22 | Grouped downloads and Stop | Pending |
| W23 | Legacy Modland request recovery | Pending |
| W24 | Language changes without interrupting playback | Pending |
| W25 | Browser Media Session and external controls | Pending; C104 reopened by scope selection |

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
