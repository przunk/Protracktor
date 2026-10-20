# Clear queue

Owner decision, 2026-10-20. Follow-up on `feature/manual-playback-queue`; no merge authorized.

## Contract

- Clear removes the source context and every waiting manual occurrence. Saved playlists,
  files, History and bookmarks are not edited. The playlist editor remains available.
- The actual current occurrence keeps its audio, position, metadata, loading/paused state and
  History origin. It becomes a one-shot without a source anchor. Already consumed occurrences
  are not revived. Without a current occurrence the playback list becomes empty.
- New manual requests retain duplicates and FIFO order. Clearing A, then adding B and C gives
  A → B → C → stop; A disappears when B starts. Previous cannot recover the cleared context.
- Shuffle and Repeat settings survive. Manual requests ignore Shuffle; Repeat One still repeats
  the current track. Repeat All cannot resurrect cleared material.
- A new explicit source selection replaces the empty context normally. Merely navigating back
  to Random or a playlist does not restore playback context.
- Clear is a labelled icon action in the queue header. Repeating Clear on an already detached,
  empty plan reports that there is nothing to clear; it does not restart playback.

## Reversible operation

Implementation choice: offer snackbar Undo, following individual row removal. Undo restores the
removed plan, not audio position or the playlist editor's tracks/position. It is valid only for the same
current occurrence, consumption state and traversal generation, with no new pending requests.
A visible but expired Undo explains why it cannot restore. A newer snackbar invalidates it.
Repeat/Shuffle changes remain in effect. Stale row callbacks and asynchronous Random advances
must not reintroduce cleared tracks.

## Implementation and verification

Use an immutable playback-plan snapshot for Clear/Undo. Retain current identity as a manual
one-shot over `QueueSource.None`, leaving native audio and its in-flight open job alone. Advance
the playback intent epoch and Random generation; cancel speculative read-ahead, not actual audio.
Renew any failed-file retry for the retained occurrence. Never restore the snapshot through a
helper that replaces the playlist editor. Its transport mode fields alone mirror live Repeat/
Shuffle when the source relinks, because the active-source getter reads those fields from the
editor. No persistence, schema, dependency, native, web or AMP changes.

Work in stages: domain/controller with regressions; UI/production wiring and interaction tests;
review, forced complete tests/lint and minified test-signed release APK. Device playback,
notification, Bluetooth and gesture feel remain unchecked until owner confirmation.

No permanent/pinned queue entries or Save queue as playlist in this task. C111/C112 remain open.

### Domain/controller checkpoint

The unimplemented Clear/Undo negative probe failed all seven new domain tests (after correcting
two test compilation errors; those are not counted as behavioural evidence). Restored domain,
real-controller/SQLite, scheduler and Random verification passed **75 tests / 4 suites**, zero
failures/errors/skips. Coverage includes paused/playing state preservation, loading-job identity,
failed-file retry, stale Random fill rejection, source callback generations, duplicate FIFO,
consumed-current handling, idle Clear, guarded Undo and Play after manual-only exhaustion.
Review corrected a cancelled Random successor draw retaining its spinner without an open job.
These host checks do not exercise native audio or establish device behaviour.

### UI and review checkpoint

A required callback connects the labelled Clear action through the real shell/VM/controller;
it does not start the service or call Play. Help explains the operation, EN/PL resources match,
and detached playback uses the compact Manual queue heading. Existing row gestures/animations
and the dock remain shared rather than being reimplemented.

Combined verification passed **135 tests / 12 suites**, zero failures/errors/skips. Physical
Clear, snackbar Undo, duplicate right swipes, expired/no-op feedback and Polish Help are covered.
Disconnected Clear and missing Random-spinner handling each failed their intended regression.
The 320-dp/double-font Polish layout check initially flagged fractional width rounding; it now
checks actual visible character range, ellipsis and height instead. Deliberately limiting the
button to 60 dp failed that check with a genuinely truncated label; production width is restored.
Full gates and the release artifact follow; physical device behaviour is still unchecked.

Review found that relinking a playlist source after Undo could override live Repeat/Shuffle
with the editor's older modes. Its real-controller regression failed before correction
(`PLAYLIST` expected, `OFF` observed). Undo now mirrors only those mode fields into the live
editor, preserving its tracks, position, metadata and dirty state. The first full gate was
deliberately interrupted at 200 s to add this regression; it is not counted as a passing gate.
The restored combined run passed **136 tests / 12 suites**, zero failures/errors/skips.

A second review case restores Random before its first pick: Undo must not start audio, but a
later explicit Play must draw rather than silently return from an empty source. Its controller
regression failed before correction. Play now delegates this case to the existing file-level
Next path, preserving source ownership and normal empty-catalogue feedback. The second full
gate was deliberately interrupted at 299 s for this addition; neither interrupted run is a pass.
The final restored release-candidate run passed **137 tests / 12 suites**, zero failures/errors/skips.
