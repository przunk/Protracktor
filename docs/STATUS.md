# Status

Updated: 2026-09-03 — version 0.2.0, versionCode 2, schema version 8

## What works

A usable player, as far as anything can be called that without a device saying so.

- **Formats**: tracker modules through libopenmpt (MOD, XM, S3M, IT and dozens more), Atari ST
  through sc68 3.0.0b (SNDH, YM, `.sc68`), Atari 8-bit through ASAP (SAP and 13 tracker formats), Commodore 64
  through libsidplayfp (PSID, RSID) and seven console families through game-music-emu (NSF, GBS,
  SPC, VGM, HES, AY, KSS). Backends sit behind one interface and are asked what they can do rather
  than assumed — libopenmpt seeks, sc68 and libsidplayfp cannot, and the UI reflects that.
- **Online archives**: Modland, browsed offline from a downloaded index and fetched per track; ASMA,
  which arrives as one 20 MB archive and then needs no network at all; The Mod Archive, searched
  live. HVSC's song lengths give SID tunes the duration the format cannot carry. Fetched music is
  capped at 512 MB, least recently used first (`docs/ARCHITECTURE.md` §19).
- **Library**: folders granted through the storage access framework, remembered between sessions.
  A folder is **scanned by opening every file with a real decoder**, not by reading its name, and
  the result is stored so later launches read an index instead of walking the tree
  (`docs/ARCHITECTURE.md` §18).
- **Playlists**: several, named, created, renamed, deleted, switched from the top bar. Shuffle and
  repeat work inside the active one.
- **Transport**: a dock on every screen with shuffle, previous, play, next and repeat. Shuffle keeps
  a real history so backward returns to what was actually played; without shuffle, backward is the
  row above. Seeking where the backend allows it.
- **Survives a restart**: playlist, settings and the track last played all come back.
- **Survives leaving the app**: a foreground service with transport in its notification. Audio focus
  and becoming-noisy are honoured.
- **Both languages**, Polish and English, from the first screen.

Verified here: **87 unit tests**, genuinely executed rather than served from the build cache —
`./scripts/test-protracktor.sh --really`, which exists because a "final verification" on 2026-09-03
turned out to have come out of the cache in one second. A release build through R8 keeps all 13 JNI
symbols, three ABIs and 99 sc68 replay binaries. Every backend was also run on the host against real
files before it was integrated, which is where the measured coverage in this file comes from.

Verified by the owner on a device: modules play (2026-08-31), SAP plays (2026-09-01), reordering a
playlist works (2026-09-02), and on the evening of 2026-09-02 he tested all five of round 3 —
**Random rolling on, history, "more from this author" and both shares all work**. Read-ahead was
working and fetching serially, which he heard; that is fixed.

**Not verified by anyone on a device**, and this is the list that matters most:

- **Every Atari ST file.** sc68 3.0.0b replaced the 2003 release on 2026-09-03 and nobody has heard
  one on a phone. A review the same day found that every such track would have stopped on the first
  audio callback (`docs/review.md` R1); the fix is guarded by a probe and by nothing else.
- **SID, the console formats, ASMA, HVSC song lengths** — built and measured on the host, never
  played on the device.
- **Scanning a real library.** What it costs on the owner's SMB share is unmeasured, and that number
  decides whether the feature is usable at all.
- **The 512 MB cache ceiling**, which nobody has yet had enough cached music to reach.
- Everything added on 2026-09-03: the local index, the cache budget, Browse remembering its place,
  and the C6/C7 fixes.

Confirmed on a device: modules play (2026-08-31), SAP plays (2026-09-01), and round 3's five
features — Random rolling on, history, "more from this author" and both shares (2026-09-02).

## Finished

- **2026-09-03** — **C6 and C7.** Entering a Browse domain now forgets where you had got to, so
  choosing Online catalogues shows catalogues rather than an emptied folder; and adding one track
  from a row's menu stays in Browse and says what it did. The two rules about position are
  deliberate opposites: descend and return keeps your place, leave and re-enter does not.
- **2026-09-03** — **Browse remembers where you were.** Each level keeps its own scroll position for
  the life of a Browse session, and coming back puts the row you descended by on screen rather than
  an offset that may no longer mean anything. Closes **A20**.
