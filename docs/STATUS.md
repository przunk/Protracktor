# Status

Updated: 2026-09-21 — version 0.7.0: the Amiga custom formats through UADE, their lengths from
songdb, the downloads grouped. versionCode is the commit count, schema version 19

## What works

A usable player, as far as anything can be called that without a device saying so.

- **Formats**: tracker modules through libopenmpt (MOD, XM, S3M, IT and dozens more), Atari ST
  through sc68 3.0.0b (SNDH, `.sc68`), the AY register dumps `.ym` and `.vtx` through ZXTune and
  lhasa (`docs/PLAN_FORMATS.md` §8 — they are LHA-packed, which is why they did not play until
  2026-09-09), Atari 8-bit through ASAP (SAP and 13 tracker formats), Commodore 64
  through libsidplayfp (PSID, RSID), seven console families through game-music-emu (NSF, GBS,
  SPC, VGM, HES, AY, KSS — measured, and opened at the first track that has sound in it, because HES
  and KSS routinely hold nothing at track 0), and the Amiga synth trackers through HivelyTracker
  (AHX, HVL). **The Amiga custom formats through UADE** — TFMX, Hippel, David Whittaker, Sonic
  Arranger and thirty more, once the replay routines are downloaded (`docs/BACKLOG.md` A44).
  **Heard on a phone: TFMX (`mdat.coolbass` with its samples)**, which exercises the second
  process, the fetched companion and the file lookup at once. The other 34 names are checked on
  the host only — 140 of 140 through the unchanged engine, under a parent directory that cannot be
  listed, as `/data` cannot on a phone. **Not yet on a phone**: the other formats, subsong
  switching, deleting the routines. **Length and seeking**: the formats state no length, so a second emulator plays the subsong silently to its end once playback starts, and the total and the slider appear a few seconds in; a tune that loops stays without either.
  The browser cannot have them at all. Backends sit behind one interface and are asked what they can do rather
  than assumed — libopenmpt and HivelyTracker seek, sc68 and libsidplayfp cannot, and the UI
  reflects that.
- **Ships no code it has no right to.** Of sc68's 99 replay routines the APK carries one — sc68's
  own — and fetches the rest from sc68 if the user asks (`docs/LICENSES.md`). SNDH is unaffected;
  `.sc68` waits for the download.
- **Online archives**: Modland, browsed offline from a downloaded index and fetched per track; ASMA,
  which arrives as one 20 MB archive and then needs no network at all; The Mod Archive, searched
  live; and **UnExoticA**, Amiga game soundtracks — 4,338 playable tunes across 924 games, indexed
  from `songdb` and fetched a game's `.lha` at a time. That one is behind a switch and can be taken
  out again, because its archive's maintainers were asked a question on 2026-09-08 and have not
  answered it yet (`docs/PLAN_UNEXOTICA.md`). HVSC's song lengths give SID tunes the duration the
  format cannot carry. Fetched music is capped at 512 MB, least recently used first
  (`docs/ARCHITECTURE.md` §19).
- **Library**: folders granted through the storage access framework, remembered between sessions.
  A folder is **scanned by opening every file with a real decoder**, not by reading its name, and
  the result is stored so later launches read an index instead of walking the tree
  (`docs/ARCHITECTURE.md` §18).
- **Playlists**: several, named, created, renamed, deleted, exported and imported as M3U, switched
  from the top bar — each row's own actions behind the same three dots a track row uses. Shuffle and
  repeat work inside the active one, and repeat-one on a file with several tunes in it loops the
  file rather than its last tune.
- **Storage you can reclaim**: everything the app has downloaded — the fetched-music cache, each
  catalogue index and archive, the HVSC song lengths — is listed with its size and a delete, and
  every one of them is re-fetchable (`docs/ARCHITECTURE.md` §19).
- **Transport**: a dock on every screen with shuffle, previous, play, next and repeat. Shuffle keeps
  a real history so backward returns to what was actually played; without shuffle, backward is the
  row above. Seeking where the backend allows it.
- **Survives a restart**: playlist, settings and the track last played all come back.
- **Survives leaving the app**: a foreground service with transport in its notification. Audio focus
  and becoming-noisy are honoured.
- **Both languages**, Polish and English, selectable inside Settings alongside the system default.
  The override applies before the activity reads resources and works across the supported API range.

Verified here: **135 unit tests**, genuinely executed rather than served from the build cache — the
full `:app:testDebugUnitTest` task ran on 2026-09-04 after the Settings changes. A controlled defect
in the language mapping made two new tests fail before it was restored. `lintRelease` and a release
build through R8 are green; the build keeps all 13 JNI symbols, three ABIs and the one bundled sc68
replay routine. Every backend was also run on the host against real
files, which is where the measured coverage in this file comes from — **including the console
families and libopenmpt, both measured 2026-09-04 having never been** — the consoles 116 of 141, up
from 100 once the measurement found what was wrong, and libopenmpt 390 of 457, which is 98.0% of the
178,795 Modland files it is handed (`docs/PLAN_FORMATS.md` §0b and §2). **Every backend now has a
number.**

Verified on a device: modules play (2026-08-31), SAP plays (2026-09-01), reordering a
playlist works (2026-09-02), and on the evening of 2026-09-02 all five of round 3 were tested on a device —
**Random rolling on, history, "more from this author" and both shares all work**. Read-ahead was
working and fetching serially, which was audible; that is fixed.

**2026-09-04, round 6 on a device.** Indexes correctly reported themselves stale after the name list
changed, and re-indexing worked. **OctaMED `.mmd0`–`.mmd3` and Oktalyzer `.okta` play** — the 5,653
files that were always playable and never offered. Freeing and re-downloading the ASMA archive
works. Adding to a playlist reports what it did; the playlist row menus work.

**2026-09-05.** Ten things found by using it and fixed the same night: deleting the only playlist
did nothing at all, an exported list came back named after its document id, the top bar changed
shape with the playlist's name and its gaps came from three unrelated sources, and changing the
language or theme threw you out of Settings. Theme and language are segmented choices now, with a
separate switch for the wallpaper palette. All confirmed on a device.

**2026-09-04, afternoon.** The console formats confirmed on a device: HES plays, KSS mostly plays,
subsongs walk. Two faults came out of that session and are fixed — the search for an audible track
stopped after twelve tracks and missed `aleste 2.kss`, whose first tune is number 47 of 256, and the
subsong strip yanked the view back to the playing tune, making the far end of a long file unreadable
while it played.

Three things came back wrong and are fixed: **repeat-one still advanced through subsongs** (see C13
below — the rule this app follows, and the simpler one), the seek bar looked draggable on tracks that
cannot seek, and **a SID played for ever** because nothing acted on the length HVSC had been
supplying since round 4 (`docs/ARCHITECTURE.md` §14). All three merged after a device confirmed them.

**Confirmed on a device:**

- modules play (2026-08-31), SAP plays (2026-09-01)
- round 3's five features — Random rolling on, history, "more from this author", both shares
  (2026-09-02)
- **2026-09-03, round 5**: **SNDH plays** — which also means `docs/review.md` R1 is genuinely fixed,
  not merely fixed as far as a probe can tell; the **local index survives a restart** and is read
  instead of rescanned; **Browse comes back to where you were**; **C6** looks right; **C7**
  confirmed — adding from a row's menu no longer closes Browse.
- **2026-09-03**: **ASMA plays** (SAP files from the archive) and a **local SID plays**, so
  libsidplayfp is confirmed on a device. Reading a local library is confirmed correct.
- **2026-09-03, late**: **subsongs work on a device.** Tactic (8 tunes) played on a device and
  confirmed the strip scrolls with many more. **C11** — auto-advance racing through every tune in
  silence — is confirmed fixed.
- **2026-09-03, evening**: the **stale-index notice** appears on Modland; the **action row** in the
  Now Playing, the **Browse** button, the **playlist counts** in both the switcher and the
  add-to dialogue, the **current playlist first**, and **history showing when** — all confirmed.
  The single **"Add to playlist…"** replacing the two add actions is confirmed too, which matters
  because it changed a route already signed off as **C7**.
- **2026-09-04, late**: **Settings works on a device** — the gear and Browse placement look right,
  System/Polish/English all switch the whole interface and survive a restart, playback survives the
  activity recreation, and deleting stored catalogue data from its new home works.

**Still not verified by anyone on a device:**

- **The two corrections from the Settings device pass:** Save and Discard now have visible labels,
  and a downloaded catalogue without an index reinforces its already explicit warning with the
  error colour. Confirmed on a device: the labelled right-side actions fit on a phone; their
  spacing was then increased by 6 dp and that final spacing has not yet been seen on a device. The
  missing-index colour also remains unverified on a phone.
- **The console formats** (NSF, GBS, SPC, VGM, HES, AY, KSS) — built and measured on the host,
  never played on the device. Modland has 5,015 NSF and 918 GBS; real headers read on 2026-09-03
  give `lil' monster.gbs` **68** tunes, `mario golf.gbs` 42 and `shinsenden.nsf` 39, which is also
  the first real test of the subsong strip scrolling. They will only appear after a Modland
  re-index — that index predates game-music-emu as well as libsidplayfp.
- **HVSC song lengths** — downloaded and stored, but no SID has been seen showing a duration.
- **C64 from a catalogue.** A local SID plays; Modland's SIDs are still invisible because the index
  predates libsidplayfp (**A22**).
- **What a scan costs on a large library.** It works and reads correctly on a phone
  (confirmed 2026-09-03). How long it takes on a library of thousands is still unmeasured.
- **The 512 MB cache ceiling**, which nobody has yet had enough cached music to reach.

## Finished

- **2026-09-08** — **Hold next to leave the file.** A press moves by tune, a hold moves by file.
  `aleste 2.kss` holds 256 of them, so leaving it with the ordinary next was 256 presses. Not a
  fourth transport button: that would be on screen always for something wanted rarely, in the row
  read without looking. Deliberately not on the notification or a headset button either —
  neither has a long press, and inventing a double-tap for them would be a second vocabulary for one
  idea.

  It uncovered an older defect next door. **`canGoNext` counted only files**, so on the last track
  of a playlist the button was disabled while `next()` would happily have stepped to subsong two —
  a file with 256 tunes could only be walked from the Now Playing strip. The notification reads the
  same value, so it was wrong there too. It now asks whether *anything* would happen; the long press
  has its own file-level test.

- **2026-09-05** — **AHX and HVL** (`docs/PLAN_FORMATS.md` §6). The two names this app claimed on an
  assumption, removed on 2026-09-04 when the assumption was measured, and back a day later with
  something behind them: HivelyTracker's standalone replayer, BSD-3-Clause, three source files —
  the smallest vendored decoder here. **80 of 80 sampled Modland files loaded from a buffer, were
  audible and reached a song end**, the first backend to come back clean on both halves, across
  1,433 files. It is also the first to arrive with a **duration and a working seek bar**: running
  the sequencer without the mixer costs about a millisecond, so the length is measured at load
  rather than looked up. The probe is built twice, with 32- and 64-bit typedefs, because
  armeabi-v7a is one of our three ABIs and this is Amiga code that predates the question.
- **2026-09-03** — **A playlist is a file, both ways** (**A25**). Export writes M3U — readable by
  other players, with our own identifiers in comments they ignore — and import reads it back,
  matching each line by its recorded id first and by filename and size second, into a new playlist
  of its own.
- **2026-09-03** — **Subsongs** (**A2**). A file holding fifteen tunes played one of them; now the
  Now Playing lists them and a mode decides whether they play through. All five backends could
  already select — ASAP was calling `ASAP_PlaySong` with a fixed index — so the work was mostly
  saying so. sc68 and libsidplayfp count their tunes from one and the rest from zero; that is
  converted at the backend boundary rather than leaked to every caller.
- **2026-09-03** — **A scrollbar you can grab**, at the edge of the playlist and of Browse's track
  lists (**A3**), and **bulk operations on the playlist** (**A4**): long press to select, then add
  the selection to a playlist — existing or new — or remove it. Undo brings a whole bulk removal
  back, which is the part that could have lost somebody's playlist and so is a tested object of its
  own.
- **2026-09-03** — **One shape for an action, and one way to add.** Icons with their names
  underneath, in a row, wherever a track offers actions and wherever Browse is entered or left
  (**A17**, **A23**). The two add actions became one *"Add to playlist…"* opening the picker, with
  the current playlist first and **each playlist's size beside its name** (**A21**). History says
  **when** each track was played (**B15**), which is what the list is for rather than a
  list that reorders itself.
- **2026-09-03** — **A catalogue index says when it is out of date.** Every index records which
  decoders built it, and one built by a different set — or by an unrecorded set, which is every
  index that exists today — says so and points at the re-index button. This is what hid 60,572 C64
  tunes: a Modland index that predated libsidplayfp, and an index filtered at index
  time to what a backend can play looks *empty* rather than stale. Closes **A22** and the asymmetry
  `docs/BACKLOG.md` A7 had been carrying as a note asking a human to remember.
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
- **2026-09-02** — **"More from this author"**, from Now Playing and from a track's menu:
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

- **2026-09-02** — Finding the playing track: a "Show in playlist" action in Now Playing, and
  a follow toggle over the list that is off by default, hides itself while following, and switches
  off the moment the user scrolls by hand.

- **2026-09-02** — A track can be played from Browse and from search results without adding it: the
  results become the queue while you are looking at them, and the playlist is untouched. Sources are
  named as paths everywhere, so a result says where it came from and Information shows
  `Modland/Format/Author`.

- **2026-09-01** — Adding tracks no longer raises a snackbar over the rows it is reporting, and
  titles and authors are filled in by a background pass instead of waiting for each track to be
  played.

- **2026-09-01** — The playlist row rebuilt to the agreed shape: ordinal or a play triangle, title
  with author and format, an overflow menu holding information and delete, and a drag handle that
  reorders. Reordering is an edit, so it waits for Save like the others.

- **2026-09-01** — **The consoles.** game-music-emu 0.6.5: NES (NSF/NSFE), SNES (SPC), Game Boy
  (GBS), Sega (VGM/VGZ/GYM), PC Engine (HES), ZX Spectrum (AY), MSX (KSS). Verified on the host
  before integration — NSF 4/4, SPC 4/4, GBS 3/3, VGM 3/3.

- **2026-09-01** — **Atari 8-bit.** ASAP 8.0.0 vendored and building for every ABI: SAP plus Chaos
  Music Composer, Raster Music Tracker, Theta Music Composer, Delta Music Composer, Music ProTracker
  and their double-play variants. Twelve of twelve random Modland SAP files played on the host
  probe, and **confirmed on a device on 2026-09-01**.
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
  `/mnt/workspace/.tooling/android-sdk` with approval; nothing already there was
  modified.
- **2026-08-31** — `push-protracktor.sh` and `internal/git-askpass.sh`, adapted from the Kratkoza
  equivalents, with permission to read them.
- **2026-08-31** — Gradle project: `app` module, Compose, Material 3, minSdk 29 / targetSdk 36,
  Gradle wrapper 9.6.0, build directory off the 9p mount. Debug APK builds, was inspected, and runs
  on a device.
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
- **The workspace mount vanished mid-session** while WSL was being adjusted, taking the whole
  project directory with it until the mount came back. Nothing was lost, but the repository exists
  in exactly one place until it is pushed.

## Next

Nothing here is chosen; this is what the current state points at.

1. **A device pass.** The list of things nobody has confirmed on a phone is now longer than the list
   of things anybody has, and five formats and two archives went in without one.
2. **Subsongs** (`docs/BACKLOG.md` A2) — agreed in shape and unstarted. Console
   files and SIDs hold hundreds of tunes each, and today every one of them plays only the first.
   The song lengths already store every subsong's duration, so half the data is waiting.
3. **The persistent index and library scan**, which is what R9 (instant start) actually depends on.
4. **A3 fast scrolling** — explicitly deferred  for a conversation, not for want of a
   plan.

Blocked on a decision: `docs/OPEN_QUESTIONS.md` Q1 (navigation model), the Commodore ROM question,
and whether `develop` should be merged to `master`.

## C — known defects

Numbered to match the A (open work) and B (wishlist) lists. A defect is something that does not do
what it was meant to; work that was never started is in `docs/BACKLOG.md`.

**Entries state the defect, not who found it.** What was wrong, how it was proved, and what was
changed — a name and a date belong to git history, and a quotation from a conversation belongs
nowhere in a repository (`docs/BACKLOG.md` A49).

### C89. Search filtered to Atari 8-bit never looks in ASMA — **reported 2026-09-24; FIXED, merged and confirmed on the phone the same day**

