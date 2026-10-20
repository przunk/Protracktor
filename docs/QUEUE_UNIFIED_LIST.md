# Unified playback list — third iteration

2026-10-20 follow-up: the owner selected long-press Info and drag reordering **only inside the
waiting manual block**. [Follow-up contract/evidence](QUEUE_MANUAL_REORDER.md) supersedes the
earlier trailing Info control and manual-reorder exclusion. APKs 1248/1245 record earlier
handoffs. The subsequent [presentation follow-up](QUEUE_PRESENTATION_POLISH.md) adds animated
departures, explicit Info haptics and on-demand gesture Help. Its source **6043902** passed 1043
tests/full gates and lint; APK **1.2.0 (1250)** records that handoff. The subsequent
[scroll-first correction](QUEUE_SWIPE_INTENT.md) passed 1056 tests/full gates and lint at source
**f6a5ee4**; APK **1.2.0 (1252)** is the current unmerged phone candidate. Clear queue remains a
discussion, not an approved or implemented extension.

2026-10-20, owner/user feedback after testing APK 1.2.0 (1239). Continue on
`feature/manual-playback-queue` in separate stage commits; no merge is authorized.
This supersedes the two-tab/mixed-manual-history rules in [the previous contract](QUEUE_CONTEXT_ANALYSIS.md).
The previous APK/reports remain historical evidence, not verification of these changes.

## Selected behaviour

- One list combines context rows with a marked/indented block of manual occurrences after the
  parked context position. Only the actual occurrence is marked playing. The same dock remains
  visible and operable below the panel; remove the duplicate Playback/current-track header.
- FIFO additions go after older manual requests, including when added during another manual file.
  Equal files are independent occurrences. A right swipe adds a copy; it does not remove/move
  the original context row or deduplicate requests.
- A manual occurrence stays visible while current and disappears on completion, file Next,
  Previous or another explicit Play. Pause retains it. A read/open failure remains retryable;
  explicit skip consumes it. Repeat One retains/repeats it until explicitly left.
- File Previous during a manual occurrence returns to the parked context track (or the nearest
  surviving earlier context row if that position was removed). Previous from the next context
  track follows context history only, never consumed manual rows. Ordinary subsong controls remain
  unchanged; system Back closes the panel, not music.
- Explicit Play on a new source starts the selected track immediately, retains waiting manual
  requests next, then continues the new source. This is an agent-selected interpretation of the
  owner's manual-priority wording: `N3 -> X -> Y -> N4`, not `X -> Y -> N3 -> N4`. Navigation or
  merely selecting another editor playlist still does not replace playback context.
- Tap inside the existing unified list selects that occurrence without rebuilding the source,
  restoring omitted rows or reshuffling. Playing a later manual request leaves earlier pending
  requests available next. The interrupted current manual occurrence is consumed, not requeued.
- Left swipe on either type of unified row removes that occurrence from this playback session,
  not from a saved playlist, History, index or storage. Source omissions survive source Repeat All
  and disappear on explicit context replacement. Each Random occurrence has its own identity;
  removal is not a permanent blacklist of that file from future draws.
- Removing the current occurrence advances to pending/context next, or stops if exhausted.
  During pause it selects the successor without opening/recording/starting audio; explicit Play
  starts it. Playing/loading intent continues automatically. This is a conservative agent choice
  for the previously unspecified paused-removal case, retained for evaluation.
  Undo restores a row without rewinding audio. Context Undo is generation-bound and must not
  revive an old source after replacement. Manual Undo remains request-specific.

## Gestures and presentation

| Surface | Left swipe | Right swipe | Tap | Long-press | Trailing control |
| --- | --- | --- | --- | --- | --- |
| Unified playback list, either origin | Remove occurrence | Append a manual copy | Play occurrence | Info | Grip only on waiting manual rows |
| Playlist, Random, Browse/Search/History/local/online tracks | No new removal gesture | Append a manual copy | Existing action | Existing action | Existing menu |

