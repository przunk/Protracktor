# Code review — Protracktor Android app, November 2026

**Status: complete.** Every in-scope source file was read: the native code, `engine/`, `player/`,
`data/`, `net/`, the alarms, the Compose screens and the build configuration. The tests were
surveyed, as the secondary subject they were meant to be. The coverage ledger (§10) says how each
file was read.

## 1. Header

| | |
| --- | --- |
| Reviewed branch | `develop` |
| Reviewed commit | `af2606123d0d6fc304eb3362d6823970233a86c7` (`af260612`), 2026-11-15 |
| Tree at start | clean; no untracked files; one worktree; level with `origin/develop` |
| `versionCode` at that commit | 1602 (commit count) |
| Review dates | 2026-11-15 to 2026-11-17 (the screens in `ui/` and the three alarm screens on the last day) |
| Reviewer | the agent (Claude), unattended, against `GOAL.md` |
| Method | in-scope own files read in full once, in order of risk; cross-cutting concepts compared side by side; reading, not execution, unless a finding says otherwise |

Every `file:line` reference below is valid for the reviewed commit. Kotlin paths are relative to
`app/src/main/kotlin/com/przunk/protracktor/`.

### Why this review exists

In priority order:

1. I suspect the code could have been written considerably better: several agents wrote it at
   different times and with different depth. I want to know where it repeats itself, where it is
   brittle, and where *n* things of one kind are handled *n* separate ways instead of through one
   mechanism with only the specific parts supplied per case.
2. I do not know this code well enough to trust it. It has to be robust and secure, and must not
   crash for trivial reasons, particularly where it meets native code.
3. It has been developed for months by AI only, across many context compactions. I expect traces.

### Scope

**In:** `app/src/main` (254 Kotlin files, 36 690 lines; manifest; resources that carry behaviour);
own native code that is compiled into the APK; the boundary with third-party code; build
configuration that shapes the APK (`app/build.gradle.kts`, `app/proguard-rules.pro`,
`gradle/libs.versions.toml`); the database schema and migrations; tests under `app/src/test` as a
secondary subject (262 Kotlin files, about 32 000 lines).

**Own native code, as settled at the start** from `app/notices/components.tsv` and the CMake files:
`native/engine/` (`engine.cpp`, `engine.h`, `player_oboe.cpp`, `lha_jni.cpp`, `log.h`,
`loudness.h`, `loudness_analysis.h`, `stereo_mix.h`), `native/lhasa/lha_extract.*`,
`native/backends/asap/asap_measure.*`, `native/backends/zxtune/lha_zxtune.cpp` and
`uint16_char_traits.h`, `native/backends/minimp3/minimp3.c`, and the one patch under
`native/patches/libopenmpt/`.

**Out:** `web/` and `native/engine/player_wasm.cpp`; `scripts/`; `native/probe/`; `native/vendor/`
and everything else `components.tsv` lists as third-party, except at the boundary; `store/`,
`user/`, `dist/`, `artwork/`.

### Legend

**Severity of a bug**

| level | means |
| --- | --- |
| **Critical** | loss or corruption of user data; a vulnerability reachable through remote content or another app; a crash at start-up or on the main playback path with ordinary input |
| **High** | a crash, freeze or ANR reachable in normal use or through a plausibly malformed file; wrong behaviour in a core function; a leak that degrades a session; a broken migration path |
| **Medium** | wrong behaviour in a secondary function or under uncommon conditions; an error swallowed silently; an inconsistency the app recovers from |
| **Low** | cosmetic; an edge case of negligible effect; a latent defect not reachable today, with the reason |

**Likelihood:** *common* · *plausible* · *rare* — how readily the trigger occurs.

**Evidence**

| state | means |
| --- | --- |
| **verified** | it was run, and the finding says what was run and what it showed |
| **partially verified** | a property was confirmed, not the behaviour |
| **unverified** | concluded from reading alone |

Anything about the screen, audio output or behaviour on a device is unverified by definition:
this workshop has no emulator and no device. Nothing in this review was run on a phone.

**Bug or observation.** A bug has a concrete input or state that produces a wrong outcome in the
code as it is. Fragility that has not broken yet, and anything whose mechanism could not be
established, is an observation.

**Identifiers:** `RV-B###` bugs, `RV-O###` observations, `RV-S###` suggestions, `RV-I###` ideas.
The prefix was checked against the identifiers the project documents already use and collides with
none. Numbers are not contiguous: some were given up while the findings were sorted, and none is
reused.

## 2. Summary

**Counts.** 40 bugs: 0 Critical, 1 High, 15 Medium, 24 Low. 44 observations, 17 suggestions,
9 ideas, 5 questions. Four bugs are partially verified (RV-B003, B004, B006, B033); everything
else is from reading.

**The ten things to look at first**

1. RV-B001 — a Protracktor page link may name any address; the app fetches it and plays it at once.
2. RV-B002 — choosing a subsong while paused starts the sound while the app goes on showing pause.
3. RV-B003 — a SID whose default song is not the first plays that song while the app believes it
   is on the first: wrong length, wrong strip, wrong order under "play all".
4. RV-B005 — music started from a page link runs without the foreground service.
5. RV-B034 — somebody who declined notifications is asked again, and then sent to system settings,
   at every start.
6. RV-B004 — notices from the controller ignore the language chosen in the app.
7. RV-B007 — a download that arrives but parses to nothing erases the index or table it replaces.
8. RV-B008 — scanning a folder that is no longer reachable ends the app.
9. RV-B014 — a database error in most write paths ends the app; only three areas handle one.
10. RV-B012 — indexing Modland holds the whole 40 MB list and half a million rows in memory at once.

From the screens, read last, the one to look at first is RV-B035: the QR scanner ends the app on a
device without a back camera or when the camera fails to start.

**Verdict on the three reasons**

*(a) Could it have been written better — yes, and the gap has a clear shape.* The code is not
careless: individual files are deliberate, most rules are pure and tested, and the data layer and
the alarms module are well built. What is missing is the level above the file. The one abstraction
that should carry the app's central idea — an online catalogue — covers identity and index parsing
and stops there; everything else a catalogue needs is decided by `if` chains on the id's prefix in
at least nine places (RV-O001), with AMP, Modland, The Mod Archive and UnExoticA each special-cased
inside generic code. The same holds one level down: "only the newest request may publish" exists
twelve times under twelve names (RV-O002), there are five HTTP clients (RV-O003) and five search
rules (RV-O005). `PlaybackController` is 3 825 lines and still owns playback, Random, sharing,
pairing, scanning and playlist edits (RV-O029). The screens repeat the pattern at their own scale:
the row for a tune is written three times (RV-O047), the seek bar twice (RV-O048), and one screen
takes forty-six parameters because the same ten actions are handed down by name through four
levels (RV-O031). A careful engineer would have written the same features with roughly the same
rules and far fewer places to remember; §6.1 sketches that shape.

*(b) Bugs and robustness — no critical defect found; the weak points are at the edges.* The native
boundary is better defended than I feared: every JNI entry in `player_oboe.cpp` is guarded, the
audio thread never blocks, exceptions do not escape. The exceptions to that discipline are the
findings: the LHA wrapper has no guard at all (RV-B009), `restart` skips the decoder lock
(RV-B011), a subsong switch runs on the main thread behind that lock (RV-B010). On the Kotlin side
the recurring cause is one decision: the controller's coroutine scope has no failure path, so any
exception in most `launch` blocks ends the process (RV-B008, RV-B014, RV-B007). Untrusted input
arrives through three doors — a page link, an imported list, an intent — and none restricts where
a tune may come from (RV-B001). The screens themselves are sound: state is hoisted, lists are
keyed, gestures have accessibility actions beside them. What they add is one unguarded camera start
(RV-B035), a rule that drifted between the two forms of the dock (RV-B036), and state shown by
colour alone in four places (RV-B037).

*(c) Traces of lost context — plentiful, mostly harmless, a few misleading.* Comments still
describe a threading model the engine left months ago (RV-O034); at least twenty KDoc blocks are
attached to the wrong declaration or to none (RV-O035); a "hard requirement" keeps a background
pass idle for a reason another comment in the same file says no longer holds (RV-O036); a kill
switch for AMP guards a toggle nothing can flip (RV-O021); code cites review documents that are not
in the repository (RV-O037). The fixes layered on fixes are visible mainly as special cases added
where the symptom appeared rather than where the rule lives.

**What was not reached.** The tests were surveyed, not reviewed line by line (§8). The path data
of the icons in `ui/PlayerIcons.kt` and the texts of the string resources were not read. Nothing
was run on a phone, so every finding about the screen is from reading.

## 3. Architecture as built

### Processes and entry points

One process, no `Application` class. Entry points: `MainActivity` (exported; launcher, `VIEW` for
`content`/`file`/`http(s)` with name patterns, the app link for the web page, `SEND`),
`PlaybackService` (foreground, media playback), `ScanStopReceiver`, a `FileProvider` for shares,
and the alarms module's two activities, service and receiver (direct-boot aware). UADE runs
`libuadecore.so` as a child process per opened tune.

### Wiring

`PlaybackController` is a process-wide singleton created on first use by `PlayerViewModel`,
`PlaybackService` or the alarm manager's view model. It is the composition root: it builds every
store (`data/`), `RemoteFiles`, and about twenty-five collaborators, most of them through anonymous
adapter objects that expose slices of itself. It publishes two state flows — `PlayerUiState`
(playback, queue, playlists, settings) and `BrowseState` (navigation, catalogue levels, search,
history page, download progress, storage sizes, pairing) — and four event flows. `PlayerViewModel`
forwards about a hundred calls one for one; `ProtracktorApp` passes them on as lambdas.

### Packages and the way dependencies point

| package | holds | depends on |
| --- | --- | --- |
| `engine` | JNI facade, native data unpacking | `player` (formats, platforms, stereo, loudness), `net` (replay stores) |
| `net` | catalogues, downloading, cache, pairing | `player` (`TrackRef`, `QueueLink`) |
| `data` | SQLite schema and stores, parsers | `player` (models, platforms, formats), `net` |
| `player` | controller, queue model, everything else | `engine`, `net`, `data`, `alarms.api`, `ui` (one helper) |
| `alarms` | alarm feature behind `alarms.api` | `player`, `data`, `engine` |
| `ui` | Compose screens | everything |
| root | activity, locale, `Appearance` (settings) | `player`, `ui`, `data` |

There is no layering: `engine`, `net`, `data` and `player` all import each other. `player` is a
catch-all of 104 files — queue model, browse navigation, downloads, scanning, sharing, history
views, Android adapters and text providers side by side. `alarms` is the exception: a module with a
facade, pure rules, a repository and platform adapters.

### One track, from tap to sound

1. A tap reaches `PlaybackController` through the view model; the queue model (`ActiveQueue`,
   `PlayQueue`, `PlaybackSchedule` — immutable, pure, well tested) decides which track.
2. `load()` (main thread) cancels the previous open, closes the previous native track, and reads
   the file through `TrackFiles`, which picks the source from the id's prefix.
3. `NativeEngine.open` copies the bytes into native memory; `openBackend` asks nine decoders in a
   fixed order; a `Player` wraps the chosen one.
4. On the main thread again: describe, loudness lookup, MD5, song-length lookups, audio focus,
   `start()` — which opens an Oboe stream whose callback pulls PCM from the decoder.
5. A 200 ms poll on the main thread reads published position, detects the end, and drives the
   queue.

### Threads at the native boundary

The audio callback only *tries* the decoder lock and plays silence when it is taken. Seeks run on
an IO thread under the lock. Subsong switches run on the caller's thread — the main thread — under
the same lock. Position, duration, description and subsong count are published by whichever thread
holds the decoder and read lock-free by everyone else. Closing is deferred behind a running seek
(`NativeLifetime`). `restart`, `start` and the first-play hook touch the decoder without the lock.

### Persistence

Three mechanisms: one SQLite database (hand-written helper, 23 schema versions, statements shared
between create and migrate); `SharedPreferences` for thirteen settings in `Appearance` plus language
and recent searches; a JSON ledger in device-protected storage for alarms. Files: fetched tracks in
`cacheDir/remote` under a 512 MB budget, archives and replay routines in `filesDir`.

### Cross-cutting concepts compared in this review

Online catalogues (5); format backends (9); download and cache; "latest request wins"; search;
notices and their text; settings; feature switches; length knowledge; store skeletons.

## 4. Bugs

### 4.1 Critical

None found.

### 4.2 High