**Fixed, the owner's choice (c), 2026-09-24:** each catalogue row stores its platform. Schema **20**
adds `catalogue_tracks.platform`; `Platforms.forCatalogueRow` is the one rule -- the archive when it is
one machine's (ASMA: Atari 8-bit; UnExoticA: Amiga), then Modland's directory, then the file's name
-- and `Platforms.sqlCase` is the same rule as the `UPDATE` that fills the column for rows already
stored, run by the re-decision that `Platforms.fingerprint`, now part of the index stamp, triggers at
the next start. Search, its count, the dice's platform scope and the chips' counts all read the
column: **the dice had the same defect**, drawing no ASMA tune under Atari 8-bit. A test holds the
`UPDATE` to the function for every name and directory in the table; another finds an ASMA row under
Atari 8-bit; a migration test keeps a version-19 row through 20. Rows in a Modland directory the table
does not map now get the platform of their name, where before they had none.

The report as noted:
### C90. "More from this author" from search results left the search behind — **FIXED, merged and confirmed on the phone 2026-09-24**

The owner: from Random the jump shows the digression -- *Browsing author* over the folder, a way
back -- and from Search it did not; "it has to work identically". **Mechanism:** the heading was
drawn only while the dice waited (`randomMode || diceWaiting`), and Back after a jump
(`arrivedByJump`) left Browse outright, so the results, their words and scope were gone. **Fixed:**
a search is a place to come back to, like the dice. The jump keeps it (`BrowseNavigation.searchToReturnTo`,
`searchWaiting`), the heading shows, next and previous walk the author when a result was what played
-- and not when the playlist was playing, since a look must not move the music -- and Back returns
to the results as they were (`resumeSearch`). A tune chosen in the folder meanwhile stops and the
results' tune waits paused, as the dice's does; otherwise the music plays on.

### C91. `Bonio.sap` plays to the ~3:00 fallback though its author knows its length — **OPEN, reported 2026-09-24**

The owner: `asma/Composers/przunk/Bonio.sap` (his own) is cut at ~3:00. **Mechanism, read from
the file:** its header has no `TIME` line -- `AUTHOR`, `NAME`, `DATE`, `STEREO`, `TYPE`, `INIT`,
`PLAYER` and nothing else -- and ASAP's length for a SAP is `TIME` alone, so the app knows none and
the fallback ends it, marked `~`. ASMA does not state it elsewhere either: its only other record of
the tune is a Demozoo entry with no length. B36's inventory put about 180 of Modland's SAPs in the
same place.

**Options:** (a) `TIME "m:ss"` added to the file, per subsong, and the file sent to ASMA -- every
player then knows it; the owner's seven files. (b) Measure a SAP's natural end, as NSF's variant (a)
does -- only for tunes that fall silent. (c) B36's length database. Recommended: (a) for these, (b)
after the launch.

**Found 2026-09-25: ASAP ships the tool that sets these `TIME` lines** -- `asapscan -t`, in
`native/vendor/asap/asapscan.c`, which is how ASAP's and ASMA's maintainers measure them. It runs the
tune without making sound and keeps POKEY's registers for every frame; it detects **silence** (five
seconds of none: the tune ended there) and **a loop** (the last three minutes of registers repeating
something already played: one pass ends where the repetition starts), scanning at most fifteen
minutes. So (b) can do more than NSF's measurement, which finds silence only -- and most Atari music
loops rather than ending.

What (b) costs this way: `asapscan` reads ASAP's internals, which `asap.h` does not expose, so a small
C file compiled in `asap.c`'s place (one translation unit, so the internals are visible and nothing is
defined twice) carries the port -- about 150 lines, the hashing included, since a plain search over
45,000 frames is too slow. Then a background measurement for a SAP with no `TIME`, as `GmeBackend`
does for NSF (`startedPlaying`, `durationArrivesLater`): a second, silent copy, a second or two of one
core on a phone. Tests on two SAPs built in the check -- one falling silent, one looping. **The page
does not get it**, as it has no NSF measurement: its engine has no threads.

**Recommended (2026-09-25), the owner agreeing to write it down:** now, the `TIME` lines for the
owner's seven files, worked out with the same detection, for him to put in the files and send to
ASMA -- every player anywhere then knows them, the page included. After the launch, (b) as above in
the app.


### C92. The web page zoomed and moved in a Safari tab on an iPhone — **FIXED and merged 2026-09-24; not yet seen on an iPhone**

Reported by a friend of the owner, opening the page from a link (a Safari tab, not the installed
app): pinch to zoom worked and the whole page could be dragged. The owner: the page should fit and
lock to the viewport. **Mechanism** (WebKit's documented behaviour; there is no Safari here to watch
it): a tab ignores `user-scalable=no` on purpose, and the earlier fix (2026-09-11) refused only
Safari's gesture events; and the document itself could move -- rubber-banding, a list passing its
drag on at the end, room left by the collapsing address bar. **Fixed:** a two-finger `touchmove` is
refused non-passively, the body is pinned to the viewport and only the lists inside scroll,
`overscroll-behavior: none`. To check: on the iPhone, from a link; and that every view still scrolls
to its end on a computer and on Android.

### C93. The web page on a phone: stretched buttons, Settings that would not scroll, a QR code past its sheet — **FIXED and merged 2026-09-24; first look on the phone "ok", full check pending**

The owner's screenshots (Chrome on Android, Safari on an iPhone): Browse, Pair and Paste were tall
slabs, the playlist's name cut to "Se…", Random's Playlist button stretched across its row; Settings
ran past the bottom with no way to scroll; the pairing code and address stuck out of their sheet.
**Mechanism:** the sizing written for Now Playing's squares was a bare `.action` rule, so since A27 it
reached every labelled button and let each grow to fill its row; a sheet taller than the screen had
nothing to scroll it, which the pinned page (C92) made plain; the code was a table of fixed 5-pixel
cells. **Fixed:** the rule is scoped to Now Playing (a page check fails if the top bar's buttons grow
again), a sheet scrolls inside itself with its heading kept, Settings takes nearly the whole height
on a phone, the code is a scalable picture and the address breaks anywhere.

### C94. Shuffle played the same order from any tune, and stopped early — **FIXED, merged and confirmed on the phone 2026-09-25 ("ok"), APK and page**

The owner, with his 41-tune Favorites, no repeat, shuffle on: from wherever he started, next next next
ended on the same tune (*Studium Fałszu*), with no next; and choosing another tune by hand gave the same
sequence. **Mechanism:** the shuffled order was one permutation dealt when shuffle came on and kept;
a tune chosen by hand stood somewhere in its middle, and next walked only what came after it -- the
same tunes from any start, ending on the permutation's last, and the ones before it never played.
Both sides, each in its own code. **Fixed, as the owner decided:** a tune chosen by hand deals a
fresh order starting at it, as does shuffle turned on (from what plays); next then plays every tune
once before the end, and **the chosen tune is the first**: back stops at it rather than stepping into
what played before the choice (the owner, on the first test build). **Repeat off + shuffle stops after one pass** and **repeat all + shuffle goes on,
dealing again each lap** -- the owner's first idea, shuffle without repeat going on for ever, was
talked through and dropped, since it would make the two the same. The rule is now in
`docs/rules/queue-cases.tsv` (`shuffleFromTap`), run by the phone's `PlayQueue` and by the page
through its own row and next; both failed before the fix.
### C88. 3,800 files offered by name that their decoder reads as another format — **found 2026-09-23; FIXED the same day, merged 2026-09-24**

**Checked on the owner's phone, 2026-09-23:** a file each from FamiTracker, Deflemask and Music Editor
did not play; SidMon 1's did (UADE). **Fixed (option (a)):** those three directories -- 3,718 files --
are listed as refused in `web/src/formats.tsv` (`directory` rows) and `SupportedFormats.refusedDirectories`,
held to one list by `SupportedFormatsFileTest`. Their rows stay in the index, unoffered; the list is
part of the index fingerprint, so an index already on a phone or in a browser re-decides itself at
the next start with no download. SidMon 1 is left alone.

**Seen by the owner, 2026-09-24:** on the phone, the three directories gone from an index that was
not downloaded again. **Not checked:** the page, and SidMon 1 still being offered. The recompute's SQL now lives in one
function the test runs too (`playableUpdate`), where the test used to keep a copy.

The finding as noted:

Found by B36's inventory (`feature/b36-length-inventory`, `docs/inventory/LENGTHS.md`): in twelve
Modland directories the page's engine opened **none** of the files sampled. The big four -- Deflemask
(1,807, `.dmf`), FamiTracker (1,779, `.ftm`), Music Editor (132, `.med`), SidMon 1 (61, `.sid`) -- carry
a name that `formats.tsv` gives to a decoder that reads it as a **different** format: X-Tracker's DMF,
Face The Music, OctaMED's MMD, a C64 SID. So they are indexed, offered, and refused; since A51 the
refusal at least names the directory. The `.gtk` case of A51 was four files and earned no machinery;
this is 3,800. **Not checked:** whether the phone, which has UADE as well, plays any of them. To
decide: leave them (A51 explains the refusal), or keep them out of the index by directory.

### C87. Online catalogues jumped while downloading — **FIXED 2026-09-23, merged, confirmed in a browser**

The owner: while indexing, the rows vanished and came back, and the download button and its spinner
moved. Two causes. Every download's start and end redraws the list, and the redraw **cleared it and
the note first**, then read from IndexedDB, then drew -- an empty moment each time, and one row
still waited mid-list for the stale-index check. And the spinner with "indexing…" under it was 84 px
against the button's 44, so the row's text rewrapped. Now the list is built aside and swapped in at
once, the note is rewritten only when it says something new, and the spinner takes the button's
44 × 44 box with its word for screen readers only.

### C86. GitHub Pages stopped deploying pushes to `gh-pages` — **worked around 2026-09-23; cause unknown**

Two pushes to `gh-pages` on 2026-09-23 (`af27036`, `43a61d5`) reached GitHub, and no *pages build and
deployment* run followed: the last was 2026-09-22 20:20 UTC, and the page kept serving that day's
files (`last-modified` of `src/app.js`), without the round's Browse fixes. Settings were unchanged.
**What worked:** *Settings → Pages → Branch: None → Save*, then *gh-pages, / (root) → Save*; a run
started at once (20:13 UTC, success). **How to tell next time:** the repository's *Actions* tab shows
no run after the push, or `curl -sI https://przunk.github.io/Protracktor/src/app.js` shows an old
`last-modified`. Why the pushes did not trigger a build is not known -- it happened the day the
`przunk.github.io` user-site repository was created, which may or may not be related.

### C85. Protracktor was offered as a browser — **FIXED 2026-09-23, merged, confirmed on the phone**

The owner, looking for "Open by default" for A40, found "Browser app" in the app's settings, with
Protracktor as a choice. Mechanism: the filter for module links (B24) named `http` and `https` and
no host, and Android ignores every path pattern in a filter without a host -- so it claimed every
web address, which is what a browser is. In 0.8.0 as published. Now the web half names the
archives' hosts (Modland, ASMA, The Mod Archive, UnExoticA) and the local half keeps `content` and
`file` as before; a test holds every `http(s)` filter to a host. **Not changed, noticed:** the local
half has no host either, so its patterns are ignored too and it matches any `content`/`file` link
that comes without a type -- which the mime-type filter above it is there for anyway.

### C84. Browse's search field stood over every list — **FIXED 2026-09-23, merged, confirmed in a browser**

The owner: the field was visible across Browse, where the phone shows it only in Search. It now
belongs to Search (`fix/web-search-in-its-place`).

### C83. Several catalogue downloads at once wrote over each other in Browse's note — **FIXED 2026-09-23, merged, confirmed in a browser**

The owner: pressing download on each catalogue made the note flicker between them. Progress is now
only the row's spinner; the note says what is held and what failed (`fix/web-download-note`).

### C82. Buttons with a label and no icon, outside dialogs — **found 2026-09-23; FIXED, merged and confirmed on the phone the same day**

**Fixed, with the icons the owner agreed to:** Add a folder -- folder; Add files -- document; Clear
history -- the cross; New playlist -- plus; Import -- download; Back to the playlist -- the playlist.
One `IconLabel` for all six, the pairing screen's shape.

Found while giving the pairing screen's two buttons their icons (`fix/scanner-button-icons`).
**Corrected 2026-09-23:** the first version of this entry also listed the dialogs' *Cancel*, *Save*,
*Delete* and *Close*. Those are not defects: `AGENTS.md` ("An action is an icon with a label")
exempts a dialogue's confirm and cancel as Material's own convention. What is left, text only today:
*Add folder* and *Add files* and *Clear history* in `BrowseScreen`; *New playlist* and *Import* in
`PlaylistSwitcher`; *Back to playlist* in `PlaylistScreen`. Found by a search for buttons whose first
child is a `Text`; a button built another way could be missing. Not fixed: outside the item it was
found in; the owner decides whether it is its own item.

### C81. An NSF refused as "invalid load/init/play address" — **reported 2026-09-22; diagnosed 2026-09-23; option (a) BUILT, merged and confirmed on the phone 2026-09-23; (b) still open**

The owner: `Nintendo Sound Format/Y. Matuo/19 neunzehn.nsf` (Modland, 26,768 bytes) does not play;
the reason shown is game-music-emu's "invalid load/init/play address".

**Mechanism (checked 2026-09-23).** The file is a **Famicom Disk System** rip: header chip flags
`0x04` (FDS), load `$6000`, init `$D000`, play `$D650`, bank bytes `01 01 01 01 01 00 01 01`.
The NSF specification allows exactly this -- on the FDS, `$6000–$DFFF` is RAM, a rip may load below
`$8000`, and two extra bank registers `$5FF6/$5FF7` map `$6000–$7FFF` -- while noting that it "is not
universally supported" ([NESdev wiki, NSF](https://www.nesdev.org/wiki/NSF)). **The file is valid;
game-music-emu 0.6.5 does not implement the FDS memory model.** `Nsf_Emu::load_` refuses any load
address below `rom_begin` (`$8000`) outright and calls it "Corrupt file", and even without that check
it maps `$8000–$DFFF` as read-only ROM and has no `$5FF6/$5FF7`. Reproduced on the host:
`native/probe/gme/build/probe-gme` on the file prints
`VERDICT reject:Corrupt file (invalid load/init/play address)`.

The later, unreleased line of the same library (blargg's "0.6pre", kept in DeaDBeeF's
`plugins/gme/game-music-emu-0.6pre/gme/Nsf_Impl.cpp`, LGPL-2.1 like ours) has it: the load address
is only too low below `$6000` when FDS is on, `$8000–$DFFF` is mapped as FDS RAM, and banks under
`$6000` are mapped for `$5FF6/$5FF7`. Read from its source 2026-09-23; not built here.

**How many files:** not known. The Modland index does not carry NSF headers; counting means
fetching every NSF, which B36's inventory step does anyway.

**Options** -- the owner chose (a), 2026-09-23:

- **(a) Chosen, BUILT: say it truthfully.** `GmeBackend::fdsLoadsLow` refuses the file before
  game-music-emu sees it: *a Famicom Disk System NSF that loads below $8000, which the console
  decoder (game-music-emu) cannot play yet*. Two engine checks: that file refused in those words,
  an FDS NSF at $8000 still opening; the first fails with the check switched off.
  The recommendation as written: The refusal becomes "a Famicom Disk
  System NSF that loads below `$8000` — not supported yet" instead of "Corrupt file". A check of
  the header in `GmeBackend` before gme sees it; a small change, both languages, no risk to what
  plays today.
- **(b) After the launch: teach gme the FDS memory model**, ported from 0.6pre's `Nsf_Impl` as a
  patch in `native/patches/` (as libopenmpt's is): RAM at `$6000–$DFFF` when FDS is flagged, the
  low load address, `$5FF6/$5FF7`. Checked with this file and other FDS rips; the existing NSF
  checks must stay unchanged. Medium; it touches the emulator's memory map.
- (c) Move the whole gme backend to 0.6pre. Would also be the moment to look at C26 (SPC tempo),
  but it swaps the engine under every console format ten days before a launch.

### C80. A SID that needs the C64's BASIC ~~plays silence~~ — **found 2026-09-21; the silence FIXED the same day, merged 2026-09-22, confirmed on the phone; playing them still OPEN**

**The small part is done:** the SID backend reads the header's own compatibility field and refuses a
BASIC tune with *"this tune is a BASIC program and needs the Commodore 64's BASIC ROM, which
Protracktor does not have"*, which the app shows as the reason the file would not open. Checked on
the host: `Prelfugueinfmaj_BASIC.sid` is refused with that sentence, and `scripts/check-engine.mjs`
builds a BASIC RSID and fails if it opens -- it did open, before the change. A folder scan now
records these files as unplayable. **Playing them is still the ROM decision below.**


The owner: `Pace_Wayne/Prelfugueinfmaj_BASIC.sid` "does not play on my phone".

**Why, checked on the host:** it is an **RSID with the BASIC flag set** — the tune is a BASIC
program, and running it needs the C64's BASIC ROM. The app plays SID without ROMs (by decision, when
libsidplayfp was integrated), so the engine opens it without complaint and renders **silence**: a peak
of 0.005 over 50 buffers, where music reaches tenths. **590 of HVSC's 60,572 files in Modland are
`_BASIC`**, and every one of them will do the same.

Two things, and only the second is small:

