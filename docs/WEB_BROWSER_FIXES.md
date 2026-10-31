# WEB browser findings: C126–C129

Selected: **2026-10-31**. Branch: `fix/web-browser-polish`, from visual-tooling branch head
`97669fd`. The explicit repair request follows the
[real-browser visual review](WEB_VISUAL_REVIEW.md). Keep the tooling branch as the parent so the
same production-page capture path proves the fixes. Do not merge or publish without acceptance.

## Scope and sequence

1. **C126:** preserve the native Window receiver for History's default debounce/cancel timers.
   Retain the 200 ms policy, latest-query ownership, paging, playback context and injectable test
   clocks. Cover rapid typing, clearing and leaving during debounce through actual browser input.
2. **C127:** give Search scope controls bounded themed icons and readable text, including
   catalogue/platform choices, selected/disabled states and keyboard focus. Keep scope semantics,
   result ordering and recent-query suggestions unchanged.
3. **C128:** share Browse's Back styling with Random's destination header without changing either
   navigation action or introducing another dice control.
4. **C129:** cover the scrolling Settings sheet's top inset with its sticky header. Retain initial
   spacing, Close reachability, rounded clipping and desktop/phone layouts.

Each fix is a separate commit with its corresponding regression guard. Reproduce failure before
the correction, exercise the production path, then compare actual rendered images. UI tests must
check rendered state or hit/paint coverage, not just CSS strings. Run the five-profile matrix,
inspect its overview/original images and run the complete project gate/lint before handoff.
No APK, release-version change, engine rebuild, AMP/slider integration or Bluetooth claim is part
of this scope. Record exact source and checked/unchecked states at each checkpoint.

