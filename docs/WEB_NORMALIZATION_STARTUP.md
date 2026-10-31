# C130: WEB normalization starts quietly before increasing gain

Reported and investigated: **2026-10-31**, source `0cd80c1`. The approved repair follows on
`fix/web-normalization-startup`, from develop `707539e` after C118 integration.

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

## Repair scope

Use explicit global timer adapters for the default analyser timers, and ensure that a setup or
cleanup failure cannot leave an active worker/job permanently occupied. Add a real-browser
regression with normalization enabled that verifies the profile is prepared before first PCM,
plus failure/retry/cleanup checks. Retain target LUFS, boost/peak limits, Narrow behaviour and
the conservative fallback for genuine unavailable analysis; that fallback can still adjust a
level after sufficient live measurement. No louder target or stereo-policy change is needed.

## Implementation checkpoint — 2026-10-31

Default timer adapters explicitly call the current global timer owner. Setup failures now settle
the optional measurement and dispose its worker. Cleanup releases the active slot before clearing
the deadline; refused timer cleanup or worker termination cannot strand the result or block a new
job. A synchronously expired injected deadline also clears its subsequently returned handle.
The Native policy, profile format/cache, target, safety limits and five-second worker deadline
are unchanged. No engine rebuild or Android code change is needed for C130.

The native-owner host guard fails before correction. All **31** worker/preparation cases pass
afterward. Four controlled negative probes remove setup handling, timer cleanup handling, safe
termination and late-deadline cleanup respectively; each fails its intended guard. Production
bytes are restored before the positive check.

`./scripts/check-web-normalization.sh` uses the existing local Chromium setup and production
`PlaybackNormalization`, Worker, AudioWorklet and WASM, with native browser timers and real
IndexedDB profile storage. A minimal harness supplies generated quiet ProTracker files and
measures actual WebAudio PCM; it is not a whole-player UI or audible-device test. Before the fix,
request one starts with no prepared profile and fails. Afterward, two cold songs and warm replay
pass for Original and Narrow: **six cases**, all twenty-second profiles, no browser-script errors,
and below **0.002 dB** early-to-later RMS difference on the generated constant tones. Warm and
cold starts agree within the checked 0.5 dB bound. The worklet options match the application's
zero-input/stereo-output configuration; no audio or Worker implementation is substituted.

Focused companions also pass: **15** playback-normalization ownership cases, **8** actual
worklet/WASM boundary cases, **36** native-WASM PCM policy cases and the real ESM/worker/worklet
bridge. Complete gate/lint, clean-source browser check and packaged WEB handoff remain pending.
Logs/reports are retained under ignored `user/c130-normalization-fix/`.

The correction remains unmerged. Firefox/device listening and publication are separate steps.
Genuine unavailable analysis still uses the documented live fallback; this change fixes the
unnecessary browser-timer failure rather than promising every source can be measured upfront.
