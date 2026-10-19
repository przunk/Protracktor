# Playback queue experiment — B45

Owner-authorized experiment, 2026-10-18. Branch `feature/playback-queue-experiment`, based on
develop `9ca86ea` (commit count 1219). This document consolidates the discussion, decisions,
code findings, implementation boundaries and verification. The owner accepted the queue behaviour
and authorized merging on 2026-10-19. Normal-version cleanup and fresh host/artifact verification
passed; merged into develop as `7cab210` (section 13). Earlier pending states and fixed-code
artifacts are historical. Follow-up work starts on a new task branch from develop.

## 1. Why an experiment

Real user feedback initiated B45: returning to the playlist from playback in Browse or Search
should not interrupt the music. The owner selected a broader solution: one playback queue,
independent of the visible collection, with its own inspection screen.

Success means a simpler and predictable listening experience, not just moving existing code.
The owner will test a minified APK and may reject the experiment. No automatic merge, push,
release or publication is authorized. If rejected, keep develop unchanged and retire this branch
on the owner's instruction; do not delete saved playlists, History, bookmarks or cached music.

AMP remains on `feature/amp-catalogue`. Its positive phone report does not authorize merging it;
maintainer permission is still pending. Do not incorporate AMP playback into this experiment.

## 2. Selected interaction contract

The following decisions were selected by the owner on 2026-10-18. They supersede the older A61
session-ending Playlist action and earlier alternatives in Q13 for this experiment only.

### A single active playback owner

- Exactly one active queue supplies the current item and file-level Next/Previous/end decisions.
- Repeat and shuffle operate on that active queue, not the collection currently on screen.
- A queue records its source so the UI can explain what is driving playback.
- Playlist, Browse, Search, History, Random and the queue screen do not independently own audio.
- Playing a row in the active queue updates the corresponding source view's selection, and
  playing a row in that source view updates the active queue.
- Navigation alone never replaces, resumes, pauses or stops the active queue.

### Playlist viewing is not playlist playback

- Selecting another saved playlist displays it while the previous queue continues playing.
- Only selecting a track to play activates the displayed playlist as the queue source.
- Returning to the playlist from Random/Browse/Search keeps the current queue and audio intact.
- Remove the blocking playlist cover and the required choice between returning to the source
  and returning to playlist playback. The playlist is usable while another source plays.
- Highlight a playing row as belonging to the playlist only when that playlist owns the queue.
  A matching file on an unrelated visible playlist does not make that playlist the active source.

Earlier discussion suggested replacing the queue on playlist selection. The owner explicitly
rejected that transition: selecting the playlist is navigation, not a request to change music.

### Search, Browse and History activation

- Playing one of ten results activates that collection, starting at the selected row.
- The queue screen and the originating collection show the same active selection where present.
- Opening another folder, editing a search or viewing a different collection does not transfer
  playback ownership. A later Play from that collection does.
- In particular, opening More from this author must not silently transfer Next/Previous to the
  author's folder. An explicit Play there is the transfer point.

### Random sessions and the dice gesture

- Reopening an existing Random view shows its remembered record without rerolling or taking
  over playback. There is no Resume Random button.
- Random -> author folder -> play an author track -> reopen Random leaves the author's queue
  active. Next/Previous still walk that author collection. Playing a Random row activates the
  remembered Random session at that row.
- Ordinary Next walks the remaining heard record first, then consumes a new Random selection.
- A double tap on the dice starts a new Random list and plays its first selection, preserving
  the selected Random filter. Global History is not cleared.
- A single tap on the dice performs no playback action. The earlier proposal for single-tap
  reroll-with-history was rejected: Next already supplies the normal forward/new-selection path.
- Animate a dice throw/rotation on the recognized double tap. Provide a persistent explanation:
  EN `Double-tap to reroll the list`; PL `Dotknij dwukrotnie, aby wylosować nową listę`.
- Provide an equivalent accessibility action; it must not depend on recognizing a physical
  custom double tap. The earlier single-click delay is unnecessary because no single action exists.
- Preserve C97: changing the filter retires only unplayed speculation. Heard history, its cursor
  and the remaining heard forward record survive; only newly generated choices use the new filter.

### Queue view is not an editor