1. **Playing them** needs the BASIC ROM, which is the ROM question kept out of scope when
   libsidplayfp arrived: ship it, ask the user for it, or live without. The owner's decision.
2. **Silence is a defect meanwhile** (AGENTS.md §7): a tune the engine cannot play must say so, not
   open and play nothing. The flag is in the file's header, so the refusal can be as specific as
   "this tune is a BASIC program and needs the C64's BASIC ROM".
### C79. ~~Accented letters in a title came out as `�`~~ — FIXED 2026-09-21, merged; confirmed on the phone

`Zalza/akes lekhorna.mod` showed **"�kes lekh�rna (za)"**; `docs/BACKLOG.md` A47 noted it.

**The cause written down first was wrong, twice over.** A47 said the title was ISO-8859-1 (`0xC5`,
`0xF6`) spoiled by `NewStringUTF`. Read from the file, the bytes are **`0x86` and `0x94`: CP437**, a
DOS machine's "å" and "ö". And the `�` was not made at the JNI boundary at all: **libopenmpt** made
it, decoding a MOD's text as "Amiga, no C1", which turns every byte from 0x80 to 0x9F into U+FFFD
before the engine sees the string. The engine was handing out valid UTF-8 with the letters already
gone. Built to the first reading, the fix would have left this very file as it was.

**The fix is one rule, in two places** (the owner chose it, 2026-09-21): valid UTF-8 stays; text
holding a byte from 0x80 to 0x9F is CP437; anything else is ISO-8859-1.
- `native/patches/libopenmpt/0001-read-c1-text-as-cp437.patch`, applied by `fetch-native-deps.sh`,
  puts it inside libopenmpt for the three "no C1" charsets it uses.
- `protracktor::fileTextToUtf8` puts it on every line of every other decoder's description, through
  `describeOf`, the one way a description leaves the engine -- a SID's Latin-1 author reached the
  phone as raw bytes before.
- **Migration 18** hands back exactly the rows stored damaged: playlist and history rows get their
  file name as the title and an empty author, which the background pass and the next play look
  for; library rows lose their decoder fingerprint, so their folder offers a rescan.

**Not changed:** names inside LHA archives stay raw, because they are what an UnExoticA tune is
extracted by.

**What it cannot tell apart:** a DOS title with no byte in 0x80–0x9F but with, say, `ñ` or box
drawing reads as ISO-8859-1; a Windows-1252 apostrophe (`0x92`) in a MOD reads as CP437's `Æ`.
Both rare.

**Verified:** the reported file through the rebuilt engine now reads `åkes lekhörna (za)` (bytes
`c3 a5 … c3 b6`). `scripts/check-engine.mjs` builds a MOD and a PSID and checks CP437, ISO-8859-1
and UTF-8 text through both paths -- three of those checks failed before the change. The migration
is tested against real SQLite, and failed before the migration existed. **On the owner's phone,
2026-09-21:** `akes lekhorna.mod` reads `åkes lekhörna`. The migration's handing-back of stored rows
was not looked at separately.

### C78. ~~The privacy policy could not be scrolled to its end~~ — FIXED 2026-09-21, released in 0.7.0

Found the day the in-app policy was added: it stopped at the first line of "Changes", would not
scroll further, and ended against the bottom edge — its last lines lay under the navigation bar.

**The first fix did not fix it.** The page was a full-screen dialog; handing the insets to its
Scaffold (`decorFitsSystemWindows = false`) changed nothing on the phone, which says the dialog
received no system-bar insets at all. So the legal pages are no longer a window of their own: they
show in the place of the Settings list and take Settings' own padding, which the app's Scaffold
already computes to clear the top bar, the dock and the system bars — plus 32 dp after the last line.
Reasoned from the layout; there is no emulator here, and the phone decides.

### C77. ~~Several deletes spoke all at once, seconds after their rows had gone~~ — FIXED 2026-09-21

After C76 the rows went at the press, but each delete's message still waited for the database. Four
deletes in a row queued on the one database, finished almost together, and four snackbars arrived
at once. Every delete in Settings now says its message at the press, with the row: the size freed
is known before anything is deleted, so the message is the true one. The work goes on behind it,
and only a failure speaks again ("Could not delete …").

### C76. ~~Deleting Modland's index did nothing for ten seconds~~ — FIXED 2026-09-21

Confirmed delete in Settings, and nothing happened; ten to fifteen seconds later "Index deleted"
and the row went. ASMA and UnExoticA went at once. Deleting Modland is half a million rows and
their indexes in one transaction: **5.1 s on a desktop, measured**, on a table built to Modland's
size. Rebuilding the table from the rows that stay measured 2.3 s, and was not taken: it needs the
table's definition written a second time outside the schema, which the next schema change would
forget.

What was wrong was the silence, so the screen is told first: the catalogue shows as not indexed,
its storage row goes, and its Browse row reads "deleting…" and refuses a new download until the
delete has finished.

### C75. ~~A downloaded set's tick appeared seconds late~~ — FIXED 2026-09-21

The song metadata and replay routine rows showed the dim cloud for three to five seconds and then
the tick. The tick waited for the counts — `COUNT(*)` over tables of 380,000 and 476,919 rows,
queued behind the platform counts on the same database — when all it needs is whether anything is
there. `HeldSets` asks that with one `EXISTS` per table and one directory listing per set, and is
published before the counts are started; the counts still arrive later for the second line.
`DownloadOfferTest` holds the rules to the quick answer and not to the counts.

### C74. ~~The replay-routine rows appeared for a few seconds and vanished~~ — FIXED 2026-09-21

Opening Online catalogues on a phone holding both sets of replay routines showed their two
download rows for a few seconds, and then they went. C71's flash again, one screen down: the rows
were drawn while the counts were zero, and the counts are zero until the screen has read them —
which is also what "not downloaded" looks like. C71 fixed the offer at the top and left these.

Fixed by the regrouping of the same day rather than by a second gate: the song metadata and the
replay routines are now one row each, always drawn, and their second line waits for
`heldCountsKnown` — a row that is always there and says nothing until it knows cannot flash.
`DownloadOfferTest` covers the two "complete" rules the rows read.

### C73. ~~The next subsong after a tune ended was selected and never heard~~ — FIXED 2026-09-21

With "play all subsongs" on, `cust.paradroid` moved to its second subsong when the first ended, the
app showed it as playing, and nothing sounded. Pause and play brought it back; four seconds later it
stopped again.

When a tune ends, the audio callback returns `Stop` and Oboe never calls it again, but the stream
object stays. `requestSubsong` then selected the next subsong and called `start()` to be heard, and
`start()` returns at once when a stream exists. `restart()` closes the stream before starting, which
is why pause and play worked. It does the same now.

**Not a UADE defect**, though UADE exposed it: every subsong there ends with a clean short render,
and paradroid's second to sixth are two- to five-second effects, so the "stopped again" was the
same fault at the next boundary. Any backend whose subsongs end by running out would have met it.
Not reproducible off a phone — Oboe is the half the host run does not have — so it was reasoned
from the code; **confirmed on the phone 2026-09-21**, all seven of paradroid's subsongs in turn.
### C72. ~~The app died when Android refused the playback service the foreground~~ — FIXED 2026-09-21

Found on a phone while stepping through `cust.paradroid`'s subsongs, with logcat:
`ForegroundServiceStartNotAllowedException: Service.startForeground() not allowed`, thrown from
`PlaybackService.onCreate`, uncaught, and the process ended.

From Android 12 a service may enter the foreground only while its app is on screen, and the check
is made when the service calls `startForeground` — not when it was asked for. A press in the app
starts the service; if the app is no longer visible by the moment `onCreate` runs, the refusal is an
exception, and nothing caught it. Now it is caught: the service logs it and stops. The music is in
the engine and plays on; the notification returns with the next press in the app.

**What put the service in that position is not established.** The service stops itself when nothing
is playing, loading or current (C43), and something asked for it again from behind the screen. The
crash is fixed whatever the sequence was; the sequence is still owed an explanation.

It was first taken for a UADE crash, because it happened on an Amiga tune. It is not one, though
looking for it did find a real UADE defect of a different kind (`docs/BACKLOG.md` A44, SIGPIPE).

### C71. ~~"Get some music to browse" flashed on every visit to Online catalogues~~ — FIXED 2026-09-21

The row appeared for a moment each time Online catalogues opened, on a phone holding every index,
and then vanished.

The rule offered it while HVSC's song lengths or the track metadata were missing, and read both as
counts. **Those counts are read only when the screen opens**; before that they are zero, which is
also what "not downloaded" looks like. So for the frame between opening and the counts arriving,
the rule saw a phone with nothing on it.

`BrowseState.heldCountsKnown` now says whether the counts are real, and the rule lives in
`BrowseState.offersDownloadEverything` rather than in the composable, where it can be tested.
`DownloadOfferTest` fails with the gate removed. In since round 12 (`030d87a`).

### C70. ~~Random played, and the playlist stayed on screen~~ — FIXED 2026-09-19

Play anything from Browse, then open Random: a tune started and the playlist was still there. The
Random view opened and closed itself in the same frame.

**Two readings of one question.** The view closes when search results take over playback (C49), and
it asked `PlayerUiState.searchMode`, which was `resultsQueue != null` and nothing else. Playing a
dice pick sets `transient` and does not clear the queue Browse was last played from, so that queue
was still sitting in the state — not driving anything, but answering yes.

The transport had the rule right the whole time. `nextFile` reads `transient != null &&
!diceWaiting` as "the dice owns this", and `searchMode` now says the same thing, so a queue left
behind is no longer a queue playing. The dice waiting under an author's list (`docs/BACKLOG.md`
A41) is still search mode, because there the list really is driving next and previous.

`openRandom` also clears the queue now. The reading was the defect; the stale queue was what fed
it, and a session that starts should not carry the last source's state — which is what the page has
always done at the same point (`away = null` in `web/src/app.js`).

`RandomIsNotSearchTest` fails against the old definition.

### C64. ~~Uninstalling did not remove the database~~ — FIXED 2026-09-17

It was not the app's doing: **Android's Auto Backup is on unless a manifest says
otherwise.** The database goes to Google Drive and comes back on the next install, so an uninstall
is undone by the platform.

Now `android:allowBackup="false"` plus a `dataExtractionRules` file that excludes every domain from
device-to-device transfer as well — the flag alone does not cover that from Android 12 onwards.

Four reasons it is off rather than selective, in `res/xml/data_extraction_rules.xml`: most of the
database is a re-downloadable copy of somebody else's archive; a restored SAF grant is dead, because
the permission belongs to the installation that asked for it; a restored database is a schema
nobody tested, since backups cross versions freely; and uninstalling is how a person says "take this
off my phone". Playlists worth keeping have an export, which is a path somebody chooses rather than
one that happens to them.

`UninstallLeavesNothingTest` reads the manifest, because a manifest attribute has no test of its own
and the alternative is finding out on a phone a second time.

### C63. ~~The empty playlist offered Browse on a phone with nothing to browse~~ — FIXED 2026-09-17

The empty screen said "Nothing here yet" and offered **Browse**, which on a fresh install opens a
list of archives that all read "no index — tap the arrow". That is where the first round of testers
stopped (`docs/BACKLOG.md` A46).

It now offers the download sheet instead, and says "Nothing to browse yet" above it — unless there
is an index or a granted folder, in which case nothing changes. A local-files-only user is not
pushed towards a 49 MB download.

**The first attempt at the rule was wrong, and shipped in a build that went out for testing.**
`CatalogueSummary.indexed` is `trackCount > 0 || isOnlineOnly`, and The Mod Archive is online-only —
so `catalogues.any { it.indexed }` is true on every install ever made, including the empty one the
offer exists for. The rule is `BrowseState.hasSomethingToBrowse` now, with rows rather than
`indexed`, and `EmptyScreenOfferTest` fails against the old form.

It also needed the controller to read the catalogue summaries and the granted folders **at
start-up**: the empty playlist has to choose before anybody has opened Browse, and until now both
were loaded only when Browse opened.

### C69. ~~Play refused every deep link: the paths did not start at the root~~ — FIXED 2026-09-18, branch

*Play Console, on the published build: "Deep link not working. Users will not go directly to your
app… Add a `/` to the beginning of the android:path attribute in the `<data>` tag."*

All **111** `pathPattern` entries began `.*\.mod`, `.*\..*\.xm` and so on. A path always begins
with `/` — a `content://` one is `/document/…`, a `file://` one is absolute, an `http(s)` one starts
at the root — and Play refuses a pattern that does not say so.

**Checked rather than assumed to be harmless.** Android's `PatternMatcher` simple glob was
reimplemented and both forms run against four real paths: a picker's content URI, a short path, a
path with an earlier dot, and a Modland URL. Every one matches the same pattern before and after,
through the same one of the three variants. The slash costs nothing and satisfies the report.

`ManifestPathPatternsTest` fails on a pattern without it. There are over a hundred, three per
extension because `pathPattern`'s `.*` does not backtrack, and one missing slash among them is not
something anybody will see by reading.

**This reaches users only in the next release**, as the report says: the links start working when
somebody updates.

### C68. ~~With no length, the bar sat at the end and the total read 0:00~~ — FIXED 2026-09-18, branch

Both halves of C67's other side, and both were waiting to be found the moment a tune honestly
reported no length.

**The bar.** `SeekBar` floors the range at a thousandth of a second so the slider cannot divide by
zero. With a real duration that floor is invisible; with none it *is* the range, so the first tick
put the thumb at the far end. It now draws an empty bar and refuses the drag — a position is asked
for as a fraction of a length, and there is no length.

**The total.** `formatTime(0.0)` is "0:00", which where a total belongs says the tune is over before
it starts. `formatTotal` prints dashes instead. Elapsed zero is still "0:00", because that one is
true.

**What is not a defect:** the tune faded at 2:30 and ended around 2:35. That is `applyFade`, still
set from game-music-emu's `play_length` on purpose (C67) — a looping tune nothing can measure fades
out somewhere rather than running until the fallback cuts it dead.

**What is still missing** is a bar that moves. The app learns the real length every time such a
tune plays to its end and then throws it away; `docs/BACKLOG.md` A50 is the proposal to
write it down.

### C67. ~~Console tunes with no length all claimed 2:30~~ — FIXED 2026-09-18, branch

**Half of it was, and the interesting half was not.**

Measured on that exact file, through this engine: **reported duration 150.0 s, last audible sample
at 29.4 s, the library ended the tune at 30.5 s.** So the tune really is thirty seconds long and
moving on was right. What was wrong was the number over it.

game-music-emu's own header says what `play_length` is:

> Length if available, otherwise intro_length+loop_length*2 if available, otherwise **a default of
> 150000** (2.5 minutes).

`durationSeconds()` returned it whatever its provenance, so every NSF, AY, KSS and GBS that states
no length — and the formats have nowhere to state one — reported exactly 2:30. A default presented
as a measurement, which is the same fault as a SID playing for ever because nothing knew its length
(C56), wearing the opposite coat.

It now asks the fields that admit to not knowing: `length` is "total length, **if file specifies
it**", and the loop fields are -1 when unknown. With none of them known the app is told nothing, and
an unmeasured tune takes the path it already had. Checked both ways on real files: an SPC with an
ID666 length still reports 120.0 s, the NSF now reports none.

`applyFade` still uses `play_length` on purpose: a looping tune nothing can measure should fade out
somewhere rather than run until the fallback cuts it dead. What changed is only what the app claims
to know.

`scripts/check-engine.mjs` builds an NSF with no length in it and fails if a duration comes back.

### C66. ~~The previous-track button drew a wedge on somebody else's phone~~ — FIXED 2026-09-18

*A tester's screenshots of 0.5.0, 2026-09-18: the control left of play drawn as a bar and a
half-filled wedge. Correct on a phone, on every emulator here, and in every browser.*

**SVG says the current point after a close returns to the start of the subpath that was closed.
Some renderers leave it at the last point drawn.** The two readings agree for every circle and
every loop that ends where it began -- which is most of an icon set, and why nothing else looked
wrong -- and disagree the moment a subpath ends somewhere else.

`SkipPrevious` was `M6 6h2v12H6zm3.5 6l8.5 6V6z`. The bar begins at (6,6) and ends at (6,18), so on
that phone the triangle started twelve units lower and drew the wedge in the screenshot.

**Five more were the same shape**, found by walking every path in both players rather than by
looking at them: `Filter` on the phone and on the page, the page's own previous and next, and the
page's pause glyph. Browsers read the spec correctly, so those were right today and fragile
tomorrow.

Fixed by moving absolutely after a close: `zM` means one thing everywhere, `zm` means two.

`scripts/check-icons.mjs` walks all 108 paths in `PlayerIcons.kt`, `web/src/app.js` and
`web/src/index.html` -- joining the fragments first, because one of the fragile ones hid its `z` at
the end of one string and its `m` at the start of the next -- and fails on any path whose shape
depends on the reading. It fails against the old `SkipPrevious`.

**Worth remembering beyond icons:** this is the first defect here that no device in this workshop
could reproduce. It was found by making the rule explicit and checking it, which is the only way
"works on mine" ever ends.

