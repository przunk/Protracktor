# Queue row presentation and playlist drag regressions

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
will exercise production gestures, layout and rendered pixels, not just move callbacks.

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

C115 implementation, complete gates and the new APK remain pending. Device fixes are unchecked.
