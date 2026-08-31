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

- **Songlength databases** — HVSC `Songlengths.md5` for SID, and equivalents elsewhere. Accurate,
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

## Q5 — Cache budget

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
