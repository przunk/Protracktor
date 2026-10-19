# Playback context and manual queue — implementation contract

Updated: 2026-10-19. Owner/colleague feedback, analysis audit and provisional implementation decisions.
This supersedes the pre-implementation analysis retained in Git at `a8539a2`. The owner authorized
a separate implementation branch after accepting C110, and delegated unresolved choices to the
implementer. Questions below are for evaluation after implementation, not prerequisites.
**Provisional choices are agent decisions, not claims that the owner approved each behaviour.**
The experiment may still be rejected.

## Baseline, scope and audit

The playback context was accepted and merged as `7cab210`. C110 generic Browse entry was accepted
and merged as `7e7f242`, whose tree equals accepted branch `57a9d65` and whose code equals tested
`fa09e7a`. Its 963 tests/lint/APK are historical evidence, not verification of this new feature.
[Existing artifact and acceptance](PR_C110_BROWSE_ROOT_NAVIGATION.md): APK 1.2.0 (1231).

C111 inconsistent Add music/Online catalogues padding is recorded separately in
[Status](STATUS.md#c111-add-music-and-online-catalogues-have-inconsistent-padding); do not fix it
incidentally here. AMP stays at `8e92002` on `feature/amp-catalogue`, with permission/integration
deferred. No AMP rebase, playback or merge is included; retained-data guards remain intact.

VersionName stays 1.2.0 until the owner releases. APK code is the commit count, with source SHA,
timestamp and test signing. No old code-1221 exception, data-clearing downgrade, new dependency,
schema/migration, decoder, web queue, release tag, master movement, push or automatic merge.
Pending/traversal state is session memory; existing paused selected-playlist restoration remains.

| Earlier gap, doubt or inconsistency | Resolution for this experiment |
| --- | --- |
| Stale text said C110 was unimplemented and code unauthorized | Record accepted C110 and the subsequent independent implementation instruction. |
| Queue meant both source and manual additions | Define context, pending requests, actual occurrence and transport record separately. |
| Forward replay conflicted with unconditional play-next insertion | Pending FIFO outranks recorded forward replay; retain the forward suffix instead of truncating it. |
| Repeat/subsong boundaries were undefined | Existing Repeat One/subsong policy first; at file boundary route pending, recorded forward, then context. |
| Context cursor was confused with actual audio | Expose actual occurrence independently; never falsely animate a suspended source row. |
| Replaying snapshots could restore deleted playlist contents or old modes | Restore continuation deliberately, remap against the current source collection, retain current modes; never restore an editor draft. |
| File ID could not distinguish duplicates, retry and Undo | Stable monotonic session occurrence/request IDs, not file IDs or row indexes. |
| Idle/failure/context replacement/reroll/lifetime were open | Explicit transition table below; preserve pending requests, retain manual failure for retry, no new persistence. |
| History could inherit the parked context's origin | Capture origin per request; actual occurrence owns History/listened bookkeeping. |
| Gesture/transport availability/Undo lacked an integration contract | Shared scheduler; pending-only swipe/removal and occurrence-specific Undo. |
| Test matrix accidentally selected Clear/reordering | Both are excluded in v1; verify selected operations only. |

## Terms and invariants

| State | Meaning |
| --- | --- |
| Context | Source identity, collection, cursor and continuation order: playlist, Search, History, online/local folder, Random or external file |
| Pending queue | Ordered manually requested file occurrences not yet selected for loading |
| Actual occurrence | The file loading/playing/paused/failed now, supplied by context or a manual request |
| Transport record | Session-local backward/forward traversal of selected occurrences, not the persistent History screen |

Enqueue does not start/resume audio, change context, save a playlist, record a listen or mark a
file heard. A request leaves pending when selected for loading. A failure is not a listen.
Duplicate files are valid distinct requests. Removal never deletes source data or physical files.

Source `A -> B -> C -> D`, B current, X/Y/Z pending: play `B -> X -> Y -> Z -> C -> D`.
Advance context once after the manual block, not once per request or back to B. Preserve source
shuffle permutation, Random heard boundary/cursor and valid hidden future. Enqueue never rerolls.

## Transport transition table

These are file-level rules, after the established subsong layer. Screen, notification, headset
and natural completion must enter the same scheduler.

| Event | Provisional outcome |
| --- | --- |
| Enqueue while playing/paused/loading | Append after older pending requests; do not disturb actual playback. |
| File Next with pending | Select oldest request and insert after current transport position; retain the recorded forward suffix. |
| File Next, no pending, recorded forward exists | Replay that occurrence without dequeuing it again. Restore its context anchor, not a saved playlist draft. |
| File Next with neither | Ask existing context for next; disable Next if none. |
| File Previous within transport record | Replay previous occurrence; retain forward record and pending requests. |
| Previous at record boundary | Use that occurrence's source anchor and existing adjacent-row/shuffle-history/Random-heard rules; prepend result, preserve forward traversal. |
| Natural completion under Repeat One | Repeat actual current subsong/file, including manual; pending requests wait. |
| Natural file completion otherwise | Pending, recorded forward, context completion; stop at total exhaustion. |
| Explicit Play on a pending row | Select only that occurrence now; leave earlier pending requests and recorded forward entries intact. |
| Explicit Play on any source/context row | Replace context/reset mixed traversal, preserve pending. Do not silently requeue an interrupted manual occurrence. |
| Random reroll | Explicit context replacement as above; pending survives. |
| Navigation/filter changes only | Do not replace context or pending. C110 Browse still opens ROOT; Random filter changes replace eligible speculation only. |
| Remove pending | Remove exactly its occurrence ID; do not touch actual track, source, position or equal-file requests. |
| Undo pending removal | Restore before surviving next neighbour, otherwise after surviving previous neighbour, otherwise bounded original index. Never duplicate an already restored ID. |
| Enqueue from idle | Stay idle; explicit Play/Next selects first pending before default source. |
| Pause/Stop during loading | Retire load intent, retain actual occurrence for explicit resume/retry; no delayed takeover. |
| Manual read/open failure | Visible failed actual occurrence, explicit Play retries it, Next can skip it. Do not apply Random auto-skip because the parked context is Random. |
| Process death | Lose transient pending/traversal; retain existing paused selected-playlist restoration. |

Example: record `B, X, Y, C`, cursor X, pending P/Q. Next yields P, Q, Y, C:
`B, X, P, Q, Y, C`. Explicit requests outrank forward replay without losing it.
Without intervening requests, Previous/Next is reversible. Replayed manual occurrences do not
re-enter pending. Restoring source anchors keeps current shuffle/repeat and remaps against current
source tracks. Source edits must not resurrect editor contents. Heard references remain replayable;
missing files receive normal errors. Removed cursors follow existing PlayQueue remapping policy.
Selecting another editor playlist alone changes neither context nor pending.

## Shuffle, repeat and subsongs

Shuffle never changes manual FIFO; it governs context continuation. A mode toggle during a
manual occurrence updates continuation, not pending or the sounding file.
Repeat Off drains pending then remaining context. Repeat All repeats context, not consumed manual
requests; an empty/external context does not manufacture a manual loop. Repeat One repeats the
actual current tune and takes precedence over requests. Explain why pending waits; file Next escapes.

Keep `SubsongAdvance`: Play all subsongs makes ordinary Next/Previous traverse tunes inside a file
first; long-press file transport skips files. Natural completion finishes subsongs first, except
Repeat One repeats the current subsong. Requests identify files, not individual subsongs. No new
notification/headset gesture is introduced.

## UI and editing

The existing far-right labelled Queue entry opens one full-height playback panel with two
icon-and-label tabs: Context left, Queue right. Fresh entry starts on Context; tab scroll/selection
survives only while that panel remains open. A unified view is explicitly deferred; preserve the
owner's indentation/icon idea for a later iteration, not this implementation.

The common header identifies the actual current file and its manual/context origin. Reuse existing
learned-title/Now Playing presentation rather than add a second metadata parser. Context remains
read-only apart from row playback/info. During manual playback, show a quiet textual context-position
explanation rather than falsely mark its old row as sounding. Actual metadata/progress/notification,
heart and sharing use the manual file. Matching file IDs alone never give a manual copy a source
playing marker. Accessibility must not rely on colour. Replayed context occurrences restore cursor.

Queue displays pending only; its current manual item is in the common header. Empty text explains
that context continuation follows, not that audio has stopped. Pending rows offer Info, labelled
Play and Remove. Tap for Info does not skip FIFO; explicit Play selects only that occurrence.
No pending reorder, bulk edit or Clear queue in v1.

Add to queue is a distinct icon-and-label row action on playlist, Browse/Search/History, Random and
context tracks. Call sites pass the row's History origin rather than infer it from parked playback.
Enqueue gives feedback without switching view or opening the panel. Unavailable retained catalogue
references are explained, not silently accepted as playable.

Pending removal supports end-to-start swipe with Material's default threshold and ordinary vertical
scroll. A labelled Remove is its accessible equivalent. Use the existing short snackbar lifetime
and occurrence-specific Undo, never the playlist's last-removal buffer. No swipe on actual/current,
context or other source rows. Broader B25 gestures and unified editing remain deferred.

## Origin, lifetime and races

History-origin requests retain the rule that replay from History does not move its row to the top.
Other manual origins record normally, including over a History context. Existing five-seconds-or-
natural-completion qualification belongs to the actual file, not the parked context. Title/author
learning preserves occurrence identity, FIFO order and source shuffle.

Use immutable pending/traversal state. Explicit source choice resets traversal but preserves pending;
navigation resets neither. Cache eviction does not erase requests; later fetching is allowed.
Revoked local grants/unavailable retained archives yield explicit errors, not regained permissions.

Each playback selection retires epoch/load intent. Retry captures occurrence identity: a stale
completion/retry cannot take over a newer choice, even for the same file. Prefetch prioritises pending,
then recorded-next/context, without publishing speculative Random rows. Enqueue/removal/Undo/mode
changes refresh read-ahead ownership, not the actual in-flight file. Keep existing Random session/
generation, focus/Bluetooth, listening-generation and source-read guards. One engine/service and
one shared scheduling rule remain authoritative.

## Architecture and implementation stages

Keep [ActiveQueue](../app/src/main/kotlin/com/przunk/protracktor/player/ActiveQueue.kt) as context
and PlayQueue as source ordering; do not cosmetically rename every old queue symbol. Add a pure
immutable scheduler for occurrences, pending, mixed traversal, removal/Undo and typed outcomes.
[PlayerUiState](../app/src/main/kotlin/com/przunk/protracktor/player/PlayerUiState.kt) separates actual
current from context current. Audit current/index/mode consumers: availability, playlist/Random/
Browse markers, metadata, History, notification heart, retry and prefetch.
[PlaybackController](../app/src/main/kotlin/com/przunk/protracktor/player/PlaybackController.kt)
integrates after subsong handling and before source advance. Snapshot restoration restores
continuation, never a stale selected editor draft; asynchronous Random selection remains guarded.

1. Create `feature/manual-playback-queue` from accepted develop after this audit. Implement pure
   FIFO/occurrence/traversal/removal/Undo rules and regression tests.
2. Integrate actual-file projection, shared transports, loading/retry and prefetch. Preserve
   no-manual behaviour, persistence, C97, C110, subsongs and Bluetooth.
3. Wire row actions, two-tab panel, honest markers, pending swipe/Remove/Undo and EN/PL strings.
4. Force full tests/lint, build minified test-signed APK into dist, verify identity/signature and
   record actual evidence/device scenarios. Stop unmerged for owner acceptance.

Each stage has a local commit and an explicit partial verification state. A pure scheduler is not
a working phone feature. Baseline C110 test counts must not be reused as feature evidence.

## Questions retained for post-implementation evaluation

The owner explicitly made these non-blocking. Choices are provisional, not settled product rules.

| Question | Provisional answer and reason |
| --- | --- |
| New request after Previous versus recorded forward? | FIFO first, old forward retained: honour explicit intent without repeating C97-style truncation. |
| Pending after Play/context replacement/reroll? | Preserve; deliberate requests must not silently disappear. |
| Requeue an interrupted manual occurrence? | No; it was already selected, and hidden duplicates are worse than explicit re-enqueue. |
| Repeat One versus waiting requests? | Keep existing Repeat One semantics; explain waiting. |
| Repeat All scope? | Context only; replaying consumed requests forever blocks continuation. |
| Subsongs as queue entries? | Whole files only; existing subsong controls remain. |
| Pending-row immediate Play? | Only that occurrence; preserve earlier pending and forward record. |
| Enqueue from idle starts audio? | No; explicit Play/Next is required. |
| Failed manual load? | Retain current for visible retry/skip; never silent Random skipping. |
| Marker during manual playback? | Quiet textual context position, no false animation; actual file remains in common header/dock. |
| Initial tab? | Context, predictable fresh entry; remember only during that opening. |
| Swipe/Undo details? | End-to-start, Material threshold, existing short snackbar, accessible Remove. |
| Reorder/Clear/bulk/unified view? | Excluded; assess real use before more editing controls. |
| Persist pending/history? | Session memory only; no migration in this experiment. |
| History-origin requests? | Preserve non-reordering, capture origin per request. |
| Replay after source edits? | References may replay without restoring editor contents; missing-file errors apply. |
| Bound transport memory? | Session-local like existing Random record; no arbitrary truncation/new disk data in v1. |
| Naming beyond the two tabs? | Retain discoverable Queue entry, evaluate actual labels on the phone. |

## Verification contract

New guards need negative/mutation evidence. Cover real shell/row gestures and production paths,
not only a helper called below the gesture. Required cases:

- B/X/Y/Z/C FIFO, duplicate occurrences, insertion after Previous, reversible forward replay,
  source-boundary Previous, shuffle/repeat and unchanged no-manual behaviour.
- Paused/idle/loading, pending Play, source replacement/reroll, source edits/remapping, preserved
  Random/filter heard boundary, empty/external context and exhaustion.
- Origin-specific History/listened rules, title enrichment, actual-file heart/progress/notification/
  markers, subsong completion and explicit file transport.
- Pending swipe/Remove/Undo, repeated equal files, consumed neighbours and stale actions;
  no context editing gestures, independent playlist Undo.
- Late fetch/native-open/retry, focus refusal, manual failure over Random, prefetch invalidation
  and no source takeover merely from navigation.
- Existing database/AMP retention, all project gates/lint and exact APK SHA/code/signature/R8/ABIs.

Device audio, notification/service layout, gestures, Bluetooth and usability remain **unchecked**
until owner testing. Record actual executed counts and pending cases in Status/handoff. Merge
still requires owner acceptance.
