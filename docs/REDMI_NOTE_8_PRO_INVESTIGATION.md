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
selector remain dark. This is direct visual evidence of C123; the images do not establish the
system's dark-mode configuration or reproduce the rendering on a test device.

Different action-pill widths are visible, including Playlist and Filter on the right of Random.
The screenshots do not identify which icons, dimensions or alignments C124 specifically concerns.
The original images remain outside Git; no screenshot or personal device data is published.

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

The activity's XML theme is `android:Theme.Material.NoActionBar`. It has no explicit Force Dark
opt-out; the manifest also lacks Xiaomi's documented opt-out metadata. Standard Android Force Dark
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

Clarify the affected controls before deciding whether to equalize action widths, align control
bounds or adjust a particular glyph. Preserve larger-font support and touch targets. Do not
rescale all icons based solely on differently sized button backgrounds.

## Verification state

**Checked:** Git/tag source comparison, startup call-path inspection, both supplied screenshots,
and primary Android/Xiaomi documentation. **Unchecked:** crash reproduction, actual cause, MIUI
configuration, runtime colour values, the exact C124 complaint, and any fix on either phone.
Only documentation changed; no application tests, APK build, code fix, merge, push or release.