Right swipe returns the original row to rest and commits once per gesture. Partial/reversed drags
must not enqueue. Left swipe reveals red with remove icon/text; right reveals green with queue-add
icon/text. Colour is not the sole cue. Retain Undo and accessible custom actions/menu equivalents;
long-press Info has a labelled accessibility action; the owner-selected drag grip also exposes
valid move-earlier/later actions. Reordering is confined to the waiting manual block.
Do not add swipe gestures to folder/header rows or steal selection/reorder interactions.

The panel occupies the Scaffold content area above the existing dock, not a full-screen modal
that intercepts dock touches. Back/close returns to the underlying screen without playback changes.
Snackbars remain reachable above the dock. The shared dock is not duplicated. Opening Now Playing
from that dock closes the queue panel before opening its existing modal.

Shuffle projection must show actual source order, without a new shuffle on each recomposition.
Random still shows its heard record, inserting manual rows after its cursor; speculative random
picks stay hidden. Next after the manual block follows remaining heard rows, then new draws under
the selected filter (C97). No speculative fake track row is introduced.

## Architecture and stages

Keep source data and manual FIFO separate underneath the one rendered list. Source occurrence IDs
must survive title learning, append/remap and session-only omissions, including equal Random files.
The selected playlist's draft remains the existing persistence owner and must never receive the
temporary omissions/manual rows. Replace the mixed manual transport record with a current actual
occurrence plus FIFO; source PlayQueue/RandomHistory continue owning source backward traversal.

1. Document this contract and retained questions (documentation check only).
2. Implement one-shot scheduling, source occurrence identities/omissions and pure projection;
   update controller/VM integration and regression tests. Prove changed rules fail old behaviour.
3. Implement shared bidirectional row gestures, one unified panel above the dock, compact Info and
   all track-list enqueue gestures with EN/PL/accessibility/real-host-gesture tests.
4. Review the full branch delta for new boundaries, force full tests/lint, build and verify a
   minified test-signed release APK into dist, record exact evidence and stop unmerged.

No schema/dependency/native/web/AMP change, versionName bump, push, tag or store release.
C111 padding and the cramped dock subsong label (`1 of 2 Tracks`) remain separate/deferred.

## Retained provisional questions

- New-source precedence: chosen track now, waiting requests next; evaluate after testing.
- Current removal/exhaustion: advance or stop; Undo restores content only, never transport.
- Same-source explicit restart resets omissions; tapping within the unified list does not.
- No context available: drain manual requests and stop; Previous cannot invent a source.
- Session-only queue/omissions, no persistence, source reorder or Clear. The follow-up allows
  reordering only waiting manual requests.
- Existing Repeat One/subsong behaviour remains; consumed manual entries do not join source laps.

All new device behaviour is **unchecked** until owner testing. The existing positive phone report
motivated changed requirements; it is not acceptance of the third-iteration implementation.

## Checkpoint evidence

Stage 2 replaces the mixed manual traversal record with one actual occurrence and a pending FIFO.
Source occurrence identities/omissions are session-local; selected draft tracks are unchanged.
The controller supports pending/current removal, source-only Previous, generation-bound source
Undo, in-list jumps without reshuffling, and omission-aware read-ahead/Random advancement.
Pure projection inserts the marked manual block in actual finite source order or Random heard order.

Checked partially: the Previous-anchor mutation failed as intended
(`/tmp/protracktor-unified-previous-mutation.log`, 22 s). Restored focused domain/controller run
passed **144 tests / 7 suites**, zero failures/errors/skips, in 35 s
(`/tmp/protracktor-unified-domain-final.log`). An earlier run stopped on an incorrectly updated intermediate assertion (expected B where
the second FIFO item Y should still play); the assertion was corrected, not the application rule.
The old mixed-history expectations are deliberately replaced by the owner-selected one-shot rules.
UI wiring, new real gestures, full gates/lint and a fresh APK are still pending. No phone claim.