- Provide a dedicated way to inspect the active queue regardless of the current source view.
- Allow browsing rows, choosing a track and inspecting track information.
- No removal, reordering, drag handles, editing files or modifying the source collection from
  the queue screen. This supersedes any earlier discussion of editing this screen like a playlist.

## 3. Transition matrix

| Action | Visible collection | Active playback |
| --- | --- | --- |
| Open Browse/Search/History/Settings | Changes | Unchanged |
| Return to the playlist | Selected playlist | Unchanged; no blocking cover |
| Select playlist B while A plays | Playlist B | A continues |
| Play a row in playlist B | Playlist B | B queue, selected row |
| Play a row in Search/folder/History | That source | That collection, selected row |
| Open an author's folder | Author folder | Unchanged |
| Open the queue screen | Current queue | Unchanged |
| Play a queue row | Current queue | Selected row of that same queue |
| Reopen remembered Random while an author plays | Random record | Author continues |
| Play a remembered Random row | Random record | Remembered Random session |
| Double-tap the dice | New Random record | New session, first selection |
| Single-tap the dice | Unchanged | Unchanged |
| Change the Random filter | Same heard record | Current track unchanged; replace speculative tail |

These transitions must also hold when the player is paused or loading. Merely viewing a screen
must not resume a paused track, cancel its current fetch or adopt a late result from an old source.

## 4. Current code and the reason this is not just a navigation patch

Read-only inspection on develop established:

- `PlayQueue` already provides immutable, Android-free list order, shuffle, repeat and cursor
  behaviour. Reuse its proven rules rather than writing an unrelated second playlist algorithm.
- `PlayerUiState` currently chooses `transient ?: resultsQueue?.current ?: queue.current`.
  Playlist playback, results playback and Random therefore have separate ownership paths.
- `RandomHistory` keeps a heard boundary, cursor, speculative tail and generation checks. Its
  C97 behaviour must survive the architectural change, including asynchronous refill cancellation.
- `PlaybackController` branches separately in transport, end handling, retry and prefetch.
  `returnToPlaylist` stops playback and clears Random/results; `openRandom` starts a new session.
- `showNeighboursOf` can install an author results queue merely from navigation. This deliberately
  changes under the new contract; it is not a newly discovered regression in the old behaviour.
- `PlaylistSession.switchToPlaylist` currently stops playback. `PlaylistSessionState` uses the
  same `queue` as both editable playlist content and playback state. Both assumptions must change.
- `ProtracktorApp` and the dock currently expose the session cover and return/resume affordances.
  Changing only the controller would leave misleading UI; changing only the UI would leave the
  old ownership and transport rules.
- `HistoryRecording` deliberately does not reorder/count entries while playback originates in
  History. Queue origin must preserve that rule even when another screen is visible.

## 5. Proposed architecture and safeguards

This is an implementation design, not authorization for additional user-facing features.

1. Introduce one Android-free active-queue model/coordinator. Its source is an exhaustive sealed
   type (playlist identity, captured Browse/Search/History source, Random session, external file).
   Transport decisions and current-entry selection must have one owner.
2. Separate selected playlist draft/persistence from active playback. Playlist writes capture the
   destination ID and that playlist's tracks, never whatever collection happens to drive audio.
   Preserve C103/C105 safeguards, dirty tracking, save/discard, Undo and asynchronous activation.
3. Keep collection identity separate from file identity. A file may occur in different sources;
   source ownership, not just a matching URL, determines which view gets an active highlight.
   Preserve stable row identity where collections allow repeated files.
4. Capture a finite results collection at activation rather than allowing a later query, History
   reorder or folder read to replace the running sequence. Metadata enrichment may update labels
   without changing identity or order. Exact pagination policy remains to be confirmed below.
5. Treat Random as an extensible queue source, retaining its heard boundary and private read-ahead
   strategy. Keep a dormant remembered Random session when another source takes over. Merely
   displaying that dormant record must not mutate the active queue.
6. Keep decoder, network and Android media-service adapters outside the domain model. UI, headset
   and notification controls issue the same transport commands against the active queue.
7. Retire asynchronous work by playback/source generation. Old downloads, native-open results,
   retries, queries and Random refills must not replace a newer source, cursor or queue.
8. Keep playback-position, subsong, seek, audio focus, Bluetooth route recovery and listening
   qualification responsibilities intact. File-level queue advancement does not erase the
   existing play-all-subsongs layer.
