# Protracktor 1.2.0 — prepared release

Prepared by the owner on **2026-10-15**; documentation/artifact verification on **2026-10-16**.
Scope: `v1.1.0..v1.2.0`. This is the Android release, not a separate web deployment.

| State | Evidence |
| --- | --- |
| AAB prepared | `dist/protracktor-1.2.0-1207.aab`, 22,033,917 bytes |
| Release source | `794d8f93da401e9cffff9649707b072ce578c169`, annotated `v1.2.0`, also master |
| Artifact version | 1.2.0 (1207); the code equals the tagged source's commit count |
| Git publication | Not confirmed; local release/evidence commits are present |
| Play upload/acceptance | Not confirmed |
| Play publication | Not confirmed; production access is not established |

The immutable, script-generated [release evidence](../store/play-console/releases.md) contains
the SHA-256 and public upload-certificate fingerprint. That entry's reference to uploaded notes
identifies the tagged text, not independent evidence that an upload happened. The copy-ready
[English](../store/listing/en-US/release-notes/default.txt) and
[Polish](../store/listing/pl-PL/release-notes/default.txt) summaries are preserved at `v1.2.0`.

## Implemented user feedback

Reports and preferences were supplied or relayed by the owner. This does not establish an
individual reporter's identity, Play closed-test membership, device/build or itemized retest.
The [status](STATUS.md) and [testing record](TESTING.md) preserve the actual acceptance scope.

### B6 — stereo mix

- Android Settings offers Original, Narrow (50% width) and Mono; the global selection persists.
- Applies to the shared Android output, including MP3, without restarting the decoder; paused
  playback stays paused. Mode transitions are smoothed rather than abrupt.
- Original remains the default. This is channel mixing, not Dolby/headphone virtualization,
  and does not change shared/exported audio or the web player.
- [Contract and evidence](PLAN_STEREO_MIX.md).

### B48 — notification playlist heart

- Add the displayed track to the selected playlist from the notification and save immediately,
  including that playlist's existing working edits. A filled heart confirms membership; it is
  not a remove toggle. Stale targets, overlapping requests and save failures have guarded feedback.
- Following owner trials, reserve the heart's position after Next and keep it visible while
  saving, so controls do not move beneath repeated taps.
- Use a flat monochrome P notification glyph, bold track title and the shared Now Playing subtitle
  policy. Author/platform/year appear when available, rather than a separate formatting copy.
- [Contract and evidence](PLAN_NOTIFICATION_HEART.md).

### B46 — recent searches

- Remember twenty unique submitted queries; show up to three case-insensitive prefix matches.
- Selecting a suggestion immediately searches in the current scope. Typing filters suggestions;
  it does not automatically submit the ordinary catalogue search.
- Owner feedback restored the integrated magnifier and plain anchored text suggestions instead
  of an extra labelled Search button and a row of recent-search buttons.
- [Contract and evidence](PLAN_RECENT_SEARCHES.md).

### B49 / C107 — History search and entry position

- Filter all History while typing, with a 200 ms debounce and case-insensitive literal matching
  against title, author and filename. Paging/counts and empty/error states follow the query.
- Filtering does not modify History order or the playback queue. Each new visit starts at the
  top; page positions within the same visit remain usable.
- The entry-position correction follows the owner's report; live search is a recorded user wish.
- [Search plan](PLAN_HISTORY_SEARCH.md), [entry-position correction](PR_C107_HISTORY_ENTRY_SCROLL.md).

### C102 — Bluetooth output recovery

- Recover a disconnected native output by reopening the output rather than replacing the decoder.
  Late recovery cannot override Pause or resurrect an obsolete track.
- The owner confirmed headphones: connecting while playing transfers sound; disconnecting pauses;
  connecting/disconnecting while paused stays silent. Car-specific and long-term scenarios were
  not independently confirmed. Reopen failure gives feedback instead of pretending to keep playing.
- [Analysis and trial evidence](PR_C102_BLUETOOTH_OUTPUT_RECOVERY.md).

## Code-review safeguards, not independent user reports

- **C103 — playlist writes:** bind a write to a detached playlist snapshot, serialize writes,
  and prevent late save/revert/delete completions from clearing newer work or restoring stale data.
  [Evidence](PR_C103_PLAYLIST_WRITE_SAFETY.md).
- **C105 — playlist activation:** only the latest requested activation may publish the destination
  and persistence state. [Evidence](PR_C105_LATEST_PLAYLIST_ACTIVATION.md).
- **C106 — search ownership:** obsolete searches cannot publish into a new context or clear another
  request's loading state. [Evidence](PR_C106_SEARCH_REQUEST_OWNERSHIP.md).
- **C108 — newer database:** refuse an incompatible schema without deleting user data, with startup/
  service checks and retry/close feedback. Schema stays 23. Host SQLite fixtures are not proof of
  exercising a future-schema refusal on a phone. [Evidence](PR_C108_PRESERVE_NEWER_DATABASE.md).

## Release tooling and version identity

The ordinary gate tracks per-class test isolation, stage deadlines, elapsed notices and scoped
process cleanup. Successful details remain in complete log files; failures show a bounded tail.
The shared-worker Compose idle failure is contained by isolation, not fully diagnosed.
[Runner analysis](PR_RELEASE_TEST_RUNNER.md), [build/release procedure](BUILD.md).

`versionName` advances only in the release procedure. Earlier 1.1.1–1.4.1 APK names are historical
trial identifiers, not separate prepared/published releases. The approved unpublished-history
cleanup preserves their original artifacts/evidence; see [the reconciliation](VERSION_HISTORY_REWRITE.md).

## Verification and remaining publication steps

The owner completed the release procedure; the agent did not build/sign/upload another AAB.
Inspected release logs show actual unit execution (320.5 s) and lint success (98.1 s). XML confirms
910 tests, zero failures/errors/skips and lint zero errors/72 warnings. Runner/release-flow logs
confirm fourteen checks and eleven disposable Git scenarios. These are existing owner-run gate
results inspected after completion, not a fresh agent-run gate or tests of a Play-delivered install.

The artifact's size/hash match the ledger and owner's output. Its embedded version-control revision
matches the tagged source. The public upload-certificate fingerprint matches the previous AAB;
this is not evidence of the certificate Google uses to sign delivered APKs. `jarsigner` reports
`jar verified.` with warnings about trust/self-signing, missing timestamp, ZIP attributes and
JarFile/JarInputStream manifest/signature interpretation. Complete output is retained locally in
`dist/release-1.2.0-signature-verification.log`; local signature verification does not establish
Play acceptance. The same warning categories are present when verifying the retained 1.1.0 AAB
with the same JDK; this comparison does not resolve them or establish Play acceptance. No private
key or keystore content was read.

The signed bundle and annotated tag establish preparation only. Upload, acceptance, publication,
Git push and a device pass of this exact store artifact await owner confirmation. The existing
additional-closed-testing requirement is not lifted by preparing this release.

Deferred playback-queue work, WWW headset buttons, Android Auto, AMP and F-Droid are not claimed
as delivered. Older played-before/bookmark/title work already in 1.1.0 is not advertised as new.
