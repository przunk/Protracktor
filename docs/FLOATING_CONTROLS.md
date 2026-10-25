# B54 — Floating playback controls

## Decision and scope — 2026-10-25

Implement an Android trial on `feature/floating-playback-controls`, based on develop `b52a6d7`.
The motivating use is controlling Random while Google Maps is visible. The panel is optional,
explicitly enabled from expanded Now Playing, and remains available outside Protracktor.
This is a phone overlay, not an Android Auto integration. Acceptance and merging follow phone
testing; application versionName remains 1.4.0 and database schema remains 23.

Selected interaction:

- One movable horizontal panel with Previous, Play/Pause and Next, followed by the playlist heart.
- Dark translucent background with contrasting icons and short visible captions. Primary touch
  targets are at least 64 dp high; the initial panel fits ordinary phone widths. Larger system
  text may increase its height rather than clipping captions.
- A dedicated, labelled drag handle moves the panel after touch slop; tapping a transport button
  does not move it. Remember the position and keep it within usable screen bounds after rotation.
- At ordinary text size, Move/Hide occupy a narrow side column to reduce the covered map area.
  Above 1.25× font scale they use a header and a 2×2 action grid with room for full captions. The initial preferred
  width is 320 dp, clamped to the usable display width; primary targets remain at least 64 dp high.
- A separate Hide action removes the panel without pausing audio. Pause retains the panel.
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
Touches outside the panel must remain available to Maps. The panel must not take keyboard focus.
Some applications and system screens suppress overlays; visibility above every app is not promised.
Use the existing media-playback foreground service, launched from the visible app, rather than a
new background service, accessibility service, screen capture or a second playback controller.

The service owns and removes the window and its Compose lifecycle. Service stop/destruction and
an empty playback session remove it. Temporary loading does not count as an empty session. Hide
changes presentation only. Activation is session-only: process restart does not silently enable
the overlay or start audio. Position preferences are independent of playlists and the database.
An outstanding permission request can complete after activity recreation. Expanded Now Playing
therefore retains its open state across recreation, preserving its result launcher and saved request.
Hide the panel while the screen is locked and restore it after unlock only within that explicitly
enabled service session. Recheck permission during window refresh and handle revocation gracefully.

## Implementation boundaries

- A pure presentation projection reuses PlayerUiState transport availability and PlaylistHeart.
- Pure position rules clamp dragging and restore normalized coordinates after screen changes.
- An Android window host owns attachment, positioning, disposal and the permission check.
- A small Compose panel uses the existing icon/caption actions and Haptics; it delegates all
  playback and heart operations to the same controller used by the notification.
- The Now Playing action owns the permission explanation/return flow. Strings are EN/PL.
- No new dependency, decoder, schema change, queue behaviour or playback policy is required.

Deferred choices: adjustable size/opacity, vertical layouts, auto-start, automatic edge snapping,
minimization to a bubble and additional actions. These are not part of this trial.

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

Focused verification passed **32 tests / 6 suites**, zero failures, errors or skips. Coverage
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

## Test APK and verification

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
