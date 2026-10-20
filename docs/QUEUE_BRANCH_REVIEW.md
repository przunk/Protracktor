# Manual playback queue branch audit

Requested 2026-10-21 after C116: review all code changed on `feature/manual-playback-queue`
against `develop`, correct worthwhile findings and build one final release APK. No merge,
push, schema, dependency or release-version change. C111/C112 and AMP remain deferred.

## Scope and method

Baseline `develop` is `d6d3297`; audit started after C116 commit `95470b9`. Review the complete
branch delta and relevant surrounding paths, not only the latest presentation correction:

- Pure source/schedule/plan state, occurrence identity, shuffle/repeat and omission traversal.
- Actual playback versus the selected editor, playlist saves/heart, History origin, retry,
  Random pruning/read-ahead, paused edits, Clear and stale Undo/async ownership.
- Every changed UI/VM callback, shared gestures and playlist/manual grips, source highlighting,
  dock/panel bounds, follow/animation interaction, labels and EN/PL resources.
- Changed regression fixtures, physical-gesture selectors, rendering evidence and retained
  contract/questions. Earlier passing APKs remain historical evidence, not phone acceptance.

## Findings and disposition

1. **C116, corrected before this audit:** the final automatic-follow snap resets lazy-item
   animation bookkeeping. Queue appearance fades briefly hide surviving rows. Appearance fading
   is disabled without removing placement/departure; [failing pixels and 18-case checkpoint](QUEUE_AUTO_FOLLOW.md).
2. **C117 — omission traversal bound:** `ActiveQueue.finiteMove` limits a search to source size plus one,
   but shuffled backward/forward history can be longer after repeated in-list taps. Omitting a
   repeatedly visited row can incorrectly disable Next/Previous despite a surviving destination.
   Both real pure-model regressions failed before correction (24.4 s). The traversal budget now
   includes retained history plus one fresh lap, owned by PlayQueue rather than guessing in
   ActiveQueue. Shuffled order and omissions remain intact; no new transport policy.
3. **Help wording:** the earlier gesture copy identifies manual requests only by indentation.
   C113 intentionally removes that indentation in a standalone queue. Describe manual requests
   and their grips directly rather than implying the inset always exists. Corrected in EN/PL.

## Complete code-delta inspection

All 22 changed production Kotlin files and both resource files were inspected, including their
integration with unchanged service/notification/persistence paths. Changed test code was reviewed
for its actual entry point and assertion, not just whether it returned a green result.

| Area / changed files | Checks and disposition |
| --- | --- |
| `ActiveQueue`, `PlayQueue`, `PlaybackSchedule`, `PlaybackPlan` | One-shot FIFO/current lifetime, source-only Previous, equal-file occurrence IDs, shuffle order, repeat, omissions, bounded moves and guarded Clear/Undo. Corrected the history-length traversal bound; no new product policy. |
| `PlayerUiState`, `PlaylistSessionState`, `PlaylistHeart` | Source snapshot versus selected editable playlist, monotonic remapping across draft edits, actual current track for heart/metadata, saved-state ownership and relinking modes. Manual rows/omissions do not enter saved playlists. No further correction identified. |
| `PlaybackController`, `PlayerViewModel`, `HistoryRecording` | Every new command and changed call site, file/subsong completion, paused removal, epoch/occurrence retry guards, Random prune/fill ownership, read-ahead, source replacement, actual History origin and Clear retaining an open job. Shared transport still serves dock/notification/headset; native engine untouched. Host tests cannot prove real audio continuity. |
| `QueueScreen`, `QueueHelp`, `QueueActions` | Generation-bound source actions, stable occurrence keys, full-width hit/highlight, context-only inset, readable labels, Info, Undo/Help discovery and auto-follow/animation interaction. C116 correction retained; Help copy reconciled. |
| `QueueSwipeIntent`, `QueueSwipeRow`, `ManualQueueReorder` | Direction/distance gate, vertical priority, cancellation/second pointer, single release commit, live callbacks, accessibility equivalents, grip ownership, pending-only bounds and edge scrolling. No threshold or boundary changes needed. |
| `PlaylistScreen` | Outer drag layer, stable wrapper/handler, numeric viewport pin after recomposition, batched moves, selection, menu/Info and draft-only editing. C115 regressions retained; no extra source reorder policy. |
| `BrowseScreen`, `RandomScreen` | Local/online/Search/History/Random menu and right-swipe wiring, History request origin, no folder/header enqueue, actual versus parked highlights, preserved navigation and remembered Random. No further correction identified. |
| `ProtracktorApp`, `PlayerDock`, `PlayerIcons` | One panel above the existing dock, snackbar reachability, hidden-underlay semantics, Back/Now Playing/Browse transitions, service entry and transport capabilities. Existing queue-add vector remains local. C111/C112 are not silently included. |
| EN/PL `strings.xml` | Same keys/placeholders and direct action labels; manual-grip wording now works with or without source indentation. Full translation/format checks remain part of the final gate. |
| Changed tests and helpers | Real shell/VM/controller and SQLite lifetime, physical gesture selectors, hardware-rendered pixels, negative probes and legacy fixture adaptation at the test boundary. Preset unavailable content URIs do not test native audio. Added a hold-through-progress-recomposition guard; its final result is recorded below. |

No additional actionable defect was identified in this code delta. This is code inspection plus
host regression evidence, not proof of perfect code or phone acceptance. Large-list release
comfort, actual vibration, accessibility, audio/service/notification/Bluetooth and long-term use
remain device checks. The experiment stays session-only; no queue persistence or source editing
is added. Final focused/full-gate results and the APK are pending.

## Verification checkpoints

The first broad focused run stopped after 90.2 s on the new Info-hold fixture: the dialog opened,
making its unqualified root selector ambiguous on pointer release. This is a test-harness error,
not an application regression or a passing gate. Release now targets the original playback panel.
Disconnecting only the Info callback then failed the intended visible-dialog assertion in
31.0 s. The callback is restored before positive verification. No native/phone audio was run.

Restored combined verification passed **218 tests / 14 suites**, zero failures/errors/skips,
in **82.5 s**. Every selected class produced results: source/playlist/schedule/plan models,
real controller/SQLite, state/heart/save semantics, Queue following/presentation/Clear, saved
playlist grips, manual grips and shared swipe gestures. The test task executed, rather than
returning cached results. Both omission regressions and the progress-recomposition Info guard
passed. Fault injections are absent from the final diff. Full forced gates/lint and APK follow.
