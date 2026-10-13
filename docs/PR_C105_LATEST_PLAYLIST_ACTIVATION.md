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
