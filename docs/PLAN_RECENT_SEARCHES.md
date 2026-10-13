# B46 — recent search suggestions

Selected 2026-10-13 from real app-user feedback relayed by the owner. Branch
`feature/recent-search-suggestions`, starting from corrected B48 `d8dfb89` with explicit owner
approval for stacking. The earlier local unpublished base `ceeaa57` was amended before handoff.
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

## Implementation checkpoint — 2026-10-13

The production field, workflow, independent preference store and controller/ViewModel wiring are
implemented on this branch. Version 1.3.0 identifies the combined feature trial; develop remains
1.2.0 and schema 23 is unchanged. No merge, push or store release.

**Checked:** 42 focused host tests passed: 8 pure rules, 4 preference tests, 26 workflow tests and
4 actual Browse/Compose tests. Deliberately changing prefix matching/limit, recorded query,
suggestion submission and preference persistence failed 11/42 tests. All four sources were
restored and SHA-256 compared. Evidence is in ignored `dist/notification-search-verification/`.
The final restored full gate/lint passed as recorded below; the combined release APK is pending.
Real phone keyboard, focus/layout, restart and notification behaviour remain **not checked**.

Two full-suite attempts reached Compose idle timeouts: existing Settings tests in the first,
then the new Search test's teardown in the second. The stuck test workers were stopped; neither
attempt is a completed gate. All three Settings tests passed separately without source changes.
The successful full suite used a temporary init script: one test class per worker, one worker
at a time, 1 GiB heap, the existing JDK, and no test exclusions or repository build-setting changes.
The underlying shared-run timeout mechanism is not established by these observations.

**Earlier checked gate:** before the app-language correction, the forced combined gate completed
in 325 s: 825 unit tests in 111 suites,
zero failures/errors/skips; lint 0 errors and 72 warnings. Three new warnings recommend existing
KTX alternatives for URI parsing (two B48 locations) and preference editing (B46); they are not
test failures. PL/EN string checks, store metadata, 581 page checks, 49 existing WASM engine checks,
8 SAP checks, browser database upgrade, 30 native audio checks, 140 icon paths, PWA and 11 loopback
server checks also passed. These are host checks; no phone or store outcome is inferred.

That gate and the clean `9583569` APK preceded the B48 app-language correction. Neither is the
final trial proof/artifact: four new SDK 29/33 locale checks and the updated adapter/controller
required a fresh combined gate and APK. The corrected first branch passed 67 focused checks.
**Final checked gate:** the forced rerun completed in 331 s: 829 unit tests in 111 suites, zero
failures/errors/skips, lint 0 errors/72 warnings and all the same web/native/resource/loopback checks.
Afterward, deliberately bypassing feedback localisation failed 2/16 adapter tests; the source was
restored and SHA-256 compared, then 16 passed again. The full-gate source is unchanged by this proof.
The final clean-source APK follows; no merge or push, and both phone checklists remain unverified.
