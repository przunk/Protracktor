# Scroll-first queue gestures

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
Full gates/lint and the new APK are pending; phone behaviour remains unchecked.

## Clear queue — discussion only

The owner asks about building a manual queue from an empty starting point. Not implemented or
approved for implementation; the existing no-Clear contract still applies.

Agent recommendation for discussion: clear all future playback (manual requests and source
continuation), leave the currently sounding/paused track unchanged, then play only newly added
manual requests and stop when exhausted. Saved playlists/files/History stay untouched. Clearing
only pending requests would still resume the source and would not deliver the requested blank
starting point. Consider Undo for the cleared upcoming list, with generation protection against
restoring an unrelated source after another explicit Play. Final semantics/control placement and
Undo behaviour need owner agreement; do not silently implement this as part of swipe tuning.
