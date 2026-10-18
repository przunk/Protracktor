# AMP catalogue trial — implementation and evidence

Owner-selected 2026-10-18. Branch `feature/amp-catalogue`, base develop `a421afc`.
**Not merged. AMP maintainer permission is not obtained; merge/publication must wait for it and
the owner's device acceptance.** The default-on switch is for this isolated test branch only.

## Implementation

- `Amp` reads a pinned songdb path index, not AMP pages. Index download is an explicit existing
  catalogue action. Existing catalogue storage, author browsing, local search, Random, saved
  places, playlist addition and History remain the same workflows. No schema migration.
- Stable `amp://Letter/Artist/FORMAT.name.gz` identities preserve the actual archive path. HTTP
  path segments are encoded individually. Playback unpacks gzip before passing the original
  filename to the existing decoder; two plain-file exceptions are accepted by content detection.
  Source-declared Face The Music uses its canonical format name rather than the ambiguous FTM
  prefix, preserving the existing Atari ST classification for all 55 matching entries.
- Normal cache entries hold the downloaded response. Cached replay needs no AMP request.
  Invalid gzip/HTML is rejected rather than cached as music. A 64 MiB response/expansion limit
  exceeds the largest entry in the pinned index (55,414,299 bytes) and bounds corrupt input.
- **Settings → Online catalogues → AMP catalogue (experimental)** persists on/off and explains the trial.
  Restored before controller stores start; it defaults on only when the preference is absent.
- Off removes AMP from offered catalogue/download/search sources and new Random draws. SQL
  visibility also covers retained index rows and platform counts. `Catalogue.known` prevents
  pruning those rows merely because the switch is off. User playlists, History and bookmarks
  are not deleted; existing references fail with a disabled explanation even when cached.
- Off stops current/pending AMP playback, not other-source playback. The transfer epoch prevents
  off/on from accepting old bytes, and active AMP connections are disconnected. Delayed index
  results cannot replace an index, and progress is retired. Overview/count reads discard old
  switch epochs. In-flight HTTP requests cannot be unsent; late data is not adopted.
- No AMP music read-ahead, folder prefetch, background metadata probing or bulk browser transfer.
  Random may prepare candidate identities locally, but it does not speculatively fetch their
  music. The heard Random record remains subject to existing C97 rules.
- The existing stable User-Agent is unchanged. No forged Referer, HTML scraping, authentication,
  new dependency or AMP protection bypass. AMP does not use the ExoticA cookie retry.
- PL/EN settings/messages, Music sources attribution and privacy-host disclosure are included.
  The index is fetched from GitHub, not bundled; songdb is already an attributed GPL-compatible
  source. This supplies no licence or maintainer permission for AMP music integration.

## Protocol evidence

[Plan and checksums](PLAN_AMP.md): pinned index confirmed byte-identical to the inspected source,
176,970 distinct file paths and 9,917 subsong continuation rows. Only one module was requested
from AMP: HTTP 200, valid gzip, 53,136 compressed / 74,406 unpacked bytes. The existing host
OpenMPT probe rendered a full three-second buffer, peak 0.538, ProTracker MOD, title
`human race gold`. This is checked host parsing/decoding, **not Android playback**.

## Automated verification

Development checkpoint: **76 focused tests / 11 suites**, no failures/errors/skips, 119.2 s.
They exercise actual catalogue SQLite queries/cache handling and the production settings
composable with controlled HTTP responses; they do not simulate a phone's audio/service stack.
The initial UI test could not find an offscreen lazy-list row; scrolling the lazy list before
querying the switch corrected the test. That failed run is not counted as successful evidence.

Deliberate negative controls: four temporary mutations removed SQL visibility, network/cache
permission checks, epoch validation and the no-prefetch rule. **28 tests ran; exactly five
expected assertion failures**, zero errors/skips, 127.4 s. The failures covered retained SQL
rows, disabled cached reads, late HTTP delivery after off/on, old epochs and speculative reads.
All four mutations were restored, and the ignored diagnostic init script was removed.

A separate direct production-class probe found that prefix-only grouping misclassified Face The
Music as Nintendo FamiTracker. It failed before correction with `expected Atari ST; got nintendo`;
the parser now retains that certain source format, with a permanent regression test. The full
gate is repeated after this correction; the earlier full-unit checkpoint is not final evidence.

The focused follow-up ran eight tests: seven parser checks passed (including FTM), and the new
batch-write reproduction failed as expected before its fix. Retiring an AMP database write must
skip that step without cancelling the remaining selected metadata downloads; actual coroutine
cancellation still propagates. The correction is included in the final gate. Transport also
keeps a stopped AMP Random/results context instead of falling back to the saved playlist;
that controller/device path is not claimed as host-UI tested.

The final pass also checks stable identity while disabled, write-boundary rejection of a retired
index epoch and preservation of playlist/History/bookmark data. Restored-code full tests/lint and
APK verification are pending. Final source and artifact identity will be recorded after them.

## Phone and permission handoff

[Device checklist](TESTING.md): on/off persistence in PL/EN; explicit index download; several
formats/authors; search, author jump, bookmark and saved playlist; cached/offline replay; no
speculative fetch; Random history preservation; off during playback/download and rapid off/on;
retained disabled references, re-enable and other-source playback.

**All phone scenarios are unchecked.** No emulator exists in this workspace. No merge, push,
master/tag movement, public release or AAB signing is part of the trial. `versionName` stays 1.2.0.
The permission email is prepared locally in ignored
`docs/letters/2026-10-18-amp-permission-draft.md`; it is **not sent**, and the owner chooses its
recipients and wording. Do not publish local correspondence or infer permission from HTTP 200.
