# B43 — Played before (experimental local PR handoff)

Branch: `feature/played-before`, from `develop` at `00abd8f`.
Status: host-verified candidate with release APK; phone trial pending; no GitHub PR opened,
push or merge performed.

## Feedback → analysis → implementation

User feedback, relayed by the owner: listeners want to recognise tracks already tried, including
those rejected quickly. The owner selected a reversible experiment, not a commitment to retain it.
Rules and the eventual retain/revise/abandon decision live in [the plan](PLAN_PLAYED_BEFORE.md).

History already records opened tracks, including those that have not played five seconds. Reusing
row presence for future plays would incorrectly mark quick skips. Schema 22 therefore adds a
qualification flag while preserving existing History timing/counts and importing old entries once.
`ListeningSession` enforces the active-time threshold; ordered `HistoryWrites` and guarded
`PlayedStatusReads` protect clear/navigation races. Only Browse and Random render the small status
glyph; playlist membership does not suppress it there. No Random exclusion/filter is introduced.

## Verification and handoff

Focused tests cover threshold timing, paused/stalled/short audio, seeking, new occurrences,
subsongs, history clearing, delayed writes/reads, legacy migration and upsert preservation, actual
Android SQLite store operations, batched IDs, and Compose Browse/Random/playlist presentation.
The three migration regressions failed against schema 21 before implementation. Deliberately
breaking the timer threshold, stale-write guard, qualification-preserving upsert, Random marker
wiring and late-read guards caused 12 failures among 42 focused tests. Removing the new column
from the exported schema alone caused the snapshot test to fail (35 tests, one failure), proving
the external file invalidates Gradle's test task. All mutations were restored.

Two native-render tests passed and produced four inspected previews (EN/PL, light/dark) in
`app/build/screenshots/b43-*.png`. This checks host rendering, not actual phone interaction.
The forced full gate passed in 240 s: **703 tests, zero failures/errors/skips**, lint without errors,
581 page checks, 49 existing-WASM checks, 8 SAP checks, the web database upgrade, 135 icon paths,
8 server checks and store metadata validation. No WASM rebuild or phone test is claimed.
Command: `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`; existing toolchain, output root
`/tmp/protracktor-origin-sync-build.S8adiD`, Gradle cache `/tmp/protracktor-architecture-gradle`.
Logs and previews are retained in `dist/b43-verification/`.

### Release artifact

- Source: clean `7ea74d3`; version **1.0.0 (1111)**, schema 22.
- APK: [`protracktor-release-1.0.0-1111-7ea74d3-20261007-181629.apk`](../dist/protracktor-release-1.0.0-1111-7ea74d3-20261007-181629.apk), 11,706,010 bytes.
- SHA-256: `d34b968427a24ef84fff0c039c02cf61aa678ded37eae2a4fe26146e7f6a6d84`.
- `build-release.sh --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`
  passed in 132 s; R8 minification ran, APK is non-debuggable with all three configured ABIs.
- APK Signature Scheme v2 verified. Test certificate SHA-256:
  `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e` (matches the accepted previous APK).
  No publication key or AAB was used. Later documentation changes do not alter these sources.

Install as an update without clearing app data. If Android reports a signature mismatch, stop and
report it; do not uninstall the existing app to force installation.

## Owner trial (not yet run)

1. Install the release candidate as an update, keeping all data. Old History items should have the
   tiny history glyph in Browse/Random, never on the playlist. Track details explain it in PL/EN.
2. Try a new track for under five seconds, then another for over five. Only the latter qualifies.
   A pause or seeking forward must not count as listening; normal background playback must count.
3. Clear History during playback: marks disappear and the current occurrence must not re-add one.
   New playback can qualify again. Restart must not restore cleared legacy marks.
4. Check Random Next/Previous, filters, repeated picks, playlist playback, favourites and cache
   behaviour. Marked tracks remain eligible for Random exactly as before.
5. Evaluate usefulness separately: does the mark help recognise tried music, and is it discreet
   enough without being confused with the cache/favourite indicators? Report retain, revise or
   abandon, including what helped or distracted. No positive result is assumed.

**Do not reinstall an old schema-21 APK to abandon the experiment.** Its existing downgrade path
can erase the database. Ask for a forward-compatible schema-22 build without the experimental
behaviour instead. No data clearing, schema downgrade, publication or merge is authorised by
the trial. The old binaries' downgrade policy cannot be fixed by installing this new candidate.
