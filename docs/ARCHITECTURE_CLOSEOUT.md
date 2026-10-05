# Architecture stabilization: bounded closeout

Owner decision, 2026-10-05: stop further extractions and finish evaluating the work already on
`feature/architecture-stabilization`. The time cost no longer justified completing the original
seven-file plan. This is a reduced deliverable, not completion of that plan.

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
not scheduled automatically after the APK. The UnExoticA User-Agent remains a separate follow-up.

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

## Technical verification and candidate

Pending final full suite/lint, release APK and artifact/signature inspection. The last extraction
checkpoint passed 639 tests in 87 suites plus lint and the enabled project checks; that is earlier
evidence, not a claim that the final candidate has already been verified. Commands and exact
artifact identity will be recorded here after execution.

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