### C65. ~~The button was four pixels too short for its own label~~ — FIXED 2026-09-17

C62 scaled the pill's height with the system text size and left the **base** at 46dp. Its contents
are 12dp of padding, a 20dp icon, 2dp under it and one line of `labelSmall`, whose line box is
16sp: **50dp**. Four short, at the *default* text size, on an ordinary phone — which is why fixing
the scaling did not fix what was on the screen. A descender is the first thing to go, so it read as
"Settings" with the tail cut off its g.

56dp now for a slim pill and 76dp for a full one, both still scaled, and the slim cap raised to the
64dp the bar itself is.

**The arithmetic is a test now, not a comment.** `ActionPillFitsTest` adds up what Compose stacks
inside the pill at seven text sizes and fails if the box is smaller than its contents, or if a slim
pill grows past the bar that would then clip it. It fails against 46dp. Twice wrong by eye is
enough.

### C62. ~~Buttons cut their labels in half at a larger text size~~ — FIXED 2026-09-17

Found by the first closed-testing round, on somebody else's phone: **Przeglądaj**, **Ustawienia**
and **Playlista** drawn with their lower halves missing.

`LabelledAction` fixed its pill at 46dp (slim) or 72dp, and the label is `labelSmall` — **sp, which
grows with the system text size, inside a box in dp, which does not**. At the size that tester runs,
the icon and one line of label no longer fit in 46dp, and Compose clips rather than overflows.

The height now follows `fontScale`, so the rule it was written for still holds — one height for
every pill in a row (C47) — and that one number grows with the words. A slim pill is capped at 56dp
because `TopAppBar` is 64dp and would clip it in turn, and above 1.25x its padding gives up three
pixels rather than the descenders. A slim label is one line with ellipsis: a second line in a bar of
fixed height has nowhere to go.

`PlaylistTopBar`'s name chip already used `defaultMinSize` for exactly this reason and says so in
its own comment. The pill did not.

### C61. ~~Every notice arrived in English on a Polish phone~~ — FIXED 2026-09-17

Also from the first testing round. The screens were translated; the sentences the app *says* were
not. Sixty-one of them were built from literals in `PlaybackController` — "Nothing is indexed yet.
Index a catalogue first." landing on top of a screen that was otherwise Polish throughout.

They are resources now, in both languages, with real plurals: Polish has four plural forms to
English's two, so "Added 3 tracks" cannot be assembled from a number and a noun.

The default playlist name was the same fault one layer down. `PlaybackController` carried
`DEFAULT_PLAYLIST_NAME = "Playlist"` while `playlist_default_name` already existed, translated, and
was read only by the screen — so a new install in Polish opened on a playlist called "Playlist".
Names created from now on are translated; an existing one is the user's data and is not renamed
underneath them.

`MessagesAreTranslatedTest` fails on a literal reaching `Message(`, and fails when the two
`strings.xml` files stop naming the same strings.

### C60. ~~A search matched the whole query as one substring~~ — FIXED 2026-09-16

Both players matched the typed query as **one** substring — `LIKE '%space ninja%'` on the phone,
`title.includes(needle)` on the page — so a single separator in the middle of a filename was enough
to miss it, and tracker files are named with separators.

**It is not a fuzzy search and does not need to be.** Fuzzy matching answers typos; this is about
`_` and `-` standing where a person types a space. Splitting the query and requiring **every word,
anywhere, in any order** covers `space_ninja`, `spaceninja`, `Space Ninja` and `ninja space` alike,
and a word may be satisfied by any of the columns searched — so `4-mat space` finds 4-Mat's
`space.mod`.

**What it deliberately does not do is normalise the stored name**, so typing `spaceninja` still
misses `space_ninja`. That was measured before it was rejected. On 500,000 catalogue rows the worst
case — a search matching nothing, which is every search still being typed — goes from **36 ms to
217 ms** in SQL, or costs a normalised column and about twelve megabytes; in the browser, where the
search runs on every keystroke, from **13 ms to 68 ms**. Splitting the query costs nothing
measurable: **36.5 ms to 36.8 ms** for two words, because the extra clause short-circuits over the
same scan.

The rule is `data/SearchTerms.kt` and `rules.js`'s `searchTerms`, with ten shared cases in
`docs/rules/queue-cases.tsv` that both suites run — including the row that expects the run-on query
to miss, so the day somebody pays for the other half, a failing test says what changed.
`SearchTermsTest` runs the SQL the rule builds against a real SQLite and compares it to the rule
itself, rather than to a hand-written expectation.

### C59. ~~A SID with a known length looped for ever on the web~~ — FIXED 2026-09-16

**The 4:05 is right, and measuring it is how the real fault was found.** The tune is Response's
*Normal People*, which plays to about 3:50 while both players say 4:05. HVSC's entry for it is
`4a2ab3b1574163989e510b70e80314ff=4:05`, the MD5 matches the file byte for byte, and rendering it
a second at a time shows why: the music fades out at **3:38**, twenty-six seconds of near-silence
follow, and at **4:05 the tune begins again**. HVSC times the loop, not the last audible note, and
4:05 is that to the second. Nothing about the length was wrong.

**What was wrong was that the browser did not stop there.** C56's clock checked `duration <= 0`,
on the reasoning that a format knowing its own length announces the end by running out of audio.
That is true of a tracker module and **false of the one format the clock was written for**: a SID
never runs out — it loops. So the moment HVSC's lengths arrived and a SID finally had a duration,
the clock stopped firing for exactly the tunes it existed to stop, and the bar sat pinned at the end
while the tune played round again.

A known length is now a reason to stop rather than a reason not to look: the engine's own length
wins over the fallback, and either ends the tune. The flag is called `endedByClock` rather than
`fallbackFired`, because it never was only about the fallback. The phone was never affected — it has
always taken the known length first.

**And the first fix carried a worse bug, found  within the hour**: a SID ending walked
the queue forward about four tracks. Clearing the guard in `playAt` looked like tidying up after
the old tune and is not — `playAt` begins *loading* the next one, and the worklet plays the old one
until the bytes arrive. Its position messages keep coming across that gap, still carrying the old
tune's clock, and `duration` has just been zeroed so the bar can start at nothing — so the limit
falls back to three minutes and every one of those messages is past it. The guard reopened and the
queue advanced once per message until the fetch finished.

The guard is cleared where a tune actually starts, which is `opened` and `subsong`, and nowhere
else. Three page checks now, and two of them fail against the two rules they replaced.

### C58. ~~Re-indexing was ninety times slower than indexing~~ — FIXED 2026-09-16

**The asymmetry was the diagnosis**: the first index has nothing to clear and the second has an
archive's worth. `catalogue.clear` walked a cursor and called `cursor.delete()` on every record,
which is the obvious way to write it and two orders of magnitude slower than the alternative,
because each step is its own request through the transaction.

Measured at 20,000 records, which is the shape Modland produces: **281 ms to write them all, 26,176
ms to delete them again**. One `IDBObjectStore.delete` over the key range instead — it takes a range
as happily as a key — brings that to **48 ms**.

It looked like it hung on "sorting" because the label is set before the clear and the next one is
not set until storing begins. That is now a window of 48 ms rather than 20 seconds, so it is left
alone; a stage that flashes for a twentieth of a second is noise.

Three page checks guard the thing that made the fast version worth trusting — that the range still
deletes exactly the right rows. `modlandish:` is in there deliberately: it is the case a
hand-written prefix scan gets wrong.

### C57. ~~Search kept the last query with none of its results~~ — FIXED 2026-09-16

**Half the screen was already being cleared.** `BrowseNavigation.enteringDomain` empties `tracks`,
`groups` and everything else about where you had got to, and the query was not in that list: it was
asserted to *survive*, in a test that grouped it with the search scope and the downloaded-data
counts as "things that are not about where you are".

That grouping was the mistake. A scope is a setting and a count is a fact, but **a query is the
input that produced the results the same function has just thrown away** — so what it showed was
the previous words with nothing underneath, which reads as a search that found nothing rather than as a
screen waiting for a new one. The query is cleared with its results now, and the test that defended
the old behaviour says why it changed.

Walking back out of a folder into results does not come through `enteringDomain`, so a search still
survives being walked away from and returned to.

**And the keyboard comes up with the screen**, asked for in the same breath. Search
is the one place nobody arrives to look around — they came to type — and two taps stood between
arriving and typing.

### C56. ~~A SID never ends on the web, and does not on a fresh phone either~~ — FIXED 2026-09-16

**Measured, and the phone's advantage is a database rather than a better engine.** `SidBackend`
reports `durationSeconds() == 0.0` and always has: a SID is a 6502 program with a play routine, and
nothing in the file says when it should stop. Four Rob Hubbard tunes through the engine confirm it —
all four open and play, all four report a length of zero.

So a SID ends only where something *else* supplies the length, and on the phone that is **HVSC's
song length database**: `PlaybackController` hashes the file, asks `SongLengthStore.forMd5`, and the
position poll calls `handleTrackEnded()` once the position passes it. About 61,000 rows. The web has
none of that machinery — no MD5, no store, no watchdog — so `_pt_duration` returns 0, `render` never
runs short, no `ended` message is ever posted, and the tune runs for ever.

**The phone is not actually immune, and this is the part that matters for a release.** The HVSC
database is a **manual, opt-in download** on the Browse screen. Until it is tapped,
`openSongLengths` is empty, the duration is 0, the watchdog's `known > 0.0` is false, and a SID
plays for ever on the phone exactly as it does on the web. The string next to the button
(`song_lengths_none`) says "SID tunes will show no length", which undersells it: they also never
end, and nothing on screen connects the two. A new install is in this state.

**Three separable pieces of work**, smallest first:

1. **Say what the missing database costs.** A one-line change to `song_lengths_none` and the storage
   confirmation. Honest immediately, and it makes the other two optional rather than urgent.
2. **A fallback length for a tune nothing knows** — **decided 2026-09-15**. A setting, with a
   slider, **3 to 10 minutes, defaulting to 3**. It applies wherever nothing supplies a length, not
   only to SID: the fault is "a tune that never ends", and a `.sndh` sc68 has no entry for hangs the
   same way. The default matters more than the range — most people will never open that screen, and
   three minutes is roughly where a C64 tune's loop has said what it has to say.

   **Both players, and the phone's watchdog already exists**: `known > 0.0 && position >= known`
   simply needs `known` to fall back to the setting. The web has no watchdog at all and needs one
   built, which is the larger half of this even though it is the smaller of the two changes.
3. ~~**Song lengths on the web.**~~ **Done 2026-09-16.** The page fetches HVSC's `Songlengths.md5`
   from the same address the phone uses, parses it with the same rules, and stores it in IndexedDB.
   Browse offers the download beside the Modland and ASMA indexes — which is where anybody would look
   for it — and Settings says how many are held and lets them go.

   **Sharded 256 ways on the first two characters of the MD5**, because a hash is uniform by
   construction: 61,157 tunes become 256 rows of about 240 each, rather than 61,157 rows to write
   and 61,157 to delete. Measured end to end against the real 5.2 MB file: **61,157 tunes stored in
   256 ms, a lookup in 1 ms.**

   **The MD5 is written out**, sixty lines in `web/src/songlengths.js`, because `crypto.subtle` does
   not offer MD5 and never will — it is broken as a *security* hash, and nothing here is security.
   HVSC chose it as a key twenty years ago and a lookup has to use the key the database was written
   with. It is checked against the vectors published with the algorithm, and against the block
   boundary at 56 and 64 bytes where a hand-written one goes wrong.

   **Hashed before the bytes are handed to the worklet**, which detaches them — the same point the
   phone hashes at — and only for `.sid`, `.psid` and `.rsid`, since HVSC is a C64 collection and
   hashing a five-megabyte MP3 to learn that is waste.

   The time tokens now live in `docs/rules/queue-cases.tsv` as thirteen shared cases that both
   `RuleCasesTest` and `check-page.mjs` run, so the two parsers cannot drift. The fraction is what
   they are there for: `.5` is half a second, and the field is called milliseconds in places.

   **The fallback length stays.** HVSC knows about the C64 and nothing else, so every other format
   nobody has measured still needs an answer.

   **Its source is the one the phone uses, and the note here said otherwise for a day.**
   `hvsc.c64.org` **does** send `Access-Control-Allow-Origin: *`, on a GET and on a preflight, and it
   answers ranged requests. The 2026-09-15 measurement that said it did not was taken with `curl -I`
   — a HEAD, which that server answers without the CORS filter. `Vary: Origin` was there in the HEAD
   response all along, which is exactly the tell that a CORS decision is being made per request, and
   it was not read.

   **The lesson is the method, not the host**: CORS is a property of the request, so it has to be
   measured with the request the page will actually make. The catalogue table in
   `docs/PLAN_CATALOGUES.md` was re-measured with GET afterwards and its verdicts stand — HVSC was
   the only one the HEAD got wrong, and HVSC was not in that table.

   So the web can do what the phone does. What is left is work rather than a question: 5,205,150
   bytes to fetch with progress, a parser mirroring `data/SongLengths.kt`, storage in IndexedDB, and
   an MD5 — which the page does not currently compute at all.

**Not a defect, and asked in the same breath — SID subsongs already work.** The question asked was what
becomes of them: `SidBackend::subsongCount()` returns `info_->songs()` and `selectSubsong` is
implemented, so the phone shows the strip, the "play all subsongs" setting applies, and HVSC's entry
is a length **per subsong** — `openSongLengths.getOrNull(index)` — so switching tune switches length
too. Three of the four Hubbard files tested carry four tunes each. The web reads the count and
switches as well. The only thing wrong with SID subsongs is the thing above: without HVSC, each of
the four plays for ever instead of one after another.

### C55. ~~A `.sap` refused with "wrong file type for this emulator"~~ — FIXED 2026-09-15

**Two faults in one report, and only one of them is ours.**

*The file is damaged.* `scene register 5 menu.sap`: Modland's copy is 7,562 bytes, and its last
block claims an end address two bytes past that — so ASAP parses the header, finds a valid TYPE B tune by Jakub Karwacki, and then
cannot load the binary into 6502 memory. Restoring the two bytes makes it play at peak 0.75, which
is how the truncation was measured rather than guessed. ASMA, which validates what it accepts, does
not carry the tune at all. **Nothing was made lenient about it**: 111 `.sap` files sampled across 25
Modland authors all played, so this is one broken file in an archive, not a gap in what we accept.

*The message was ours.* `openBackend` asks the backends in order of how strongly they can claim a
file — by name, then by magic, then by content — and each one **overwrote** the reason the one
before had given. `.sap` is claimed by name (`AsapBackend::claimsName`) and ASAP refused it first;
then game-music-emu, whose `gme_identify_header` knows `SAP\r\n` and whose build here has no Atari
emulator, refused it too and spoke over ASAP on its way past. What was shown was a decoder's opinion
about a file it had no business with. **The first refusal is now the one kept**, which by the order
of the asking is always the decoder with the best claim.

`scripts/check-engine.mjs` holds it: a malformed SAP built in the check — valid header, last block
two bytes short, exactly the damage above — is refused, and the reason must name ASAP and must not
name the emulator. The same bytes under a name ASAP does not claim must quote game-music-emu, since
the rule is "whoever claimed it first", not "never say game-music-emu". Both halves fail against the
previous code.

### C54. ~~The dock was taller over Random than over the playlist~~ — FIXED 2026-09-15

The now-playing card took its height from its tallest child, and over Random that is the keep
button — an `IconButton`, which carries Material's 48dp touch target, next to a chevron that is a
bare 24dp icon. The card has a height of its own now, with room for two lines and for that target,
so the dock is the same over every screen.

### C53. ~~Leaving Random kept playing the random tune~~ — FIXED 2026-09-15

`stopPlayback` cancelled the prefetch and closed what was open, but not the **open in flight**. A
fetch already running finished after the switch and started its tune over a screen naming another.
It is cancelled with the rest now.

### C52. ~~A crash while Random advanced~~ — FIXED 2026-09-15

`IndexOutOfBoundsException: Index -1 out of bounds for length 1`, on the main thread, while a track
was playing or changing.

`advanceRandom` reads the record by index, and it **suspends twice on the way there** — filling the
queue asks the database. Start the dice again, or leave for the playlist, while it waits, and it
resumes against a record that is not its own: the cursor back at -1 and the new session's first pick
already in the list, which is the length of 1 in the message. A session counter is raised wherever a
session begins or ends and checked after every wait, and the pick is read rather than indexed.

### C51. ~~Next rolled the dice while an author's folder was on screen~~ — FIXED 2026-09-14

A digression leaves the dice's tune playing, and the transport asks about the transient track before
it asks about a results queue — so next rolled another pick while the author's list filled the
screen. Walking into the folder now sets that queue **without playing anything**, pointing at the
tune the jump was made from, and the transport prefers it while a dice is waiting. The dice keeps
its record and its cursor, and Back still returns to it.

### C50. ~~Browse's rows changed height with the words in them~~ — FIXED 2026-09-14

