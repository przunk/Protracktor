# Manual playback queue — implementation evidence

Historical first/second-iteration evidence. User testing on 2026-10-20 selected a unified list and
one-shot manual requests; [the third-iteration contract](QUEUE_UNIFIED_LIST.md) supersedes the
two-tab/mixed-history rules here. Existing artifacts and measurements remain unchanged.

Branch `feature/manual-playback-queue`, based on audited develop `d6d3297`, 2026-10-19.
[Contract, audit findings, delegated choices and retained questions](QUEUE_CONTEXT_ANALYSIS.md).
No merge or push authorized. AMP and C111 padding work are excluded; versionName/schema unchanged.
The stage notes below retain their checkpoint evidence. Both APK/gates and the completed
owner-requested second-iteration audit are recorded in [the second-iteration report](MANUAL_QUEUE_SECOND_ITERATION.md).
Final phone candidate: **1.2.0 (1239)**, clean source `3d27868`, **1013 host tests**, lint 0 errors/
72 warnings. Unmerged; all phone behaviour unchecked. The first code-1238 APK is intermediate.

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

## Stage 3 — panel and row actions

Playlist, Browse/Search/History, Random and Context row menus now offer Add to queue through one
shared menu item. The playback panel separates read-only Context and pending Queue. Its common
header uses the actual track and shared Now Playing subtitle; parked source positions have quiet
text/icon markers, not a false playing indicator. Pending occurrence IDs own Play, Remove and
end-to-start swipe. The shared snackbar is inside the modal so its Undo is reachable.

Two idle/resume boundaries became explicit: ordinary Play resumes an existing paused source before
pending even when its decoder is released; manual playback from a never-started editor list instead
uses an absent context and stops after pending. The paused-source regression failed before the
controller priority correction (`/tmp/protracktor-manual-resume-before.log`).

Initial host UI run: **19 tests / 3 suites** passed (7 C110, 4 real-shell manual queue, 8 existing
Queue UI cases), `/tmp/protracktor-manual-ui-focused.log`, 59 s. Removing the actual swipe callback
while leaving the Remove button wired made the physical-swipe regression fail as intended
(`/tmp/protracktor-manual-swipe-mutation.log`); the callback was restored. A later combined run
stopped at a Compose-idle timeout in an existing reactive Random test. That run is **not a pass**;
the test now explicitly flushes snapshots and advances its clock, like the other reactive UI cases.
The corrected focused rerun passed **63 tests / 6 suites**, zero failures/errors/skips, in 50 s
(`/tmp/protracktor-manual-ui-final.log`): 23 scheduler, 10 state projection, 9 controller,
4 manual-shell, 7 C110 and 10 Queue UI cases. Controller/host UI tests do not decode native audio.

The owner requested a second iteration **after** the first verified APK: review requirements and
all branch commits, record findings, fix justified issues, repeat verification and rebuild if code
changes. Neither this checkpoint nor the first APK is the final test candidate. The first forced
gate passed 1011 tests/lint (0 errors, 72 warnings) and APK 1.2.0 (1238) was built from clean
`783d8fa`; exact artifacts and the completed corrective iteration are in the second-iteration report.
All phone behaviour remains unchecked. No merge, release or push.