9. Do not add dependencies or change database schema 23. An experimental restoration format, if
   selected, must be separate and ignored by the old APK; do not repurpose shared persisted fields
   with meanings that AMP/develop would interpret differently.

The notification heart continues saving immediately to the selected playlist, as previously
approved. That playlist may differ from the active queue's playlist. Bookmarking, saving a track,
sharing and viewing information must not silently replace the active queue.

## 6. APK interchange with AMP — a required compatibility gate

The owner wants to alternate AMP and QUEUE APKs without uninstalling or losing existing data.
Keeping only the visible version string is insufficient.

**Verified by reading the existing AMP APK with aapt2:**

- Artifact: `dist/protracktor-release-1.2.0-1221-64c7dcc-20261018-150242.apk`.
- Application ID `com.przunk.protracktor`, versionName `1.2.0`, versionCode **1221**.
- The existing handoff records its test certificate and schema 23; compare the finished QUEUE
  APK's certificate and manifest directly rather than relying solely on build configuration.

Android rejects an APK with a lower versionCode than the installed package. Equal version codes
are needed for repeated replacement with these fixed artifacts, with the same application ID and
compatible signing certificate. This is sideload testing, not a Play upload/release procedure.
See [Android versioning](https://developer.android.com/studio/publish/versioning).

**Owner-approved exception, 2026-10-18:** a narrowly scoped exception pinning only QUEUE
test APKs to code 1221. Normal builds/AABs retain the commit-derived code and versionName stays
1.2.0. Guard this behind the existing test-APK build path, identify every artifact by
SHA/timestamp, disclose the exception and remove the temporary override before any merge. Do not
silently replace the project's versioning policy, rewrite history or relabel the existing AMP APK.

### Same schema is not enough

**Code-inspection finding:** develop's startup calls `CatalogueStore.pruneUnknownCatalogues()`.
Its known IDs come from `Catalogue.all`, which has no AMP on this branch; it would therefore delete
AMP's persisted index when QUEUE first runs against the same app data.

The experiment must preserve that index while keeping AMP unavailable here. Preserving rows alone
is insufficient: broad search/Random/platform-count queries must not expose retained unsupported
AMP rows. Also check retained playlists, History, bookmarks and cache, not just the catalogue list.
This compatibility safeguard does not enable AMP, import its feature or authorize publication.
It requires a real SQLite regression test for retained AMP rows and visibility in both directions.
No destructive migration, uninstall, Clear storage or automatic database reset is acceptable.

## 7. Further choices and conservative implementation boundaries

- **Owner selected, 2026-10-18:** the queue opens as a Now Playing-like sheet, immediately at full
  screen height, through an icon-and-label Queue action by the player. Reuse track/info presentation.
- **Owner selected, 2026-10-18:** the first entry into an empty Random session starts playing as
  today. Later entries only show the remembered list, without changing the active queue.
- Showing speculative Random picks in the queue view. Earlier discussion proposed Up next, but
  the owner has not selected this; today's Random view intentionally shows only the heard record.
- Random shuffle/repeat-all semantics. Earlier suggestions to disable them were not approved.
  Preserve existing intentional behaviour until an explicit alternative is selected.
- Finite-result pagination: proposed queue = currently loaded collection, without background
  fetching all result pages. Do not confuse a database result count with actual queued entries.
- Playlist edits when that playlist owns the active queue. Proposed: reflect edits with stable
  identity and preserve current removal/Undo semantics; unrelated playlist edits cannot affect it.
- Process-death restoration of temporary queues was not selected and is not included. The existing
  paused selected-playlist restoration remains; temporary collection/Random sessions stay in memory.
  A future persistence design must also remain safe for the older AMP APK.
- Accessible reset affordance beyond the standard accessibility action; a menu alternative was
  suggested but not selected as an additional visible control.

## 8. Implementation stages

Each stage ends in a focused commit and states its actual verification evidence.

1. Resolve the test-APK version exception and remaining interaction choices. Establish the shared
   data compatibility guard and failing-before-fix SQLite tests; record the two-APK test protocol.
2. Introduce/test the single active-queue domain, source identity and deterministic transitions.
   Reuse existing PlayQueue/RandomHistory invariants, with controlled randomness in tests.
3. Separate playlist selection/draft writes from playback; integrate controller transport,
   natural ends, retry and read-ahead. Remove navigation-triggered queue changes and obsolete
   waiting/resume ownership. Keep unrelated bug C109 and AMP implementation out of scope.
4. Add the read-only queue view, source-aware highlights, usable uncovered playlist, remembered
   Random navigation and double-tap reset/animation/help/accessibility in EN and PL.
5. Review changed paths, update architecture/Q13/B45/status/testing/build documentation, run
   forced full tests and lint, then build/inspect the minified test-signed release APK in `dist/`.
   Leave the branch unmerged for owner testing and rejection/acceptance.

## 9. Verification and acceptance

For each new regression test, demonstrate a failure against the relevant old/broken mechanism
or a deliberate mutation, then restore it. No zero-test success or cached-only run counts.

Host checks must cover at least:

- playlist A playing -> select/edit/save B -> transport still owns A, saves target B;
- click B -> one coherent transition to B; stale A load/save does not override it;
- source/queue selection symmetry and source-sensitive highlighting, including a shared file;
- snapshot Search/History order despite query changes, new History entries or learned titles;
- Random -> author Play -> reopen Random -> Next remains author -> Random row Play transfers;
- C97 rewind/filter/Next behaviour, one natural-end advance, cancelled late Random refill;
- no single-tap action, exactly one reset per double tap, actual gesture/accessibility paths;
- queue screen has no remove/reorder actions; ordinary playlist edit/Undo/save still works;
- repeat, shuffle history, subsongs, finite queue end and external single-file ownership;
- heart destination, History-origin suppression, listened markers, bookmarks and audio controls;
- AMP index retention without AMP search/Random leakage, no schema change or user-data loss;
- actual APK identity, code/signature, R8 result and packaged EN/PL resources.

Run the project full gate and lint after application/build changes. If a test reads an external
fixture/configuration, declare it as a Gradle task input. Build-tool changes require verification
of the affected artifact path, not just syntax. Keep Gradle work serial in the shared build root.

Owner phone acceptance must cover uninterrupted navigation and playlist switching, queue/source
selection, Random double-tap/reset/return flows, loading/paused transitions, notification/headset
transport and actual AMP -> QUEUE -> AMP replacement without uninstalling. In both replacements
verify playlists, History, bookmarks and AMP index/cache retention. The owner must confirm the
installed build; host tests and a successful APK build do not prove gestures or audio on a phone.

If the experiment is rejected, return to the existing AMP/develop APK without forcing data loss.
If accepted, remove temporary APK-version exceptions, reverify and obtain explicit merge approval.
After integration, update the still-unmerged AMP branch from develop and retest its playback,
switch gating and no-speculative-fetch rules before considering its separate approval gates.

## 10. Implementation and current evidence

Implementation stages 3–4 are one atomic integration: removing old state constructor fields also
requires replacing their screen/transport consumers. This avoids a non-compiling intermediate
commit or keeping two competing playback owners.

- `ActiveQueue`/`QueueSource`/`QueueStep` implement the common Android-free transport rules.
  Existing mode aliases in `PlayerUiState` are read-only projections, not independent state.
- The selected playlist draft is separate from playback. The same selected source playlist stays
  live; switching its view captures the old order, and viewing it again restores the active cursor.
  Unrelated playlist edits and metadata do not replace the active queue.
- Controller natural completion, notification/headset commands, queue-row selection and retry use
  the active source. Random queries carry an intent epoch; retries consult the current queue.
- More from this author and Back restore only navigation. The blocking cover and obsolete return
  controls are removed. The queue sheet shares existing row/info components with editing disabled.
- `RandomDice` recognizes touch double tap, never touch single tap; its semantic accessibility
  action is intentionally equivalent to reset without requiring the custom physical gesture.
- No speculative Random rows, new mode policy, expanded result fetching, persistent temporary
  queues, schema change, new dependency, web-player implementation or C109 fix was added.
  Process death retains the old paused selected-playlist restoration, not the temporary queue.
- Normal Gradle/AAB configuration remains commit-derived. Only the existing test-APK flag selects
  the owner-approved 1221 exception. This must be removed before merging.

### Focused and negative evidence

- AMP index loss reproduced before the fix: one failed assertion, one fail-fast skip. Visibility
  mutation failed on the retained-row count; fail-fast means it did not prove the routing guard.
- Restored compatibility checkpoint: **16 tests passed** (three native SQLite interoperability,
  thirteen TrackFiles), zero failures/errors/skips. AMP implementation was not imported.
- Initial domain run passed ActiveQueue/PlayQueue/RandomHistory tests. Mutating external-file Next
  to RandomNext caused the expected assertion failure (seven ActiveQueue tests, one failure).
- Integration run executed **96 tests**, with two failures: an accidentally moved existing atomic
  read/retry boundary, and a test using physical `performClick` instead of the semantic accessibility
  action. The concurrency behaviour was restored; the accessibility test now invokes that action.
- Corrected targeted run: **11 tests passed**, including six source-selection checks, four UI tests
  and the existing atomic retry-boundary check.
- Controlled production mutations then executed **18 tests**, with exactly **four expected
  assertion failures**, zero errors/skips: lost source ownership on playlist navigation, AMP bytes
  falling through to document reads, single-tap reset, and visible Remove in the read-only queue.
  All four mutations were restored before final verification.

### Final review corrections

The first full gate stopped at 185 tests on the existing AtomicFileWrite concurrent-reader test.
Production AtomicFileWrite and its original test were identical to develop before this finding.
The assertion reopened the target for each candidate payload, so several valid publications could
all fail different comparisons. A deterministic rotating reader reproduced this false rejection.
Only the test now captures one byte snapshot before comparing candidates; production cache writing
is unchanged. This is not attributed to the environment, RAM, Java or a decoder failure.

Review also found that an explicit Random reset left the previous source in control until the first
database result. A production-used state transition now installs an empty Random owner immediately,
with a loading state, while preserving the selected playlist draft. Explicit Stop/Pause retires the
intent and native/file open; stale query results cannot restart it. Filter changes during a pending
query restart only that query, not an already selected file fetch. No new navigation side effect
was introduced.

Both mechanisms were reproduced by tests before correction (two tests, two assertion failures).
The corrected focused run passed 31 tests. A further real UI regression reproduced a stale Rolling
label after cancellation; idle/failed/loading empty Random states are now distinct. Removed cover
strings are deleted in both languages rather than shipping obsolete UI resources.

A subsequent full run stopped at 911 tests (one failure, one fail-fast skip): the old played-before
UI fixture supplied Random rows but no active Random source. Its selection assertion was retained;
the fixture now models the actual source. **All 91 UI tests / 15 suites then passed**, zero failures,
errors or skips. This does not establish phone behaviour.

Actual Gradle configuration probes passed without signing/building an AAB: normal code **1223**
matched the source checkpoint's commit count; the test-APK flag alone selected code **1221**.
Both retain `com.przunk.protracktor` and name 1.2.0. Later documentation commits do not relabel
the artifact or make the historical probe count the current HEAD count.

## 11. Initial host gate and test APK — 2026-10-18

Clean source **`da75839789726ce42aed9c383f9c196bf87f2261`** passed
`./scripts/test-protracktor.sh --really` in **510 s**:

- **953 tests / 128 suites**, zero failures, errors or skips; unit stage **302.8 s**.
- Lint **0 errors / 73 warnings**, **95.4 s**. One new warning is the retired
  `search_playing_title` resource after removal of the cover. It is unused source text, not a
  failed build; clean it up alongside the temporary version override before an accepted merge.
- All companion stages passed: store metadata, runner/release fixtures, 581 page checks,
  49 engine checks, eight SAP checks, database upgrade, native audio, icons, PWA and 11 server checks.
- Local documentation link check: 118 targets, none missing.
- Unit XML and logs are retained in ignored `dist/queue-verification/`.

`./scripts/build-release.sh --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
built the clean source in **143 s**. R8 executed, its mapping is nonempty, and the APK is not
debuggable. It uses the existing local test certificate; no AAB/publication key was used.

| Property | Verified value |
| --- | --- |
| APK | `dist/protracktor-release-1.2.0-1221-da75839-20261018-235148.apk` |
| Name / code | `1.2.0 (1221)` — owner-approved test-only exception |
| Application ID | `com.przunk.protracktor` |
| Size | 11,828,663 bytes |
| APK SHA-256 | `0e9104c714ee73d2901df8fc19ed50fd925ae4f3d288fabf090e2e72986be772` |
| Signature | APK v2 verified; Android Debug test certificate |
| Certificate SHA-256 | `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e` |
| SDK | min 29, target 36 |
| Engine ABI entries | arm64-v8a, armeabi-v7a, x86_64 |
| Database | schema 23, no migration |
| Packaged source credits | SHA-256 `60831cb8b91af709872eb524bdb432f215f1320626eea4e101b6619ecc955233`, identical to source |
| Packaged privacy policy | SHA-256 `626b613ccc2297a8f2db4eed807ca0982dd22f1ea6cbbce45df56ab7eebb3862`, identical to source |

The reference AMP APK `protracktor-release-1.2.0-1221-64c7dcc-20261018-150242.apk` has the
same package/name/code and certificate, verified directly. Its file SHA-256 remains
`d4a9f33999fdeace6c578064faf64ac9fbeddcdd22c19f09cce4990fdcbccc04`.
This verifies the artifact prerequisites, not an actual phone install/round-trip.

**Device behaviour remains unchecked:** full-height sheet, continuity, gestures, audio and
AMP -> QUEUE -> AMP data interchange require the owner's [phone checklist](TESTING.md).
Temporary queue restoration across process death is deliberately not included.
No merge, push, tag movement or store release was performed. Develop remains `9ca86ea`,
AMP remains `8e92002`, master remains `794d8f9`.

After owner acceptance, remove the test-only version override and unused retired heading,
reverify the changed sources and obtain explicit merge authorization. The separate AMP permission
hold remains in force. Subsequent documentation-only commits do not change this APK's fixed source.

## 12. First device feedback — 2026-10-19

The owner tested the initial QUEUE APK and reported a promising overall result, with four requested
refinements. This is feedback, not blanket acceptance or merge authorization:

1. Put the labelled Queue action at the far right of the dock, outside the expandable Now Playing
   card, with its own touch target. The original card contains the action as a child.
2. Back from Random must return to Browse, its entry point, rather than skipping to the playlist.
   The original callback hides Random after its entry hid Browse. System Back must also respect
   a Browse/author view over Random instead of letting Random consume that view's Back.
   The explicit playlist shortcut remains a direct exit; neither exit transfers playback.
3. Make the persistent dice hint explicitly identify the dice as the double-tap target (EN + PL).
4. Each fresh Queue opening must start at the current row, aligned at the top where the list's
   trailing boundary permits it. The shared `KeepRowInView` intentionally ignores arrival, so
   initialize Queue's list position explicitly and retain shared following for later changes.

Implemented on the experiment branch, without changing the playback controller, queue model,
schema or test-APK identity. **Checked on the host:** all eight `QueueExperimentUiTest` cases passed.
The card-boundary test fails against the original nested action. An isolated long-list test with
an explicitly controlled Compose clock fails against the original initial position, then passes
with the current row at the top and again after dismissing/reopening at a different cursor.
The real Back dispatcher test covers all four visible-screen priorities; deliberately putting
Random first produces one expected assertion failure (four Random callbacks instead of the four
visible destinations). The correct priority was restored before the full gate.

Intermediate automatic-clock UI runs encountered host idle timeouts, not confirmed application
defects. Explicit frames and snapshot notification, as used by the existing UI harness, corrected
the test setup; its position/callback assertions were retained. These timeouts are not counted as
successful bug reproductions.

Clean source **`bc32beabdeecdebafdb222f0f925f8c9d095776b`** then passed the forced full gate in
**535 s**: **956 tests / 128 suites**, zero failures/errors/skips; unit stage **342.6 s**.
All companion checks passed (metadata, runner/release fixtures, 581 page checks, 49 engine checks,
eight SAP checks, database upgrade, native audio, icons, PWA and 11 server checks).
Lint took **95.3 s**, with **0 errors / 73 warnings**, unchanged from the initial QUEUE build.
The retired search-cover heading is still a pre-merge cleanup, not part of these four refinements.
Four updated documents' **78 local links** were checked; none is missing.

The release script built the clean checkpoint in **107 s**; R8 executed and produced a nonempty
mapping. APK inspection verified the package/name/code, absence of the debuggable flag, APK v2
signature and all three engine ABI entries:

| Property | Verified value |
| --- | --- |
| APK | `dist/protracktor-release-1.2.0-1221-bc32bea-20261019-002459.apk` |
| Name / code | `1.2.0 (1221)` — unchanged owner-approved test-only exception |
| Application ID | `com.przunk.protracktor` |
| Size | 11,830,331 bytes |
| APK SHA-256 | `7eb0ed0df15f2a9b0da9f407453c39640289191317014c435b7ce3bdab5b51c6` |
| Certificate SHA-256 | `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e` — same AMP test certificate |
| SDK / engine ABIs | min 29, target 36; arm64-v8a, armeabi-v7a, x86_64 |

Logs, full JUnit XML, lint XML and mapping are retained in ignored
`dist/queue-verification/feedback-bc32bea/`. The previous artifact remains an immutable checkpoint.
Device confirmation of these refinements and actual AMP interchange are **unchecked**; general
positive feedback does not certify every scenario. No merge, push, tag movement or store release
occurred. Develop remains `9ca86ea`, AMP `8e92002`, master `794d8f9`. Documentation-only closeout
does not relabel this APK or its tested source. The test version override must still be removed
before any accepted merge.

## 13. Owner acceptance and normal versioning — 2026-10-19

The owner confirmed that Queue behaves as intended and selected integration into develop before
the next iteration. Further feedback from a colleague has not yet been described; record and
analyze it separately rather than guessing or changing behaviour during this merge.

Before integration, the test-only code-1221 override is removed: every build again uses
`git rev-list --count HEAD`. `protracktorTestApk=true` still selects the existing test certificate,
not a different code. VersionName stays 1.2.0 until the owner's release procedure. The obsolete
`search_playing_title` strings are removed in both languages; no playback/UI behaviour changes.

The existing code-1221 AMP and QUEUE APKs are not relabelled or overwritten. New higher-code APKs
cannot ordinarily be replaced by the older AMP APK. Do not uninstall or clear data as a workaround.
The owner deferred AMP until after the next queue iteration; its branch, permission gate and
playback implementation remain untouched. Retained AMP data guards and schema 23 stay in place.

**Checked:** clean source `069b23807d378c8f54c9fbfde34f39717abdd7ae` passed the forced full gate
in **623 s**, **956 tests / 128 suites**, zero failures/errors/skips. Unit stage **423.8 s**;
lint **97.0 s**, **0 errors / 72 warnings**, one fewer after the dead resource was removed.
Every companion stage passed: metadata, runner/release fixtures, 581 page checks, 49 engine checks,
eight SAP checks, web database upgrade, native audio, icons, PWA and 11 server checks.
The normal debug manifest reports code 1227, equal to that checkpoint's commit count.

The accepted feature was merged as **`7cab2104631457df1ebfaa3fe7e820f7d030aa55`**. A full-tree
comparison with `069b238` is empty: integration added no source changes. The clean merge then
built the minified, test-signed APK in **98 s**:

| Property | Verified value |
| --- | --- |
| APK | `dist/protracktor-release-1.2.0-1228-7cab210-20261019-220200.apk` |
| Name / code | `1.2.0 (1228)`; code equals the merge's commit count, no APK override |
| Application ID | `com.przunk.protracktor` |
| Size | 11,829,987 bytes |
| APK SHA-256 | `14332bfb9e7dab4155b472973d68493b3e57a7dbf8b319cba036331b355f6e14` |
| Certificate SHA-256 | `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e` |
| Signature / variant | APK v2 verified, test certificate; R8 ran, mapping nonempty, no debuggable flag |
| SDK / engine ABIs | min 29, target 36; arm64-v8a, armeabi-v7a, x86_64 |

JUnit XML, unit/lint logs, lint XML, the normal debug manifest, merge-build log and mapping are
retained in ignored `dist/queue-verification/integration-069b238/`. Later documentation-only
commits do not relabel this APK or its source. The original experiment branch is integrated;
further queue work must start on a new task branch from develop, not continue that branch.

The owner's positive phone report covers the tested QUEUE behaviour, not every checklist item,
an actual AMP round-trip or this newly built normal-code artifact. The new APK remains unchecked
on a phone. AMP stays `8e92002`, master `794d8f9`; no store release, tag movement or push occurred.
