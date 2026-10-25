# B54 — Floating playback controls

## Decision and scope — 2026-10-25

Implement an Android trial on `feature/floating-playback-controls`, based on develop `b52a6d7`.
The motivating use is controlling Random while Google Maps is visible. The panel is optional,
explicitly enabled by holding the dock's Play/Pause control, and remains available outside Protracktor.
This is a phone overlay, not an Android Auto integration. Acceptance and merging follow phone
testing; application versionName remains 1.4.0 and database schema remains 23.

Selected interaction:

- Hold the dock's existing Play/Pause control to show or hide the panel, with haptic feedback and
  an accessibility action label. Ordinary taps retain Play/Pause/Stop. There is no dedicated
  floating-controls button in expanded Now Playing.
- One movable horizontal panel with Previous, Play/Pause and Next, followed by the playlist heart.
- Dark translucent background with contrasting icons and short visible captions. Primary touch
  targets are at least 64 dp high; the initial panel fits ordinary phone widths. Larger system
  text may increase its height rather than clipping captions.
- Drag anywhere on the panel, including over a disabled control. Passing touch slop cancels the
  child button gesture before moving the window. Taps and stationary holds retain their actions.
- Release outside the dismissal target to animate to the nearest left/right edge. Save edge and
  normalized height and keep the panel reachable after rotation or resizing.
- During a drag, show a bounded bottom-centre **× Hide** target. Highlight it with a ring, size,
  caption and haptic change when armed. Only a release inside it hides the panel; cancellation
  and a downward drag elsewhere never hide or pause music. No persistent Move/Hide buttons.
- The preferred width is 288 dp, clamped to the usable display. Ordinary text uses four columns;
  above 1.25× font scale, use a 2×2 grid. Primary targets remain at least 64 dp high.
- A hold on Previous/Next invokes the same previous-file/next-file operations and availability
  rules as the dock. Unavailable file holds must not fall through to ordinary subsong clicks.
- Pause retains the panel. Hiding by drop, the dock hold or an accessibility action preserves audio.
- Controls keep their slots while unavailable, loading or saving. Loading uses the existing
  cancel-loading transport behaviour with an explicit Stop label.
- The heart uses the notification's add-only operation: add to the selected editor playlist and
  save its full working contents immediately, including preceding unsaved edits. Filled means
  present; a second press does not remove music. Reuse existing stale-request and save guards.

## Permission and lifecycle

Request `SYSTEM_ALERT_WINDOW` only after the floating-controls action. Explain the purpose before
opening Android's permission management screen; recheck permission on return. Denial, an absent
settings handler or a rejected window/service must produce feedback and leave playback alone.
The permission is optional; ordinary playback and notifications continue without it.

Use a tightly bounded `TYPE_APPLICATION_OVERLAY` window, not a transparent full-screen layer.
During dragging, a second bounded, non-focusable/non-touchable window shows only the dismissal
target. Its window opacity respects Android's touch-through limit. Remove it on release,
cancellation, lock, reconfiguration, permission failure or service destruction.
Touches outside the panel must remain available to Maps. The panel must not take keyboard focus.
Some applications and system screens suppress overlays; visibility above every app is not promised.
Use the existing media-playback foreground service, launched from the visible app, rather than a
new background service, accessibility service, screen capture or a second playback controller.

The service owns and removes the window and its Compose lifecycle. Service stop/destruction and
an empty playback session remove it. Temporary loading does not count as an empty session. Hide
changes presentation only. Activation is session-only: process restart does not silently enable
the overlay or start audio. Position preferences are independent of playlists and the database.
An outstanding permission request can complete after activity recreation. The permission flow
is registered in the application shell, independently of whether Now Playing is expanded.
Hide the panel while the screen is locked and restore it after unlock only within that explicitly
enabled service session. Recheck permission during window refresh and handle revocation gracefully.

## Implementation boundaries

