# Code review — round 5, 2026-09-03

A review of the whole `develop` tree after round 5's six implementation items, as `GOAL.md` Phase 2
asks for.

## How to read this

Two rules were written into `GOAL.md` before the review started, because the review is done by
whoever wrote the code:

1. **`confirmed` requires evidence that can fail** — a failing test, a deterministic diagnostic, a
   measurement. Reasoning, however good, produces an *unverified risk*.
2. **Findings in code written earlier in this same run get more suspicion, not less.**

That second rule earned its place immediately: the most serious finding here, **R1**, is in code
written a few hours earlier and would have made the largest piece of work in the round look
completely broken on a phone.

Nothing in this document is confirmed by running the app on a device. That remains true of the whole
project.

---

## Confirmed defects

### R1 — every Atari ST track stops on the first audio callback · **critical**

**Where:** `native/engine/engine.cpp`, `Sc68Backend::render`.

**Mechanism.** `sc68_process` returns `SC68_IDLE|SC68_CHANGE` and **zero frames** on its first call
for a freshly played track; the machine has not been run yet. `render` passes that straight back:

```cpp
if (count <= 0) return ended_ ? 0 : 0;
```

The player treats a short render as the end of the tune — `onAudioReady` silences the rest of the
buffer and stops the stream — so the first callback ends the track.

**User impact.** Every SNDH and `.sc68` file plays nothing and stops immediately. That is the entire
Atari ST format family, and it would have made round 5 item 1 — which took `.sndh` from 14 of 30 to
30 of 30 — appear to have broken playback completely.

**Evidence.** `sc68_process` was measured directly against three real Modland files
(`/tmp/firstpass.c`, built against the probe library):

```
pass 0: code=3 frames=0 SHORT
pass 1: code=0 frames=1024
pass 2: code=0 frames=1024
```