`DomainRow` was a Material `ListItem`, which is as tall as its content. The Random row says what the
dice is set to, and that sentence is a line longer for some scopes than for others — so the row grew,
its icon sat above centre, and every row under it moved. The rows are now a fixed height with room
for two lines, the icon centred in it, and the text ellipsised rather than allowed to push.

### C49. ~~The Random view stayed up while something else was playing~~ — FIXED 2026-09-14

Reported step by step: in Random, "more from this author", play one of that author's tunes, go
back, press next on the Random list — and the next tune of the **author's folder** plays.

The cause was as reported. Playing from a Browse list moves playback to a results queue, and
`PlayerUiState.randomMode` goes false with it — but the Random *screen* is a flag in the UI that
nothing turned off. So the record of the dice stayed on screen while `next` walked the author's
folder. A file handed over by another app already closed it; a tune played from a list did not.

It closes now on both. Going back lands on the playlist, which says what is playing, as it does for
any search result. The page never had this fault, because a Browse tune there ended the dice session
outright — which is the half that changed next: `docs/BACKLOG.md` A41 made the dice **wait** on both
sides instead, and `docs/SPEC_RANDOM.md` is what the two are held to now.

### C48. ~~The top bar squeezes the playlist's name to fit its buttons~~ — FIXED 2026-09-14

A `TopAppBar` is one fixed-height row and cannot answer that, so the playlist screen no longer uses
one. Wrapping was tried first, and it costs this: the bar changed height as Save and
Discard came and went, and the list under it jumped. `PlaylistTopBar` is **two rows of fixed
height** instead — the name across the whole width with its count beside it, and under it a slim row
of actions with Browse at the left edge and Settings at the right. Save and Discard appear beside
Settings, growing into the gap in the middle, so nothing that was on the bar moves. The pills in
that row are a shorter variant with a smaller icon: five of them cross a phone with room to spare,
which a pill wide enough for an icon beside its label could not do.

### C47. ~~Now Playing's buttons are not the same height~~ — FIXED 2026-09-14

**Two attempts.** Forcing two lines of label on every pill did make the heights equal and put the
words against the top and bottom edges, which was rejected on sight. What it is now: one height for
every pill, whatever the name, with the icon and the label centred between real margins. A long name
still wraps to two lines and changes nothing about the row.

### C46. ~~Adding ticked tracks from search closes the search~~ — FIXED 2026-09-14

### C45. ~~The seek bar's position dot is nearly invisible~~ — FIXED 2026-09-14

**It was the tunes that cannot be seeked.** There the bar drew **no** dot at all -- a deliberate
answer to "this is progress, not a control" -- and Material's disabled track colours are `onSurface`
at a third, which on this surface is a line you cannot read. So where the tune had got to could not
be seen. The dot is now drawn there too, and after one build at half size, which was rejected,
because the track is inset by the thumb's radius and a smaller circle sits higher, taking the bar
with it -- it is the same size and in the same place as the one you can drag. The colours are the
change: the dot in the playing colour, and the disabled track dimmed rather than greyed.

### C44. ~~The playlist list keeps stale counts after "Add to playlist…"~~ — FIXED 2026-09-14

**Making, renaming and deleting a playlist already re-read the list; the writes that change only its
contents did not.** `refreshPlaylists()` now follows every one of them: adding to another playlist,
appending to the one showing, and the debounced write of the queue itself.

### C43. ~~The transport is sometimes missing from the notification~~ — FIXED 2026-09-15

**One path was found and closed.** The service stopped itself whenever the state carried no current
track — and that is not the end of playback: it happens while a queue is replaced and when Random or
History hands the playlist back. Audio is the controller's, not the service's, so the music went on
without a transport; and nothing brought the service back, because a track ending and the next one
starting never passes through the buttons that ask for it. It now stops only when nothing is playing
**and** nothing is loading.

**Not proven to be the reported case**, which was not reproduced then. So `stopForegroundAndSelf`
logs; that line separates "the service stopped" from "the notification was never posted".

**The rest of it, 2026-09-15, with a repro:** enter Random, play something, go to the home screen,
and the music stops — the process taken, with no player in the notification at any point. The
service is started by the view model, and only some of its doors did it: the transport buttons and a
file handed over by another app. **Entering Random, tapping a row in its record, and playing from a
Browse list went straight to the controller**, so those tunes played with no foreground service —
nothing in the notification, and nothing telling the system this process was doing anything. Every
entry point that can make a sound opens the service now, and `ensureServiceRunning` says so where it
is defined.

### C42. ~~One file the engine refuses by throwing ends the whole session~~ — GUARDED 2026-09-14 (web), 2026-09-15 (phone)

