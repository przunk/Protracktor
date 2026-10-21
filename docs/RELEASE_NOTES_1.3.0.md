# Protracktor 1.3.0 — prepared release

Release preparation completed on **2026-10-21** with the interactive release procedure.
Scope: `v1.2.0..v1.3.0`. The agent subsequently inspected the existing artifact, Git state and
verification records; the agent did not build/sign another AAB, upload it or publish a release.

| State | Evidence |
| --- | --- |
| AAB prepared | `dist/protracktor-1.3.0-1269.aab`, **22,154,124 bytes** |
| Release source | `d456fa77766c9844e8802395560650e679bbb76f`, annotated `v1.3.0`, also master |
| Version identity | **1.3.0 (1269)**; the code equals the tagged source's commit count |
| Git publication | Not confirmed; local release and evidence commits exist |
| Play upload/acceptance | Not confirmed |
| Play publication/production access | Not confirmed |

SHA-256: `3f63164fb1256160696fd374615e7bb21d6be41f029997177c2860eccafec8db`.
The unchanged script-generated [release ledger](../store/play-console/releases.md) contains the
artifact metadata and public upload-certificate fingerprint. Its uploaded-notes wording identifies
the tagged text, not independent confirmation of an upload. The copy-ready
[English](../store/listing/en-US/release-notes/default.txt) and
[Polish](../store/listing/pl-PL/release-notes/default.txt) notes match `v1.3.0`.

## Changes since 1.2.0

### Playback context and manual queue

- Playback is independent of the currently visible playlist, search, History or folder. Browsing
  or selecting a different editor playlist does not itself replace the playing source; explicitly
  playing a row does. The dock opens a combined source/manual Queue view.
- Manual requests play in order before source continuation, including duplicate files. They are
  distinct occurrences, not permanent playlist edits; consumed requests do not become backward
  playback history. Previous from a manual request returns to the parked source. Existing
  Repeat One/subsong behaviour is retained; source shuffle does not shuffle manual requests.
- Right swipe adds a request from track lists. Within Queue, left swipe removes a session row
  with Undo, tap plays and long press opens Information with haptic feedback. Waiting manual
  requests can be reordered only among themselves, including accessible move actions.
- Clear detaches the source continuation and waiting requests while retaining the actual track
  without restarting audio. New requests can form a manual-only queue. Undo is guarded against
  newer playback changes and does not restore an old playlist editor snapshot.
- Queue stays above the operable dock, follows the current track and animates departures without
  replaying appearance fades during automatic scroll snaps. Saved playlists are not modified by
  session-only Queue removals/Clear. Manual requests and omissions are not persisted after process
  exit. [Current contract](QUEUE_UNIFIED_LIST.md), [reordering](QUEUE_MANUAL_REORDER.md),
  [Clear](QUEUE_CLEAR.md), [acceptance and audit](QUEUE_BRANCH_REVIEW.md).

### Random, navigation and interaction corrections

- Reopening Random shows the remembered list without taking playback from another source.
  Double-tapping the animated dice starts a fresh list; Next retains its transport meaning.
- Generic Browse opens its root rather than the playing History/Search/folder. Explicit author
  destinations and source playback remain separate from navigation.
- C113–C117 correct standalone Queue alignment, full-width playing-row highlighting, playlist
  drag ownership/layering/viewport behaviour, automatic-follow flashes and traversal across
  omitted repeated shuffle history. Overall phone acceptance is recorded, not individual
  confirmation of every timing/accessibility scenario. [Row corrections](QUEUE_ROW_REGRESSIONS.md),
  [automatic following](QUEUE_AUTO_FOLLOW.md), [remaining device checks](TESTING.md).

### Browse and download stability

- Reject obsolete catalogue/local-folder reads by navigation visit as well as route; publish Back
  transitions without overwriting newer/reentrant results.
- Retire old read-ahead even when the replacement has no missing candidates; late completions
  cannot repopulate retired memory. Serialize cache transactions, use unique staging files and
  clean up a failed write's own staging file.
- Download-failure text no longer assumes that every failure is a lost internet connection.
  This is not a claim that the reported Same Game SPC download failure is resolved.
  [Architecture](ARCHITECTURE.md) and [stability scope](PLAN_BROWSE_STABILITY.md).

## Verification and limits

The existing release-run unit log records an executed `:app:testDebugUnitTest`, **31 executed
Gradle tasks** and success in **365.3 s**. The corresponding fresh JUnit XML contains
**1096 tests / 142 suites**, zero failures/errors/skips. These are inspected results from the
interactive release run, not a new test run by the agent.

Lint completed successfully in **7.9 s**, reusing Gradle's up-to-date analysis/report tasks.
The retained report has **0 errors / 72 warnings** and predates this invocation; no fresh lint
analysis is claimed. The web engine build completed in **49.7 s**, and the inspected runner,
release-flow, page/engine/SAP, web upgrade, native audio, icon, PWA and server logs all end with
exit zero. Bundle Gradle output records executed R8 and bundle tasks and success in **94 s**.

Artifact size/hash match the ledger and release-terminal result. Embedded version-control metadata
names the tagged commit; all three engine ABIs are present. The public upload certificate matches
1.2.0: `E2:02:EF:AD:A7:48:70:F0:8D:A3:63:28:D4:E0:74:00:04:93:EC:9E:FF:AA:43:39:95:84:13:60:D9:CC:A9:9D`.
This is not the certificate with which Google signs delivered APKs.

`jarsigner` reports successful JAR verification with warnings about certificate trust/self-signing,
missing timestamp, ZIP attributes and differing JarFile/JarInputStream signature interpretation.
The retained 1.2.0 verification log contains the same warning categories. This comparison does
not resolve the warnings or establish Play acceptance. Verification logs, JUnit XML and the reused
lint report are retained locally in ignored `dist/release-1.3.0-verification/`.
No private key or keystore contents were read by the agent.

Tagged app/native/web/script sources equal the accepted Queue APK source `1a1503f`; the Gradle
change from that candidate is only `versionName` 1.2.0 → 1.3.0. Schema stays **23**. Master equals
the tag; the following evidence commit changes only the release ledger. Later documentation
commits do not change the AAB's source identity or versionCode.

Overall Queue phone acceptance predates this release. Installation/testing of a Play-generated
1.3.0 build, upload, rollout and production access still need separate confirmation. AMP remains
outside this release, as do the tutorial and deferred C111/C112 layout refinements. No new APK,
Git push, tag movement or store action was performed during documentation verification.
