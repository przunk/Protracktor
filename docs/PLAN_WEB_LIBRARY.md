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

Both machines are **Firefox 155 on Windows**, so what separates them is the disk rather than the
engine.

| batch | | at work | at home |
|---|---|---|---|
| 1,000 | | 10,573 rows/s | 14,134 rows/s |
| 5,000 | | 11,019 | 12,840 |
| 20,000 | | **12,544** → 41 s | **17,720** → **29 s** |
| 5,000 | **with the `(format, author)` index** | 7,855 | 11,519 |

**The index costs about a quarter of the throughput** — 29% at work, 10% at home, comparing the two
runs that share a batch size. *The batch curve is noisy — home's 5,000 came in under its own 1,000 —
so the only claims worth making from this table are that bigger batches win and that the index is
never free.*

*An earlier version of this table said 43%, which was wrong: it compared the indexed run against the
fastest unindexed one rather than against the same batch size. The number was a quarter of the way
to being nonsense and the conclusion below survives it, but a measurement compared against the wrong
baseline is the third time in two days that a comparison, not the thing measured, was the fault.*

**Between the machines it is 1.5×, and both agree on the direction.** Twenty-seven seconds is
workable and forty-one is a wait; either way the structure below removes the question rather than
answering it.

### And then the shape that matters, measured on the slower of the two

The design in S3 does not write half a million small records at all. It writes 43,715 large ones —
the same 30 MB in an eleventh of the transactions. The tool writes those, all of them, with
Modland's real spread:

| | at work | at home |
|---|---|---|
| a record per track | 41 s | 29 s |
| **a record per bucket** | **2.6 s** | **1.9 s** |
| **the whole index, on disk** | **18 MB** | **18 MB** |

**Fifteen times faster on both machines, and better than the arithmetic said.** Dividing the per-track rate by 11.8
predicted three and a half seconds; the real answer is 2.6, because a larger record amortises the
per-transaction cost that dominates the small ones. That gap is the whole reason this was measured
rather than divided.

**So the question in the next section is settled: the index belongs in the browser.** Two and a half
seconds is not a progress bar, it is a pause — and it is once.

**And it answers a second question without being asked.** The browser offered **473 GB** at work and
**256 GB** at home, and granted `persist()` on both. Eighteen megabytes against that is not a budget worth writing code for: the storage
screen should *report* what the page holds, and there is no eviction rule to design. A hand-built
playlist is also safe from being swept, which was the only real risk in S2.

---

## Where the index lives — the decision this plan rests on

**Two honest options.**

**In the browser (IndexedDB).** The page fetches `allmods.zip` itself, streams it into a store, and
browses with no server involved. It is what the phone does, one layer down.

**On the server (`serve-web.mjs`).** The Pi already holds the page and the pairing rooms; it could
hold the index and answer queries. Much less code in the page and instantly fast on a phone.

**Settled: in the browser**, by his measurement above — 2.6 seconds for the whole index — and by
what the server would otherwise become. `serve-web.mjs`
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

**Built 2026-09-10.** `docs/rules/queue-cases.tsv` — 22 cases in four groups — read by
`RuleCasesTest.kt` and by `check-page.mjs`, and the page's own `web/src/rules.js` is what the second
one drives, so the rules are not merely checked but **used**.

**Tab-separated rather than JSON**, because a unit test has only a stub of `org.json` and would have
failed by finding nothing rather than by saying so. Everything else this project reads is a TSV
anyway.

**Verified by breaking it**: one expected value changed in the file, and both suites went red — the
page check naming the case, `RuleCasesTest` naming the group. That is the mechanism, and a mechanism
nobody has watched work is not one.

Two things are deliberately not in the file. **Shuffle**, because the two sides shuffle with
different generators and agreeing on a permutation would mean sharing an implementation. And
**stepping back through a file's tunes**, because on the phone that rule is still inline in
`PlaybackController.previous()` rather than extracted — C31's other half, and the next thing to pull
out.

### S2. Storage, and playlists that survive a reload

IndexedDB, because everything after this needs it and `localStorage` is a few megabytes and
synchronous.

Stores: `playlists`, `tracks`, `history`, `settings`, and later `catalogue`. Ask
`navigator.storage.persist()` — without it a browser may evict the lot under pressure, which for a
playlist somebody built by hand is data loss rather than a cache miss.

**Playlists as the owner described them**, and the rule he added on 2026-09-10 after using it —
*"te funkcje powinny działać tylko jak przełączę listę"*: **browsing never writes into the phone's
playlist.** Pressing a tune found by browsing while that one is showing does nothing but say to
switch or make one. A page that quietly rewrote it would make the two devices disagree about what
he built.


