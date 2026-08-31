# Open questions

Things the requirements deliberately do not settle. Each has options and a recommendation; the
choice belongs to the owner. Nothing here gets implemented by guessing.

---

## Q1 — Navigation model (R7)

The requirement says browsing must not be a page you slide onto by accident. It does not say what
replaces it.

- **(a) Bottom navigation bar, 3 destinations** — Now Playing / Playlists / Library. The docked
  player bar sits above it. Conventional, one tap to anywhere, costs a strip of vertical space.
- **(b) Single screen + browser as a modal sheet.** Now Playing is the app; browsing slides up over
  it and is dismissed. Maximum room for metadata, and browsing is unmistakably deliberate.
- **(c) Navigation rail on wide screens, bottom bar on narrow.** Best on a tablet, most work.

Recommendation: **(a)**, with (c) added later when tablet layout is tackled. It is the pattern
Material 3 is designed around and it is the least surprising.

## Q2 — Which formats ship in version 1

Every backend is a separate native build with its own quirks. Doing all of them before anything
plays is how a project like this dies.

- **(a) `libopenmpt` + `sc68` first** — trackers plus SNDH. SNDH is the reason the project exists,
  and `libopenmpt` is the easiest possible native build, so it de-risks the toolchain cheaply.
- **(b) Everything at once.**

Recommendation: **(a)**. Then `libsidplayfp`, then game-music-emu, then UADE last — UADE is the
hardest (it emulates a whole 68k machine and ships original replayer binaries whose licensing needs
a careful look).

## Q3 — How the library gets its files

- **(a) SAF folder grant.** The user picks folders once; we keep persistable URI permissions.
  Works with SD cards and any provider. Slower to enumerate.
- **(b) `MediaStore`.** Fast, but Android's media scanner ignores `.sndh`, `.sid`, `.mod` and
  friends, so most of the collection would be invisible.
- **(c) App-private storage with an import step.** Fastest and fully under our control, but the
  user's collection is duplicated.

Recommendation: **(a)**. (b) is disqualified by what MediaStore indexes.

## Q4 — Track duration where the format has none

Most of these formats have no intrinsic length. Options, combinable:

- **Songlength databases** — HVSC `Songlengths.md5` for SID, and equivalents elsewhere. Accurate,
  but means shipping or downloading a database.
- **Silence detection** — render ahead and stop after N seconds of silence. Works everywhere,
  wrong on tracks with deliberate quiet passages.
- **Loop detection** — some backends report a loop point.
- **Fixed default + fade** — 3 minutes, say. Always works, often wrong.

Question for the owner: is a downloaded songlength database acceptable, or should the app work
entirely offline?

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