The baseline defects and original images remain recorded in
[STATUS](STATUS.md#c126--web-history-search-throws-when-using-the-native-browser-timer).
Implementation and verification results follow below as each stage completes.

## C126 focused checkpoint — 2026-10-31

Default timer adapters now call the global timer owner explicitly; injected clocks and the
200 ms/latest-query policy are unchanged. A new host regression fails before correction and all
**31** History filter/ownership cases pass afterward. The actual Chromium phone-input regression
also fails before correction (rapid edits/clear/departure produce native receiver errors) and
passes after correction: one Evening match, eight restored rows after clearing, cancellation on
departure and no page errors. The eighteen-view phone capture exits 0. This is focused host and
headless-browser evidence from the working tree, not a full gate, phone test or publication.

## C127 focused checkpoint — 2026-10-31

Search and Random now share basic chip/disabled/focus styling. Search category tiles use the
existing action palette, 24 px icons above labels and a common top alignment even when captions
wrap. Catalogue/platform sub-options retain compact 16 px icons and their existing pressed and
availability semantics. Three real-browser appearance/state guards fail before correction and
pass afterward, alongside both C126 cases. The original phone Search PNG has been inspected;
the complete viewport/theme matrix and full gate remain pending.

## C128 focused checkpoint — 2026-10-31

The shared destination-header Back rule now also covers Random. Its toolbar uses the same 16 px
side inset as Browse. The actual browser style/hit-target comparison fails before correction and
passes after it, together with all five C126/C127 browser checks. Navigation callbacks, captions
and the single dice are unchanged. The complete matrix/full gate remain pending.

## C129 focused checkpoint — 2026-10-31

The shared sheet header now includes the sheet's responsive top inset in its sticky background.
Equal negative margin and padding retain the initial heading position; the scrollport no longer
exposes content above the header. A real browser coverage/Close hit-target guard fails before
correction and passes on the 412 px Dark and 320 px Light profiles. Both original scrolled PNGs
were inspected and show a clear top strip. All seven C126–C129 browser checks pass on both
profiles; the full five-profile matrix, project gate/lint and clean-source package are next.

## Final verification and handoff — 2026-10-31

Checked source: **`30112cfe803b579b068d4912422b478f44feccb0`**, clean tree, on `fix/web-browser-polish`.
The four fix commits are `f26d58d` (C126), `849d805` (C127), `11a0429` (C128) and `30112cf`
(C129). The visual-tooling parent remains included. This checkpoint preceded integration and
publication preparation, recorded below; no physical-device acceptance is inferred.

- **Full project gate:** **1266 tests / 173 suites**, zero failures/errors/skips, **1259 s**.
  The JVM unit task executes; 9 Gradle tasks execute and 22 remain up to date. All companion
  phases pass, including 25 fresh-page starts, **1368 ESM page assertions** and **57 engine checks**.
  Existing JDK17 with the process-local C1 verification configuration is retained.
- **Lint:** all three analyses execute; **0 fatal / 0 errors / 79 warnings**, **149.2 s**.
- **Browser:** all **35 regression guards** pass in the five Chromium 156.0.8078.4 profiles;
  **90 original PNGs / 15 overview sheets**, zero page exceptions or unexpected external
  requests. The agent inspected every overview and the critical original History, Search,
  Random and compact Settings renders. Root horizontal overflow is absent. Initial Settings
  Close geometry matches the before-fix phone measurement.
- **Source identity:** all **838** frozen gate inputs match before packaging. Packaging changes
  only generated `web/vendor/legal/build.json` and `web/sw.js`; UI/script/engine bytes still
  identify the checked candidate. Later documentation does not relabel these artifacts.
- **Extracted WEB package:** executable launcher, real HTTP/MIME/redirects, all **111** worker
  assets/integrity fetches, **14** components/**19** notice files and the **19** engine exports
  pass. Real WASM renders non-silent ProTracker PCM and reaches equal Mono channels after the
  shared transition. The WASM hash remains
  `b711abb3141a62db046e8e7123989a8713a1c235df2c94f2c2469c42770cbaff`; no engine rebuild is needed.

Run the standard gate with `./scripts/test-protracktor.sh`; the dedicated real-browser phase is
`./scripts/capture-web.sh` after its explicit setup. These results cover the inspected sources
and profiles, not Windows/Firefox, physical touch/headsets, audible quality, background/lock-screen
routing or actual installed-PWA activation/update. C104 stays open. This handoff itself produced
no merge, push, release tag or APK; the later integration is recorded below.

### Artifacts

- [Updated gallery](../dist/web-visual-30112cf-2026-10-31T18-43-29-407Z/index.html) and [visual assessment](../dist/web-visual-30112cf-2026-10-31T18-43-29-407Z/REVIEW.md).
- [Portable visual report](../dist/protracktor-web-visual-fixes-30112cf-20261031.tar.gz): **8957315 bytes**,
  SHA-256 `51bda2678aa205040e87f6387cd5f10d3f2173a4d9ebf967bf0d42fec6eadf02`.
- [Installable WEB distribution](../dist/protracktor-web-30112cf-20261031-200521-791608657.tar.gz): **1610349 bytes**,
  SHA-256 `a14a6a4238a22d5d90dc138b0e8b7f467ed3df2f9ab7bb7da63af6e808374fa8`. Source metadata is `30112cfe803b579b068d4912422b478f44feccb0`, `dirty: false`.

The report archive contains the images, assessment and exact gate results. The WEB archive
contains the player/server/assets; browser tooling and synthetic screenshot data are not shipped.

## Integration and Pages preparation — 2026-10-31

Integration and WEB publication were approved. `fix/web-browser-polish`, including its visual
tooling parent, merged into develop as **`0e8d660e82d65e0ba921111cd4d519d77e7b56ea`**. The merge
tree equals the approved branch head. All **836 non-generated inputs** from the 838-file freeze
match checked source `30112cf`; this comparison does not claim a second full test run.

`./scripts/publish-web-pages.sh` prepared local gh-pages
**`fbd75a794a99a56a416c527f76c00451222da567`**, from clean source `0e8d660`:

- All **115 files / 4,503,999 bytes** match the intended production site. Browser tooling and
  screenshot fixtures are absent.
- All **111** PWA integrity entries match their assets. The revision recomputes as
  `e6591ce981a15c39cc3da584de86c8a7de9f360a323a9c9c7e355e27cb0799c8`.
- Source metadata names the merge commit with `dirty: false`. Only that generated metadata
  and the worker revision differ from the checked candidate; the WASM hash recorded above is
  unchanged. No engine rebuild was required.

**Push and public deployment are pending.** The last HTTP-verified public source remains
`1779419`; preparing a local branch does not establish that the site has updated. Publish both
the source and Pages branches from the project root:

```bash
./scripts/push-protracktor.sh develop gh-pages
```

After the push, verify the public source metadata and served bytes against this preparation.
Physical browser/headset and installed-PWA update checks remain separate. Master, release tags,
release versions, AMP and the parked normalization slider are unchanged; no APK/AAB was built.
