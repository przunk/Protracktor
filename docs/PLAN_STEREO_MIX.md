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
  backend stereo settings. Smooth live width changes over at most 10 ms; no callback allocation,
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

## Regression checkpoint

Checked: the native control boundary stores the requested width, but rendering has not yet
been changed. The production Player/JNI host harness executed 29 checks; 8 failed on Narrow/Mono,
live changes, paused resume, EOF and recovery expectations. The existing 18 recovery checks remain
green. Evidence: ignored `dist/stereo-mix-verification/native-red.log`. No phone acceptance,
full test/lint result or APK is established at this checkpoint.
