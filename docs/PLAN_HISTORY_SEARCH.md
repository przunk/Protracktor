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
