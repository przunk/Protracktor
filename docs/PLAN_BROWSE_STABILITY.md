# Browse and download stability

Owner-selected work, 2026-10-16. Baseline: develop `0bc82e7` (release name 1.2.0).
This is a bounded correctness pass, not a playback-queue redesign or a repository-wide refactor.
Existing navigation, playlist persistence, Random history and cache-budget policy must remain intact.

## Delivery sequence

1. **Navigation ownership.** Reproduce out-of-order catalogue and local-folder reads using
   controlled coroutine gates, including leaving and revisiting the same destination. Give reads
   an explicit current-visit owner; obsolete completions must not replace rows or loading state.
   Keep unrelated catalogue/storage counters independent of navigation.
2. **Back publication order.** Reproduce a synchronous/reentrant parent read being overwritten by
   the captured child snapshot. Publish the parent transition before invoking its reader and
   preserve unrelated concurrent state. Retain search/jump/saved-place Back semantics.
3. **Read-ahead ownership.** Retire previous work even when the new selection is empty or fully
   cached. Reject late bytes after replacement or clear, including cancellation-resistant reads.
   Preserve concurrent fetching, consume-once behaviour and existing memory limits.
4. **Disk-cache transactions.** Recheck historical review R7/R8 against current SharedFetches;
   do not assert that an already prevented duplicate download still reproduces. Protect temporary
   writes, cache reads and eviction at their actual shared boundary. Exercise production storage
   code with controlled JVM filesystem/concurrency tests, without network or private credentials.

Each implementation stage ends in its own commit with a report, mechanism, regression checks and
verification evidence. A failing regression on the old code is required before its correction;
already-fixed historical findings get a preservation check and an honest documentation correction.
Use one task branch from develop; do not merge, push, move release tags or advance master.

## Verification and handoff

- Focused deterministic host tests, including cancellation-resistant and immediate callbacks.
- Full `scripts/test-protracktor.sh --really`, including lint and available companion checks.
- Minified, test-signed APK via `scripts/build-release.sh`, in `dist/`, with SHA/version identity.
- Update STATUS, architecture, testing checklist and this plan with actual results and limitations.
- Phone verification remains **unchecked** until the owner runs the scenarios. No automatic merge
  and no store release; versionName, database schema, dependencies and queue rules stay unchanged.

## Progress

- Stages 1–3 implemented as separate commits; navigation-focused checks: 47 passed.
- Stage 4: old disk algorithm extracted and exercised before correction; serial reproduction:
  three tests, two expected failures (obsolete eviction and staging-file cleanup), 94.4 s.
- All four corrections implemented; combined serial focused run: 65 passed in 110.1 s.
- Final clean candidate `f32e6cc`: forced full gate passed in 468 s, 929 tests / 124 suites,
  zero failures/errors/skips; lint zero errors / 72 existing warnings. All companion checks passed.
- Minified test-signed release APK 1.2.0 (1214) built and verified; exact identity is in the report.
- Technical handoff complete. Owner explicitly authorized integration on 2026-10-18 before phone
  acceptance; merged into develop at `e9a7a98`, with the complete branch tree preserved.
  Phone checks remain pending; nothing pushed/released and no new APK built for the merge.
- [Mechanisms, intermediate failures and evidence](PR_BROWSE_DOWNLOAD_STABILITY.md).