- **"From the phone"** — always present, cannot be deleted, and **replaced wholesale** by each
  handoff. It is a view of the last thing the phone sent, not a document.
- **Custom ones** — created in the page, edited in the page, kept in the browser. The phone knows
  nothing about them and should not.

The switcher is the phone's (`ui/PlaylistSwitcher.kt`) and the model is `PlaylistStore`'s, which is
worth reading first: the phone already answers "what happens to the current track when the playlist
changes" and the page must answer it the same way.

**Built 2026-09-10.** `web/src/store.js` — IndexedDB behind promises, two stores, and
`makePersistent()` asked once. The chip in the top bar is a button now, as it is on the phone; it
opens a sheet listing what there is, marks which is showing, offers *Save this queue as…*, and shows
one honest line of what the browser is holding against what it offered.

**The phone's playlist is not a document and the code says so in three places**: it sorts first
whatever it is called, `remove()` refuses it, and every handoff — link or pairing — takes the queue
back to it whatever was showing.

**A saved track carries no audio.** The bytes a phone sends are somebody else's music, they are the
largest thing in a queue by far, and a page that hoards them quietly is not what this is. A checked
property, not a comment.

**The restore runs before `fromFragment()` and the order is the whole of it.** A link in the address
bar is somebody asking for *that* queue now; storage is what they were doing yesterday, and
yesterday must not win.

Checked against a real IndexedDB — `fake-indexeddb` is a dependency of `web/` for the same reason
jsdom is, because a stub of storage would let a broken store pass.

- **Still to be checked by him:** on the Pi, across a browser restart.

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

**Measured rather than divided, and the measurement was kinder than the division.** 43,715 real
buckets, Modland's whole content, on the slower of his two machines: **2.6 seconds** against the 41
a record per track costs. Dividing had predicted three and a half.

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

**Built 2026-09-10.** A Browse button beside Pair and Paste, a four-level stack — catalogue,
format, author, tracks — and `web/src/catalogue.js` behind it. Opening an author and pressing a tune
makes **that author the queue**, which is what the phone does: somebody who opened a folder and
pressed a tune meant the folder.

**The download is offered, not assumed.** It is 5.76 MB off somebody else's server and this page has
until now cost nothing to open, so the first screen says what it is and waits.

**Two things measured before they were designed.** The file is *not* grouped — 3,727 buckets come
back after a gap — so flushing a bucket when the author changes would mean merging. Holding all of
them instead costs **55 MB** against 218 for a row per track, on a machine that offered 473 GB. And
building the records from the real index takes **554 ms**.

**And the shared cases caught a real disagreement within the hour.** Java's `URLEncoder` escapes
everything outside `A-Za-z0-9.-*_`; `encodeURIComponent` keeps seven more characters, `!`, `~`, `'`,
`(` and `)` among them. Both URLs fetch the same file, so it would have gone unnoticed until
something compared them as strings — and a queue decided the phone's copy of a track and the page's
were two different tracks. `[modlandUrl]` in `docs/rules/queue-cases.tsv` now holds six cases,
including `!!uu !! !!.it` because the owner played it, and both sides are checked against them.

The staleness rule is the phone's: the engine's fingerprint is recorded when an index is built and
compared when one is read, and a mismatch says so rather than showing a short list.

- **Still to be checked by him:** the download itself, on the Pi.
- **Not built yet:** ASMA, whose index *is* its 20 MB archive.

### S4. Search — **built 2026-09-10**

A field at the top of Browse, matching **authors and tunes together**, because a person typing a
name does not know which they are after. It says how long it took, in milliseconds, rather than
showing a spinner.

**Titles are sharded by their first two characters** — 1,663 records instead of 43,715 — and
**every shard is read**. That is the design and not a shortcut: an index on the first characters
answers a prefix instantly and never finds `elysium` inside `the elysium remix`, which is what
somebody typing a name expects. Reading a twenty-sixth of the index to get a substring search is
what the sharding was for.

Measured while choosing it: the shards cost **433 ms** to build and about **29 MB**, against 473 GB
offered. Each entry carries the title, its format and its author, so a hit is playable with no
second lookup.

**Authors are matched by scanning the 339 format lists**, which hold all 43,715 names between them
and cost nothing.

*`SearchResults.kt`'s rules — scope, per-source caps — are not shared yet. There is one source here,
so there is nothing to scope; when ASMA arrives that changes and the cases file is where it goes.*

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
2. ~~How much storage the page may take~~ — **answered by the measurement**: 18 MB held against
   256–473 GB offered, persistence granted on both machines. The page reports; it does not ration. Whether it *asks* before the
   first 5.76 MB download is still worth a word, and the answer is probably yes, once.
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
