# Plan: the browser as a second player, not a second screen

*Asked for by the owner, 2026-09-10: **"web powinien mieć możliwość przeglądania archiwów… podział
playlisty (zawsze dostępna from the phone + customowe), przeglądanie katalogów online tak jak na
telefonie… ostatecznie też random i historia ma działać — słowem ma działać jak w apk."***

---

## What this reverses, and it should be said first

`docs/PLAN_HANDOFF.md` §5a is explicit that the browser is **not** a second app: *no browsing, no
search, no settings.* That was the right call for what the page was for — one wish, "hear my phone's
playlist at my desk" — and it is why the page is 1,300 lines instead of 13,000.

This asks for the opposite, and the cost is not the features. It is that **the rules stop having one
home.** `PlayQueue`, `SubsongAdvance`, `PlayFromEnd` and `SearchResults` are pure Kotlin, extracted
and tested precisely so playback behaves the same everywhere; a browser cannot run them. Every rule
this plan touches becomes two implementations that must agree, and "must agree" is a promise nobody
keeps by hand — C23, C30 and C31 were all one screen doing what the other did not, inside a week.

**So the first item below is not a feature.** It is the thing that decides whether the rest ages
well.

---

## The measured ground

### What a browser is allowed to fetch, checked 2026-09-10

| | `Access-Control-Allow-Origin` | so the page can |
|---|---|---|
| modland.com — index **and** files | `*` | **do everything itself** |
| asma.atari.org — the 20 MB archive | `*` | **do everything itself** |
| raw.githubusercontent.com — songdb | `*` | **do everything itself** |
| files.exotica.org.uk — UnExoticA | none, through the redirect too | nothing without a relay |
| modarchive.org — search | none | nothing without a relay |

**Two of four archives are open and two are shut.** That single table decides most of the design
below, and it was worth ten minutes to know rather than assume.

### What indexing Modland costs, in a runtime like the browser's

`allmods.zip` is 5.76 MB. In node, which is the same JavaScript engine a Chromium browser runs:

| | |
|---|---|
| inflate | **79 ms** |
| parse 515,509 rows | **482 ms** |
| the decompressed text | 30.8 MB |
| **all rows as objects, at once** | **~218 MB of heap** |

The last row is the one to design around. Half a million small objects is fine on a desktop and is
not fine on a phone browser — and the page is opened on a phone often enough (that is what the
pairing code is for). **Nothing may hold the whole index in memory at once**; it has to stream from
the zip into storage in batches.

### Measured by the owner, 2026-09-10, on two machines

`web/tools/storage-check.html`, 60,000 records of Modland's real shape:

| batch | | at work | at home |
|---|---|---|---|
| 1,000 | | 8,966 rows/s | 16,741 rows/s |
| 5,000 | | 9,878 | 17,616 |
| 20,000 | | **12,552** → 41 s | **18,916** → **27 s** |
| 5,000 | **with the `(format, author)` index** | 7,176 | 13,387 |

**The index costs about a quarter of the throughput** — 27% at work, 24% at home, comparing the two
runs that share a batch size.

*An earlier version of this table said 43%, which was wrong: it compared the indexed run against the
fastest unindexed one rather than against the same batch size. The number was a quarter of the way
to being nonsense and the conclusion below survives it, but a measurement compared against the wrong
baseline is the third time in two days that a comparison, not the thing measured, was the fault.*

**Between the machines it is 1.5×, and both agree on the direction.** Twenty-seven seconds is
workable and forty-one is a wait; either way the structure below removes the question rather than
answering it.

**Still unmeasured, and it is now the only number that matters:** the design in S3 does not write
half a million small records at all. It writes 43,715 large ones — the same 30 MB in an eleventh of
the transactions, which is a completely different balance between per-record overhead and payload.
Extrapolating one from the other would be the guess this page exists to avoid, so the page writes
the real thing and reports it.

---

## Where the index lives — the decision this plan rests on

**Two honest options.**

**In the browser (IndexedDB).** The page fetches `allmods.zip` itself, streams it into a store, and
browses with no server involved. It is what the phone does, one layer down.

**On the server (`serve-web.mjs`).** The Pi already holds the page and the pairing rooms; it could
hold the index and answer queries. Much less code in the page and instantly fast on a phone.

