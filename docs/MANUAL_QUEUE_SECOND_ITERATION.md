# Manual queue — second iteration

Audit/corrections: 2026-10-20. The first APK below was built on 2026-10-19.

Branch `feature/manual-playback-queue`, base `d6d3297`. Requested by the owner after implementation:
build a first APK, then audit requirements and every branch commit, correct worthwhile findings,
verify again and build the test candidate. No merge, push or store release is authorized.

## First iteration checkpoint

Clean source `783d8fa55607339bb0e12d0667b43ba20ea90e0a` passed the **forced** full gate in **525 s**:
**1011 tests / 133 suites**, zero failures/errors/skips; unit stage 351.6 s. All companion stages
passed: store metadata, runner/release fixtures, web page (581), engine (49), SAP (8), web database,
native audio host, icons, PWA and server (11). Lint: **0 errors / 72 warnings**, 82.9 s.

Intermediate release APK, **not the final phone candidate**:
`dist/protracktor-release-1.2.0-1238-783d8fa-20261019-235636.apk`, **11,881,407 bytes**.
SHA-256: `19b46afa07b4be0490a314560ce1a63acb2f0ca7d0c327bcdc9e69f8066746c7`.
Build: 118.7 s. Package/name/code, APK v2 test signature, absent debuggable flag, actual R8 task/
nonempty mapping, minSdk 29/targetSdk 36 and arm64-v8a/armeabi-v7a/x86_64 engine libraries checked.
Public test certificate SHA-256:
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
Retained reports: `dist/manual-queue-verification/783d8fa/` (ignored artifacts, not source files).

## Every implementation commit reviewed

| Commit | Scope reviewed and assessment |
| --- | --- |
| `bc426e5` | Pure FIFO/occurrence identity, mixed backward/forward insertion, source replay anchors, removal/Undo, metadata enrichment, tests and checkpoint docs. IDs separate equal files; source drafts are not restored from snapshots. Later positional-prefix protection fixes duplicate Random anchors. No extra editing/reordering/persistence is implied. |
| `91477f2` | All controller transport/completion/retry/prefetch changes; state/current/origin projection; playlist ownership/removal; VM/service entry; EN/PL feedback and domain/controller tests. Found an obsolete History policy still tested separately from production (F1). Paused resume and no-source boundaries are completed in the next commit. |
| `783d8fa` | All row-menu wiring, two-tab/modal/snackbar layout, pending gestures, dock availability, honest source markers, vector/EN/PL resources, absent-context rules, tests and documents. C110 fixture extraction preserves its seven scenarios. Found missing restored-row visibility assertion (F2), avoidable title-list allocation (F3), and architecture/status text needing the current ownership distinction (F4). |

## Requirements recheck

| Requirement / consequential boundary | Result of inspection |
| --- | --- |
| One source continues through navigation or editor playlist selection | Retained ActiveQueue context; explicit row Play alone replaces it. C110 root-navigation tests remain. |
| Manual additions play FIFO before context, regardless of source shuffle | PlaybackSchedule makes pending priority explicit, preserving parked cursor and heard forward suffix. Equal files have distinct request IDs. |
| Next/Previous and natural completion share the same rule | Controller routes file transport and completion after SubsongAdvance; screen/service/headset reuse it. Repeat One waits, Repeat All wraps source only. |
| Context versus actual audio must not be confused | State.current projects the actual occurrence; header, notification/heart, Keep, History and metadata consume it. Source rows have quiet position markers during manual playback. |
| Two views and pending removal by gesture | Context is read-only; pending has labelled Play/Remove, Info and end-to-start swipe with occurrence-specific Undo above the modal scrim. Unified editing, reorder and Clear remain excluded. |
| Pause/idle/failure/source replacement | Enqueue never starts audio. Play resumes known paused current before pending. With no chosen source, drain and stop. Failed manual over Random stays retryable. Explicit source Play/reroll preserves pending and resets mixed traversal. |
| Edits, duplicates, learning, retry races and retained AMP data | Live source edits/modes are retained during replay, duplicate Random prefixes stay positional, retries check epoch/occurrence, AMP guards/schema unchanged. Native audio timing still needs phone testing. |
| Unsettled product choices are saved, not blocking | The contract retains provisional answers and post-implementation questions. No per-rule owner acceptance is fabricated. Session-only lifetime is explicit. |

