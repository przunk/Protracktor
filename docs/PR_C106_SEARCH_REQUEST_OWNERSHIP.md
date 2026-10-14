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
