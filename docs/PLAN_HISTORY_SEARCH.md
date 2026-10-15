# B49 — live History search

## Selected contract — 2026-10-15

The owner selected implementation on a separate branch after discussing live filtering.
`feature/history-live-search` starts from develop `5954c86`. Match a literal phrase inside title,
author or filename, ignoring case, across all History rather than only the visible hundred.
Debounce edits by 200 ms, reset to page zero, keep newest-first ordering and show matching counts.
Clearing the input restores full History. Editing must not change playback or its captured queue.
New History entry starts unfiltered at the top; ordinary query/result updates retain the field
and keyboard. A no-match message differs from an empty database. PL/EN from the first change.

## Storage choice

Keep schema 23. SQLite search used elsewhere relies on precomputed folded columns absent from
History. Instead of an upgrade solely for this field, stream the existing newest-first cursor,
compare the three fields using Kotlin Unicode-aware case-insensitive substring matching and retain
only a requested page and a last-page fallback. Count matches in that same pass; clamp shrinking
pages correctly. A blank query retains the indexed SQL count/page path. Reads remain on IO and
check cancellation during traversal. No full-History list, new dependency, wildcard interpretation
or accent-removal promise. Measure a large History on the host; phone latency remains unverified.

## Stages and verification

1. Bounded matching/paging and real-store tests, including Unicode, literal special characters,
   matches beyond page zero, data preservation and a large-history measurement.
2. Debounced latest-request reads and shared History browse workflow, with input/domain/visit
   ownership, error feedback and stale non-cancellable read tests.
3. Persistent live field and pagination, production wiring and actual Browse Compose checks.
   Keep the general Search field, recent-query storage, History recording and playback unchanged.
4. Controlled negative checks, full forced tests/lint, clean-source minified release APK with
   independent artifact checks. New feature trial 1.4.0; versionCode remains Git-based.

No merge without owner acceptance. Play release is a later owner-driven step; no push, tag,
master move, AAB signing or store upload is authorized by this implementation task.

## Separate finding

Existing `ProtracktorDatabase.onDowngrade` drops/recreates tables. This is a pre-existing data-loss
risk, not a B49 regression. B49 changes no schema and does not enter or modify that path. Record
and discuss it separately instead of coupling an unrequested database-policy change to this feature.

## Phone checks (pending)

- Filter title/author/filename, mixed case and non-ASCII letters; find a track beyond the first page.
- Type rapidly, change pages, clear input, leave/reenter and return after visiting another domain.
  No obsolete rows/counts/errors, stuck loading or lost keyboard focus.
- Check empty History versus no matches, counts and pages, PL/EN, and long-history latency.
- While playlist/Random/History playback continues, edit/clear the filter: no interruption or queue
  change. Explicitly playing a match captures those results; Next keeps that captured queue.
- Clear History while a filter/read is pending; all entries are removed and none reappear.

No phone outcome is claimed by implementation or host tests.

## Stage 1 checkpoint

44 checks in four suites passed, zero failures/errors/skips, 107 s: new matching/paging and real
store checks plus existing schema/qualification regressions. Real native SQLite on the host read
10,000 rows/5,000 matches in 168 ms; this is not phone latency. The first attempt failed compiling
two new test expressions (26 s, no test result); both were corrected before the successful run.
Bypassing matching, page offset and clamping failed all six new checks (99 s). Production source
was restored and matched its saved SHA-256 sum. Evidence: ignored `dist/b49-storage-*`.
The storage feature is not wired to UI yet; final restored/full gates and phone checks remain pending.

## Integration checkpoints

`HistoryBrowser` shares the production Browse transitions with the Compose tests. `HistoryReads`
captures phrase/visit ownership, cancels/debounces obsolete work and guards uncooperative late
reads/errors. Production reads wait for the History writer's clear barrier. Input and result scopes
are separate: query changes reset result scroll/follow state without disposing the focused field.
Current read failures clear loading, explain the failure and permit a labelled same-phrase retry.
No playback queue, History recording policy, general Search or WWW change is intended.

First integration run executed 34 checks: 18 data/coordinator checks passed; 16 UI checks could not
load `BrowseScreenKt` (`ClassFormatError`, illegal `<anonymous>` method), rather than demonstrating
screen behaviour. Replacing early exits inside the keyed Compose block with `when` branches
removed the class-load failure with the same JDK/dependencies. The retry passed all 34 checks in
six suites, zero failures/errors/skips, 123 s. A Polish UI check was then added.

Temporarily bypassing request generation and disconnecting the real text field's change callback
failed two of nine selected checks: identical-input stale completion and the actual Browse field
gesture path. Seven checks passed; zero errors/skips, 114 s. Both production files were restored
and matched saved SHA-256 sums. Evidence: ignored `dist/b49-browser-*`. Final restored/full gates
and the release APK remain pending at this checkpoint; no phone result is inferred.

**Restored focused gate:** 35 checks passed in six suites, zero failures/errors/skips, 109 s:
eight History browser, four existing read-ordering, 11 actual History UI (EN/PL), four matching/
paging, two real-store and six ordinary Search UI checks. Sources match the saved restoration
checksums. Evidence: ignored `dist/b49-focused-restored*`. Full gate/lint and release APK follow.
Navigation guards late publication; an IO scan already running at domain exit may finish before
closing its cursor. Superseding the phrase cancels it cooperatively per row.

## Full-gate checkpoint

Forced full run on implementation commit `27bd9fa` passed in 457 s: **897 tests in 119 suites**,
zero failures/errors/skips; the unit task actually executed, not cache/up-to-date. Lint XML contains
zero errors and 72 warnings, unchanged from the accepted base. PL/EN resources/store metadata,
581 page checks, 49 WASM engine checks, eight SAP checks, browser database upgrade, 30 native audio
checks, 140 icon paths, PWA and 11 loopback server checks passed. Existing JDK; temporary per-class
test isolation (one worker, 1 GiB heap), no exclusions or repository build-setting changes.
Evidence: ignored `dist/b49-full-gate*`, including copied JUnit/lint XML and Gradle logs.
Clean-source release APK and owner phone acceptance remain pending. No merge or Play step.

## Release APK handoff

Built from clean source `e4f5ccc` in 161 s: **1.4.0 (1185)**,
`dist/protracktor-release-1.4.0-1185-e4f5ccc-20261015-201306.apk`, 11,817,367 bytes.
SHA-256: `5170532e9363d331c5523b72812536d6f48ced2bc380106868e61a361e01518a`.
R8 actually executed. Independent artifact checks confirm APK v2 signature and unchanged test
certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`,
non-debuggable manifest, minSdk 29/targetSdk 36, arm64-v8a/armeabi-v7a/x86_64 and all four new
History resources. This is a minified test-key APK, not a Play upload artifact.

App/native/scripts/web/build sources match full-gate `27bd9fa`; the APK checkpoint and later
handoff change documentation only. Existing 1.3.3 APK is preserved. Checked 93 local documentation
file links without missing targets; this is not a complete external-link or anchor audit.
Evidence: ignored `dist/b49-release*` / `dist/b49-apk-*`. Phone behaviour/timing and acceptance
remain **not checked**. No merge/push/master/tag/store change or AAB build. After acceptance,
integration and the owner's Play release procedure are separate steps; discuss C108 separately.
