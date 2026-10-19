# Manual playback queue — implementation evidence

Branch `feature/manual-playback-queue`, based on audited develop `d6d3297`, 2026-10-19.
[Contract, audit findings, delegated choices and retained questions](QUEUE_CONTEXT_ANALYSIS.md).
No merge or push authorized. AMP and C111 padding work are excluded; versionName/schema unchanged.

## Stage 1 — pure scheduler

Added immutable request/occurrence identities, FIFO insertion around a source context, mixed
backward/forward traversal, explicit pending selection, source replacement, removal/Undo and
title enrichment. PlaybackSchedule returns typed selections or source transitions, not audio
side effects. PlayQueue gains a cursor-restoration boundary that retains live edits/modes/shuffle.
Source names are presentation, not playlist identity when replaying a saved continuation anchor.

**Checked partially:** the initial 19 scheduler tests passed. A deliberate mutation removed FIFO
priority; the targeted B/X/Y/Z/C regression failed as intended
(`/tmp/protracktor-manual-schedule-mutation.log`). The production rule was restored. The expanded
focused run passed **67 tests / 3 suites**: 20 scheduler, 40 PlayQueue and 7 ActiveQueue, no
failures/errors/skips, in 22 seconds (`/tmp/protracktor-manual-schedule-final.log`).

This is a domain checkpoint, **not an integrated player feature**. Controller/state/UI wiring,
real row/gesture regression tests, forced full gate/lint and a feature APK are still pending.
Device behaviour is unchecked. Do not reuse C110's 963-test result as evidence for this code.

## Stage 2 — controller integration (UI still pending)

The existing controller now arbitrates file Next/Previous/completion through the scheduler after
subsong handling. Actual file/origin is independent of context; pending Play/removal/Undo, source
replacement, Random continuation, current-aware Keep/History/metadata and priority prefetch are wired.
Retry captures epoch/occurrence; old retries cannot take over a later request. Pending operations
do not resume audio. Emptying/removing source rows must not stop a temporary manual occurrence.

Integration exposed a repeated-Random-file anchor hazard: remapping an unchanged/growing prefix by
file ID selected its last duplicate. Its regression failed before protection
(`/tmp/protracktor-manual-random-anchor-before.log`). Prefix restoration and metadata-only copies
now preserve positions. Source-list edits still use the existing remapping policy and never restore
an obsolete editor draft or shuffle choice.

**Checked partially:** final focused run passed **93 tests / 5 suites**: 22 scheduler, 10 projection,
7 real-controller/native-SQLite, 48 playlist-session and 6 subsong cases, zero failures/errors/skips
(`/tmp/protracktor-manual-controller-final.log`, 31 s). The controller tests preset the source and
use unavailable content URIs; they exercise scheduling and actual failure paths, **not native
audio playback**. Removing the manual-over-Random failure guard made its regression fail
(`/tmp/protracktor-manual-controller-mutation.log`); the guard was restored before the passing run.
An earlier 92-test run also passed before adding the source-emptying projection guard.

No complete UI feature or release APK is claimed yet. Row actions, two tabs, honest source markers,
pending swipe/Remove/Undo, host UI regressions and the final forced full gate/lint remain pending.