From the browser console: a Startrekker AM file (libopenmpt: "external synthesizes instruments …
not supported"), then `uncaught exception: 1464664` from `___cxa_throw` in `engine.mjs` — and after it **no tune plays at all** until the tab is reloaded. A C++ exception
escaping `pt_open` leaves the engine unusable, and the worklet with it. Second of seven in
`docs/PLAN_ROUND_9.md`.

**The file was never reproduced, and the hole was real anyway.** Eight Startrekker AM modules off
Modland open and render with that same libopenmpt warning, and their `.nt` companions are refused
cleanly — so the warning is not the failure and the throwing file is still unknown. What was found
instead: **no entry point of the web engine caught anything**. `openBackend` guards the *choosing*
of a decoder; describing the file (inside `pt_open`), rendering, seeking, asking a length and
selecting a subsong were all unguarded, so any throw from any backend went straight out through
`___cxa_throw` — which is what the report showed, and why nothing played afterwards.

Now every `pt_*` in `player_wasm.cpp` catches, remembers the reason for `pt_last_error`, and answers
with a refusal or with silence; and `processor.js` catches whatever is left, answers `failed`, and
keeps the worklet alive. Checked on one engine instance: a refused file, then a tune that opens and
renders. **If it happens again, the page will now say what the decoder said** — which is the next
piece of evidence.

**The phone had the same hole open until 2026-09-15, and worse consequences.** `player_oboe.cpp`
was written before its web counterpart and never caught anything either: sixteen JNI entry points
and Oboe's audio callback, all calling straight through to a decoder. On the web an escaping
exception wedges a worklet and the tab has to be reloaded; through a JNI frame, or out of the
real-time callback, it is `std::terminate` — the process is gone mid-tune with no message and no
Android crash dialog worth reading. The same shape is in place now: a `guarded` helper, one
fallback per call that means what the caller already reads as failure (no handle, `false`, an empty
string, zero), and the reason to logcat.

Three of those places are not merely "the same as the web's":

* **`onAudioReady`.** The one guard that could not have been put at the boundary, because this is
  Oboe's thread and nothing above it is ours. A throw mid-render is treated as a decoder that ran
  out — the buffer is silenced and the stream stops, which is what the end of a tune already does,
  so Kotlin's poll moves to the next one.
* **`publishDescribe`.** Where the reported crash actually came from: `describe()` walks a decoder's
  instrument and sample tables, and a truncated file is where a length read past the end turns into
  a throw. An unreadable description now costs a line of metadata instead of the process.
* **`nativeOpen`.** The only guarded call with somewhere to put the reason — it already carries a
  sentence back to the screen — so a `std::bad_alloc` from a file too large for the heap, or
  anything thrown that is not a `std::exception` at all, is written into it rather than swallowed.

### C41. ~~"Add to playlist…" takes the track out of the playlist it was in~~ — FIXED 2026-09-14

Adding to another playlist took the track out of the one it was in. It must copy, never move.
`PlaybackController.addToPlaylist(targetPlaylistId, tracks)` writes only the **target**, so the
source is being rewritten somewhere else on that journey — the caller, the selection, or a stale
copy written back afterwards. It looks like losing music, so it was first of the seven.

**It was the `tracks` row, and SQLite's REPLACE.** `LibraryStore.replaceTracks` wrote each track
with `CONFLICT_REPLACE`; a REPLACE is a DELETE and an INSERT; `playlist_tracks.track_id` references
`tracks(id)` `ON DELETE CASCADE`; and `ProtracktorDatabase.onConfigure` turns foreign keys on. So
writing the track for the target playlist deleted **every** row that put it in any playlist, and the
write that followed gave it back to the target alone. A copy behaved as a move. The row is now
inserted with `CONFLICT_IGNORE` and its fields updated, so it is never deleted. Two tests in
`SchemaSqlTest` run both forms against a real SQLite: the safe one keeps the track in both
playlists, the old one empties the source.

### C40. ~~The phone's Now Playing shows only the first line of a module's message~~ — FIXED 2026-09-11

*Found 2026-09-11 while giving the page the phone's Now Playing, not by a listener.*

The engine's `describe` block is `key<TAB>value` lines, and `message` is written **last** because it
is the one value with line breaks in it (`native/engine/engine.cpp`). `NativeEngine.describe()`
splits the whole block into lines and keeps those with a tab: the message's first line survives as
`message`, and every line after it — no tab — is dropped. A line of the message that happens to hold
a tab becomes a bogus key instead. `NowPlaying.kt` draws the message monospaced "because the
alignment is part of what they say", and gets one line of it.

The page had the other half of the fault: it read every line as a key, and asked for `comment`,
which no backend writes, so it showed no message at all. **Fixed on the page on 2026-09-11**
(`describeFields` takes everything after `message<TAB>`); the phone is left for its own branch, the
fix being the same few lines in `NativeEngine.describe()`.

**Fixed the same night** on `fix/module-message`: the parsing moved out of `NativeEngine`, which
loads the native library when touched, into `DescribeBlock`, where a JVM test can reach it — and it
reads the block by the page's rule. `DescribeBlockTest` holds it to a message of several lines with
its spacing, a line of it holding a tab, and the word `message` appearing inside another value.

### C39. ~~Round 8's web bundle, first look: four faults~~ — FIXED 2026-09-11

1. **Random played picks it could not open**, and stopped on them. The index keeps only formats the
   engine plays, but a file can still be refused, packed or gone, and the page had no answer: the
   phone has always walked past those (`skipFailedRandomPick`) and the page's Random was built
   without it. Now it walks past up to eight in a row, as the phone does, and **the failed pick
   leaves the record** — the record is what played, and it did not.
2. **The seek bar hung on the last tune's position** — 1:07 — through the next download. It was only
   ever set by the engine's position reports, which do not come until the new file opens; the phone
   zeroes it the instant a track is chosen. So does the page now.
3. **The playlist chip did nothing during Random.** Deliberately — the phone hides it there — and
   the other thing was wanted: the chip looks like a way out, so it should be one. It now
   opens the sheet, and choosing a playlist ends the session and shows that one.
4. **The page never asked for the re-index it needed.** It did ask, on the root of Browse — but with
   "From the phone" showing, Browse is a single panel that explains why it is shut, and the sentence
   was not on it. It is now.

A fifth report — text-only buttons where the app has icon and label — is a standing requirement the
page did not meet, and is being done on its own branch.

### C38. ~~The Random heading showed over every panel~~ — FIXED 2026-09-11

The heading is shown and hidden with the `hidden` attribute, and styled `display: flex`. An author
`display` outranks the browser's own `[hidden] { display: none }`, so it was never hidden: "Playing at
random", the Filter button and the scope were on screen whatever the page was doing. The same was
true of the Filter row, and of the Filter button History's heading hides.

**It was suspected during round 8 and dismissed on a bad check.** A search of the stylesheet for a
`[hidden]` rule found one and stopped — but the page has only *scoped* ones, `.panel[hidden]`,
`.overlay[hidden]` and the like, each added beside an element's own `display`. The search matched
`.panel[hidden]` and reported the page covered. The next element forgot its line, as it was always
going to.

Fixed with one global `[hidden] { display: none !important }`, so the next element cannot forget.
And jsdom turned out able to see it after all: it runs the page's stylesheet through
`getComputedStyle`, and a page check now asserts that every element marked hidden is not displayed.
Run against the unfixed page it names all three.

### C37. ~~Browse threw on "From the phone" once an index was held~~ — FIXED 2026-09-11, same round

*Found by `GOAL.md` round 8 while building item 4; introduced by item 3 of the same round.*

Item 3 added a Random row to Browse's "From the phone" branch, calling the `row` helper — which was
declared with `const` further down the function. Reading a `const` before its declaration throws, so
**opening Browse on the phone's list with an index downloaded failed outright**. The page checks
never reached that state: when they opened that branch no index record existed, so the call was
skipped. A check now builds exactly that state and asserts it does not throw.

jsdom reads attributes and runs code, but it had never been put in this state, which is how the
fault passed 195 checks. A related suspicion from the same pass — that the Random heading's
`display: flex` would outrank `hidden` — was right, and was wrongly dismissed: see C38.

### C36. A next from the keyboard or a media key can be swallowed after a long press — **OPEN**

*Found 2026-09-11 by `GOAL.md` round 8, while a page check was being written, not by a listener.*

`holdToSkipFile` (`web/src/app.js`) swallows the click that follows a long press on next or
previous, so the press does not count twice. It remembers that it owes a swallow in `held`, and
forgets only on the next `pointerdown`. A finger always sends `pointerdown` first. **The arrow keys
and the media keys do not** — they press the button with `$('next').click()` — so after a long press
that ended without a click (the finger slid off the button before lifting), the next arrow or media
key press is eaten silently.

Narrow, and reproduced only in jsdom: an earlier check holds next and lifts without a click, and a
later bare `.click()` on the same button did nothing. The Random checks now tap the way a finger does
(`pointerdown`, `pointerup`, `click`) rather than clicking bare.

**Not fixed, deliberately.** The obvious repair — forget the owed swallow on `pointerleave` — is
wrong on touch, where `pointerleave` arrives *after* `pointerup` and *before* the click it was meant
to swallow, so a long press would then skip a file and step a track both. The event order has to be
settled on a real touch device before this changes. Recorded so it is not rediscovered as a mystery.

### C35. ~~A Random tune ending skipped several picks at once~~ — FIXED 2026-09-10

**A race between the end-of-track poll and the Random advance.** The poll runs every 200 ms and
skips only while `playing` is false or a load is running. The playlist and search paths call
`load()` before returning, so the next tick finds a load in progress. Random did not: `randomNext`
moves the cursor and publishes the row, then waits on the database for the next read-ahead pick,
and only after that does `playTransient` clear `playing` and start the load. Every tick inside that
wait found the same finished track, still "playing", and advanced again — one more row each time.

Every detail of the report follows from that:

- **only at a natural end** — a pressed next leaves a track that is not finished, so the poll never
  sees one;
- **five, then two, then none** — the count is the query's duration divided by 200 ms;
- **worse under a platform filter** — `format IN (…)` cannot use an index, so the scope is scanned
  and sorted whole (`docs/review-round-8.md` R7 predicted the cost, not this consequence);
- **gone on the second try** — SQLite's page cache was warm by then.

**Fixed by clearing `playing` before the advance, not after.** The poll's own comment already stated
the rule — *"every skip sets `playing = false` before the load starts"* — and this path broke it.
Not reproduced on the host: it needs the phone's database under the phone's timing, so the device
is the test.

The query itself is still unmeasured and still sorts the scope per pick. That is R7, and it is now
known to be more than a performance question.

### C34. ~~The index download choked on the zip's own tail~~ — FIXED 2026-09-10

A zip is not a gzip. After the member's deflate stream come a data descriptor, a central directory
and an end record — **103 bytes** in `allmods.zip` — and the page was handing the decompressor the
whole rest of the file.

**Node ignores those bytes and Firefox refuses them**, which is why this shipped: it was checked by
asking node, and node is not what a phone runs. The same code, the same file, the same
`DecompressionStream` API, and one of them says nothing.

Fixed by feeding exactly the member: its compressed length from the local header, or — when bit 3
of the flags says the writer did not know it yet — from the central directory, found by scanning
back for the end record. `allmods.zip` fills in the local header today; the fallback is fifteen lines
and a zip writer is allowed not to.

**And the checks now build a zip rather than trusting one.** Two of them, one with the length in
each place, asserting the bounds are computed and that the bytes after the member are not passed on.
That is the part that could be checked anywhere; the leniency that hid it could not.

### C33. ~~A Beepola tune came back as MP3~~ — FIXED 2026-09-10

`Beepola/4mat/plastic galaxy.bbsong` — `BBSONG` at offset zero, 27 KB of ZX Spectrum beeper data.
libopenmpt refuses it, correctly, and then minimp3's content guess claimed it and played at **peak
1.08**, which is clipping.

**C32 moved that guess to last and did not make it stricter**, which was half a fix.
`mp3dec_detect_buf` searches for a frame sequence *anywhere* within its scan limit, and 27 KB of
anything obligingly contains one.

**A frame has to be where an MP3 keeps one**: at the start, or at the start after an ID3v2 tag whose
length is read from its four syncsafe bytes. A sync eight kilobytes in is not evidence, it is a
coincidence.

Strict enough to refuse a real MP3 that keeps junk before its first frame, and that is the right
trade: a file called `.mp3` never reaches this code at all, because `claimsName` takes it much
earlier. This only ever judges files that arrived under some other name.

| | |
|---|---|
| `plastic galaxy.bbsong` | refused, with libopenmpt's own reason |
| 3 MPEG streams stripped of their extension | still play |
| 83 conformance streams | 70 / 10 / 3, unchanged |
| 198 console, SID, SNDH, SAP and module files | 172 played, unchanged |

**`.bbsong` is not in `SupportedFormats`**, so Modland's 105 of them were never indexed — this only
ever bit a file opened by hand or arriving in a link.

### C32. ~~MP3 stole an Impulse Tracker module~~ — FIXED 2026-09-10

Reproduced first, which took a minute: the file's first four bytes are `IMPM` and the engine
answered `format MP3 (minimp3) … peak 10.36` — clipping noise, no duration.

**The comment on the dispatch predicted this and the code did nothing about it.** It said minimp3's
detector "walks the file looking for a frame that parses, which is … exactly the test that will
eventually say yes to somebody else's sample data", and then placed that test **before**
libopenmpt. An Impulse Tracker module is tens of kilobytes of sample data; something in it parses.

**MP3 is now claimed by name first and by content only last.**

- `Mp3Backend::claimsName` takes `.mp3`, early, the same shape ASAP has for the fourteen formats it
  tells apart by extension. Nothing else here claims that name.
- `Mp3Backend::recognises` is asked **after libopenmpt has refused** — so a file that reaches it is
  one that nothing which can *prove* what it holds recognised, and a guess costs nothing.

Measured after:

| | |
|---|---|
| `!!uu !! !!.it` | **Impulse Tracker 2.14**, `!!uu !! !!`, 72 s, peak 0.41 |
| 83 MPEG conformance streams, named `.mp3` | 70 played, 10 silent, 3 refused — unchanged |
| 3 of them with **no extension at all** | 3 played — the last resort still does its job |
| 195 console, SID, SNDH, SAP and module files | 169 played, unchanged |

**The lesson is not "guess later".** It is that a comment naming a risk is not a guard against it,
and this one had the risk written down in the same commit that shipped it.

### C31. ~~Back left the file, and a long press meant the opposite of the phone's~~ — FIXED 2026-09-10

**Half the job, done in C23.** `next` was taught to walk the tunes inside a file before moving the
queue; `previous` was not, so on a `.sndh` back left the file whatever the switch said. The phone has
done both since subsongs existed — `PlaybackController.previous()` steps back a subsong when
`playAllSubsongs` and there is one behind.

**And the long press was backwards, which is worse.** On the phone `ui/PlayerDock.kt` binds a long
click to `onNextFile` and `onPreviousFile`: **past the file**, skipping whatever is left inside it.
This page had a long press step *within* the file — the opposite gesture for the opposite meaning.
Found while fixing the first half, not reported, and it would have been maddening to hit.

Both now mirror the phone exactly, forwards and backwards:

| | short press | long press |
|---|---|---|
| switch on | the next or previous **tune**, then the file | past the file |
| switch off | the next or previous **file** | past the file |

`previous` is also live now while the *file* has somewhere to go, which it was not — the button was
greyed at the top of the queue even sitting on a file's fourth tune.

### C30. ~~Six backends never said which tune they were on~~ — FIXED 2026-09-10

`Backend::currentSubsong()` returns 0 unless a backend says otherwise, and **only two of eight said
otherwise** — game-music-emu, which had to because a HES opens at a track with sound in it, and
HivelyTracker. libopenmpt, sc68, ASAP and libsidplayfp all answered zero for ever, whatever was
playing.

Nothing noticed until C23 gave the page a reason to ask. The web player takes its position from the
worklet's answer rather than from its own request — deliberately, so the chips cannot disagree with
the audio — so it believed every file was on its first tune. `next` then asked for tune 2 on every
press, and got it, from the beginning, for ever. Both halves of the report are one line of missing
code.

**The phone was never affected**: it tracks the index itself in `selectSubsong`, which is why this
survived a year of subsong work.

Fixed in the four that were silent: libopenmpt has `get_selected_subsong`, sc68 keeps the track it
was told and converts back from its own one-based numbering, ASAP keeps `song_` — and it is not
always zero even at open, because a SAP names its own default song — and libsidplayfp now remembers
what it reselected. MP3 and ZXTune hold one tune and the default is the truth for them.

**Measured** on real multi-tune files, one per backend that was broken: a 7-tune ProTracker module,
two SNDH files of 6 and 4 tunes, and a 2-song SID. Asked for tune 1, all four now say 1. Before the
fix the interface's own default was the only answer any of them could give.

### C29. ~~The page opened without a trailing slash and every relative URL missed~~ — FIXED 2026-09-10

**The cause is `/src` against `/src/`, and it is ours.** Ask this server for a directory without a
trailing slash and it served `index.html` at that URL and said nothing. The page arrives and renders
— all of that markup is static — and then every relative URL in it resolves one level too high:
`./app.js` becomes `/app.js`, which is not there.

Reproduced exactly:

```
GET /src        200  text/html          ← the page, at the wrong address
GET /app.js     404                     ← what "./app.js" then means
```

And `../lib/qrcode.js` still resolves, from `/src` and from `/src/` alike — so **the QR library
loaded and only `app.js` died**, which is why the page looked right and every control was inert.
"The QR icon does not work" is what a dead `app.js` looks like from outside.

**Fixed** with the redirect every static server does: a directory without a trailing slash answers
301 to the slash form. The fragment rides along because the browser carries it across a redirect
itself, so a shared queue link still works.

### The two things found while chasing it, and kept

**A missing asset was reported as a MIME problem.** `text/javascript` is what this server sends for
a `.js` file that exists — measured. `text/plain` was what it sent for one that does not: the
friendly 404 page. So the browser's sentence pointed at a MIME bug in a server that does not have
one, and cost an evening. A missing asset now answers with its own content type and no body, so the
browser says 404; a path with no known extension — what a person types — keeps the friendly page.

**Every 404 is logged**, because the one place this gets diagnosed is a log file on a machine in
another room. `404 /app.js` in `~/server.log` would have named the cause in one line.

**And `root` no longer follows the caller's working directory** — it comes from the script's own
location. `run.sh` and `serve-web.sh` both `cd` first, so this worked as long as nobody started the
server any other way; a systemd unit with the wrong `WorkingDirectory` made every request 404.
Verified by serving from `/tmp`.

### C28. ~~A line about one track stayed under the next~~ — FIXED 2026-09-10

The status line at the foot of Now Playing is the machine talking about the track in front of it —
what the engine answered, what a control just did. **Nothing replaced it when the track changed.**
So "Tune 2 of 2", written while walking a `.sndh`, sat there under a file that holds one tune, two
tracks later.

It is written on `opened` now, with what the engine said about *this* file: the rate, and how many
tunes are inside when that is more than one. The rate warning still wins where it fires, because a
decoder playing sharp is the more important thing to say.

A sentence about the wrong file is worse than no sentence — the same rule that made a refusal stop
Now Playing describing whatever worked last (C16).

### C27. ~~game-music-emu's fade is set and then thrown away~~ — FIXED 2026-09-09

*Found 2026-09-09 while measuring C26 — by comparing our engine against the library directly, which
is the only reason it was noticed at all.*

`GmeBackend`'s constructor sets the fade and then calls `openAtSomethingAudible()`, which calls
`gme_start_track` again — in **both** its branches, including the early return taken by every file
that has music at track 0. And `Music_Emu::clear_track_vars()`, which `start_track` runs first,
does this:

```cpp
fade_start = INT_MAX / 2 + 1;
fade_step  = 1;
```

So the fade is discarded on every console file this app opens. The comment above the `gme_set_fade`
call says exactly why it is there — *"Without a fade the last buffer stops dead"* — and that is what
happens instead.

**Measured:** the same SPC rendered until the library says the track ended runs **128.0 s** with the
fade set last and **120.1 s** through our engine. The missing eight seconds are the fade.

**Fixed** by making it one function, `applyFade()`, called after every start that begins real
playback — the two ends of `openAtSomethingAudible`, the silent-file fallback, and `rewind`, which
lost it too on every replay. `selectSubsong` already had the order right and now says so through the
same call.

Measured after, on the same corpus and the same file:

| | before | after |
|---|---|---|
| `top gear 2 - title.spc` | 120.09 s | **128.08 s** — the library's own answer to the digit |
| 145 console files through the engine | 120 played, 4 silent, 21 refused | **unchanged** |

The second row is the one that matters: a fade that ends a track could have cut short every file
that states a length, and it did not.

### C26. SPC plays at a different tempo from every other player — OPEN, not being worked on

**Ruled out: the audio path.** This is the defect the web build shipped with until 2026-09-08 —
44,100 samples playing on a 48,000 context, 8.8% fast, about a semitone and a half sharp — and it
was found by a SID sounding quicker than it should. The Android side has the same shape, and the
assumption behind it -- *"Oboe resamples if need be"* -- had never been measured.

It has now. From a device, 2026-09-09:

```
AAudioStreamBuilder_openStream() called
rate   =  44100, channels  = 2, ... sharing = SH, dir = OUTPUT
AAudioStreamBuilder_openStream() got Legacy, devIds = [3], perf = NO, burst = 2215
AAudioStreamBuilder_openStream() returns 0 = AAUDIO_OK
```

**Asked for 44,100 and given 44,100.** The stream is opened at the rate `GmeBackend` synthesises at,
so nothing is resampled and nothing plays sharp. `Player::start()` keeps the check anyway (it costs
one comparison per track and says so on screen when it fires), and on this device it says nothing.

**Measured 2026-09-09, and it is probably not tempo either.** The tune was timed against several
YouTube rips that agree with each other: **Protracktor ends at 1:49, they end at 2:02** — 11.9%
short, which is not 8.8% and not any ratio the audio path can produce.

**An SPC has no end.** It is a memory dump of a running SNES and loops for ever; where it stops is a
number in its ID666 tag, not a musical fact. `GmeBackend` reads `info_->play_length` and hands it to
`gme_set_fade`, so **our ending is the file's own metadata** and a YouTube uploader's ending is
whatever they chose. Two different end policies, compared as if they were two tempos.

**The landmark test says the "it is only the ending" answer was wrong.** The same passage
plays at **0:51.5 here and 0:58.0 there** — 12.6% fast, which agrees with the 11.9% the endings gave.
So it *is* tempo: the music genuinely runs quick, and the early ending is a consequence rather than
the cause.

**And 12.6% is not a ratio this audio path can make.** The only one available is 48,000/44,100 =
8.8%, and the device's own log shows the stream opened at 44,100 anyway. So the error is below Oboe: either
game-music-emu, or the file is not the tune the videos are playing.

**Where it splits, and neither half has been measured yet:**

- **The same `.spc` in the web player.** Same `engine.cpp`, same gme, a different host and a context
  also forced to 44,100. Fast there too, and Android is out of it entirely.
- **The same `.spc` rendered on the host at 32,000 and at 44,100.** 32,000 is gme's native SPC rate
  and needs no resampling; 44,100 goes through `Fir_Resampler`. If the two disagree musically, the
  resampler ratio is wrong. If they agree, the emulation speed is, and gme is a much less likely
  culprit than the rip.
- **The rip.** Several videos agreeing with each other is weaker evidence than it looks — they may
  share one source. Top Gear 2 also exists on Mega Drive, Amiga and PC with different music, and a
  PAL SNES capture runs 5/6 the speed of an NTSC one, which is the same size of error in the same
  direction.

### Measured on the host, 2026-09-09

The file is `top gear 2 - title.spc`, one of two reported alongside a YM the same day. **Not
confirmed as a defect**; recorded because the obvious cause has been eliminated and nobody should
pay for that again. Its ID666 tag: **length 120,000 ms, fade 7,000 ms**, dumped by
Grass-eatin'me, artist *Patrick Phelan, Ashley Bennett*.

Rendered through the vendored game-music-emu on this machine, once at its native rate and once at
the rate the app asks for:

| opened at | tag | rendered until `gme_track_ended` |
|---|---|---|
| 32,000 (no resampling) | 120,000 ms | 4,099,200 frames = **128.1 s** |
| 44,100 (through `Fir_Resampler`) | 120,000 ms | 5,644,800 frames = **128.0 s** |

Repeated with `-funsigned-char`, which is ARM's default and the difference most likely to make a
decoder behave differently on a phone: **identical to the digit**.

**So the decoder is not the problem.** 128 s against the videos' 2:02 is the right answer plus the
fade; the two rates agree with each other; the emulation does not care about char signedness. What
is left is the difference between this machine and the phone, and the only thing in that gap is
Oboe.

**And the arithmetic now fits the classic fault exactly.** 120 s played 8.8% fast is **110.3 s**, and
it measures **1:49**. The landmark pair (0:51.5 against 0:58.0) is cruder and gave 12.6%, but it
was approximate, and 8.8% would put it at 53.3 s. **8.8% is 48,000/44,100** — the one ratio
this path can produce, and the same defect the web build had.

`Player::start()` says nothing, so `getSampleRate()` returns the 44,100 that was asked for. That
does not settle it: on the Legacy AAudio path the stream reports the requested rate while
`AudioTrack` below it is supposed to do the conversion. If it is not doing it, every number here
lines up.

### Settled 2026-09-09: nothing in this app plays it fast

**The listening test first, and it is the better one.** The file was played in the web player and on the
phone at the same time, one in each ear: they stayed in step. Two decoders, two hosts,
two audio stacks, no drift over two minutes. Whatever they are doing, they are doing it identically —
so Oboe was never the difference, and the 8.8% story above is wrong.

**Then, byte for byte.** Thirty seconds of `top gear 2 - title.spc` rendered at 44,100 twice — once
through game-music-emu directly, once through `pt_render` in the wasm engine, which is the same
`GmeBackend` the phone runs:

```
5,292,032 bytes each, cmp -l → 0 differing bytes
```

**Identical.** Not "close": the same file. Our engine reproduces the library exactly, the library
plays the tune for its tagged 120 seconds, and the videos end at 2:02.

**The offset theory was rejected on listening**, 2026-09-10: the tempo is plainly different from
the video. Both were listened to; that is worth more than an inference about
title cards, and this entry stays open on that rather than being closed on an inference.

**What the measurements above actually prove is narrower than I claimed.** They prove our engine
reproduces game-music-emu exactly, and that the phone and the browser agree with each other. **They
say nothing about whether game-music-emu itself plays SPC at the speed a Super Nintendo does.**
Every number in this entry is downstream of that library, so a fault in it would be invisible to all
of them — and a self-consistent wrong answer is exactly what this evidence would look like.

**Not being worked on** (owner, 2026-09-10: *"nie robimy teraz"*). When it is picked up, the missing
measurement is the one nobody has made: the same `.spc` through a **second, independent** SPC
implementation — snes9x, bsnes, or `snes_spc` used directly rather than through gme's wrapper — and
the two waveforms lined up. That is the only test left that can tell "gme is wrong" from "the video
is not this rip", and neither the tag, the fade nor the audio path can stand in for it.

**Two tests, both now run:**

- **A tracker module against the same tune elsewhere** — right (MOD certain, XM almost).
  libopenmpt returns 0 from `preferredSampleRate()`, so its stream opens at the device's own rate
  and nothing is converted. Had the fixed-rate path been broken, this is the comparison that would
  have shown it.
- **The same file in the web player** — identical to the phone, sample for sample by ear.

**Not needed, on the evidence above, but recorded because it is still the better design.** `gme_open_data` takes the rate, so
`GmeBackend` could be opened at whatever the stream turns out to be and return 0 from
`preferredSampleRate()` — no conversion by anybody. That is not available to sc68 or ZXTune, which
synthesise at a rate they do not choose, but it removes the whole question for the console formats.

What is left, in the order worth trying:

- **A different rip.** SPC files circulate in many dumps of the same tune, and Top Gear 2 exists on
  SNES, Mega Drive, Amiga and PC with **different music per platform**. The thing remembered may not
  be the thing playing.
- **gme's SPC timing.** It resamples 32,000 to whatever it is opened at, and a fault there would be
  a fixed ratio on every SPC rather than on two. Cheap to test: the same file in the web player,
  which links the same `engine.cpp` through a context also forced to 44,100. If both sound the same,
  the decoder is consistent and the phone is exonerated twice over.
- **A stopwatch.** An 8% error is ten seconds in two minutes — audible against any independent
  recording of the same rip, and not a matter of opinion once timed.

### C25. ~~Changing the theme restarts playback~~ — FIXED 2026-09-10

**The player was innocent, and the entry said so before the cause was found.** `PlaybackController`
is an application-scoped singleton; an activity being destroyed and rebuilt does not take it, the
backend or the Oboe stream with it. The note above this one warned that somebody would try to fix
that first, and it was right to.

**It was the intent.** `recreate()` — which a theme, palette or language change calls deliberately,
so the window is rebuilt with the new one — hands the activity back **the same intent**. `onCreate`
read `openableUri(intent)` every time, so an app opened by tapping a file in a file manager found
that URI again, handed it to `playExternal`, and started the music over.

Which also explains the shape of the report: it happens after opening a file from another app and
not after launching from the icon, and nobody would think to mention the difference.

The fix is the idiomatic one: read the intent only when `savedInstanceState` is null. That is exactly
"this activity has not been here before" — a configuration change and a restore after process death
both hand back a bundle, and neither is somebody asking to open a file. In the second the controller
restores what was playing by itself.

### C24. ~~Play does nothing on a track that has reached its end~~ — FIXED 2026-09-10

**"Finished" had two meanings and only one was being asked about.** `Track.isFinished()` is set by
the engine when a backend renders a short buffer, and some never do — libsidplayfp is running a 6502
in a loop and has no idea the music is over. For those the *app* ends the track, on the length HVSC
or the file supplied, and the engine still says it is running. So play called `start()`, the decoder
resumed from a position already past that length, and the poll ended it again within two hundred
milliseconds. From outside: a button that does nothing.

The rule is now `PlayFromEnd.shouldRestart`, in its own file and pure, for the reason
`SubsongAdvance` is — the alternative is reasoning about it inside a method that needs a phone to
run. Its awkward case has a test: a duration of zero means *nobody knows the length*, not *zero
seconds long*, and without that guard every paused tune with no stated duration would restart
instead of resuming.

In the browser the same press now sends `rewind` to the worklet rather than re-fetching bytes it is
already holding.

### C23. ~~The web player's `next` skips the file, never the tune inside it~~ — FIXED 2026-09-10

A `.sid` or a `.sndh` holds several tunes; the phone knew and the page did not, so on a file with
twelve subsongs eleven were reachable only by tapping a chip.

Built as the phone's, because the phone's behaviour is the specification: a switch in Now Playing
(*"Play every tune in this file"*, kept per browser and shown only where it decides something) and a
**long press on next** that skips within the file whatever the switch says. The end of a tune now
walks the file before it walks the queue — and repeat-one is checked first, deliberately, because it
means "this tune again" and a file's other tunes are not it.

Two things fell out of doing it properly. The worklet **answers** a subsong request now, with that
tune's own duration and title: a GBS gives each track its own length, and the page had been showing
the first one's against the third one's audio — the same defect `selectSubsong` fixed on the phone.
And `opened` carries `pt_current_subsong`, which is not always zero: `GmeBackend` opens a HES or KSS
at the first track with sound in it, so assuming zero pointed the chips at a tune that was not
playing.

### C22. ~~The volume control sits under the repeat button~~ — FIXED 2026-09-10

The cause was the thing that made it work: `.volume` was positioned absolutely so it could not
decentre the transport, and `.transport` is `justify-content: space-evenly`, so `repeat` was placed
knowing nothing was there.

**The space is reserved now rather than taken.** A spacer of the same width on the left, the five
buttons spread between them, and one `--volume-width` variable that both read — so the narrow-screen
rule that hides the slider cannot leave the buttons off-centre by the width of something invisible.
Absolute positioning could keep play centred or keep the two apart, never both.

### C21. ~~The seek bar does not fill in behind the handle~~ — FIXED 2026-09-10

The only thing saying where you were was the handle. Every player fills the part already played, and
the phone does.

`::-moz-range-progress` exists in one engine and has no webkit counterpart, so the portable answer is
to paint the **track itself** with a gradient and move its stop from script — a custom property on
the input, which inherits into the pseudo-element. The volume slider gets it too, and a disabled seek
bar deliberately does not: a slider that says "you cannot move this" must not also say "you are
here".

The one trap: assigning to `.value` fires no event, so every place that sets a slider from state
paints it as well.

### C20. ~~The index promises files no backend can open~~ — HALF FIXED 2026-09-09

*Found 2026-09-08 by the wasm probe (`docs/PLAN_WEB.md` §14), and it was an **Android** defect — the
same files were refused on the phone. The web build only made it easy to run hundreds of files at
once, which is what the probe is for.*

`SupportedFormats.extensions` is what a folder scan and a catalogue index are filtered through, and
it had the same rule written into it twice already: `.gym` and `.snd` were removed because listing
them "only indexed files that cannot open". Two more were in that state and had not been noticed.

**`.ym` — 4,961 files in Modland. Fixed.** They play, 20 of 20 sampled
(`docs/PLAN_FORMATS.md` §8). The entry below is left standing because the reasoning in it was
**wrong in one clause** and that is worth keeping visible:

> `Sc68Backend::worthTrying` claims a file only on `ICE!`, `SC68` or an `SNDH` tag in the first 256
> bytes; a YM file has none, so it never reaches sc68 at all. Nor would it help: the vendored
> `file68` has no YM loader and no LHA support, and **Modland's YM files are LHA-packed** (`-lh5-`
> at offset two). So nothing here can open one, packed or not.

