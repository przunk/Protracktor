# WEB visual review

Selected: **2026-10-31**. Branch: `feature/web-visual-review`, from develop `5717b52`.
The previous WEB parity and navigation changes are already integrated. This stage adds a way
to inspect actual browser rendering without requiring screenshots to be supplied manually.

**Repair follow-up, 2026-10-31:** the subsequently approved C126–C129 corrections are complete
on `fix/web-browser-polish`, checked source `30112cf`, and merged with approval as `0e8d660`. All 35 Chromium guards, the full
project gate and lint pass; 90 new originals are captured; all 15 overview sheets and critical originals have been inspected. See the
[repair handoff and current gallery](WEB_BROWSER_FIXES.md#final-verification-and-handoff--2026-10-31).
The failed baseline findings/images below retain their original source and identity; they are
not the result of the corrected run. Local gh-pages `fbd75a7` is prepared; push, public deployment
and physical acceptance remain pending. [Integration record](WEB_BROWSER_FIXES.md#integration-and-pages-preparation--2026-10-31).

**Current capture update, 2026-11-01:** B55 adds open/editing/playing/empty/closed playlist-search
states and a pixel comparison proving that results remain bright while chrome dims. Clean
source `c884b4c` passes **55/55 guards** across the same five profiles. The agent inspected the
five editing originals and the native after-correction bitmap. Positive phone feedback accepts
the Android appearance; physical browser acceptance remains separate. The baseline/repair
records below remain historical. [Current gallery and evidence](PLAYLIST_SEARCH.md#result-emphasis-correction--2026-11-01).

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

The clean-source matrix and image assessment are recorded below; the dated initial observations
remain evidence of what exposed the defects.

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

## Clean-source capture and visual assessment — 2026-10-31

Checked tooling source: **`f1bf550082f7911b938a97e91cea7fe67fdba5ea`**, clean tree.
Browser: **Chromium 156.0.8078.4**, Linux x64. All **130** application/tooling/served-vendor input
hashes remain unchanged through capture. Application sources and runtime dependencies match
develop `5717b52`; this stage changes tooling/documents only.

The full run produces **90 primary PNGs** (eighteen views in each of five profiles), **5 failure
diagnostic PNGs** and **15 overview PNGs**. All original image hashes and viewport dimensions
are checked. The agent inspected all fifteen overview sheets and original critical renders,
including desktop Search, the 360 px Random view and the 320 px scrolled Settings panel.

- **Checked:** real browser setup/launch, fresh project-local library extraction, syntax checks,
  invalid-profile rejection and the complete capture/gallery path. The native five-subsong NSF
  opens through the production WASM/worklet; no decoder message was synthesized.
- **Application run fails, exit 1:** C126 occurs once in every profile after the real History
  input action. No unexpected external request or additional page exception is recorded. A full
  image set is not a passing application test.
- **Visual defects confirmed:** C127 and C128 remain visible across the final profiles. Search
  SVGs measure about **75–221 px**, all black; Random Back measures **24 px** high with native
  grey styling. The compact original also confirms **C129**: Settings content appears above its
  sticky header after scrolling. The header sticks at `top: 0` inside a padded scrolling sheet,
  leaving the top padding strip exposed. Inspect the header's coverage in both themes when fixing.
- **Visual assessment:** the ordinary lists, manual-row indentation, compact player and Dark/Light
  palettes are coherent. Queue remains visible within every captured viewport; no root horizontal
  overflow is measured. Title/path truncation is expected with long fixtures; the 320 px Random
  filter is scrollable through `main`, so its off-screen chips are not classified as missing.
  Settings starts with dense technical/storage details, which is an information-hierarchy
  observation rather than approval to redesign it.

No Android/full application gate or lint was rerun for this tooling-only change. Earlier WEB host
passes remain their own evidence and do not override this real-browser failure. At this baseline
checkpoint the tool was ready with defects recorded; subsequent repairs and integration are
recorded at the top of this document. Windows/Firefox, actual phones, Bluetooth, audible output
and PWA lifecycle acceptance remain unchecked.

### Artifacts

- [Gallery](../dist/evidence/web-visual-f1bf550-2026-10-31T18-27-21-332Z/index.html)
- [Capture/assessment metadata](../dist/evidence/web-visual-f1bf550-2026-10-31T18-27-21-332Z/report.json)
- [Portable report archive](../dist/protracktor-web-visual-f1bf550-20261031.tar.gz): **9,411,735 bytes**,
  SHA-256 `7449623ca06db98d23de92d26b95cde89470f5ad34a053938c5337f486114631`.

The archive contains images, overview sheets, gallery, `REVIEW.md`, diagnostics and `SHA256SUMS`.
It contains no browser binaries, npm installation or live user profile. These are review artifacts,
not a new WEB distribution or an APK.
