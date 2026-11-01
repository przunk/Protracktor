# AMP catalogue — implementation and evidence

Selected 2026-10-18. Branch `feature/amp-catalogue`, original base develop `a421afc`;
rebased onto **`e581a25`** on 2026-10-26.

## Release integration — phone trial pending, 2026-11-01

The original trial branch stays intact at `e728fa6`. It has been merged into
`feature/amp-release-integration` from current develop `ed1bc88`, preserving current queue,
History, playlist filtering, cache and normalization. Only STATUS/TESTING had textual conflicts;
fresh combined host checks and packaging pass; a combined phone trial is still required.

AMP remains Android-only, with **`Amp.ENABLED = true`** and bounded individual downloads.
The 2026-11-01 clarification removes the Settings preference and its state/controller plumbing;
old saved values are ignored. A disabled build retains user data and cannot be re-enabled through
internal transfer access. Corrected source 2794a1db passes the complete gate and replacement APK
inspection; previous results remain historical. Attribution/privacy describe the selected integration instead
of an unpublished experiment. The earlier publication hold is superseded by the project decision
in [PLAN_AMP](PLAN_AMP.md#release-integration--selected-2026-11-01). There is no maintainer reply
or individual approval, and the informational follow-up has not been sent. Develop, master and
release tags remain unchanged. The dated checks below are historical, not current verification.

Earlier integration stage: **64 tests / seven suites**, zero failures/errors/skips,
**223 s** including compilation. They covered index/HTTP/cache ownership, the real queue controller,
the former Settings persistence, attribution and privacy. The Settings part is superseded by the
build-gate correction; current results include the complete corrected gate and disabled-build
profile. Physical phone acceptance remains pending.
[Current artifact and verification](RELEASE_1.5.0_PREPARATION.md#replacement-apk-after-the-amp-build-gate-correction--2026-11-02).

## Rebase verification — 2026-10-26

Current application candidate: **`f21e3b2`**. The seven original commits were replayed onto
develop; the original head **`8e92002`** remains under
`backup/amp-before-rebase-20261026-8e92002`. Develop, master and release tags were preserved.

Conflict resolution retains current queue transport, the default-Off floating-controls gate,
normalization and Modland's legacy/nested unknown-author recovery. AMP access epochs and gzip
validation remain active. Removed unconditional AMP-retention SQL filters now use the shared
runtime catalogue visibility rule; reference blocking follows actual catalogue availability.
The old `diceWaiting` API is replaced by the current Random source-context check.

The agent ran **99 focused tests / 10 suites**, zero failures/errors/skips, in **216.5 s**.
They include Modland recovery and controlled HTTP/cache cases, retained AMP data, availability,
read-ahead, settings and the real controller with preset host playback state. New controller
checks cover FIFO admission, duplicates, disabled enqueue feedback and stopping a current AMP
manual occurrence while preserving its source and waiting requests.

A temporary return to the unconditional AMP-reference block caused the new controller test
to fail with the expected missing manual request: **one test, one assertion failure**, zero
errors/skips, **93.0 s**. The original guard was restored before the full gate. The initial
compile attempt exposed the removed `diceWaiting` property and is not counted as passing evidence.

**Full forced gate: passed**, **1242 tests / 162 suites**, zero failures/errors/skips, in
**761 s** overall; unit execution took **565.9 s**. All companion checks passed: store metadata,
runner/release fixtures, web page/engine, SAP measurement, web database upgrade, native audio,
icons, PWA and local server. Fresh lint analysis completed in **93.8 s** with **zero errors /
81 warnings**. Evidence is retained in ignored `user/amp-rebase-20261026/`, including JUnit XML,
lint XML, negative-control results and complete logs. No new AMP request or live archive probe
was made for this rebase. Subsequent closeout changes only documentation; application, native,
script, web and store trees match the tested candidate.

**Phone behaviour after rebase: unchecked. No new APK was built.** The original positive phone
result and APK below remain evidence for their original sources. VersionName is now **1.4.0**,
versionCode remains commit-derived and schema remains **23**; the old 1.2.0 APK is not relabelled.
Use the [new integration checklist](TESTING.md#amp-rebase--phone-checks-pending-2026-10-26)
for a later phone trial. Maintainer permission is still pending.

## Initial implementation and verification — 2026-10-18

The following records the original implementation, test run and APK. Rebase evidence is above.

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

The production parser was also run against the complete pinned index: **176,970 unique,
addressable file entries**, no compressed decoder filenames, and all **55 Face The Music
entries classified as Atari ST**. The production gzip reader expanded the downloaded sample to
74,406 bytes. These are host corpus checks, not a claim that every indexed format plays.

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

**Final clean source `64c7dcc`:** the forced full gate passed in **509 s**. JUnit XML confirms
**952 tests / 127 suites**, zero failures/errors/skips; unit execution took **313.6 s**.
This includes stable identity while disabled, rejection of retired index writes, continuing
unrelated batch downloads and preserving playlist/History/bookmark data. All companion stages
passed: store metadata, 14 runner fixtures, 11 disposable release scenarios, 581 web-page checks,
49 engine checks, 8 SAP checks, database upgrade, native audio, icons, PWA and local server.
Lint passed in **99.7 s**, **zero errors / 73 warnings**. The new preference setter adds one
`UseKtx` style suggestion to the previously recorded 72; it uses the existing preference style.

## Test APK

| Field | Verified value |
| --- | --- |
| Variant | Minified release, local test certificate; not a Play publication artifact |
| Version | `1.2.0 (1221)` |
| Source | `64c7dcc689f6649d0f0a6aef0f1e88ed0da6f983` |
| File | `dist/protracktor-release-1.2.0-1221-64c7dcc-20261018-150242.apk` |
| Size | 11,845,087 bytes |
| SHA-256 | `d4a9f33999fdeace6c578064faf64ac9fbeddcdd22c19f09cce4990fdcbccc04` |
| Test certificate SHA-256 | `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e` |
| Native ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |

APK v2 signature, package/version, all three engine libraries and nonempty R8 mapping were
checked. Packaged source credits and privacy policy are byte-identical to their current sources.
The first build failed on an AGP duplicate entry in the generated compressed privacy-policy JAR.
AGP had removed the failed intermediate; the remaining source-credit intermediate matched the
source. An unchanged retry succeeded in 92 s. No source asset or toolchain change was needed.
Application/native/web/scripts content was identical before the gate and after the build.
Subsequent documentation-only commits do not relabel this APK or change its versionCode.

## Phone and permission handoff

[Device checklist](TESTING.md): on/off persistence in PL/EN; explicit index download; several
formats/authors; search, author jump, bookmark and saved playlist; cached/offline replay; no
speculative fetch; Random history preservation; off during playback/download and rapid off/on;
retained disabled references, re-enable and other-source playback.

**Owner-reported phone check, 2026-10-18:** AMP was tested and reported to work well. Individual
checklist results were not supplied; this does not establish every edge case as checked or
authorize merging. No emulator exists in this workspace. No merge, push,
master/tag movement, public release or AAB signing is part of the trial. `versionName` stays 1.2.0.
On 2026-10-18 the owner confirmed sending the AMP permission request. A reply and explicit
permission remain pending. The local draft is retained in ignored
`docs/letters/2026-10-18-amp-permission-draft.md`; the final sent wording was not supplied.
Do not publish local correspondence or infer permission from HTTP 200.
