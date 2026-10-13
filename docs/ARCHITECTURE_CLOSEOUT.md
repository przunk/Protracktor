# Architecture stabilization: bounded closeout

Owner decision, 2026-10-05: stop further extractions and finish evaluating the work already on
`feature/architecture-stabilization`. The time cost no longer justified completing the original
seven-file plan. This is a reduced deliverable, not completion of that plan.

**Follow-up, 2026-10-06:** the owner reported that most functions worked during phone checks and
authorised merging this refactor to `develop`. The merge is complete locally; no push or release
was performed. The Random-filter availability defect reported afterwards is being handled
separately: its missing opening refresh predates the refactor. The historical verification and
unchecked detailed checklist below describe the original handover, not a claim that every
scenario or long-term behaviour has now been verified.

## Boundary assessment

Reviewed the production wiring and extracted workflows through `6eb5577` against the ownership
map and checkpoint evidence. This is a focused assessment, not a new full-project audit.

| Boundary | Concrete benefit | Cost and closeout decision |
| --- | --- | --- |
| `LibrarySearch`, `ScanBatch` | Source ordering/deduplication and scan checkpoint/cancellation rules can be exercised without constructing the controller | Ports/callbacks model real I/O and progress boundaries; retain |
| `TrackFiles`, `PlaybackReadAhead`, `BrowseCache` | One reader serves playback/share/prefetch; read-ahead map/job has a named owner; cache checks retain existing storage | Android document adapter is thin but isolates resolver handling. Cache observers remain controller-owned; retain |
| `LibraryDownloads`, `ReplayDownloads`, `LibraryMaintenance` | Download/delete ordering and failure/cleanup paths are testable separately from native playback | Numerous progress/text/storage bindings increase setup code. They reuse existing stores and guards, not a new framework; retain without another abstraction layer |
| `PlaylistSession`, `PlaylistTransfer` | Session jobs and persistence ID have an explicit owner; import/export can be tested without activating a playlist | Session/state pair splits an asynchronous operation across two files. The state adapter preserves publication/retry timing and uses the same flows; retain, do not mistake characterization for fixing races |
| `CatalogueBrowser`, `LocalFolderBrowser`, `BrowseWorkflow` | Catalogue reads, folder rendering and navigation/search publication have separate tests; `LocalTree` and `BrowseNavigation` remain rule owners | Most forwarding is in controller setup. State adapters and actions are not independent state owners; retain, but do not extend this into one class per button |
| State definitions and Android adapters | Definitions are readable without the controller; platform resource/file operations can be checked separately | Moving declarations alone does not improve behaviour or performance. Keep the same package and existing adapters; no extra interfaces/factories |

The controller still owns the process scope, state-flow instances, native track, focus, scan/open
jobs and dispatchers. Extracted workflows receive that scope rather than constructing independent
lifetimes. `PlaylistSession` owns its two write jobs; `PlaybackReadAhead` owns its job/map;
`LibraryDownloads` owns its selected-download job. The reviewed constructors do not launch work
or invoke the injected native callbacks. Startup observers and shutdown calls remain in the
controller; `stopPlayback` still clears read-ahead before closing the native track.

There is no new controller object passed into workflows. Controller callbacks still connect
effects such as stop, refresh and scan; this is deliberate coupling, not a fully isolated domain
layer. Multiple workflows publish into the same flows, so the design does **not** establish a
single writer or eliminate timing races. The explicit wiring is substantial; further extraction
needs a concrete change or defect to justify adding more of it.

No extraction-introduced blocker was identified in this focused inspection. Retaining the current
boundaries avoids a second speculative restructuring. This is not evidence that phone behaviour
is unchanged: integration with real dispatchers, providers, native playback and UI still needs the
owner's checks. The existing safeguard tests are retained; their earlier mutation runs are recorded
in `STATUS.md` rather than repeated merely to add another checkpoint.

## Scope left in place

Library/queue editing, sharing and native playback orchestration remain in `PlaybackController`.
`BrowseScreen`, `ProtracktorApp`, `PlaylistScreen`, `web/src/app.js`, `web/src/catalogue.js` and
`native/engine/engine.cpp` are unchanged by this branch. `git diff 1455422..6eb5577` also shows no
changes to build configuration, scripts, database schema or dependencies. No web PR was merged or
rebased into this branch during closeout. Those refactors and a broad comment pass are deferred,
not scheduled automatically after the APK. The UnExoticA User-Agent was left as a separate follow-up;
its implementation and verification are tracked in `STATUS.md` and `PLAN_UNEXOTICA.md`.

## Known risks retained, not repaired

Detailed reproductions are in `STATUS.md`; controlled tests establish the sequences, not their
frequency on a phone. In particular:

- A delayed metadata write can pair a newly selected playlist ID with the previous queue, risking
  replacement of the destination's contents. Save completion can also mark a newer edit clean.
- Older catalogue, folder or search reads can publish after newer navigation. Back can overwrite
  a reentrant parent-read update with its captured snapshot.
- Read-ahead's no-missing-bytes return precedes cancellation of an older fetch.
- The selected-download stopped notice checks `isActive` inside non-cancellable cleanup.

These predate their extractions. Fixes require separate approval and regression tests, not changed
expectations hidden in the refactor. Export important playlists before testing; do not use valuable
lists to deliberately reproduce the write race. Do not uninstall or clear app data to force an
update to install.

