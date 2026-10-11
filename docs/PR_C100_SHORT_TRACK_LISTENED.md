# C100 — Qualify naturally completed short tracks

Branch: `feature/played-before` (B43), at the owner's request on 2026-10-11.
C100 corrects that feature, so it belongs with it; C101 remains a separate branch.
Originally implemented as `0f70540` above the combined B42/B43 trial, then moved below B42.
The former `fix/short-track-listened` branch is superseded. No merge, push or publication.
B43 alone uses schema 22; the combined trial uses 23. C100 changes neither schema.
Status: host verified on the original combined stack; phone trial pending.

## Feedback → analysis → correction

User report relayed by the owner, 2026-10-11: a three-second track never receives the listened
marker although a five-second listen does. The requested rule is five active seconds **or a
natural end**. No exact file/build was supplied; the latest trial APK supplied was 1120.
This deliberately revises B43's original strict rule. On 2026-10-08 the owner found use acceptable
so far but explicitly withheld acceptance; neither B42 nor B43 is accepted by this correction.

The pure `ListeningSession` previously tested elapsed time alone. It now accepts a completion
signal with positive audio progress. `PlaybackController.handleTrackEnded` samples it before
automatic advance, subsong selection or repeat restart. Decoder EOF and known-duration ends
count, whereas the unknown-length fallback does not establish a natural end. Pause/resume keeps
eligibility. Seeking keeps earned listening time but disables the end shortcut for that
occurrence; five actual seconds still qualify. Clear, new occurrences and one-shot writes keep
their existing protections. PL/EN track details explain the revised rule.

No migration, Random ordering, catalogue naming, bookmarking or playback advancement changes.
The second user report about filenames versus titles is C101/[Q12](OPEN_QUESTIONS.md#q12--learned-titles-in-random-and-browse):
approved by the owner as a separate correction, not implemented here.

## Verification

The original temporary Gradle cache/build from October 7 no longer existed. The first attempt
spent time resolving dependencies and was stopped; an offline retry with a copy of existing
dependency caches reported missing Android dependencies. No Java or shared tooling was changed.
An online retry subsequently reached compilation, but overlapped the source update and compiled
the old main API against new tests, failing on `seeking`. The subsequent full run forced all
tasks to execute on the final, unchanged sources; that intermediate failure is not a regression.

Direct Kotlin/JUnit execution of the production `ListeningSession` and its repository tests
reproduced four failures among 12 tests with the old elapsed-only rule (only the new optional
input existed). After the fix, all 14 tests passed, including two additional seek/restart cases.
Two further tests cover the controller's stopped-poll-then-end sequence and a known-duration end
while the decoder is still playing: the final direct run passes 16 tests. Removing the seek
eligibility guard in an isolated copy fails the intended seek regression (one of 16 tests).
Production sources were not mutated during the full run. This is domain evidence, not a phone
test. The original combined stack's forced full gate passes in **270 s: 741 tests, zero failures/errors/skips**, lint without
errors, 581 page checks, 49 existing-WASM checks (not rebuilt), 8 SAP checks, web database upgrade,
137 icon paths, 8 server checks and store metadata validation. The unit-test invocation executed
all 31 Gradle tasks; its test result was not cached.
Command: `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`; existing JDK, project-specific temporary build
and Gradle cache paths as in earlier trial handoffs. Logs/reproduction command:
`dist/c100-verification/`. The owner approved C101 separately during verification; the combined
release **1.0.0 (1123)** from `cbb6f3e` is recorded in
[the C101 handoff](PR_C101_LEARNED_TRACK_TITLES.md). Its full gate passes 749 tests and lint.
No C100-only APK or phone result is claimed.

## Phone checklist — unverified

1. Use a previously unqualified short track (or clear History first). Play its approximately
   three seconds through to the natural end; the marker should appear in Browse and Random.
2. Stop/skip that track before the end: no mark. Pause, wait and resume through the end: mark.
3. Seek a fresh short track to near its end: no completion-only mark. Five real seconds still
   qualify a longer track after seeking. Retry from the start to test natural completion again.
4. Clear History while a short track is playing: its later end must not undo Clear. A fresh play
   may qualify. Revisit Browse/Random to check persistence; playlist rows still omit the marker.
5. Verify both decoder EOF and a known-duration end, repeat-one, automatic Random advancement and
   a short subsong. A completed subsong can mark the file, just as five seconds in one already can.

Install only a forward-compatible release, preserving existing data. Older schema-22/21 binaries
can destructively downgrade the combined trial database; do not use them as rollback APKs.
