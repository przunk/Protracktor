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
- Android first. Web playback and exported audio remain unchanged. DSP stays Android-free.

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

References: [ITU BS.1770](https://www.itu.int/rec/R-REC-BS.1770/en),
[EBU metering](https://tech.ebu.ch/loudness/),
[EBU streaming/headroom guidance](https://tech.ebu.ch/docs/r/r128s2.pdf).