- **2026-09-03** — **The fetched-file cache has a ceiling**: 512 MB, least recently used first,
  enforced after every write and at start-up (`docs/ARCHITECTURE.md` §19). Answers
  `docs/OPEN_QUESTIONS.md` Q5, which had left the cache growing without limit. Unfinished downloads
  and files in use are exempt from eviction; permanent downloads are in other directories and are
  not counted at all. The online screen now says what is held.
- **2026-09-03** — **The local library is scanned by opening files, not by reading their names**
  (`docs/ARCHITECTURE.md` §18). A scan hands every file to the same decoder path playback uses and
  stores the result, so re-entering a folder reads an index instead of walking the tree again. Rows
  record which decoder set produced them and a folder scanned by a different one is reported stale —
  which mattered the same day, since sc68 3.0.0b made a great many previous verdicts wrong. Closes
  **C4** and **A6**.
- **2026-09-03** — **sc68 3.0.0b**, replacing the 2003 release. Measured on one corpus, both
  libraries, same bytes: **`.sndh` 14/30 → 30/30**, `.sc68` 10/10 → 10/10, and SNDH files now carry
  durations at all because 3.x ships a database of known tunes. Closes **C1** and confirms **C2**.
  It was a rewrite rather than a version bump — `api68_*` no longer exists — and the licence moved
  from GPL-2-or-later to GPL-3-or-later.
- **2026-09-02** — **How a list behaves** (`docs/ARCHITECTURE.md` §17). Tapping a track plays it;
  a long press starts selecting; the always-visible checkbox and play button are gone; every row
  has a three-dot menu with add, information, both shares and "more from this author". Back leaves
  a selection before it leaves a level. The dock's **+** now appears for anything playing outside
  the playlist, not only for Random.
- **2026-09-02** — Read-ahead fetches its tracks **in parallel**; it was written serially and the
  owner heard the difference on a device. The Random icon is a die rather than a solid square, and
  the two share actions no longer share an icon.
- **2026-09-02** — **Sharing**: the file, from anywhere, through a `FileProvider` copy; and a link,
  for catalogue tracks, which is the file's own URL where the catalogue publishes one and the
  collection plus the path inside it where it does not.
- **2026-09-02** — **"More from this author"**, from the expanded player and from a track's menu:
  opens Browse at the author's folder in the catalogue the tune came from. **Catalogue tracks only** —
  a local file's neighbours would be its directory, and the local browser lists a whole granted tree
  flat rather than directory by directory, so there is no folder to open.
- **2026-09-02** — **History.** A fifth domain in Browse listing what has been played, most recent
  first, playable and addable exactly like any other list. One row per track rather than one per
  play, 500 of them, forgetting the oldest.
- **2026-09-02** — **Random plays on, and reads ahead.** A tune ending goes to the next random pick
  instead of stopping, and Random now decides two or three picks in advance so the read-ahead has
  something to fetch — it never could before, because Random picked at the moment you pressed it.
  Repeat-one still means what it says. The dice and next now differ: next walks forward into the
  queue, the dice re-rolls.
- **2026-09-02** — **SID durations**, from HVSC's song length database: 61,157 tunes, downloaded on
  request from the online screen and looked up by the MD5 of the file. SID position now counts the
  frames actually played, because libsidplayfp has no notion of a position to ask for and a
  duration with nothing counting towards it would have been half a feature. Seeking is still not
  possible in a SID — the only way to a position is to run the machine there.
- **2026-09-02** — **ASMA**, the Atari SAP Music Archive: 6,335 files, downloaded once as a 20 MB
  archive and then browsable and playable with no network at all. A second kind of catalogue
  (`docs/ARCHITECTURE.md` §13).

- **2026-09-02** — **Commodore 64.** libsidplayfp 3.1.1 with the SIDLite emulation, building for
  every ABI. Thirty random Modland SIDs played on the host with **no Commodore ROMs supplied at
  all**, none of them BASIC-compatible — which shrinks the ROM question rather than answering it.
  SID files have no intrinsic length, so durations came from HVSC the same day (below).

- **2026-09-02** — The follow-track button is a small icon rather than a labelled one, and adding
  tracks scrolls to them.