Everything up to the last sentence holds. The last sentence surveyed the backends we *build* and
concluded about the ones we *have*: ZXTune ships a YM/VTX decoder and `native/backends/zxtune/
CMakeLists.txt` had been excluding it, in a comment on the exclusion, for want of lhasa. The lesson
is narrow and repeats one from `docs/PLAN_FORMATS.md` §7 — **check the library's source tree, not
the list of what the build compiles**, because the second is a decision somebody made and can be
unmade.

`.vtx` came with it: 879 files, the format the original exclusion was weighed against.

**`.med` — 132 of Modland's 140. Still open.** They begin `MED\x04`, the older Amiga *Music Editor*
format. libopenmpt's loader requires `MMD` at offset zero (`Load_med.cpp:865`) and handles MMD0–MMD3
only. The other 8 are genuine OctaMED and do play. lhasa does nothing for this; it needs a decoder
nobody here has, which puts it with UADE's territory rather than with anything cheap.

**The re-index this cost.** Adding `.vtx` changed `SupportedFormats.fingerprint`, so every stored
index went stale and Modland's 40 MB is downloaded again. That price was the argument *against*
option 1 in the original entry — paying it to remove 5,093 dead rows. Paying it to gain 5,840
playable ones is the same transaction with the sign flipped.

### C19. ~~"More from this author" offered itself where it could never work~~ — FIXED 2026-09-08

**The Mod Archive is searched live and has no index at all.** It publishes no file to download, so
`catalogue_tracks` never holds a row for it; the lookup this action does could not have succeeded
for one of its tracks on any day. Worse, its search result rows carry a title, a format and a module
id and **no artist** — so there is not even a name to search by instead.

The action was gated on "did this come from a catalogue", which The Mod Archive answers yes to. It
is gated on "…from a catalogue with an index" now, and is simply absent for a live-search one, the
same way it is absent for a local file and for the same reason: there is nowhere to go.

**Two wrong messages were behind it, one of them mine from an hour earlier.** The original said
"Only tracks from an online catalogue can do that", which was untrue. Splitting that produced "not
in the ${'$'}{catalogue} index — index it to jump to the author", which is right for a Modland track whose
index was deleted and **wrong here**, because The Mod Archive cannot be indexed. Both survive, for
the cases they are actually about, behind a gate that stops either being shown to a live-search
track.

The report contained the answer: the track had been added from a search the day before. The catalogue was
in the message the whole time.

### C18. ~~A downgrade left an app that could not start~~ — FIXED 2026-09-08

*Found while adding schema 12, by reading the method next to the one being changed.*

`onDowngrade` exists so that installing an older build over a newer one recreates the database
instead of throwing on every launch — which is what SQLite's default does, and it leaves an app that
cannot be opened at all until its data is cleared by hand. **It had been doing exactly that itself
for seven versions.** It dropped a list of five tables, written when there were five, and then ran a
`CREATE` that had grown to twelve; the recreate met a `catalogues` that was still there and stopped.
Verified against a real SQLite: `[SQLITE_ERROR] table catalogues already exists`.

Nothing caught it because **a downgrade needs a device**. The tests here run migrations, and
migrations never take this path; there is no emulator in this environment (AGENTS.md §3).

**Why it had not been seen on a phone either:** Android refuses to install a lower `versionCode`
over a higher one, and `versionCode` is the commit count, so every older schema also has a smaller
number. The combination that bites is a **higher number carrying an older schema** — which is
precisely what a schema bump sitting on a branch produces while `develop` keeps moving. Schema 12
was on such a branch when this was found.

The tables are asked of `sqlite_master` now, because a hard-coded list is a claim about the schema
kept somewhere other than the schema, and it went stale the first time one was added.

**And the test earned its place immediately.** The first version dropped tables in the order
`sqlite_master` lists them and failed with `no such table: main.playlists`: `DROP TABLE` runs an
implicit delete of the table's rows, that delete resolves foreign keys, and the app turns foreign
keys on. Dropping in reverse is dependency order, because a child is always created after its
parent. That would have shipped as a second version of the same bug.

### C17. ~~Holding next skipped two files, then four~~ — FIXED 2026-09-08

**The first diagnosis was wrong and is recorded here because it was believable.** It said the 200 ms
position poll saw the outgoing decoder finish while its replacement loaded, and called
`handleTrackEnded` a second time. It cannot: every skip sets `playing = false` before it starts
loading, and the poll checks that first. The fix that came with it — the poll deciding nothing while
a load is in flight — is harmless and stays, but it never was the cause.

The cause is Compose. The dock passed `onLongClick = onNextFile.takeIf { state.canGoNextFile }`,
which builds a **new lambda on every recomposition**. `combinedClickable` keys its gesture detector
on the handlers it is given, so a new lambda tears the detector down and starts a fresh one — and a
detector started while the finger is still down begins timing a new long press from that moment.
Skipping a file changes the title, the title recomposes the dock, the dock restarts the detector,
and the detector fires again about every 500 ms for as long as the press is held. Two files for a
short hold, four for a longer one; the count follows the duration, which is what "teraz o 4" said.

`TransportButton` now takes a plain `onLongClick: () -> Unit` with a separate
`longClickEnabled: Boolean`, and wraps both handlers in `rememberUpdatedState` + `remember` so the
modifier is handed one stable lambda for the life of the button while the behaviour behind it stays
current. `DomainRow` in the browse screen had the same shape and got the same treatment.

**What to take from it:** "sometimes two" invited a race, and a race was duly found. The number
going to four when the hold got longer was the fact that did not fit, and it was in the second
report, not the first.

**And a third report that was not a defect.** With the fix installed the hold appeared to "jump
somewhere random, six forward, sometimes backwards" — shuffle was on, and had gone unnoticed. Under
shuffle the next *file* is the next one in the shuffled order, which is a jump; the short press does
exactly the same thing, which is the check that settles it in one press. Worth keeping because two
of the three reports about this button were about the play order rather than the button.

### C16. ~~One message for four different failures~~ — FIXED 2026-09-07

*Found twice in one day,  and by me, and it cost an hour each time.*

`ice.pt2` failed to **download** and the screen said nothing about the network; it played on the
second attempt. `&SFTDEMO.stc` is one file among Modland's 3,639, of which this build plays 95%, and
the screen said *"Spectrum is a format Protracktor cannot play yet"* — naming a whole platform.
Both readings sent the search somewhere useless, and in the second case the message was simply
false: the app claims the format and plays nearly all of it.

**The rule is short: never blame the format for a file we claim.** If `SupportedFormats` says the
name is playable, a failure is about that file. Four messages now, chosen by `OpenFailure` where the
choice can be tested and worded in string resources where it can be translated:

- the bytes never arrived — *"Could not download X. It may be the connection — try again."*
- no backend claims the name — *"X is a format Protracktor cannot play yet."*, which is the only
  case where naming the format is honest
- we claim it and a decoder gave a reason — the reason, attached to the file
- we claim it and nothing said why — *"X would not open. Other files of this format do play, so it
  is this one."*

The decoder's own reason is kept where there is one: it is true, and occasionally it is the only
thing that says which backend gave up. It is no longer offered as a verdict on the format.

**And the reasons are now one sentence in six voices instead of six.** The question was whether
naming ZXTune in the app was right; it is — the Information panel already credits every backend by
name, five of the six licences ask for attribution, and the library name is the one fact that makes
a report actionable, as `.stc` proved that morning. But "ZXTune" alone can read as a program the
user is missing, so every message now names the platform beside it:

> `&SFTDEMO.stc` would not open: the ZX Spectrum decoder (ZXTune) did not recognise it

The platform explains itself, the library stays as a credit. libopenmpt is the awkward one — it
throws its own exception, so its sentence is written where it is caught rather than where it is
raised. Two messages that wrapped another message ("ASAP refused it: ASAP could not load...") lost
their outer layer.

### C15. ~~The Mod Archive returns nothing~~ — FIXED 2026-09-06

Every search against The Mod Archive comes back with zero results. Modland and ASMA answer normally
in the same search, so it is this one source rather than the search.

**What has been ruled out:** not the catalogue being treated as unindexed. `CatalogueSummary.indexed`
is `trackCount > 0 || isOnlineOnly`, and The Mod Archive sets `isOnlineOnly`, so it is in the set an
empty selection expands to and reaches `ModArchive.search` normally.

**What is left to check**, in the order that costs least: whether the request is being made at all,
what the service answers, and whether the response shape it is parsed against still matches. It is
the only source that is a live call to somebody else's server, so it is also the only one that can
break without anything here changing.

**Not the blank-query change.** That skips The Mod Archive deliberately — there is no index here to
list — but the report is about typed searches.

### Worked on 2026-09-06 — the parser was fine, and two other things were not

**The scraper works.** `https://modarchive.org/index.php?request=search…&query=elysium` was fetched
with the app's own User-Agent: HTTP 200, no redirect, and the page contains two results in ordinary
`<tr>` rows. Every one of the parser's five patterns matches them. That page is now saved as
`app/src/test/resources/modarchive-search-elysium.html` and `ModArchiveSearchTest` parses it —
pinning a scraper to a real page is the only honest way to test one, and when the site changes that
test is what says so.

So the defect was somewhere else, and two candidates were found without a device:

**The failure was swallowed.** `runCatching { … }.getOrDefault(emptyList())` turned a blocked
request, a dead network and a changed page into the same answer as "this tune is not in the
archive". `search` now returns **null** when it could not ask, logs what the server said, and the
search reports "The Mod Archive could not be reached." A fact and a fault are different things and
the app was saying only one of them.

**The results were buried.** `SearchResults.combine` appended the live search last, after the
offline indexes — which return up to `PER_SOURCE_LIMIT`, two thousand rows. Forty live results at
row two thousand and one are present, correct and unreachable, which is exactly what "returns
nothing" looks like from the sofa, with nothing broken at all. Display order and de-duplication
precedence are now separate: a live result is placed with the local ones, while a duplicate is still
resolved in favour of the copy you already have.

**It came back "Nothing found for this search."** — so the request was made, the server
answered, and nothing was parsed. That rules out the network and points at the page.

Which turned up a third thing, found by asking the site rather than the code: **its "no results"
answer is not an empty page.** Searching for `zzzzqqqq` returns *"Or perhaps enjoy some of these…"*
and ten unrelated modules, each with a working download link. A parser that went looking for
download links returned all ten as matches — so this catalogue could report the wrong tunes as
readily as none. `parseSearchResults` now refuses any page without the results heading, and that
page is saved as a second test fixture.

The live search reports three outcomes now, not two: reached and read, could not reach, and answered
with something unreadable.

**Confirmed fixed on a device**: `elysium` returns its two modules. So the cause was the
burial — forty live results appended after a full catalogue page — and not the network or the
markup.

**An empty query is a separate case and stays skipped.** The archive answers one with the same
"perhaps enjoy" page: it has no way to list itself, so there is nothing to ask for. The screen used
to say "nothing found", which is a claim about the archive rather than about what we did; it now
says the source is searched live and needs a term, and only when that is the whole story.

### What The Mod Archive does not publish

**The artist is not in the search listing**, and this was checked rather than assumed: the expanded
view (`&detail=1`) adds Module ID, genre, size, channels, downloads, date and licence — and not the
author. So a result carries a blank author and a subtitle of `The Mod Archive/MOD`, where the second
part is the format because there is no folder to name; unlike Modland, this archive is flat.

Getting the artist means one request per result to the module page — forty requests for a page of
results. The shape that would work is the one the app already uses for local metadata: fetch it
lazily, when the user opens a track's information. Not started, and worth doing only if it is wanted.

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

### C8. ~~Back restored the position for one level only~~ — FIXED 2026-09-03

Reported  the same day A20 was confirmed working: *"back really does go back to where I
was, but only once — two folders down, the last level goes back and the one above starts at the top
again."*

**Cause.** Going back a level sets the new level immediately and fetches its contents
asynchronously, so for a moment the level key is the parent's while the list on screen is still the
child's. `RestorePosition` saw a non-empty list without the row it wanted, concluded the row had
gone, and threw the marker away. The real list then arrived with nothing left to restore.

The deepest level worked because its list is briefly *empty* rather than stale, and an empty list was
already treated as "not loaded yet".

