# Backlog

Work that is agreed but not done, ordered so each item can be picked up cold. Written 2026-09-01
after a device session; the owner's words are quoted where the requirement came from him.

A defect that exists in shipped behaviour lives in `docs/STATUS.md`, not here. This file is for work
not yet started.

**Read `AGENTS.md` (workshop root and project root) before starting any of it.** Work happens on a
branch off `develop`, one stage per commit, and nothing merges without the owner saying so.

---

## 1. ~~Persistence — the state survives a restart~~ — DONE 2026-09-01

> "po restarcie/aktualizacji aplikacji wszystkie utwory zniknęły z listy"
> "aplikacja powinna zapisywać stan ustawień po restarcie (np. loop, który ostatnio odtwarzaliśmy)"

This is **R2**, the requirement the whole project started from, and everything below is easier once
it exists. Do it first.

**What has to survive:** the track list, shuffle on/off, repeat mode, and which track was last
playing. **Not** the playback position — that is `docs/OPEN_QUESTIONS.md` Q6, still open, and
formats that cannot seek make it expensive.

**Where:** app-private storage. Room is the eventual home (it is what item 2 and the R9 index need),
but a first cut may use a plain file plus `SharedPreferences` — no new dependency, and the migration
into Room happens with the index rather than twice.

**Watch for:**
- SAF grants must be persisted too, or the saved URIs will be unreadable on the next launch.
  `MediaScanner.persistPermission` already takes them; confirm they actually survive a reboot rather
  than assuming.
- A track whose file has since disappeared must be visible and marked unavailable, not silently
  dropped (AGENTS.md §7).
- Saving on every state change will write on every poll tick. Debounce, or save on the changes that
  matter.

**Done when:** the app is force-stopped and reopened and shows the same list, the same scroll
position, the same shuffle and repeat, and the same track loaded in the dock.

## 2. ~~Browse that adds a selection, not a whole folder~~ — DONE 2026-09-01

> "chcę mieć widok browse który dodaje do playlisty pojedyncze (lub kilka zaznaczonych) utwory"

Today Browse is two buttons and a folder scan dumps everything it finds. It should show what was
found and let the user choose.

- A browsable list of the scanned folder with checkboxes, a select-all, and an explicit "add to
  playlist".
- Remembers granted folders between sessions so browsing does not begin with a file picker every
  time.
- This is also where **several playlists (R6)** becomes possible: adding needs a destination, and
  once there is a destination there is more than one. The top bar's playlist name is plain text
  today precisely because a switcher with nothing to switch to is a control that does nothing.

**Done when:** the user can open Browse, tick three files out of a folder of two hundred, add them
to a named playlist, and find them there.

## 3. ~~Undo and confirmation on removal~~ — DONE 2026-09-01

> "usuwanie z potwierdzeniem oraz opcja undo w snackbarze"

Read as two different things, because one destructive action is not like the other:

- **Removing one track → undo, no dialog.** `Message` already carries an unused `actionLabel` for
  exactly this. Restore the track at the index it came from.
- **Clearing the whole playlist → a confirmation dialog.** Undoing many rows from a snackbar that
  vanishes in four seconds is not a real escape route.

A confirmation dialog *and* an undo for a single row would mean two interactions to delete one
line. If the owner wants both, say so and it is done — this reading is an interpretation, not a
decision he made.

**Built that way on 2026-09-01**: undo on a track, a dialog on deleting a playlist.

## 4. ~~A snackbar that can be swiped away~~ — DONE 2026-09-01

> "snackbar, który można 'przesunąć'"

Material 3's `Snackbar` has no swipe dismissal. Wrap it with a horizontal drag that dismisses past a
threshold, via a custom `snackbarHost`.

## 5. Media session and audio focus

The foreground service landed on 2026-09-01; playback survives the app leaving the screen and the
notification carries transport. What is left of the original item:

> "nie gra za długo w tle"

The largest item and the one that makes the app usable rather than demonstrable.

- ~~Audio focus and becoming-noisy~~ — done 2026-09-01.
- Headphone and Bluetooth controls, lock-screen transport. Needs the `MediaSession`.
- `docs/ARCHITECTURE.md` §4 already picked the approach: Media3's `SimpleBasePlayer` over the native
  engine, so the session comes without writing an ExoPlayer renderer for a synthesiser.
- Android 14+ requires a `foregroundServiceType` and its permission; `targetSdk` is 36, so this is
  not optional.

**Done when:** a headphone button pauses playback, the lock screen shows transport, and another app
starting audio pauses us instead of playing over us.

## 6. ~~sc68 — SNDH, the format that started this~~ — BUILT 2026-09-01, UNPROVEN

It compiles and links for every ABI and the licence is verified. Nobody has heard a note: there is
no SNDH file on the build machine and no emulator. First job when the owner has one to hand.

Also outstanding from this item:

- `.sc68` container files reference external replay binaries we do not ship, so they will not play.
- Subsong selection: sc68 reports `tracks`, and the UI has nowhere to show them.

`docs/OPEN_QUESTIONS.md` Q2 put it second after libopenmpt and it has not been done.

- Vendor `sc68` through `scripts/fetch-native-deps.sh`, pinned and checksummed like libopenmpt.
- **Verify its licence against the actual `COPYING` before anything else** and record it in
  `docs/LICENSES.md`. If it turns out to be GPL-2-only it invalidates the licence decision and must
  be raised, not worked around.
- It needs a second backend behind the engine facade, which is what will show whether that facade is
  the right shape.
- `sndh.net` did not resolve from this machine on 2026-08-31 — unverified whether the domain is gone
  or the network here blocks it. Find a source as part of the work.
- Backends differ in what they can do: sc68 emulates a CPU and **cannot seek**. The capability has
  to be declared and the UI has to reflect it (`docs/ARCHITECTURE.md` §5), which means item 5's
  transport and the seek slider both need a "can this backend do it" answer.

## 7. Probe content instead of trusting extensions

> "wczytuje dobrze pliki z folderu ale opiera się o rozszerzenie tylko (na razie ok)"

`SupportedFormats` filters by extension and says in its own documentation that this is provisional.
Extensions here are unreliable, absent, or shared between unrelated formats. Probing means reading
every candidate, which is why it belongs with the persistent index from item 1 rather than with a
foreground scan.

## 8. Instant start (R9)

Nothing is cached and nothing is prepared ahead. A local module is fast because it is small.

Two things make this real, and the owner's own setup shows why: his library sits on an **SMB share**
(the `hierynomus.smbj` traces in his log are the SAF provider for it), so every read crosses a
network before it reaches us.

- A cache of fetched bytes, keyed by source identity, with the eviction budget from
  `docs/OPEN_QUESTIONS.md` Q5.
- Preparing the next track while the current one plays.

## Later, agreed but not scheduled

- Remote catalogues — Modland and HVSC (`docs/ARCHITECTURE.md` §8, endpoints already measured).
- The remaining backends: `libsidplayfp`, game-music-emu, UADE last.
- Everything in `docs/WISHLIST.md`.
