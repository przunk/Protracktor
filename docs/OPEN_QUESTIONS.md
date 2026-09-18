# Open questions

Things the requirements deliberately do not settle. Each has options and a recommendation; the
choice belongs to the owner. Nothing here gets implemented by guessing.

---

## Q1 — Navigation model (R7)

**Decided 2026-08-31.** The choice was delegated with one requirement fixed: a player bar
docked at the bottom of every screen carrying previous / play / next / shuffle / repeat-one.

### The shape

Three layers, only one of which is a real destination.

```
┌──────────────────────────────────────┐
│  Lotus 3            ▾      ⌕  Browse │  top bar: active playlist as a button
├──────────────────────────────────────┤
│                                      │
│   the active playlist                │  the only destination; what you look
│   (tracks, reorderable)              │  at almost all of the time
│                                      │
│                                      │
├──────────────────────────────────────┤
│ ▸ rebels - megablast     4-Mat       │  dock, identity row -> tap to expand
│ ────────────────────────             │  progress
│  ⤨    ⏮    ▶    ⏭    ⟳¹             │  dock, transport row
└──────────────────────────────────────┘
```

- **Playlist** is the home surface. It is the active playlist, not the whole library — shuffle and
  repeat operate inside it (R6), so it is also the thing whose contents you need to see.
- **Now Playing** expands upward from the dock, over the playlist. It is the metadata screen (R4),
  not a separate tab.
- **Browse** opens as a full-screen modal from the top bar: folders, formats, authors, the remote
  catalogues. You add or play from it and dismiss it.
- **The playlist switcher** is a bottom sheet, opened by tapping the playlist name in the top bar.

- **Settings** opens as a second full-screen modal, from a gear in the top bar. Added 2026-09-04,
  and it is the model's first real test: a new screen that needed no new idea, because "a modal you
  open and dismiss" already existed and back already meant one thing.

Back always descends one layer, which is the only rule the user has to learn.

### Why not a bottom navigation bar

It was the earlier recommendation, and the dock is what changed it. A nav bar *plus* a dock is two
stacked strips of furniture — roughly 160dp of a phone screen spent on chrome before any content.
The dock already provides the one thing a nav bar would: a fixed anchor that is present everywhere.

### Why browsing is a modal rather than a tab

R7 says browsing must not be something you slide onto by accident. A full-screen modal you open and
dismiss cannot be entered by accident and cannot be left by accident either. It also gets the whole
screen, which the library tree wants and a tab sharing space with a nav bar does not.

### Dock details

- **Repeat is one control cycling three states**: off → repeat playlist → repeat one. Shuffle is a
  separate toggle, with the real backward history R5 requires.
- **State is never carried by colour alone** (AGENTS.md §8). Repeat-one uses a different icon from
  repeat-all, not the same icon tinted, and the content description states the mode in words.
- **With nothing loaded the dock stays, but is visibly inert**: transport disabled, identity row
  reading "Nothing playing — Browse" and acting as the button that opens Browse. A permanently dead
  strip teaches the user something untrue (AGENTS.md §7); this turns it into the obvious next step.
- **Tap targets**: five controls on a phone is tight but standard. Play is the larger filled centre;
  the rest are icon buttons at the 48dp minimum.

### Wide screens

The dock stays full width at the bottom. Browse becomes a side pane instead of a full-screen modal,
and Now Playing sits beside the playlist rather than over it. Same three layers, laid out rather
than stacked.

### Standing objection

This is the agent's proposal, made on delegated authority. It is recorded as decided so work can
proceed; the owner overrules it by saying so, and this section gets rewritten rather than argued
with.

## Q2 — Which formats ship in version 1

Every backend is a separate native build with its own quirks. Doing all of them before anything
plays is how a project like this dies.

- **(a) `libopenmpt` + `sc68` first** — trackers plus SNDH. SNDH is the reason the project exists,
  and `libopenmpt` is the easiest possible native build, so it de-risks the toolchain cheaply.
- **(b) Everything at once.**

