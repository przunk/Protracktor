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
An unrelated sentinel process survives cancellation. Three deliberately broken supervisor copies
(lost log writes, missing elapsed heartbeat, timeout reported as success) fail all three selected
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
the normal auxiliary checks/lint. Corrected focused checks pass; the final forced full gate is
pending. No readiness, phone or release claim.

**Checked real Gradle cancellation:** a disposable unconfigured project initially refused because
it lacked its own settings file (not timeout evidence). After adding that file, its real Gradle
9.6 single-use daemon reached a deliberate 60-second sleep. The supervisor timed it out at 15.0 s,
returned 124 after 15.3 s, and the recorded daemon PID no longer existed. This used only an ignored
`dist/` fixture, the existing JDK and offline Gradle, without stopping shared daemons.