- A pure presentation projection reuses PlayerUiState transport availability and PlaylistHeart.
- Pure position rules clamp dragging and restore normalized coordinates after screen changes.
- An Android window host owns attachment, positioning, disposal and the permission check.
- A small Compose panel uses the existing icon/caption actions and Haptics; it delegates all
  playback and heart operations to the same controller used by the notification.
- The application shell owns the permission explanation/return flow; the dock delegates its hold
  gesture to that shared action. Strings are EN/PL.
- No new dependency, decoder, schema change, queue behaviour or playback policy is required.

Deferred choices: adjustable size/opacity, vertical layouts, auto-start, minimization to a bubble
and additional actions. Edge snapping and bubble-style drag dismissal were selected in the
follow-up below. System notification bubbles are conversation-specific; this remains an overlay.

## Whole-panel dragging and dock-compatible holds — 2026-10-26

Implement the agreed bubble-style gestures on the same experiment branch while keeping all four
controls visible. Keep activation under the dock Play/Pause hold. A small collapsed circle would
add a tap before Next, so collapsing is not selected. The current overlay permission remains
necessary; this does not bypass the reported restricted-settings refusal.

`FloatingDragContainer` receives native events before the Compose content. It uses raw screen
coordinates, cancels the child press when dragging starts, and suppresses a drag's final Up. This
keeps window movement out of tap/hold detection and prevents either a heart save or file skip
while moving the panel. Multiple pointers and cancellation end the drag without hiding.
`FloatingPanelPosition.snapped()` and `FloatingDropZone` own pure edge/drop geometry.
`FloatingControlsWindow` owns the temporary drop window and cancellable 180 ms edge animation.
Starting another touch cancels animation at its actual position; a plain tap resumes docking
on release. The final Up position is checked again before deciding a drop. A dismissed panel reopens at
its last saved resting position, rather than at the bottom drop target. Accessibility actions
still expose left/right docking, vertical movement and Hide without requiring a drag.

Project the existing file-transport availability and wire both holds directly to the existing
controller's previousFile/nextFile operations. Ordinary transport and playlist-heart semantics
remain unchanged, including current context and subsong handling.

Run focused gesture/window/state/geometry checks and lint, then build a minified release APK.
Do not repeat the full test suite for this iteration, as requested. Host checks must exercise
native parent dispatch as well as Compose buttons; injecting directly into a Compose root alone
would skip the production drag container. Phone acceptance remains required.

Initial focused pass: **49 checks**, zero failures/errors/skips. A controlled disconnection of
child cancellation caused the physical drag/hold regression to fail; it was restored. Three
EN/PL/enlarged-PL host previews were rendered and visually inspected, including the production
native dismissal indicator. The indicator is drawn separately in the host preview because the
offscreen Compose renderer omits AndroidView. These are not screenshots from Maps or a phone.
Final verification against clean source **24976323fc27614eb34bfc5dba6cfc1b734ff577** passed
**51 focused tests / 6 suites**, zero failures/errors/skips. This includes the native parent
dispatch path, one file operation per hold, unavailable-file holds, cancellation, final drop
coordinates, target cleanup and animation interruption on API 29/33. Fresh lint has **0 errors /
79 warnings**; tests and lint took **199.8 s** together. The three additional warnings are two
retired Move/Hide caption resources and `ViewConstructor` for the programmatic drag container,
whose mandatory callbacks deliberately prevent XML/tools instantiation without wiring. The
full test suite was **not repeated**, as requested.

Latest minified, test-signed release **1.4.0 (1313)**:
[protracktor-release-1.4.0-1313-2497632-20261026-001409.apk](../dist/protracktor-release-1.4.0-1313-2497632-20261026-001409.apk),
**12,023,719 bytes**, SHA-256
`b5b5710b8848488d53b3e1a3cfe58304296b3a681b4c80d997c63277e31411b6`.
Build supervision took **131.2 s**; R8 executed. Independent inspection confirms the existing
v2 test certificate, exact embedded source/version/code, non-debuggable manifest, overlay
permission, minSdk 29/targetSdk 36, ZIP CRCs, 16 KB alignment and seven native libraries in each
of the three supported ABIs. Logs, reports, inspected previews and metadata are retained under
ignored `user/floating-controls-verification/bubble-gestures/`. Subsequent documentation commits
do not relabel this artifact.

