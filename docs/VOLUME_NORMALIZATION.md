# Volume normalization — experimental Android playback

Selected 2026-10-22 after the Queue-source feature was accepted. B52 is implemented separately
on `feature/volume-normalization`, from develop 9c44fc9. Do not merge before phone acceptance.

## Trial contract

- Settings offers Off/On, default Off; preserve versionName 1.3.0 and schema 23.
- Measure perceived stereo loudness using BS.1770 K weighting and gated 400 ms blocks. Use
  a bounded 0.1-LU histogram, not an unbounded allocation in the audio callback.
- Start with an experimental -23 LUFS target. Apply one measured trim per subsong, with at most
  +6 dB for partial analysis and +12 dB for complete analysis; peak headroom may reduce either.
- A disposable measurement cache is independent of History and playlists. Identity includes
  source content, companion content, subsong, stereo mode and algorithm/decoder revision.
- Analyse a limited prefix using a separate decoder, never seek/advance the playing decoder.
  Prefetch may warm measurements; explicit play must have a cooperative work budget. It is
  not a hard deadline for an individual decoder open/render call. Report actual host timings.
- Collect a fuller measurement while listening. Unknown/partial/complete and silence must not
  be confused. No automatic scan of every subsong or catalogue and no downloaded PCM cache.
- Keep stereo mixing, normalization and transient focus ducking separate. Pause/seek/BT output
  recovery must not reset the active correction. Another subsong must not inherit it.
- A stereo-linked emergency sample-peak guard catches transients outside a measured prefix;
  reserve -2 dBFS headroom. This is not an oversampled true-peak limiter or a guarantee after
  hardware/resampler processing. It may alter an unexpected transient; document that limitation.
- Fixed trim avoids chasing quiet intros/outros. A previously unknown fallback can settle once
  after sufficient audio; there is no promise of perfect first-play normalization without cost.
- Android first. Web playback stays unchanged and normalization does not process exported audio.
  DSP stays Android-free.

## Verification plan

Check calibrated tones at 44.1/48 kHz, relative/absolute gates, silence, short tracks, stereo
linkage, gain caps, unforeseen peaks, disabled exact bypass, subsong reset, live settings,
ducking and BT recovery. Exercise production JNI/Player paths with the existing host harness.
Check cache invalidation/corruption and stale asynchronous results. Demonstrate a failing
regression/mutation, then force full tests/lint and inspect the minified release APK.

Phone sound quality, comfortable transitions and real-device CPU/battery cost remain unchecked
until the trial is used on a phone. This document is a contract, not verification evidence yet.

## First host checkpoint

Eight pure DSP checks pass: calibrated tones at both rates, gates, silence/short clips, copy
equalization within the boost cap, exact Off bypass, linked peak protection and non-finite input.
Deliberately replacing the gain policy with unity breaks copy equalization; the real policy
was restored and all eight checks passed again. These checks do not establish audio quality.

Existing WASM decoding of four real local examples rendered non-silent audio: IT 279x realtime,
SPC 191x, AdLib S3M 72x and Megadrive VGZ 16x. These are host decoder baselines, not Android or
normalizer benchmarks. They justify a cooperative work budget rather than always rendering
the same long prefix. Evidence is ignored under `user/volume-normalization-verification/`.

The first -18 LUFS trial left the quiet VGZ prefix at -25.57 LUFS because partial profiles
permit only +6 dB. Select -23 LUFS for this experiment instead: reduce loud examples further
without raising the partial-analysis boost cap. This is a playback policy, not a requirement
to match a streaming service. The system volume remains adjustable independently.

## Integration safeguards

Normalization control is one atomic packet (gain, enabled/prepared flags, revision), consumed
by the existing callback. Settings and best-effort measurement snapshots never wait behind
an emulator seek. All native DSP state remains decoder-guard owned; no disk, JNI, allocation
or waiting is added to the audio callback. A changed output sample rate invalidates whole-song
completeness but preserves trim. Seek/restart and mid-song mode changes remain partial.

The read-ahead -1 alias only skips repeated warming; audible playback resolves the actual
initial subsong and reads its own identity. Discard optional measurement failures and cancelled
results without preventing playback. Disk cache entries are bounded to 512 small profiles;
no schema migration, external audio effect or new dependency is required.

The native production-path checks initially covered 37 cases, plus 12 pure DSP checks; ASan/UBSan
passed. Deliberately bypassing callback normalization fails three integration checks. Replacing
the best-effort snapshot with a blocking lock fails the controlled concurrent-seek regression.
Both mutations were restored. The Kotlin focused set passed 17 tests / 5 suites (129.1 s),
including the physical Settings switch, preference persistence, cache identity/corruption,
initial audible subsong, cancellation and optional-analysis failure. An initial UI fixture
failed because the unrelated storage overview asked for an Android decoder library absent
on the host; only that fingerprint supplier is substituted, not the new setting path.

Four 20-second real decoded prefixes (Original stereo) reduce a 21.14 LU spread to 2.57 LU:
IT -26.25 to -23, S3M -18.55 to -23, SPC -10.43 to -23, VGZ -31.57 to -25.57 LUFS. These are
prefix measurements, not complete-song results or listening tests. Three meter/DSP passes over
each 20-second input took about 33–38 ms on this host; no Android CPU claim follows.

Final hardening adds a fixed-size checksum-validated cache record and retains the existing
cancel-on-second-press rule while optional analysis is in flight. Its full verification and
artifact are recorded below. No phone behaviour, merge or store release is established.

Additional guards: a relative-gate regression uses quiet but audible material above the absolute
gate (silence alone would not distinguish the two). Disabling the relative gate fails it.
Bypassing the cache checksum also fails on a one-bit mantissa change which remains finite and
in range. Both mutations were restored. The final DSP set contains 12 cases.