**Decided 2026-08-31: (a).** Then `libsidplayfp`, then game-music-emu, then UADE last — UADE is
the hardest (it emulates a whole 68k machine and ships original replayer binaries whose licensing
needs a careful look).

## Q3 — How the library gets its files

- **(a) SAF folder grant.** The user picks folders once; we keep persistable URI permissions.
  Works with SD cards and any provider. Slower to enumerate.
- **(b) `MediaStore`.** Fast, but Android's media scanner ignores `.sndh`, `.sid`, `.mod` and
  friends, so most of the collection would be invisible.
- **(c) App-private storage with an import step.** Fastest and fully under our control, but the
  user's collection is duplicated.

**Decided 2026-08-31: (a).** (b) is disqualified by what MediaStore indexes.

Remote catalogues were added alongside this, not instead of it — see `docs/ARCHITECTURE.md` §8.
SAF is still what reaches a collection already on disk.

## Q4 — Track duration where the format has none

Most of these formats have no intrinsic length. Options, combinable:

- ~~**Songlength databases**~~ — HVSC's is in, 2026-09-02 (`docs/ARCHITECTURE.md` §14). Equivalents
  for other platforms remain open. Accurate,
  but means shipping or downloading a database.
- **Silence detection** — render ahead and stop after N seconds of silence. Works everywhere,
  wrong on tracks with deliberate quiet passages.
- **Loop detection** — some backends report a loop point.
- **Fixed default + fade** — 3 minutes, say. Always works, often wrong.

**Decided 2026-08-31.** The app is both: it may use the network, and it must work offline with
whatever is cached. A downloaded songlength database is therefore acceptable, and HVSC's
`Songlengths.md5` was confirmed to be directly fetchable (5.2 MB), so SID durations are exact
rather than guessed.

Still open for the formats with no such database — silence detection, loop points, or a fixed
default with a fade. To be settled once something plays and the options can be compared by ear.

## Q5 — ~~Cache budget~~ — ANSWERED 2026-09-03: 512 MB, least recently used first

**512 MB with LRU eviction**, enforced after every successful cache write and once at start-up so an
installation that grew past the limit converges on it rather than staying over. What counts and what
is exempt is in `docs/ARCHITECTURE.md` §19; making the number a setting is `docs/BACKLOG.md` A13.

The original question and its options are kept below, because the reasoning is still what would be
revisited if the number turns out to be wrong.

### Original entry

The extracted-file cache (ARCHITECTURE §6) has no size limit yet. Fixed ceiling, percentage of free
space, or user setting? Recommendation: user setting with a 512 MB default, evicted least-recently-
played first.

## Q6 — What "resume" means for a format that cannot seek (R2)

For SID, SNDH and UADE formats, restoring playback position means silently re-rendering from the
start at speed, which takes real time for a position deep into a track.

- **(a) Restore the track and the position, rendering silently to get there.** Honest to R2, but a
  three-minute-deep resume costs a visible wait.
- **(b) Restore the track, start from the beginning.**
- **(c) (a) with a cap** — restore position if it is under some threshold, otherwise start over.

Recommendation: **(c)**. Threshold to be measured once something plays.

## Q9 — Should the decoders run in their own process?

*Raised 2026-09-08 by a C++ engineer shown the project, whose first reaction to JNI
was "fuuj". The reaction is about the API; the argument underneath it is about architecture, and it
is a real one: **two programs talking over shared memory or a socket, rather than one program with a
foreign-function bridge inside it.***

### The argument is right where it comes from

For a desktop or server program it is usually the better default. Two processes have independent
crash domains, a contract that is data rather than function signatures, independent release cadence,
and each half can be written in the language that fits. An FFI bridge buys speed and pays with a
shared address space, which means a bug anywhere is a bug everywhere.

### Where Android breaks the analogy

**The things that must happen are Java-only APIs.** A foreground service, audio focus, the media
notification, `MediaSession` — none has a native equivalent, and Android grants the "this process is
doing something the user asked for" exemption to a declared component, not to a process that forked
itself. So a pure-native engine process would either be killed at the system's convenience or need a
Java process to speak for it — which is the JNI call, moved rather than removed.

