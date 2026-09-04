# Code review — round 6, 2026-09-04

A review of the whole `develop` tree after round 6's five items, as `GOAL.md` Phase 2 asks for.

## How to read this

The same two rules as round 5, because the review is done by whoever wrote the code:

1. **`confirmed` requires evidence that can fail** — a failing test, a deterministic diagnostic, a
   measurement. Reasoning, however good, produces an *unverified risk*.
2. **Findings in code written earlier in this same run get more suspicion, not less.**

That second rule earned its place again. **Four of the six findings below are in code written
earlier today**, and one of them is a race in the same class as round 5's R6 — introduced while
fixing a defect, three lines from the comment explaining why its neighbours are atomic.

Nothing here is confirmed by running the app on a device. That remains true of the whole project.

---

## Confirmed defects

### R1 — `current_` is written on the audio thread and read on the control thread · **serious**

**Where:** `native/engine/engine.cpp`, `Sc68Backend`. Written today, fixing `docs/STATUS.md` C13.

**Mechanism.** C13's fix has `selectSubsong` remember its choice in a plain `int current_`, and
`rewind()` read it. But `selectSubsong` is called from **`Player::render`** — the audio callback —
at engine.cpp:935, where a pending subsong is applied. `rewind()` is called from `restart()` on the
control thread. So a plain `int` is written by one thread and read by another.

**Why it looked fine.** `restart()` calls `stop()` before `rewind()`, and closing an Oboe stream
does synchronise with its callback, so in practice the write happens-before the read. That is
exactly the argument round 5 rejected for `rendered_`: *"a race by the language's rules however
benign it looks on ARM"* (`docs/review.md` R6). The two atomics that make that argument are
declared eleven lines below the new field.

**Evidence.** Structural and specific: engine.cpp:935 is inside `onAudioReady`'s path and calls
`backend_->selectSubsong`, and `Sc68Backend::selectSubsong` assigns `current_` unconditionally.

### R2 — the same `selectSubsong` writes a whole struct across threads · **serious**

**Where:** `native/engine/engine.cpp`, `Sc68Backend::selectSubsong` → `info_`. **Pre-existing**, not
from today, and larger than R1.

**Mechanism.** `selectSubsong` also calls `sc68_music_info(sc68_, &info_, ...)`, filling a
`sc68_music_info_t` — from the audio thread. `describe()`, `durationSeconds()` and `subsongCount()`
read that struct from the control thread whenever the UI asks what is playing, with no stop in
between. Unlike `restart()`, nothing synchronises those two.

**What it looks like when it bites:** a duration or a subsong count read while a subsong switch is
in flight — a torn field rather than a crash, so it would be seen as "the time was wrong for a
moment" and never reported.

**Evidence.** Structural. Note this is a *pre-existing* hazard that R1 walked into rather than
created; finding R1 is what made it visible.

### R3 — a doc comment describes work the function does not do · **moderate**

**Where:** `PlaybackController.deleteCatalogueIndex`. Written today.

Its documentation says *"Stops playback of anything from that catalogue first, because the queue can
hold tracks whose only description lives in the rows being deleted."* The function does no such
thing: it clears the index, drops the catalogue from the search scope and closes it if open.

Worse, the stated reason is **false**. A queued catalogue track carries its own id, and the URL to
fetch it is derived from that id by `Catalogue.urlFor` — not from `catalogue_tracks`. Playback is
unaffected by deleting an index. What actually degrades is the format label
(`catalogueFormatOf` returns null) and "more from this author".

A comment that promises a safety measure nobody implemented is worse than no comment: the next
person reads it and trusts it.

### R4 — deleting an archive leaves its catalogue looking healthy and playing nothing · **moderate**

**Where:** `StorageSection` + `PlaybackController.deleteArchive`. Written today.

For an archive catalogue — ASMA — the downloaded zip **is** the index: `indexCatalogue` fetches one
file, stores it as the archive, and parses the same bytes into rows. The storage section offers
those two as separate deletes.

Delete only the archive and the catalogue still reports its full track count, still browses, still
searches — and every track fails to open, because the bytes are gone. The confirmation text warns
that nothing will play, which is honest, but the row afterwards looks indexed and gives the user no
reason to press download again.

They are one thing and should be deleted as one thing.

### R5 — two messages race for one snackbar after the dock's plus · **minor**

**Where:** `PlaybackController.keepTransient`. Introduced today by the change that made adding speak.

`keepTransient` calls `addToPlaylist`, which now sets a message through `describeAdded`, and then
sets its own more specific one. `appendTracks` is synchronous, so the specific message wins and the
user sees the right thing — today, by ordering rather than by design. `_state` is a conflated flow;
a collector timed between the two updates could show the generic notice.

The comment above it still explains that it speaks *because* `describeAdded` stays silent on
success. That has not been true since this morning.

---

## Considered and deliberately not changed

- **`SupportedFormats.fingerprint` uses `String.hashCode`.** Stable by specification across JVMs and
  platforms, which is the only property required — it must differ when the list differs, not resist
  an adversary. A cryptographic digest here would be cost without a question it answers.
- **The storage section lives on the Online catalogues screen.** A stated placeholder, recorded in
  `docs/ARCHITECTURE.md` §19 and `docs/BACKLOG.md` A13, not an oversight.
- **`probe-uade.py` and `probe-extensions.py` share plumbing through `importlib`.** Ugly, and the
  alternative is a copy of the Modland index and download code that goes stale. The ugliness is
  visible; the stale copy would not be.
- **UADE is not integrated.** That is item 1's measured recommendation, not an unfinished item.

## Not found

Deliberately looked for and absent:

- Anything in the storage section that deletes something not re-fetchable. Every row is a copy, and
  `summaries()` is driven by `Catalogue.all` rather than the table — checked, because a delete that
  removes the way back would be the worst thing this round could ship.
- Any new use of a process-wide mutable global, the class of defect round 5 spent three fixes on.
- Any string added without its Polish counterpart, or a format specifier with the flag before the
  argument index. `./scripts/test-protracktor.sh` checks both and passes.
