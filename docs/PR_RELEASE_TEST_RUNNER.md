# Bounded, observable release validation

## Report and analysis — 2026-10-15

The owner ran the ordinary `release.sh 1.2.0 --check` path and reported an unexpectedly long
wait at stage 3. The agent inspected its current log: two `SettingsStereoMixTest` cases failed
with `AppNotIdleException`; the suite had not produced its final result. This is host-test
evidence, not a phone regression or an established explanation of Compose's idle failure.

`release.sh` waits synchronously for `test-protracktor.sh`, which redirects Gradle output into
a log and has no deadline. Failures are summarized only after Gradle exits. Shell `set -e`
cannot bound a command that never returns. The earlier 910-test successful gate used a temporary
per-class worker configuration absent from the ordinary release entry point. That missing
integration is an agent oversight. The shared-worker timeout's underlying cause remains open.

## Approved scope

- A task branch, no release execution, no merge/push, no new app feature or APK.
- Reproduce the missing deadline using the real script and a disposable stuck Gradle fixture.
- Bound unit tests to 20 minutes by default, with an explicit override; show progress, elapsed
  time and full log paths. Terminate only processes belonging to that invocation.
- Make the previously tested class isolation a tracked containment measure, without excluding
  tests or claiming it fixes the unknown underlying Compose issue.
- Exercise success, failed tests, a hang and interruption against disposable release repositories.
- Run the ordinary full test/lint path without an external init script before handoff.

## Verification

**Checked before the fix:** `python3 scripts/check-test-runner.py` executes the real test script
with a disposable Gradle stub that sleeps. The one-second configured deadline is ignored and the
five-second fixture watchdog fails the test, then kills only its own process group. One test,
one assertion failure, no error/skip. This reproduces the missing bound independently of Compose.
The owner's interrupted log separately establishes the two Settings idle failures; their cause
has not been reproduced or diagnosed by this fixture.

## Implementation

The normal gate now uses the tracked `scripts/internal/run-check.py` supervisor. It streams merged
stdout/stderr to the terminal and a complete per-stage log, reports elapsed time every 30 seconds,
and enforces a wall-clock deadline (including compilation). Unit tests, lint and the web build
default to 1,200 seconds separately. Remaining checks also have independent 1,200-second bounds;
this is not a 20-minute bound on the entire release or on interactive bundle signing.

On Linux/WSL the supervisor adopts its own orphaned descendants as a child subreaper. Cleanup
walks only its descendant tree via `/proc/PID/task/PID/children`, checks process start identities,
sends TERM and then KILL after a five-second grace, and reaps children. It does not enumerate or
stop shared Gradle daemons. Gradle checks use `--no-daemon` and in-process Kotlin compilation so
the active build belongs to this invocation; detached single-use Gradle daemons are covered too.
Exit 124 means deadline, 130 means Ctrl+C, and incomplete cleanup refuses success with 125.

The tracked Test configuration uses one class per worker, one worker at a time, 1 GiB heap,
fail-fast and started/passed/failed/skipped logging. This is containment of the observed shared-run
problem, not proof of its cause. No test is removed. Release validation requests `--really` to
force execution rather than reusing cached unit results. The existing test script also refuses
zero tests or non-passing JUnit totals and retains failure reporting when no result directory exists.
Unknown release options are rejected before any work, rather than falling through to a release.

