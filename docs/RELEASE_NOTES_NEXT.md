# Historical 1.1.0 release notes draft

Historical **1.1.0 draft**, retained as the original planning record below, not the next-release
inventory. See the [prepared 1.4.0 record](RELEASE_NOTES_1.4.0.md) and the
[release ledger](../store/play-console/releases.md) for prepared artifacts and their evidence.

Prepared 2026-10-11 at the owner's request to include all implemented user feedback in the
release description. Scope: `v1.0.0..d5dbe4a`; later release-preparation documentation does not
change the application. Recommended version: **1.1.0**, because this adds user-facing features;
the number still awaits the owner's decision. No version bump, release tag or AAB is created
by these notes. Android publication remains pending; web deployment is recorded separately.

The historical short English and Polish Play notes are pinned to
[`en-US`](https://github.com/przunk/Protracktor/blob/v1.1.0/store/listing/en-US/release-notes/default.txt) and
[`pl-PL`](https://github.com/przunk/Protracktor/blob/v1.1.0/store/listing/pl-PL/release-notes/default.txt).
This full record preserves details
that do not fit their 500-character limit. The previous notes remain in Git at `v1.0.0`.

## Implemented user feedback

Reports below were relayed by the owner, except where identified as the owner's own testing.
This attribution does not establish a reporter's identity, closed-test membership, installed
build or individual retest result. Acceptance records and regression evidence remain in
[STATUS](STATUS.md), [the Random handoff](RANDOM_FEEDBACK_PRS.md) and the linked feature handoffs.

### Random filters and navigation

- **Filter availability:** opening filters reads the existing catalogue/favourites data;
  downloading favourites with the sheet open updates it without requiring a catalogue or song
  metadata re-download. Unknown/loading counts are no longer treated as missing music. Failed
  reads offer Retry; genuinely unavailable scopes remain unavailable.
- **C97 — history after changing a filter:** preserve all heard tracks, including those after
  a selected earlier entry. Next walks the remaining heard history first. A changed filter
  prepares new picks in the background immediately, but only new draws use it. Unheard old-scope
  picks are replaced without appearing as already-played rows or disturbing selection. Explicit
  dice re-roll remains a fresh draw. This addresses the user's disappearing-history and
  inconsistent-selection report.
- **C98 — covered playlist:** Browse and Web are unavailable while the return-choice overlay
  covers the playlist. Settings, Back to Random and Back to Playlist remain available. The user
  reported Browse; matching Web behaviour was the owner's requested consistency improvement.
- **C99 — temporary disabled filters and opening delay:** following the owner's tests, keep
  known availability across sheet reopenings and ordinary refreshes, preload counts and avoid
  recounting on every download progress update. Invalidation still follows real data changes.
  No measured latency reduction is claimed.

### B43 / C100 — played-before indication

- Add a discreet listened marker in Browse and Random, hidden in playlist views only; playlist
  membership does not suppress it in Browse or Random. It does not exclude heard songs from
  Random or change their selection probability.
- Following the owner's visual feedback, place it below the cache/status icon in Browse and
  below the track number/play marker in Random rather than before the file path. The indication
  is accessible without relying on colour alone.
- **C100 — short songs:** mark after five active playback seconds **or natural completion**,
  so a three-second track can qualify. Skipping, failed/empty playback, or seeking to the end
  alone does not qualify. Seeking preserves earned active time but disables the completion
  shortcut for that occurrence. Pause/resume preserves progress.
- Seed existing History records once; Clear History removes the marks and cancels qualification
  for the current occurrence. No separate, hidden listened log survives clearing.
- Initially implemented as a reversible experiment at the owner's request. On 2026-10-11 the
  owner accepted the combined trial and authorised merging; retained with C100 as part of B43.
  See [B43](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_B43_PLAYED_BEFORE.md) and [C100](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C100_SHORT_TRACK_LISTENED.md).

### B42 — saved author/game folders

- Bookmark an exact online author/game folder and reopen it from Saved places without adding
  all its tracks to a playlist. Bookmarks persist independently of History and catalogue indexes.
- Following owner feedback, use labelled empty/filled Bookmark icons instead of Save for later
  and Remove text. Saved places shows the name above the path, with Bookmark and Open together
  at the right; both controls have equal dimensions.
- Removing a bookmark in Saved places offers Undo in a snackbar, restoring its original order.
- Missing indexes or unavailable locations remain explained and manageable rather than silently
  discarding bookmarks or downloading data. See [B42](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_B42_SAVED_PLACES.md).

### C101 — known titles in Random and Browse

- Prefer a nonblank title learned during playback, as already recorded in History, over the
  original catalogue filename. Retain the filename/source context; do not reorder rows or alter
  catalogue sorting, identity, selection or Random history.
- Learn during ordinary playback, not by opening every cached file. Titles survive restart and
  cache eviction; Clear History removes them until learned again, as explicitly agreed with the
  owner. Blank metadata keeps the catalogue name. See [C101](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C101_LEARNED_TRACK_TITLES.md).

## Archive-maintainer feedback

Following UnExoticA maintainer feedback and the owner's approval, identify catalogue/index and
archive downloads with the same stable Protracktor User-Agent, including verification retries.
This allows the maintainer to recognise or whitelist the client; whitelist activation is not
confirmed. UnExoticA was already enabled, not newly introduced in this release. The owner
confirmed playback and catalogue caching. See [the archive plan](PLAN_UNEXOTICA.md).

## Web companion — separate from the Android bundle

- GitHub PR #1 configures the optional iOS playback audio session; physical iOS background and
  screen-lock behaviour has not been separately confirmed.
- PR #2 centres playback controls and adds standalone bottom spacing.
- PR #3 adds installation as a PWA, Android-derived launcher/maskable icons and offline startup
  of the interface and bundled decoder. Remote music and pairing still need a network; pairing
  is unavailable on static GitHub Pages. Updates wait until player windows close.
- Published web source: `d5dbe4a`, deployment commit `3b7a9ee`. GitHub deployment succeeded;
  ten sampled public files matched `gh-pages`. On 2026-10-11 the owner confirmed installation
  and normal PWA operation. No separate offline, iOS or all-browser test is inferred.

## Internal maintenance and release evidence

The bounded controller refactor separates file access, scanning/search, replay installation,
reference downloads, read-ahead/cache, playlist persistence/transfer, catalogue/local browsing
and maintenance workflows, with focused comment corrections. This is maintenance, not a claim
that the entire architecture/comment-cleanup wish was completed. See
[architecture closeout](ARCHITECTURE_CLOSEOUT.md).

Android schema 23 includes data-preserving migrations for listened flags and saved places.
The combined Android candidate passed 749 tests and lint, then received overall owner acceptance.
After web PR #3, web/PWA/page/server/icon/storage checks passed. A fresh release-wide check and
owner-controlled signed AAB remain to be produced; previous tests are not a newly executed
release gate. No APK build is requested by these documentation changes.

## Not included as delivered features

- **B44 Android Auto:** tester request recorded, not implemented or scheduled.
- **F-Droid:** maintainer suggestion retained for discussion; no distribution is prepared.
- Deferred architecture extractions and repository-wide comment cleanup remain deferred.
- Older icon, button-label/spacing, catalogue-navigation and decoder work already present in
  1.0.0 is not advertised again as new. Historical reports remain in project documentation.
- No claim of renewed Google Play production access, completed extra testing period or public
  Android rollout is made. The owner's proposal to release is not evidence of an uploaded AAB.
