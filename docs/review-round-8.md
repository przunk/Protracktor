# Code review, round 8 — 2026-09-10

*Asked for by the owner: read the **code**, not the documents, and look for plain defects,
inconsistencies, and architecture that is fragile or unpleasant. Findings first, in the order I
would act on them; what I then did is marked on each.*

Read: `native/engine/*` (engine, the Oboe host, the JNI), `native/lhasa/*`,
`app/src/main/kotlin/**` with attention to `PlaybackController`, `CatalogueStore`, `RemoteFiles`,
`PlaybackService`, and `web/src/*`. Roughly 19,700 lines.

**Two of these are live defects a user would meet**, and one of them is mine from yesterday. The
rest are hazards, costs and one piece of architecture worth a decision rather than a patch.

---

## R1. A file sent from the phone plays exactly once — **FIXED**

**Severity: high. Live. Reproducible without a device.**

`playAt` hands the bytes to the worklet with a transfer list:

```js
node.port.postMessage({ type: 'open', bytes, name: … }, [bytes]);
```

When those bytes came from the phone, `bytes` **is** `entry.data` — the queue's own copy. Transferring
detaches it. Measured, in node:

```
after one transfer, entry.data.byteLength = 0
```

So the second play of the same row sends a **zero-byte** buffer. In a browser the transfer of an
already-detached buffer throws `DataCloneError`, and it throws *outside* the `try` that would have
caught it — so the dock sits on "0 KB — opening…" until the ten-second watchdog, and the row never
plays again. Repeat-one, previous, and clicking the row are all this path.

Nobody has reported it because sending local files is two days old and nobody has replayed one.

**The two actions added yesterday had it too**: `saveFile` and `informAbout` both take `entry.data`
and the second transfers it.

**Fixed** by never handing away a buffer the queue owns: `bytesOf` and `playAt` transfer only bytes
they fetched themselves, and copy when the bytes belong to an entry. A `.slice(0)` of a 20 KB module
is not a cost worth reasoning about; a detached queue is.

## R2. `subsongCount()` reads the backend from the wrong thread — **FIXED**

**Severity: medium. Live, and it is `docs/review.md` R6 with one case missed.**

R6 established the rule and the reason: libopenmpt's own header says an object must be touched *from
a single thread at a time*, and game-music-emu's `gme_tell` reads state that `gme_play` mutates. The
fix published position and duration from the audio thread and left everything else alone.

`Player::subsongCount()` still calls straight through to the backend, and it is called from Kotlin
**after the stream has started**:

```kotlin
val started = opened.start()
…
subsongCount = opened.subsongCount().coerceAtLeast(1),
```

That is `get_num_subsongs()` and `gme_track_count()` on an object the audio callback is reading. The
same file, eleven lines earlier, explains that `describe()` is called *before* `start()` precisely
because of this race.

**Fixed** by publishing it as an atomic in the `Player` constructor, exactly as position and duration
are. The count does not change for the life of a file, so once is enough.

## R3. `describe()` is the same shape, and only convention keeps it safe — **FIXED**

**Severity: medium as a hazard, zero today.**

Every caller happens to describe before starting. Nothing enforces it, `nativeDescribe` is a public
JNI entry point, and the next person to want a title mid-playback will reach for it.

**Fixed** with the same pattern: the string is taken once in the constructor and refreshed by the
audio thread whenever a subsong switch is applied, behind a mutex that the audio thread takes only
when a tune actually changes. Reading it can no longer race with rendering whoever asks.

## R4. The phone keeps the previous tune's title after a subsong switch — **FIXED**

**Severity: low. Live.**

`PlaybackController.selectSubsong` updates the index and the duration and never re-reads the
metadata, so a GBS or SNDH that names each of its tunes goes on showing the first one's name while
the fourth plays. The web player had this until yesterday, where fixing it was forced by having to
answer a message; on the phone nothing forced it.

**Fixed** on the back of R3: the poll re-reads the description once after a switch has actually been
applied, the same way it picks up the new duration.

## R5. "Information" can wait for ever — **FIXED**

**Severity: low. Mine, from yesterday.**

`informAbout` posts a `describe` message and awaits the answer with no timeout. If the worklet never
replies — the failure this page has already met twice — the panel sits on "reading it…" and the
promise is never settled. `playAt` has a ten-second watchdog for exactly this and the new path did
not get one.

**Fixed**: same watchdog, and the pending entry is dropped.

## R6. The verification-gate retry reads one header by exact case — **HARDENED**