**Recommendation: in the browser**, and the reason is what the server would become. `serve-web.mjs`
is three hundred lines of static files and pairing — small enough that the whole of it is read
before it is changed. Putting half a million rows and a query language in it makes it a database
server that has to be deployed, migrated and kept in step with the app's schema, and the project
gains a second thing that can be out of date. IndexedDB costs a one-time download and then works
offline, which is also what the phone does.

**The exception is forced anyway.** UnExoticA and The Mod Archive cannot be reached from a browser
at all, so if they are wanted in the page, `serve-web.mjs` gains a **relay** — a route that fetches
one URL on the page's behalf. That is twenty lines and no state, and it is a different thing from a
database.

---

## The pieces, in the order they can be tested

### S1. One set of rules, two runtimes

**Before any feature.** The queue rules exist twice already — `PlayQueue.kt` and the top of
`app.js` — and this plan multiplies that by browsing, search, random and history.

The cheap answer is not to share code; it is to **share the cases**. A JSON file of scenarios —
queue, mode, position, action, expected result — read by the Kotlin tests and by the page checks
alike. Neither implementation is the reference; the file is. A rule that changes on one side and
not the other fails on the side that did not change, which is what C23, C30 and C31 all needed and
none of them had.

- **Deliverable:** `docs/rules/queue-cases.json`, read by `PlayQueueTest` and by `check-page.mjs`.
- **Checked by:** deleting a rule from either side and watching the other side's suite go red.
- **Size:** small, and it pays for itself on the second bug.

### S2. Storage, and playlists that survive a reload

IndexedDB, because everything after this needs it and `localStorage` is a few megabytes and
synchronous.

Stores: `playlists`, `tracks`, `history`, `settings`, and later `catalogue`. Ask
`navigator.storage.persist()` — without it a browser may evict the lot under pressure, which for a
playlist somebody built by hand is data loss rather than a cache miss.

**Playlists as the owner described them:**

- **"From the phone"** — always present, cannot be deleted, and **replaced wholesale** by each
  handoff. It is a view of the last thing the phone sent, not a document.
- **Custom ones** — created in the page, edited in the page, kept in the browser. The phone knows
  nothing about them and should not.

The switcher is the phone's (`ui/PlaylistSwitcher.kt`) and the model is `PlaylistStore`'s, which is
worth reading first: the phone already answers "what happens to the current track when the playlist
changes" and the page must answer it the same way.

- **Deliverable:** a playlist chip that opens a list, "save this queue as…", and a page that comes
  back after a reload with what was playing.
- **Checked by:** him, on the Pi, across a browser restart.
- **Open:** how big may the browser's own copy get? A cap and an eviction rule, or an honest number
  on a storage screen — the phone has one and the page has nothing.

### S3. The index, and browsing it

Modland first, because it is the one that matters and the one whose numbers are above.

Stream `allmods.zip` through `DecompressionStream('gzip'/'deflate')` — already used for the queue
link — and write in batches with a progress count. **Never materialise the whole thing.**

**Not a row per track, and the owner's measurement is why.** An index on `(format, author)` costs
about a quarter of the write throughput, and it exists only to answer "which tracks are under this author" — a
question with a fixed, tiny answer set. Counted in Modland, 2026-09-10:

| | |
|---|---|
| tracks | 515,509 |
| formats | 339 |
| **`format` + `author` buckets** | **43,715** |
| largest bucket | 3,617 tracks |
| median bucket | **3 tracks** |

So store **a record per bucket**, holding its tracks, keyed by `format/author`. That is **11.8×
fewer writes and no index at all** — the key *is* the lookup, which is the structure an index would
have built anyway.

**How much that is worth is being measured rather than divided.** 43,715 records at his per-track
rate would be three or four seconds, but those records are eleven times larger and IndexedDB is not
priced per row alone. The tool writes the real buckets — median 3 tracks, the four biggest holding
3,600 — and reports the seconds and the megabytes on disk.

Two more things fall out of it rather than being designed:

- **Browsing is three reads, not three queries.** One record for the format list, one per format for
  its authors, one per bucket for its tracks — computed once while writing.
- **Random gets better than the phone's.** Pick a bucket weighted by its count, then a track inside
  it. `docs/review-round-8.md` R7 says the phone's `ORDER BY RANDOM()` over half a million rows is
  unmeasured and probably wasteful; this is the shape it may want to borrow.

