# C110 — generic Browse opens the root

Owner-reported defect, 2026-10-19. Branch `fix/browse-root-navigation`, from develop `a8539a2`.
This is a navigation correction, not the proposed manual queue or an AMP integration.

## Report and mechanism

Play History, return with Back to Browse and then the playlist, and press Browse again.
The owner saw History instead of the main Browse view. Exact installed build/device were not
independently supplied; the previous handoff was 1.2.0 (1228).

The shell's generic Browse action called `returnToSession()`. That restored `sessionBrowse`
whenever the active source was a collection. The same mechanism affected Search and online/local
folders, not only History. It is a leftover return-to-source convention, not an audio-engine fault.

## Correction boundary

Every generic Browse entry uses the existing `openDomain(ROOT)` path. The toolbar, empty-playlist
entry and dock fallback share this callback. Explicit author destinations still open through
`showBrowse`; Back still uses the existing hierarchy. Playing source, order/cursor, selected
playlist draft, pause state and playback position are not replaced. No stop, new decoder request,
snapshot deletion, schema/version-name change, dependency or UI label is introduced.

The old session-restore API is not removed as an unrelated cleanup. Generic Browse no longer
invokes it. The separate context/manual queue decisions remain in
[the saved analysis](QUEUE_CONTEXT_ANALYSIS.md).

## Regression evidence

`BrowseRootNavigationTest` renders the real `ProtracktorApp`, ViewModel and controller with native
SQLite. Playback/source state is preset, not decoded; these checks do not establish real audio
behaviour. The test clicks the actual Browse action and uses toolbar and Android system Back.

The original History regression failed on unchanged application code: after leaving History and
reopening Browse, the root's Online catalogues entry was not displayed
(`/tmp/protracktor-c110-before-looper.log`). It passed after changing only the generic entry
(`/tmp/protracktor-c110-after.log`). An earlier setup attempt timed out because the host Main looper
was not drained; that harness failure is not counted as a reproduction of C110.

Additional cases cover paused Search, online author and local-folder parent navigation, repeated
entry, Random playing/paused record preservation, and the explicit destination event contract.
The latter presets the producer's resolved author route; it does not test a remote catalogue lookup.
Playback assertions include source/order/cursor and repeat/shuffle, selected draft, position,
duration, metadata, playing flag and Random record/cursor.

## Verification and handoff

**Checked:** all seven expanded shell regressions passed in a completed focused Gradle run,
27 seconds (`/tmp/protracktor-c110-focused.log`). Two earlier expanded runs stopped on test-harness
assertions (an ambiguous title selector and an unrelated cache-status update); neither is counted
as a passing run or a new application defect. The final assertions distinguish navigation from
asynchronously derived cache status. Local Markdown targets (115) and `git diff --check` pass.

**Checked:** clean source `fa09e7a57ad722393326129fbe5d270cf75869d7` passed
`scripts/test-protracktor.sh --really` in **498 s**. Fresh JUnit XML: **963 tests / 129 suites**,
zero failures/errors/skips; all 31 unit-build tasks executed, unit stage **322.9 s**.
Lint completed in **83.1 s**, **0 errors / 72 warnings**, unchanged from the accepted baseline.
All companion stages passed: metadata, runner and release-flow fixtures, web page/engine/SAP,
web database upgrade, native audio, icons, PWA and local server.

The minified release APK was built from that same clean source with the project script, supervised
to a 1200-second deadline; it completed in **90.8 s**. No application change followed the gate.

- Artifact: `dist/protracktor-release-1.2.0-1231-fa09e7a-20261019-231016.apk`.
- Package/version: `com.przunk.protracktor`, **1.2.0 (1231)**; code matches the source commit count.
- Size: **11,831,347 bytes**.
- SHA-256: `597e0a2aaab9f6cc4e3db35f44d0742490ea8fb8362aae67b82ce1dfda8132d5`.
- APK Signature Scheme v2 verified; one test signer, `CN=Android Debug`, certificate SHA-256
  `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, matching the previous APK.
- R8 executed, nonempty mapping retained; manifest has no debuggable flag, minSdk 29/targetSdk 36,
  and the engine is packaged for arm64-v8a, armeabi-v7a and x86_64.

Logs, JUnit XML, lint XML, mapping, manifest and signature evidence are retained in ignored
`dist/browse-root-verification/fa09e7a/`. The later documentation closeout does not relabel this
artifact or its tested source. This is a test-signed release APK, not a store AAB.

Phone scenarios are **unchecked**; see [TESTING](TESTING.md#c110--browse-root-navigation).
No merge, push, release tag or master movement occurred. Develop remains `a8539a2`,
AMP `8e92002`, master `794d8f9`; AMP remains deferred. Wait for owner acceptance before merging.
