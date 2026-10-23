# Protracktor 1.4.0 prepared release

Release preparation completed through the interactive release procedure on **2026-10-24**.
Scope: `v1.3.0..v1.4.0`. The agent inspected the existing AAB, Git state and verification records;
the agent did not rebuild/sign an AAB, upload it or publish a release.

| State | Evidence |
| --- | --- |
| AAB prepared | `dist/protracktor-1.4.0-1304.aab`, **22,224,341 bytes** |
| Release source | `5b6936e5389d9e34bd6861e574907bb8becd6015`, annotated `v1.4.0`, also master |
| Version identity | **1.4.0 (1304)**; code equals the tagged source's commit count |
| Git publication | Not confirmed |
| Play upload and acceptance | Not confirmed |
| Play publication and production access | Not confirmed |

SHA-256: `3c78300eb5074f50a1409c61c75b62e3db519904178fbd8418b5544e0099d67a`.
The unchanged script-generated [release ledger](../store/play-console/releases.md) records the
artifact metadata and upload-certificate fingerprint. Its uploaded-notes wording identifies
the tagged text, not independent confirmation of an upload.

Copy-ready Play notes: [English](../store/listing/en-US/release-notes/default.txt) and
[Polish](../store/listing/pl-PL/release-notes/default.txt), both within the project's 500-character
limit and matching `v1.4.0`. Earlier notes remain at their release tags.

## Features and user-reported improvements

- **Volume normalization (B52):** optional Settings control, default Off, reducing perceived
  loudness differences between tracks. Fixed per-subsong trim uses bounded initial analysis
  and cached listening measurements; first-play matching can be less precise. Source files,
  system volume, exported audio and web playback are not normalized. Phone feedback reports
  comparable loudness for Studium Fałszu (Jakim) and Lotus 3 Space ninja with the option On.
  This is reported listening feedback, not catalogue-wide or exhaustive device validation.
  [Contract and acceptance](VOLUME_NORMALIZATION.md).
- **Return from Queue to its source:** the source name opens the originating playlist,
  remembered Random, Search, History, online author/game or local folder without restarting
  playback or replacing pending requests. Playlist returns protect unsaved edits; absent/stale
  destinations are handled explicitly. [Navigation contract](QUEUE_SOURCE_NAVIGATION.md).
- **Compact dock (C112):** title and metadata stay on the left; Keep and the subsong-position
  label have stable right-hand slots. Queue remains outside the card. The corrected layout
  preserves the compact height and text spacing at ordinary text size, with a wrapping fallback
  for enlarged text. [Acceptance and scope](STATUS.md#c112-dock-subsong-count-crowds-now-playing-text).
- **Browse alignment (C111):** align the leading content in Add music/Browse and Online
  catalogues. Practical phone feedback accepted the reduced mismatch, not pixel-identical
  placement of every row. [Retained checks](TESTING.md#c109c111-download-and-browse-alignment--accepted-trial).
- **Legacy Modland downloads (C109/C119):** recover eligible older unknown-author addresses
  after HTTP 404, including nested folders, without rewriting saved playlist/History identities.
  Reported examples include YM Conflict Europe, Same Game SPC, AdLib S3M Totally Nuts and the
  Megadrive VGZ Ending Theme. This is a path-recovery correction, not new decoder support or
  a guarantee for every failed download. [Mechanism](MODLAND_LEGACY_DOWNLOADS.md).

## Implementation hardening

- Publish the actual initial subsong index on Android when a decoder omits it from text metadata.
  Indicators and normalization identity no longer have to assume zero.
- Keep preparing decoders private and close allocations discarded by cancelled loading handoffs.
- Preserve smooth gain changes when switching stereo mode, reject profiles for another mode and
  distinguish an initial mix from a mid-song change when determining measurement completeness.

These corrections came from code inspection and regression checks during the normalization
implementation/review, not a claim that each was independently reported by a tester.

## Verification and release state

The inspected release-gate unit log from **2026-10-23** records an executed test task, all
**31 Gradle tasks executed** and success in **458.0 s**. Fresh JUnit XML contains **1163 tests /
152 suites**, zero failures/errors/skips. These are inspected results from the interactive
release run, not another test run by the agent.

Lint succeeded in **9.1 s**, reusing up-to-date analysis/report tasks. The retained XML from
2026-10-23 18:19 contains **0 errors / 73 warnings**; no fresh lint analysis is claimed.
All runner/release-flow, page/engine/SAP, web upgrade, native audio, icon, PWA and server logs
end with exit zero. Bundle Gradle output records executed R8, package/sign/bundle tasks and
success in **92 s**. Individual phone checklist outcomes, accessibility and power cost remain
separate from host verification.

Independent artifact inspection confirms the size/hash, ZIP CRCs, exact tagged revision in
embedded version-control metadata and engine/UADE libraries in all three ABIs. The public
upload-certificate fingerprint matches the release ledger and 1.3.0. No private key or keystore
contents were read by the agent.

`jarsigner` reports successful JAR verification with warnings about certificate trust/self-signing,
missing timestamp, ignored POSIX attributes and differing JarFile/JarInputStream interpretation.
The retained 1.3.0 verification log has the same warning categories. This comparison does not
resolve them or establish Play acceptance. Logs, JUnit XML, the reused lint report and artifact/
signature inspection are retained locally in ignored `dist/release-1.4.0-verification/`.

Application/native/web/script sources match smoke-test APK source **296de93**; the Gradle change
is only versionName 1.3.0 to 1.4.0. Schema remains **23**. That earlier APK **1.3.0 (1301)** is not
relabelled as 1.4.0. Master stays on the release tag; subsequent ledger/documentation commits
do not change the AAB's source or versionCode. Upload, rollout, Git push and testing of a
Play-generated 1.4.0 build still require separate confirmation.

## Not included

- AMP remains outside develop and this release scope.
- C118 live History freshness remains open.
- B51 saving individual subsongs and B50 the animated tutorial remain future work.
- Stereo modes, listened markers, saved places, History search and the underlying playback queue
  already existed in 1.3.0 and are not advertised again as new features.