Develop, master, tag v1.4.0 and the prepared AAB are unchanged. The branch is unmerged.
**Unchecked on a phone:** actual Maps touch-through, whole-panel drag feel, edge animation,
drop target, file holds and permission-restriction resolution. Use the updated
[phone checklist](TESTING.md#b54-floating-controls--phone-acceptance-pending).

## Verification and acceptance

Before handoff: test state projection, geometry, actual panel taps/drag separation, fixed slots
during delayed saves, permission result handling and window cleanup. Exercise production paths;
negative probes must break the relevant assertions. Run the full host gate and lint, inspect a
minified test-signed release APK and retain logs/source identity under ignored `user/` or `dist/`.

Phone behaviour is not verified by those host checks. Test while parked: first grant and denial,
Random above actual Google Maps, all four actions, drag versus tap, remembered placement,
rotation/cutouts, Pause/resume, Hide without audio interruption, locking/unlocking, revocation and
service stop. Confirm heart persistence after reopening the playlist. Check EN/PL and enlarged
text. Configure placement before driving; the trial does not establish driving safety.

## Implementation review and host checkpoint

Initial implementation: focused verification passed **32 tests / 6 suites**, zero failures, errors or skips. Coverage
includes actual panel taps/drag separation, fixed slots during a delayed save, permission denial
and return/recreation, the window configuration and cleanup on API 29/33, language changes,
normalized geometry and screen-coordinate dragging. Two controlled negative probes disconnected
the physical drag callback and bypassed the grant check; the corresponding UI assertions failed.
Both mutations were restored. The full-gate and artifact results are recorded below.

Review refinements:

- Subscribe to the extra presentation projection only while the panel is enabled.
- Preserve fractional drag movement rather than losing slow movement to per-event rounding.
- Release the composition, lifecycle, receiver, preference listener and window-context references
  when closing; Hide never delegates to Pause.
- Center captions in an explicitly width-filling text node. Enlarged-text checks exposed a
  paragraph wider than its measured caption node; the opt-in `fillLabelWidth` mode fixes this
  for overlay actions while preserving the existing action layout elsewhere.
- Retain the permission flow through activity recreation and explain denial without starting
  another playback session.

The trial remains on its branch, ready for phone testing and awaiting acceptance before merging.
Actual operation above Google Maps remains unverified. C118, C120, B53 and the separate AMP
branch are outside this task.

## Play/Pause hold follow-up — 2026-10-25

Phone feedback found the separate activation button too large and visually intrusive. Replace
it with a long press on the existing dock Play/Pause control. Keep its size, circular appearance,
ordinary tap, loading cancellation and transport availability. The hold must not trigger a tap
or repeat when playback progress recomposes the dock. Haptics and the labelled accessibility
action provide feedback without adding a visible row.

Android also refused access to the overlay switch on the phone. The reported system message
is consistent with restricted settings for a sideloaded application; the device model/version
and successful resolution have not been confirmed. Add conditional instructions to the denied-
grant explanation: in Android **Settings → Apps → Protracktor → ⋮ → Allow restricted settings**, if
available, then grant **Display over other apps**. The application cannot grant this permission
for itself, and the new APK does not claim to remove the system restriction. No global security
setting is changed. [Android's restricted-settings help](https://support.google.com/android/answer/12623953?hl=en).

The agent ran **17 focused tests / 2 suites**, with zero failures, errors or skips, covering real
Play/Pause holds versus taps, loading cancellation, the labelled accessibility action, grant
return/recreation, denied-grant guidance and existing dock geometry. A controlled disconnection
of the actual dock hold callback made the grant/show gesture regression fail; the callback was
restored before the passing run. Focused tests and fresh lint completed in **121.5 s**; lint has
**0 errors / 76 warnings**. The full test suite was **not repeated**, as requested. The earlier
1195-test full-gate evidence below belongs only to its original source.

Replacement minified, test-signed release **1.4.0 (1311)**, clean source
**620e0213a4e23b620d32f4cba0327819ca7e7687**:
[protracktor-release-1.4.0-1311-620e021-20261025-231810.apk](../dist/protracktor-release-1.4.0-1311-620e021-20261025-231810.apk),
**12,018,335 bytes**, SHA-256
`b9f20ab3e1caeeda50e0cc2bc4874e3e94bbfff3daa3f14677b331b4f0832b31`.
The supervised build took **161.1 s** and executed R8. Independent inspection confirms the same
v2 test certificate, embedded source, version/code, non-debuggable manifest, overlay declaration,
minSdk 29/targetSdk 36, ZIP CRCs, 16 KB alignment and seven native libraries in each of the three
ABIs. Evidence is retained under ignored `user/floating-controls-verification/hold-followup/`.
Later documentation commits do not relabel this APK. Develop, master, tags and the prepared AAB
remain unchanged. New gestures, actual Maps operation and resolution of the Android restriction
remain **unchecked on a phone**; the branch is unmerged.

## Initial test APK and verification

The agent ran the full host gate against clean source
**9c73ad0441cd256c4937a98df2f053f04542db2f**: **1195 tests / 158 suites**, zero failures,
errors or skips. All 31 unit-stage tasks executed; the unit stage took **462.8 s**, and the
complete gate **678 s**. All companion checks passed, including **53 native/DSP cases**,
web playback/page/database checks, build-runner/release fixtures, icons, PWA and local server.
Fresh lint analysis completed in **107.7 s** with **0 errors / 76 warnings**.

Compared with the preceding 73-warning report, the additional diagnostics are two KTX style
suggestions and a physical-position gravity warning. The Gradle-version notice changed its
suggested version and is not a new functional finding. The overlay deliberately anchors with
`Gravity.LEFT`: drag and saved position use physical screen coordinates, which must not mirror
when changing language. These warnings do not establish device behaviour or require a toolchain
upgrade. Three production-panel host previews (EN, PL and enlarged PL) were rendered and visually
inspected; they are not screenshots from Google Maps or a phone.

Minified, test-signed release **1.4.0 (1309)**:
[protracktor-release-1.4.0-1309-9c73ad0-20261025-225718.apk](../dist/protracktor-release-1.4.0-1309-9c73ad0-20261025-225718.apk),
**12,017,471 bytes**, SHA-256
`6a35fc56c44fbaec655052f27b84aaf9a72e1cb256c00dcf3a70adfa48a68ac1`.
Build supervision completed in **221.7 s**; R8 executed. Independent inspection confirms the
v2 test signature matches the preceding test APK, the exact source revision, non-debuggable
manifest, optional overlay permission, minSdk 29/targetSdk 36, ZIP CRCs, 16 KB alignment and
seven native libraries for each of arm64-v8a, armeabi-v7a and x86_64. This is a sideload test
artifact, not a publishing-signed APK. Subsequent documentation commits do not change its
source identity or versionCode.

Logs, JUnit reports, lint reports, inspected previews, R8 mapping/configuration and artifact
metadata are retained under ignored `user/floating-controls-verification/`. Develop remains
`b52a6d7`; master/tag v1.4.0 and the prepared 1.4.0 AAB are unchanged. Nothing was pushed or
merged, and no AAB or publishing key was used for this task.

**Checked:** host assertions, full gate/lint and APK structure/signature/source identity.
**Partially checked:** production panel presentation through host rendering and interaction tests.
**Unchecked:** installation, actual overlay/Google Maps behaviour, physical gestures,
rotation/cutouts, screen lock and permission revocation timing on a phone. Use the
[phone checklist](TESTING.md#b54-floating-controls--phone-acceptance-pending) before acceptance.

## Platform references

- [Overlay permission and its management screen](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)).
- [Application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY).
- [Window contexts and display configuration](https://developer.android.com/reference/android/content/Context#createWindowContext(int,android.os.Bundle)).
- [Android 12 touch-through restrictions](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events).
- [Applications can suppress overlays](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean)).
