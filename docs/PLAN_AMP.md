# AMP catalogue trial

Owner-selected implementation, 2026-10-18. Branch `feature/amp-catalogue`, from develop `a421afc`.
This branch must **not merge before explicit AMP maintainer approval**, even if the owner accepts
its phone behaviour. No public release, push, maintainer message or automatic merge is authorized.

## Contract

- Android trial only. An in-app AMP switch defaults **on** in this isolated branch and persists
  the user's choice. Disabled AMP is absent from browsing/search/Random and cannot fetch tracks,
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

## Stages

1. Verify the index/address/container contract and record limits; write fixtures and parser tests.
2. Add the catalogue using the existing index/database/playback path and source attribution.
3. Add the persistent default-on trial switch and enforce it at data, transfer and playback edges.
   Exercise disabling with retained rows, playlists, queued reads and pending downloads.
4. Focused regression/negative checks, forced full tests/lint, minified test-signed release APK in
   `dist/`, exact source/hash evidence, phone checklist and an unsent English permission-mail draft.

## Permission and verification

AMP permission is **not obtained**. HTTP availability is not permission and a test APK is not
publication approval. The older [catalogue survey](PLAN_CATALOGUES.md) remains historical; its
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

Progress: stages 1–4 complete for the agent handoff. Clean source `64c7dcc` passed the forced
full gate: 952 tests / 127 suites, zero failures/errors/skips; lint zero errors / 73 warnings.
The minified test APK 1.2.0 (1221) is prepared and its signature/assets are verified.
The owner reported on 2026-10-18 that AMP works well in phone testing; individual checklist
results were not supplied. AMP permission and owner merge authorization remain pending;
**do not merge or publish**.
[Implementation evidence](PR_AMP_CATALOGUE.md).
