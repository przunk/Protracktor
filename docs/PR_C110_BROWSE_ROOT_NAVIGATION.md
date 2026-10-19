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

Forced full gate/lint and release APK verification are pending at this implementation checkpoint.
Phone scenarios are **unchecked**; see [TESTING](TESTING.md#c110--browse-root-navigation).
No merge, push, release tag or master movement is authorized. AMP remains deferred and untouched.
