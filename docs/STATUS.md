# Status

Updated: 2026-09-04 — version 0.3.0, versionCode from the commit count, schema version 10

## What works

A usable player, as far as anything can be called that without a device saying so.

- **Formats**: tracker modules through libopenmpt (MOD, XM, S3M, IT and dozens more), Atari ST
  through sc68 3.0.0b (SNDH, YM, `.sc68`), Atari 8-bit through ASAP (SAP and 13 tracker formats), Commodore 64
  through libsidplayfp (PSID, RSID), seven console families through game-music-emu (NSF, GBS,
  SPC, VGM, HES, AY, KSS — measured, and opened at the first track that has sound in it, because HES
  and KSS routinely hold nothing at track 0), and the Amiga synth trackers through HivelyTracker
  (AHX, HVL). Backends sit behind one interface and are asked what they can do rather
  than assumed — libopenmpt and HivelyTracker seek, sc68 and libsidplayfp cannot, and the UI
  reflects that.
- **Ships no code it has no right to.** Of sc68's 99 replay routines the APK carries one — sc68's
  own — and fetches the rest from sc68 if the user asks (`docs/LICENSES.md`). SNDH is unaffected;
  `.sc68` waits for the download.
- **Online archives**: Modland, browsed offline from a downloaded index and fetched per track; ASMA,
  which arrives as one 20 MB archive and then needs no network at all; The Mod Archive, searched
  live. HVSC's song lengths give SID tunes the duration the format cannot carry. Fetched music is
  capped at 512 MB, least recently used first (`docs/ARCHITECTURE.md` §19).
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

Verified by the owner on a device: modules play (2026-08-31), SAP plays (2026-09-01), reordering a
playlist works (2026-09-02), and on the evening of 2026-09-02 he tested all five of round 3 —
**Random rolling on, history, "more from this author" and both shares all work**. Read-ahead was
working and fetching serially, which he heard; that is fixed.

**2026-09-04, round 6 on a device.** Indexes correctly reported themselves stale after the name list
changed, and re-indexing worked. **OctaMED `.mmd0`–`.mmd3` and Oktalyzer `.okta` play** — the 5,653
files that were always playable and never offered. Freeing and re-downloading the ASMA archive
works. Adding to a playlist reports what it did; the playlist row menus work.

**2026-09-05.** Ten things found by using it and fixed the same night: deleting the only playlist
did nothing at all, an exported list came back named after its document id, the top bar changed
shape with the playlist's name and its gaps came from three unrelated sources, and changing the
language or theme threw you out of Settings. Theme and language are segmented choices now, with a
separate switch for the wallpaper palette. All confirmed on his device.

**2026-09-04, afternoon.** The console formats confirmed on his device: HES plays, KSS mostly plays,
subsongs walk. Two faults came out of that session and are fixed — the search for an audible track
stopped after twelve tracks and missed `aleste 2.kss`, whose first tune is number 47 of 256, and the
subsong strip yanked the view back to the playing tune, making the far end of a long file unreadable
while it played.

Three things came back wrong and are fixed: **repeat-one still advanced through subsongs** (see C13
below — his rule, not mine, and the simpler one), the seek bar looked draggable on tracks that
cannot seek, and **a SID played for ever** because nothing acted on the length HVSC had been
supplying since round 4 (`docs/ARCHITECTURE.md` §14). All three merged after he confirmed them.

**Confirmed on a device by the owner:**

- modules play (2026-08-31), SAP plays (2026-09-01)
- round 3's five features — Random rolling on, history, "more from this author", both shares
  (2026-09-02)
- **2026-09-03, round 5**: **SNDH plays** — which also means `docs/review.md` R1 is genuinely fixed,
  not merely fixed as far as a probe can tell; the **local index survives a restart** and is read
  instead of rescanned; **Browse comes back to where you were**; **C6** looks right; **C7**
  confirmed — adding from a row's menu no longer closes Browse.
- **2026-09-03**: **ASMA plays** (SAP files from the archive) and a **local SID plays**, so
  libsidplayfp is confirmed on a device. Reading a local library is confirmed correct.
- **2026-09-03, late**: **subsongs work on a device.** The owner played Tactic (8 tunes) and
  confirmed the strip scrolls with many more. **C11** — auto-advance racing through every tune in
  silence — is confirmed fixed.
- **2026-09-03, evening**: the **stale-index notice** appears on Modland; the **action row** in the
  Now Playing, the **Browse** button, the **playlist counts** in both the switcher and the
  add-to dialogue, the **current playlist first**, and **history showing when** — all confirmed.
  The single **"Add to playlist…"** replacing the two add actions is confirmed too, which matters
  because it changed a route the owner had already signed off as **C7**.
- **2026-09-04, late**: **Settings works on a device** — the gear and Browse placement look right,
  System/Polish/English all switch the whole interface and survive a restart, playback survives the
  activity recreation, and deleting stored catalogue data from its new home works.

**Still not verified by anyone on a device:**

- **The two corrections from the Settings device pass:** Save and Discard now have visible labels,
  and a downloaded catalogue without an index reinforces its already explicit warning with the
  error colour. The owner confirmed that the labelled right-side actions fit on a phone; their
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
- **What a scan costs on a large library.** It works and reads correctly on the owner's phone
  (confirmed 2026-09-03). How long it takes on a library of thousands is still unmeasured.
- **The 512 MB cache ceiling**, which nobody has yet had enough cached music to reach.

## Finished

- **2026-09-08** — **Hold next to leave the file.** A press moves by tune, a hold moves by file.
  `aleste 2.kss` holds 256 of them, so leaving it with the ordinary next was 256 presses. Not a
  fourth transport button: that would be on screen always for something wanted rarely, in the row
  the owner reads while driving. Deliberately not on the notification or a headset button either —
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
  **when** each track was played (**B15**), which is what the owner wanted from it rather than a
  list that reorders itself.