The first full gate was interrupted intentionally after 370 s (not a passing gate): some
backends open their nonzero default subsong but omit that text field. Guessing zero would bind
normalization to another melody. A new production Player regression fails before publishing
`Backend::currentSubsong()` in Android metadata. Prefix it before the multiline message, which
consumes the remainder of the block; a parser regression protects that boundary. Both the initial
Android indicator and normalization now follow the real initial tune, as do existing consumers
of that selection. This Android-only metadata fix does not apply normalization to exports or
change web metadata. Native Player checks now number 38; final ASan/UBSan passed all 50 native/
DSP cases and the full gate below was rerun after this correction.

## Final verification and APK — 2026-10-23

Clean source **48466de589f533397527c405c51ad0c8284d5183** passed
`./scripts/test-protracktor.sh --really --no-configuration-cache` in **685 s**:
**1158 tests / 152 suites**, zero failures/errors/skips. All 31 unit-stage tasks executed
(489.0 s). All companion checks passed. Fresh lint ran in **103.7 s**, **0 errors / 73 warnings**;
the extra advisory versus the previous 72 is UseKtx in unchanged RecentSearchPreferences.kt.

Minified test-signed release **1.3.0 (1295)** built in **231 s** (233.3 s supervised):
[protracktor-release-1.3.0-1295-48466de-20261023-001631.apk](../dist/protracktor-release-1.3.0-1295-48466de-20261023-001631.apk),
**11,960,131 bytes**, SHA-256
`8f4974e5e81940f2243f412bf0411400e9715a4f4ffa1f7c6124d3818b8eef5f`.
Independent inspection checked the v2 test signature, version/code, full embedded source revision,
non-debuggable manifest, ZIP CRCs/alignment, minSdk 29/targetSdk 36, engine/UADE libraries and
the three new JNI exports in arm64-v8a, armeabi-v7a and x86_64. R8 executed and its mapping is
retained alongside logs, reports, decoded examples and mutation evidence under ignored
`user/volume-normalization-verification/`. No store release, push or merge is implied.

**Checked:** host evidence above. **Unchecked:** actual Android sound, settling/transition
comfort, UI/accessibility and device CPU/battery cost. Settings defaults Off; enable manually
and start with modest device volume. [Phone checklist](TESTING.md#b52-volume-normalization--trial).
Develop stays 9c44fc9; master/tag stay d456fa7; AMP untouched. VersionName 1.3.0, schema 23.

References: [ITU BS.1770](https://www.itu.int/rec/R-REC-BS.1770/en),
[EBU metering](https://tech.ebu.ch/loudness/),
[EBU streaming/headroom guidance](https://tech.ebu.ch/docs/r/r128s2.pdf).

## Code review and hardening — 2026-10-23

Initial listening established an audible difference, not sound-quality acceptance. Keep the
experiment Off by default and unmerged. The agent reviewed the branch's DSP, analysis, JNI,
controller lifecycle, cache, setting and regression tests; no redesign of the loudness policy
or new dependency is justified by this review.

### Findings and corrections

1. **Live mix changes discarded the current trim.** Resetting the whole normalizer also reset
   its smoothing state, so the first callback could jump directly to a different cached gain.
   Reset only the measurement on a live mix change; retain the audible gain and ramp to the
   new target. A new subsong still starts with fresh conservative state. The production Player
   regression failed before this correction and passes afterward.
2. **Mix and gain commands could cross callback boundaries.** Width and normalization are
   separate atomic updates. A callback could observe a width with a profile prepared for another
   width; alternatively, resetting the width could discard an already-consumed matching profile.
   The normalization command now carries its mix identity. Use the unknown fallback for a
   mismatch and reconsider the same command when the matching width arrives. Named packet
   fields replace the repeated bit positions. A controlled production Player regression fails
   the previous implementation and passes both sides of the crossed-update case after the fix.
3. **Preparation exposed the native track too early.** The controller assigned its playable
   handle before optional analysis suspended. Subsong controls could then start/change that
   decoder while opening still assumed exclusive access for metadata and known lengths.
   Keep the handle private until preparation completes, acquire audio focus afterward, then
   adopt it. Capture the actual decoder inside the opening worker too: cancellation at the
   dispatcher handoff previously could discard its result without closing the native allocation,
   including with normalization Off. One small `prepareOrClose` guard closes private resources
   on failure/cancellation without touching a later playback's focus or track. Tests exercise
   successful adoption, failure and both cancellation handoffs; the complete Android loading
   and control interaction still requires phone retesting.
4. **A non-default initial mix falsely prevented complete measurements.** The first callback
   treated initial Mono/Narrow as a mid-song change from Original, invalidating completeness
   even though no audio had been measured. Invalidate continuity only after samples exist.
   A regression failed for the initial mode before the fix; it also checks that an actual
   mid-song mix change still cannot claim a complete measurement.

### Review assessment

The fixed trim, bounded meter and disposable cache remain separate from queue rules and system
volume. Keep them together as an optional trial; do not add a continuous compressor, full-library
scanner or additional persistent state to improve the first-play approximation. The principal
remaining trade-offs are preview/opening latency, conservative first-play matching, possible
alteration of unexpected peaks and unmeasured device power cost. None is settled by green host
tests or by the initial listening feedback.

**Review verification in progress:** all three native regressions failed before their corrections;
40 Player/analysis plus 12 DSP checks passed ASan/UBSan before the last initial-mode correction;
the expanded 41+12 set is being checked again outside the ptrace sandbox.
Bypassing cleanup failed three of the eight Kotlin preparation tests; cleanup was restored.
The expanded Kotlin cancellation set, forced full gate, lint and replacement release APK are
pending. The original APK/source above remains historical evidence, not a build of these changes.