**And the audio has to land somewhere.** Two shapes:

- **The engine renders and sends PCM to the app**, which plays it. That crosses a boundary every few
  milliseconds on the one thread that must never be late, which is exactly what `ARCHITECTURE` §4
  rejected for JNI — and an IPC round trip is a context switch where a JNI call is a function call
  with bookkeeping. Doing it properly means a shared-memory ring buffer and a futex, which is to say
  writing an audio server. Android already has one.
- **The engine owns the audio device** and the app is a remote control. This is clean, the boundary
  carries control only — and now the *engine* needs the foreground service, the audio focus and the
  notification, so it needs JNI. Same conclusion.

**Measured 2026-09-08, because the objection deserves numbers rather than a position.** The audio
callback class in `engine.cpp` (lines 1497–1731) contains **zero** references to `JNIEnv`, `JavaVM`,
`AttachCurrentThread` or any call back into Java. PCM is produced and consumed entirely in C++; the
boundary is fifteen functions carrying "open this", "play", "seek", "what is the title". That is the
shape that makes an FFI bridge unobjectionable, and it is why the same engine ports to a browser by
replacing one class and one `extern "C"` block.

### The part of the objection that survives all of that, and it is the strong part

**Crash isolation.** Seven third-party decoders parse hostile binary formats in the app's own address
space. This is not hypothetical here: `HivelyBackend` allocates `kLoaderSlack` — **a mebibyte of
zeros past the end of every file** — because upstream's loader never bounds-checks against the file
length. The slack is derived arithmetic rather than a guess, and it is still a mitigation for a
decoder that reads past what it was given. A malformed file that gets past it takes the whole app
down, including the playlist the user was editing.

### The version that takes the objection seriously

Not a native process talking over a socket, but the engine and its service in **their own Android
process** (`android:process=":engine"`), reached over Binder:

- a decoder that segfaults kills that process; the UI survives, says which file did it, and skips;
- the engine process is a declared service, so the framework treats it as one;
- audio never crosses a boundary — that process owns Oboe;
- JNI stays, quarantined inside the process it belongs to.

**The cost is a real refactor**, larger than the engine split the web port needs: `PlaybackController`
holds `NativeEngine` directly today, so the split needs an AIDL interface and the controller becomes
a client of it. It is not a manifest attribute.

### Two things worth knowing before deciding

1. **The web version gets this for free.** A wasm module cannot corrupt the page; a bad file traps
   inside the sandbox. Whatever is decided here, the browser build already has what the objection
   asks for.
2. **It is orthogonal to the web work** and should not be bundled with it. The web port narrows the
   JNI surface; this changes where the surface lives.

**Not answered.** The honest summary is that the objection is right about the API, wrong about the
alternative being available on Android, and right again about crash isolation — which is a live
question this project has not asked itself and should.

## Q7 — Store distribution

The repository is private for now, with a possible public release later. If Protracktor ever goes
to a store, GPL-3 means sources must be published at that point. F-Droid or Google Play — or
neither, and just an APK. No decision needed yet; noted so it is not a surprise.

## Q8 — ~~Should a playlist edit need saving at all?~~ — ANSWERED 2026-09-06

*Raised 2026-09-04: leaving the app without noticing the list was never saved.*

Editing the active playlist builds a **draft**: reordering and removal change `queue` and set
`dirty`, and nothing reaches the database until Save. Discard throws the draft away. Adding to a
*different* playlist writes immediately, which is already the opposite rule in the same app.

**What was reported is not "the marker is too subtle".** It is that the marker exists at
all: a person who reorders a list and walks away has, in their own mind, reordered the list. Every
music player they have ever used behaves that way. The draft is asking them to remember a step whose
purpose is invisible from where they stand.

Three ways out, and the middle one was the suggestion:

- **(a) Keep the draft, mark it harder.** Cheapest, and it treats the symptom. If the current
  marker is missed, a louder one is a bet that the next one will not be.
