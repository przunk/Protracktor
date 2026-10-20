# Unified playback list — third iteration

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
  Undo restores a row without rewinding audio. Context Undo is generation-bound and must not
  revive an old source after replacement. Manual Undo remains request-specific.

## Gestures and presentation

| Surface | Left swipe | Right swipe | Tap | Trailing control |
| --- | --- | --- | --- | --- |
| Unified playback list, either origin | Remove occurrence | Append a manual copy | Play occurrence | Info |
| Playlist, Random, Browse/Search/History/local/online tracks | No new removal gesture | Append a manual copy | Existing action | Existing menu |

Right swipe returns the original row to rest and commits once per gesture. Partial/reversed drags
must not enqueue. Left swipe reveals red with remove icon/text; right reveals green with queue-add
icon/text. Colour is not the sole cue. Retain Undo and accessible custom actions/menu equivalents;
the new compact Info control is an owner-selected exception to visible icon-plus-label controls.
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
- Session-only queue/omissions, no persistence or reorder/Clear in this iteration.
- Existing Repeat One/subsong behaviour remains; consumed manual entries do not join source laps.

All new device behaviour is **unchecked** until owner testing. The existing positive phone report
motivated changed requirements; it is not acceptance of this unimplemented iteration.

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