- **2026-09-02** — Haptics on the three gestures the screen does not already answer: a row picked up,
  put down, and each position it crosses; plus the snackbar's dismiss threshold.

- **2026-09-02** — Finding the playing track: a "Show in playlist" action in the expanded player, and
  a follow toggle over the list that is off by default, hides itself while following, and switches
  off the moment the user scrolls by hand.

- **2026-09-02** — A track can be played from Browse and from search results without adding it: the
  results become the queue while you are looking at them, and the playlist is untouched. Sources are
  named as paths everywhere, so a result says where it came from and Information shows
  `Modland/Format/Author`.

- **2026-09-01** — Adding tracks no longer raises a snackbar over the rows it is reporting, and
  titles and authors are filled in by a background pass instead of waiting for each track to be
  played.

- **2026-09-01** — The playlist row rebuilt to the owner's sketch: ordinal or a play triangle, title
  with author and format, an overflow menu holding information and delete, and a drag handle that
  reorders. Reordering is an edit, so it waits for Save like the others.

- **2026-09-01** — **The consoles.** game-music-emu 0.6.5: NES (NSF/NSFE), SNES (SPC), Game Boy
  (GBS), Sega (VGM/VGZ/GYM), PC Engine (HES), ZX Spectrum (AY), MSX (KSS). Verified on the host
  before integration — NSF 4/4, SPC 4/4, GBS 3/3, VGM 3/3.

- **2026-09-01** — **Atari 8-bit.** ASAP 8.0.0 vendored and building for every ABI: SAP plus Chaos
  Music Composer, Raster Music Tracker, Theta Music Composer, Delta Music Composer, Music ProTracker
  and their double-play variants. Twelve of twelve random Modland SAP files played on the host
  probe, and **confirmed on the owner's device on 2026-09-01**.
  It seeks, which the Atari ST backend cannot.

- **2026-09-01** — Search scope is explicit and two-level (library / online / which catalogues);
  local results show where the file lives. Tracks take their real name from the tune's metadata
  once played, keeping the filename. Random is a mode: the playlist goes behind glass, next and
  previous walk the random history, and keeping a track no longer leaves it.

- **2026-09-01** — Ducking: a notification lowers the music instead of stopping it, a call stops it
  and does not bring it back. Duplicate tracks are refused by file identity rather than by URI.
  Random plays without adding, with a control to keep what you hear.

- **2026-09-01** — Media session: lock-screen transport, Bluetooth and headphone buttons, with the
  notification bound to it through `Notification.MediaStyle`. Seeking is advertised only for
  backends that can honour it.

- **2026-09-01** — Read-ahead: the next track's bytes are loaded while the current one plays, and
  the dock says "Loading…" when a read is actually happening. R9's wait is a read, not a decode.

- **2026-09-01** — Browse is a full screen with four domains: local filesystem, online catalogues,
  random, and search across both. Modland is indexed into the database (schema version 2) and
  browsable offline by format and author; remote tracks stream and are cached. **None of it has run
  on a device.**

- **2026-09-01** — **R1: Atari ST.** sc68 2.2.1 vendored and building for every ABI, behind a backend
  interface alongside libopenmpt. SNDH files are 68000 machine code, so this is a whole emulated
  Atari. **Not confirmed playing** — there is no SNDH file on this machine and no emulator; the
  owner has to try one.

- **2026-09-01** — Audio focus and becoming-noisy: another app or a phone call takes the speaker and
  we pause; pulling headphones out pauses instead of playing to the room.

- **2026-09-01** — Removing a track offers undo instead of asking first; deleting a playlist asks.
  Snackbars can be pushed off the screen sideways.

- **2026-09-01** — **R6 met.** Several named playlists, created, renamed, deleted and switched from
  the top bar. Browse remembers granted folders and adds a *selection* rather than a whole folder.

- **2026-09-01** — Playback survives the app leaving the screen: `PlaybackController` is now a
  process-wide singleton and `PlaybackService` keeps it in the foreground with a notification
  carrying previous, play/pause, next and stop.

- **2026-09-01** — **R2 met.** Playlist, shuffle, repeat and the track last played survive a restart,
  in a hand-written SQLite database (`docs/ARCHITECTURE.md` §9 explains why not Room). Granted SAF
  folders are remembered too. Six tests run the schema against a real SQLite engine on the JVM.

