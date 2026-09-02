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
`startForeground`, and the first track is still being read off disk — over SMB, on the owner's setup.
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
SMB share, so "read the file" is a network round trip either way. Fetched bytes are cached by a hash
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

## 13. Catalogues come in two shapes

Decided 2026-09-02, when ASMA turned out not to fit the one that existed.

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