**What it costs is search**, and S4 has to answer for it: matching an author or a format reads
43,715 keys and is cheap, but matching a *title* means reading every bucket — 30 MB of records.
That belongs in a worker and it belongs behind a measurement before it is promised.

The staleness rule is the phone's and is not optional: `SupportedFormats.fingerprint` decides
whether a stored index is still current, and the page must record and check the same string — the
engine reports it through `pt_backends`. An index built before a decoder arrived is missing files
and looks empty rather than out of date, which cost the owner 60,572 C64 tunes once already.

ASMA is the same shape with a different trade: its "index" **is** the archive, 20 MB, and once it is
in the browser its tracks need no network at all.

- **Deliverable:** Browse → Modland → format → author → tracks, offline after the first download.
- **Checked by:** the numbers. 516,107 rows in, and the same counts per format the phone shows.
- **Needs him:** whether a 5.76 MB download and a progress bar on first use is acceptable in the
  browser, or whether the page should offer it rather than assume it.

### S4. Search

The phone searches title and author with `LIKE` over an indexed table. IndexedDB has no `LIKE`, so
this is a **prefix** search over a lower-cased key, or a cursor scan with a cap. Prefix is what an
index can do quickly and is most of what a person types; substring needs a scan and should say so by
being slower rather than by being absent.

**`SearchResults.kt` is pure and its rules — scope, per-source caps, what the empty query means —
are already extracted.** This is the first place S1's shared cases earn their keep.

### S5. Random

The phone asks SQLite for `ORDER BY RANDOM() LIMIT n`, which `docs/review-round-8.md` R7 already
records as unmeasured and probably wasteful. IndexedDB cannot do it at all.

The browser's shape is better and the phone may want to borrow it: **pick a random key and take the
next record from a cursor.** With a filter (a platform, favourites) it needs a count per bucket,
which the browse tree needs anyway.

Read-ahead is the phone's rule (`READ_AHEAD = 3`) and the reason is the same in a browser: a tune
that has not been fetched is a gap between tracks.

### S6. History

`HistoryStore`'s shape, in IndexedDB. It is the smallest of these and the one with the least to
decide, which is why it is last rather than first.

### S7. The two archives that need a relay

**UnExoticA** and **The Mod Archive** cannot be fetched from a page. If they are wanted:

`serve-web.mjs` gains `GET /relay?url=…`, allow-listed to the hosts we already name, forwarding the
response and adding `Access-Control-Allow-Origin`. Twenty lines, no state, and it inherits the
verification-gate retry the Kotlin side already needed for ExoticA.

**It is a real decision, not a detail.** A relay means the Pi fetches on the listener's behalf, so
UnExoticA sees the Pi's address rather than his — which is the opposite of what the letter in
`docs/letters/2026-09-08-exotica-unexotica.md` promised them ("the user's own client fetching a file
they asked for"). **Ask them before building it**, or leave those two archives out of the page.

---

## What stays on the phone, and why

**The local folder library.** A browser has no folder to scan: the File System Access API is
Chromium-only and asks for a permission grant per session, which is a different thing from the
phone's persistent tree. Files reach the page the way they do now — the phone sends them — and MP3s
do not travel at all (A29).

So the page's library is **online archives plus what the phone hands over plus what the browser
itself saved.** That is not a smaller version of the app; it is the half of it that makes sense at a
desk.

---

## What the owner still has to decide

1. **The relay, and therefore UnExoticA and The Mod Archive in the browser** — it changes who
   fetches from ExoticA, and they have not answered the first letter yet.
2. **How much storage the page may take**, and whether it asks before the first 5.76 MB.
3. **Whether "From the phone" is one playlist or the newest of several.** Replacing it wholesale is
   simplest and is what he described; keeping the last few would let him go back to yesterday's
   queue, and the phone has no equivalent.
4. **Order.** S1 → S2 → S3 is the spine and each is testable on its own. S4–S6 can come in any
   order after S3, and S7 may never come.

---

## What is not in this plan

Accounts, sync, and a server that holds his music — `docs/WISHLIST.md` B5 raised that in September
and it is a different project. Everything above works with a static page, a browser's own storage,
and archives that already say a browser may read them.