**Follow-up, 2026-10-13:** the owner selected the two playlist-write risks for C103, implemented
separately on `fix/playlist-write-safety`. Snapshot ownership, Save completion and Revert/Delete
lifecycle guards now have passing focused regressions and mutation evidence. Full gate passed
768 unit tests and lint (0 errors, 68 warnings). C103 subsequently merged with owner approval at
`f4c9bf1`; individual playlist phone checks remain unreported in
[Status](STATUS.md) and the
[C103 handoff](PR_C103_PLAYLIST_WRITE_SAFETY.md). The other risks above remain open; this follow-up
does not rewrite what the refactor itself preserved.

## Technical verification and candidate

Technical closeout completed on 2026-10-05; phone acceptance is still pending. Scope/review record:
`db925f7`. Limited comment corrections: `3defe38` (Undo token, index playability, persistence target,
duplicate switch/debounce explanation and Browse ownership). The source diff was inspected in
context: four files, comment text only, no executable changes. The broad comment pass was not done.

**Checked:** at clean source commit `3defe38dbf35e1adb4fb34f71b913bd3d189cd9e`,
`scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` passed **639 tests in 87 suites**, zero
failures/errors/skips, in 219 seconds overall. XML timestamps and the executed test task were
checked; the results were not reused from the previous checkpoint. Lint completed without errors.
The same run passed 581 page checks, 49 existing-WASM checks, 8 SAP checks, 135 icon paths,
the web v3→v4 storage upgrade, 8 server checks and store metadata validation.

The initial sandboxed attempt failed before tests because Gradle could not obtain a usable wildcard
IP for its local locking service. Its displayed counts came from older XML and are **not** a pass.
The permitted run outside the sandbox produced the fresh results above. Existing shared JDK
17.0.19 and the isolated build/cache paths from `STATUS.md` were used, without diagnostic JVM
overrides. `GRADLE_OPTS='-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2'` also limited lint.

**Checked:** `scripts/build-release.sh --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` completed in 690 seconds from the same clean
commit. All three native ABIs built; R8 and resource shrinking ran. No publication credentials,
release script, version-name change or branch synchronization was used. `CMAKE_BUILD_PARALLEL_LEVEL=2`
was set for the build. The script-produced artifact was given an explicit release/SHA filename
without changing its contents, then copied byte-for-byte to the main checkout's `dist/`.

| Candidate property | Verified value |
| --- | --- |
| File in the main checkout's `dist/` | `protracktor-release-1.0.0-1076-3defe38-20261005-215545.apk` |
| Application / version from APK | `com.przunk.protracktor`, `1.0.0 (1076)` |
| Source commit | `3defe38dbf35e1adb4fb34f71b913bd3d189cd9e`, clean at build time |
| Size | 11,678,630 bytes |
| APK SHA-256 | `b218bfe0dfc39e91b9ba015424fa17db387fcf40cf4dae070139d88a00fd7ba3` |
| Native ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Signature | APK v2 verified; Android Debug/test certificate, **not for publication** |
| Certificate SHA-256 | `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e` |

The certificate matches the preserved `protracktor-1.0.0-1057-20261002-002729.apk`; the new build
number is higher. It does **not** match the separately test-signed baseline
`protracktor-release-1.0.0-1055-2aaeeeb-20261004-234643-test-signed.apk` (certificate starts
`feee33c9`). The installed phone copy and Google Play signing certificate have not been inspected:
do not assume either can accept this APK as an update. If Android refuses it, retain the installed
app and its data; resolve signing/version compatibility rather than uninstalling. Baseline APKs
were not overwritten.

Evidence is local to the implementation worktree under `dist/architecture-closeout/`: final unit
XML, full test/lint/gate logs, release build/Gradle logs, APK metadata/signature reports, reference
certificate reports and R8 mapping. `pre-run-*` and `sandbox-*` files are explicitly not final-run
evidence. The later handover documentation commit does not change this candidate's source identity.
Local Markdown link targets and `git diff --check` were checked.

**Partly checked:** host workflow tests, Android compilation/minification and artifact inspection
confirm those properties, not end-to-end playback or UI equivalence. The WASM checks exercise the
existing artifact; no web/native refactor or new packaged-web handover was performed.

**Not checked:** installation, real document providers, gestures, playback/audio equivalence,
real-browser operation, performance and long-term stability on the owner's phone. The known
pre-existing races above remain. No merge or release acceptance is implied.

## Owner's phone checklist — not checked

Record the APK SHA/build, device/Android version, scenarios and observations. Long-term acceptance,
merge and release remain separate owner decisions.

1. After a compatible update, confirm existing playlists/settings and startup without autoplay.
2. With disposable playlists: add a track, try a duplicate, reorder/remove/Undo, Save/Revert,
   switch playlists, then restart. Export/import one and compare its name and tracks.
3. Re-enter **Browse online catalogue**, navigate catalogue/format/author and Back, then use
   **Add to playlist**. Confirm the expected Browse location and that adding does not dismiss it.
4. Search local/online/by-platform, change filters, navigate away and return. Distinguish any
   late-result issue from the known risks above rather than assuming it was fixed.
5. Open a granted folder, scan/cancel/resume, enter subfolders during scanning and check that the
   level stays put. Reopening alone must not start a scan.
6. Fetch a small catalogue, play it offline and test cache/index removal only for replaceable data.
   Confirm progress clears and unavailable data is labelled; check PL/EN notices.
7. Play local and remote tracks; try next/previous, seek, subsongs, Random and read-ahead. If used,
   check replay-dependent formats and a companion-file format too.
8. During normal listening, test screen-off/background playback, audio interruptions, returning to
   the app and later restarts. Compare perceived responsiveness with the existing release;
   no performance improvement or long-term stability has been established on the host.
