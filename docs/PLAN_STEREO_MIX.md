# B6 — stereo mix trial

Owner-selected trial, 2026-10-13. Branch: `feature/stereo-mix-modes`, based on develop `2eaf590`.
Source: real listener feedback about strongly separated channels, relayed by the owner.

## Contract

- Android Settings → Playback offers three modes, not a slider: Original, Narrow and Mono.
- Original is the default and leaves PCM untouched. Narrow uses 50% stereo width for this trial:
  `L' = 0.75 L + 0.25 R`, `R' = 0.25 L + 0.75 R`. Mono averages channels into both outputs.
  The Narrow strength and unchanged default are agent trial assumptions, not separately chosen
  percentages/defaults from the owner.
- Remember the choice globally in existing preferences, independently of playlists and schema 23.
- Apply the latest choice before a new track starts and to existing playback without resetting
  position, subsong, focus or output. Paused playback remains paused.
- Process the common Android output after decoding, including MP3, without modifying individual
  backend stereo settings. Smooth live width changes over up to approximately 10 ms; no callback allocation,
  JNI calls or new locks. Preserve the existing ducking gain, EOF and Bluetooth recovery policies.
- No amplitude normalization/boost: channel averages are convex combinations. Opposite-phase
  content can cancel in Mono; do not claim Mono preserves every stereo detail or repairs a decoder.
- Use labelled, state-accessible controls and PL/EN text in Settings.

## Boundaries and acceptance

Playback processing only. Crossfeed, Dolby-branded processing, per-format/output automation,
web controls and processing of shared/exported audio are not selected. B48 notification hearts
remain discussion-only. No dependency, database migration or store release is needed.

1. Establish callback regression evidence with asymmetric source PCM through the production
   Player/JNI and fake device. A setter alone does not prove that rendering uses its value.
2. Implement shared DSP, setting persistence and Settings wiring; test real UI clicks and stored
   values. Deliberately break relevant guards/wiring and confirm tests fail, then restore.
3. Run the full forced test/lint gate and native sanitizers; prepare a clean-source minified release
   APK in dist. Record actual counts, signature/source, checks and missing device evidence.
4. Owner compares separated stereo tracks and already-mono material, changes modes while playing
   and paused, checks next/subsong/restart/persistence and Bluetooth routing. The trial can be
   revised or rejected after listening. Stop unmerged until owner approval.

## Regression checkpoint — `e38e94d`

At this checkpoint the native control boundary stored the requested width, but rendering had not
been changed. The production Player/JNI host harness executed 29 checks; 8 failed on Narrow/Mono,
live changes, paused resume, EOF and recovery expectations. The existing 18 recovery checks remain
green. Evidence: ignored `dist/stereo-mix-verification/native-red.log`. No phone acceptance,
full test/lint result or APK is established at this checkpoint.

## Implementation verification

Implemented shared output processing and Settings/persistence wiring. Focused checks executed:
3 mode/width tests, 4 real Android preference tests and 3 Compose tests against the actual Settings
screen (including English/Polish clicks and selected-state feedback): 10 passed. The expanded native
Player/JNI harness passes 30 checks, including another device-rate/mid-transition check.

Native bypass mutation failed 9/30 checks; removing the transition ramp failed 2/30. Disconnecting
the real Settings callback failed 2/3 UI tests. Mutating the preference write and Kotlin width
mapping failed 4/7 focused tests (3 preference checks and 1 mode check). Mutated native/UI/preference
sources were restored and SHA-256 compared. ASan/UBSan passed all 30 checks outside sandbox;
the sandbox attempt could not finish LeakSanitizer under ptrace.

Forced full gate completed in 205 s: 782 unit tests in 106 suites with zero failures/errors/skips;
lint 0 errors and 69 warnings. Strings, store metadata, 581 page checks, 49 WASM engine checks,
8 SAP checks, browser database upgrade, 30 native audio checks, 140 icon paths, PWA and 11 loopback
server checks also passed. Earlier lint rejected literal percent formatting in the description;
both resources now set `formatted="false"`. A subsequent host test worker ran out of memory and
that run was interrupted. The successful full rerun used a local temporary Gradle init script
setting the test worker heap to 1 GiB; the existing JDK, app/build configuration and full test
selection stayed unchanged. Evidence is retained under ignored `dist/stereo-mix-verification/`.

## Release APK handoff — 2026-10-13

Clean source `ad6e3cd`, version 1.2.0 (1146), built in 106 s through `scripts/build-release.sh`:
`dist/protracktor-release-1.2.0-1146-ad6e3cd-20261013-213208.apk` (11,760,006 bytes).
SHA-256: `dbbdc1ed91f11c53d69cc4b988f57fb670b1544c1ede7a52c58094474e613487`.
R8 ran; the APK is non-debuggable, minSdk 29/targetSdk 36 with arm64-v8a, armeabi-v7a and x86_64.
APK v2 signature verified; the test certificate matches the accepted Bluetooth trial (full
fingerprint in Status). The new JNI export exists in all three libraries and its native name is
retained in minified DEX. No upload key, AAB, tag, push, merge or store release was used.

These checks establish the artifact, not phone behaviour or sound quality. Install as an update
without clearing app data and follow [the listening checklist](TESTING.md#b6-stereo-mix--owner-accepted-remaining-device-checks).
At handoff all phone outcomes were not checked; merging required owner approval. The handoff
documentation update did not change APK sources or rerun application tests/builds.

## Owner acceptance and merge — 2026-10-13

Following positive feedback after the APK handoff, the owner authorized merging. Branch
`feature/stereo-mix-modes` merged into develop at `f4d304c` without conflicts; its tree matched the
approved branch exactly. Develop declares 1.2.0/schema 23; app sources remain identical to
`ad6e3cd`. Installed build/device/headphones and individual checklist results were not separately
supplied, so remaining device checks are not established by the general acceptance.

Closeout checked merge ancestry/tree identity, documentation consistency, links and diff whitespace.
No full test/lint rerun or new APK was needed for the unchanged application sources and
documentation-only closeout. No push, master/tag change, AAB or store publication.