No further product scope was selected by this audit. In particular, do not add reordering, Clear,
queue persistence, web queue controls, AMP integration or C111 padding fixes under this task.

## Findings and disposition

### F1 — obsolete History policy was outside the production path

Stage 2 correctly consulted actual occurrence origin directly, but left HistoryRecording and its
tests implementing the old screen/results-mode rule. That was misleading coverage and two policy
definitions. Consolidate into one origin-based pure policy called by the controller. Adapt legacy
shared rule fixtures at their test boundary, not by retaining a second production rule. Add a real
controller/ordered-writer/native-SQLite case for both origin directions. The old-rule mutation
failed it as intended: a History-origin request over a playlist changed playCount 1 to 2 and
changed its timestamp (`/tmp/protracktor-manual-history-mutation.log`, 29 s). The production path
is now restored through the origin-based policy and passed the combined positive run.

### F2 — Undo coverage checked restored state, not the restored row

The physical-swipe test proved removal and actual snackbar Undo, but only checked the restored
model. It would not catch an off-screen/dismissed restored occurrence. The strengthened test asserts
that its row and Play control are displayed after Undo and passed with the other three shell cases
and the new History integration case (`/tmp/protracktor-manual-audit-baseline.log`, 30 s). This is
coverage strengthening, not evidence of a reproduced UI defect.

The first History probe bypassed the production writer to seed SQLite directly while controller
startup writes were active; it hit SQLITE_BUSY. The corrected test seeds through the same ordered
HistoryWrites used by production and uses a following accepted write as a completion barrier,
without arbitrary sleeps. No application/database workaround was added for that fixture failure.

### F3 — context presentation remapped every row on each position tick

The panel mapped all source titles on every recomposition, even when only progress changed. The
older panel already did this; the larger new panel made retaining that cost unnecessary. Memoize
the projection by source tracks and learned-title map; verify that learning while the panel stays
open still updates titles without moving rows. Omitting the learned-title key deliberately made
that regression fail (`/tmp/protracktor-manual-title-mutation.log`, 35 s); both keys are restored.
The new UI regression passed in the combined positive run. No timing/performance gain on a phone
is claimed.

### F4 — architecture and current checkpoint text were stale

Architecture still described the context as the sole playback owner and the panel as wholly
read-only. Update it to distinguish actual occurrence, source continuation and pending editing;
retain older accepted-baseline evidence as history. Link the second-iteration report and do not
reuse the first APK's counts/SHA for a later build. Architecture, contract, implementation evidence,
Status, roadmap, wishlist and Q13 now distinguish these states. Stale retry/ownership comments were
also corrected and shortened. Local path checks passed for 151 links; final review remains.

### F5 — test cleanup could close SQLite before cancelled controller tasks finished

The first combined corrective run stopped waiting for the initial History write, despite that case
passing separately. The fixture cancelled its controller scope but immediately closed the database
and dispatchers without awaiting children. Both new controller/shell harnesses now drain Main and
wait for the cancelled scope to complete **before** closing resources. The next combined run passed
(`/tmp/protracktor-manual-second-focused-cleanup.log`, 43 s). This fixes an unsafe test lifetime;
it does not prove the sole cause of every earlier SQLITE_BUSY or claim an application database fix.

## Completion evidence

Corrective code and tests have been self-reviewed as a diff against `783d8fa`, including restoration
of both deliberate mutations. The combined corrective focused run passed **89 tests / 8 suites**,
zero failures/errors/skips in 43 s; final forced full gate,
lint and new release APK are pending. The corrective commit/report closeout will also be reviewed.
All device audio, notification/service layout, gesture feel, Bluetooth and usability remain
**unchecked**. [Phone checklist](TESTING.md#manual-queue--unmerged-experiment-phone-checks-pending)
and [provisional product questions](QUEUE_CONTEXT_ANALYSIS.md#questions-retained-for-post-implementation-evaluation).
