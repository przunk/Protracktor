# C106 — search request ownership

## Report and scope

Code-review finding R2, recorded on 2026-10-14, selected by the owner on 2026-10-15 after the C105
handoff. This is not a new independent app-user report. Branch `fix/search-request-ownership`
starts from C105 `ad9ec54` with explicit owner approval; C105 and its 1.3.1 (1170) APK remain
unchanged and unmerged while the owner tests them.

`BrowseWorkflow.runSearch` captures an input request but unconditionally publishes rows, counts
and loading into the current Browse state. Its live-result callback can likewise show an old
archive notice after navigation. The existing characterization test already demonstrates older
results replacing History after a newer query. The defect predates B46 autocomplete.

## Selected correction

- Only the latest still-relevant submitted request may publish search rows/counts, live-source
  notices or completion. Preserve unrelated Browse updates and capture indexed sources at submit.
- Editing words, changing scope/catalogue/platform filters, Back and leaving/restoring a search
  retire pending work. Returning to identical inputs does not resurrect an abandoned request.
- Cancel obsolete work where possible, but also guard uncooperative late completions and errors.
  An old finally block must not clear another request's or another Browse view's loading state.
- Typing and filter changes still do not search. Retain completed results where the existing
  navigation/filter contract retains them. Autocomplete, History retention, source matching,
  result ordering, playback and schema are unchanged.
- Current failures stop loading and use localized feedback; cancellation is not a search failure.

## Regression checkpoint

Controlled source tests exercise the real workflow/search service, with non-cancellable reads
where needed to distinguish ownership protection from cancellation alone. They cover overlapping
searches, navigation/ABA, filter edits, live notices, pre-launch invalidation, current failures and
loading cleanup. Before correction, **36 workflow checks executed; 14 failed by assertion**, zero
errors/skips. These are ten added checks and four adjusted characterization checks; 22 existing
checks passed. The cold build took 176 s using the installed JDK and temporary per-class isolation.
Log/XML: ignored `dist/c106-regression-before-retry*`.

The first cache/bootstrap attempt failed before tests with a missing Kotlin compiler helper class
while cache files were still being copied. The completed runtime JAR contains that class and
matches its source SHA-256; a fresh Gradle process without configuration-cache reuse executed the
tests above. The bootstrap failure is not regression evidence. No JDK/tooling change or phone
result is claimed. No production correction at this checkpoint.

## Implementation

`BrowseWorkflow` retires the previous owner before cancelling its job. Publication requires both
the current generation/owner and the original domain/query/scope. Retired non-cancellable reads
cannot publish into a newer request, an unrelated loading view or restored identical inputs.
Loading cleanup follows the same ownership guard. Lazy launch assigns the job handle before
`Main.immediate` can complete or reenter. Current exceptions clear loading and reach a required
controller callback with PL/EN feedback; cancellation and obsolete errors stay silent.

The same retirement entry point covers domain/Back/input edits plus explicit controller History,
Playlist, author navigation and snapshot restoration. Failed author lookups that do not navigate
do not retire the visible search. Same-value query edits and inapplicable filter toggles are no-ops.
Submission history is still recorded once per explicit request. No autosubmit, result-sort change,
database migration, dependency, toolchain change or generalized catalogue/folder race fix.
The combined trial declares 1.3.2; versionCode is still calculated from Git history.

## Mutation check

Temporarily bypassing ownership and removing the same-value-query guard caused **12 assertion
failures out of 39 workflow checks**, zero errors/skips, in 93 s. This includes the three additional
checks for identical-input resubmission, no-op edits and explicit retirement before snapshot restore.
The production sources were restored and matched their saved SHA-256 sums. Evidence: ignored
`dist/c106-ownership-mutation.log` and `.xml`, `dist/c106-restored-sources.sha256`.

## Restored focused verification

**Checked:** 104 tests in four suites passed, zero failures/errors/skips: 39 Browse workflow,
11 `LibrarySearch`, six actual Compose autocomplete checks and 48 C105 playlist-session checks.
The Gradle run took 117 s, using the installed JDK and temporary per-class isolation. Log/XML:
ignored `dist/c106-focused-restored.log` and `dist/c106-focused-restored-results/`.
This is a host test result, not phone UI/navigation acceptance. Full gate/lint and release APK follow.

## Phone checklist

Phone behaviour is **not checked** until the owner reports these results. Install the combined
release trial without clearing data. Record APK/build and Android/device with any failure.

1. Submit query A, then quickly submit B (including tapping a recent suggestion). Only B may finish
   into the results; an older completion must not remove B's spinner or change its rows/count.
2. Start a live The Mod Archive search, then leave for History, Online Catalogues, Playlist or an
   author view. Wait for the old request: no ghost search rows, stale archive notice or unexpected
   loading change may appear. Return to the same inputs and submit again; the old request stays retired.
3. While searching, edit words or scope/platform/catalogue filters. Pending work must stop owning
   loading; edits alone must not start another search. Explicit submission uses the new inputs.
   Previously completed rows still follow the existing Back/filter-retention contract.
4. Check original integrated magnifier, IME submit and plain-text autocomplete: at most three
   case-insensitive prefix matches, one submission per choice, current scope retained. Check stored
   suggestions after restart and PL/EN feedback. An active non-cancellation failure should end
   loading and explain the failure; leaving the request must not produce that obsolete explanation.
5. Smoke-test playback, rapid playlist A/B/C switching, Save/Discard/restart and notification-heart
   destination using the C105 checklist. C106 must not change audio queues or playlist persistence.

Do not merge either pending branch or infer acceptance from host tests. No push, master/tag,
Play Console or publication action is part of this task.
