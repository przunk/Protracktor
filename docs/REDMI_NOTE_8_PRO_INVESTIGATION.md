# Redmi Note 8 Pro startup and appearance investigation

Date: 2026-10-27. Reports: [C122–C124](STATUS.md#c122-redmi-note-8-pro-crashes-at-startup-after-a-previously-working-installation).

## Current disposition

**C122 is under observation, unresolved — decision recorded 2026-10-27.** Retain the findings;
defer further reproduction, startup instrumentation and diagnostic builds until new evidence
or another occurrence warrants returning to the issue. This does not close C122 as fixed or
as a false alarm. C123/C124 retain their separate statuses.

The reproduction work is preserved on unmerged `fix/c122-startup-investigation`, commit
`0134822`, with logs, source hashes and XML under ignored `user/c122-reproduction/`:

- Six synthetic startup scenarios passed on both API 29 and 30 in Robolectric. The final
  targeted run passed 125 tests / 6 suites, zero failures/errors/skips; a deliberately wrong
  restored playlist failed the new startup test on both APIs before restoring the original code.
- Existing native-player/DSP tests passed 41 audio-host + 12 loudness cases under ASan/UBSan.
  Those use a fake output/decoder and do not exercise the affected file through ARM backends.
- Released schema-23 snapshots are identical across v1.2.0, v1.3.0 and v1.4.0. The crash was
  not reproduced; the affected person's data, MIUI behaviour and Play/R8 build remain unchecked.

The test branch has not been merged. Application production sources are unchanged. No APK was
built and no full application gate/lint was run for these reproduction attempts. New reports
should retain the exact version/build, installation channel and circumstances when available.

WEB work is paused until the evening. Its clean branch `feature/web-feature-parity` remains at
`488d3a957047024b220c372ec36a73f3fe1dd927`. This analysis uses develop `3183eee` and release tags
v1.2.0, v1.3.0 and v1.4.0; no WEB implementation is integrated into Android.

## Evidence and limits

The Android 11 / SDK 30 installation crashes at startup after a previously working 1.2.0 or
1.3.0 installation. A different person's fresh installation on the same phone model, Android 10 /
SDK 29, starts and plays. The current build, installation channel and MIUI builds are unknown.
Both software version and retained data differ; the successful fresh installation does not isolate
an Android-version effect. A stack trace cannot currently be obtained; its absence does not
establish any hypothesis.

The agent inspected both supplied 738 × 1600 screenshots under ignored `user/bug/`:
`IMG-20261027-WA0001.jpg` shows Playlist; `IMG-20261027-WA0002.jpg` shows Random. Near-white titles,
captions and dock text have inadequate contrast on light surfaces. Several icons and the Playlist
selector remain dark. This is direct visual evidence of C123, not a reproduction on a test device.

Different action-pill widths are visible, including Playlist and Filter on the right of Random.
The original images remain outside Git; no screenshot or personal device data is published.

### Appearance follow-up — 2026-10-28

The reported configuration is **system Dark mode with app Light**, with Polish labels in the
screenshots. Several configurations were tried, but their individual results, exact MIUI settings
and the installed build were not supplied. System recolouring is suspected; the report does not
establish whether the app switched themes or MIUI transformed selected colours after rendering.

The comparison phone is a Pixel 7 running Android 16, stock Android and English labels; its
buttons and colours look correct in the reported use. Screen dimensions, OS, manufacturer and
language all differ, so this is not a controlled comparison isolating one cause. C124 concerns
the visible icons/buttons; no particular glyph was identified as malformed.

## C122: what the startup path actually does

1. `MainActivity.attachBaseContext` reads the language preference. Activity creation reads
   appearance preferences, then `DatabaseGate` runs a read-only compatibility preflight before
   creating `PlayerViewModel` / `PlaybackController`.
2. `DatabaseAccess.check` rejects a newer schema or an unreadable database without deleting it.
   A successful header/version open does not validate every table, query or later data read.
3. `PlaybackController` starts independent coroutine jobs. `PlaylistSession.restore` reads the
   saved player state, playlists and active playlist tracks, publishes the restored view, then
   starts background metadata resolution. The restored current track is selected without playing.
4. `resolveMetadataInBackground` selects tracks with an empty author and an unresolved title.
   It reads their bytes and calls `NativeEngine.open`, `describe` and `close` while playback is
   idle. Therefore a retained playlist can invoke decoders automatically, whereas a fresh empty
   installation has no matching work. It is not an automatic restart of the last playing song.
5. Startup also loads the native library to obtain the backend fingerprint and configure replay
   paths. Some optional jobs use `runCatching`; playlist restore, initial catalogue/folder reads
   and metadata resolution lack equivalent error presentation. A `SupervisorJob` isolates jobs
   but does not itself handle an uncaught coroutine exception or a native process crash.

### Hypotheses to distinguish

| Candidate | Concrete reason to inspect | Limit / distinguishing observation |
| --- | --- | --- |
| Retained playlist item triggers a decoder fault during background metadata resolution | Existing startup path opens unresolved files without a Play action; no such item exists on a fresh empty install | Source-level mechanism only. An isolated test build that postpones this optional pass can distinguish it; record which file/stage was reached |
| Retained data, stored preference type, or a database/storage read fails | Restore and some initial reads can propagate exceptions; preference reads occur before or during controller construction | No corrupt data is established. Handle failed required startup explicitly while keeping original data intact; a read-only preflight alone is insufficient |
| Native load/initialization or MIUI/Android-specific startup behaviour | Native startup runs before manual playback; Android/MIUI versions and possibly installed ABI differ | Same hardware does not prove identical ABI/build. Existing data versus OS differences remain confounded |

All three release tags have **schema 23**, `minSdk = 29`, `targetSdk = 36`, and the same ABI list
(`arm64-v8a`, `armeabi-v7a`, `x86_64`). There is no new schema migration in the normal
v1.2.0/v1.3.0 → v1.4.0 update. SchemaSql's change in this interval factors an existing query;
it does not add a column/table. An actual older database or damaged schema remains a separate,
unverified possibility. Audio normalization defaults off; its added analysis is not called merely
by restoring a selected track. No specific Android 11 incompatibility has been identified.

### Proposed diagnostic sequence — deferred during observation, not implemented

- Keep the existing installation and data. First add bounded startup checkpoints before preference
  reads, database checking/restore, native setup and optional metadata decoding, including the
  build, SDK and ABI. Make failure information accessible before creating the normal controller.
- Capture uncaught Java/Kotlin failure details locally and preserve normal crash handling. A
  Java handler cannot catch a native signal. No external crash-reporting dependency is needed.
- Android 11 exposes historical process exit reasons through `ApplicationExitInfo`; these may
  distinguish Java/native crash, ANR or low-memory termination. Available records are not
  guaranteed, and Android 11 does not provide native tombstones through `getTraceInputStream`:
  that native-crash capability starts at API 31. See the
  [exit-info reference](https://developer.android.com/reference/android/app/ApplicationExitInfo)
  and [historical exit API](https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,%20int,%20int)).
- Use a narrowly isolated diagnostic option to postpone background metadata resolution while
  preserving playlist restore. A successful launch would implicate that path, without identifying
  the responsible decoder/file or proving a permanent fix. Change one startup variable at a time.
- Match the diagnostic update to the installed signing channel. Local test release APKs use the
  debug certificate; they cannot update a Play-signed installation. A Play-installed affected
  phone needs a compatible Play-delivered update to preserve the failing state. Do not uninstall
  it to make an incompatible APK install.

## C123: light-theme contrast

`ProtracktorTheme` uses Material light/dark schemes. Dynamic colour is disabled below API 31.
`LabelledAction` explicitly gives its icon and caption the same `LocalContentColor`, derived from
`onSecondaryContainer`; those elements nevertheless differ in the supplied screenshots. This
supports checking rendering-time recolouring, as well as inherited text styles and text colours.
The confirmed system-Dark/app-Light configuration strengthens the MIUI recolouring hypothesis
without proving it. Dynamic-colour differences between the phones do not justify unreadable text.

Before this candidate, the activity's XML theme, `android:Theme.Material.NoActionBar`, had no
explicit Force Dark opt-out; the manifest also lacked Xiaomi's documented opt-out metadata.
Standard Android Force Dark
normally excludes a dark platform theme, so absence of an opt-out is not itself proof that it ran.
Xiaomi additionally documents MIUI colour inversion and a manufacturer-specific opt-out for apps
that already manage their own dark theme. See the
[Android dark-theme guide](https://developer.android.com/develop/ui/views/theming/darktheme) and
[Xiaomi integration guide](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1595).

First compare the same app screen/theme with MIUI's per-app forced darkening enabled and disabled.
If it explains the symptom, preserve app-controlled Light/Dark/System and opt out of automatic
recolouring at the documented platform/OEM boundaries. Do not hardcode dark text everywhere:
that would introduce a dark-theme regression. An opt-out remains a proposed change requiring
device confirmation, not a completed fix.

## C124: icon and action geometry

`PlayerIcons` uses a shared 24 × 24 viewport. Slim `LabelledAction` fixes icon size to 20 dp and
height through a shared function, but its width is only a minimum: longer Polish captions can
make a pill wider than its neighbour. Random's Filter and the top-bar Playlist action do not
share an equal-width measurement contract. Glyph ink bounds can also differ within equal boxes.

The follow-up confirms that the complaint concerns the visible icons/buttons. The Playlist and
Filter buttons share a right edge but have different centre axes; WEB and Settings also have
different widths. Label-dependent sizing explains how such differences can occur, without
establishing that language alone caused the reported appearance.

The proposed direction is consistent adaptive widths and alignment within related action groups,
rather than one fixed width across all toolbars. Preserve Polish/English labels, narrow-screen
layout, larger-font support and touch targets. Do not rescale all icons based solely on differently
sized button backgrounds. The candidate below implements this direction; its appearance on
either phone remains unchecked.

## Verification state

### Light palette candidate — 2026-10-28

The requested visual revision is separate from the recolouring opt-outs: a different palette
alone cannot stop MIUI transforming text. `ui/theme/LightColors.kt` provides cool near-white
surfaces, dark ink and restrained blue/teal accents. Existing Material roles distribute the
palette across the UI without per-widget colour overrides. System wallpaper colours still take
precedence when enabled on API 31+; below API 31 the Light fallback is always used. Keep Dark
unchanged. To compare the new palette on a Pixel, select Light and disable System colour palette.

Contrast checks cover enabled foreground/container pairs, secondary text on all surface levels,
and active outlines. Theme-selection checks exercise Light against system Dark and both System
variants on API 29. Host previews cover English/Polish Playlist, dirty actions and Random in
Light/Dark; they are component previews without MIUI or a real playback engine. Final verification
results and the device checklist are recorded after the build.

Eleven focused checks passed across the three new test classes. A deliberately white action
foreground failed the contrast assertion, and the original palette was restored. The agent
generated and inspected five host previews: titles/captions are legible, related controls align,
and Dark retains its prior palette. In a crowded five-action row, long captions may ellipsize
under the existing one-line policy; their full accessibility labels and touch actions remain.
Full verification and the release APK are recorded below.

### C124 candidate — 2026-10-28

On the same unmerged branch, playlist action widths follow the longest localized caption, capped
by available row space divided in physical pixels. Browse and Settings keep opposite edges.
The return-to-playlist action is shared between Browse and Random; it and Random Filter use one
measured width. Glyph size, height policy, callbacks and theme selection stay unchanged.

The Polish production-bar test failed before the change (139 versus 112 px). Five rendered
geometry/touch checks now pass: English/Polish playlist groups, a five-action 320dp row at 2x text,
and English/Polish Random/return widths. This is host layout evidence, not MIUI confirmation.

### C123 candidate — 2026-10-28

Implementation authorized on `fix/redmi-theme-action-alignment`; retain it unmerged until phone
testing. The existing window theme now explicitly sets `android:forceDarkAllowed=false`, and
application metadata sets Xiaomi's documented `force_dark_google=true`. The latter value means
opt out of MIUI inversion, despite its name. No platform theme migration, dependency or app theme
selection change is included. The packaged-metadata regression test failed on the old manifest
on API 29; resource tests also cover the production theme on API 29/30. These cannot exercise MIUI
and do not establish the original cause or prove a phone fix. Verification results follow below.

**Checked:** Git/tag source comparison, startup call-path inspection, both supplied screenshots,
and primary Android/Xiaomi documentation. The 2026-10-28 configuration and complaint clarification
are user-reported evidence. **Unchecked:** crash reproduction, actual cause, exact MIUI
configuration, runtime colour values, and any fix on either phone.
The initial discussion changed documentation only. The candidate above changes resources and
configuration; its device result is still unverified. No merge, push or release publication.

### Final host verification and APK — 2026-10-28

Source **b8717bbaf4b61e8c8920a51f897d2d10a9372b29**, unmerged
`fix/redmi-theme-action-alignment`. VersionName **1.4.0** and schema **23** are retained.

- **Checked:** forced full gate, **1228 tests / 162 suites**, zero failures/errors/skips.
  All 31 unit-test tasks executed; 446.9 s for that stage, **621 s** overall. Store metadata,
  runner/release fixtures, page/engine/SAP/database/native audio/icon/PWA/server checks passed.
  Fresh lint: **0 errors / 79 warnings**, **87.3 s**.
- **Checked:** three preview tests generated five host renders, inspected by the agent.
  The deliberately white action label failed the contrast assertion before restoring the
  candidate palette. These are host checks, not device reproductions.
- **Checked:** minified release APK, test certificate, **1.4.0 (1339)**, build **106 s**,
  **11,975,431 bytes**. Signature, ZIP CRC, 16KB ZIP alignment and non-debuggable/version fields
  verified. Packaged application metadata contains `force_dark_google=true`, and the actual
  window theme contains `forceDarkAllowed=false`. Three native ABIs are present: arm64-v8a,
  armeabi-v7a and x86_64. All 281 recorded application/build input hashes match the candidate.
- **Unchecked:** the original MIUI cause, effective device rendering and visual acceptance on
  the Redmi or Pixel. C123/C124 remain open; C122 remains under observation. No merge or push.

[Release APK](../dist/protracktor-release-1.4.0-1339-b8717bb-20261028-011415.apk), SHA-256
`e690dc82fc5ea72b14cc629ae0c4b7ea65596f3fd8e648baabb5c4cf04894a6f`.
[Light preview](../dist/protracktor-light-preview-b8717bb.png). Private evidence is under ignored
`user/redmi-appearance-verification/`. Subsequent documentation commits do not change the
source identity or version recorded in this APK.

### Device check — planned for Thursday, 2026-10-29

1. Redmi: system Dark, app Light. Inspect Playlist, Random, Settings, dock and system-bar icon
   contrast; reopen the application. Titles, captions and metadata should remain readable on
   light surfaces, with the selected app theme retained. Record the MIUI build and per-app
   recolouring settings with the result.
2. Compare explicit app Light/Dark and System against both system modes. System should follow
   the device; explicit selections should preserve their app palette.
3. Polish labels: compare WEB/Settings and Playlist/Filter widths and icon axes. After a playlist
   edit, check all five actions remain visible and usable, including at an enlarged font size.
   Crowded one-line captions may ellipsize; touch targets and full accessibility labels remain.
4. Pixel: choose Light and disable **System colour palette** to inspect the new fallback.
   Re-enable the switch to compare wallpaper colours, and check Dark separately.

### System-bar clock follow-up — 2026-10-28

App testing after the first candidate reports an unreadable clock with Light selected. The
initial colour and layout changes did not align system-bar icons with the app override.
`MainActivity` called the default `enableEdgeToEdge()`, whose appearance followed system night
mode, while `ProtracktorTheme` separately respected Light/Dark/System. Thus system Dark with
app Light could leave light clock/status icons over light content. This mechanism is distinct
from the still-unconfirmed MIUI recolouring hypothesis.

The real `MainActivity` entry test reproduces the mismatch on API 29 before the fix: the Light
appearance flag is false although app Light requires true. A synthetic newer database fixture
blocks player initialization while retaining the real window/theme setup; no decoder or fake
activity entry path substitutes for production. The fix shares a pure `AppTheme.isDark` decision
between Compose and the window. After AndroidX edge-to-edge setup, set both status and navigation
icon appearances from this effective mode, retaining AndroidX's backgrounds/contrast behaviour.

Four mode combinations are checked on API 29/30: Light with system Dark, Dark with system Light,
and System in daytime/night. These check actual window appearance flags, not rendered OS clock
pixels. An initial API 36 attempt did not execute because Robolectric requires Java 21 there;
the existing Java 17 toolchain is retained. Android 16 behaviour remains unchecked on a phone.
Clean source **c5f0ccb776dd39e829d1a007176993e568d081a8** passes the full gate: **1236 tests /
162 suites**, zero failures/errors/skips; fresh unit execution **424.1 s**, all companion checks
passed, **637 s** overall. Fresh lint: **0 errors / 79 warnings**, **96.9 s**. Eight window cases
and two palette tests also passed in the focused run. Minified, test-signed release **1.4.0
(1341)** built in **100 s**, **11,975,599 bytes**:
[replacement APK](../dist/protracktor-release-1.4.0-1341-c5f0ccb-20261028-091012.apk), SHA-256
`6413c6620a2688b559324256d65e1eef5b76d448e6576a9d15a130174014d03d`.
Signature, version/non-debuggable state, ZIP CRC/16KB alignment and MIUI metadata were checked;
all 282 recorded application/build input hashes still match. Private logs, JUnit XML and receipts
are under ignored `user/system-bars-verification/`. This supersedes build 1339 for the clock fix.
Android 16 and actual OS rendering remain unchecked. Integration after the build is authorized;
merge does not close the outstanding device checks.