Three of three. The player's contract is at `engine.cpp`, `onAudioReady`: `if (rendered <
numFrames)` → silence and stop.

**Why the probe missed it.** `probe-sc68.py` asks "does this file produce audio", loops, and ignores
a short pass. It answers the question item 1 asked and not this one. A probe that mirrors the
backend's *interface* would have caught it; one that mirrors its *purpose* did not.

**Correction.** Loop until the buffer is full or the track genuinely ends, with a bound of eight
consecutive empty passes so an idle decoder costs one silent buffer rather than a locked-up device.

**Status:** **fixed**, guarded, and **confirmed on a device** — SNDH played on
2026-09-03. `native/probe/sc68/probe_render.c` mirrors the backend's *contract* rather than its
purpose: it asks whether the **first** buffer comes back full. Five of five files answer `full` with
the fix and `short:0` with the single-call logic restored, which was checked rather than assumed.

### R2 — the shared open-error string is a data race · **high**

**Where:** `native/engine/engine.cpp`, `lastOpenError()`; called from `openBackend`.

**Mechanism.** One process-wide `static std::string`, cleared and written by every `openBackend`
call and read afterwards through JNI. Until round 5 there was one caller at a time. There are now
three that can overlap — playback (`load`), the library scan (`probe`), and background metadata
resolution — because item 2 made scanning concurrent with playback on purpose.

**User impact.** At best an error message describing a different file. At worst undefined
behaviour: concurrent `clear()` and assignment on a `std::string` can corrupt its internal state
and crash the process.

**Evidence.** `native/probe/engine/open_error_race.cc` holds both patterns and runs either:

```
g++ -fsanitize=thread -g -O1 -o /tmp/openrace native/probe/engine/open_error_race.cc -lpthread
setarch -R /tmp/openrace before   -> 3 × WARNING: ThreadSanitizer: data race
setarch -R /tmp/openrace after    -> clean
```

`setarch -R` because ThreadSanitizer will not start under WSL2 with address-space randomisation on.

**Correction.** Make the error per-open rather than per-process: `openBackend` takes it by
reference, `nativeOpen` writes it into a one-element `Array<String?>`, and `NativeEngine.open`
returns an `Opened(track, error)`. The global and its JNI accessor are gone — the release APK
carries 13 native symbols where it carried 14.

**Status:** **fixed**, and the before/after pair is kept in the tree so the claim can be re-run
rather than believed.

### R3 — the schema migration is not idempotent, and five helpers can run it · **high**

**Where:** `app/src/main/kotlin/com/przunk/protracktor/data/ProtracktorDatabase.kt` and its five
callers — `LibraryStore`, `CatalogueStore`, `SongLengthStore`, `HistoryStore`, `LibraryIndexStore`.

**Mechanism.** Each store constructs **its own** `SQLiteOpenHelper` on the same database file.
`SQLiteOpenHelper` synchronises within an instance and not between instances, so two of them opening
concurrently can both read the old version and both run `onUpgrade`. The migration statements are
plain `CREATE TABLE` / `CREATE INDEX` and fail on a second run.

**User impact.** A crash during an upgrade, on the launch that upgrades — which is exactly the
launch that matters. The window is narrow (SQLite's write lock serialises the transactions, and the
second helper usually sees the new version) but the consequence is the one thing that cannot be
fixed afterwards.

**Evidence.** The v8 migration replayed against a real SQLite engine:

```
first migration: OK
second migration: FAILS -> table library_index already exists
```

**Correction.** One helper for the whole process, shared by every store, behind double-checked
locking on a `@Volatile` field. `IF NOT EXISTS` would have silenced the symptom while leaving five
connection pools racing, which is the wrong half of the problem.

**Status:** **fixed**, with a test that keeps the reason alive: replaying the newest migration must
fail. If somebody later makes the statements idempotent and that test starts failing, the right
answer is not to delete it but to ask whether the singleton is still needed.

### R4 — a conditional whose branches are identical · **low**

**Where:** `native/engine/engine.cpp`, `Sc68Backend::render`: `return ended_ ? 0 : 0;`.

**Mechanism.** Both branches return the same value. It reads as though the zero case is handled
differently depending on `ended_`, and it is not — which is how **R1** stayed invisible while being
read.

**Status:** fixed with R1 — the loop has no such expression.

---

## Unverified risks

Real by inspection, not demonstrated to occur. Listed because the reasoning is worth keeping, not
because they are known to bite.

### R5 — ~~`Sc68Backend::sharedDataPath()` is read while it may be written~~ · **medium** — FIXED

A `static std::string` set once from JNI during start-up and read when the first sc68 file is
opened. Nothing orders the two. Before round 5 a scan could not run alongside start-up; now it can.
A torn read gives an empty replay path, and sc68 with no replay path loads files and plays silence —
the failure that cost a day on 2.2.1 and looks exactly like a broken decoder.

*Fixed:* both the setter and the reader take a mutex, and the reader takes a **copy** rather than
handing out a reference into shared storage — a reference would have moved the race one line
outwards rather than removing it.

### R6 — ~~playback counters are shared between the audio thread and the UI~~ · **medium** — FIXED

`rendered_` and `ended_` in `Sc68Backend` and `SidBackend` are written by `render` on the audio
thread and read by `positionSeconds()` / `isFinished()` from elsewhere, without atomics. On the
architectures this ships for a `size_t` load will not tear in practice, and the visible consequence
is a stale position readout rather than a crash — but it is a race by the language's rules and the
rest of the player (`pendingSeek_`, `gain_`, `finished_`) already uses atomics for exactly this.

*Fixed:* `std::atomic` on both backends' counters, at the default ordering. Relaxed would be enough
for a counter and a flag; a fence per buffer next to emulating a 68000 is not worth the cleverness,
and a comment claiming an ordering the code does not use is worse than no comment.

### R7 — two fetches of one URL share a temporary file · **low**

`RemoteFiles.fetch` writes through `<name>.part`. The read-ahead and a play of the same track can
run at once and would write the same temporary path. Both write identical bytes, so the outcome is
correct by luck rather than by design.

### R8 — eviction can delete a file that another fetch has just written · **low**

`enforceBudget` protects the file the calling fetch wrote; a concurrent fetch's file is fair game
between its write and its use. `fetch` returns the bytes it downloaded rather than re-reading the
file, so nothing breaks — the cost is a wasted download.

---

## Looked at and found sound

Recorded so a later reader knows these were examined rather than skipped.

- **Release build.** R8 keeps all 14 JNI symbols including the new `nativeBackendsFingerprint`;
  three ABIs present; 99 sc68 replay binaries packaged. The `-keepclasseswithmembernames` rule
  behaves as its comment claims.
- **`CacheBudget`.** No clock, no filesystem, deterministic ordering with a tie-break; nine tests.
- **Cache exemptions are structural.** The ASMA archive is in `filesDir`, the song lengths and the
  library index are in the database, share copies are in a different cache subdirectory. A sweep of
  the fetched-file directory cannot reach any of them by construction rather than by rule.
- **`ensureLibraryReady()`** uses a function-local static, which C++11 guarantees is initialised
  exactly once even under contention.
- **Other backends' `render`.** libopenmpt, GME, ASAP and libsidplayfp either loop until the buffer
  is full or genuinely return what was asked for. **R1 is sc68's alone.**
- **Selection state** in Browse lives in the composable and dies with it, so a stale tick cannot
  survive a rescan and add a file nobody chose.
- **String resources.** Both languages complete, no malformed format specifiers — checked by the
  test script since 2026-09-02.

---

## What is still open

| | severity | status |
| --- | --- | --- |
| R1 render contract | critical | fixed, guarded by `probe-render` |
| R2 open-error race | high | fixed, before/after kept in the tree |
| R3 migration and five helpers | high | fixed, guarded by a test |
| R4 identical branches | low | fixed with R1 |
| R5 replay path race | medium | fixed |
| R6 playback counters | medium | fixed |
| **R7 shared `.part` name** | low | **open** — both writers produce identical bytes |
| **R8 eviction during another fetch** | low | **open** — costs a re-download, breaks nothing |

R7 and R8 are left deliberately. Both are correct by accident rather than by design, which is worth
recording; neither has a consequence a user could notice, and inventing locks for them would add
more moving parts than they remove.

## Not reviewable here

- Anything about **how it feels**: gesture timing, whether a list jumps under a thumb, whether a
  scan of a real library is bearable. Every defect this project has actually shipped was
  found that way.
- **R9 (instant start)** on a network share, which is `docs/STATUS.md` C3 and needs a
  hardware.
