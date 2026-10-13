# B46 — recent search suggestions

Selected 2026-10-13 from real app-user feedback relayed by the owner. Branch
`feature/recent-search-suggestions`, starting from B48 `ceeaa57` with explicit owner approval.
One combined minified release APK will test both; no develop merge before acceptance.

## Contract

- Android remembers the latest 20 unique nonblank submitted queries, newest first. Trim outer
  whitespace and deduplicate ignoring case; reusing a query moves its latest spelling to the front.
- Focusing the field shows up to three latest queries. While typing, select at most three
  case-insensitive prefix matches from the entire retained history, not just its first three.
  Match by `startsWith`, not substring; preserve recency among matches.
- Clicking a suggestion fills and immediately submits it in the CURRENT search scope. Do not
  restore previous archive/platform scopes, trigger a query on typing, or submit twice.
- After explicit submission hide suggestions/keyboard; tapping the field makes suggestions
  available again. Use labelled, accessible actions and PL/EN explanatory text.
- Persist separately from playback History/B43, outside Browse navigation snapshots. Clearing
  playback History does not clear query history. No separate clear-history UI was selected.
- No database migration, dependency, WWW search-history or media-button change. B47/C104, C96
  and B45 remain deferred. The existing ordinary search/result ordering contract remains intact.

## Verification

Pure tests cover recency, trimming, duplicates, 20-entry retention, prefix/case matching and
filter-before-limit. Android preference checks cover restart reads and malformed/unknown input
without touching other preferences. Compose tests use the actual field/suggestions and the real
BrowseWorkflow submission path with fake sources: typing only filters, a click submits once in
the current scope, scope is unchanged and blank submissions are not recorded. Mutate prefix/limit
and click/recording wiring; tests must fail, then restore and compare sources.

Run the full forced gate and lint for the combined B48+B46 sources, build a clean minified release
APK in dist, verify signing/version/source and record actual results. Owner checks three latest
entries, prefix filtering, older matching entries, immediate submission, restart persistence,
keyboard/scope/result layout, large font and both languages, plus B48's notification checklist.
No phone outcome or store release is established at this planning checkpoint.
