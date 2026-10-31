# WEB visual review

Selected: **2026-10-31**. Branch: `feature/web-visual-review`, from develop `5717b52`.
The previous WEB parity and navigation changes are already integrated. This stage adds a way
to inspect actual browser rendering without requiring screenshots to be supplied manually.

## Scope

Use a pinned Playwright installation with a local headless Chromium. Keep tooling dependencies
separate from the WEB application's package and store browser binaries/profiles under ignored
`user/web-visual/`. Do not install system packages, change the shared toolchain or introduce a
production dependency. Browser installation is an explicit setup step, not part of normal tests.

Capture the production page from the local project server, using isolated browser contexts and
demonstration data. Use production storage APIs to seed data, actual UI actions to reach views
and the real WASM/worklet for playback. Record fixtures/network interception explicitly; do not
replace CSS, UI rendering or audio APIs to obtain a screenshot. Keep external archive requests
out of repeated captures. Never reuse a person's live browser profile or retained library.

Cover desktop and phone viewports, EN/PL and Dark/Light. Include the empty state, playlist,
collapsed/expanded player, Queue, Browse/catalogues, Random/filter, History, Search, Saved places
and Settings. Save a browsable image gallery, per-image metadata and browser error/layout
diagnostics under `dist/`, identified by source SHA and dirty state. Inspect generated images
before reporting visual findings; successful capture alone is not visual acceptance.

This stage may report UI defects but does not authorize unrelated application changes. Changes
to application behaviour need their own reviewed scope and full regression checks. Do not
publish, change release versions or merge this new tooling branch automatically.

## Evidence and limits

Headless Chromium can establish actual DOM/CSS rendering, hit targets and the scripted browser
interactions that are run. A phone viewport is responsive-layout emulation, not a physical phone
or Android emulator. It does not establish Windows/Firefox compatibility, touch feel, audible
quality, physical Bluetooth routing, OS lock-screen controls or installed-PWA update behaviour.
Keep [C104](STATUS.md#c104--www-headsetmedia-buttons-do-not-control-playback-physical-verification-pending)
and the remaining [browser trial](WEB_PARITY_CHECKLIST.md) open.

## Run

```bash
./scripts/setup-web-visual.sh
./scripts/capture-web.sh --list
./scripts/capture-web.sh
./scripts/capture-web.sh --profile phone-en-dark
```

Setup downloads the locked development dependency and its matching Chromium headless shell.
On Ubuntu 24.04 x64, if required, it unpacks three checksum-pinned Ubuntu libraries under the
project's ignored directory. It never installs/registers a system package. Other platforms need
their normal Chromium libraries already available. The local library bundle is for this isolated
loopback/fixture tool, not a general-purpose browser installation. Package source URLs/checksums
are retained in `user/web-visual/linux-libs/receipt.json` after extraction.

Capture starts and stops the existing project server on a private loopback port. Each profile
uses a fresh context, production IndexedDB APIs and actual UI events. Catalogue, History and
bookmarks are synthetic; intercepted Modland file requests return generated ProTracker tones or
a real five-subsong NSF whose CPU routines are silent. AudioWorklet and WASM are not mocked;
headless output is muted. Service workers are blocked to keep this run on the inspected sources.
This is not a network/archive, PWA-update or audio-quality test.

Default profiles: desktop 1440×960 EN/Dark and 1280×800 PL/Light; phone layouts 412×915 EN/Dark,
360×800 PL/Light and a compact 320×640 EN/Light stress case. The runner captures eighteen views
per profile and an additional diagnostic image on failure. A phone-sized context does not
identify a particular physical handset. EN/PL and Dark/Light are sampled, not every combination.

The last line identifies `dist/web-visual-<SHA>[-dirty]-<timestamp>/index.html`. That gallery links
to the PNGs; `report.json` records input/image hashes, browser, viewport, fixture requests, page
errors and layout measurements. Per-profile overview PNGs group six images for inspection.
Original PNGs remain available for reading text at native resolution. Partial rows in a scrolling
list can extend below the viewport; raw bounds alone do not establish a defect.

Unknown options, zero profiles, failed captures and browser page errors fail the run. Artifacts
are retained on failure. A complete screenshot set can therefore accompany a failing application
check; do not describe it as a green browser test or approve the appearance without inspecting it.
No automatic screenshot baselines are accepted or updated by this tool.

## Initial browser finding — C126, 2026-10-31

The first matrix runs from develop `5717b52` with uncommitted tooling reproduce an uncaught
`TypeError: Illegal invocation` after typing into History search, in all five Chromium profiles.
`HistoryBrowser` calls a retained native timer with its own class instance as the receiver.
The exception precedes the read/error handler and leaves the result view loading/empty. The
existing Node/jsdom paths do not enforce Chromium's native Window receiver requirement.

This is an agent-run browser reproduction using the real input event, not a reported phone
observation. It establishes C126; it does not repair it. Full screenshot capture/visual inspection
continues, while the runner correctly returns failure for this application error.

The final source identity, screenshot archive and image assessment will be recorded after the
tooling commit and the clean-source matrix run.

## Initial visual findings — 2026-10-31

The agent inspected all three phone EN/Dark overview sheets and the original Search, History and
Random PNGs before recording these findings. Final matrix inspection follows the tooling commit.

- **C127 — Search scope controls lack theme styling.** Saved playlists, Online catalogues and
  By platform render as grey browser buttons with large black SVGs. `SearchScopesView` emits
  `.chip` buttons, but the base chip colour/padding/SVG sizing is scoped to `.randomfilter`.
  Search's own selectors supply flex/selection rules only. This is a visible integration defect,
  not a new visual design proposal; inspect catalogue/platform sub-options in the repair too.
- **C128 — Random Back does not share Browse's appearance.** The real Random view renders Back
  as a small grey native button. The themed plain-button selector requires `.browseview`, while
  `sessionbar` is inside `main`. The recently aligned navigation actions reach the right views,
  but their visual treatment does not match. No CSS fix is included in this tooling stage.

The compact dock retains its separate Queue action and Tune footer in the inspected 412 px
images. Long titles/path text truncate; this alone is not an overlap defect. Earlier History
fixture rows lacked the replayable flag and were intentionally dimmed by production rendering;
the fixture now supplies valid replayable entries. Do not report that fixture omission as an
application bug. Native touch highlights are allowed to settle for 350 ms before capture;
no highlight or application style is overridden.
