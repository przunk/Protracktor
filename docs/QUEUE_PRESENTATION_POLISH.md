# Queue presentation polish

Ready for phone testing: **APK 1.2.0 (1250), source 6043902**, unmerged. Final evidence below.

Subsequent owner feedback selected [scroll-first swipes](QUEUE_SWIPE_INTENT.md). Its APK **1252**
is now the current unmerged candidate, with 1056 host tests/full gates/lint passed. This document
retains code-1250 evidence; neither handoff has device acceptance of all behaviour.

Owner feedback, 2026-10-20, after the manual-reorder handoff (latest supplied APK 1.2.0/1248;
installed build ID not independently supplied). Continue on `feature/manual-playback-queue`,
without merging. [Previous contract/evidence](QUEUE_MANUAL_REORDER.md).

## Report and implementation scope

- Consumed manual rows disappear abruptly. Animate their visual departure and the remaining
  rows closing the gap, without delaying scheduler changes or audio transport.
- Opening row Info by holding needs a deliberate haptic response, using the existing View-based
  haptics policy and respecting Android's system setting. A grip hold must not also open Info.
- The persistent gesture instructions dominate the panel header. Replace them with a compact,
  labelled Help control beside the source name and an on-demand EN/PL gesture guide.

Keep source/current identities, duplicates, manual-only reorder boundaries, swipe/Undo, source
continuation, shuffle/repeat, dock layout and all saved data unchanged. Animation belongs to the
UI, not to pending-request lifetime. No dependency/schema/native/web/AMP change or version bump.
C111/C112 remain excluded. No merge is authorized.

## Verification plan

Use production-shell host gestures and a controlled clock to check intermediate row positions,
immediate scheduler advancement, Info feedback, Help open/close without playback mutation,
reorder/swipe retention and accessibility. Verify a regression test fails without its change.
Then run the forced complete host gates and lint; build and inspect a clean minified test-signed
release APK into dist. Phone appearance, animation feel, vibration and TalkBack remain unchecked
until the owner tests them. Host rendering is only partial evidence of on-device behaviour.

## Implementation checkpoint

Queue rows use Compose's existing lazy-item animator with their occurrence keys: 160 ms fade-in/
fade-out and 220 ms placement. While a grip owns movement, placement animation is disabled so it
cannot compete with the bounded pointer offset. The framework owns animation duration scaling;
there is no delayed removal, extra scheduling state or new animation dependency. Departed layers
are visual only, not live controls. Source following and dock measurements are unchanged.

Info explicitly calls the shared `Haptics.gestureStart()` before opening the existing dialog.
The installed Compose 1.7.6 combined-click API does not add that feedback. On API 29 the shared
policy requests LONG_PRESS; on API 30+ it requests GESTURE_START, with no system-setting bypass.
Help uses the existing labelled Info icon beside a two-line-capped source name. The EN/PL dialog
has four short gesture sections and a scrollable body for smaller screens/larger text.

The missing-haptic regression failed on the original code (expected LONG_PRESS, observed the
pre-test CLOCK_TICK sentinel): `/tmp/protracktor-queue-polish-before.log`, 34 s. Restored new
presentation tests passed **6 tests**, 25 s (`/tmp/protracktor-queue-polish-presentation-third.log`),
including intermediate positions with/without a source anchor, partial fade pixels, immediate
schedule changes, no actionable departed row, Help dismissal without state/scroll changes and
Polish help. Exit pixels use Robolectric's native RenderNode-capable renderer, not a software
Canvas that cannot draw lazy-item exit layers. An initial completion fixture incorrectly used
Next at the final source, which is intentionally a no-op; it now invokes the actual natural-end
controller path. These were test-harness corrections, not application fixes.

An earlier combined run passed **27 tests** covering the first three presentation cases plus
all existing manual-reorder, queue-shell and queue-experiment cases, 62 s
(`/tmp/protracktor-queue-polish-first.log`).

Independent negative probes failed as intended, production code restored afterwards:

- Placement disabled: intermediate position equalled the final position, 18.9 s,
  `/tmp/protracktor-queue-polish-placement-negative.log`.
- Fade-out disabled: intermediate pixels equalled final pixels, 18.7 s,
  `/tmp/protracktor-queue-polish-fade-negative.log`.
