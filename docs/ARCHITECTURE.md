# Architecture

This document records what has been decided and **why**. A decision without its reason gets
re-litigated every few weeks, or worse, applied in a situation it was never meant for.

Nothing below is built yet. It is the shape we are building toward, and it will be corrected here
when reality disagrees.

---

## 1. Platform

| Setting | Value | Reason |
| --- | --- | --- |
| Language | Kotlin | — |
| UI | Jetpack Compose, Material 3 Expressive | R8 |
| `minSdk` | 29 (Android 10) | Below this, scoped storage and the current media APIs both become a fight. The owner's own device is API 36; 29 exists so other people can install it. |
| `targetSdk` | 36 (Android 16) | Owner's device generation. |
| Native | NDK 29.0.14206865, CMake 3.31.6 | Pinned. A native toolchain that changes underneath you produces failures nobody can reproduce. |

## 2. Licence: GPL-3.0-or-later

Decided 2026-08-31.

The decoders worth having are GPL — `libsidplayfp` (C64), UADE (Amiga custom formats), `sc68`
(Atari ST), ASAP (Atari 8-bit). Linking them makes the whole application GPL. That part is not a
choice.

Choosing **3** rather than 2 is a choice, and the reason is our own stack, not the decoders:
Jetpack Compose, AndroidX, Kotlin's standard library and Oboe are all **Apache-2.0**, which is
incompatible with GPL-2 and compatible with GPL-3. Under GPL-2 we would have a licence conflict
with our own UI framework. GPL-3 additionally carries an explicit patent grant that GPL-2 lacks.

This requires every GPL dependency to be "**or later**" so it can be taken to 3. That is verified
per dependency at integration time and recorded in `docs/LICENSES.md`. A GPL-2-only dependency
would invalidate this decision and must be raised with the owner rather than worked around.