- **2026-08-31** — The Compose UI: dock, playlist, Now Playing, Browse, theme with dynamic colour,
  Polish and English strings. `PlayerViewModel` ties `PlayQueue` to the native engine. The engine
  gained a finished flag and a restart, polled rather than pushed — signalling from the audio
  callback would mean attaching a JNI environment on the thread that must never be late.

- **2026-08-31** — `PlayQueue`: play order, history and repeat/shuffle semantics as pure Kotlin with
  no Android in sight. 16 unit tests, all four deliberate mutations of the logic killed by them.
  `scripts/test-protracktor.sh` runs them quietly.

- **2026-08-31** — Repository created. GPL-3.0-or-later, remote `https://github.com/przunk/protracktor`
  (private for now).
- **2026-08-31** — Build environment scripts: `use-tooling.sh`, `check-tooling.sh`,
  `build-debug.sh`. NDK 29.0.14206865 and CMake 3.31.6 added to the shared
  `/mnt/workspace/.tooling/android-sdk` with the owner's approval; nothing already there was
  modified.
- **2026-08-31** — `push-protracktor.sh` and `internal/git-askpass.sh`, adapted from the Kratkoza
  equivalents with the owner's permission to read them.
- **2026-08-31** — Gradle project: `app` module, Compose, Material 3, minSdk 29 / targetSdk 36,
  Gradle wrapper 9.6.0, build directory off the 9p mount. Debug APK builds, was inspected, and runs
  on the owner's device.
- **2026-08-31** — Decisions taken: formats to do first (Q2), how files reach the library (Q3),
  duration policy (Q4), and the two-kinds-of-source model with remote catalogues
  (`docs/ARCHITECTURE.md` §8). Modland's index and file endpoints were measured, not assumed.
- **2026-08-31** — `AGENTS.md` in the project root records the English-documentation override as
  applying to Protracktor only; the workspace-wide rule is untouched.
- **2026-08-31** — Build scripts rewritten to keep Gradle's output in a log file and print four
  lines: the token cost of an agent reading task names was the whole reason. Artifact naming now
  matches the workshop's other projects (`versionName-versionCode-timestamp`), release signing uses
  the shared `PRZUNK_UPLOAD_*` properties, and the failure path was tested by breaking a Kotlin
  source deliberately.
- **2026-08-31** — Native layer: libopenmpt 0.8.9 vendored by `scripts/fetch-native-deps.sh`
  (pinned, SHA-256 verified), built by a CMakeLists that reads its source list from upstream's own
  `Android.mk`. JNI engine with Oboe output in `native/engine/`. Kotlin facade in
  `engine/NativeEngine.kt`, with the R8 keep rule that stops the release build renaming the native
  method names.

## Surprises worth remembering

- **AGP 9 embeds Kotlin.** Applying `org.jetbrains.kotlin.android` alongside it is a hard error,
  not a warning. But the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is *still*
  separate and *still* required whenever `buildFeatures.compose` is on. The two facts look
  contradictory and cost two failed configurations to establish.
- **`-keepclasseswithmembernames` preserves names but still allows shrinking.** The 0.1.0 release
  APK contains five of `NativeEngine`'s seven native declarations: `nativePositionSeconds` and
  `nativeDurationSeconds` were removed because no Kotlin code calls them yet. That is correct — JNI
  resolves lazily and per call — but it looks exactly like a broken keep rule at a glance, so check
  whether the missing method is simply unused before changing anything.
- **A test can pass for the wrong reason and mutation testing is how you find out.** The test for
  "forward after backward replays the same track" was built with plain `next()` calls, which makes
  the history identical to the play order — so deleting the redo branch entirely still produced the
  right answer by accident. It only became a real test once the history was made to *diverge* from
  the order with `startAt`. Two further rounds were needed: the mutation was also shadowed by
  `appended()` failing to truncate the forward branch, a latent inconsistency that is unreachable
  today only because another branch runs first.
- **`set -e` with `set -o pipefail` kills a script on a grep that matches nothing.** The test
  script's failure report died halfway through, so the run that most needed explaining printed
  least. Every grep that may legitimately find nothing now ends in `|| true`.
