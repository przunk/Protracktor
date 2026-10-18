# Browse and download stability: implementation record

Owner-selected maintenance, 2026-10-16. Branch `fix/browse-download-stability`, from
develop `59ba578`; [scope and delivery contract](PLAN_BROWSE_STABILITY.md).
No new feature, playback-queue design, schema/version-name change or publication is selected.

## Reports and reproductions

These are retained code-review findings, not newly reported phone failures. Controlled host
sequences do not establish occurrence frequency on a device.

The baseline run compiled unchanged production code with five correctness regressions. All five
failed (`/tmp/protracktor-Protracktor-stability-before-all.log`, 91.4 s). The disposable ignored
init script disabled fail-fast only for that reproduction so all affected classes could run;
the project's normal full gate retains its configured process isolation and fail-fast behaviour.

1. Suspend catalogue A, visit a newer catalogue (including A again), then release the old read:
   old groups replace the new groups and clear their loading state. Leaving and restoring an
   identical route also admits old rows.
2. Suspend a local-folder index read, close and reopen the folder, then release the first read:
   the older tree overwrites the new visit.
3. Let Back's parent reader publish immediately: Back subsequently copies its captured child
   snapshot and erases the parent's result and concurrent fields.
4. Complete a cancellation-resistant read after replacing read-ahead with an empty selection:
   obsolete bytes re-enter the in-memory map. The clear variant is retained in the same test.

## Implementation stages

### 1. Navigation ownership — focused checks passed

Use a shared `BrowseReadOwner` and monotonic `BrowseState.navigationVisit`. Selection is published
at the call boundary, before queuing the read; completion checks both visit and route inside the
atomic state update. A route match alone cannot distinguish two visits to the same location.
Catalogue, format, author and local-folder reads use this boundary. Domain entry and snapshot
restoration advance the visit; restoration derives it from current state, not the old snapshot.
The controller's author jump checks ownership before navigation and before playback-side effects.
Its resolution intent borrows current visit ownership without retiring the existing read; an
unavailable jump must leave that read valid. A separate intent counter rejects older resolutions,
and Search's existing request revision retires it even if changed words/scope later return to the
same values. Direct capture-preservation and search-revision checks were added after the initial
focused run. Two initial full-gate attempts were deliberately interrupted after 75.2 s and 42.8 s
to finish these edge-case corrections; neither is a verification pass. The final forced run must
start again from the corrected committed candidate.
Independent catalogue/storage counts remain independent of navigation.

Checked: the final navigation-focused run passed all 47 tests in 116.2 s
(`/tmp/protracktor-Protracktor-browse-final.log`). The first focused run executed 43 tests, with 42 passing and one old assertion
expecting folder selection to remain deferred until after scan start. Selection now registers
synchronously to invalidate queued work; that assertion has been corrected. The final focused rerun
also includes four direct ownership checks. Cancellation/failure reporting policy
is not redesigned by this stage. Final full-gate evidence is below; device acceptance is pending.

### 2. Back publication — focused checks passed

Back now atomically publishes its parent transition before invoking the parent reader. It modifies
current state instead of publishing a captured complete snapshot afterwards. Each successful
transition advances the navigation visit; immediate/reentrant results and unrelated fields survive.
The controlled synchronous-parent regression failed on the old code. Existing search-scope, jump,
saved-place and local hierarchy contracts remain covered by the workflow suite.
Exiting Browse also retires outstanding navigation reads, even when no outside-playlist session
is active. The serial combined run passed all 65 checks in 110.1 s, including this workflow.

### 3. Read-ahead ownership — focused checks passed

Retire the previous generation before checking for missing bytes. Cancel after retiring ownership,
reject obsolete/cancelled completions and assign a lazy job before starting it. Empty and fully
held selections now cancel previous work. Clear retires the generation too; an immediate
cancellation callback cannot overwrite a preparation started reentrantly. Existing parallelism,
consume-once semantics and four/eight-megabyte limits remain unchanged. Duplicate candidates are
read once per request. The old implementation fails the controlled empty-selection regression.
The complete read-ahead class passed in the 65-check serial run, including cancellation-resistant
completion, same-ID replacement, reentrant cancellation and the existing memory limits.

### 4. Disk-cache transactions — focused checks passed

Historical review R7 predates `SharedFetches`: the current app already joins overlapping requests
for one URL. This is not a reproduction of duplicate network transfers. Disk operations have been
extracted into `RemoteFileCache` with the old write/evict algorithm first, so the actual shared
storage boundary can be exercised on real files without Android or network access. Controlled
tests expose two defects: an old eviction snapshot deletes a concurrent replacement, and a failed
rename leaves its staging file. The serial pre-correction run completed in 94.4 s, three tests,
two expected assertion failures and no reporting/worker errors
(`/tmp/protracktor-Protracktor-cache-before-serial.log`).

