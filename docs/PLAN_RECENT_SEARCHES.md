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
  available again. Preserve the original integrated magnifier, with its localised accessibility
  label. Suggestions are plain text rows in an anchored dropdown, without icons or a heading.
  The owner explicitly selected this search-field exception to the general labelled-action rule.
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
The final restored full gate/lint passed as recorded below; the combined release APK is ready.
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
The final clean-source APK is ready; no merge or push, and both phone checklists remain unverified.

## Combined trial handoff — 2026-10-13

Offered minified release: **1.3.0 (1154)**, clean source `1452309`, built in 35 s:
`dist/protracktor-release-1.3.0-1154-1452309-20261013-224501.apk`.
Version, non-debuggable manifest, three ABIs, retained new resources and APK v2 test signature were
checked. [Artifact size/hash/certificate and limits](STATUS.md); [phone checklists](TESTING.md).
The earlier `9583569` APK is superseded. The two features remain on separate stacked branches;
develop/master, schema and publication are unchanged. Handoff documentation does not alter the
APK's app/native sources. No installed build or device outcome is inferred from producing it.

## Owner-reported presentation correction — 2026-10-13

The owner reported that the trial changed the integrated magnifier into a labelled Search
button and presented recent queries as icon buttons under a heading. This is real owner trial
feedback, not an independent tester or an agent reproduction on a device. The initial implementation
over-applied the general labelled-action convention to a specialised text-field interaction.

Restore the existing field appearance and use Material's editable anchored autocomplete dropdown:
plain text suggestions, no heading/icons, no extra section pushing scope controls/results down.
Keep the IME focus while typing. Explicit submission still hides suggestions/keyboard; refocusing
and editing reopen matching suggestions. Retention, prefix/case matching, current-scope submission,
persistence and the three-entry limit are unchanged. Revised host and phone checks follow.

**Checked:** the revised real Browse/Compose suite failed 4/6 checks with the old inline buttons
and labelled search action. After restoring the integrated icon and editable popup, all 44 focused
checks passed (8 rules, 4 preferences, 26 workflow, 6 UI), zero failures/errors/skips. This includes
actual magnifier/IME/suggestion clicks, popup dismissal, refocus, both languages and immediate
submission in the current scope. Evidence: ignored `dist/recent-search-popup-{before,after}*`.
No dependency or toolchain was upgraded. A full combined forced gate and release build follow;
real keyboard, touch layout and notification placement remain phone checks.

**Corrected combined gate checked:** forced rerun completed in 346 s: 835 unit tests in 112 suites,
zero failures/errors/skips; lint 0 errors/72 warnings (unchanged count). All existing PL/EN,
store, web, native audio, icon, PWA and loopback checks also passed. The same temporary per-class
worker isolation/1 GiB heap and existing JDK were used, without exclusions or repository settings
changed. Evidence: ignored `dist/notification-search-verification/full-gate-presentation*`.
The replacement release APK is recorded below; phone acceptance is still pending and
develop/master are unchanged.

## Corrected combined trial handoff — 2026-10-13

Minified release **1.3.0 (1158)**, clean source `f289863`, built in 56 s:
`dist/protracktor-release-1.3.0-1158-f289863-20261013-232128.apk`.
Manifest/version/ABIs, APK v2 test signature and retained reservation/heart/magnifier resources
were checked; the removed heading resource is absent. [Artifact size/hash/certificate](STATUS.md)
and [corrected phone scenarios](TESTING.md). The earlier `1452309` trial is superseded following
the owner's presentation feedback. Handoff documentation does not change the gate/APK sources.
Both features remain on their separate stacked branches, unmerged, with phone acceptance pending.

## Unchanged search in the stable-heart follow-up — 2026-10-14

The owner reported the corrected search field as acceptable on 2026-10-13, then approved B48's
stable-save action/shared subtitle/flat-P follow-up. B48 `6b4efca` is integrated into this trial
branch without changing search field, preferences, rules or workflow sources. This is not full
acceptance of every retention/restart checklist result. The new forced combined gate passed in
364 s: 852 tests/115 suites, zero failures/errors/skips, lint 0 errors/72 warnings plus all existing
web/native/resource/store/loopback checks. Evidence: `full-gate-stable-heart*` in the ignored
verification directory. A replacement minified APK follows; both branches remain unmerged.

The replacement is **1.3.0 (1162)** from clean source `ea04525`, built in 51 s:
`dist/protracktor-release-1.3.0-1162-ea04525-20261014-001502.apk`.
[Artifact checks](STATUS.md) and [phone retest](TESTING.md). The prior `f289863` APK is superseded
for the saving-state issue, not a new search change. Handoff is documentation-only; no merge/push
or full feature acceptance is implied.
