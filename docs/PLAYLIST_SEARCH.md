# Search within the open playlist (B55)

Decision, 2026-11-01: implement a compact search mode on a task branch, with Android and WEB
using the same interaction. This is local filtering of the open editor, including unsaved edits;
it does not change catalogue search, saved queries or B56.

## Selected behaviour

- The playlist chooser shares its existing row with a subtle, labelled Search action on the right.
  Opening search replaces that row with a full-width field, animated in both directions. It adds
  no row and preserves header height. The other action buttons and the dock retain their layout.
- Entering search focuses the field and opens the keyboard. Editing dims the surrounding UI,
  including the list; results remain tappable. A result starts playback, dismisses the keyboard
  and removes dimming while retaining the phrase and filtered list. Refocusing dims again.
- Dimmed header/dock controls dismiss editing on the first press instead of activating underneath.
  The field has a clear-query action and a separate close-search action. Closing restores the
  full playlist and its previous scroll position. Back/Escape first ends editing, then closes
  search. Keyboard Done/Enter ends editing and retains the filter.
- Match every query word across title, author, filename and source path, ignoring case and accents,
  using the existing search folding rule. Keep original order and playlist row numbers. No file
  opening, network request or persistent store is required.
- A row tap addresses its original index in the full playlist. Shuffle, repeat, Next, Previous and
  the manual queue continue using that full context, never just the visible subset.
- Row removal and bulk actions address the visible occurrences in the underlying editor. Selection
  cannot silently include hidden results. Reordering is available with an empty filter; clear the
  query before arranging the full playlist. Search does not save or discard edits.
- Leaving the playlist closes search; switching playlists starts with an unfiltered view. The
  original scroll position is retained while filtering, independently of result scrolling.
- No matches has an explicit empty-results message and offers clearing through the field. It
  must not claim the underlying playlist is empty or offer index downloads.

## Implementation and evidence — 2026-11-01

Implemented on `feature/playlist-search`, from develop `d22afc5`; integration awaits the phone
trial and explicit approval. The matching projection is pure Kotlin/JavaScript; the existing
player remains the owner of the complete playback order. No dependency, schema or release
version changes are included.

Agent verification:

- **Checked:** 1,288 Android JVM tests / 176 suites, zero failures/errors/skips, actual execution
  in 524 s at source `d5b048f`. Candidate `0ddb961` has byte-identical application inputs;
  subsequent changes only repair page fixtures. This includes three matching/projection cases and seven production-shell Compose
  cases under Robolectric SDK 29. A wrong filtered-removal index fails its regression check;
  inactive search clearing another destination's focus also failed before correction.
- **Checked:** 1,396 production-page ESM/DOM assertions, all companion domain/storage/startup,
  engine/PCM/worklet, native audio, migration, icon, PWA and server checks. A wrong filtered
  playback index fails the page regression. Gate coverage was completed in stages: the complete
  Android/initial companions, a corrected full-page rerun, the remaining gate phases and lint.
  An earlier full run stopped at inherited page-fixture assertions; it is not recorded as a
  successful uninterrupted run. Fixtures now await correlated decoder opens/completed paging
  and compare every editor document field instead of requiring the same wrapper object.
- **Checked:** fresh Android lint, 0 errors / 79 warnings, 146.5 s.
- **Checked:** actual Chromium, five desktop/phone-width EN/PL and Dark/Light profiles, including
  320 px: 50 guards, no page errors, 110 primary captures. The agent inspected all 20 overview
  sheets and four critical original captures. Header height, overflow, editing/playing/empty
  states and closing restoration were exercised. Capture source `0bdca78` has byte-identical
  WEB inputs to candidate `0ddb961`; the local hash changed during Android/test-fixture
  cleanup. [Capture gallery](../dist/web-visual-0bdca78-2026-11-01T18-55-38-573Z/index.html).
- **Partially checked:** release packaging, R8 minification, non-debuggable manifest, verified
  test certificate, version and 16 KB ZIP alignment. These establish the installable artifact,
  not phone behaviour. All application inputs match the complete Android/browser checks.
- **Unchecked:** physical Android keyboard/focus integration, touch, animation feel and audible
  continuity; physical browser/Firefox acceptance. Follow [the phone trial](TESTING.md#b55-playlist-search-trial--2026-11-01).

Candidate: clean source **0ddb961**, minified test-signed **1.4.0 (1415)**, built in
134 s; **12,022,087 bytes**.
[Release APK](../dist/protracktor-release-1.4.0-1415-0ddb961-20261101-210355.apk). SHA-256:
`259148132b0460c11d3017770a0669852e68b990f87cb109d1fc94599c0c496a`.

The branch remains unmerged; no push, WEB deployment, AAB or release tag was made.
