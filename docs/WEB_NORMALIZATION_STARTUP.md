# C130: WEB normalization starts quietly before increasing gain

Reported and investigated: **2026-10-31**. Application source: `0cd80c1`. No application code
has been changed for this investigation.

## Report

In Firefox, with **Volume normalization On** and **Narrow**, a song can begin quietly and become
louder after approximately two to five seconds. Restarting from the beginning can retain the
higher level. The supplied sequence is:

1. `Modland/OctaMED MMD1/Faroul/slow beat.mmd1`
2. `Modland/HVSC/MUSICIANS/S/SMC/Soft_Boobies.sid`
3. `Modland/Video Game Music/Sega Megadrive/Masaru Suzuki/Blue Almanac/16 planet ohly.vgz`

The increase was specifically noticed for the SID and subsequently the VGZ. Browser version,
exact active subsong and the served build were not supplied. The sequence is recorded without
assuming that the previous song is necessary to trigger the fault.

## Confirmed mechanism

`LoudnessAnalyser` retains native `setTimeout`/`clearTimeout` as object properties and invokes
them with the analyser instance as the receiver. In actual **Chromium 156.0.8078.4**, the default
analysis call rejects with `TypeError: Illegal invocation` before sending the worker its request.
Its active-job marker also remains set; a second attempt rejects as already running.

`LoudnessPreparation` treats the failure as optional and the production `PlaybackNormalization`
handshake sends `prepared` with `profile: null`. The worklet then starts the enabled normalizer
without a prepared profile. Native `VolumeNormalizer` uses **0.5 gain** for unknown material,
measures live input, and first attempts a usable profile after **three seconds**. It then ramps
toward the measured gain. A seek/rewind preserves that established gain, explaining the louder
immediate restart. This differs from a valid private measurement arriving after playback starts:
the normal initial handshake waits for preparation.

Using explicit Window timer wrappers **only in the diagnostic constructor** lets the same real
Web Worker and packaged WASM produce a valid twenty-second profile and finish idle. The default
production path fails in the same browser context. The worker and its native timers were not
mocked. This isolates timer ownership as a confirmed defect; it does not claim a Firefox run.
Mozilla's [timer interface](https://raw.githubusercontent.com/mozilla/gecko-dev/master/dom/webidl/WindowOrWorkerGlobalScope.webidl)
also defines these operations on Window/Worker globals, but source inspection is not a Firefox
execution result.

## PCM measurements on the reported files

All three supplied files downloaded successfully from their exact Modland paths. The real
packaged WASM renders their initial subsong, **Narrow (50%)**, at 48 kHz with 128-frame callbacks.
For the missing-profile path, normalized PCM is compared with the same decoder/mix with
normalization Off. These are gain changes, not absolute LUFS or device-volume measurements.

| File | Gain before 3 s | Gain at 3.5 s | Increase |
| --- | ---: | ---: | ---: |
| `slow beat.mmd1` | -6.02 dB | +6.00 dB | 12.02 dB |
| `Soft_Boobies.sid` | -6.02 dB | -1.16 dB | 4.86 dB |
| `16 planet ohly.vgz` | -6.02 dB | +6.00 dB | 12.02 dB |

The established gain is retained after seeking to zero for all three. A generated quiet
ProTracker fixture independently demonstrates the same transition. Narrow affects the measured
signal/profile identity; the timer defect also reproduces with Original width, so Narrow is not
a prerequisite for this mechanism. Exact Firefox/device playback and an audible retest remain
unchecked. Diagnostic scripts, downloaded bytes/checksums and result JSON stay under ignored
`user/web-normalization-investigation/`.

## Recommended repair

Use explicit global timer adapters for the default analyser timers, and ensure that a setup or
cleanup failure cannot leave an active worker/job permanently occupied. Add a real-browser
regression with normalization enabled that verifies the profile is prepared before first PCM,
plus failure/retry/cleanup checks. Retain target LUFS, boost/peak limits, Narrow behaviour and
the conservative fallback for genuine unavailable analysis; that fallback can still adjust a
level after sufficient live measurement. No louder target or stereo-policy change is needed.

**Open; repair not implemented.** This investigation changes documentation only. No application
gate, APK, engine rebuild, merge or publication is claimed by this stage. C118's separate APK
and branch are unaffected.
