# Queue row presentation and playlist drag regressions

Integration update, 2026-10-21: included in the full trial accepted after phone testing and
merged as **3bc5a98**. [Acceptance record](QUEUE_BRANCH_REVIEW.md#acceptance-and-integration--2026-10-21).
Earlier pending/unmerged statements below are historical; granular phone results were not listed.

Subsequent 2026-10-21 follow-up: [C116](QUEUE_AUTO_FOLLOW.md) and the [complete branch audit](QUEUE_BRANCH_REVIEW.md)
produce current source **1a1503f**, APK **1.2.0 (1261)**, with 1096 tests/full gates/lint passed,
unmerged and awaiting phone testing. The APK 1258 evidence below is historical.

Owner phone feedback, 2026-10-20, following APK 1.2.0 (1255), source `1aec1aa`.
Installed build ID was not separately reported. Continue on `feature/manual-playback-queue`;
no merge authorized. C111/C112 and AMP remain outside scope.

## Reports and mechanisms

- C113: Clear introduces left indentation. The retained occurrence becomes a manual one-shot;
  presentation applies manual indentation even with no source rows left. Indentation should
  distinguish inserts inside a context, not indent an entire standalone manual list.
- C114: a current manual row leaves its indented margin unhighlighted. Padding is outside the
  ListItem container. Paint and interaction must cover the full row; indent its content only.
- C115: playlist reorder can hide a downward-dragged row behind neighbours, follow the first
  row to the top instead of keeping it under the finger, or lose its grip after a small move.
  The new swipe wrapper clips and stacks outside the translated ListItem. Switching its enabled
  branch at drag start also replaces the grip subtree, cancelling pointer ownership. LazyColumn's
  keyed first-visible-item anchoring follows the dragged first item when its data index changes.

The phone observations are the reproduction evidence for device behaviour. Host regressions
exercise production gestures, layout and rendered pixels, not just move callbacks.

## Selected correction

1. Indent manual content only when source rows are visible. The full row, including that inset,
   shares the playing highlight and tap/hold area. Clear/Undo must not change transport semantics.
2. Keep the playlist swipe subtree stable throughout a grip gesture, using its existing ownership
   gate rather than changing enabled state. Translate/elevate the outer lazy item, not its clipped
   child. Keep the numeric viewport anchor across reorder, so item identity cannot drag the view.
   Preserve stable row identity and handle successive moves before recomposition; release/cancel
   must clear the offset. Do not weaken horizontal swipe thresholds or manual-block boundaries.
3. Verify focused regressions and negative probes, review touched paths, run the complete forced
   suite/lint, build a minified test-signed release APK and record phone checks. No dependencies,
   schema, native/web/AMP, saved-data semantics or versionName changes.

## Presentation checkpoint

C113/C114 are implemented: standalone rows align with source content, while inserts within a
visible context retain their indentation. Background and tap/hold wrap the entire row before
content padding. Negative regressions failed on the old implementation: Clear moved the title
from x=112 to x=144; rendered inset/body pixels differed; holding the margin did not open Info.
The corrected run passed **20 tests / 4 suites**, zero failures/errors/skips, covering layout,
Clear/Undo, presentation animations/haptics and manual grips. Rendered-pixel sampling is shared
with the existing animation regression, using the same hardware RenderNode path.

## Playlist-drag checkpoint

C115 is implemented. The outer swipe item owns translation and z-order, while its subtree stays
enabled and uses the shared swipe-ownership gate during a grip. Numeric viewport anchoring runs
in SideEffect after the new item provider is composed; a synchronous request inside onMove can
remeasure old data and consume the override too early. Gesture-local order bridges event batches,
and live membership changes cancel safely. Membership is cached across pointer events rather
than rebuilding whole-playlist sets for every pixel. Release/cancel clears the offset and lock.

The initial corrected run passed 28 tests / 4 suites, including six playlist gesture/rendering
cases and existing queue layout, manual grips and swipe tests. Reintroducing the old enabled
branch failed the actual-grip regression; removing viewport pinning and lowering the outer layer
each failed its regression. The first batching probe incorrectly allowed time between moves and
did not fail; zero-delay moves now share a frame and the stale-index mutation fails as intended.
All fault injections are restored before final verification. The optional ignored Gradle init
script disables fail-fast only for the multi-probe run, never normal gates or builds.

Tests select the unmerged grip Icon, not the merged clickable row. They measure a descendant's
draw-transformed bounds for finger tracking: outer wrapper semantics describe its allocated slot.
Preliminary whole-row gesture/slot-coordinate attempts are not counted as reproductions.

Final restored combined verification passed **147 tests / 14 suites**, zero failures/errors/skips,
in 98.4 s. Complete gates and artifact checks are recorded below; device fixes are unchecked.

## Final verification and APK

2026-10-21: clean source **aca3cd6**, commit count **1258**, passed the forced complete gate in
**545 s**: **1090 tests / 141 suites**, zero failures/errors/skips, all companion checks and lint
**0 errors / 72 unchanged warnings**. Warning ID/severity/message/file records match source
`1aec1aa` exactly, ignoring shifted line numbers. Unit tests took 371.3 s and lint 88.5 s.

Minified test-signed release APK built in **104.1 s**:
[protracktor-release-1.2.0-1258-aca3cd6-20261021-001730.apk](../dist/protracktor-release-1.2.0-1258-aca3cd6-20261021-001730.apk),
**11,915,735 bytes**. Package `com.przunk.protracktor`, versionName `1.2.0`, code `1258`,
minSdk 29/targetSdk 36, absence of debuggable flag, executed R8/nonempty mapping and engine
libraries for arm64-v8a, armeabi-v7a and x86_64 checked. APK Signature Scheme v2 verified with
public test-certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
This is a release-variant test APK, not a Play upload artifact. APK SHA-256:
`f7d0396c6080b908900a536b081a9d5d13b34dd48b2e444bf4fab5ed2777904f`.

Ignored `dist/row-regression-verification/aca3cd6/` retains JUnit/lint reports, full/companion/
build logs, focused/negative probes, mapping, badging/manifest, signature and ZIP contents.
The earlier `fffbafc` layout checkpoint is not the final artifact source.

**Checked partially:** host Compose gestures/layout/rendering, complete gates/lint and artifact
properties. **Unchecked:** phone drag ownership/viewport/visual comfort, audio, notification,
Bluetooth, TalkBack and long-term use. [Phone checklist](TESTING.md#queue-row-and-playlist-drag-corrections--phone-checks-pending).
Owner acceptance and merge remain pending. No push/tag, schema, dependency, native, web, AMP or
versionName changes. Develop `d6d3297`, AMP `8e92002` and master `794d8f9` are unchanged.
Documentation-only closeout does not relabel or rebuild this APK.
Closeout checked 223 existing local link destinations across ten updated documents and the new
handoff/checklist headings. Application, native, web and build-script sources still match the
APK source; no additional application build is needed for this documentation-only update.
