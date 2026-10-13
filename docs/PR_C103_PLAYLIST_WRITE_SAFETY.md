# C103 — playlist-write safety

Date: 2026-10-13. Branch: `fix/playlist-write-safety`; base: `develop` at `01e5bc1`.
Local PR/handoff draft only: no GitHub PR, push or merge performed.

## Report and analysis

Source: code review and controlled-store tests during the 2026-10-04 architecture extraction;
the owner selected a separate safety fix on 2026-10-13. Not an independent tester report.

Playlist activation changes the persistence ID before destination tracks finish loading. A
delayed metadata write sampled that ID alongside the still-visible source queue, risking
replacement of the destination. Save captured tracks only after its coroutine began and cleared
dirty state unconditionally after writing: later edits could remain visible but appear saved.
The extraction intentionally preserved these behaviours rather than silently fixing them.

## Fix

- Capture a detached list and its published playlist ID synchronously for each track request.
  Reject un-restored state and the interval where persistence/publication IDs disagree.
- Serialize metadata, explicit Save and automatic-add writes through the session's mutex.
  Save/automatic additions cancel older pending metadata for that target.
- Clear dirty state only if the current playlist and its track contents match the completed save;
  unrelated transport/scanning updates do not prevent a valid completion.
- Revert/Delete invalidate obsolete request revisions. Keep reset/read publication and final
  invalidation under the write lock; reject deleted targets before destination publication.
  Guard Revert completion against a different active playlist.

The automatic-add controller path now uses this same production entry point. Existing delays
(400 ms settings, 1500 ms metadata), metadata autosave policy, editing rules, playlist activation
order, schema 23, app version and native/audio ownership are unchanged. The mutex is not a claim
of global serialization across every `LibraryStore` caller.

## Regression evidence

Stage 1, `016321a`: safety assertions against unchanged production executed 29 session tests;
11 failed. Three historical characterization expectations became safety requirements.
Stage 2 adds snapshot/writer/lifecycle implementation and further lifecycle tests. Three reset,
Revert and post-deletion assertions first failed before their corresponding guards.

Final focused session run: 38 tests, zero failures/errors/skips. Controlled mutations removing
snapshot writes, serialization, revision invalidation, deleted-target protection and guarded
Save/Revert completion executed 38 tests and failed 13. Mutated sources were restored; both
SHA-256 values matched the checked pre-mutation sources.

Full forced gate (`scripts/test-protracktor.sh --really`): **768 unit tests**, zero failures,
errors or skips; lint completed with **0 errors, 68 warnings**. Fresh results cover 102 suites;
the test task executed, not UP-TO-DATE/FROM-CACHE. Also passed: PL/EN strings, store limits,
581 page checks, 49 existing WASM engine checks, 8 SAP measurement checks, browser database
upgrade, 137 icon paths, PWA checks and 11 loopback server checks. Gate duration: 310 s.
Host evidence uses the project's existing JDK and Gradle,
an isolated build directory and the production session/state pair with controlled store suspension.
No new Java, dependency, schema or shared-workspace configuration was introduced.
Logs, final JUnit XML and lint reports are retained in ignored `dist/playlist-write-safety/`.
Changed documentation was checked for whitespace and 41 local file-link targets; all exist.

## Handoff and limits

Checked host assertions and the full gate do not establish real SQLite timing or phone behaviour.
No minified APK, device reproduction, long-term acceptance or release/publication is claimed.
The owner deferred the APK until the Bluetooth fix; leave the branch unmerged meanwhile.
The [phone checklist](TESTING.md#c103-playlist-write-safety--phone-retest-pending) uses disposable
playlists and backups, not clearing application data. C102 Bluetooth symptoms were recorded
separately; they are not diagnosed or fixed by this change.