- Help click disconnected: physical click did not open the guide, 18.9 s,
  `/tmp/protracktor-queue-polish-help-negative.log`.

Initial wildcard-filter probes had overlapping report writes (EOF/missing in-progress result
file), so they are not assertion evidence. The valid probes above were run sequentially with
exact class filters. No JDK/environment change. Restored combined run passed **78 tests / 7 suites**,
zero failures/errors/skips, **55.4 s**, `/tmp/protracktor-queue-polish-focused.log`: both domain/
controller suites, shared swipe gestures, manual reorder, queue shell/experiment and all six
new presentation cases. The navigation suite was not selected by that run's mistyped package
filter; it was subsequently included in the unfiltered full gate. At that checkpoint full
gates/lint and the new APK were pending. APK 1248 records the previous handoff.

## Full-gate test refinement

The first unfiltered forced run stopped after **1002 cases** (1 failed, 1 skipped) in **301.7 s**:
the single sampled fade pixel was already at its final value. This is an incomplete gate, not a
passing application result; lint and companion stages did not run. Evidence is preserved in
`dist/queue-polish-verification/a526132/failed-gate/`.

The presentation test now records 12 consecutive 16 ms draw passes and requires multiple
partially faded frames. It still checks that transport has advanced and the old semantics have
disappeared after 32 ms. No production-code change was made for this test refinement. The refined
six-case class passed in **27.8 s**; disabling only fade-out failed its multi-frame regression in
**29.0 s** (`/tmp/protracktor-queue-polish-frame-fade-negative.log`). Restored code then passed the
six-case class with **all 31 Gradle tasks forced to execute**, **78.2 s**
(`/tmp/protracktor-queue-polish-frames-forced.log`). The repeated full gate passed as recorded below.

## Final verification and APK

Clean source **6043902**, versionName **1.2.0**, commit-count versionCode **1250** passed
`./scripts/test-protracktor.sh --really` in **521 s**:

- **1043 tests / 136 suites**, zero failures/errors/skips; the test task actually executed.
  Includes the previously mistyped navigation filter's seven cases and all six new presentation
  cases, with multi-frame fade sampling, intermediate positions and immediate transport state.
- Store metadata, runner/release-flow fixtures, web page/engine/database upgrade, SAP,
  native audio, icon paths, PWA and local server checks passed.
- Lint **0 errors / 72 warnings**. Issue ID/message/file records equal the archived code-1248
  report; the unchanged total does not hide a new issue.

Minified release APK built in **86.6 s** with the unchanged public test certificate:
[protracktor-release-1.2.0-1250-6043902-20261020-194651.apk](../dist/protracktor-release-1.2.0-1250-6043902-20261020-194651.apk),
**11,907,759 bytes**. SHA-256:
`809da54fa82bd4d57aae4be5aecfe6d63b269b9061ea49373509cfebb62a999d`.

Checked package `com.przunk.protracktor`, version/code, APK v2 signature and certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, absence of the debuggable flag,
executed R8/nonempty mapping, minSdk **29**/targetSdk **36**, and actual engine libraries for
**arm64-v8a / armeabi-v7a / x86_64**. No AAB or publication-key operation.

Ignored `dist/queue-polish-verification/6043902/` retains JUnit/lint reports, full gate/build and
companion logs, negative/focused probes, R8 mapping, manifest/badging, signature and APK contents.
The earlier failed-gate evidence remains under the directory identified above.
[Phone scenarios](TESTING.md#queue-presentation-polish--phone-checks-pending).

**Checked partially:** production-shell host gestures/rendering, domain/controller regression,
full gates/lint and artifact identity. **Unchecked:** phone animation feel/layout, vibration,
TalkBack, audio/service/notification/Bluetooth and long-term usability. System animation/haptic
settings need the phone checks; no bypass was introduced. No merge before owner acceptance.
Develop `d6d3297`, AMP `8e92002` and master `794d8f9` are unchanged. No push/tag/release/schema
or versionName change. Documentation-only closeout does not change this APK's source SHA/code.
Closeout checked 183 existing local link destinations across nine updated documents; application,
native, web and script sources still equal the APK source. No further build is needed for this
documentation-only update.