**Fix.** The decision moved into `BrowseScroll.restoreFor`, which returns wait / scroll / forget, and
waits while the level is loading as well as while its list is empty. It is a separate function so it
could be tested: removing the loading check fails the test that catches this.

### C9. ~~"No backend recognised it" for files we could name~~ — FIXED 2026-09-03

Reported  2026-09-03: two `.sid` files from Modland refused with *"no backend recognised
it: error reading file"*.

**Not a SID defect.** Of Modland's 60,633 `.sid` files, **60,572 are HVSC (Commodore 64) and 61 are
"SidMon 1"** — an Amiga tracker format that happens to use the same extension. A SidMon file begins
`08 f9 00 01 00 bf e0 01`, which is 68000 machine code (`bset.b #1,$bfe001`, the Amiga "stop the
drive motor" idiom), not `PSID`. So libsidplayfp is never offered it, libopenmpt cannot parse it,
and the message is the fallback's.

**Two separate things follow, and neither is "fix SID".**

- **The message is true and useless.** The app knows the file came from Modland's `SidMon 1`
  directory: the catalogue index stores that as its format. Saying *"SidMon 1 is an Amiga format
  Protracktor cannot play yet"* costs a lookup and turns a dead end into an answer.
- **SidMon 1 needs UADE** (`docs/PLAN_FORMATS.md`, `docs/BACKLOG.md` A5). 61 files is not a reason
  to hurry, but it is a concrete instance of the Amiga gap rather than an abstract one.

**Fixed:** a catalogue track that no decoder opens now names the format the archive files it under.
The catalogue index still admits these files, because it filters by extension and that is the one
place content cannot be probed — half a million files live on somebody else's server
(`docs/ARCHITECTURE.md` §18). The format directory was information we already had and were not
using.

SidMon 1 itself still needs UADE (`docs/BACKLOG.md` A5). Sixty-one files is not a reason to hurry.

### C10. ~~The list stutters for the first ten to twenty seconds after launch~~ — NOT A DEFECT, 2026-09-03

Reported from a device, and established to **predate the scrollbar it was first blamed on** by
going back to the previous build. That mattered: it stopped the investigation looking at the new
thing.

**Cause.** Background metadata resolution opens each unidentified track to learn its real title, and
`adoptTitleFrom` wrote the playlist to disk after every one. `LibraryStore.replaceTracks` deletes
every row of the playlist and reinserts it — **two inserts per track** — so a three-hundred-track
playlist meant some six hundred inserts, roughly eight times a second, into the same database the
list was being read from, for as long as the resolution ran. Which is ten to twenty seconds.

**That explanation was wrong**, and one sentence disproved it: it still stutters for
twenty seconds on a playlist of **twenty-two** tracks. Twenty-two tracks is forty-four inserts per
write — nothing. The debounce is kept because writing the whole playlist per resolved track was
indefensible anyway, but it was not the cause.

**Second attempt, and what is actually known.** Twenty seconds for twenty-two tracks is roughly a
second each, which points at the per-track work rather than at anything cumulative: reading the
file, building a decoder, tearing it down. Two things were wrong with how that ran, and both are
fixed:

- `describe()` and `close()` were on the **caller's thread, which is the main one**. `close()`
  destroys a decoder — for sc68, an entire 68000 emulator.
- All of it ran at **default priority**. `Dispatchers.IO` competes with the UI thread on equal
  terms, so a second of native work per track is a second of contention per track. It now runs on
  one thread at `THREAD_PRIORITY_BACKGROUND`, in Android's background cgroup, where it gets a small
  share of the processor and cannot starve drawing however long it takes. The library scan uses the
  same thread for the same reason.

**And it is now measured rather than reasoned about.** Each resolution logs its own duration
(`Protracktor` tag). The first explanation here was confidently wrong; a number would have shown
that immediately.

**Third attempt, and this one came from measuring rather than reasoning.** Compared
the two screens: *Browse with a local folder of 300 tracks does not stutter; the playlist with 22
does.* That rules out the background work entirely — it runs the same either way — and points at
what the two screens do differently.

`PlaylistBody` took the whole `PlayerUiState`. `positionSeconds` is updated every **200 ms** while
anything is playing, so every row, every drag modifier and every list item recomposed **five times a
second**. Browse takes `BrowseState`, which does not tick, and stayed smooth at three hundred rows.
It now takes the track list rather than the state, so a position tick cannot reach it.

The scrollbar had the same shape of problem, smaller: it re-runs on every frame of a scroll — that
is what a scrollbar is — and was doing it with `BoxWithConstraints`, a subcomposition. It is three
weighted boxes now, which is pure layout.

**A fourth thing:** after the first twenty seconds it still stuttered a little, and
*most visibly when flinging the list hard*. A fling is a per-row cost rather than a per-second one,
so that pointed at what each row does — and every playlist row carried
`graphicsLayer { translationY = dragOffset }` unconditionally, which allocates a render node per
row, created and thrown away again for every row a fling brings past. Browse's rows have no such
modifier. At most one row is ever dragged, so the layer is now applied only to that one.

**Then the shape of it changed the question.** The fifth report is not about a per-item cost
at all:

> It stuttered. I added 200 SAP tracks — it still stuttered, but briefly. About five seconds after
> adding them it ran **better than it had with 22**. Restarted the app: swiping stutters, and after
> ten or fifteen swipes it stops.

**More items made it faster, and repeated scrolling cures it.** No amount of per-row or per-second
work behaves like that. That is a **warm-up** curve: ART interpreting until the JIT compiles the
paths, and Compose composing each composable type for the first time.

**Which makes the build type the first thing to rule out.** Every measurement so far has been on a
**debug** APK, and a debug build is `debuggable=true` — which turns off a great deal of ART's
optimisation and holds the JIT back, on top of having no R8. A release build is the honest
comparison and has never been tried.

**If it persists in release**, the answer is a **baseline profile**: this project has none, and
"janky until it warms up, then fine" is precisely what one exists to fix. Generating a real one
needs a device or emulator, so it is a run on a phone rather than a workshop one.

**Answer: the release build has none of it.** Installed and reported as *"zero stuttering
now"*. C10 was an artefact of testing on a **debug** APK, which is `debuggable=true` and gives up a
great deal of ART's optimisation for it. There is no defect in the app that ships.

**What that cost, and what it bought.** Four fixes went in chasing it, and all four were real
problems worth keeping — a full playlist rewrite per resolved track, background work at foreground
priority, the whole list recomposing five times a second while playing, and a render node allocated
per row. None of them was the cause. The cheapest check of all, "is this the debug build", came
fifth.

**The rule that follows is in `AGENTS.md`:** performance is judged on a release build. A debug build
is for finding out whether something *works*.

### C11. ~~Auto-advance raced through every tune in silence~~ — FIXED 2026-09-03

Reported  the day subsongs landed: pressing **next** moved to the following tune
correctly, but *letting one end* skipped instantly through all the remaining ones without a sound.

**Cause.** When a backend runs out, `onAudioReady` returns `Stop` and Oboe calls it no more. The
stream object is still there — it is simply never entered again. A subsong switch is handed to that
callback on purpose, because it is the only thread that touches the decoder; handed to it *after the
end*, it sat there forever. `finished_` stayed true, the poll on the Kotlin side asked for the next
tune, and the same thing happened again, all the way to the last.

**Fix.** When nothing is running the switch is applied directly and the stream is started again. The
test for "running" is **`stream_ != nullptr && !finished_`**, because the stream outlives the last
callback — which is the whole trap.

**`seek` had the same latent bug** and is fixed with it: seeking a tune that had just ended stored a
request for a callback that would never run. Nobody had reported it, because seeking a finished
track is a thing people rarely do.

### C12. ~~The notification had no skip buttons in Random~~ — FIXED 2026-09-04

Reported, who also guessed correctly that it would not be only Random.

**Cause.** The media session declared `ACTION_SKIP_TO_NEXT` and `ACTION_SKIP_TO_PREVIOUS` from
`queue.hasNext` and `queue.hasPrevious` — that is, from the **playlist**. But next does not always
walk the playlist: in Random it walks the picks, and in a search it walks the results. The app
already had `canGoNext` and `canGoPrevious` for exactly that, and the notification was not asking
them. So the buttons vanished in precisely the modes where the dock was still offering them, and
search had the same fault.

Since Android 13 the system builds a `MediaStyle` notification's buttons from the session's
`PlaybackState` rather than from the notification's own actions — which is why the
`Notification.Action`s, added unconditionally all along, were never the thing to look at.

### C13. ~~Repeat-one does not repeat a subsong~~ — FIXED 2026-09-04

Reported 2026-09-04: repeat-one did not repeat a subsong. Reading the code turns
one report into **two independent faults**, and only the first is unambiguously a bug.

**Fault 1 — `Sc68Backend::rewind()` hardcodes track 1.**

```cpp
void rewind() override {
    sc68_stop(sc68_);
    sc68_play(sc68_, 1, SC68_DEF_LOOP);   // <- always the first subsong
    ...
}
```

`restart()` is what repeat-one calls, and it goes through `rewind()`. So on a multi-tune SNDH sitting
on subsong 5, repeat-one plays **subsong 1** — it does repeat, just not the thing that was playing.
The other backends already do the right thing: ASAP replays `song_`, game-music-emu replays
`track_`, and libsidplayfp reloads the tune with its selected song. sc68 is alone in forgetting.

The fix is to remember what `selectSubsong` chose and rewind to that. It is provable on the host
through `probe_render.c` without a device: select a subsong, rewind, check which one plays.

**Fault 2 — with "play all subsongs" on, repeat-one is never consulted.**

`onTrackEnded` walks to the next subsong *before* it looks at the repeat mode:

```kotlin
if (now.playAllSubsongs && now.subsong + 1 < now.subsongCount) { selectSubsong(now.subsong + 1); return }
```

So under all-subsongs + repeat-one, every subsong but the last ignores repeat-one entirely, and the
last one then falls through to `restart()` — which on ASAP, GME and libsidplayfp loops that **last**
subsong forever, and on sc68 jumps to the first. Three backends, three behaviours, none of them
chosen.

**What repeat-one should mean here is a real question, not an oversight to patch.** "One" everywhere
else in this app means one row of the playlist, and a multi-tune file is one row. That reading says:
with all-subsongs on, the end of the last subsong goes back to the first and the whole file loops;
with all-subsongs off, the current subsong loops. It is coherent and it is a guess — it belongs in a
commit that says so, not in silence.

**Fixed 2026-09-04, both halves — and the guess in the paragraph above was wrong.**

Tested on a device the same day: with a multi-tune file, repeat-one walked on to the next subsong.
Whatever "play all" is set to, repeat-one should always repeat the one that is playing.
One to one."* **Repeat-one outranks "play all".** While it is on nothing advances, and the tune that
just ended plays again, whether it is a whole file or the fifth tune inside one.

The reading it replaces — "one" means one row of the playlist, so a multi-tune file loops from its
first tune — is defensible on paper and fails the only test that counts: with "play all" on,
pressing repeat-one still moved off the tune that was playing. **A repeat that goes somewhere
else is not a repeat.** This is the second time a subsong control was designed to mean different
things in different modes, and it was corrected to the simpler rule; the pattern is
worth naming rather than meeting again.

That correction rests on the other half, which was right: `Sc68Backend` remembers what
`selectSubsong` chose and rewinds to it — so "repeat what is playing" reaches the right tune.
`native/probe/sc68/probe_subsong_rewind.c` demonstrates the fault rather than arguing it, on the
same corpus the other sc68 probes use — six of the forty files have more than one tune, and all six
show the same thing:

```
subsongs=21 chose=11 selected=11 before=1 after=11 DEMONSTRATED
subsongs=9  chose=5  selected=5  before=1 after=5  DEMONSTRATED
```

Writing that probe found a second thing worth keeping: `sc68_play` sets the *pending* track and the
change lands when processing next runs, so asking which track is current before rendering reports
the previous one. The first version of the probe did exactly that and reported `selected=1` after
choosing 11 — which is the same mechanism as C11, met again from the other side.

The Kotlin half moved out of `onTrackEnded` into `SubsongAdvance`, which has no Android imports and
six tests. This decision has now been wrong twice in a method that needs a phone to run; it is worth
one small object that does not.

### C14. ~~The app crashed when a search found a track that was also in a playlist~~ — FIXED 2026-09-04

Reported  with the sequence, which is the ordinary one and that is the point of it:
find a Modland tune by searching, play it, add it to a playlist, search for it again.

```
java.lang.IllegalArgumentException: Key "https://modland.com/pub/modules/SNDH/Dubmood/
Tempest_fjortisfacials.sndh" was already used. If you are using LazyColumn/Row please make
sure you provide a unique key for each item.
```

A hard crash on the main thread, from `dispatchDraw` — a stack trace with nothing in it about
search, because by the time it throws the only thing left is a list drawing itself.

**`runSearch` merges four sources and de-duplicated only the first two.** The scanned library and
the playlists were guarded against each other; the catalogue indexes and The Mod Archive were then
concatenated on the end. Once a catalogue track is in a playlist it comes back from **both** the
playlist scan and the catalogue search — and for a catalogue track the id *is* its URL, so the two
are identical. The list is a `LazyColumn` keyed by track id.

Fixed in `SearchResults`, which has no Android imports and takes all four sources at once, keeping
the first occurrence of each id. Order decides which copy wins and is chosen rather than inherited:
library, playlists, catalogues, live search — a tune you already have should present itself as
yours rather than as a download. Five tests, one of them walking all six pairs of sources, because
the old code guarded exactly one of the six.

**The same shape existed on the other side and is closed too.** Nothing stopped an imported M3U
naming a tune twice, and a playlist row is keyed by id as well. `LibraryStore.replaceTracks` is the
single funnel every playlist write goes through, so the guarantee is made there once rather than
remembered at five call sites. Worth noting what the old code actually did with a repeat: the
second insert conflicted on the track id and *replaced* the first, leaving a hole in `position` and
a playlist shorter than the caller believed. Quietly wrong rather than loudly wrong, which is worse.

### C3. R9 is addressed but unmeasured

The next track is read while the current one plays and remote fetches are cached, but nobody has
measured whether that turns the original five-to-thirty second wait into nothing **on a
own library**, which is the only measurement that counts. The five-to-thirty second wait was his
complaint about another player and is real; the SMB share it was once attributed to was not
(`docs/BACKLOG.md`, R9's correction).

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

Reported  2026-09-02: opening Browse and choosing **Online catalogues** should show the
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

Reported  2026-09-02.

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

- **2026-09-06 — Search silently threw away most of its results.** A search for `.sap` and
  got 506, then `.mod` and got 308, and said it felt like too few. It was: **200 + 300 + 6** and
  **300 + 8**. Two hard caps, 200 on the scanned library and 300 on the catalogues — different
  numbers for no reason anybody wrote down — applied with nothing on screen to say a limit had been
  reached. An archive of 6,335 SAP files answering "506" reads as a broken index, not a full page.

  One cap now, 2,000, in `SearchResults` beside the rest of the search policy, and the count of
  what was left out shown above the list. The cap is not a memory limit — a `TrackRef` is small and
  the list is drawn lazily — it is the point past which the answer to "where is my tune" is a better
  query. Browse by format has no cap and is the tool for "every SAP file".

  The lesson is the one this project keeps relearning: **a number chosen once and never explained
  becomes a bug the day somebody counts.** The 12-track bound on gme's audible search went the same
  way (C9).

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

- `master` — repository base. Merging to it is the decision (`AGENTS.md` §3).
- `develop` — current work; everything below is merged into it.
- **2026-09-04, round 6** — all merged: `feature/add-feedback`, `docs/round-6-goal`,
  `feature/uade`, `fix/missing-extensions`, `docs/amp-and-reindex`, `feature/storage-you-can-free`,
  `fix/repeat-one-subsongs-c13`, `docs/play-store-readiness`, `fix/two-more-extensions`,
  `review/round-6`, `fix/review-round-6-engine`, `fix/review-round-6-storage`,
  `docs/round-6-reconcile`.
- 2026-09-04 — `feature/bigger-dock`, `feature/launcher-icon` (Codex's), merged before the round.
- **2026-09-03, round 5** — all merged: `feature/sc68-3-0-0b`, `feature/local-index`,
  `feature/cache-budget`, `feature/browse-scroll`, `fix/browse-defects`, `review/round-5`,
  `fix/review-round-5`, `fix/open-error-race`, `fix/database-singleton`,
  `fix/engine-shared-state`, `tooling/test-cache-honesty`, `docs/round-5-reconcile`.
- 2026-09-02 — `feature/asma-catalogue`, `feature/hvsc-songlengths` and the round-3 branches, merged.
- `feature/project-scaffold` — scaffolding and the Gradle skeleton. Not merged.

- `feature/play-store-readiness` — Codex's, **not merged** and left alone on instruction.

**Merged without any of it having been run on a device.** `AGENTS.md` says to wait for that test
before merging to `develop`; the exception is an unattended `/goal` run, which rounds 5 and 6 both
were. Both are therefore in `develop` unreviewed by anyone but their author.
