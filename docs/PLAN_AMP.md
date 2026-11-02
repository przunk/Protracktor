# AMP catalogue

Implementation selected 2026-10-18. Branch `feature/amp-catalogue`, originally from develop `a421afc`.
The original private trial waited for explicit AMP maintainer approval. The following decision
supersedes that publication hold; original trials and their measurements below remain historical.

## Release integration — selected 2026-11-01

Include the Android catalogue in the planned 1.5.0 release with visible attribution and an
informational follow-up before publication. No maintainer reply or individual approval has been
received. AMP's published FAQ allows individual module links with attribution and welcomes
notice; it does not explicitly describe a complete application catalogue. This is a project
integration decision, not a claim of permission from AMP. Source: https://amp.dascene.net/faq.php
(indexed text checked 2026-11-01; direct tool access returned HTTP 403).

Preserve `feature/amp-catalogue` at `e728fa6`. Merge it into
`feature/amp-release-integration` based on develop `ed1bc88`; do not rewrite the original trial.
Use **`Amp.ENABLED = true`** as the build-time availability gate; keep the pinned songdb index,
schema 23 and transfer guards. No AMP setting, UI state or controller toggle is exposed. Ignore
the old trial preference rather than allowing it to override the build.
AMP stays Android-only. Browsing/search use the explicitly downloaded index; each selected file
and any format-required companion are fetched on demand and cached. No music read-ahead, folder
prefetch, archive crawl, bulk browser transfer or access-control bypass is introduced.

