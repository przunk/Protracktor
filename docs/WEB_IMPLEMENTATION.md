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
| W01 | Independent playback context and selected playlist | Model substage checked; page/event integration in progress |
| W02 | Full manual queue and list interactions | Pending |
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

The WEB currently makes its `queue` both the transport source and the visible playlist. Random
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
protection from a late save clearing a newer edit. The module is not connected to the page yet:
this substage does not establish a completed W01 user feature.

The new `docs/rules/playback-context-cases.tsv` has 32 transport cases, consumed both by
`PlaybackContextParityTest` against production Kotlin ActiveQueue and by
`scripts/check-playback-context.mjs` against the WEB model. The Kotlin file input is registered
with Gradle. The complete JS model suite currently passes **66 checks**, including the shared
cases and ownership/immutability cases ported from existing Android tests. A deliberate mutation
replacing the active context when a different playlist is selected fails the ownership test;
restored code passes. The focused production-Kotlin parity test also passes all 32 fixture rows.
Page/event integration remains in progress. Logs and mutation evidence
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