### RV-B001 — A page link can name any address, and the app fetches and plays it at once
- Severity: High · Likelihood: rare · Evidence: unverified (reading)
- Where: `player/QueueLink.kt:235` (any row containing `://` becomes a track id verbatim);
  `player/PlaybackController.kt:1985` (`openLink` plays row 0 immediately);
  `net/RemoteFiles.kt:258` (the body is read whole unless the URL is AMP's);
  `app/src/main/res/xml/network_security_config.xml:20` (cleartext allowed app-wide);
  `player/AndroidTrackFileAccess.kt:24` (any other scheme is opened through the resolver).
- Mechanism: a link to `przunk.github.io/Protracktor/#…` carries a packed list of addresses. A row
  is turned into a `TrackRef` whose id is the address as written; nothing requires the address to
  belong to a catalogue. `TrackFiles.read` then fetches any `http(s)` id — plain `http` included —
  with no size limit, caches it, and hands the bytes to the decoders. A `content://` or `file://`
  row is opened through the content resolver.
- Trigger: somebody sends a link whose packed list names a file on a server of their choosing. One
  tap opens the app (the link is a verified app link), and the file is downloaded and decoded with
  no further question.
- Impact: the whole native decoder surface becomes reachable from one tap on a link. Any memory
  safety defect in a third-party decoder — outside this review — is then remotely triggerable; a
  very large response is read into memory; a plain-`http` address is fetched in the clear.
- Direction: accept only ids a catalogue owns (`Catalogue.owning(id) != null`) when reading a link;
  apply the same rule to an imported list (`player/PlaylistTransfer.kt:139`) and to `http(s)`
  intents; give every download the size bound AMP's already has; do not start a foreign tune
  without a press.
- Already known: no. `docs/OPEN_QUESTIONS.md` Q9 (playback in a separate process) discusses a
  malformed file taking the app down, not where files may come from.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** a link's tunes are played at once only when a
  catalogue serves every address (`Catalogue.serves`: its ids, and for ASMA and UnExoticA also their
  web addresses); otherwise the app asks first, naming how many tunes and which servers
  (`ForeignLinkDialog`, PL and EN), and Cancel fetches nothing -- question 3 taken on the review's
  recommendation in my absence. A `content://` or `file://` row in a link is dropped. **Not done:**
  a size bound on every download (`net/RemoteFiles.kt`, AMP only today) -- a fair cap needs the
  largest real modules measured; cleartext allowed app-wide; imported lists, which do not play by
  themselves. Checked: `LinkSafetyTest` (catalogue addresses not foreign; other servers, a private
  address and a look-alike host foreign; document and file rows dropped; the hosts named once, three
  at most), `ForeignLinkDialogTest`, and `LinkControllerTest` through the real controller (such a
  link waits with nothing played, Cancel leaves the queue as it was, Play makes the link's tunes the
  queue); each rule broken once to see a test fail. Not checked: the phone.

### 4.3 Medium

### RV-B002 — Choosing a subsong while paused starts the sound and leaves the app showing pause
- Severity: Medium · Likelihood: plausible · Evidence: unverified (reading)
- Where: `player/PlaybackController.kt:2890-2923` (`selectSubsong` never sets `playing`);
  `native/engine/player_oboe.cpp:151-154` (a switch on a stopped stream closes and starts it);
  `player/SubsongNormalization.kt:43` (`play = true` whenever a switch is requested);
  `player/PlaybackController.kt:798` (the poll skips everything while `playing` is false).
- Mechanism: pause closes the audio stream. A subsong switch on a player that is not running
  restarts the stream unconditionally. The controller updates the subsong, position and length and
  nothing else.
- Trigger: pause a multi-tune file; tap another tune in the subsong strip (or press next with
  "play all" on).
- Impact: music plays while the transport shows play; the poll is idle, so the bar does not move
  and the end is never handled — a SID then never ends; audio focus was released at pause and is
  not taken again, so it plays over a call; the notification shows paused.
- Direction: decide what a subsong tap means when paused (question 1), then make the controller
  and the engine agree: either the controller starts playback properly, or the engine is told not
  to start.
- Already known: no.

### RV-B003 — A SID that opens at its default song is treated as being on its first
- Severity: Medium · Likelihood: plausible · Evidence: partially verified — the vendored header
  states the contract (`native/vendor/sidplayfp/src/sidplayfp/SidTune.h:146`, "0 = default starting
  song"); the path through the app was traced, not run.
- Where: `native/engine/engine.cpp:1413` (`tune_.selectSong(0)`), `:1494` (`currentSubsong`
  returns `song_`, which stays 0 until a switch); `native/engine/player_oboe.cpp:295-299` (the
  published subsong); `player/PlaybackController.kt:3600-3610` (length and strip taken from it).
- Mechanism: the constructor asks libsidplayfp for the tune's own default song but records
  nothing. The app is told subsong 0. The length is then looked up for the first song.
- Trigger: any multi-song SID whose header names a start song other than the first.
- Impact: the tune is ended at another song's length (cut short, or left playing into silence);
  the strip highlights the wrong tune; under "play all" the first song is never played and the
  default one is played twice.
- Direction: record `info_->currentSong() - 1` at open, as ASAP and game-music-emu already do.
- Already known: partly. `docs/STATUS.md` C30 (the web player believed every file was on its first
  tune) fixed what a backend reports after a switch and notes that ASAP's default is not always
  zero; the SID's own default at open was left.

### RV-B004 — Notices from the controller ignore the language chosen in the app
- Severity: Medium · Likelihood: common for anyone whose app language differs from the phone's ·
  Evidence: partially verified — traced that the context is the application's and is never
  wrapped; not seen on a device.
- Where: `player/PlaybackController.kt:86` (holds `applicationContext`) and its 99 uses of
  `context.getString`/`context.resources`; `AppLocale.kt:33` (the language is applied by wrapping a
  context); the wrap is applied in `MainActivity.kt:40` and in about fifteen single places —
  `PlaybackController.kt:543` and `:1821` among them — and nowhere else.
- Mechanism: the in-app language exists only on contexts passed through `AppLocale.wrap`. The
  controller, the three text providers (`AndroidDownloadText`, `AndroidMaintenanceText`,
  `AndroidPlaylistText`) and `ScanNotification` read strings from the unwrapped application
  context, which follows the phone's language.
- Trigger: set the app to English on a Polish phone (or the reverse) and do anything that raises a
  notice: add a track, scan a folder, download an index.
- Impact: the screen is in one language and the snackbars, the default playlist name, History's
  "yesterday, 21:14" and the scan notification are in the other.
- Direction: one provider of localised text that wraps once, handed to everything that builds a
  notice (RV-S005). The test that exists for this purpose does not see it (§8).
- Already known: no.

### RV-B005 — Music started from a page link plays without the foreground service
- Severity: Medium · Likelihood: plausible · Evidence: unverified (reading)
- Where: `ui/ProtracktorApp.kt:162` (a page link goes to `viewModel.openLink`);
  `player/PlayerViewModel.kt:86` (`openLink` does not call `ensureServiceRunning`, although the
  comment above it says it does); `:255` (the rule, applied by hand at fourteen other entries).
- Mechanism: the service is started by the view model before each call that can make sound. The
  call for a link was missed. `selectSubsong` (`:109`) is the second entry without it, and with
  RV-B002 it too can start sound.
- Trigger: open a Protracktor link while the service is not running — a cold start, or after Stop
  in the notification.
- Impact: no notification, no lock-screen transport, and the process is a candidate for being
  stopped as soon as the app leaves the screen. This is the defect `docs/STATUS.md` C43 (playback
  from Random and Browse had no service) described, through a door added later.
- Direction: the controller asks for the service when playback starts, in one place (RV-S008).
- Already known: the class is (C43); this instance is not.

### RV-B006 — A malformed `%` in a Modland address ends the app
- Severity: Medium · Likelihood: rare · Evidence: partially verified — `URLDecoder.decode("%zz.mod")`
  and `("tune%")` both throw `IllegalArgumentException` on the workshop JDK (run in a scratch
  folder, 2026-11-16); the path through the app was not run.
- Where: `net/Catalogue.kt:178` and `:185` (`Modland.pathFrom` decodes each segment and lets the
  exception out; `:169` wraps the same call in `runCatching`); `player/OpenFailure.kt:102` (a
  second unguarded copy); reached from `player/QueueLink.kt:238` and from `TrackRef.canonical()`
  in `player/PlaylistTransfer.kt:139-147`.
- Mechanism: `Catalogue.owning`, `fileOf` and `sourceOf` call `pathFrom` on any id that starts with
  Modland's file base. None of the callers on the link and import paths catches.
- Trigger: a link or an M3U holding `https://modland.com/pub/modules/%zz`; or a tapped Modland
  address with a literal `%` in a file name that was not escaped — the manifest claims that host.
- Impact: an uncaught exception on the main thread — the app closes.
- Direction: `pathFrom` answers null for an address it cannot decode.
- Already known: no.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** `Modland.decodeSegment` answers null for a segment
  `URLDecoder` refuses, so such an address belongs to no catalogue; `OpenFailure.modlandFormatOf`
  uses the same function instead of its own copy. Checked: `ModlandMalformedAddressTest` (owning,
  sourceOf, fileOf, canonical and the format guess on three malformed addresses; good addresses
  still decode), failing with the guard removed. Not checked: the phone.

### RV-B007 — A download that arrives but parses to nothing erases what it replaces
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `player/LibraryDownloads.kt:186-200` (the "no rows means failure" guard at `:196` is for
  AMP only); `net/Catalogue.kt:191` (a body that is not a zip yields an empty list);
  `player/LibraryDownloads.kt:178` (ASMA's archive is overwritten before parsing);
  `data/TrackMetadataStore.kt:63`, `data/SongDbLengthStore.kt:54`, `data/FavouriteStore.kt:62` (the
  delete is committed even when no row follows).
- Mechanism: for Modland, ASMA and UnExoticA an index that yields no rows is passed to
  `replaceIndex`, which deletes every row and stamps the catalogue complete with nothing in it. The
  three tables of facts do the same inside their own transaction and only then report "empty". A
  corrupt zip throws, and `fetchCatalogue` catches only cancellation — the exception ends the app.
- Trigger: a server answers 200 with a maintenance page; UnExoticA's list or Modland's favourites —
  both read from another project's `master` branch — change shape.
- Impact: half a million indexed rows, or a table of lengths, replaced by nothing until downloaded
  again; for ASMA the 20 MB archive too.
- Direction: treat zero rows as a failed download everywhere, before anything is deleted; parse
  before storing an archive; catch what parsing throws.
- Already known: no.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** a catalogue index is parsed before anything is
  stored, a parse that throws counts as no rows, and no rows is a failed download for every
  catalogue (not only AMP): nothing is replaced and, for an archive catalogue, the archive is not
  overwritten. The three fact tables replace through one `replaceUnlessEmpty`, which rolls the
  delete back when no row was written. Checked: `LibraryDownloadsTest` (Modland and ASMA bodies that
  are not an index, zipped or not) and `EmptyDownloadKeepsTablesTest` (metadata, songdb lengths and
  favourites keep their rows; a real replacement still replaces), each failing with its guard
  removed. Not checked: the phone.

### RV-B008 — Scanning a folder that is no longer reachable ends the app
- Severity: Medium · Likelihood: plausible · Evidence: unverified (reading)
- Where: `player/PlaybackController.kt:1043-1108` (`scanFolder`, no `try`);
  `player/MediaScanner.kt:100` (`resolver.query` on the granted tree).
- Mechanism: the query throws `SecurityException` when the grant has been revoked and other
  runtime exceptions when the provider's storage is gone. Nothing between it and the coroutine
  scope catches, and the scope has no handler.
- Trigger: revoke the folder's access in system settings, or remove the card or disconnect the
  share it lives on, then press Scan.
- Impact: the app closes.
- Direction: a scan that cannot list its folder says so and marks the folder as needing access.
- Already known: no.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** `MediaScanner.listFiles` answers null when the
  provider refuses or fails to list the tree (security, argument, state and unsupported-operation
  exceptions; cancellation still propagates), and `scanFolder` then clears the progress and says the
  folder could not be read and should be added again (new `notice_scan_unreadable`, PL and EN). The
  folder is not marked in the list as needing access: that would be new state, left for later.
  Checked: `UnreadableFolderScanTest` with a provider that throws `SecurityException` and one that
  throws `IllegalStateException`, failing with the guard narrowed. Not checked: the phone; the
  notice itself (the controller path has no host test).

### RV-B009 — The LHA wrapper trusts the size an archive states and has no guard at the boundary
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `native/lhasa/lha_extract.cpp:128` (`result.resize(header->length)` before a byte is
  read); `native/engine/lha_jni.cpp` (neither function contains what is thrown; the result of
  `NewByteArray` is not checked).
- Mechanism: the uncompressed length is a 32-bit field in the member's header. A member claiming
  four gigabytes makes the vector allocate that much; `std::bad_alloc` crosses the JNI frame, which
  is `std::terminate`. Every JNI function in `player_oboe.cpp` is wrapped against exactly this;
  these two are not.
- Trigger: a damaged or hostile archive served for an UnExoticA tune.
- Impact: the process ends with no message.
- Direction: cap the stated length against the archive's own size and a ceiling; wrap both
  functions as the player's are.
- Already known: no.

### RV-B010 — A subsong switch waits on the main thread behind a running seek
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `player/PlaybackController.kt:2903` and `:329` (the switch is called on the main thread,
  directly and through `SubsongNormalization`); `native/engine/player_oboe.cpp:134` (it takes the
  decoder lock); `player/PlaybackController.kt:2870` (a seek holds that lock on an IO thread for as
  long as the emulator needs).
- Mechanism: a seek on a SID, an SNDH or a UADE tune runs the machine to the position and holds
  the lock throughout — seconds. A subsong switch asked meanwhile blocks its caller.
- Trigger: drag the seek bar far on a long SID, then tap another subsong, or let "play all" move
  on, before the seek lands.
- Impact: the interface freezes for the rest of the seek; past five seconds the system offers to
  close the app.
- Direction: switches go to the same worker a seek uses; a newer request abandons the older, as
  seeks already do among themselves.
- Already known: no.

### RV-B011 — `restart` rewinds the decoder without the lock a seek holds
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `native/engine/player_oboe.cpp:358-365` (`restart`: `stop()`, then `backend_->rewind()`
  with no lock); `:345-355` (`seek` works under the lock on another thread);
  `player/PlaybackController.kt:3085` and `:3207` (the two callers, on the main thread).
- Mechanism: stopping the stream removes the audio thread but not a seek in flight. `restart` then
  enters the same decoder a second thread is running.
- Trigger: seek to the very end of a slowly seeking tune, then pause and play before it lands
  (`PlayFromEnd` reads the position the bar shows, which is already the target); or a tune ends and
  a seek is started in the 200 ms before the poll replays it under repeat-one.
- Impact: two threads inside one emulator — the class of crash `docs/STATUS.md` C140 (a close
  under a running seek) was.
- Direction: every entry that touches the decoder takes the lock; `NativeLifetime` then has one
  thing less to protect.
- Already known: no (C140 fixed close against seek only).

### RV-B012 — Indexing Modland holds the whole list and every row in memory at once
- Severity: Medium · Likelihood: plausible on phones with a small heap limit · Evidence:
  unverified — the figures are the agent's arithmetic from the code, not a measurement.
- Where: `net/Catalogue.kt:189-216` (`Modland.parseIndex`); `data/CatalogueStore.kt:208`
  (`replaceIndex` takes the list); the same shape for AMP's 45 MB map, read as a stream but
  collected into one list (`net/Amp.kt:59-75`) and handed on whole
  (`player/StoreDownloadSources.kt:31-36`).
- Mechanism: the 40 MB list is unzipped into one string — two bytes a character, since a few names
  are not ASCII — and then into about 516 000 `CatalogueEntry` objects of four fresh strings each,
  all alive until the last insert. Roughly 200 MB of Java heap at the peak.
  `data/TrackMetadataStore.kt:54-58` describes this failure for a file a third the size and
  streams instead.
- Trigger: download Modland's index on a phone whose heap limit is 192 MB or less.
- Impact: `OutOfMemoryError` — the app closes mid-download and the index is not stored.
- Direction: read lines from the zip stream straight into the insert statement, as the metadata
  table does.
- Already known: no.

### RV-B013 — AMP's number lookup shares one read buffer between threads
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `net/AmpModuleIds.kt:70` (`private val chunk`, one per instance), `:39` (`idFor`, not
  synchronised); callers: `player/BrowseCache.kt:48` (IO thread, once per visible AMP row),
  `player/TrackFiles.kt:111` (the caller's thread — the main thread from `load()`).
- Mechanism: each lookup opens its own file but reads into the same array. Two lookups at once
  overwrite each other's line mid-parse.
- Trigger: open an AMP folder with many rows and tap a tune while the "on this phone" marks are
  still being worked out.
- Impact: the lookup answers nothing — the tune is reported as not in AMP's list — or another
  line's number, and a different module is fetched.
- Direction: a buffer per call. The lookup also does file I/O on the main thread for every AMP
  play, which a suspend function would move.
- Already known: no.

### RV-B014 — A database error in most write and read paths ends the app
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `player/PlaybackController.kt:133` (the scope: no exception handler);
  `player/PlaylistSession.kt:72-88`, `:215-229`, `:243-264`, `:128-134`, `:199-211` (restore,
  create, rename, delete, write, discard — no `catch`); `player/PlaybackController.kt:2442-2468`,
  `:2476-2488`; `player/CatalogueBrowser.kt:62-173`; `player/LocalFolderBrowser.kt:35-87`; the
  reads in the controller's `init` (`player/PlaybackController.kt:730-736`).
- Mechanism: an uncaught exception in a `launch` on a scope without a handler reaches the thread's
  default handler. History, Saved places, search and the playlist switch catch and report; the
  rest do not.
- Trigger: the disk fills while a playlist is saved; a migration fails on the first start after an
  update (the pre-flight check in `data/DatabaseAccess.kt` reads only the version, so the gate
  opens and the first store call throws — on every start).
- Impact: the app closes; with a failed migration, on each launch, with no message.
- Direction: one failure path for the scope, and stores that return a result where the caller can
  say something useful (RV-S005).
- Already known: no.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** the controller's scope is built by
  `ControllerFailures.scope`, whose handler reports any `Exception` from its work -- logged, and a
  notice on the main thread, worded for storage when it is a database or I/O failure (new
  `notice_failed`, `notice_failed_storage`, PL and EN) -- while the supervisor keeps the scope
  working; an `Error` still goes to the default handler. The stores do not yet return results a
  caller could explain better (RV-S005, left). Checked: `ControllerFailuresTest` (a database failure
  on the main thread and a state failure on IO are both reported, and the next launch runs), failing
  with the handler removed. Not checked: the phone; a failed migration on start (no host test drives
  the controller's init).

### RV-B034 — The notification permission is asked at every start, and then system settings open
- Severity: Medium · Likelihood: plausible — anyone who declines · Evidence: unverified (reading)
- Where: `ui/ProtracktorApp.kt:192-197` (a request in `LaunchedEffect(Unit)` at the top of the
  shell, on Android 13 and later); `permissions/AccessRequestCoordinator.kt:55-73` (once the dialog
  can no longer be shown, the request opens the app's notification settings instead).
- Mechanism: the coordinator is built for a request somebody pressed a button for: if the system
  will not show its dialog again, open Settings. The shell uses it unprompted each time it is
  composed — every launch, and every recreation after a theme or language change. Nothing records
  that the question was already answered; the stored flag only chooses between dialog and Settings.
- Trigger: decline the dialog on first launch (it returns on the next); decline it again; from
  then on each start opens the system's notification settings for the app.
- Impact: an app that leaves for system settings by itself whenever it is opened, for exactly the
  people who said no. The comment at `ui/ProtracktorApp.kt:190` says it is asked once.
- Direction: ask once and remember; offer the way to Settings from a visible control, not from the
  start-up path.
- Already known: no.
- **Fixed 2026-11-17, merged into `develop` 2026-11-18** (`47452328`): the start-up ask shows only
  the system dialog, and only while it was never shown; a button's request still ends in Settings.
  Checked: the permission tests with a negative control, the full suite with lint. Not checked:
  the phone -- I merged it without that check so the release need not wait.

### RV-B035 — The QR scanner ends the app when the camera cannot be started
- Severity: Medium · Likelihood: rare · Evidence: unverified (reading)
- Where: `ui/ScannerScreen.kt:150-177` (`CameraPreview`): `future.get()` at `:153` and
  `provider.bindToLifecycle(…, CameraSelector.DEFAULT_BACK_CAMERA, …)` at `:170` run in a listener
  on the main thread with nothing around them; `onDispose` at `:174` calls
  `ProcessCameraProvider.getInstance(context).get()` again. `AndroidManifest.xml:26` declares
  `android.hardware.camera.any` as not required.
- Mechanism: the manifest lets the app install on a device whose only camera faces the front;
  binding the back camera there throws `IllegalArgumentException`. A camera that fails to
  initialise makes `get()` throw `ExecutionException`. Neither is caught, and both are thrown on
  the main thread. The second `get()` in `onDispose` blocks the main thread until initialisation
  ends and throws the same exception when it failed.
- Trigger: press WEB in the More sheet with tunes in the playlist and no browser paired yet, or
  hold it ("Scan a different browser"), on a tablet or Chromebook with a front camera only; or on
  any device while the camera service is failing.
- Impact: the app closes instead of saying the camera is not available. The screen already has
  the words for a refused permission (`scan_refused`) and a way out that needs no camera ("Send as
  link"); neither is reached.
- A second, smaller defect in the same block: leaving the screen before the provider is ready
  does not cancel the listener. It then binds the camera to the activity after the screen is gone,
  and the camera stays open until the activity is destroyed.
- Direction: catch both failures and show the refused state; keep the provider from the first
  `get()` for `onDispose`; ignore the listener once disposed.
- Already known: no.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** the listener starts the camera through
  `CameraStart.attempt`, which catches the four ways a start fails; the screen then says the camera
  could not be started (new `scan_unavailable`, PL and EN) and keeps "Send as link". A listener that
  fires after the screen is gone binds nothing, and `onDispose` unbinds the provider the listener
  got instead of a second blocking `get()`. Checked: `ScannerCameraTest` -- under Robolectric, which
  has no back camera, the screen with the camera allowed threw `IllegalArgumentException: No
  available camera can be found` with the guard removed, which reproduces the crash; with it, the
  message shows. Not checked: the phone, and the late-listener case (no host test reaches it).

### 4.4 Low

### RV-B015 — A seek clears "finished" without restarting a stream that had stopped
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `native/engine/player_oboe.cpp:353`; `:163` (`running()` then answers true).
- Mechanism: at the end of a tune the callback returns Stop and the stream stays open and idle. A
  seek issued in the 200 ms before the poll notices resets the flag; nothing calls the callback
  again.
- Impact: the app shows playing and is silent until pause and play.
- Direction: a seek on a finished player restarts output, as a subsong switch does.

### RV-B016 — sc68's replay path is read once, and only one of five open paths waits for it
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `native/engine/engine.cpp:505-517` (the path is applied in a once-only initialiser);
  `player/PlaybackController.kt:73`, `:774-783` (`nativeReady`), `:1295` (its only `await`).
- Mechanism: an Atari ST file opened before `setDataPath` has run initialises sc68 with no replay
  path, for the life of the process.
- Trigger: tap an `.sndh` in a file manager on the first start after an update, while assets are
  still being unpacked.
- Impact: ICE-packed SNDH files play silence until the app is restarted.
- Direction: `load`, the scan probe and the other opens wait for `nativeReady`; or the path is
  applied whenever it is set.

### RV-B017 — A failed unpack of decoder data is recorded as done
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `engine/NativeData.kt:42` and `:99` (the version marker is written after copies whose
  failures were swallowed at `:163-168`).
- Trigger: the disk is full on the first start after an update.
- Impact: Atari ST or Amiga formats stay broken until the next version, with nothing said.
- Direction: write the marker only when every copy succeeded.

### RV-B018 — The offline renderer hands out a description the engine has not converted
- Severity: Low · Likelihood: plausible · Evidence: unverified
- Where: `native/engine/player_oboe.cpp:718` (`backend->describe()`); the player's path goes
  through `describeOf` (`:293`), which `native/engine/engine.h:176-183` calls the one way out for
  a description; used by `alarms/AlarmMusicPreparer.kt:70`.
- Mechanism: titles in CP437 or ISO-8859-1 reach `NewStringUTF` as raw bytes.
- Impact: alarm music whose title or author has letters outside ASCII is stored — and asked in
  the quiz — with damaged characters; a debuggable build aborts in CheckJNI instead.
- Direction: the renderer uses `describeOf`.

### RV-B019 — A console file whose first tune states a length never measures the others
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `native/engine/engine.cpp:914` (the measuring thread is started only if the track playing
  at first start has no length); `:1066` (a later switch only sets what is wanted).
- Impact: a later tune with no stated length keeps the approximate bar although it could be
  measured.

### RV-B020 — The favourites count includes tunes this build cannot play
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `data/FavouriteStore.kt:42` (the join has no `playable = 1`); the dice draws with it
  (`data/CatalogueStore.kt:588`).
- Mechanism: since schema version 15 the index keeps every row; the count was written when it held
  only playable ones.
- Impact: the chip shows a larger number than Random draws from — the mismatch the function's own
  comment warns against.

### RV-B021 — The total beside search results ignores the platform filter for local files
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `data/LibraryIndexStore.kt:195` (`countMatches(query)` has no platform argument, while
  `search` filters by platform). The comment at `:223` says the query has no limit; it has one.

### RV-B022 — Tracks removed from every playlist still come up in search
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `data/LibraryStore.kt:192` (only membership rows are deleted; nothing in the code deletes
  from `tracks`); `data/LibraryStore.kt:152` and `player/PlaybackController.kt:518` (`allTracks`
  is search's "playlists" source).
- Impact: a tune taken out of a playlist, or left behind by a deleted one, is still offered as the
  phone's own. Whether `tracks` is meant to be a library that outlives playlists is question 2.

### RV-B023 — Folder and file grants are never given back
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `player/MediaScanner.kt:33-35`, `:160` (a persisted grant per tree and per picked file);
  `player/LocalFolderBrowser.kt:52-60` (forgetting a folder deletes rows only).
- Mechanism: Android keeps a bounded number of persisted grants per app and drops the oldest.
- Impact: after many individually picked files, earlier ones — or a folder — silently become
  unreadable; a forgotten folder stays readable by the app.

### RV-B024 — The background title pass downloads tunes on any network
- Severity: Low · Likelihood: plausible · Evidence: unverified
- Where: `player/PlaybackController.kt:3689-3701` (rows with no author whose title equals the file
  name are read with `trackFiles.read`, which fetches `http` ids).
- Trigger: a playlist holding rows that came from a link or from an imported list of addresses.
- Impact: every such row is fetched in the background, on mobile data too, although the visible
  "cache ahead" setting defaults to Wi-Fi only.

### RV-B025 — Browse's Search does not match learned titles among "tunes below" in a folder
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `player/PlaybackController.kt:994` (reads `learnedTitles` from the controller's state,
  where it is always empty); `ui/ProtracktorApp.kt:127` (the only place it is filled, on a copy).

### RV-B026 — Deleting the replay routines leaves sc68's installed copies
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `player/LibraryMaintenance.kt:186` onwards; `player/AndroidMaintenanceFiles.kt:22`
  (`deleteAdoptedUade` exists, nothing equivalent for sc68); `engine/NativeData.kt:62` (the copies
  are made into `filesDir/sc68/Replay`).
- Impact: after "delete", Atari ST replays still play and still occupy the disk until the next
  version re-unpacks; the notice reports what the store held.

### RV-B027 — English on a Polish screen
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `player/LibraryDownloads.kt:79`, `:109`, `:114` and `player/ReplayDownloads.kt:36-37`
  (the labels in the download banner, with "done/total" appended);
  `player/PlaybackController.kt:1627`, `:1662-1663` (share subjects);
  `player/PlaylistTransfer.kt` (the default names "playlist" and "Imported").

### RV-B028 — AMP's tunes are missing from the Amiga platform where their prefix is not listed
- Severity: Low · Likelihood: plausible · Evidence: unverified
- Where: `player/Platforms.kt:238` (`wholeCatalogues` names ASMA and UnExoticA; AMP, which is
  entirely Amiga, is not there).
- Impact: such rows are absent from the Amiga chip's search, count and Random.

### RV-B029 — An interrupted replay download counts as installed
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `net/Sc68Replays.kt:87` and `net/UadePlayers.kt:144-151` (each file written as
  it arrives, not atomically); `net/UadePlayers.kt:77` (`present` is "more than none").
- Impact: a partial set shows as downloaded; the missing routines fail one format at a time; a
  truncated file is never replaced, because only missing files are copied in.

### RV-B030 — Three actions drawn as a word without an icon
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `ui/BrowseScreen.kt:685-688` (Scan folder), `:1632-1640` (Add selected), `:2025-2029`
  (Select all / none).
- Mechanism: the project rule is an icon with its name for every action outside a dialogue's
  confirm and cancel. The selection bar is the example the rule itself gives.

### RV-B031 — Two additions to another playlist at once can lose one
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `player/PlaybackController.kt:2442-2456` (read, append, replace, with no transaction or
  lock); `data/LearnedLengthStore.kt` `remember` has the same shape.

### RV-B032 — A link's packed list is inflated without a bound, on the main thread
- Severity: Low · Likelihood: rare · Evidence: unverified
- Where: `player/QueueLink.kt:246` onwards (`decode`); `runCatching` there also swallows
  `OutOfMemoryError`.
- **Fixed on `fix/review-2026-11`, 2026-11-18:** `QueueLink.decode` stops at 1,000,000 inflated
  bytes and answers null past that, catches only what bad base64 and bad deflate throw (no longer
  `OutOfMemoryError`), and always ends the inflater. Checked: `LinkSafetyTest` (a list past the
  bound is not read, one under it is), failing with the bound removed.

### RV-B033 — A ZXTune failure outside its own guards skips the decoders after it
- Severity: Low · Likelihood: rare · Evidence: partially verified — `native/vendor/zxtune/include/error.h:20`
  shows `class Error` has no base class.
- Where: `native/engine/engine.cpp:2893-2899` (`catch (const std::exception &)`), `:1870-1873`
  (calls outside the per-decoder `catch (...)`).
- Impact: such a file is reported with a generic failure and sc68, libopenmpt and UADE are never
  asked. Contained one level up, so nothing crashes.

### RV-B036 — The collapsed dock's Play is dead where the full dock's is live
- Severity: Low · Likelihood: plausible · Evidence: unverified
- Where: `ui/PlayerDock.kt:315` (`loadingTrack || current != null || transportQueue.tracks.isNotEmpty()
  || schedule.pending.isNotEmpty()`); `ui/DockCollapsed.kt:162` (`loaded != null ||
  transportQueue.tracks.isNotEmpty()`).
- Mechanism: one rule written twice; the full dock's copy gained two cases the collapsed copy did
  not. The comment above the full dock's line gives the reason for one of them: Random pressed
  with an empty playlist must still give something that can be stopped.
- Trigger: collapse the dock with nothing loaded and an empty playlist, then (a) add a tune to the
  queue from Browse — Play stays dead although the queue badge beside it counts one; or (b) start
  Random — while the first pick is being fetched the button shows Stop and cannot be pressed.
- Direction: one `canPlay` on the state, read by both.

### RV-B037 — Four controls show their state by colour alone
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `ui/PlayerDock.kt:289-297` and `:346-359` with the tint at `:434-438` (shuffle on or off;
  repeat off or whole list — the same glyph in `primary` or in `onSurfaceVariant`);
  `ui/SearchScopePanel.kt:115-123` (the three scope tiles: container colour only, and no `selected`
  state for a screen reader, which `ui/PlaylistSwitcher.kt:111` and `ui/ShellNavigation.kt:59` do
  set); `ui/SearchScopePanel.kt:194`, `:220` and `ui/BrowseScreen.kt:554-576` (filter chips without
  the tick Material's filter chip has for this: selected differs by fill).
- Mechanism: the workshop rule is that information never depends on colour only. The comment at
  `ui/PlayerDock.kt:346` says the rule is met because repeat-one has its own glyph; that covers one
  of the three distinctions the two toggles make. The subsong chips are the fifth case and are
  already in RV-O046.
- Impact: somebody who cannot tell the accent colour from grey cannot see whether shuffle is on,
  or which search scope is chosen. Screen readers are told, except for the scope tiles.
- Direction: a filled or ticked form for "on" (RV-S017).

### RV-B038 — Actions drawn as an icon without a name
- Severity: Low · Likelihood: common · Evidence: unverified
- Where: `ui/BrowseScreen.kt:1644-1655` (add the selection to another playlist — an icon between
  two named actions in the selection bar), `:761` (forget a folder), `:1908` (download or refresh a
  catalogue), `:1274`, `:1285` (history pages); `ui/StorageSection.kt:205` (delete stored data);
  `ui/DockCollapsed.kt:156` (keep the tune, in the collapsed dock).
- Mechanism: the other half of the project rule in RV-B030 — an icon alone does not say where it
  goes. Not counted: the transport controls, the three-dot button on a row, the clear and back
  buttons inside a text field, and dialogue buttons.
- Forgetting a folder is also the only removal that happens on one tap with no confirmation and
  no undo; it costs a rescan, not data (the playlists keep their tunes).

### RV-B039 — A scrollbar drag can stop by itself in a list of unequal rows
- Severity: Low · Likelihood: plausible · Evidence: unverified — inferred from the code; the
  existing test (`ScrollbarBubbleTest`) uses rows of one height
- Where: `ui/DraggableScrollbar.kt:87` (`pointerInput(total, visible)`), `:89-91` (the callbacks
  that clear `dragFraction`).
- Mechanism: the gesture handler is keyed on the number of rows currently visible. When that
  number changes the handler is restarted, which abandons the gesture in progress without calling
  `onDragEnd` or `onDragCancel`. In a list of equal rows the count is constant, because the drag
  scrolls to whole rows. In a list that mixes row heights it changes as the list moves.
- Trigger: drag the thumb through a folder that holds subfolders and tunes, or through filter
  results with the "below" heading.
- Impact: the list stops following the finger; the thumb and its bubble stay where they were until
  the next touch.
- Direction: key the handler on nothing and read the counts through `rememberUpdatedState`.

### RV-B040 — The web player address cannot be emptied
- Severity: Low · Likelihood: plausible · Evidence: unverified
- Where: `ui/SettingsScreen.kt:391-406` (`remember(webPlayer)`, saved on every keystroke);
  `AppTheme.kt:67-70` (an empty value falls back to the paired or default address), `:120-126`.
- Mechanism: deleting the last character stores "no address", the stored value is read back as
  the default, and the field is re-keyed on it.
- Impact: backspacing through the address to type another one puts the whole default back at the
  last character. Selecting everything and typing over it works.
- Direction: keep the typed text in the field and save on Done or on leaving it.

## 5. Observations

### 5.1 Duplication

- **RV-O001 — "Which source is this id" is decided by hand in at least nine places.**
  `player/TrackFiles.kt:32-49` and `:66-106`, `player/OnPhone.kt` `checkFor`,
  `data/PlaylistFile.kt` `locationOf`, `player/QueueLink.kt` `pack`/`canSend`/`open`,
  `net/WebRemote.kt` `buildJson`, `player/SelectionShare.kt`, `player/PlaybackController.kt`
  `localBytesFor`, `player/PlaybackReadAhead.kt:55`, `alarms/AlarmSourcePolicy.kt` (catalogue ids by
  name again). `amp://` is tested for in seven places in the
  controller alone. The `Catalogue` class (`net/Catalogue.kt`) covers identity and index parsing;
  how a track is read, what travels with it, whether it is on the phone, whether it may be
  prefetched, what it costs to download and which platform it is are all answered elsewhere, per
  catalogue, by name.
- **RV-O002 — "Only the newest request may publish" is written twelve times.**
  `BrowseReadOwner` (visit), `HistoryReads` (ticket), `HistoryWrites` (generation and revision),
  `BrowseWorkflow.SearchOwner` (generation), `SavedPlaces` (open ticket), `PlaylistSession`
  (activation generation, write revisions), `PlaybackReadAhead` (generation), `RandomHistory`
  (generation), the controller's `playbackEpoch`, `alarmIntent`, `seekGeneration`,
  `neighboursRequest`, `AmpAccess` (epoch), `PlaybackSchedule.generation`. Each carries its own
  comment about assigning before an immediate dispatcher re-enters.
- **RV-O003 — Five HTTP clients.** `net/RemoteFiles.kt` `attempt`, `net/ModArchive.kt` `search`,
  `net/Sc68Replays.kt` `fetch`, `net/UadePlayers.kt` `openStream`, `net/WebRemote.kt` `send` — five
  sets of timeouts, one size bound (AMP's), and the user agent that exists so archive operators can
  whitelist the app is sent by two of the four that fetch.
- **RV-O004 — Store skeletons and small helpers.** Four stores repeat `any`/`count`/`clear`/
  `replaceAll` down to an identical four-line comment (`SongLengthStore`, `SongDbLengthStore`,
  `TrackMetadataStore`, `FavouriteStore`), and disagree on whether to stream. Two `transaction`
  helpers are in use (the project's own in `data/ProtracktorDatabase.kt:99`, and androidx's).
  Bytes-to-hex is written five times and MD5 twice. Modland's file base is a constant in three
  files.
- **RV-O005 — Five search rules.** Catalogue rows (title, author; every word, accents folded),
  local index (plus file name), playlist rows in `player/LibrarySearch.kt:45` (title and file name,
  not author), `player/PlaylistSearch.kt:14` (title, file, author, source), and
  `player/HistoryFilter.kt:10` (one literal phrase, no folding). "space ninja" finds
  `space_ninja` everywhere except History. The alarm music picker adds a sixth
  (`alarms/AlarmMusicSource.kt`: title, file name and author).
- **RV-O006 — What formats exist is kept in at least six lists.** `player/SupportedFormats.kt`
  (six sets), the native name checks (`engine.cpp` `worthTrying`, `claimsName`),
  `web/src/formats.tsv` (held to the Kotlin by a test), `player/Platforms.kt` (the extensions
  again, per platform), `player/Companions.kt`, and the manifest's 37 extensions × 3 patterns × 2
  filters, which nothing checks.
- **RV-O007 — Inside `engine.cpp`.** Nine decoders in one 2 970-line unit; `openBackend` is nine
  near-identical try/catch blocks though `engine.h` speaks of a registry; 16-bit-to-float loops in
  six backends; chunk buffering in three; a background length measurer in three (ASAP's and
  game-music-emu's loops are the same code).
- **RV-O008 — Twins that drifted.** `engine/NativeData.kt` `ensureUnpacked` and
  `ensureUadeUnpacked` (one re-adopts downloads on every start, one does not); the replay
  deletion (RV-B026); length resolution in `PlaybackController.kt:3553-3558` and `:1369-1372`,
  which a comment says must stay the same; the `playable` and `platform` rules, each written in
  Kotlin and again in SQL.
- **RV-O009 — Five shapes of "a notice and its text".** `DownloadText`,
  `LibraryMaintenance.Text`, `AndroidPlaylistText`, the lambda handed to `SavedPlaces`,
  `PlaylistHeartActions.feedback` — beside about sixty direct `getString` calls in the controller.
  Two undo mechanisms: a lambda on the `Message`, and a label the screen recognises plus
  `undoRemoval()` (`ui/MessageSnackbar.kt:28`).
- **RV-O010 — An icon with its name is drawn three ways**: `LabelledAction`, `IconLabel`, and
  `Icon`, `Spacer`, `Text` inline (`ui/ScannerScreen.kt:119-133`, `ui/PlaylistScreen.kt:765`).
- **RV-O047 — The row for a tune is written three times.** `ui/PlaylistScreen.kt:519-715`
  (`TrackRow`), `ui/BrowseScreen.kt:1690-1856` (`BrowseTrackRow`) and the row inside
  `ui/QueueScreen.kt:115-141`. The first two share the same menu, the same "fetching" pulse
  (`tween(550)`, `1f` to `0.45f`, at `PlaylistScreen.kt:675` and `BrowseScreen.kt:1815`), the same
  tap and hold rules and the same leading status, and already differ: Info is the first menu entry
  in a playlist and the third in Browse; a playlist row has Delete. Which actions a tune offers is
  decided again at each call site from its id (`PlaylistScreen.kt:310`, `:315`,
  `BrowseScreen.kt:1443`, `:1447`, `ShareMenu.kt:103`) — the screen-level face of RV-O001.
- **RV-O048 — Two seek bars.** `ui/SeekBar.kt` (a `Slider`) and `ui/DockEdgeSeek.kt` (its own
  gestures) each implement scrubbing, notch haptics and the dimmed state, with their own copies of
  the constants (`SEEK_NOTCHES = 40` at `SeekBar.kt:271` and `NOTCHES = 40` at `DockEdgeSeek.kt:36`;
  `DIMMED = 0.4f` at `SeekBar.kt:252` and `EDGE_DIMMED = 0.4f` at `DockEdgeSeek.kt:147`). A seek bar
  that cannot seek is only dimmed, with nothing saying why (a tune whose decoder cannot seek). The
  system's animation scale is read separately in `ui/PlayerDock.kt:111`, `ui/DockCollapsed.kt:96`
  and `alarms/AlarmRingingScreen.kt:170`.
- **RV-O049 — Smaller twins in the screens.** Two dialogs that ask for a playlist's name:
  `ui/PlaylistSwitcher.kt:259` (capitalised words, Done saves) and `ui/AddToPlaylistDialog.kt:52`
  (neither). Two private `Int.dp` extensions shadow Compose's (`ui/SwipeableSnackbar.kt:81`). Fixed
  colours outside the theme in `ui/QueueSwipeRow.kt:159` (green and red behind a swipe; icon and
  name carry the meaning, so it is not a colour-only case) and `ui/FloatingControlsPanel.kt:96`.

### 5.2 Brittleness

- **RV-O011 — The engine describes a tune as text and the app parses it ad hoc.** `subsong`,
  `subsongs`, `seekable == "0"`, `ends_at`, a format ending in `(UADE)`
  (`player/PlaybackController.kt:900` decides behaviour on it), `title`, `artist`, `year` are read
  in the controller, `PlayerUiState`, `ReleaseYear` and the alarms.
- **RV-O012 — Rules kept as lists to remember.** The service start (RV-B005);
  `player/QueueSourceNavigation.kt:29` (about 28 fields of `BrowseState` copied by hand);
  `player/DownloadPlan.kt:79` (`else -> 0` for a catalogue nobody listed); the 28 `inputs.file`
  lines of the test task in `app/build.gradle.kts`.
- **RV-O013 — Adding a catalogue touches about a dozen places by hand**: `Catalogue.known`,
  `DownloadSizes`, `DownloadPlan` (twice), `BrowseCache.folderOf` (`player/BrowseCache.kt:39`,
  Modland only), `TrackFiles`, `OnPhone`, `QueueLink`, `Platforms.wholeCatalogues` (RV-B028 is what
  forgetting one looks like), `sources.tsv`, the manifest's hosts, strings.
- **RV-O014 — The native track is confined to the main thread by convention only.**
  `engine/NativeEngine.kt:249-252` checks "not closed" and then calls; `NativeLifetime` protects a
  seek and nothing else. It holds today because every other caller is on the main thread.
- **RV-O015 — Identity has two shapes.** Modland's and The Mod Archive's ids are their download
  addresses; ASMA, UnExoticA and AMP use `<id>://path`. `ModArchive.owns` exists beside `pathFrom`.
- **RV-O050 — Screens rebuild keys and ask the catalogue during drawing.**
  `ui/ManualQueueReorder.kt:80` and `:103` look rows up by `"manual:$id"`, a key defined in
  `player/PlaybackSchedule.kt:32`; a change of that format makes dragging in the queue silently do
  nothing. `Catalogue.owning(id)` is evaluated for every visible row while composing
  (`ui/PlaylistScreen.kt:310`, `:315`; `ui/BrowseScreen.kt:1443`, `:1447`; `ui/ShareMenu.kt:103`),
  so the exception in RV-B006 would surface as a crash while drawing a list, not only at play.
  The import path refuses such an id first (§11), which is why this is not a bug of its own.

- **RV-O045 — One-shot events have no listener when the screen is gone.** `reveal`, `showBrowse`,
  `share` and `scan` are shared flows without replay, collected in `ui/ProtracktorApp.kt:229`,
  `:271-275`, `:287`, `:353`. A share that finishes preparing after the app left the screen — an
  audio render takes seconds — is dropped, or its chooser is refused as a background start, and
  nothing says so.

### 5.3 Inconsistency

- **RV-O016 — Three ways to switch a feature off.** `UnExoticA.ENABLED` (left out of the list,
  rows pruned), `Amp.ENABLED` with `AmpAccess` (kept, hidden, rows retained), and the alarms'
  Gradle property with a manifest transform and a `BuildConfig` field.
- **RV-O017 — Settings live in two stores.** Thirteen in `Appearance` (`AppTheme.kt`) —
  appearance, but also the web address, pairing, cache-ahead, stereo mix, normalisation — and five
  in the database's `player_state`. The preferences file's name is a literal in four files.
- **RV-O018 — Failure handling.** History, Saved places, search, the playlist switch and the alarms
  catch and report; the rest of the controller's coroutines do not (RV-B014). `runCatching` is used
  in 72 places and catches `Error` too.
- **RV-O019 — Thread safety inside the backends.** sc68's counters and `SidBackend::rendered_`
  became atomics after a review; ASAP's, game-music-emu's, Hively's and ZXTune's equivalents are
  plain fields. Only `seekByRendering` honours "a newer seek abandons this one"; Hively's and
  UADE's own seeks do not.
- **RV-O020 — Two hands are visible.** The loudness code (`native/engine/loudness.h`, parts of
  `player_oboe.cpp:199-217` and `:307-327`, `player/LoudnessProfiles.kt`, parts of the controller)
  and the alarms module are written densely, without spaces after commas and with 200-character
  lines; the rest is not.

### 5.4 Dead code

- **RV-O021 — AMP's gate guards a switch nothing can flip.** `net/AmpAccess.kt:18` `setEnabled`
  has no caller in the app and `Amp.ENABLED` is a constant `true`; the tickets, epochs, attached
  transfers, `CatalogueRetention`, `catalogueVisibility` and about twenty-five checks across
  `RemoteFiles`, `TrackFiles`, `CatalogueStore`, the controller and the read-ahead are therefore
  always satisfied in this build.
- **RV-O022 — Smaller remains.** `Lha.list` and its JNI function have no caller
  (`net/UnExoticA.kt:177`); the `composer` key no decoder emits is read in three places;
  `library_index.backend` always equals `format` (`player/PlaybackController.kt:1128-1129`);
  `player/MediaScanner.kt:32-36` has two identical branches; `player/PlaybackReadAhead.kt:50` tests
  a condition that cannot be false; `SidBackend::rate_` only ever holds 44 100; the R8 rule in
  `app/proguard-rules.pro` repeats what the default rules already keep.

### 5.5 Complexity

- **RV-O029 — `PlaybackController`: 3 825 lines.** Composition root, playback, the poll, Random,
  scanning, sharing in five forms, pairing, history mapping, playlist adds, queue edits, failure
  wording. Parts were extracted, each with its own port shape; the centre remains.
- **RV-O030 — `BrowseState`: about 55 fields in one data class** for navigation, the folder tree,
  catalogue levels, search, the history page, download progress, storage sizes, replay counts,
  pairing, the web address, Random's scope and cache marks. Five extracted classes each wrap it in
  an adapter of their own.
- **RV-O031 — Every action travels four hand-wired hops.** Controller method, a one-line forward
  in `PlayerViewModel` (about a hundred of them), a lambda in `ProtracktorApp` (144 references to
  the view model), a parameter on the screen. Inside the screens it goes further: `BrowseScreen`
  takes 46 parameters (`ui/BrowseScreen.kt:105-170`), hands about ten track actions by name to four
  domain composables, and each of them to `Selectable`, called five times with the same fifteen
  arguments (`:709`, `:809`, `:836`, `:1113`, `:1214`); `SettingsScreen` takes 40 and passes 14 on
  to `StorageSection`. `ShareActions` (`ui/ShareMenu.kt:29`) shows the holder that is missing one
  level up (RV-S016).
- **RV-O032 — History's view is five classes and four counters** for a paged list that can be
  cleared. Correct as far as read; heavier than the problem.

### 5.6 Traces of lost context

- **RV-O034 — Comments describe a threading model the engine no longer has.** Subsong switches
  have run on the caller's thread since the lock was introduced, yet "applied on the audio thread"
  stands at `native/engine/engine.cpp:556-567`, `:575`, `:753`, `:1064`,
  `native/engine/player_oboe.cpp:117-125` (above a body that says the opposite),
  `engine/NativeEngine.kt:230-233`, `player/PlaybackController.kt:831`, `:849`, `:2907`, `:2917`.
- **RV-O035 — KDoc attached to the wrong thing or to nothing.** Stacked or orphaned blocks at
  `engine/NativeEngine.kt:50-93`; `player/PlaybackController.kt:101-108`, `:1016-1034`,
  `:1411-1432`, `:1901-1909`, `:2936-2948`, `:3756-3768`; `player/PlayerUiState.kt:142-148`,
  `:174-187`; `player/PlayQueue.kt:60-69`; `data/CatalogueStore.kt:149-155`, `:496`, `:518-534`
  (documents a parameter that no longer exists); `data/LibraryIndexStore.kt:176-184`;
  `data/SchemaSql.kt:240-247`, `:386`, `:465-472`; `data/PlaylistFile.kt:101-108`;
  `net/RemoteFiles.kt:129`; `player/LibraryDownloads.kt:72-79`, `:101-109`.
- **RV-O036 — A constraint kept alive by a reason that expired.**
  `player/PlaybackController.kt:3678-3682` calls it a hard requirement that the background title
  pass runs only while nothing is loaded, because sc68 keeps global state; `:1029-1033` in the same
  file records that sc68 3.0 does not, which is why scans run during playback.
- **RV-O037 — Numbers, names and references that rotted.** "Seven backends"
  (`native/engine/engine.cpp:8`, `engine.h:7`, with "fifteen JNI functions" and "1,868 lines");
  "three functions" (`native/engine/lha_jni.cpp:4`, there are two); Modland as "the only one wired
  up so far" (`net/Catalogue.kt`); a tombstone for a deleted function
  (`player/PlaybackController.kt:2430-2434`). The code cites `docs/review.md`,
  `docs/review-round-8.md` and `docs/review-2026-09-28.md`; no file of those names is in `docs/`.

### 5.7 Documentation drift

- **RV-O038** — `MainActivity.kt:63` says the activity is `singleTask`; the manifest says
  `singleTop` (`AndroidManifest.xml:74`). `player/PlaybackController.kt:1023-1027` and the class
  comment of `player/SupportedFormats.kt` say a scan decides by content and that a module under any
  name is found; since `MediaScanner.worthReading` only listed names are opened, and
  `MediaScanner.listFiles` says "no filter on its name" a few lines above the filter.
  `player/BrowseState.kt` calls Random's scope "not persisted"; it is saved.
  `AppTheme.kt` says `rememberPairing` is called only after a browser was reached;
  `player/PlaybackController.kt:1458` stores an untested address when the queue is empty.

### 5.8 Performance

- **RV-O039 — The media session is republished five times a second.**
  `player/PlaybackService.kt:124-127` publishes metadata — with a 512 × 512 bitmap
  (`player/PlaybackPresentation.kt:60`) — and playback state on every state emission; the position
  changes every 200 ms. The comment above says the split exists to avoid that.
- **RV-O040 — Native and hashing work on the main thread at every track change**: closing the
  previous track (stream stop, decoder teardown, thread joins, a UADE process), MD5 over the whole
  file (`player/PlaybackController.kt:3553`), opening the Oboe stream (`:3567`).
- **RV-O041 — A file is held several times over.** The Kotlin array is kept for the life of the
  track (`:3543`), the engine copies it, several backends copy it again, the loudness analysis
  opens another decoder, and up to three read-ahead files wait beside it. Local playback has no
  size cap (a scan has one: 32 MB).
- **RV-O042 — Position ticks share one state object with everything else**, collected at the root
  of the screen (`ui/ProtracktorApp.kt:119`). How far that recomposes was not measured.
- **RV-O051 — Browse keeps a list position for every place it has been.** `ui/BrowseScroll.kt:37`
  holds one `LazyListState` per level key for the life of the screen, and the search level's key
  contains the query (`:112`), so each search typed adds one. Small, but it only grows.
- **RV-O043 — One lock for every loudness analysis** (`player/LoudnessPreparation.kt`): the tapped
  tune waits behind read-ahead measurements; the content hash is computed with normalisation off.

- **RV-O046 — Accessibility, as far as it was looked at.** The subsong chips
  (`ui/SubsongStrip.kt`) mark the tune playing by colour and expose neither a role nor a selected
  state; the line above them says the position in words, so the information is not lost, but a
  screen reader hears a row of bare numbers. 44 icons carry `contentDescription = null`; reading
  the screens showed each of them beside its own text or inside a control that names itself, so
  none is a missing label. The rest of what the screens do for a screen reader is careful: swipes
  and drags have custom actions (`ui/QueueSwipeRow.kt:79-84`, `ui/ManualQueueReorder.kt:146-151`,
  `ui/FloatingControlsPanel.kt:99-104`), the alarm's hold-to-stop has a confirmed tap instead
  (`alarms/AlarmRingingScreen.kt:139`). The colour-only cases are RV-B037.

### 5.9 Security notes that are not bugs by themselves

- **RV-O044** — `MainActivity` is exported and accepts `file://` and any `http(s)` address in an
  explicit intent; nothing leaves the phone without a share. UADE's file loader falls through to
  UADE's own search for absolute paths and `..` (`native/engine/engine.cpp:2496-2516`). Three
  downloads follow another project's `master` branch (UnExoticA's list, `song.conf`, favourites).
  UADE's scratch folders are removed only by the destructor; nothing clears them at start after a
  crash. `AlarmService.onCreate` calls `startForeground` without the guard `PlaybackService` has.

## 6. Suggestions

### 6.1 Architectural

**RV-S001 — Make `Catalogue` the whole strategy.** *Size L · risk medium · prerequisite: a
contract test every catalogue must pass.*

```
Catalogue                      one object per archive, nothing about it lives elsewhere
  id, displayName, enabled     enabled replaces both switch patterns
  identity: owns(id), pathOf(id), idFor(path)
  index:    IndexSource?       how the list arrives; null for a live-search catalogue
              stream(bytes) -> Sequence<CatalogueEntry>     never a List (RV-B012)
  files:    TrackSource        read(ref): bytes?, companions(ref), onPhone(ref): Check
              ModlandFiles(http + legacy path retry)   ArchiveFiles(zip)   LhaFiles(game archive)
  fetch:    FetchPolicy        maxBytes, cookieGate, userAgent; one HttpFetcher applies it
  sharing:  webUrl(path), browserUrl(path)
  traits:   platform?, prefetchable, approxDownloadMb, extras (Modland: favourites)
  search:   LiveSearch?        The Mod Archive only
```

`TrackFiles`, `OnPhone`, `QueueLink`, `WebRemote`, `DownloadPlan`, `BrowseCache`, `Platforms` and
the controller then ask `Catalogue.owning(id)` and stop testing prefixes. Local files become one
more source (`content://`), so the dispatch has no `else`. The contract test fails for a catalogue
that leaves a member unanswered — the compiler and one test replace the checklist of RV-O013.

**RV-S002 — One `LatestOnly` primitive** in place of the twelve counters (RV-O002): `launch(key)`
cancels the previous run and lets a result through only if it is still the newest. *Size M · risk
low per site; convert one site at a time under its existing tests.*

**RV-S003 — Split the controller along the lines it already hints at.** *Size L · risk high ·
prerequisite: RV-S004 and the scope's failure path.*

```
PlaybackEngine      owns the native Track and ONE control thread; every decoder call goes
                    through it (open, start, seek, subsong, restart, close) -> RV-B010/011/015/016
PlaybackSession     queue + schedule + poll; decides, never touches native code directly
Library             playlists, edits, import/export        Discovery   browse, search, history, saved
Sharing             files, links, audio, pairing           Downloads   indexes, facts, replays
PlaybackController  composition root and facade only
```

State follows: a small `PlaybackClock` (position, seeking) apart from `PlayerUiState`, and
`BrowseState` split by the section headings it already has.

**RV-S004 — A typed description from the engine.** Parse the describe block once, at the
boundary, into `TrackDescription(title, artist, format, backend: Backend, subsong, subsongs,
seekable, endsAt, …)`; `Backend` is an enum, so "is this UADE" stops being a string suffix
(RV-O011). *Size S · risk low.*

**RV-S005 — One way to say something and one way to fail.** A `Notices` provider that owns the
wrapped context and every sentence (RV-B004, RV-B027, RV-O009), and a `CoroutineExceptionHandler`
on the controller's scope that turns an unexpected failure into a notice and a log line instead of
a closed app (RV-B008, RV-B014). *Size M · risk low.*

**RV-S006 — One HTTP fetcher** with a size bound, the user agent, timeouts and an allow-list of
hosts taken from the catalogues (RV-B001, RV-O003). *Size S–M · risk low.*

**RV-S007 — In the engine.** A table of `{claims, make}` entries for `openBackend`; shared
helpers for PCM conversion, chunk buffering and background length measurement; the same guard on
every JNI function; atomics or the lock for every field two threads touch. *Size M · risk medium ·
prerequisite: the host probes already in the repository.*

**RV-S008 — The service start becomes the controller's business**: when a track is about to be
loaded it asks for the service through an injected hook (RV-B005). *Size S · risk low.*

**Order of work**

| step | what | size | must exist first | only I can check on the phone |
| --- | --- | --- | --- | --- |
| 0 | RV-B034: ask for notifications once | S | — | decline twice, start again |
| 0 | RV-B035: catch the camera failures in the scanner | S | — | a device without a back camera, if one is to hand |
| 1 | RV-S005: failure path and `Notices` | M | — | notices in the chosen language |
| 2 | RV-S006 and the id allow-list (RV-B001, B006, B032) | S–M | — | a real link still opens |
| 3 | RV-S008, RV-B002, RV-B003 | S | question 1, question 5 | subsong while paused; a SID with a start song |
| 4 | RV-B007, RV-B012 (streaming, zero-row guard) | M | — | indexing Modland on the phone |
| 5 | RV-S004 | S | — | — |
| 6 | RV-S001 | L | contract test | every catalogue: browse, play, share, prefetch |
| 7 | RV-S002 | M | step 1 | — |
| 8 | RV-S003 | L | steps 1, 5 | all of playback |
| 9 | RV-S007 | M | host probes | all formats |

Steps 1–5 are small and each removes several findings; they are worth doing whatever happens to
the rest. Steps 6 and 8 are rewrites of code that works today and can only be confirmed on the
phone: I would do 6, because the catalogue is where most of the special cases breed, and take 8
only in pieces — the engine facade first, since it carries four bugs. I would leave the queue
model (`PlayQueue`, `ActiveQueue`, `PlaybackSchedule`), the schema and the alarms alone.

### 6.2 Local

- **RV-S009** — Decide the AMP gate (question 4) and delete what the answer makes dead (RV-O021).
- **RV-S010** — A `ReplaceableTable` helper for the four stores, with the zero-row guard built in
  (RV-O004, RV-B007).
- **RV-S011** — One format table — `web/src/formats.tsv` already is one — from which
  `SupportedFormats`, `Platforms.names` and a test for the manifest's patterns are derived
  (RV-O006).
- **RV-S012** — Release persisted grants when a folder is forgotten or a picked file leaves the
  last playlist (RV-B023).
- **RV-S013** — Thread assertions in `NativeEngine.Track` in debug builds, and `StrictMode` there
  (RV-O014, RV-O040).
- **RV-S014** — A pass over comments: delete history that belongs to git, fix what RV-O034 to
  RV-O038 list. Comments that narrate past mistakes are a large part of this code base and the
  part that goes stale.
- **RV-S015 — One row for a tune.** A `TrackRow` taking the tune, its marks and a list of actions,
  used by the playlist, Browse, Random and the queue; the "fetching" pulse and the menu order live
  in it once (RV-O047). Do it together with RV-S016, or the row only moves the parameters.
- **RV-S016 — Hand screens a holder of actions, not forty lambdas.** A `TrackActions` (play, add,
  queue, alarm, info, neighbours, the four shares) built once in `ProtracktorApp` from the view
  model, and `actionsFor(track)` decided once from the catalogue (RV-S001), instead of each row
  asking `Catalogue.owning` (RV-O031, RV-O050). `ShareActions` is the existing pattern to grow.
- **RV-S017 — One "on" form for toggles and choices.** A filled or ticked variant used by the dock's
  toggles, the scope tiles and the filter chips, with `selected` semantics (RV-B037). With it,
  `canPlay` and the seek bar's dimmed state go on the state once and are read by both docks
  (RV-B036, RV-O048).

## 7. Ideas

### 7.1 Features

- **RV-I001 — A diagnostics page**: the last few hundred of the app's own log lines, the decoder
  fingerprint, what is installed (replays 176/176, index dates), with Share. The code repeatedly
  notes that logcat is out of reach on the phone; refusals already carry reasons that only reach
  the log.
- **RV-I002 — "Play from an unknown address?"** — a confirmation for a tune a catalogue does not
  own, instead of a silent refusal, once RV-B001 is closed.
- **RV-I003 — Repair for downloads**: verify the replay sets and the archives against expected
  counts and offer to fetch what is missing (RV-B029).
- **RV-I004 — Keep an author offline**: the folder prefetch exists; a pinned folder would be
  exempt from the cache budget.
- **RV-I005 — Sleep timer**: the alarm module has the clock, the service and the fade; stopping
  after *n* minutes or at the end of the tune is small beside it. Not found in `docs/WISHLIST.md`
  or `docs/BACKLOG.md` by keyword.
- **RV-I006 — One-file backup** of playlists, history, saved places and settings: nothing survives
  an uninstall by design, and export exists per playlist only.
- **RV-I007 — Tunes in no playlist** as a view, if `tracks` is meant to be a library (question 2).

### 7.2 Other

- **RV-I008 — Fuzzing on the host** for the parsers this project owns: `lha_extract`,
  `QueueLink.decode`/`open`, `PlaylistFile.read`, each `parseIndex`, the describe block, the tar
  reader. Small, pure, and the place three findings came from.
- **RV-I009 — Rules the build can hold**: a test that fails on a prefix test outside `net/`
  (`startsWith("amp://")`), one for package dependencies (`engine` must not import `player`), one
  that every JNI function is wrapped, detekt for swallowed exceptions; a sanitizer build of the
  engine's host probe.

## 8. Tests

Surveyed, not reviewed: 262 files and about 1 645 tests (player 827, ui 345, data 154, alarms 153,
net 98). What the survey shows:

- **Migrations are the best-tested part.** `SchemaSqlTest` runs every statement against real
  SQLite, compares an upgraded database with a fresh one, checks data survives, and fails on a
  missing step; three more tests cover single migrations and the downgrade refusal.
- **Pure rules are tested where they were extracted** — queue, schedule, search terms, cache
  budget, length sources — often against shared case tables in `docs/rules/`.
- **The controller is barely reachable.** Two tests construct `PlaybackController`. None of
  RV-B002, B005, B008, B010, B014 could have been caught by the suite as it is; they live in the
  3 825 lines that need a phone.
- **The native boundary has no JVM test** beyond `NativeLifetime`; its checks are host scripts,
  outside this review.
- **A test that gives false comfort:** `MessagesAreTranslatedTest` exists so that no notice
  reaches a Polish screen in English. It checks that no `Message` is built from a literal and that
  both languages name the same strings. It cannot see the language of the context the string is
  read from (RV-B004) or text that is not a `Message` (RV-B027).
- **The test task lists its inputs by hand** (28 files); a new case table that is not added is a
  test Gradle will report as up to date.
- **The screens are tested through Robolectric and screenshots, and the gaps match the findings.**
  `DockCollapseUiTest` checks that the collapsed bar's Play is there, not when it is enabled
  (RV-B036). `ScrollbarBubbleTest` drags over rows of one height, the case in which RV-B039 cannot
  happen. Nothing reaches `ScannerScreen`'s camera path (RV-B035), which needs a device anyway.

## 9. Questions

1. **A subsong tapped while paused** (RV-B002): start playing, or select and stay paused?
   *Recommended: start playing — a tap on a tune is a request to hear it — and do it properly:
   state, focus, service.*
2. **Is `tracks` a library that outlives playlists** (RV-B022)? *Recommended: no. Remove a row
   when its last playlist lets go of it; if a library is wanted, give it a view (RV-I007).*
3. **Where may a tune come from** (RV-B001)? Only the catalogues; or anything, after a
   confirmation; or anything. *Recommended: the catalogues silently, anything else after a
   confirmation.*
4. **AMP's runtime gate** (RV-O021): keep the machinery for a future switch, or reduce it to a
   build-time property of the catalogue? *Recommended: reduce.*
5. **A SID's default song** (RV-B003): open at the song the file names, or always at the first?
   *Recommended: the file's, shown correctly.*

## 10. Coverage ledger

Status is one of: **not read** · **read — findings** · **read — nothing found** · **read in part**
(with what was skipped and why) · **out of scope**. "Nothing found" is written only for a file that
was read in full. "Code only" means every line of code was read and comment lines were not.

### 10.1 By area

| area | files | lines | status |
| --- | --- | --- | --- |
| native engine and JNI | 16 | 5 061 | read in full — findings |
| `engine/` | 5 | 598 | read in full — findings |
| `player/` | 104 | 13 816 | read — findings; 27 files code only |
| `data/` | 25 | 3 652 | read in full — findings |
| `net/` | 16 | 2 016 | read in full — findings |
| `permissions/`, app root | 6 | 581 | read — nothing found beyond observations |
| manifest, XML configuration, build files, R8 rules, versions | 9 | — | read — findings |
| string resources | 2 | 1 589 | key parity checked mechanically (647 = 647); texts not read |
| `alarms/` | 34 | 3 163 | read in full, code only — findings in the ringing screen only (RV-O048); the rest nothing beyond observations |
| `ui/` | 64 | 12 864 | read in full, code only — findings; `PlayerIcons.kt`: the builder, not the path data |
| tests | 262 | 32 067 | surveyed (§8); not reviewed |

### 10.2 By file

| file | lines | status |
| --- | --- | --- |
| `native/backends/asap/asap_measure.c` | 180 | read — nothing found |
| `native/backends/asap/asap_measure.h` | 47 | read — nothing found |
| `native/backends/minimp3/minimp3.c` | 16 | read — nothing found |
| `native/backends/zxtune/lha_zxtune.cpp` | 79 | read — findings |
| `native/backends/zxtune/uint16_char_traits.h` | 96 | read — nothing found |
| `native/engine/engine.cpp` | 2970 | read — findings |
| `native/engine/engine.h` | 208 | read — findings |
| `native/engine/lha_jni.cpp` | 72 | read — findings |
| `native/engine/log.h` | 27 | read — nothing found |
| `native/engine/loudness.h` | 158 | read — nothing found |
| `native/engine/loudness_analysis.h` | 31 | read — nothing found |
| `native/engine/player_oboe.cpp` | 914 | read — findings |
| `native/engine/stereo_mix.h` | 35 | read — nothing found |
| `native/lhasa/lha_extract.cpp` | 143 | read — findings |
| `native/lhasa/lha_extract.h` | 35 | read — nothing found |
| `native/patches/libopenmpt/0001-read-c1-text-as-cp437.patch` | 25 | read — nothing found |
| `alarms/AlarmCodec.kt` | 103 | read (code only) — nothing found |
| `alarms/AlarmCoordinator.kt` | 148 | read (code only) — nothing found |
| `alarms/AlarmFeatureLifecycle.kt` | 55 | read (code only) — nothing found |
| `alarms/AlarmFileStore.kt` | 67 | read (code only) — nothing found |
| `alarms/AlarmManagement.kt` | 536 | read (code only) — nothing found |
| `alarms/AlarmManagementViewModel.kt` | 114 | read (code only) — nothing found |
| `alarms/AlarmMetadata.kt` | 37 | read (code only) — nothing found |
| `alarms/AlarmModels.kt` | 141 | read (code only) — nothing found |
| `alarms/AlarmMusicPicker.kt` | 194 | read (code only) — nothing found |
| `alarms/AlarmMusicPreparer.kt` | 96 | read (code only) — nothing found |
| `alarms/AlarmMusicQueue.kt` | 73 | read (code only) — nothing found |
| `alarms/AlarmMusicSource.kt` | 66 | read (code only) — nothing found |
| `alarms/AlarmNotifications.kt` | 62 | read (code only) — nothing found |
| `alarms/AlarmPlanner.kt` | 49 | read (code only) — nothing found |
| `alarms/AlarmPlatform.kt` | 24 | read (code only) — nothing found |
| `alarms/AlarmPlaybackObservation.kt` | 64 | read (code only) — nothing found |
| `alarms/AlarmQuizFactory.kt` | 51 | read (code only) — nothing found |
| `alarms/AlarmReadiness.kt` | 52 | read (code only) — nothing found |
| `alarms/AlarmReceiver.kt` | 48 | read (code only) — nothing found |
| `alarms/AlarmRepository.kt` | 62 | read (code only) — nothing found |
| `alarms/AlarmResults.kt` | 15 | read (code only) — nothing found |
| `alarms/AlarmRingingActivity.kt` | 49 | read (code only) — nothing found |
| `alarms/AlarmRingingScreen.kt` | 202 | read (code only) — findings |
| `alarms/AlarmRingingViewModel.kt` | 107 | read (code only) — nothing found |
| `alarms/AlarmRules.kt` | 132 | read (code only) — nothing found |
| `alarms/AlarmService.kt` | 192 | read (code only) — nothing found |
| `alarms/AlarmSound.kt` | 96 | read (code only) — nothing found |
| `alarms/AlarmSourcePolicy.kt` | 17 | read (code only) — nothing found |
| `alarms/AlarmSummaries.kt` | 43 | read (code only) — nothing found |
| `alarms/AlarmTimeLabels.kt` | 34 | read (code only) — nothing found |
| `alarms/AlarmsActivity.kt` | 82 | read (code only) — nothing found |
| `alarms/AndroidAlarmScheduler.kt` | 52 | read (code only) — nothing found |
| `alarms/api/AlarmMusicSelection.kt` | 17 | read (code only) — nothing found |
| `alarms/api/MusicAlarms.kt` | 83 | read (code only) — nothing found |
| `data/CatalogueStore.kt` | 644 | read — findings |
| `data/DatabaseAccess.kt` | 37 | read — findings |
| `data/DatabasePreparation.kt` | 41 | read — findings |
| `data/FavouriteStore.kt` | 89 | read — findings |
| `data/HistoryStore.kt` | 159 | read — findings |
| `data/LearnedLengthStore.kt` | 53 | read — findings |
| `data/LegalText.kt` | 76 | read — findings |
| `data/LibraryIndexStore.kt` | 288 | read — findings |
| `data/LibraryStore.kt` | 291 | read — findings |
| `data/Md5.kt` | 19 | read — findings |
| `data/ModlandFavourites.kt` | 65 | read — findings |
| `data/MusicSources.kt` | 38 | read — findings |
| `data/OpenSourceNotices.kt` | 60 | read — findings |
| `data/PlaylistFile.kt` | 163 | read — findings |
| `data/ProtracktorDatabase.kt` | 107 | read — findings |
| `data/RecentSearchPreferences.kt` | 28 | read — findings |
| `data/SavedPlacesStore.kt` | 43 | read — findings |
| `data/SchemaSql.kt` | 683 | read — findings |
| `data/SearchTerms.kt` | 141 | read — findings |
| `data/SongDbLengthStore.kt` | 89 | read — findings |
| `data/SongDbLengths.kt` | 152 | read — findings |
| `data/SongDbMetadata.kt` | 99 | read — findings |
| `data/SongLengthStore.kt` | 85 | read — findings |
| `data/SongLengths.kt` | 85 | read — findings |
| `data/TrackMetadataStore.kt` | 117 | read — findings |
| `engine/DescribeBlock.kt` | 50 | read — findings |
| `engine/NativeData.kt` | 177 | read — findings |
| `engine/NativeEngine.kt` | 305 | read — findings |
| `engine/NativeLifetime.kt` | 51 | read — findings |
| `engine/OutputRecovery.kt` | 15 | read — findings |
| `gradle/libs.versions.toml` | 49 | read — findings |
| `net/Amp.kt` | 148 | read — findings |
| `net/AmpAccess.kt` | 35 | read — findings |
| `net/AmpModuleIds.kt` | 115 | read — findings |
| `net/AtomicFileWrite.kt` | 23 | read — findings |
| `net/CacheBudget.kt` | 121 | read — findings |
| `net/Catalogue.kt` | 291 | read — findings |
| `net/CatalogueHttp.kt` | 9 | read — findings |
| `net/CatalogueRetention.kt` | 11 | read — findings |
| `net/ModArchive.kt` | 199 | read — findings |
| `net/RemoteFileCache.kt` | 46 | read — findings |
| `net/RemoteFiles.kt` | 282 | read — findings |
| `net/Sc68Replays.kt` | 108 | read — findings |
| `net/SharedFetches.kt` | 50 | read — findings |
| `net/UadePlayers.kt` | 213 | read — findings |
| `net/UnExoticA.kt` | 190 | read — findings |
| `net/WebRemote.kt` | 175 | read — findings |
| `permissions/AccessRequestCoordinator.kt` | 84 | read — nothing found |
| `permissions/AppAccess.kt` | 85 | read — nothing found |
| `player/ActiveQueue.kt` | 103 | read — nothing found |
| `player/AndroidCatalogueStorage.kt` | 29 | read (code only) — nothing found |
| `player/AndroidDownloadText.kt` | 67 | read (code only) — findings |
| `player/AndroidFolderAccess.kt` | 18 | read — nothing found |
| `player/AndroidMaintenanceFiles.kt` | 23 | read (code only) — findings |
| `player/AndroidMaintenanceText.kt` | 32 | read (code only) — findings |
| `player/AndroidPlaylistFiles.kt` | 35 | read (code only) — nothing found |
| `player/AndroidPlaylistText.kt` | 30 | read (code only) — findings |
| `player/AndroidTrackFileAccess.kt` | 60 | read — findings |
| `player/AudioExport.kt` | 63 | read — nothing found |
| `player/AudioFocus.kt` | 116 | read — nothing found |
| `player/BarLength.kt` | 26 | read — nothing found |
| `player/BrowseCache.kt` | 93 | read — findings |
| `player/BrowseFilter.kt` | 78 | read — nothing found |
| `player/BrowseNavigation.kt` | 78 | read — nothing found |
| `player/BrowseReadOwner.kt` | 39 | read — nothing found |
| `player/BrowseState.kt` | 266 | read — findings |
| `player/BrowseWorkflow.kt` | 337 | read — findings |
| `player/CatalogueBrowseState.kt` | 109 | read (code only) — nothing found |
| `player/CatalogueBrowser.kt` | 203 | read — findings |
| `player/CatalogueTracks.kt` | 32 | read — nothing found |
| `player/Companions.kt` | 50 | read — nothing found |
| `player/DownloadKeys.kt` | 38 | read (code only) — nothing found |
| `player/DownloadPlan.kt` | 82 | read (code only) — findings |
| `player/DownloadSizes.kt` | 46 | read (code only) — nothing found |
| `player/DownloadText.kt` | 33 | read (code only) — nothing found |
| `player/FallbackLength.kt` | 65 | read — nothing found |
| `player/FloatingControls.kt` | 63 | read (code only) — nothing found |
| `player/FloatingControlsWindow.kt` | 367 | read (code only) — nothing found |
| `player/FloatingPanelDrag.kt` | 40 | read (code only) — nothing found |
| `player/FloatingPanelPosition.kt` | 49 | read (code only) — nothing found |
| `player/FolderPrefetch.kt` | 118 | read — nothing found |
| `player/HistoryBrowser.kt` | 90 | read — nothing found |
| `player/HistoryFilter.kt` | 31 | read — findings |
| `player/HistoryPages.kt` | 32 | read — nothing found |
| `player/HistoryPresentation.kt` | 25 | read — nothing found |
| `player/HistoryPresentationReads.kt` | 49 | read — nothing found |
| `player/HistoryReads.kt` | 106 | read — nothing found |
| `player/HistoryRecording.kt` | 9 | read — nothing found |
| `player/HistoryWrites.kt` | 80 | read — nothing found |
| `player/IncrementalScan.kt` | 47 | read — nothing found |
| `player/LengthSource.kt` | 73 | read — nothing found |
| `player/LibraryDownloads.kt` | 424 | read — findings |
| `player/LibraryMaintenance.kt` | 206 | read (code only) — findings |
| `player/LibraryMaintenanceState.kt` | 87 | read (code only) — nothing found |
| `player/LibrarySearch.kt` | 122 | read — findings |
| `player/ListeningSession.kt` | 57 | read — nothing found |
| `player/LocalFolderBrowser.kt` | 125 | read — findings |
| `player/LocalTree.kt` | 53 | read — nothing found |
| `player/LoudnessPreparation.kt` | 49 | read — findings |
| `player/LoudnessProfiles.kt` | 80 | read — findings |
| `player/M4aWriter.kt` | 108 | read — nothing found |
| `player/MediaScanner.kt` | 193 | read — findings |
| `player/Message.kt` | 19 | read — findings |
| `player/NowPlayingText.kt` | 21 | read — nothing found |
| `player/OnPhone.kt` | 50 | read — findings |
| `player/OpenFailure.kt` | 113 | read — findings |
| `player/PcmExport.kt` | 74 | read — nothing found |
| `player/PickedFiles.kt` | 27 | read — nothing found |
| `player/Platforms.kt` | 310 | read (code only) — findings |
| `player/PlayFromEnd.kt` | 41 | read — findings |
| `player/PlayQueue.kt` | 352 | read — findings |
| `player/PlaybackController.kt` | 3825 | read — findings |
| `player/PlaybackMediaSession.kt` | 21 | read — nothing found |
| `player/PlaybackMetadata.kt` | 22 | read — nothing found |
| `player/PlaybackPlan.kt` | 32 | read — nothing found |
| `player/PlaybackPresentation.kt` | 111 | read — findings |
| `player/PlaybackReadAhead.kt` | 91 | read — findings |
| `player/PlaybackSchedule.kt` | 138 | read — nothing found |
| `player/PlaybackService.kt` | 292 | read — findings |
| `player/PlayerUiState.kt` | 206 | read — findings |
| `player/PlayerViewModel.kt` | 259 | read — findings |
| `player/PlaylistHeart.kt` | 70 | read (code only) — nothing found |
| `player/PlaylistHeartActions.kt` | 73 | read (code only) — nothing found |
| `player/PlaylistSearch.kt` | 19 | read — nothing found |
| `player/PlaylistSession.kt` | 309 | read — findings |
| `player/PlaylistSessionState.kt` | 142 | read (code only) — nothing found |
| `player/PlaylistTransfer.kt` | 162 | read — findings |
| `player/QueueLink.kt` | 278 | read — findings |
| `player/QueueSourceNavigation.kt` | 44 | read — findings |
| `player/RandomHistory.kt` | 96 | read — nothing found |
| `player/RandomScope.kt` | 74 | read — nothing found |
| `player/RecentSearches.kt` | 41 | read — nothing found |
| `player/ReleaseYear.kt` | 70 | read — nothing found |
| `player/ReplayDownloads.kt` | 98 | read (code only) — findings |
| `player/SavedPlace.kt` | 38 | read (code only) — nothing found |
| `player/SavedPlaces.kt` | 126 | read (code only) — nothing found |
| `player/ScanBatch.kt` | 115 | read — nothing found |
| `player/ScanNotification.kt` | 82 | read — findings |
| `player/SearchResults.kt` | 85 | read — nothing found |
| `player/SearchScope.kt` | 60 | read — nothing found |
| `player/SeekProgress.kt` | 26 | read — nothing found |
| `player/SelectionShare.kt` | 47 | read — nothing found |
| `player/SessionSource.kt` | 57 | read — nothing found |
| `player/StereoMix.kt` | 15 | read — nothing found |
| `player/StoreDownloadSources.kt` | 54 | read (code only) — findings |
| `player/SubsongAdvance.kt` | 55 | read — nothing found |
| `player/SubsongNormalization.kt` | 64 | read — findings |
| `player/SupportedFormats.kt` | 260 | read — findings |
| `player/TrackArtwork.kt` | 155 | read (code only) — nothing found |
| `player/TrackArtworkBitmaps.kt` | 76 | read (code only) — findings |
| `player/TrackEditing.kt` | 41 | read — nothing found |
| `player/TrackFiles.kt` | 137 | read — findings |
| `player/TunesFound.kt` | 9 | read — nothing found |
| `ui/AddToPlaylistDialog.kt` | 151 | read (code only) — findings |
| `ui/AlarmMenuItem.kt` | 15 | read (code only) — nothing found |
| `ui/BrowseFilters.kt` | 164 | read (code only) — nothing found |
| `ui/BrowseScreen.kt` | 2071 | read (code only) — findings |
| `ui/BrowseScroll.kt` | 117 | read (code only) — findings |
| `ui/DatabaseGate.kt` | 85 | read (code only) — nothing found |
| `ui/DockCollapsed.kt` | 202 | read (code only) — findings |
| `ui/DockEdgeSeek.kt` | 147 | read (code only) — findings |
| `ui/DockMarquee.kt` | 29 | read (code only) — nothing found |
| `ui/DownloadPicker.kt` | 211 | read (code only) — nothing found |
| `ui/DraggableScrollbar.kt` | 160 | read (code only) — findings |
| `ui/FeedbackRow.kt` | 84 | read (code only) — nothing found |
| `ui/FloatingControlsAction.kt` | 105 | read (code only) — nothing found |
| `ui/FloatingControlsPanel.kt` | 140 | read (code only) — findings |
| `ui/FloatingDismissTarget.kt` | 74 | read (code only) — nothing found |
| `ui/FloatingDragContainer.kt` | 90 | read (code only) — nothing found |
| `ui/Haptics.kt` | 174 | read (code only) — nothing found |
| `ui/HistoryScroll.kt` | 25 | read (code only) — nothing found |
| `ui/HoldToConfirm.kt` | 157 | read (code only) — nothing found |
| `ui/LabelledAction.kt` | 328 | read (code only) — nothing found |
| `ui/LegalScreens.kt` | 234 | read (code only) — nothing found |
| `ui/ListScrolling.kt` | 123 | read (code only) — nothing found |
| `ui/ManualQueueReorder.kt` | 157 | read (code only) — findings |
| `ui/MenuFocus.kt` | 70 | read (code only) — nothing found |
| `ui/MessageSnackbar.kt` | 33 | read (code only) — findings |
| `ui/NavSlots.kt` | 86 | read (code only) — nothing found |
| `ui/NavSlotsSetting.kt` | 131 | read (code only) — nothing found |
| `ui/NowPlaying.kt` | 278 | read (code only) — findings |
| `ui/PlayedBefore.kt` | 38 | read (code only) — nothing found |
| `ui/PlayerDock.kt` | 601 | read (code only) — findings |
| `ui/PlayerIcons.kt` | 493 | read in part — the icon builder; path data not read |
| `ui/PlaylistScreen.kt` | 785 | read (code only) — findings |
| `ui/PlaylistSearchHeader.kt` | 156 | read (code only) — nothing found |
| `ui/PlaylistSwitcher.kt` | 297 | read (code only) — findings |
| `ui/PreparingDatabase.kt` | 75 | read (code only) — nothing found |
| `ui/ProtracktorApp.kt` | 1092 | read (code only) — findings |
| `ui/QueueActions.kt` | 20 | read (code only) — nothing found |
| `ui/QueueCount.kt` | 52 | read (code only) — nothing found |
| `ui/QueueHelp.kt` | 105 | read (code only) — nothing found |
| `ui/QueueScreen.kt` | 161 | read (code only) — findings |
| `ui/QueueSwipeIntent.kt` | 17 | read (code only) — nothing found |
| `ui/QueueSwipeRow.kt` | 172 | read (code only) — findings |
| `ui/RandomDice.kt` | 56 | read (code only) — nothing found |
| `ui/RandomFilterState.kt` | 88 | read (code only) — nothing found |
| `ui/RandomScreen.kt` | 220 | read (code only) — nothing found |
| `ui/SavedPlacesScreen.kt` | 123 | read (code only) — nothing found |
| `ui/ScannerScreen.kt` | 203 | read (code only) — findings |
| `ui/ScrollingActionRow.kt` | 129 | read (code only) — nothing found |
| `ui/SearchQueryField.kt` | 99 | read (code only) — nothing found |
| `ui/SearchScopePanel.kt` | 260 | read (code only) — findings |
| `ui/SeekBar.kt` | 271 | read (code only) — findings |
| `ui/SettingChoice.kt` | 94 | read (code only) — nothing found |
| `ui/SettingsScreen.kt` | 557 | read (code only) — findings |
| `ui/ShareMenu.kt` | 120 | read (code only) — findings |
| `ui/ShellNavigation.kt` | 170 | read (code only) — nothing found |
| `ui/ShellState.kt` | 133 | read (code only) — nothing found |
| `ui/StereoMixSetting.kt` | 39 | read (code only) — nothing found |
| `ui/StorageSection.kt` | 210 | read (code only) — findings |
| `ui/SubsongStrip.kt` | 144 | read (code only) — findings |
| `ui/SwipeableSnackbar.kt` | 81 | read (code only) — findings |
| `ui/TrackInfo.kt` | 64 | read (code only) — nothing found |
| `ui/theme/LightColors.kt` | 41 | read (code only) — nothing found |
| `ui/theme/SystemBars.kt` | 21 | read (code only) — nothing found |
| `ui/theme/Theme.kt` | 36 | read (code only) — nothing found |
| `app/build.gradle.kts` | 427 | read (comments not read) — findings |
| `app/proguard-rules.pro` | 15 | read — findings |
| `app/src/main/AndroidManifest.xml` | 437 | read — findings |
| `app/src/main/res/values-pl/strings.xml` | 816 | key parity only |
| `app/src/main/res/values/strings.xml` | 773 | key parity only |
| `app/src/main/res/xml/data_extraction_rules.xml` | 21 | read — nothing found |
| `app/src/main/res/xml/file_paths.xml` | 11 | read — nothing found |
| `app/src/main/res/xml/network_security_config.xml` | 21 | read — findings |

## 11. Dismissed suspicions

- **A use-after-free of game-music-emu's track info on a subsong switch** — the switch runs on the
  caller's thread under the decoder lock and the description is published under it; no reader
  touches the pointer.
- **SQLite's 999-parameter limit in the `playable` and `platform` updates on Android 10 and 11** —
  about 170 and 450 parameters respectively.
- **R8 renaming `net.Lha`'s native methods** — the default optimise rules keep native method names
  for every class.
- **A SID's position computed at the device's rate** — the stream is opened at the decoder's rate
  and a mismatch is reported.
- **Exceptions from decoders reaching the audio thread or the JVM** — contained by `guarded` at
  every entry in `player_oboe.cpp` (the LHA wrapper is the exception, RV-B009).
- **A crash loop from a malformed address stored in a playlist** — the import evaluates the
  address before storing it, so it fails there (RV-B006) and nothing is kept.
- **Pairing with an address that was never validated** — the scanner checks the code's shape and,
  for plain `http`, that the host is local, before the controller sees it.
- **A ringing alarm with neither a question nor the backup flag**
  (`alarms/AlarmRingingScreen.kt:87`, `checkNotNull`) — every rule that drops the quiz sets
  `backup` in the same copy (`alarms/AlarmRules.kt:62-63`, `:72`, `:122`).
- **Duplicate keys in the lazy lists** — history and playlist rows are unique by their primary
  keys, search results are de-duplicated in `player/SearchResults.kt`, the "below" list holds only
  deeper tunes and its keys are prefixed, and Random keys by position.
- **Forgetting a folder taking its tunes out of playlists** — it deletes `library_index` rows only
  (`data/LibraryIndexStore.kt:284`); playlists reference `tracks`.
- **Lost native handles when an open is cancelled** — ownership passes to `track` before the next
  suspension, and `prepareOrClose` covers the window before that.

## 12. Working notes

No open thread remains. The screens in `ui/` and the three alarm screens, read last on 2026-11-17,
were checked for state and side effects in Compose, keys in lists, gestures against their
accessibility actions, and colour as the only carrier of information; what they produced is
RV-B035 to RV-B040, RV-O047 to RV-O051 and RV-S015 to RV-S017. What remains unread is stated in
§10: the icons' path data, the texts of the string resources, and the tests as code.