Settings → Music sources credits AMP and links to its website. To withdraw access, set
`Amp.ENABLED` to false and ship an update. That build hides discovery/index downloads and blocks
AMP reads, including cached/saved references, while retaining playlists, History, bookmarks and
the existing index. There is no per-installation switch or remote revocation; older installed
builds keep their compiled availability. The informational follow-up was sent on 2026-11-01,
confirmed on 2026-11-02. Its final sent wording was not supplied; the retained local draft is not
an exact sent-message record. No maintainer reply or individual approval is recorded.
The complete corrected gate and replacement APK 1434 are verified;
integration is approved and merged as **3519f05b** on 2026-11-02. Detailed physical outcomes remain
separately unreported.
[Current artifact and verification](RELEASE_1.5.0_PREPARATION.md#replacement-apk-after-the-amp-build-gate-correction--2026-11-02).
Release versionName changes only in the release procedure; current builds remain 1.4.0.

Earlier integration stage: **64 tests / seven suites**, zero failures/errors/skips,
**223 s** including compilation. They covered index/HTTP/cache ownership, the real queue controller,
the former Settings persistence, attribution and privacy. The Settings part is superseded by the
build-gate correction; current results include the complete corrected gate and disabled-build
profile. Integration is approved and merged as 3519f05b; specific phone outcomes remain unreported.
[Current artifact and verification](RELEASE_1.5.0_PREPARATION.md#replacement-apk-after-the-amp-build-gate-correction--2026-11-02).

## Build-gate correction — selected 2026-11-01

The current candidate replaces the initial Settings-based proposal with a code constant.
Only Floating controls has a new user preference. Remove AMP preference reads, PlayerUiState/VM
fields and the controller toggle; keep the old preference data untouched and ignored.
`AmpAccess` bounds its initial state and every re-enable attempt by `Amp.ENABLED`, so disabling
the build cannot be bypassed by an older preference or a later internal access request.
Existing transfer-ticket checks remain internal; production has no runtime enable action.

The real Settings absence test fails against the previous candidate. Validate the same build-gate
and transfer/retention tests with the constant false, including an attempted re-enable, then restore
true for the full combined gate and replacement release APK. The corrected candidate now passes
the complete gate and APK inspection;
[Current artifact and verification](RELEASE_1.5.0_PREPARATION.md#replacement-apk-after-the-amp-build-gate-correction--2026-11-02).
Earlier source 8a2ca43e / APK 1432 retain their historical identity.

## Rebase — 2026-10-26

Rebased the seven trial commits onto develop **`e581a25`**. The original branch is retained as
`backup/amp-before-rebase-20261026-8e92002`. The trial now inherits **versionName 1.4.0**, the
unified/manual playback queue, normalization and Modland path recovery. Schema remains **23**;
the AMP switch still defaults on when its saved preference is absent. Floating controls remain
disabled by default, as in develop.

AMP's transfer guards and gzip reader are combined with current Modland recovery. The retained
AMP-reference guard now follows catalogue availability; it must allow enabled AMP into the
manual queue. Current occurrence, source-context and failed-manual-request rules remain owned
by the current queue implementation. No reply or permission from AMP has been received as of
this rebase. [Current verification](PR_AMP_CATALOGUE.md#rebase-verification--2026-10-26).

## Contract

- Android only. **`Amp.ENABLED` is a code constant, currently true.** Disabled builds omit AMP
  from browsing/search/Random and cannot fetch tracks,
  including existing playlist/history references. Keep user playlists/history/bookmarks intact.
- Use the ready-made songdb AMP source index, not a crawler. The existing songdb dependency and
  licensing record are in [the source survey](reference/songdb.md). On 2026-10-18 its public
  `songdb/sources/site/amp.tsv` is 23,820,617 bytes and contains archive paths and metadata.
  Fetch the index only on explicit request, following the existing catalogue mechanism.
- Support catalogue browsing, search, author navigation, add-to-playlist and on-demand cached
  playback through existing application rules. No automatic AMP folder prefetch, mass archive
  download or speculative music fetching. Do not bypass access controls or spoof a referring site.
- Preserve the stable Protracktor User-Agent. Explain blocking/unavailable/disabled states rather
  than presenting HTML or a failed transfer as music. Establish the actual file URL/container
  contract from public primary sources before implementing it; do not invent an API or index IDs.
- No schema migration, versionName bump, new dependency, web integration or unrelated refactor.

## Original trial stages — historical

1. Verify the index/address/container contract and record limits; write fixtures and parser tests.
2. Add the catalogue using the existing index/database/playback path and source attribution.
3. Add the persistent default-on trial switch and enforce it at data, transfer and playback edges.
   Exercise disabling with retained rows, playlists, queued reads and pending downloads.
4. Focused regression/negative checks, forced full tests/lint, minified test-signed release APK in
   `dist/`, exact source/hash evidence, phone checklist and an unsent English permission-mail draft.

## Permission and verification

No individual maintainer approval has been received. HTTP availability is not approval; the
2026-11-01 project decision above replaces the earlier publication hold. The older [catalogue survey](PLAN_CATALOGUES.md) remains historical; its
claim that no usable index exists is superseded by the songdb finding above. Direct access from
the web tool returned 403; a bounded diagnostic request with the app User-Agent read the public
download page successfully, while robots.txt was blocked. No crawl was performed. One explicit
module probe returned HTTP 200 and valid gzip: `M/Major Tom/MOD.human race gold.gz`, 53,136
compressed / 74,406 uncompressed bytes. The `/modules/` address contract is confirmed by the
upstream `songdb/scripts/sources/amp.sc`, which reads AMP download redirects. No Referer or
protection bypass was used. The owner reported a positive overall phone test on 2026-10-18;
individual checklist results were not supplied.

The index is pinned to songdb `ead6d2d72f31c805f24966bd6dbe6f45ed9d2e0f`, with SHA-256
`a2cf1f0f8eba1f791691a756afa4c8d7b4deaa296950fd88375fa29294cb59b9`: 176,970 file rows and
9,917 four-column subsong rows. File rows have eleven columns; the final field is the archive
path, not a module ID. Files use `Letter/Artist/FORMAT.title.gz`; two indexed exceptions are
uncompressed. Browse groups by the filename's format prefix, not hundreds of encoder versions.
The gzip suffix is retained in identity/download paths but removed from the decoder filename.
One certain source format overrides prefix grouping: 55 `FaceTheMusic` entries use `Face The
Music`, because the shared filename-only `.ftm` fallback means Nintendo FamiTracker. A direct
production-parser probe failed before correction (Nintendo instead of Atari ST); a regression
fixture now protects the source-specific disambiguation.
The one module probe SHA-256 is `742da2b3a43360d9e1f0ead400a8e2ee6ec8bd3e89ba1d2bae72658e003188a1`.

On 2026-10-18 the owner confirmed sending the permission request to AMP. A reply and explicit
permission remain pending. The local draft is retained in ignored
`docs/letters/2026-10-18-amp-permission-draft.md`; the final sent wording was not supplied.
Correspondence is not part of the public branch.

Initial handoff, 2026-10-18: stages 1–4 complete. Clean source `64c7dcc` passed the forced
full gate: 952 tests / 127 suites, zero failures/errors/skips; lint zero errors / 73 warnings.
The minified test APK 1.2.0 (1221) is prepared and its signature/assets are verified.
The owner reported on 2026-10-18 that AMP works well in phone testing; individual checklist
results were not supplied. At that historical handoff, maintainer permission and project integration authorization were
pending. The publication hold is superseded by the 2026-11-01 decision above; the current combined
candidate was subsequently approved and merged as 3519f05b; individual phone outcomes remain
unreported.
[Implementation evidence](PR_AMP_CATALOGUE.md).

### Disabled-build verification — 2026-11-01

The real `Amp.ENABLED = false` build passes **12 checks / eight suites**, zero failures,
errors or skips, in **43 s**. The same code paths verify initial/re-enabled access, catalogue and
index choices, download sizes, module/index transfer admission, saved reads, SQLite/user-reference
retention and the Settings section without an availability control. Removing the re-enable clamp
fails the build-gate assertions. The initial UI absence test also failed on the previous AMP row.
The host UI fixture uses a bounded catalogue-section viewport because the unrelated About footer
requires Android JNI. The target availability section is rendered and inspected.

Restored `Amp.ENABLED = true` for the full gate and replacement APK. Old `amp_enabled=false` data
is seeded before real controller startup in the admission tests, covering an upgrade from an
opted-out trial. The current full gate and replacement APK pass; [Current artifact and verification](RELEASE_1.5.0_PREPARATION.md#replacement-apk-after-the-amp-build-gate-correction--2026-11-02). Earlier
artifact/source identities are retained; no develop merge or release is performed here.
