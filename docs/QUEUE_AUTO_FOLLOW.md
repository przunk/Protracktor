# Queue automatic-follow flicker

Phone report, 2026-10-21, after the APK 1.2.0 (1258) handoff; installed build ID was not
separately supplied. With Queue open, advance near the bottom of its visible rows. When Next
scrolls the newly playing row into view, the whole list briefly flashes. It should scroll
continuously without fading surviving rows or changing playback/queue semantics.

Continue on `feature/manual-playback-queue`, unmerged. C116 covers this presentation defect;
C111/C112 and AMP remain outside scope. Investigate automatic row following together with the
lazy-item animator. The panel visibility and source traversal generation remain stable during
ordinary source Next, so a panel reopen is not the apparent mechanism.

Host verification will observe rendered pixels through production Queue/Dock and controlled
frames, not only final positions. Establish a failing regression before changing application
code, retain insertion/removal animations and manual grips, run full tests/lint, then supply
a minified test-signed release APK. Phone appearance remains unchecked until retested.

## Mechanism and selected correction

The production-shell pixel regression fails on source `aca3cd6`: after a physical Next crosses
the lower viewport edge, an untouched source glyph changes from `-11975345` to background
`-67585` for two frames, then fades back. In the installed Compose 1.7.6 bytecode,
`animateScrollToItem` finishes with an exact snap; a different index/offset resets the lazy-item
animator's key map. Queue's appearance tween then treats surviving rows as arrivals.

Set Queue appearance fading to null. Keep keyed 220-ms placement and 160-ms disappearance,
including the existing manual-grip placement exclusion. New content is immediately readable;
ordinary following uses the existing shared scrolling policy without a framework workaround,
new scheduling state, dependency or change to the absolute-target/rapid-Next rules.

The initial test attempts used the wrong accessibility selector and then missed an extension
import; neither is behavioural evidence. The corrected physical-action pixel run failed in
22.4 s. With appearance fading disabled, restored verification passed **18 tests / 4 suites**,
zero failures/errors/skips, in **46.9 s**. Covers 40-frame opacity sampling, visible-row Next,
rapid-Next final visibility, manual departure/placement, row layout and bounded manual grips.
No complete gate or new APK at this checkpoint: a full branch review was requested before
final verification/build. Device behaviour remains unchecked; no merge authorized.
