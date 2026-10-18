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
download page successfully, while robots.txt was blocked. No crawl or music download performed
at this checkpoint. Phone behaviour remains unchecked until the owner tests the APK.

Progress: planning and address/format verification; implementation/tests/APK pending.
