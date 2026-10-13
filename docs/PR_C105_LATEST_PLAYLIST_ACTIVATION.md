# C105 — latest playlist activation wins

## Report and analysis

Code-review finding recorded on 2026-10-14, not an app-user report. The owner selected its repair
after reviewing the session findings. Branch `fix/latest-playlist-activation`, based on develop
`ad18da6`. [Original review](review-2026-10-14.md).

Each activation independently saves settings, stops playback, changes the persistence target and
awaits destination tracks/list data. An older completion can replace a newer selection. A suspended
list read inside the state update can also publish stale activation data. The resulting displayed
playlist and persistence target disagree, so C103 correctly refuses subsequent writes.

## Selected correction

- The most recent requested activation owns publication and follow-up effects, including when an
  older settings, track or list operation finishes later.
- Keep the published source and persistence target aligned until destination data is ready.
  Preserve C103's rejection of new track writes during activation; already captured writes retain
  their original owner.
- Serialize settings writes so an older in-flight write cannot finish after the latest saved state.
- A failed current activation keeps the source target usable. Superseded failures must not disturb
  the current playlist. Cancellation remains cancellation; this does not redesign general error UI.
- Preserve normal single-switch playback stopping, edit abandonment, shuffle/repeat, restoration,
  schema and notification full-save behaviour. No search-race fix is included.

## Regression stage

Six permanent controlled-store checks cover out-of-order track/list completion, a suspended
pre-switch settings save, target alignment during loading, current read failure and superseded
read failure. These exercise the actual session/state pair with existing fixture ports. Results
before correction: 47 session checks executed, all six new checks failed with assertions; existing
41 checks passed, zero errors/skips. Gradle completed in 91 s using the existing JDK and temporary
per-class test-worker isolation. Log/XML are retained in ignored `dist/c105-regression-before*`.
No application correction or phone result is claimed at this checkpoint.

## Implementation stage

Activation ownership is numbered when `switchToPlaylist` is called, before launching work.
Guards follow every suspended settings/track/list read and playback stopping; only the newest
request publishes or schedules persistence/metadata follow-up. The destination list is read before
the state update, so publication is a non-suspending, pure copy of the latest player state.
The persistence target changes directly before that publication. New track snapshots remain
blocked during activation, and become available before publication observers/metadata follow-up
can save the destination. Previously captured C103 writes keep their original identity/revision.

Settings writes share a separate mutex and capture current state inside it. Superseded activation
requests are checked inside that lock before writing. Current exceptions retain their previous
propagation policy, superseded exceptions do not affect the latest request, and cancellation is
rethrown. A failed current read leaves the source's target aligned and releases the snapshot gate.

Two historical tests now expect the source persistence ID until publication, not an early target
assignment. This changes ownership timing deliberately without weakening their row-write checks.
The first implementation run executed 65 checks with one failing historical early-ID assertion;
the six new correctness regressions passed. That checkpoint is not final verification. A seventh
check covers an immediate destination Save during metadata follow-up. The old state-adapter test
retains suspended-read retry coverage for Reload/Empty; activation no longer suspends inside CAS.

**Negative proof:** forcing all request generations to appear current and keeping the destination
write gate closed until final cleanup fails five of 48 session checks, all by assertion, zero
errors/skips. This includes the new follow-up Save check. Both production files were restored and
SHA-256 compared. Mutation log/XML: ignored `dist/c105-ownership-mutation*`; 95 s Gradle run.
**Restored focused evidence:** 66 checks passed in three suites, zero failures/errors/skips:
48 session, 10 heart and eight SDK 29/33 presentation checks. Gradle completed in 101 s;
log/XML retained in ignored `dist/c105-focused-restored*`. Full tests/lint and APK remain pending.

## Phone checklist

Use disposable playlists or exported backups; do not uninstall or clear application data.

1. Prepare three playlists A/B/C with distinct tracks. From A, choose B then C quickly, reopening
   the playlist selector if needed. After loading settles, C's name and rows must remain visible.
   Repeat C/A/B and alternate between a small and larger playlist. Do not infer a race was hit
   merely because the final screen looks correct; host checks deliberately suspend the reads.
2. Edit the final playlist, use Save, switch away/back and restart normally. Only that playlist
   should contain the edit; the other two should remain unchanged. Repeat Discard and normal
   single switching with shuffle/repeat enabled.
3. After a settled switch, play a Browse/Random track and press the notification heart twice
   rapidly. It should save to the final selected playlist without duplication or moving controls.
   Confirm persistence after restart; prior working edits are intentionally included by B48.
4. Check playback stopping on normal playlist switching, PL/EN, create/rename/delete on disposable
   lists, and the accepted Bluetooth/stereo scenarios. Record installed build and actual outcomes.

Phone behaviour and long-term data safety remain unverified until the owner supplies outcomes.
The source-level fix does not automatically resolve the separate stale-search finding.