Stage 3 adds one shared horizontal gesture wrapper to playlist/Random and Browse/Search/History/
local/online track rows. Enqueue is disabled during selection/reordering, while existing menus
remain. Physical gesture end commits once beyond 30% of the row width (minimum 56dp); right returns
to rest without dismissing the original. The coloured background includes icon/text and custom
accessibility actions. The unified panel will use the same wrapper for left-side removal.

Checked partially: **5 physical/semantic host gesture tests passed**, 14 s
(`/tmp/protracktor-unified-swipes-final.log`), including duplicate additions, both reveal directions,
partial/reversed/vertical motion, tap retention, selection disabling and accessible equivalents.
Disconnecting only the physical right callback failed its test while semantic actions remained
wired (`/tmp/protracktor-unified-swipe-mutation.log`, 27 s); the callback was restored. A small
movement below touch slop may still count as a normal tap; it never commits a queue gesture.
The two-tab panel replacement/real-shell checks and final full verification remain pending.

Stage 4 replaces the tabs with one occurrence-keyed list, compact Info, source/manual swipes and
one actual-playback highlight. The animated panel uses the Scaffold content bounds above the same
dock; underlying screen semantics are hidden while it is open. Snackbars stay physically reachable.
Source actions carry the context generation as well as the occurrence ID, rejecting stale rows.

Checked partially: **31 tests / 4 suites passed**, zero failures/errors/skips, in 70 s
(`/tmp/protracktor-unified-panel.log`). These include real App/VM/controller host gestures,
duplicate/origin preservation, source omissions without playlist edits, Info/title learning,
initial scroll, dock interaction and C110 navigation. Removing the bottom inset failed the dock
geometry regression (`/tmp/protracktor-unified-dock-mutation.log`, 28 s); the restored positive
test passed in 14 s (`/tmp/protracktor-unified-dock-final.log`). These are host checks, not phone
acceptance. Full gates/lint, final boundary review and the new release APK remain pending.

## Final branch review

Reviewed the original stages `bc426e5`, `91477f2`, `783d8fa`, `3d27868`, `0ce1f6f` against the
changed contract, then third-iteration stages `8e35e05`, `3068f0c`, `ea01a2f`, `d7042b8` and the
final hardening delta. Earlier mixed traversal/tabs are intentionally superseded, not retained
as a hidden parallel implementation. No remaining product question authorizes deferred features.

| Finding | Correction and evidence |
| --- | --- |
| Removing a paused current row started audio | Propagate playback intent through scheduling/source/Random selection. A paused edit selects without decoder/history activity until explicit Play. Regression failed before the correction (`protracktor-unified-paused-before.log`, 15 s); both source/manual paths and playing continuation are covered. |
| Random's old menu prune left the source snapshot/IDs out of sync | Apply the positional deletion to source rows/IDs, not file-based matching; preserve remaining equal occurrences and omissions. Regression failed before correction (`protracktor-unified-boundary-before.log`, 30 s; fail-fast stopped later cases). |
| Linked draft edits could recycle a lazy occurrence ID | Capture source ID allocation on every editor-track mutation, including notification heart, discard/empty/switch. Stale row actions must not act on a different new file. Controller regression failed before correction (`protracktor-unified-draft-id-before.log`, 23 s). |
| Manual row survived Next when Random returned no candidate | Consume on the explicit Random advance, not only after a new file is found. Regression failed before correction (`protracktor-unified-random-consumption-before.log`, 16 s). |
| Pruning the sounding Random row lent its playing highlight to the new parked cursor | Match actual source occurrence identity, not just the live cursor; share that pure projection across the unified, Random and playlist views. The new regression failed before correction (`protracktor-unified-highlight-before.log`, 19 s), including a retained equal file. Native playback remains untouched by the existing Random record-prune action. |
| Progress reads rebuilt unchanged ID maps | Same-order/same-track-list fast paths retain the existing snapshot; changed rows still remap occurrences. |
| Superseded labels/comments/docs obscured the new contract | Remove unused two-tab/header/button strings, rename obsolete replay terminology, mark old reports historical and update architecture, questions, roadmap and phone checklist. |

