# Protracktor 1.4.0 release notes draft

Prepared 2026-10-23 for the proposed next release. Scope: application changes on develop after
`v1.3.0`. The application still reports 1.3.0 until the release procedure selects the release version.

Copy-ready Play notes: [English](../store/listing/en-US/release-notes/default.txt) and
[Polish](../store/listing/pl-PL/release-notes/default.txt), both within the project's 500-character
limit. Earlier notes remain at their release tags.

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

Application source d68bfad passed the forced full gate: 1163 tests / 152 suites, zero failures,
errors or skips, all companion checks including 53 native/DSP cases, lint 0 errors / 73 unchanged
warnings. Integration and later documentation preserve those application inputs. The unchanged
native code also passed 53 ASan/UBSan checks during review. These are host results; individual
phone checklist outcomes, accessibility and power cost remain separate.

The supplied smoke-test APK is **1.3.0 (1301), source 296de93**, not an issued 1.4.0 artifact.
It is minified/test-signed and includes final EN/PL Settings copy. Build, signature/source/
package/ZIP/ABI inspection and its exact hash are recorded in [Status](STATUS.md).
Long tests and standalone lint were not repeated for that build, as requested, because the
application inputs matched the checked source. The release procedure still needs its fresh gate
and signed store AAB; upload and rollout are separate steps.

## Not included

- AMP remains outside develop and this release scope.
- C118 live History freshness remains open.
- B51 saving individual subsongs and B50 the animated tutorial remain future work.
- Stereo modes, listened markers, saved places, History search and the underlying playback queue
  already existed in 1.3.0 and are not advertised again as new features.
