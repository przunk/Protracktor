# Protracktor 1.5.0 — release notes draft

Prepared 2026-11-01. Scope: development since `v1.4.0`, plus the combined floating-controls and
AMP candidate. This is a planned release, not a prepared AAB, release tag, upload or publication.
Test APKs retain versionName 1.4.0 until the release procedure selects 1.5.0. Source/artifact and
verification evidence belong in [Status](STATUS.md) and [release preparation](RELEASE_1.5.0_PREPARATION.md).

Short Play notes: [English](../store/listing/en-US/release-notes/default.txt) and
[Polish](../store/listing/pl-PL/release-notes/default.txt). Tagged 1.4.0 notes remain unchanged.

## Android changes

- **Search the current playlist (B55):** replace the chooser with an animated search field in
  the same row. Filter the current draft as you type, ignoring case and accents in titles,
  authors, filenames and paths. Keep the field/results bright while typing; choosing a match
  restores normal controls. Playback retains the complete playlist context. Closing search
  restores the list and previous viewport. [Contract and acceptance](PLAYLIST_SEARCH.md).
- **Live History (C118):** refresh an open History view after new records or cleared data,
  preserving its query, page and viewport where possible. The captured playback context remains
  independent of live results, and History playback does not record itself again.
  [Contract](HISTORY_LIVE_UPDATES.md).
- **AMP catalogue (A7):** browse/search a downloaded songdb index, open author folders, bookmark
  places and play/cache individually selected tracks using the existing decoders and queue.
  A build-time flag controls availability. A disabled update blocks AMP access without deleting
  playlists, History, bookmarks or the index; there is no AMP switch in Settings. AMP is credited in Music sources. No crawler,
  speculative music prefetch, bulk transfer or new decoder is introduced. The prior approval
  hold is superseded by the project decision of 2026-11-01; no maintainer reply or individual
  approval is claimed. The informational follow-up was sent on 2026-11-01, confirmed on 2026-11-02.
  [Access contract and historical trial evidence](PLAN_AMP.md).
- **Floating controls (B54):** default-Off Settings opt-in enables the existing Play/Pause hold
  for controls over another app. Overlay permission remains explicit. Drag the panel, dock it
  to an edge or drop it on Hide; Previous/Next holds match file navigation in the dock. The
  heart saves to the selected playlist. Availability persists, visible panels do not restore
  automatically after process restart, and Off hides without pausing.
  [Interaction and verification limits](FLOATING_CONTROLS.md).
- **Light appearance (C123/C124):** explicit theme colours, control alignment and system-bar
  contrast corrections, including a readable status-bar clock. Tester-specific Redmi rendering
  still requires device verification; the changes do not establish a startup-crash fix.
- **Normalization:** the fixed target changes from -23 to -18 LUFS, retaining boost/peak limits
  and the default-Off preference. Listening feedback accepts the louder policy; the separate
  target-slider experiment remains parked and is not included.
  [Measurements and limits](VOLUME_NORMALIZATION.md).

## WEB companion — deployed separately from the Android release

Development since 1.4.0 brings the selected Android parity scopes to WEB: independent playback
context/manual queue and source navigation, Random history/filters, History and learned titles,
played-before markers, saved places, recent searches/scopes, playlist editing/transfer, retained
music/cache management, stereo/normalization and browser system-media controls. B55 also adds
search inside the open playlist. Browser/OS support and remaining physical checks are separate
from host verification. [Implementation and publication records](WEB_IMPLEMENTATION.md).

Decoder handoffs retain filenames for Spectrum PT3 and SAP examples (C121). Navigation/dice
controls and narrow layouts have consistency corrections (C125–C129). The normalization worker
startup fix (C130) prepares the correct track profile before playback instead of falling back
to a quiet start and later gain jump. No new WEB deployment is implied by this Android preparation.

## Verification and exclusions

The corrected source **2794a1db** passes **1323 tests / 181 suites**, all
**85** gate stages, **1396 WEB page assertions**, **57 engine
checks**, and fresh lint **0 errors / 81 warnings**. Actual false-constant checks
also pass. A minified test-signed **APK 1.4.0 (1434)** is prepared and inspected; develop
integration is approved and completed as **3519f05b**, 2026-11-02. Individual phone checklist
outcomes remain separately unreported. [Preparation](RELEASE_1.5.0_PREPARATION.md#replacement-apk-after-the-amp-build-gate-correction--2026-11-02) records exact
artifact/source/hash and limits. Earlier APK 1432 and other historical feedback keep their
original identities. Schema stays 23; no destructive migration or new dependency is selected.

C122 Redmi startup crash remains under observation. No stack trace or reproduced mechanism
establishes a fix. Android Auto, individual saved subsongs (B51), tutorial (B50), free-library
integration and normalization-target slider are not part of this release scope. AMP remains
Android-only. Production access, AAB signing/upload, Git push and publication remain separate
steps; the production-access application was declined again on 2026-10-27.