The omission-skip Random loop also checks generation after a suspended refill, not only epoch.
All probe logs above are under `/tmp/`; final artifact evidence will retain copies. Full verification
and the new APK are not yet claimed by this review checkpoint.

After hardening, **125 tests / 7 suites passed**, zero failures/errors/skips, in 56 s
(`/tmp/protracktor-unified-review-restored.log`): scheduler/controller/state, playlist session/heart
and the real shell/panel tests. The preceding review run failed an old whole-state expectation
that required a stale backing playback snapshot after Discard; its expected backing order now
matches the already-effective live source while retaining occurrence IDs. No audio/storage
expectation was relaxed. An earlier boundary-focused run passed 62 tests / 5 suites in 53 s
before the linked-draft/Random-exhaustion additions. Full forced gates/lint and release APK follow.

The first forced gate passed **1027 tests / 134 suites**, zero failures/errors/skips, all companion
stages and lint (0 errors) in 514 s. It preceded the final source-highlight guard; its log is
`/tmp/protracktor-unified-full-gate-pre-highlight.log`, not final evidence for that guard.
The final guard has its own failing regression and requires another full gate before APK handoff.
Restored scheduler/controller/panel verification passed **58 tests / 4 suites**, zero failures,
errors or skips, in 46 s (`/tmp/protracktor-unified-highlight-final.log`).

## Final verification and handoff

Clean application source **`613b154`**, versionName **1.2.0**, commit-count versionCode **1245**.
The final forced `./scripts/test-protracktor.sh --really` completed in **511 s**:

- **1028 tests / 134 suites**, zero failures/errors/skips; test task actually executed.
- Store metadata, runner/release-flow fixtures, **581 web-page checks**, **49 engine checks**,
  **8 SAP checks**, real web-store upgrade, **30 native audio checks**, **141 icon paths**,
  PWA and **11 server checks** all passed.
- Lint **0 errors / 72 warnings**. Compared issue ID/message/file records with APK 1239's archived
  report: all 72 warnings are unchanged (line positions are not part of that comparison).

Minified release APK, automatically signed with the same public test certificate:
[protracktor-release-1.2.0-1245-613b154-20261020-182237.apk](../dist/protracktor-release-1.2.0-1245-613b154-20261020-182237.apk).
Built in **142.8 s** (bounded runner), **11,878,271 bytes**.
SHA-256: `08ae9164d7ec925944470f8da21ca1b50ccffba8c2c2711c3e5d2056cfb74b0c`.

Checked package `com.przunk.protracktor`, version/code, APK v2 signature with certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, absent debuggable flag,
executed R8/nonempty mapping, minSdk **29**, targetSdk **36** and actual engine libraries for
**arm64-v8a / armeabi-v7a / x86_64**. APK contents were listed using the JDK jar tool because
unzip is unavailable on this host. No publication key/AAB/store operation was used.

Retained evidence under ignored `dist/unified-queue-verification/613b154/`: JUnit XML, lint HTML/XML,
full gate/build/companion logs, R8 mapping, manifest/badging, public signature and APK contents;
`probes/` retains focused positive/negative runs and the pre-final-highlight gate separately.
Final documentation destinations were checked: **161 existing local links, none missing**.
Application/native/web/script sources still equal the clean APK source after this closeout.

**Checked partially:** domain/controller/real-host UI behaviour, full host gates/lint and release
artifact properties. **Unchecked:** real device audio, gesture feel/layout/TalkBack, service/
notification, Bluetooth and usability. [New phone checklist](TESTING.md#unified-queue--third-iteration-phone-checks-pending).
Source-switch precedence and paused-removal choices remain provisional questions for evaluation.

Separate commits on `feature/manual-playback-queue`; no merge/push/tag/release change.
Develop remains `d6d3297`, AMP `8e92002`, master `794d8f9`; schema 23 and versionName are unchanged.
C111/C112, AMP integration/permission and web queue are excluded. A later documentation-only
commit increases HEAD's count but does not change this APK's source SHA, versionCode or bytes.