Consequence accepted by the owner: when the repository goes public, sources must stay available.
F-Droid is the natural channel. Google Play does not object to GPL-3 (the App Store conflict people
remember is Apple's, and does not apply).

## 3. We do not fork ZXTune

ZXTune's decoding core is good and its format coverage is the reference. But it is a long-lived C++
codebase with its own framework, and its architecture is the source of most of what
`docs/REQUIREMENTS.md` complains about. Understanding it well enough to change it safely costs
about as much as writing a new shell, and leaves us owning its structure.

So: **new application, upstream decoders.** ZXTune is consulted for its format list and its
content-probing logic, not vendored.

## 4. The audio path stays native

```
  ┌──────────────── Kotlin ─────────────────┐   ┌────────── C/C++ ──────────┐
  │ MediaSessionService                     │   │                           │
  │   └─ SimpleBasePlayer implementation ───┼──▶│ engine facade (JNI)       │
  │        control, position, queue         │   │   ├─ backend registry     │
  │                                         │   │   ├─ libopenmpt  (trackers)│
  │ SQLite: index, playlists, cache (§9)    │   │   ├─ sc68        (SNDH/YM) │
  │ Compose UI                              │   │   ├─ libsidplayfp (SID)   │
  │                                         │   │   ├─ UADE        (Amiga)  │
  │                                         │   │   └─ game-music-emu (NSF…) │
  │                                         │   │                           │
  │                                         │   │ Oboe callback ──▶ speaker │
  └─────────────────────────────────────────┘   └───────────────────────────┘
```

**Rendering and output both live in native code; JNI carries control and metadata only.** The
alternative — rendering natively and pushing PCM buffers into an `AudioTrack` from Kotlin — crosses
the JNI boundary every few milliseconds on the one thread that must never be late. Keeping the
render callback and the decoders on the same side of the boundary removes that whole class of
glitch.

Oboe rather than raw `AudioTrack` because Oboe already solves device-specific buffer sizing and
stream recovery on Android, and it is Apache-2.0.

On the Kotlin side, Media3's `SimpleBasePlayer` looked like what makes this affordable: it lets a
custom player expose itself to `MediaSession`, so notification controls, audio focus,
becoming-noisy, Bluetooth and Android Auto all work without writing an ExoPlayer `Renderer` for a
synthesiser.

> **Superseded by §12.** The session was built on the platform's own `android.media.session`
> instead, and Media3 is not a dependency of this project. The paragraph above is left as the
> decision that was actually made at the time; §12 says what replaced it and why.

## 5. One facade, many backends

Every decoder sits behind a single native interface: probe, open, render, seek, subsong select,
metadata, close. Adding a format means adding a backend to a registry, not touching the player.

**Format is decided by content, not by file extension.** Extensions in this world are unreliable,
absent, or shared between unrelated formats. Probing is what ZXTune gets right and it is worth
copying the approach.

Capabilities differ per backend and must be *declared*, not assumed — `libopenmpt` and
game-music-emu can seek; `libsidplayfp`, UADE and `sc68` effectively cannot, because they are CPU
emulators with no way back except re-running from the start. The UI reflects what the current
backend can actually do rather than offering a control that silently does nothing (AGENTS.md §7:
a press that does nothing and says nothing is a defect).

## 6. Startup latency is an indexing problem, not a decoding one

R9's 5–30 second wait is not decode time — a module is kilobytes and renders in milliseconds. It is
re-opening the containing archive and re-probing the format on every single play.

Three mechanisms, in order of effect:

1. **Persistent index (SQLite, §9).** Path, container, offset, detected format, metadata, duration,
   subsong count — written once at scan, read instantly afterwards. This alone removes the probe.
2. **Extracted-file cache.** Entries pulled out of ZIP/LHA archives are kept on disk keyed by
   container identity, so playing the second track from an archive does not re-open it.
3. **Prepare-ahead.** While a track plays, the next one is opened and its first buffers rendered.

The index is also what makes R2 possible: a playlist that refers to index rows does not need the
filesystem to exist at launch, so opening the app cannot destroy the view.

## 7. Domain logic stays off Android

Shuffle order and history (R5), queue and cursor semantics, repeat scope within a playlist (R6),
duration policy — these are pure Kotlin, testable without an emulator, and they are the parts most
worth testing. There is no emulator in this environment (AGENTS.md §3), so anything that can only
be verified by running the UI is verified by the owner on a real phone, and everything else is
verified by tests that run here.

What has been pulled out this way, and why each earned it: `PlayQueue`, `SchemaSql`, `SongLengths`,
`CacheBudget`, `BrowseNavigation`, `BrowseScroll`, `TrackEditing`, `PlaylistFile`,
`SupportedFormats` and — since 2026-09-04 — `SubsongAdvance`.

It also made the correction cheap. When the owner said repeat-one was still advancing, the fix was
one line in `SubsongAdvance` and a rewritten test that says why the old rule was wrong — rather than
a change inside a method nothing can exercise here.

**The rule for when to extract is not "when it is easy".** It is *when the decision has been wrong
before*. `SubsongAdvance` holds one question — what a file with several tunes in it does when one
ends — and that question has now been answered wrongly twice inside a method that needs a phone to
run: C11 raced through every subsong in silence, and C13 never consulted repeat-one at all. Sixty
lines of object and six tests is a cheap way to stop a third time.

## 8. Two kinds of source behind one index

Decided 2026-08-31, after the owner asked whether online catalogues with a local cache were
possible. They are, and the measurements below were taken rather than assumed.

| Source | Index | File |
| --- | --- | --- |
| Local folder (SAF) | produced by a scan | already present |
| Remote catalogue (Modland, HVSC) | downloaded once | fetched on demand, then cached |

Both produce rows in the same index, so **a playlist can mix local and remote entries** — it refers
to index rows, not to paths.

### What was verified on 2026-08-31

`https://modland.com/allmods.zip` is 5.75 MB and contains a single `allmods.txt` of **515,502**
lines, each `<size>\t<Format>/<Author>/<title>.<ext>`. A file is fetched at
`https://modland.com/pub/modules/` + the URL-encoded path; one was pulled and its length matched
the index exactly, with a real ProTracker header rather than an error page. The server answers
`Range` requests with `206 Partial Content`.

`https://www.hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5` is 5.2 MB and downloads
directly, which settles Q4: exact SID durations are available and do not have to be guessed. Used
since 2026-09-02 — re-checked that day, still 5,205,150 bytes; §14 records what it turned out to be.

`sndh.net` did not resolve from this machine. **Unverified** — whether the domain is gone or the
network here blocks it is unknown. Atari ST material is also in Modland, so nothing depends on it
yet.

### What follows from those numbers

- **Browsing Modland works entirely offline.** The index is one 5.75 MB file; in the database it
  gives browse-by-format and browse-by-author with no network. The network is first needed at the moment
  a track is played.
- **Range support means fetches resume**, and a partially fetched file can start playing.
- The one fetch measured took 4 s for 212 KB. That is an argument for fetching the next track
  ahead of time, not against remote catalogues.

### Offline behaviour

An entry whose file is not cached stays **visible and explicitly marked unavailable**. It is not
hidden and not silently skipped: a press that does nothing and says nothing is a defect
(AGENTS.md §7).

## 9. Storage: hand-written SQLite, not Room

> **One helper per process, since 2026-09-03.** Every store used to construct its own
> `SQLiteOpenHelper` on the same file. That class synchronises within an instance and not between
> them, so five of them meant five things that could independently run `onUpgrade` — and the
> migration statements are plain `CREATE TABLE`, which fail on a second run. `docs/review.md` R3.


Decided 2026-09-01.

Room was the intention and is the obvious choice. It cannot be used here: Room's annotation
processor needs KSP, and **KSP has no release built against the Kotlin that AGP 9.2.1 embeds**. The
newest KSP is 2.3.11, built for Kotlin 2.3; AGP 9.2.1 carries Kotlin 2.4.0. There is no version pair
that works, and downgrading AGP to reach one would give up the whole toolchain this project is
pinned to.

So the schema is hand-written SQL executed by an `SQLiteOpenHelper`. The arrangement is deliberately
shaped so the move to Room stays mechanical if KSP catches up:

- **All SQL lives in `SchemaSql`, a plain Kotlin object with no Android imports.** That is what
  makes it testable at all here. A migration meets a phone holding somebody's data exactly once, and
  there is no emulator in this environment — so the statements are executed against a real SQLite
  engine (`org.xerial:sqlite-jdbc`, test scope) in unit tests that run on this machine.
- **Migrations are keyed by the version they produce**, and asking for a version that has none is an
  error rather than a silent skip.
- **`SchemaSqlTest` checks that a fresh install and a fully migrated database end up identical.**
  Updating `CREATE` after adding a migration is the step everyone forgets, and the symptom — a
  schema that differs between an upgraded phone and a new one — surfaces far from its cause.
- **Going down recreates rather than refusing to open**, per AGENTS.md §10. Installing an older
  build over a newer one happens whenever anyone tests from a file, and the default behaviour leaves
  an app that cannot start at all until its data is cleared by hand.

### What is stored

`playlists`, `tracks`, `playlist_tracks` (ordered membership), `granted_folders` (SAF grants, so
browsing does not begin at a file picker every session), and a single-row `player_state`.

The schema already carries several playlists although the UI offers one, so R6 arrives without a
migration. `player_state` is one row enforced by a `CHECK` rather than five key-value rows: settings
that come as a set should not be able to exist half-written.

**Restoring makes a track current without starting it.** Coming back to the app is not a request to
make noise, and R2 asks for the view to be restored, not the playback. Resuming a *position* is
`docs/OPEN_QUESTIONS.md` Q6 and still open.

## 10. One player per process, and a service to keep it alive

Decided 2026-09-01.

Playback used to live in the ViewModel. That cannot work: a ViewModel dies with its screen, and
playback has to outlast it. Worse, a service with its own copy would mean two players agreeing by
accident.

So `PlaybackController` is a process-wide singleton and owns everything — the queue, the open
module, the store. The ViewModel forwards to it and `PlaybackService` holds the same instance. There
is one answer to "what is playing" no matter who asks.

### Why the service starts before playback

Android only permits a foreground service to be *started* while the app is itself in the foreground.
Waiting until the user leaves is waiting until it is no longer allowed, so the service starts on any
press that makes noise. That has a consequence worth stating: at start-up there is legitimately no
track yet, so "nothing playing" must not mean "stop the service" — only an empty state that follows
a non-empty one means playback is really over.

It also has a deadline. Android gives a started service roughly five seconds to call
`startForeground`, and the first track is still being read off disk.
So the notification is posted immediately with whatever state exists and updated afterwards.

### What is missing

**No `MediaSession`.** Lock-screen transport, Bluetooth and headphone buttons do not work; the
notification's own actions do. §4 picked Media3's `SimpleBasePlayer` for this — **§12 records what
was built instead** — and it was a separate step because it was a separate risk: doing it alongside
the service would have meant debugging two new things at once. Recorded in
`docs/BACKLOG.md`.

**No audio focus.** Another app starting playback will talk over us, and a phone call will not duck
us. Same step.

## 11. Browse has domains, and online catalogues live in the database

Decided 2026-09-01, after the owner asked for Browse to start with *where to look* rather than with
"add a folder".

Four domains: **local filesystem**, **online catalogues**, **random**, **search**. Below that it is
the same list-and-tick machinery whatever the source, which is why one component covers all four —
a folder of files and an author's page in an archive are the same problem once the rows exist.

### The index goes in the database, the files do not

A catalogue's index is downloaded once and stored, so **browsing an online archive needs no
network**. The network is next needed only when a track that is not cached is played. Modland's
whole index is a 5.75 MB zip of about half a million lines; kept as rows it is browsable by format
and author instantly and offline.

Only entries a backend might handle are indexed. Modland lists formats nothing here can play yet,
and indexing them would cost minutes and disk to browse a list of tracks that cannot be opened.
**Re-index after adding a backend** — recorded in `docs/BACKLOG.md`, because otherwise it is
discovered by wondering where the SIDs went.

### A remote track's identity is its URL

It has no document URI and never will. The URL is what the cache keys on and what the player opens,
so it is what `TrackRef.id` holds — the same field a local track keeps its `content://` URI in. The
player branches on the scheme and nothing else in the app has to know the difference.

### The cache is not an optimisation

A measured Modland fetch took four seconds for 212 KB, and the owner's own local library sits on an
network, so "read the file" is a round trip either way. Fetched bytes are cached by a hash
of the URL — hashed rather than sanitised, because real Modland paths carry spaces, slashes, `@` and
`$`, and any escaping scheme would eventually collide. Downloads land through a temporary file, so
an interrupted one cannot leave a truncated file that looks valid forever after.

The eviction budget is still `docs/OPEN_QUESTIONS.md` Q5 and still open.

### Only Modland so far

Each archive publishes its contents differently, so parsing belongs to the catalogue rather than
being shared. `Catalogue` is a sealed class with one implementation; adding ASMA, AMP, Aminet or
ModArchive means writing its index parser and, in most cases, the backend that can play what it
holds. The UI says as much rather than showing archives that would open on nothing.

## 12. The media session is the platform's, not Media3's

Decided 2026-09-01, changing the plan recorded in §4.

§4 chose Media3's `SimpleBasePlayer` so a `MediaSession` could be had without writing an ExoPlayer
renderer for a synthesiser. By the time the session was actually needed, that reasoning had stopped
applying: `PlaybackController` already is the player, with its own queue, transport and state flow.
Media3 would have meant a dependency plus an adapter that translates our player into a `Player` so
Media3 can translate it back into a session.

`minSdk` is 29, so `android.media.session.MediaSession` is available directly. It gives the lock
screen, Bluetooth and headphone buttons, and `Notification.MediaStyle` binds the notification to it.
No dependency, no adapter.

Two details worth keeping:

- **Seeking is advertised only when the backend can do it.** sc68 emulates a 68000 and cannot seek;
  a lock screen offering a scrubber that does nothing is the same lie as an app that does.
- **A media button says which action it wants, so the controller has an explicit
  `togglePlayPauseTo(play)`.** Toggling on a stale idea of the state is how a headphone press ends
  up pausing something that was already paused.

If Media3 is ever wanted for something else — Android Auto's browse tree, say — this does not stand
in the way; the session is created in one method in `PlaybackService`.

## 13. Catalogues come in three shapes

Decided 2026-09-02, when ASMA and The Mod Archive each added a shape that did not fit the original.

**Indexed** (Modland): a downloadable list of what is in the archive, and files fetched individually
by URL. Browsing is offline because the index is; playing needs the network.

**Archive** (ASMA): the whole collection is one download — 20 MB holding 6,335 `.sap` files — so
there is no separate index at all. **The archive's own entry list is the index.** Downloading it
once makes browsing *and* playing work with no network, at the cost of taking everything whether you
wanted six files or six thousand.

`Catalogue.isArchive` says which, and two things follow from it:

- The downloaded bytes are **kept** rather than parsed and discarded, in `filesDir` rather than the
  cache — the system may clear a cache directory at any time, and losing 20 MB to a sweep would mean
  fetching it again.
- A track's id is `<catalogue>://<entry>` and the player reads it out of the stored zip. Which
  catalogue that is comes from asking the catalogue list, not from matching a name: the next archive
  catalogue would otherwise be added without anyone noticing that line existed.

ASMA's paths run `asma/<section>/<author>/<title>.sap`, and the section — Composers, Games,
Unknown, Misc, Groups — becomes the top browse level. Every file in it is a SAP, so browsing by
format would offer one choice; browsing by section is the useful hierarchy. Checked against the real
archive: 730 entries have no author folder and keep an empty author rather than being dropped.

**Live search** (The Mod Archive): the archive provides no monolithic index file to download offline.
`Catalogue.isOnlineOnly` marks it as search-driven: it participates directly in the Search domain filters
without requiring an index download, query results are fetched live via its web endpoint, and individual
modules are streamed and cached via direct HTTP URLs on demand.

## 14. Where a duration comes from

Most backends know how long the thing they are playing is, and are asked. Two do not, for opposite
reasons, and only one of them is fixable.

A **SID is a program**. It plays until stopped, and "how long is it" is a question the format cannot
answer — not one it answers badly. HVSC answers it by hand: somebody listened and wrote a number
down, for 61,157 tunes, published as `DOCUMENTS/Songlengths.md5`. So the rule is: **take the
backend's duration; when there is none, ask HVSC.** Never the other way round — a format that knows
its own length knows it better than a lookup on a hash could.

The lookup key is the **plain MD5 of the whole file**. That is worth writing down because it used
not to be: older HVSC releases keyed the database on a hash of selected header fields, which
libsidplayfp still exposes as `SidTune::createMD5`, and code written from the old documentation
would find nothing and look merely empty rather than wrong. Verified by fetching three tunes from
the collection and finding all three by file MD5.

The database is downloaded on request rather than with the first SID, because 5 MB is not something
to spend at the moment somebody presses play. It is not a catalogue — nothing in it is playable —
so it sits below the catalogue list rather than in it. All subsongs' lengths are stored, not just
the first, so the table survives subsongs becoming selectable (`docs/BACKLOG.md` A2).

**Position** for a SID is counted rather than asked, for the same reason: libsidplayfp is running a
machine and has no position to report. Frames handed to the output device is the same quantity by
another route, and it is the one the listener is hearing. **Seeking is still impossible** — the only
way to a position in a SID is to run the machine there — so the scrubber shows progress and does not
accept a drag.

The other backend without a duration is a tracker module with an unbounded pattern loop, where the
length genuinely depends on what the tune does. Nothing here fixes that.


### And what the duration is *for*

Until 2026-09-04 it was for the label and the progress bar, and nothing else. That left a hole the
owner walked into: a SID played for ever. `isFinished` is set by the engine when a backend hands
back a short buffer, and libsidplayfp never does — it is running a 6502 in a loop and has no idea
the music is over — so nothing ever ended the track and the playlist never moved on.

**So a known duration now ends the tune.** The position poll already had both numbers; reaching the
length calls the same `handleTrackEnded` a real end calls, so repeat, shuffle, subsongs and Random
all behave identically whether the tune ended by itself or by the clock. This is not SID-specific
and deliberately so — any backend that loops for ever now stops when something knows better.

**It trusts whatever supplied the number.** A wrong HVSC entry cuts a tune short; that is the trade
every player using these databases makes, and the alternative is the one being fixed.

**Per subsong, which is what made this more than one line.** HVSC stores a length for every tune in
the file and only the first was ever used. Switching subsongs cleared the duration and waited for a
backend that would never answer — so tune two onwards showed no length, and would have played for
ever again the moment the rest of this worked. The list is now kept for the open file and indexed by
subsong.

## 15. History is not a log

Added 2026-09-02 with `docs/WISHLIST.md` B8.

The question it exists to answer is **"that tune two days ago, what was it"**. Everything arguable
about the design follows from that being the question, and would be decided the other way if the
question were "audit this app":

- **One row per track, not one per play.** `played_at` moves and `play_count` rises. A true log
  fills with a repeat-one track fifty times over and buries the thing being looked for.
- **It forgets.** Five hundred tracks, oldest first out, pruned in the same transaction as the
  insert so a crash cannot leave the table over its limit.
- **The rows are self-contained** and reference nothing. A tune played from Random or from a search
  result is never added to a playlist and so has no row in `tracks` — and those are exactly the
  tunes this list exists for. A foreign key would have given history only for music you had already
  decided to keep, which is the opposite of the point.
- **Recorded after the metadata is read**, so it stores the name the tune calls itself rather than
  the filename it arrived under.

It is a browse domain rather than a screen of its own, which means playing from it and adding from
it are the same code as everywhere else, and cost nothing.

**One trap worth keeping written down.** The natural way to write "insert or bump the count" is
`INSERT ... ON CONFLICT ... DO UPDATE`. That is SQLite 3.24, and **API 29 ships 3.22** while
`minSdk` is 29 — so the natural way crashes on the oldest device supported. Nothing in this project
would have caught it: the JVM tests run against a current SQLite through `sqlite-jdbc`, and there is
no emulator here. The statement is written as `INSERT OR REPLACE` with the previous count read back
in the same statement, and it lives in `SchemaSql` where a test can reach it.

## 16. Sharing has to copy

Added 2026-09-02 with `docs/BACKLOG.md` A10.

Neither place this app keeps a file can be handed to another app:

- A **local** file is a storage-access-framework document URI plus a permission grant belonging to
  *this* app, and a grant cannot be passed on. Sending the URI sends a string the receiver cannot
  read.
- A **downloaded** file is inside app-private storage, which nothing outside the app can see.

So a share copies the bytes into a cache directory that a `FileProvider` is allowed to vouch for,
and shares that. The provider is declared with `exported="false"` and a single path — the shared
directory and nothing else — because a share should expose the one file being shared and not the
library, the database or the 20 MB archive. Copies are swept an hour after they are made rather
than on the next share: the receiving app reads the file after the chooser closes, and deleting the
previous copy the moment a new share starts would sometimes pull it from under a slow reader.

**A link is a different feature, not a cheaper one.** It exists only for catalogue tracks, and what
it contains is the catalogue's answer rather than ours. Modland serves every file over HTTP, so its
track URL *is* the link. ASMA publishes one archive and has no per-file address at all, so
`webUrlFor` returns null and the share names the collection and the path inside it. That is
deliberate: an `asma://` reference means nothing on anyone else's phone, and an action that appears
to work is worse than one that says what it can do.

The owner settled the open part on 2026-09-02: **a link points at the file**, the same bytes that
are playing, and what the recipient does with it is theirs to decide. That is what Modland's link
already was, and it is what ASMA cannot offer — which is why ASMA's share is a collection and a
path rather than a worse link.

## 17. How a list behaves

Settled with the owner on 2026-09-02, after he said list handling was what irritated him most about
the app. The complaint that started it: **tapping a track selected it instead of playing it**, which
is a thing almost nobody does.

### The two modes

| | Normal | Selecting |
| --- | --- | --- |
| Entered by | default | **long-press** on a row |
| Leading slot | nothing | a checkbox |
| Tap a row | **plays it** | ticks or unticks it |
| Trailing slot | a three-dot menu | nothing — the checkbox is the affordance |
| Back | up one level, then out to the playlist | **leaves selecting, ticking nothing** |

Long-press to select and tap to act is the Android convention, and the app was the odd one out. The
cost is real and was accepted deliberately: adding several tracks now takes a long-press first,
where it used to take a tick. In exchange the common case — hear this one — drops from two taps to
one, and listening is more frequent than collecting.

**Back clears the selection to nothing.** The owner suggested keeping the first or playing track
ticked; this does not, because *playing* and *selected* are different states and only one of them is
a mode. A cancel that leaves something behind reads as an app that did not quite listen.

### The back stack in Browse

Three jobs, in this order, each one step:

1. leave the selection, if there is one
2. up one browse level
3. out of Browse, back to the playlist

That is what makes back predictable, and it is also why there is a separate, always-present way out:
from four levels deep the stack is correct and still four taps. The header carries a labelled
**Playlist** action that leaves Browse in one press from any depth.

**A jump is not a descent.** "More from this author" puts you three levels deep without your passing
through any of them, so back there returns you to the playlist rather than climbing a hierarchy you
never climbed. Any ordinary navigation from the landing place clears the mark and the stack above
applies again.

### Nothing may move when selection starts

Rows keep a reserved leading slot and a floor on their height whether or not a checkbox is showing,
and the header keeps a fixed height whether or not the select-all button is there. Without that,
long-pressing a row made the list grow under the very finger that had pressed it.

### What is not numbered

Rows in Browse carry no ordinal. In the playlist the number answers "where am I in *my* list of
three hundred", which the owner defended and is right; in Browse it would only say "row n of
somebody else's archive". The space goes to the checkbox instead, so rows do not change width when
selection begins.

### Actions are icons with labels

See `AGENTS.md`. Every control that does something is an icon with its name underneath, through
`LabelledAction` — one shape, one implementation, so a row of them reads as a set of equals. Their
width comes from the caller as `weight(1f)` rather than a fixed size: a fixed cell only produces
equal columns on a screen wide enough to hold them, and on a phone the extras wrapped out of sight
and read as missing.

### One name for the panel: **Now Playing**

Settled 2026-09-04, because it had two. The composable is `NowPlaying`; the documentation said
"the expanded player", which describes how you reach it rather than what it is. One name now, in
code and in prose. It is the panel that opens upward from the dock and holds the seek bar, the
file's metadata, the actions and — when there is more than one tune in the file — the subsong strip.

### The subsong strip follows the music only while you are watching it

A file can hold 256 tunes and the strip is a row of 256 chips, so it moves itself to keep the
playing one in view. Following unconditionally is what that used to mean, and it makes the far end
of a long file unreadable: scroll out to tune 240 to see what is there, and the moment the current
tune ends the strip snaps back to tune 68.

**The signal for "am I watching the music" is the playing chip itself.** If the tune that just
finished was on screen, the user is looking at the playing area and following is what they want. If
it was not, they are reading somewhere else and are left there. No toggle, no timer, no tracking of
who scrolled last — the thing being followed answers the question by being visible or not.

Opening a file is the exception and always moves, because that is being *taken* somewhere rather
than *kept* somewhere — and it is the one moment the strip must move, since a KSS can open at tune
47 of 256 (`docs/PLAN_FORMATS.md` §2).

**And it moves the minimum.** `bringIntoView` puts an item at the *start* of the view, which is
right when you are being taken somewhere and wrong when you are being kept somewhere: following
with it pinned the playing tune to the left edge and put everything before it out of reach, on every
single advance. `keepInView` does nothing at all when the item is already visible.

### A row's own actions live behind its three dots

A track row in Browse and in the playlist puts everything you can do *to* that track behind an
overflow menu, and since 2026-09-04 a playlist row in the switcher does the same: **rename, export,
delete**. Before that the switcher had three shapes for the same idea — rename appeared only on the
active row, delete sat beside it as a bare icon, and export was a button at the bottom that silently
meant "whichever one is open". Naming the playlist in the menu you opened is also what makes export
answer the obvious question, "can I export one I am not listening to".

**Import is deliberately not in that menu.** It does not add to the row you opened; it creates a
playlist of its own, always. It sits next to *New playlist* instead, because those two are the only
things here that bring a playlist into existence — an "Import" among a row's own actions would
promise precisely what it refuses to do.

### Anatomy

Browse, search and history rows share one anatomy, and the playlist row is the same thing plus a
drag handle. That is the only genuine difference between them: the playlist has an order that
belongs to the user.

### The plus

Playing something from Browse used to offer no way to keep it — the dock's **+** existed only for
Random. It now appears whenever something outside the playlist is playing, which is the natural
moment to decide you want to keep a tune: just after hearing it.

**And it says so — as does every other way of adding, since 2026-09-04.** The rule used to be
"silent when the rows appear, spoken when they do not", and the silent half kept being wrong. Adding
from a local folder closes Browse and lands on a playlist whose visible part may not change at all:
the new rows are at the end. Scrolling to them is not something a person reads as an answer, and the
owner reported the silence as a fault twice, in two different places.

The reasoning behind the silence was that a notice covered the very rows it reported. That
collision was fixed separately when the snackbar became swipeable, so what remained was a rule
defending against a problem that no longer existed.

### Adding to other playlists

Every row menu allows adding to another playlist directly (`docs/WISHLIST.md` B18). Unlike the
active playlist whose modifications form a temporary draft waiting for Save or Discard, writes to
another playlist happen immediately on disk to avoid managing multiple concurrent dirty drafts in
memory. A snackbar explicitly names the target playlist to confirm the addition since the target
list is not currently on screen.

### Deliberately still open

- **A4** — the same gesture in the *playlist*, and the bulk actions it would need there. The gesture
  is agreed; which actions the playlist gets is not, and guessing them is not the job.
- Mis-tapping a remote track now costs a download rather than nothing. `docs/WISHLIST.md` B17.

## 18. The local library is scanned by opening, not by reading names

Added 2026-09-03 with `docs/BACKLOG.md` A6 and `docs/STATUS.md` C4.

A folder used to be listed by walking the tree and keeping files whose **name** looked playable. Two
things were wrong with that and both bit: a module named `readme.txt` was invisible, and a
photograph named `tune.mod` was added and refused only when the user pressed play. The scan now
lists everything and hands each file to the same `NativeEngine.open` that playback uses. **What a
file is, is decided by a decoder accepting it.**

`MediaScanner.worthReading` is the only filter, and it takes the display name as a parameter it
deliberately never reads. That is not an oversight to tidy: the parameter is there so that the day
somebody reaches for it, the change shows up in a diff and fails a test. Size is the one thing it
judges on, and size is content rather than a name — nothing here is above a few megabytes, and
reading a four-gigabyte film off a network share to learn it is not a SID helps nobody.

### It has to be stored, or it is unaffordable

Opening every file in a library is minutes on a network share, so the answer goes in `library_index`
and later launches read it. **Scanning is never a side effect**: not of launching, not of returning
to the app, not of opening a folder. The one moment it starts without a second press is when the
user grants a folder, because granting a folder *is* asking for it to be usable.

### An index made by decoders expires with them

Every row records which decoder set produced it. This is not theoretical: replacing sc68 2.2.1 with
3.0.0b on the same morning took `.sndh` from 14 of 30 to 30 of 30, so every "nothing can play this"
the old set had written down became wrong at once. A folder whose rows name a different set is
reported stale and offers a rescan. `docs/BACKLOG.md` A7 has the same problem for catalogue indexes
and solves it with a note asking a human to remember.

**Catalogue indexes are still filtered by name, and that is right rather than a shortcut** — a
catalogue index is a list of filenames on somebody else's server, and deciding by content would mean
downloading half a million files to find out.

**Which is why the recorded set is the decoders *and* the name list, since 2026-09-04.** Recording
only the decoders was half the truth, and the missing half bit the same day it was noticed: five
names were added to `SupportedFormats` for formats libopenmpt had always been able to play, and
every index in existence was instantly missing 5,558 Modland files while reporting itself perfectly
current — because no decoder had changed. `SupportedFormats.fingerprint` is folded into
`NativeEngine.backendsFingerprint`, so a name added to that list expires exactly the indexes it
would have changed, with nobody having to remember.

### Scanning while music plays

Measured rather than assumed, and the answer changed. sc68 **2.2.1** kept its 68000 emulator in
global state, so opening a second instance while one played clobbered it — which is why background
metadata resolution in this app waits for playback to stop, and why probing a whole library looked
blocked before it was started. sc68 **3.0.0b** is instance-based, and
`native/probe/sc68/probe_concurrency.c` runs four threads each creating, loading, playing and
destroying its own player twenty times: no failures, every thread producing audio. So a scan runs in
the background and the music keeps playing.

## 19. What the cache keeps

Added 2026-09-03, answering `docs/OPEN_QUESTIONS.md` Q5. Before this, nothing was ever deleted.

**512 MB, least recently used first.** These files are kilobytes to a few megabytes, so that is
thousands of tunes — a library rather than a cache. It is a chosen number rather than a measured
one, and exposing it as a setting is `docs/BACKLOG.md` A13.

Enforced **after every successful write** and **once at start-up**. The second is what makes an
installation that grew past the limit before the limit existed converge on it instead of sitting
over it forever.

### What counts

Only the fetched-file cache, and that is structural rather than a rule anybody has to remember —
the things that must survive live in different directories entirely:

| | where | counted |
| --- | --- | --- |
| fetched tracks | `cacheDir/remote` | **yes** |
| ASMA archive (20 MB) | `filesDir/catalogues` | no |
| HVSC song lengths, the library index | the database | no |
| copies made for sharing | `cacheDir/shared` | no |

### What is never evicted

- **Unfinished downloads.** A `.part` file is a fetch in progress. The budget neither charges for
  it nor deletes it: charging would bill the user for bytes that may never become a file, and
  deleting would corrupt a download that is still running.
- **Anything in use** — the track playing and the ones read ahead. A file deleted mid-read looks to
  the user exactly like a corrupt download.

A protected file still **counts**. It is real disk taken by real bytes, and excluding it would let
the cache sit above its ceiling while reporting that it does not. When protected files alone exceed
the ceiling, everything else goes and no more: deleting what is playing to satisfy an arithmetic
target would be the cache breaking the app in order to obey itself.

### And what the user can throw away themselves

**Since 2026-09-04 everything the app stores has a delete next to it**, in a Storage section on the
Online catalogues screen: the fetched-music cache, each downloaded archive (ASMA's is 20 MB), each
catalogue index, and the HVSC song lengths (5.2 MB). Before that, the app took disk and said the
number without offering any way to give it back — which is better than taking it quietly and still
not good enough on a phone.

**A catalogue is one row, not two.** For an archive catalogue the downloaded zip *is* the index —
ASMA publishes a single file that is stored whole and parsed in place — so offering the file and the
rows as separate deletes offered two ways to break one thing. Taking either left the other
describing a catalogue that no longer worked.

**One rule holds the section together: everything on it is a copy.** Each item came from somewhere
that still has it, and each is re-fetched by a button already on that screen. The user's own things
are deliberately absent — playlists, granted folders and history are not storage to be reclaimed,
and a screen that mixes "this is a cached download" with "this is your work" teaches people to be
afraid of it.

The fetched cache deletes without asking, because it refills itself as music plays and a mistaken
tap costs one slower song. Everything else takes a real download to get back and asks first, naming
what stops working until it is fetched again.

**It lived in Browse until 2026-09-04 and has moved to Settings**, in one piece, which is what the
placeholder note here promised. Browse is for finding music; what the app is keeping on the phone
is not that.

### Why it is a separate object

`CacheBudget` has no Android imports and **no clock** — it is handed a list and returns names to
delete. Reading the directory and deleting is `RemoteFiles`'s job. That split is the whole reason
the rules have tests: Q5 stayed open for weeks partly because there was nowhere to write one. The
ordering has a name tie-break for the same reason — a rule that cannot be predicted cannot be
tested.

**A ceiling stops growth; it does not give the disk back.** Nothing in the app deletes the ASMA
archive or the song lengths (A13). The online screen at least says what is held, because an app that
takes disk quietly is worse than one that takes the same disk and says so.

## 19b. Settings, and the gear that opens it

`docs/BACKLOG.md` A13, built 2026-09-04.

**A full-screen modal, exactly like Browse.** Same shape, same back rule, same way out. That is the
whole reason for the choice: `docs/OPEN_QUESTIONS.md` Q1 describes a navigation model with one rule
— back descends one layer — and a second modal costs nothing to learn. A bottom sheet was the
alternative and fights the dock as soon as it scrolls.

**A labelled gear, not an overflow menu.** A menu holding a single item is worse than the button it
hides. The first version used an unlabelled gear; the owner rejected it on 2026-09-04, consistently
with the application's rule that an action has both an icon and its name. Browse sits on the left,
beside the playlist chooser with a 12 dp gap, while Settings stays on the right. This keeps the two
ways of choosing what plays together without making them look like one compound control.

**A choice you can see is better than a choice you have to open.** Language and theme are both
"pick one of three", and both were a dialog: a tap to find out what the options are, and then the
answer hidden again behind a summary line. They are segmented buttons now — every option and the
current answer visible at once, changed in one tap. `SettingChoice` is one component used twice,
because two controls that behave identically should be one piece of code or they drift.

**Changing either recreates the activity**, exactly as a language change already did. A theme
applied after Compose has drawn is a flash of the wrong colours, which is the visual equivalent of
the half-translated screen `AppLocale.wrap` exists to prevent — so both are read in
`attachBaseContext` and `setContent` rather than remembered in composition.

**The wallpaper palette is a separate switch, and absent below Android 12.** It is a different
question from light-or-dark — *where do the colours come from* rather than *how bright are they* —
and the platform below 12 has no palette to take, so offering a switch there would be a promise it
cannot keep. On by default where it exists, because that is what a Material app is expected to do.

**One scrolling screen with sections, not a tree.** With this little in it a tree is ceremony, and
every extra tap stands between somebody and the thing they came to change.

**Nothing in it was invented for it.** Every switch is a setting the app already had somewhere less
findable — "play every tune in a file" was global all along and reachable only from inside Now
Playing, on a file that happened to have more than one tune. Storage moved from Browse. Language is
chosen inside the application from System, Polish and English on every supported Android version.
The choice is stored privately and applied to the activity context before Compose reads resources;
returning to System removes the override rather than copying today's system language into it.

**About is not decoration.** A GPL-3 app distributed through a store should be able to say what is
inside it: the version, the decoders and their versions — read from the engine's own fingerprint,
the same string an index records to know it is stale — and where the source is.

## 20. Where the time goes on screen

Changed 2026-09-03, at the owner's suggestion and in his layout:

```
0:36  ─────────●──────────────  2:20
```

Position on the left, length on the right, the bar between them. Before, both sat beside the title
as `0:36 / 2:20` — one string to decode, in a row that had become crowded once the tune count joined
it. Flanking the bar, the two numbers read as **where this line starts and where it ends** rather
than as a fraction to parse.

## 21. The dock is sized for a glance, not for a desk

Changed 2026-09-04, and the reason is the specification: *"when I am driving it is hard to hit
them."*

Material's 48dp touch target is the minimum that counts as reachable when you are sitting still and
looking at the screen. The transport is **56dp with a 30dp glyph**, and play is **64dp with 34**.
The glyph grows with the target deliberately — a large button around a small mark is harder to aim
at than a small button, because the eye aims at the mark.

The title moved from `titleSmall` to `titleMedium` and the author from `bodySmall` to `bodyMedium`,
and **the dock grew to fit** rather than the text being squeezed to preserve its old height. The
owner offered that trade before being asked for it, which is the right way round: the dock is on
every screen, and the thing it is for is being read at a glance.