Gradle's documented [worker isolation and test logging](https://docs.gradle.org/current/userguide/java_testing.html)
and [single-use daemon behaviour](https://docs.gradle.org/current/userguide/gradle_daemon.html)
inform these settings. No JVM, library, schema, app-version or signing change is made.

**Checked focused fixtures:** twelve Python checks pass, including the formerly failing entry-point
deadline, success/log/Unicode parity, error status, heartbeat, invalid deadline refusal, stubborn
detached descendants, Ctrl+C, a completed launcher leaving background children, missing results,
non-passing XML despite Gradle success and lint deadline propagation. The lint regression first
failed because an empty error grep under `pipefail` replaced status 124 with 1; reporting now
preserves the status and log path. Auxiliary checks use the same status-preserving wrapper.
An unrelated sentinel process survives cancellation. A supervisor copy with three deliberate faults
(lost log writes, missing elapsed heartbeat, timeout reported as success) fails all three selected
checks; the repository supervisor was not mutated.
An additional cleanup-window regression first failed with exit 0 when Ctrl+C arrived after the
launcher exited but before its workers were reaped. Completion now honours cancellation too;
the restored twelve-check run passes, including that window.

**Checked release flow:** all eleven disposable Git scenarios pass, including the real unit
entry point under deadline and Ctrl+C, unchanged refs/version/tree after each failure, ordinary
release, hotfix and bundle failure. The fixture initially lacked execute permission on its mock
Gradle wrapper; adding it enables the intended process path. Fixtures now use minimal local Git
repositories and fixed versions instead of cloning the entire history; no owner keys are used.

**First ordinary full-gate attempt:** all **910 application tests passed**, zero failures/errors/
skips, 369 seconds for the unit stage, including Settings. The expanded runner checks then caught
the lint status-reporting regression above, so this is not a completed full gate and did not reach
the normal auxiliary checks/lint. Corrected focused checks pass; the completed gate follows.

**Checked real Gradle cancellation:** a disposable unconfigured project initially refused because
it lacked its own settings file (not timeout evidence). After adding that file, its real Gradle
9.6 single-use daemon reached a deliberate 60-second sleep. The supervisor timed it out at 15.0 s,
returned 124 after 15.3 s, and the recorded daemon PID no longer existed. This used only an ignored
`dist/` fixture, the existing JDK and offline Gradle, without stopping shared daemons.

## Completed host verification and handoff — 2026-10-15

The ordinary command `PROTRACKTOR_BUILD_DIR_ROOT=/tmp/protracktor-search-ownership-build
./scripts/test-protracktor.sh --really` completed with exit 0 in **547 seconds**. No temporary
init script, additional test exclusions, fresh JDK or release invocation was used. Unit Gradle/test execution
took 360.9 s; lint took 94.8 s. JUnit XML confirms **910 tests in 121 suites**, zero failures,
errors or skips; lint XML confirms zero errors and **72 warnings**, unchanged from baseline.
This is executed test evidence, not a cache-only result.

The same gate passed twelve runner checks (8.1 s), eleven disposable Git release-flow scenarios
(14.0 s), resources/store metadata, 581 page checks, 49 engine checks, eight SAP checks, the browser
database upgrade, 30 native audio checks, 140 icon paths, PWA and eleven local-server checks.
Those Git scenarios execute release logic only in disposable fixtures with stub builds/test keys;
they are not real releases or evidence of production AAB signing.

Source chronology: the second forced gate started at `c46b1c1`. The cancellation-window-only
supervisor refinement and its extra regression were completed as `2be75c5` while unit tests ran.
App/build inputs and the test/release shell entry points are identical between those commits;
the subsequently executed twelve-check runner stage tests the final supervisor, including the
late-Ctrl+C guard. Final tracked script/build SHA-256 checks match. Application, native and web
source trees still match develop `13fbd4f`; the only Gradle change is Test-task configuration.
No claim is made that the already-running unit supervisor reloaded Python source mid-invocation.

Evidence is retained in ignored `dist/release-runner-*`: first/final full logs, copied unit/lint
logs, 121 XML suites, lint XML, focused/negative checks, source checksums and the real Gradle
cancellation log/fixture. Bash syntax, whitespace and store metadata checks pass; tracked worktree
was clean before this documentation closeout. Generated Kotlin sessions remain untouched/ignored.

**Pre-merge handoff:** ready for owner approval on `fix/release-test-runner`, not merged at that
checkpoint. No application APK/AAB,
phone run, push or release was performed. Master stays at `da4569b` / `v1.1.0`, develop at `13fbd4f`,
and `v1.2.0` is still free. The planned release is 1.2.0; the app literal remains 1.1.0 until the
owner's release procedure changes it. The cause of the shared-worker Compose idle failure remains
open; this task delivers tested containment and bounded/observable validation, not that diagnosis.

## Owner-approved integration — 2026-10-15

The owner authorized merging into develop. Merge `6736cd0` contains the approved branch
`6ec0a3a`; their complete tracked trees are identical. Script/build SHA-256 values match the
retained final checksums. This is source-equivalence verification, not a repeated unit/lint run;
the 547-second gate and focused evidence above remain the actual executed checks. The closeout
changes documentation only. No app artifact or real release was built, and nothing was pushed.
Master `da4569b`, every tag and every remote-tracking ref stayed unchanged; `v1.2.0` is absent.
The earlier pending-approval checkpoint is superseded for integration only, not for diagnosing
the still-open Compose idle issue or claiming device tests.

## Follow-up: concise console output — owner approved and merged, 2026-10-15

The owner completed an ordinary `--check` with 910 passing tests in 556 seconds, then requested
the previous concise terminal style. Streaming every successful test and auxiliary-check detail
was excessive. Scope is console presentation only: retain stage names, 30-second elapsed messages,
exit results and full file logs; show a bounded diagnostic excerpt only on failure/timeout.
Do not change class isolation, forced execution, deadlines, process cleanup or release transitions.
This is not a speed optimization and does not justify skipping the full release gate.

**Checked before correction:** three real-supervisor regressions fail: successful child output
leaks to the console, 2,000 passed cases flood it, and failure diagnostics are unbounded. The
existing log-retention and status/process tests remain the safety contract.

The helper now logs all child bytes without echoing successful command output. Status/heartbeat
notices remain visible. Nonzero completion displays at most forty lines read from the final 8 KiB
of the file, without duplicating the excerpt in the complete log. Process ownership, deadlines,
cancellation, test/build settings and release transitions are unchanged.

**Checked:** all **14** real-supervisor/entry-point checks pass in 7.7 s, including complete Unicode
logs, 2,000 successful lines hidden from the terminal, bounded failure diagnostics, deadline/Ctrl+C,
late cancellation and isolated cleanup. All **11** disposable release scenarios also pass. A direct
preview produces two console notices while retaining all 2,000 child lines (2,002 total log lines).
Bash syntax and whitespace checks pass. Application/build/native/web and `release.sh` sources
match develop `e750591`; only the helper, regression tests, a shell header and documentation change.

Evidence: ignored `dist/concise-runner-before.log`, `concise-runner-focused.log`,
`concise-runner-release-flow.log` and `concise-runner-preview.log`. No new 910-test/lint run is
claimed for this output-only tool change; the owner's 556-second run above is separate evidence.
No app artifact, release, merge or push at that handoff checkpoint.

The owner subsequently authorized merging `fix/concise-check-output`. Merge `420caed` into develop
has the same complete tracked tree as approved branch `6d1311b`; verified by Git tree comparison,
not a fresh test/lint run. The integration closeout is documentation-only. No app artifact, release
or push; master, tags and remote-tracking refs remain unchanged.