- **Filtering test-result XML by modification time reports zero tests on every second run**, because
  Gradle skips the task when nothing changed. The zero-tests guard has to distinguish "skipped as
  up to date" from "genuinely ran nothing", and now says which.
- **CMake's regex engine has no `\t` escape.** An anchor written `"^[ \t]*..."` matches nothing at
  all on a tab-indented file, silently. Strip the line with `string(STRIP)` and anchor on `^`
  instead.
- **`file(STRINGS)` treats a trailing backslash as a list escape.** Reading a makefile whose source
  list is continued with `\` yields the entire block as *one* list element beginning
  `LOCAL_SRC_FILES += \`, so per-line matching finds nothing. Read the file whole and use
  `string(REGEX MATCHALL)`.
  Both of the above produced zero parsed sources and both were caught only because the CMake asserts
  the count is over 100. Without that assert, the build would have produced a correctly linking
  libopenmpt with no decoders in it.
- **Oboe's prefab package requires `c++_shared`.** CMake's NDK default is `c++_static`, so the first
  native build fails with `[CXX1212] User is using a static STL but library requires a shared STL`.
  Fixed with `-DANDROID_STL=c++_shared`.
- **`unzip` is not installed in this environment.** Irrelevant to the app (archive handling will be
  native), but it will bite any script that reaches for it. Use Python's `zipfile` instead.
- **`sndh.net` does not resolve from this machine.** Whether the domain is gone or the network here
  blocks it is unknown. Recheck when `sc68` is integrated.
- **The workspace mount vanished mid-session** while the owner was adjusting WSL, taking the whole
  project directory with it until the mount came back. Nothing was lost, but the repository exists
  in exactly one place until it is pushed.

## Next

Nothing here is chosen; this is what the current state points at.

1. **A device pass.** The list of things nobody has confirmed on a phone is now longer than the list
   of things anybody has, and five formats and two archives went in without one.
2. **Subsongs** (`docs/BACKLOG.md` A2) — agreed in shape with the owner and unstarted. Console
   files and SIDs hold hundreds of tunes each, and today every one of them plays only the first.
   The song lengths already store every subsong's duration, so half the data is waiting.
3. **The persistent index and library scan**, which is what R9 (instant start) actually depends on.
4. **A3 fast scrolling** — explicitly deferred by the owner for a conversation, not for want of a
   plan.

Blocked on the owner: `docs/OPEN_QUESTIONS.md` Q1 (navigation model), the Commodore ROM question,
and whether `develop` should be merged to `master`.

## C — known defects

Numbered to match the A (open work) and B (wishlist) lists. A defect is something that does not do
what it was meant to; work that was never started is in `docs/BACKLOG.md`.

### C1. ~~Roughly half of `.sndh` files do not play~~ — FIXED 2026-09-03 by sc68 3.0.0b

Measured on thirty random Modland files through the real backend logic: **16 play, 5 load and render
silence, 9 fail `api68_load_mem`**. sc68 2.2.1 is from 2003 and its SNDH support is partial.

The fix is sc68 3.0.0b — see `docs/PLAN_FORMATS.md` §0, which has the route and what it costs.
Failures now say which backend refused and what it said, rather than claiming the format is
unsupported.

### C2. ~~`.sc68` container files — status unknown~~ — ANSWERED 2026-09-02: they play

Ten random `.sc68` files from Modland (of 1,775) through the host probe: **8 play, 2 render silence,
0 fail to load**. Better than `.sndh`, and the two silent ones fail in the same shape as the silent
SNDH files — the same 2003 library, the same partial coverage, so C1's fix is this one's fix too.

Kept in the list with its answer rather than deleted: the entry existed because the reasoning
underneath it had gone stale, and the record of that is worth more than a shorter list.

### C3. R9 is addressed but unmeasured

The next track is read while the current one plays and remote fetches are cached, but nobody has
measured whether that turns the owner's original five-to-thirty second wait into nothing **on his
SMB share**, which is the only measurement that counts.

### C5. ~~`%,1$d` in a plural would have crashed the online screen~~ — FIXED 2026-09-02

Never reported, because nobody had downloaded the song lengths on a device. The argument index must
come before the flag (`%1$,d`); the other way round is not a format specifier and `String.format`
throws. The count is always the `other` quantity at 61,157 entries, so the crash was certain rather
than conditional.

**Generalised the same day.** `./scripts/test-protracktor.sh` now fails on a malformed specifier
and on any string that exists in English and not in Polish, naming the line either way. Both checks
were verified by breaking the files on purpose and watching the script exit non-zero — a check
nobody has seen fail is a check nobody should trust.

### C6. ~~Re-entering Online lands inside the last folder, or on nothing~~ — FIXED 2026-09-03

Reported by the owner 2026-09-02: opening Browse and choosing **Online catalogues** should show the
catalogues. Instead it shows some folder, or an empty screen.

**Cause, read in the code.** `openDomain` clears `tracks` and `groups` but leaves `openCatalogue`,
`openFormat` and `openAuthor` exactly as the last visit left them:

```kotlin
it.copy(domain = domain, tracks = emptyList(), groups = emptyList(), arrivedByJump = false)
```

`OnlineDomain` branches on those three, so it renders the deepest one still set — with the list
underneath just emptied. Hence a folder view with nothing in it. Longstanding rather than new; what
changed is that `docs/BACKLOG.md` A20 has made people notice where Browse thinks it is.

**Fixed with A20**, as predicted, and the two rules are deliberate opposites that both hold:
descending and returning keeps your place (A20), leaving a domain and re-entering it does not (this).
The transition lives in `BrowseNavigation.enteringDomain`, pulled out of the controller so it could
be tested — removing the three lines that clear the hierarchy fails the test that catches it.

### C7. ~~"Add to the playlist" from a row menu closes Browse~~ — FIXED 2026-09-03

Reported by the owner 2026-09-02.

**Cause.** The row menu's single-track add and the bulk add button call the same callback, and that
callback closes Browse:

```kotlin
onAdd = { tracks -> viewModel.addToPlaylist(tracks); showBrowse = false }
```

Closing is right for the bulk button — you have finished choosing and want to see what you chose.
It is wrong for a menu item on one row, where the whole point is to keep browsing. One callback for
two intentions.

**Fixed as one thing, because it was one question.** The row menu and the bulk button now call
different things: finishing a selection means you are done here, adding one track from its menu
means you are not. And because Browse stays open, the row-menu add speaks — naming the track and the
playlist, or saying the track was already there rather than doing nothing visible. Fixing the
navigation alone would have replaced a jarring screen change with no feedback at all.

### C4. ~~Folder scanning trusts file extensions~~ — FIXED 2026-09-03

Not content probing, as `docs/ARCHITECTURE.md` §5 requires. A misnamed file is skipped by a scan; a
misleadingly named one is added and refuses only when played. Tracked as work in `docs/BACKLOG.md`
A6 as well, because it is both a defect and a piece of work.

## Known limitations — deliberate, not defects

- ~~**Background metadata resolution waits for playback to stop.**~~ **Lifted 2026-09-03.** It was
  a sc68 2.2.1 limitation: that release kept its 68000 emulator in global state. 3.0.0b is
  instance-based and was measured safe across four concurrent threads
  (`native/probe/sc68/probe_concurrency.c`), so a library scan now runs while music plays.
- **"More from this author" does nothing for local files**, and is absent rather than disabled for
  them. The local browser lists a granted tree flat, so there is no directory to jump to;
  directory-level local browsing is its own piece of work and nobody has asked for it.
- **A SID cannot be seeked**, only played from the start. libsidplayfp runs the machine; the only
  way to a position is to run it there, and nothing about the song lengths changes that. The
  position readout counts frames played and the scrubber shows progress without accepting a drag.
- **Subsong selection does not exist.** Every backend plays track 0. Some console files hold
  hundreds. It is a defect in effect, but the design is an open decision rather than a bug to fix —
  `docs/BACKLOG.md` A2.

## Fixed

- ~~**Play/Stop label goes stale when a module reaches its end.**~~ Fixed 2026-08-31 by the polling
  loop: the native side raises a flag when a module ends and the tick that drives the progress bar
  notices it.

- **2026-09-01 — `.sndh` played silence.** Not a missing format and not a wrapper bug: **sc68 wraps
  SNDH in a replay routine that lives on disk**, not inside the tune, and opens it by path. We
  shipped none, so every Atari ST file loaded, reported its title and author correctly, and then
  rendered nothing.

  Found by building sc68 for the *host* and calling its API on a real file, which is what turned a
  guess into a trace: `-> external replay 'sndh_ice'` followed by `failed '/Replay/sndh_ice.bin'`.
  No device involved, and the run took seconds.

  Two fixes. The replay binaries are packaged as assets and unpacked to a real path on first launch.
  And the sc68 gate no longer trusts `api68_verify_mem`, which returns **-1** for an ICE-packed SNDH
  that `api68_load_mem` then loads and plays perfectly — gating on it was rejecting these files
  before sc68 ever saw them. See `docs/LICENSES.md` for the question the replay binaries raise.

- **2026-08-31 — pressing next twice quickly played two tracks at once.** Each press launched its
  own open; the second overwrote the handle without closing the first, which kept playing with
  nobody holding it. Two things were wrong: the queue advanced *inside* the coroutine, so both
  presses read the same starting point, and there was nothing to cancel an open already in flight.
  The queue now advances synchronously and the in-flight open is cancelled, with anything it had
  already opened closed rather than started.

- **2026-08-31 — next and previous "behaved randomly", reported from a device.** Navigation walked
  the play history in *every* mode. That is right under shuffle — R5 asks for exactly it — and wrong
  without, where the list is on screen and "previous" has to mean the row above. History now governs
  only when shuffle is on. Whatever the bookkeeping, a control that disagrees with the visible list
  looks broken.
- **2026-08-31 — the gesture bar covered the transport row.** The dock's contents now sit above the
  navigation-bar inset while the surface still paints behind it; padding the surface instead would
  leave a strip of the wrong colour beneath the dock.

- **2026-08-31 — `NoSuchElementException: List is empty` in `PlayQueue.advanced()` on adding the
  first folder.** `order` was a constructor property defaulting to `tracks.indices`, and **`copy()`
  does not re-evaluate default arguments** — so a queue created empty and then given tracks by
  `copy(tracks = …)` kept the empty order, and the first `hasNext` indexed into nothing.

  Fixed by deriving `order` from `tracks` and a shuffle seed instead of storing it, which makes the
  two impossible to disagree. Patching the one call site would have left the trap in place for the
  next person, and `copy()` cannot be taken away from a data class. Two regression tests cover both
  routes in, and both were confirmed to fail against the original code.

- **2026-08-31 — `IllegalStateException: Track already closed` on pressing Play.**
  `DisposableEffect` was keyed on the loaded module. It runs `onDispose` whenever its key changes,
  not only when the composable leaves, and the lambda read the state at dispose time — by which
  point it already held the *new* value. So opening a module closed the module that had just been
  opened, and Play hit a freed handle. Keyed on `Unit` instead; replacing a module is the picker's
  job, and the effect only has to catch the screen going away.

  Worth keeping: the crash came from the code written to prevent a leak, and the guard in
  `Track.handle()` is what turned a use-after-free in native memory into a named exception with a
  line number.

## Branches

- `master` — repository base. Merging to it is the owner's decision (`AGENTS.md` §3).
- `develop` — current work; everything below is merged into it.
- **2026-09-03, round 5** — all merged: `feature/sc68-3-0-0b`, `feature/local-index`,
  `feature/cache-budget`, `feature/browse-scroll`, `fix/browse-defects`, `review/round-5`,
  `fix/review-round-5`, `fix/open-error-race`, `fix/database-singleton`,
  `fix/engine-shared-state`, `tooling/test-cache-honesty`, `docs/round-5-reconcile`.
- 2026-09-02 — `feature/asma-catalogue`, `feature/hvsc-songlengths` and the round-3 branches, merged.
- `feature/project-scaffold` — scaffolding and the Gradle skeleton. Not merged.

**Merged without the owner having run any of it.** `AGENTS.md` says to wait for his device test
before merging to `develop`; the exception is an unattended `/goal` run, which this was. Round 5 is
therefore in `develop` unreviewed by anyone but its author.
