# Open questions

Things the requirements deliberately do not settle. Each has options and a recommendation; the
choice belongs to the owner. Nothing here gets implemented by guessing.

---

## Q1 — Navigation model (R7)

**Decided 2026-08-31.** The owner delegated the choice and fixed one requirement: a player bar
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
SAF is still what reaches the owner's existing collection on disk.

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

## Q7 — Store distribution

The repository is private for now, with a possible public release later. If Protracktor ever goes
to a store, GPL-3 means sources must be published at that point. F-Droid or Google Play — or
neither, and just an APK. No decision needed yet; noted so it is not a surprise.

## Q8 — ~~Should a playlist edit need saving at all?~~ — ANSWERED 2026-09-06

*Raised by the owner, 2026-09-04: he leaves the app without noticing the list was never saved.*

Editing the active playlist builds a **draft**: reordering and removal change `queue` and set
`dirty`, and nothing reaches the database until Save. Discard throws the draft away. Adding to a
*different* playlist writes immediately, which is already the opposite rule in the same app.

**What he actually reported is not "the marker is too subtle".** It is that the marker exists at
all: a person who reorders a list and walks away has, in their own mind, reordered the list. Every
music player they have ever used behaves that way. The draft is asking them to remember a step whose
purpose is invisible from where they stand.

Three ways out, and the middle one is his suggestion:

- **(a) Keep the draft, mark it harder.** Cheapest, and it treats the symptom. If the current
  marker is missed, a louder one is a bet that the next one will not be.
- **(b) Save on every change, and confirm only what cannot be undone.** A reorder writes
  immediately; a removal asks first, or offers undo the way it already does for a single track. This
  is a **change of requirements**, not a bug fix — the draft was a deliberate decision — and it
  deletes a whole class of "did I save it?" from the app.
- **(c) Save on leaving the screen**, silently. Removes the question without changing what the
  buttons mean, but "when did that happen" becomes the new invisible step.

**Answered 2026-09-06, and with a fourth option none of the three above had.** The owner:

> dodawanie do playlisty "spoza playlisty" (np. w wyszukiwaniu) powinno dodawać do niej bez
> konieczności zapisywania (zrobić autosave). Save rezerwujemy na operacje typu remove/reorder

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
