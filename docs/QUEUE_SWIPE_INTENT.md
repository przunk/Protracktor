# Scroll-first queue gestures

Ready for phone testing: **APK 1.2.0 (1252), source f6a5ee4**, unmerged. Final evidence below.

Owner feedback, 2026-10-20, after the [presentation handoff](QUEUE_PRESENTATION_POLISH.md).
Latest supplied build: APK 1.2.0 (1250), source 6043902; installed build ID was not separately
reported. Continue on `feature/manual-playback-queue`, unmerged.

## Report and mechanism

Up/down scrolling can accidentally move a row horizontally. The shared horizontal drag detector
claims movement after the platform's small horizontal touch slop, without a stronger horizontal
intent requirement. Increasing only the release/commit threshold would leave this capture intact.
The correction must arbitrate before consuming pointer movement, not just suppress its action.

## Selected implementation scope

- Delay row movement until horizontal travel exceeds max(24 dp, twice platform touch slop).
- Require horizontal displacement to exceed twice vertical displacement. Ties and meaningful
  diagonals favour vertical scrolling. Once vertical intent/parent consumption wins, leave the
  rest of that pointer gesture to scrolling, even if it later drifts sideways.
- Once deliberate horizontal intent wins, retain reveal colours/text, reversal/reset and the
  existing release threshold. Commit at most once on release; do not weaken distance checks.
- Preserve taps/holds, accessible actions, grip ownership, selection disabling and Undo. All
  track lists use this one wrapper, so the fix must cover both queue directions and right-add
  elsewhere without introducing a second gesture policy.

No transport/source, saved data, dependencies, schema, native/web/AMP or versionName changes.
No merge. Host pointer tests must exercise a real scrolling parent, not just a helper. Full host
gates/lint and a minified release APK remain required. Device gesture feel remains unchecked.

## Implementation checkpoint

The original real-list wobble/upward-scroll test failed with the add reveal still present
(`/tmp/protracktor-scroll-first-before.log`, **37.4 s**). The new shared policy is a pure
distance/direction decision. Pointer handling waits without consuming movement until it wins;
vertical intent is latched, and the Final pass observes a scrollable parent's consumption.
Grip permission is captured before any swipe could cancel/release the grip's ownership.

A larger dead zone exposed another boundary: an abandoned short drag could reach a child's click
handler. Its physical regression failed (**25.4 s**, `/tmp/protracktor-scroll-first-short-press.log`).
The wrapper now remembers movement beyond platform slop and consumes an abandoned release in
Initial, before a child can play, without consuming its earlier scroll movements. This also covers
unsupported left swipes outside Queue and returning to the starting position. True taps remain
available. Cancellation/multiple pointers cannot commit a partial swipe.

Deliberate action tests now travel 45% of row width, including the larger activation dead zone;
the existing 30%-of-width/minimum-56-dp release threshold is not reduced. An old 280-pixel test
stroke no longer cleared that combined distance in the host configuration; its action-distance
fixture was adjusted rather than weakening the new threshold.

The restored first 30-case run passed in **39.1 s**; after abandoned-click protection, **37 tests
/ 5 suites** passed in **55.0 s**, covering a real scrolling parent, both directions and diagonals,
direction locks, intentional swipes, taps/holds, cancellation, grips, Undo and presentation.
Lowering horizontal dominance from 2:1 to 1:1 failed the real diagonal-scroll regression in
**24.8 s** (`/tmp/protracktor-scroll-first-direction-negative.log`); the original policy was
restored afterwards. Final restored combined run passed **98 tests / 9 suites**, zero failures/
errors/skips, **67.0 s** (`/tmp/protracktor-scroll-first-final-focused.log`). It includes scheduler/
controller, scrolling/direction policy, queue shell, Info/animation, manual grips and navigation.
At that checkpoint full gates/lint and the new APK were pending. Final evidence follows;
phone behaviour remains unchecked.

## Final verification and APK

Clean source **f6a5ee4**, versionName **1.2.0**, commit-count versionCode **1252** passed
`./scripts/test-protracktor.sh --really` in **531 s**:

- **1056 tests / 137 suites**, zero failures/errors/skips; tests actually executed, not up-to-date.
  Includes the real scrolling-parent regressions, abandonment/cancellation protection, supported
  and unsupported directions, normal taps/holds, grip ownership, Undo, presentation and navigation.
- Store metadata, runner/release-flow fixtures, web page/engine/database upgrade, SAP, native
  audio, icons, PWA and local server checks passed.
- Lint **0 errors / 72 warnings**, equal to the archived code-1250 issue ID/message/file records.

Minified test-signed release APK built in **96.3 s**:
[protracktor-release-1.2.0-1252-f6a5ee4-20261020-223105.apk](../dist/protracktor-release-1.2.0-1252-f6a5ee4-20261020-223105.apk),
**11,907,283 bytes**. SHA-256:
`c228e109f54247ffb2957bd44d0da0a307149c561bc4f8e5dc095b4235795024`.

Checked package `com.przunk.protracktor`, version/code, APK v2 signature with the unchanged public
test certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`,
absence of debuggable flag, executed R8/nonempty mapping, minSdk **29**/targetSdk **36**, and actual
engine libraries for **arm64-v8a / armeabi-v7a / x86_64**. No AAB or publication-key operation.

Ignored `dist/swipe-intent-verification/f6a5ee4/` retains JUnit/lint reports, full gate/build and
companion logs, focused/negative probes, mapping, manifest/badging, signature and APK contents.
[Phone scenarios](TESTING.md#scroll-first-row-gestures--phone-checks-pending).

**Checked partially:** host pointer/scroll behaviour, domain/controller regressions, full gates/
lint and release artifact identity. **Unchecked:** real device touch comfort/speed, TalkBack,
audio/service/notification/Bluetooth and long-term use. Phone acceptance and merge remain pending.
Develop `d6d3297`, AMP `8e92002` and master `794d8f9` are unchanged. No push/tag, schema, versionName
or store change. Documentation-only closeout does not relabel this APK. Clear queue below was
discussion-only at that handoff; its subsequent approval is recorded separately.
Closeout checked 195 existing local link destinations in nine updated documents; application,
native, web and script sources still match the APK source. No additional build is needed for
this documentation-only update.

## Clear queue — initial discussion, subsequently approved

The owner initially asked about building a manual queue from an empty starting point. It was
not part of this swipe fix. Subsequent explicit approval supersedes the old no-Clear restriction;
see [the selected contract and implementation record](QUEUE_CLEAR.md).

Agent recommendation for discussion: clear all future playback (manual requests and source
continuation), leave the currently sounding/paused track unchanged, then play only newly added
manual requests and stop when exhausted. Saved playlists/files/History stay untouched. Clearing
only pending requests would still resume the source and would not deliver the requested blank
starting point. Consider Undo for the cleared upcoming list, with generation protection against
restoring an unrelated source after another explicit Play. Final semantics/control placement and
Undo behaviour were open at this stage. The later task selects a separate Clear operation, not
an unannounced part of swipe tuning.

The later Clear follow-up is ready at source **1aec1aa**, release APK **1.2.0 (1255)**, with
1080 tests/full gates and lint passed. [Current handoff](QUEUE_CLEAR.md#final-verification-and-apk)
remains unmerged and requires phone acceptance; APK 1252 above is its historical predecessor.