- **(b) Save on every change, and confirm only what cannot be undone.** A reorder writes
  immediately; a removal asks first, or offers undo the way it already does for a single track. This
  is a **change of requirements**, not a bug fix — the draft was a deliberate decision — and it
  deletes a whole class of "did I save it?" from the app.
- **(c) Save on leaving the screen**, silently. Removes the question without changing what the
  buttons mean, but "when did that happen" becomes the new invisible step.

**Answered 2026-09-06, and with a fourth option none of the three above had:** adding to a playlist
from *outside* it — a search, a folder — saves itself, and Save is reserved for removing and
reordering.

**Split by the kind of edit, not by when it happens.** An add arrives from *outside* the list — a
search result, a folder, another playlist — and nothing about it is provisional: you asked for a
tune to be in the list and it is. Removal and reordering happen *inside* the list, where a wrong
drag or a mistaken removal is a real risk, and that is where Save and Discard earn their place.

This is better than (b), which was the leaning before: it removes the "did I save it?" question from
the case that raised it while keeping the safety net exactly where the danger is. It also removes
the inconsistency `docs/ARCHITECTURE.md` §17 records, from the other end — adding to *any* playlist
now writes immediately, whether it is the active one or not.

**One case bends the rule, and it bends towards keeping your tracks.** Writing the list to disk
writes all of it, so an auto-saved add made while a removal is pending would quietly commit the
removal too. When an edit is already unsaved the add joins it and one Save covers both.

**Related:** `docs/ARCHITECTURE.md` §17 records why adding to another playlist writes immediately
while the active one drafts — the reasoning is sound and it is also exactly the inconsistency a user
cannot see.

---

## Q10 — How a release is built, and from which branch — **ANSWERED 2026-09-11**

**Answered: from `develop` until version 1, from `master` afterwards.** Releases are built
from `develop` until version 1 ships; from then on, from `master`. The two defects found in the script
while answering — no refusal on a dirty tree, and a stale message about bumping a versionCode that
is counted, not typed — stand on their own and are worth fixing either way.

### Original entry

*Raised 2026-09-10: does `scripts/build-bundle.sh` build from `master` or from the current branch?
Recorded as a decision; nothing changes until it is made.*

### What the script does today

It builds **whatever is checked out**. There is no `git checkout` in it; the versionCode is
`git rev-list --count HEAD`, the same expression `app/build.gradle.kts` uses. Standing on `develop`
it builds `develop`.

### Why the branch matters more than it looks

The two branches count differently. `master` carries three merge commits `develop` does not have,
so the same content numbers higher there. Measured 2026-09-10:

| | versionCode |
|---|---|
| `develop` as it stands | 531 |
| `master` after merging `develop` in | 535 |

Play rejects any upload not higher than the last. **Upload 535 from `master` once and a later build
from `develop` at, say, 533 is refused** — so releasing from both is not an option, only choosing.

### Two defects in the script, found while answering

1. **No check for uncommitted changes.** Anything dirty in the tree goes into the bundle, under a
   versionCode that names a commit which does not contain it — a Play build no state in the history
   describes. Recommendation: refuse to build on a dirty tree.
2. **A stale message.** When a bundle for the same code already exists it says "bump it in
   `app/build.gradle.kts` first". Nothing has been typed there for a long time — the code is counted.
   And the check looks only at a file in `dist/`, not at what Play has actually received.

### Options

- **A. Release from `master`**, always: merge `develop` into it, check it out, build. Matches the
  history (`Merge develop into master — release 0.2.0`) and keeps `master` meaning "what shipped".
- **B. Release from `develop`**, and let `master` lag or retire it. Fewer steps; `master` stops
  meaning anything.
- **C. Let the script do A itself** — refuse unless on `master`, clean, and level with `develop`.

**Recommendation: C**, which is A made impossible to get wrong, plus the dirty-tree refusal either
way. It is the one step in this project that cannot be undone once it has happened.