**Severity: low, possibly zero.**

`RemoteFiles.attempt` reads `connection.headerFields["Set-Cookie"]`. Android's `HttpURLConnection`
is OkHttp underneath and its header map is case-insensitive, so this almost certainly works — but
"almost certainly" is doing the work, the failure is silent (the retry never fires and a module is
an HTML page), and the honest version is three lines.

**Changed** to find the header without caring about case. Not called a defect, because I could not
show one.

---

## Left alone, deliberately

### R7. `randomSample` sorts half a million rows to pick one

```sql
SELECT … FROM catalogue_tracks WHERE … ORDER BY RANDOM() LIMIT ?
```

`catalogue_tracks` holds 516,107 rows for Modland alone. SQLite has to draw a random number for
every row that passes the filter and keep a top-N heap; there is no index that can help, because the
sort key is generated. Random reads ahead three at a time and `skipFailedRandomPick` may go round
eight times, so one press can be eleven of these.

**Not touched, because I have not measured it.** It may be 40 ms and irrelevant. The cheaper shapes
(a random `rowid` and a scan forward, or `LIMIT 1 OFFSET random`) all behave differently in the
presence of the filters and of gaps, and swapping a correct query for a faster one on a guess is how
Random starts returning the same tune. **The measurement is one script and should come first.**

### R8. `startForeground` is called unguarded

`PlaybackService.showNotification` calls `ServiceCompat.startForeground` with no `try`. On API 31+
that can throw `ForegroundServiceStartNotAllowedException` when a service is started while the app is
in the background. Every path that starts it today is a user action in the foreground, which is
exempt — so this is a crash that should not be reachable, guarded by an argument rather than by code.

**Not touched**, because catching it silently would hide a service that then gets killed at five
seconds anyway, and doing it properly means deciding what the app should *do* when it may not play.
That is the owner's call.

### R9. `PlaybackController` is 3,656 lines

It owns playback, the queue, the random walk, catalogue indexing, downloads, favourites, search,
subsongs, the web handoff, persistence, and the audio focus. Every one of those has needed a change
this week and every one of them touched the same file.

**This is the "obrzydliwa architektura" answer**, and it is not a patch. The seams are already
visible — `PlayQueue`, `SubsongAdvance`, `PlayFromEnd`, `QueueLink`, `SearchResults` were all pulled
out of it and each is tested because it is out. The next three are readable from the field list:

- **the catalogue side** — `indexCatalogue`, `downloadSongLengths`, `downloadTrackMetadata`,
  `downloadFavourites`, `downloadReplays`, `beginDownload`/`endDownload`, and the `DownloadKeys` that
  already had to be invented for it. It shares nothing with playback but the state object.
- **the random walk** — `randomHistory`, `randomCursor`, `fillRandomQueue`, `skipFailedRandomPick`,
  `randomNext`, and the failure counter.
- **the handoff** — `pairWith`, `postQueue`, `sendQueueToBrowser`, `sendQueueAsLink`, `localBytesFor`.

Each could move behind an interface the controller holds, with its own state class, and each would
then be testable without a phone. **Not started**, because it is a day's work with no user-visible
result, and because doing it while the owner is testing daily would put a large diff between him and
every bug report. It is a decision, not a defect.

---

## Also looked at and found sound

- **The threading discipline in `player_oboe.cpp`** is right and is the reason R2 and R3 are the only
  two holes: every control path stops the stream before touching the backend, `stop()` blocks until
  the callback returns, and seek and subsong switches are handed to the callback rather than done
  behind its back. The comments say why, which is why the two exceptions were findable.
- **The cancellation window in `load()`** — `openJob?.cancel()` then a new job that closes the old
  track. There is no interleaving, because both run on `Dispatchers.Main.immediate` and there is no
  suspension point between the `isActive` check and `track = opened`.
- **Every buffer bound in the six decoders.** `Sc68Backend::render`, `AsapBackend::render`,
  `HivelyBackend::render`, `SidBackend::render`, `GmeBackend::render` and `audible()` all write
  within `frames`, and each scratch buffer is sized before use. `Sc68Backend::worthTrying`'s window
  is `min(size - 4, 256)` and reads four bytes at `i`, which is in range for the last `i`.
- **The SQL is parameterised**, including the `IN (…)` lists, which are built from `?` and never from
  values. `LIKE` patterns escape `%`, `_` and the escape character itself.
- **No `!!`, no `GlobalScope`, no `runBlocking`** anywhere in the app sources.
