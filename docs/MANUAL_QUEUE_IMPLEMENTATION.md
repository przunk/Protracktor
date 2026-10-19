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
