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

Current state: analysis recorded; implementation and verification pending. C118, C120, B53 and
the separate AMP branch are outside this task.

## Platform references

- [Overlay permission and its management screen](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)).
- [Application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY).
- [Window contexts and display configuration](https://developer.android.com/reference/android/content/Context#createWindowContext(int,android.os.Bundle)).
- [Android 12 touch-through restrictions](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events).
- [Applications can suppress overlays](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean)).
