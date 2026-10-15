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

Implementation and final verification are pending. No readiness, device or release claim.
