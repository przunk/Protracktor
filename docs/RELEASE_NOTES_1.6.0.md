# Protracktor 1.6.0 — release notes

Prepared 2026-11-05. Scope: `v1.5.0..v1.6.0`. Release source is **ca854cb1**, tagged **v1.6.0**,
version **1.6.0 (1467)**. The AAB is prepared; Play upload and availability are not yet confirmed.
The GitHub APK is prepared for final signing with the existing release certificate. GitHub
Release publication remains pending; public Git references have been verified separately.

## Music alarms — requested and refined through tester feedback

- Prepare an offline excerpt with **Use for alarms** in a track menu or expanded Now Playing.
  Manage prepared music and alarms through **Browse → Alarms**.
- Create multiple one-off or repeating alarms with a name, weekdays and selected music. Saving
  an edited disabled alarm enables it; the switch can disable it again.
- Dismiss an alarm by recognizing the artist, platform, year or title from three answers,
  depending on the available metadata. A wrong answer selects another eligible question;
  an emergency three-second hold remains available.
- The ringing screen has an animated alarm and a short completion confirmation. The hold
  tolerates finger movement; deleting an alarm offers Undo.
- Prepared audio is independent of the ordinary music cache. Alarms pause ordinary music while
  retaining its playback context, without automatically resuming it on dismissal.
- Android access requirements are explained in the alarm view: exact scheduling, notifications
  and full-screen alerts. Screen presentation remains subject to Android and device policy.

The feature remains an enabled Android experiment. Its physical-device checklist is in
[Testing](TESTING.md#music-alarm-phone-trial); host checks do not establish every device's delivery,
lock-screen, reboot or audio behaviour.

## Browse navigation

- Destinations are ordered as Local filesystem, Saved places, Online catalogues, Random, Search,
  Alarms and History. Alarms is reached through Browse rather than Settings.
- Browse scrolls when its destinations do not fit on the screen.

## Verification and artifacts

Agent-run checks on the unchanged tag pass **1,472 Android tests in 213 suites**, all **85 gate
stages**, and fresh lint with **0 errors / 81 warnings**. All **1,008 tracked files** remained
unchanged through packaging. The full gate took 2034.1 seconds; APK packaging took 204.5 seconds.

APK inspection confirms its package/version, non-debuggable release configuration, retained
shrinking, alarm permissions, three native ABIs and ZIP integrity. ZIP alignment and 64-bit ELF
load-segment alignment were checked against the [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes).
This is host/package evidence; no phone test of this APK is recorded.

The unsigned intermediate is **12,369,427 bytes** and is not an installable release asset.
Final signing must retain the certificate used for previous GitHub APKs; the signed APK and its
SHA-256 file are the two upload assets. Preparation evidence is summarized in
[Status](STATUS.md); the unchanged AAB record is in the [release ledger](../store/play-console/releases.md).

Tagged Play notes: [English](https://github.com/przunk/Protracktor/blob/v1.6.0/store/listing/en-US/release-notes/default.txt)
and [Polish](https://github.com/przunk/Protracktor/blob/v1.6.0/store/listing/pl-PL/release-notes/default.txt).

## Known issue

The previously reported startup crash on Redmi Note 8 Pro with Android 11 remains under
observation; this release does not establish a fix. [Retained findings](REDMI_NOTE_8_PRO_INVESTIGATION.md#current-disposition).

[Full changelog](https://github.com/przunk/Protracktor/compare/v1.5.0...v1.6.0).