The correction serializes cache read/touch/write/snapshot/delete/accounting per app-owned
cache instance, keeping network transfers outside the monitor. Each cache/archive writer uses
a unique `.part` sibling and cleans up only its own staging file. The existing one-URL transfer
sharing remains; a cache recheck after joining closes the miss-to-transfer-completion window.
The cache budget, protection rules, URL hash keys and permanent archive locations stay unchanged.
An unreadable cache hit now falls through to the normal shared fetch rather than returning a
permanent null result. The app has one RemoteFiles owner in its application-scoped controller;
the monitor is not a cross-process lock and cannot prevent OS-managed cache removal.
The serial combined run passed all 65 checks (110.1 s), including real disk races, failed/successful
atomic writes, concurrent archive-style writers/readers, existing SharedFetches and the real local
HTTP User-Agent/cache-hit checks. No external archive traffic or private credentials were needed.

### Diagnostic-run limitation

Two diagnostic Gradle invocations were accidentally overlapped against the same build/results
directory. The Back/read-ahead run printed passing checks but ended with `EOFException`; the
overlapping cache run printed two expected assertion failures but also ended with a missing binary
results file. Neither is counted as a successful completed verification. Subsequent runs are
serial. The five-test initial regression run and the completed 47-test navigation run are separate
completed executions; the final forced gate must establish the complete candidate independently.

## Final handoff

**Checked, 2026-10-16:** clean source `f32e6cc75571e5bcd2dcddb8cededb8f92974832`;
`scripts/test-protracktor.sh --really` completed successfully in **468 s**. Unit execution took
299.1 s: **929 tests / 124 suites, zero failures, errors or skips**, counted from fresh JUnit XML.
All 31 unit-build tasks executed; no cached test result is presented as a fresh run. Lint completed
in 83.8 s: **zero errors / 72 warnings**, unchanged from the release baseline, verified in XML.
The initial terminal progress counter matched only 928 PASSED lines; XML and the gate's final
summary establish the actual 929-test total.

Other successful checks: PL/EN strings and store metadata, 14 test-runner fixtures, 11 disposable
release-flow scenarios, 581 web-page checks, 49 engine checks, eight SAP checks, web database
upgrade, 30 native audio checks, 140 icon paths, PWA and 11 local server checks. New local Markdown
links and `git diff --check` pass. Scripts, build configuration, schemas, native and web sources
are unchanged by this branch.

**APK:** `dist/protracktor-release-1.2.0-1214-f32e6cc-20261016-003937.apk`.
Built from the same clean source after the gate using `scripts/build-release.sh --no-daemon
-Pkotlin.compiler.execution.strategy=in-process`, supervised to a 1200-second deadline.
Build completed in 113.5 s, with R8 and resource shrinking; **11,830,407 bytes**.
Artifact metadata: `com.przunk.protracktor`, **1.2.0 (1214)**; arm64-v8a, armeabi-v7a and x86_64.
SHA-256: `ed73de347cd0d43cb82e01368e4991828c45ba3ceecf0833c369ac465b4a0106`.

`apksigner verify --verbose --print-certs` succeeds with APK Signature Scheme v2 and one test
signer (`CN=Android Debug`), SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
This is a minified release variant, not a debug variant or a publishable store bundle. Do not
uninstall/clear data to work around a signer mismatch with a Play-installed build.

Evidence logs are preserved in ignored `dist/browse-stability/`. The phone checklist is in
[TESTING](TESTING.md#browsedownload-stability-trial--2026-10-16). **Phone behaviour remains unchecked**;
host gates do not establish visual behaviour, real audio output or timing on a device.
At the original APK handoff, no merge, push, release tag/master movement, version-name bump,
migration or publication was performed.
The subsequent handoff commit changes documentation only; it does not relabel or rebuild this APK.

## Owner-authorized integration — 2026-10-18

The owner explicitly requested merging while phone testing remains pending. This authorizes
integration ahead of the normal device-acceptance hold; it does not establish successful phone
execution. Merged branch `1af5a72` into develop at `e9a7a98` without conflicts. **Checked:** the
complete merge tree equals the branch tree; application, native, web and script sources also equal
verified candidate `f32e6cc`. These are Git comparisons, not a repeated test/lint run.
The APK above remains the test artifact with its original SHA/versionCode. No new build, push,
master/tag movement, store release or migration; phone scenarios remain unchecked.
