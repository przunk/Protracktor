# C102 — recover output after Bluetooth route disconnection

Date: 2026-10-13. Branch: `fix/bluetooth-output-recovery`, based on C103 at `b134b3e`.
The owner approved a separate Bluetooth fix and a combined APK containing playlist-write safety.
Local PR/handoff draft only: no GitHub PR, push or merge.

## Report → analysis

Real owner test on headphones: connecting Bluetooth stops sound, the app's progress stalls,
notification progress advances, seeking does not help and Pause/Play restores playback.
Build, phone/Android version, headphones and error log were not supplied; the car variant is
uncertain. This is not an independent tester report or a host reproduction of Bluetooth.

The Player registered only its data callback. On an AAudio disconnection Oboe's default error
handler stops/closes underlying output, while the Player retains a non-null pointer. `start()`
and `running()` treated pointer existence as output health. Position is published from data
callbacks, while Kotlin's playing flag stays true and the session advertises speed 1. Seeking
touches the decoder, not the output; Pause/Play replaces output. This fits the observation but
does not establish the actual device error code or exclude another receiver-specific problem.

Primary reference: [Oboe 1.10.0 disconnection guide](https://github.com/google/oboe/blob/1.10.0/docs/FullGuide.md#disconnected-audio-stream)
and [AAudio default/custom error-handler implementation](https://github.com/google/oboe/blob/1.10.0/src/aaudio/AudioStreamAAudio.cpp).

## Analysis → fix

- Every stream owns a shared callback with an atomic error and no Player pointer. `onError`
  reports handled so Oboe does not close concurrently with control operations; the existing
  control thread must stop/close the stream instead, as required by Oboe's custom error contract.
- The existing 200 ms playing-only poll calls typed JNI recovery. It handles a disconnected
  state even before the separate error thread arrives. Stop/close and reopen use the same
  production stream builder, retaining format/channel/sample-rate conversion settings.
- Reopen only output: retain backend, position, selected subsong, description, duration and gain;
  do not repeat backend startup measurement or rewind. Manual Start also rejects stale output.
- Paused/closed/finished tracks are not recovered. Each replacement has a new error state;
  a late callback from the old stream cannot affect it or access a deleted Player.
- Non-disconnection errors and failed reopens pause, release focus and show the existing PL/EN
  audio-device notice. No automatic retry after a failed reopen; explicit Play can retry.

No new receiver/broadcast, polling job, decoder, dependency, database schema or store release.
Trial version: 1.1.1. The release APK uses the existing test certificate, not the owner's AAB
upload key. Project artifact naming now includes variant, version, short SHA and dirty marker;
the helper was exercised against an actual existing APK in a disposable clean/dirty Git fixture.

## Fix → verification

Regression stage `72af15e`: unchanged production executed four fake-device host checks; three
failed (error registration, running state, stale Start). The host harness includes the actual
production Player/JNI translation unit, not a duplicate recovery implementation. Its fake device
and decoder do not open hardware, real files or host decoder caches.

Completed host suite: 18 checks pass, also under ASan/UBSan. It covers recovery, pause/destruction,
late errors, EOF, failure/retry, repeated routes/rates, seek, subsong and preserved ducking/position.
Controlled native mutations failed 11/18. Mutation initially exposed a weak seek assertion that
read only published position; it now checks the decoder and the next rendered position too.
All four JNI-result mapping tests failed under a separate Kotlin mutation. Both sources were
restored and SHA-256 compared. The sanitizer run inside sandbox stopped at LeakSanitizer/ptrace;
the outside-sandbox run completed, without changing Java or shared tools.

Full forced gate (`scripts/test-protracktor.sh --really`): **772 unit tests** across 103 suites,
zero failures/errors/skips; all 18 audio-host checks passed. Lint completed with **0 errors and
68 warnings**. Also passed: strings/store limits, 581 page checks, 49 existing WASM engine checks,
8 SAP checks, browser database upgrade, 137 icon paths, PWA and 11 loopback server checks.
Gate duration: 270 s. Shell syntax and artifact-copy/name paths were checked.
Minified APK completed from clean source `d1d8964`; actual Android/Oboe compilation succeeded
for all three ABIs. No device execution is implied by compilation or export checks.
Evidence is retained in ignored `dist/bluetooth-output-recovery/`.

## Combined trial artifact

`dist/protracktor-release-1.1.1-1138-d1d8964-20261013-203532.apk` — **1.1.1 (1138)**,
11,751,338 bytes, built in 661 s from clean `d1d8964`, including C103.
SHA-256: `4077ab501bb11726eb70da1bd3dc688236891b2c946ff0340c4049827bfc7988`.

R8 executed and its mapping exists; the APK is non-debuggable, minSdk 29/targetSdk 36, with
armeabi-v7a/arm64-v8a/x86_64 libraries. APK v2 signature verified; the test certificate matches
trial 1123 (SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`).
The recovery JNI export was checked in each packaged engine library and its native method name
in minified DEX. The host lacked `unzip`; extraction used the existing JDK's `jar`, without
installing tools or changing Java. These checks do not establish runtime routing on a phone.

## PR → owner phone retest

**Partially checked on phone, 2026-10-13:** following the combined trial handoff, the owner
confirmed speaker → Bluetooth playback works and Bluetooth disconnection correctly pauses.
The installed build/device details were not separately supplied. Position/subsong continuity,
progress bars, seeking, Pause during connection, focus/EOF/error cases and car playback have no
individual retest result yet; C103 playlist checks also remain pending.

No merge approval, tag, AAB or publication is claimed. Remaining scenarios are in the
[checklist](TESTING.md#c102-bluetooth-output-recovery--combined-trial).
The current working tree includes C103; it does not put either fix on develop.