- **2026-09-03** — **A catalogue index says when it is out of date.** Every index records which
  decoders built it, and one built by a different set — or by an unrecorded set, which is every
  index that exists today — says so and points at the re-index button. This is what hid 60,572 C64
  tunes from the owner: his Modland index predated libsidplayfp, and an index filtered at index
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

### C17. ~~A skip taken near the end of a tune advanced twice~~ — FIXED 2026-09-08

*Owner, 2026-09-08: "skip next (long press na next) przeskakuje czasem 2 pliki".*

**The long press did not cause it; it made it easy to hit.** `track` still points at the outgoing
decoder while the next one loads — it is closed inside that coroutine, not before it — so a skip
taken as a tune runs out leaves the 200 ms position poll looking at a finished decoder whose
replacement is already on its way. The poll then called `handleTrackEnded` and advanced the queue a
second time.

"Sometimes" is the tell: it needs the outgoing tune to be at its end. **Ordinary next had the same
race**, and pressing next as a tune runs out is not a rare thing to do — the long press only made a
skip cheap at any moment, so the window got sampled more often.

The poll now decides nothing while a load is in flight. That is one line, and finding it took
reading what "sometimes" could mean rather than looking at the gesture the owner was holding.

### C16. ~~One message for four different failures~~ — FIXED 2026-09-07

*Found twice in one day, by the owner and by me, and it cost an hour each time.*

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

**And the reasons are now one sentence in six voices instead of six.** The owner asked whether
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

*Owner, 2026-09-06, while testing the platform filter. Deferred by him to after that work.*

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
list — but the owner's report is about typed searches.

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

**The owner ran it and got "Nothing found for this search."** — so the request was made, the server
answered, and nothing was parsed. That rules out the network and points at the page.

Which turned up a third thing, found by asking the site rather than the code: **its "no results"
answer is not an empty page.** Searching for `zzzzqqqq` returns *"Or perhaps enjoy some of these…"*
and ten unrelated modules, each with a working download link. A parser that went looking for
download links returned all ten as matches — so this catalogue could report the wrong tunes as
readily as none. `parseSearchResults` now refuses any page without the results heading, and that
page is saved as a second test fixture.

The live search reports three outcomes now, not two: reached and read, could not reach, and answered
with something unreadable.

**Confirmed fixed on the owner's device**: `elysium` returns its two modules. So the cause was the
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

Reported by the owner the same day A20 was confirmed working: *"back really does go back to where I
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

Reported by the owner 2026-09-03: two `.sid` files from Modland refused with *"no backend recognised
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

Reported by the owner, who also established it **predated the scrollbar he had just been given** by
going back to the previous build. That mattered: it stopped the investigation looking at the new
thing.

**Cause.** Background metadata resolution opens each unidentified track to learn its real title, and
`adoptTitleFrom` wrote the playlist to disk after every one. `LibraryStore.replaceTracks` deletes
every row of the playlist and reinserts it — **two inserts per track** — so a three-hundred-track
playlist meant some six hundred inserts, roughly eight times a second, into the same database the
list was being read from, for as long as the resolution ran. Which is ten to twenty seconds.

**That explanation was wrong**, and the owner disproved it in one sentence: it still stutters for
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

**Third attempt, and this one was found by his measurement rather than my reasoning.** He compared
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

**A fourth thing, from him again:** after the first twenty seconds it still stuttered a little, and
*most visibly when flinging the list hard*. A fling is a per-row cost rather than a per-second one,
so that pointed at what each row does — and every playlist row carried
`graphicsLayer { translationY = dragOffset }` unconditionally, which allocates a render node per
row, created and thrown away again for every row a fling brings past. Browse's rows have no such
modifier. At most one row is ever dragged, so the layer is now applied only to that one.

**Then the shape of it changed the question.** The owner's fifth report is not about a per-item cost
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
needs a device or emulator, so it would be the owner's run rather than a workshop one.

**Answer: the release build has none of it.** The owner installed it and reported *"zero stuttering
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

Reported by the owner the day subsongs landed: pressing **next** moved to the following tune
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

Reported by the owner, who also guessed correctly that it would not be only Random.

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

Reported 2026-09-04 by the owner: *"repeat one nie działa dla subtracków"*. Reading the code turns
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

The owner tested it the same day: *"repeat one na wielościeżkowym przechodzi do kolejnych
subutworów (źle); niezależnie od play all / first only, repeat one powinno zawsze powtarzać jeden.
One to one."* **Repeat-one outranks "play all".** While it is on nothing advances, and the tune that
just ended plays again, whether it is a whole file or the fifth tune inside one.

The reading it replaces — "one" means one row of the playlist, so a multi-tune file loops from its
first tune — is defensible on paper and fails the only test that counts: with "play all" on,
pressing repeat-one still moved him off the tune he was listening to. **A repeat that goes somewhere
else is not a repeat.** This is the second time a subsong control was designed to mean different
things in different modes and the second time he corrected it to the simpler rule; the pattern is
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

Reported by the owner with the sequence, which is the ordinary one and that is the point of it:
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
measured whether that turns the owner's original five-to-thirty second wait into nothing **on his
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

- **2026-09-06 — Search silently threw away most of its results.** The owner searched `.sap` and
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

- `master` — repository base. Merging to it is the owner's decision (`AGENTS.md` §3).
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

- `feature/play-store-readiness` — Codex's, **not merged** and left alone at the owner's word.

**Merged without the owner having run any of it.** `AGENTS.md` says to wait for his device test
before merging to `develop`; the exception is an unattended `/goal` run, which rounds 5 and 6 both
were. Both are therefore in `develop` unreviewed by anyone but their author.
