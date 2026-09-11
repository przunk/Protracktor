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

### S2a. Editing a playlist of his own — **built 2026-09-11**

*Owner, 2026-09-11: "w widoku mojej playlisty webowej nie mogę usuwać utworów. Powinno to działać tak
jak na telefonie. Tylko lista from the phone powinna być niemodyfikowalna."*

A row of one of his own playlists offers **Remove from this playlist**; "From the phone" offers
nothing of the kind, being what the phone sent. The phone's rules (`removeTracks`): no question
first, and removing what is playing stops it rather than starting something else. An **Undo**
snackbar puts it back where it was, for six seconds.

**Edits wait for Save, as on the phone** — his decision the same day. Removing rows, or replacing
the list from Browse or the paste box, changes what is on screen and not what is stored; Save and
Discard appear in the top bar only while an edit waits, and a switch, a new playlist or a queue from
the phone asks the phone's *Unsaved changes* first. Closing the tab asks the browser to ask. Where
playback has got to is saved as it goes, but only while the list is the saved one.

**Ticking many rows, as on the phone** — also his decision the same day. A long press, or Select in
the row menu, starts it; a bar offers Add to playlist and, in a playlist of his own, Delete, which is
one edit with one undo. Adding writes another playlist at once and skips what it already holds.

Every item of the row menu has its icon now, the phone's paths — the page's menu was text alone.

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
- **ASMA:** built 2026-09-11, without its 20 MB archive — S7.

### S3a. Only what the browser can play — **built 2026-09-11**

*The owner, 2026-09-10: "nie indeksujmy utworów, których nie zagramy. Trzeba to będzie jawnie
napisać w wyszukiwaniu/browse."* `GOAL.md` round 8, item 1.

**The list is one file now**, `web/src/formats.tsv`: every name the phone's index keeps, with the
decoders that open it. `SupportedFormatsFileTest` fails the moment it and `SupportedFormats`
disagree. The page keeps a name only if at least one of its decoders is in the engine it is running
on — and "absent" is read from the engine's fingerprint (`zxtune:none`), never inferred, because
HivelyTracker is in every build and is never named there.

It replaced a hand-kept set of Spectrum extensions in `app.js` that had drifted: no `ym`, no `vtx`,
`psm` wrongly included (libopenmpt plays it), and a cost stated as 3,639 files.

Measured with the page's own functions on the real `allmods.txt`, 2026-09-11:

| | kept | dropped, plays on the phone | buckets | formats | records, JSON |
|---|---|---|---|---|---|
| before — everything | 516,107 | — | 43,721 | 339 | ~55.6 MB |
| the phone's engine | 341,831 | 0 | 33,940 | 94 | ~34.5 MB |
| **the browser's engine** | **315,294** | **26,537** | **32,212** | **90** | **~32.0 MB** |

`toRecords` took 450 ms against 917 ms before, in node. The JSON size is a proxy for bytes on disk,
not a measurement of them; IndexedDB's own figure and its write time need a browser, and the owner's
last were 18 MB and 2.6 s for the unfiltered index. **The index should shrink by about two fifths.**

After filtering, the buckets Random draws from (S5): 32,212, median 2, largest 3,615, 41% holding
one track — a little more skewed than before, which does not change the decision to draw uniformly
over tracks.

**The fingerprint is the engine's and the list's together**, the phone's lesson from 2026-09-04,
when five names added to the list left every index missing 5,558 files while it reported itself
current. An index from before this change has no counts and is treated as stale on sight; Browse
says why and what a re-download costs before one is started.

**Browse and search say it.** Browse: *"This browser holds 315,294 of Modland's 516,107 tunes. The
other 200,813 are in formats it cannot play — 26,537 of them play on the phone."* Search reports
every result as found *among the tunes this browser can play*, so "nothing matched" cannot be read
as Modland not having it.

Downloading the index now starts the engine first if nothing has played yet, and waits for it to
say which decoders it has — filtering before that answer would keep every Spectrum row.

### S4. Search — **built 2026-09-10**, answering with tunes only since 2026-09-11

A field at the top of Browse, matching **authors and tunes together**, because a person typing a
name does not know which they are after. It says how long it took, in milliseconds, rather than
showing a spinner.

**It answers with tunes, never folders** (owner, 2026-09-11: *"pokazuje mi konkretne utwory (nie
foldery!)"*). An author it finds brings their tunes into the results rather than a row to walk
into — which is what the phone's `CatalogueStore.search` does, matching title or author and
returning tracks either way. At most 300, and the note says when there were more.

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

### S4a. Browse is shut while "From the phone" is showing — **built 2026-09-10, undone 2026-09-11**

**Undone by S4c.** Browse no longer writes into any playlist, so there is nothing left for it to
protect "From the phone" from: Browse opens whole whichever list is showing, and Add on the phone's
list asks which of his own playlists to put the tune in. **The paste box keeps its refusal** —
pasting still replaces the list on screen, and that list must not be the phone's. What follows is
the section as it was built.

*Asked for by the owner in two goes, and the second one is the interesting half.*

Browse plays into the playlist that is showing. "From the phone" is replaced wholesale by each
handoff (S2), so writing into it means the next scan silently throws the writing away — and worse,
the queue he is looking at is not the queue the phone thinks he has.

The first version refused **after the press**: he walked three levels down, chose a tune, and got a
paragraph. *"browse powinno byc zablokowane z podpisem dla phone playlist"* — shut it before it is
walked into. So now:

- the Browse button carries `aria-disabled` and a tooltip saying which way out;
- opening it shows the caption and **no archives at all** — one row, which offers to make an empty
  playlist and drops him into Browse with it;
- the search field is gone, there being nothing it could usefully fill;
- `playFromBrowse` keeps its refusal as the second line of defence, and a check still drives it;
- **and the paste box does the same** — it went through the other door, writing straight into the
  queue. *"te funkcje powinny dzialac tylko jak przelacze liste"* is plural, and this is one of
  them. Found while answering a question about a missing file, which turned out to be unrelated.

`aria-disabled` rather than `disabled`, because a disabled button cannot be pressed and therefore
cannot say why it is shut. "Nothing happens" is the worst of the three answers.

**One bug fell out of it.** Boot restored a stored playlist only when it had tracks, which was true
while every playlist arrived full from the phone. Make an empty one, reload, and it was gone — back
to "From the phone", with the tab he had just unblocked shut again. An empty playlist is now
restored like any other.

### S4c. Browse plays without touching the playlist — **built 2026-09-11**

*`docs/BACKLOG.md` A33, decided by the owner the day it was raised.* Until now a folder or a search
result replaced the playlist that was showing, and the owner met it the way it reads: a search, one
tune pressed, and the playlist he had built was gone under forty results. The phone never did that.

- **Pressing a tune plays it through a session**, the one History and Random already use: the list
  it came from (the folder, the results, History) is what next and previous walk, the playlist waits
  in the stash untouched, and the heading reads "Playing from Browse" with the way back beside it.
  Going back stops the music, as it does on the phone (`returnToPlaylist`).
- **Browse stays open** after the press, so the next tune can be tried — which is why it stopped
  being a dialog. It is a screen in the column now, standing where the list stands, with the dock
  under it: pause, next and the seek bar in reach while browsing.
- **Every tune row has Add beside it, icon and label, and its menu**: add to another playlist,
  information, the author's other tunes (the phone's "Show neighbours"), save the file, copy a link.
- **Add appends to the playlist that is showing, or waiting under a session, and waits for Save** —
  the save model the owner asked for on 2026-09-11. A tune already there is not added twice, and its
  row says "Added". On "From the phone", which is never written to, Add asks which playlist instead.

Not built: ticking many Browse rows at once. The phone has it; the owner did not ask for it here.

### S4b. Every list behaves like the phone's — **built 2026-09-11**

*The owner: "pamiętaj też, żeby listy działały tak jak w apk (nasze ostatnie poprawki)."* `GOAL.md`
round 8, item 2 — before Random and History, so both are born obeying it.

The phone reached its rule in five builds on 2026-09-10 (`ui/ListScrolling.kt`): keep the playing
row in view, **one row at a time and only when it would leave**, and **never on arrival**. The page
already had most of it without knowing — and that is worth saying, because it means the page was
not wrong, only incomplete:

- `scrollIntoView({ block: 'nearest' })` *is* the phone's rule, natively. It is now one function,
  `revealRow`, shared by every list.
- The page called it only from `playAt`, so arriving at a list, drawing it again, adding or
  removing rows never scrolled — the phone's "never on arrival", already true.
- **The phone's worst defect cannot happen here.** Its dock was a layer over the list, so a row
  behind it counted as visible and was left there. The page's dock is `main`'s sibling in the
  column, not a layer, so nothing a list considers on screen can be covered. Read from the
  markup, not seen in a browser.

What was missing was Browse. Its track lists — an author's folder and search results — now **mark
the tune that is playing** the way the playlist does, **without scrolling when opened**, and **follow
it by the same rule when the tune changes while Browse is open**. Rows carry their address so the
mark can be found; a tune merely *selected* by the phone is not marked as playing.

No selection mode exists on the page, so the phone's "not while ticking rows" has nothing to apply
to. No follow button, as on the phone since the same evening; "Show in playlist" stays as the
deliberate jump.

jsdom lays nothing out: the checks prove *whether* the page asks to scroll and with what options —
once on a new playing row, never on arrival or redraw, Browse only while open — and cannot prove
where the row lands. That is the owner's to judge.

### S5. Random — **built 2026-09-11**

*`GOAL.md` round 8, item 3.* The shape is `docs/PLAN_RANDOM.md`; the phone's corrected behaviour,
builds 519–532, is the specification. What the page does, and where it had to differ:

- **Browse → Random** opens the record and plays at once. The engine is started before the first
  `await`, because a browser allows sound only from inside the click. **Offered even while "From
  the phone" is showing**, where the rest of Browse is shut: that rule is about rewriting what the
  phone sent, and the dice writes into nothing.
- **A second queue, explicitly transient.** While the dice runs, `queue` *is* the record, so every
  row keeps the playlist's actions, marking and scrolling (S4b); the playlist that was showing waits
  in a stash. `remember()` refuses to write, and **saving now reads the queue before it awaits** —
  it used to read the name first and the tracks after, which would have written the dice's picks
  into the playlist it had just left if a save was pending at the swap. A pending save is flushed
  before the swap.
- **Uniform over tunes**: a running count over the author lists (item 1's, so only playable tunes),
  built once per session; one random number picks the bucket and the tune in it.
- **The record is what played.** Three picks are decided and **fetched** ahead and not shown. The
  page had no prefetch at all; `playAt` now uses the fetched bytes of a pick read ahead, once.
- **Next walks the record and rolls only at its end**; previous walks back; the end of a tune checks
  repeat-one first and the button does not — the phone's exact split. No repeats while the pool has
  others: drawn four wide, the session's tunes passed over (`freshPick`).
- **The C35 lesson is the first line of `rollRandom`**: the roll is marked before the first await, so
  two "ended" in a row — or two presses — advance once. A page check does exactly that.
- Rows gain **Remove from this list**; the one playing may go and goes on playing.
- **Shuffle is shut, and looks it.** The page had the phone's defect in CSS: `.tbutton.on` came after
  `.tbutton:disabled` at the same specificity, so a shut shuffle that had been on stayed lit.
- **Leaving stops playback** and puts the playlist back exactly as it was; a queue arriving from
  elsewhere — the phone, a playlist, Browse — ends the session and becomes what is showing.
- The **Filter** button is there and says *Everything*; the chips the phone has need `Platforms.kt`
  shared and a favourites download, both out of this round.
- **Two differences from the phone, both the owner's after first use** (`docs/STATUS.md` C39): the
  playlist chip stays usable during a session and choosing a playlist leaves it; and a pick that
  will not open leaves the record instead of staying in it.

**The rules are in `docs/rules/queue-cases.tsv` as three groups driven by the page only**, with the
reason beside them: the phone's rule lives inside `PlaybackController` rather than in a function,
and this round may not refactor the APK to reach it. `RuleCasesTest` parses and ignores them.

What follows is the decision record from before it was built.

#### What was decided on 2026-09-10

**The shape of the screen is in `docs/PLAN_RANDOM.md`**, agreed the same day and written for
the APK and the browser together because he wants the two as alike as they can be. What
follows here is the web-specific half: what the dice picks from, and the measurements behind
it.

The phone asks SQLite for `ORDER BY RANDOM() LIMIT n`, which `docs/review-round-8.md` R7 already
records as unmeasured and probably wasteful. IndexedDB cannot do it at all.

#### What the measurement changed

Three numbers were taken before proposing anything, and the first one reframed the rest.

**The browser's index holds everything, including what nothing here can play.** `toRecords` takes
every line of `allmods.txt` with no extension filter; the phone indexes only what
`SupportedFormats` lists. And ZXTune is built *off* for wasm
(`scripts/build-web-engine.sh -DPROTRACKTOR_WITH_ZXTUNE=OFF`), so the browser loses formats the
phone keeps.

| | |
|---|---|
| lines in Modland | 516,107 |
| the APK indexes | 342,169 (66%) |
| of those, ZXTune-only | 26,559 — `pt3` 7,376, `pt2` 6,284, `ym` 4,977, `stc` 3,639 leading |
| **the browser can actually open** | ~315,610, or **61% of what it has indexed** |

So an unfiltered dice would hand back something that cannot open **close to four times in ten**.
The phone's answer to a pick that will not open is `skipFailedRandomPick`, which walks up to eight
times; at a 39% failure rate that is not a safety net, it is the mechanism, and the dice becomes a
machine for cycling past things.

**Filter before picking, not after.**

**And the distribution is skewed enough that "random" has to be defined.** 43,721 buckets over
516,107 tracks: **median 3, mean 11.8, largest 3,615** (`Protracker/- unknown`), and **35% of
buckets hold exactly one track**. Uniform over tracks gives the largest bucket 0.70% of rolls;
uniform over buckets gives every bucket 0.0023%, which would make a one-tune author exactly as
likely as Richard Bayliss with 1,298.

#### What was decided

**1. Uniform over tracks.** *"Mnie interesują utwory, nie autorzy"* (owner). The per-author
alternative was a real option — it is the shape that favours discovery — and it was declined on
its merits rather than on cost, since both are free: the counts already sit in the
`authors(format)` records, 339 lists that the author search already sweeps in full. A cumulative
table over them is built once and kept in memory; a roll is one random number and a binary search.

**2. Transient, with its own history.** The dice does not write into the playlist that is showing,
the same rule Browse and the paste box grew on 2026-09-10 and for a stronger reason: you roll it
repeatedly. `Previous` walks back through what the dice gave, not through the playlist. *The owner
wants to talk about this part further before it is built.*

**3. `Everything` first.** The web has only Modland indexed. `OnPlatform` needs the format →
platform table that lives in `Platforms.kt`, which would have to become a shared file the way
`docs/rules/queue-cases.tsv` did; `Favourites` needs a second download of Modland's 991. Both are
later steps, not part of this one.

**4. Read-ahead, as on the phone.** `READ_AHEAD = 3`. The reason is stronger in a browser, where
every tune comes over the network and a pick that has not been fetched is an audible gap.

#### And a decision that reaches past Random

**Stop indexing what we cannot play.** *"Nie indeksujmy utworów, których nie zagramy"* (owner).
This makes the dice's filter unnecessary, and it fixes browse and search at the same time — today
they list tunes the browser will refuse.

Two things it costs, and both must be handled rather than absorbed:

- **A re-download of the index.** Changing what is stored changes what a stored index means, so
  every browser holding one has to fetch Modland again. He has done that twice already today.
- **The absence has to be said out loud.** *"Trzeba to będzie jawnie napisać w wyszukiwaniu/browse"*
  — a catalogue that quietly holds 61% of what its name promises is worse than one that says which
  61%. The phone has the same gap and does not say so either.

The filter is not simply the phone's `SupportedFormats.extensions`: the browser's set is that minus
what ZXTune claims, so the two builds disagree and the disagreement is a fact about the build, not
about the format. That means the list has to be derived from the engine rather than copied beside
it, or it will drift the first time a backend moves.

### S6. History — **built 2026-09-11**

*`GOAL.md` round 8, item 4.* The phone's rules (`data/HistoryStore.kt`), kept in IndexedDB at
database version 3 in a store keyed by address: **one row per tune**, moved to the top and counted
when played again, the title written every time because it improves, **the last 500** kept and the
oldest forgotten. A stamp that only increases orders two plays in one millisecond.

- **One recording path.** Every play is recorded in the `opened` handler — the one message every
  play reaches, whether it came from the playlist, Browse, Random or History itself — so it is a
  tune the engine actually opened, filed under the name it gives itself.
- **Browse → History** lists them, marks the one playing by S4b's rule, plays from them, and clears.
  It is open even while "From the phone" is showing, like Random, because it writes into nothing.
- **It plays transiently**, through the session Random built, with a heading that says *Playing from
  your history* and a way back. The goal asked for that, and it is how the phone's Browse lists
  play — but the page's folder and search lists still replace the playlist showing. The mismatch is
  the owner's to settle: `docs/BACKLOG.md` A33.
- A tune the phone handed over as **bytes is recorded and marked**, not offered for a replay the
  page cannot give — the bytes are never kept.
- The Random record stays its own list for the session and is not persisted; the plays that fill it
  go into History by the same path as every other play.

**A fault from item 3 was found while building this, invisible to jsdom:** Browse on "From the
phone" with an index held threw — a helper read before its `const` (`docs/STATUS.md` C37). Fixed
here, with a check that builds that state. A second suspicion — that the Random heading's
`display: flex` would beat `hidden` — was right and was dismissed on a check that matched a scoped
rule; the owner found it on the first bundle (`docs/STATUS.md` C38).

What follows is what was written before it was built.


Settled in the same conversation: the Random view *is* the session's history, so this and S5
are one mechanism rather than two that resemble each other. See `docs/PLAN_RANDOM.md`. The
only differences either of us could name are which actions a row offers — no reorder in the
Random view — and whatever a later one adds. **The plan had two of these in it, and that
would have been waste.**

What follows is what was written before that was noticed, kept because the storage question
it asks is still the storage question.

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

### S7. ASMA — **built 2026-09-11**

*Owner, 2026-09-11: "proponuję dodać obsługę ASMA do web". Agreed with two answers from him: the
list only, not the archive, and the phone's half at the same time.*

**The premise everything here rested on was wrong, and a curl said so.** The phone was built
believing ASMA publishes one 20 MB zip and no per-file address — `Catalogue.webUrlFor` returned null
for it, its share link named "the collection and the path", and Send to Protracktor web left its
tunes out. Measured that evening:

| | answer |
|---|---|
| `https://asma.atari.org/asma/Composers/Aki/Robots.sap` | 200, 1,922 bytes, `Access-Control-Allow-Origin: *` |
| `asma.zip`, a ranged request | 206, `Accept-Ranges: bytes`, CORS on the 206 too |
| its end record → central directory | 849,030 bytes: 6,780 entries, 6,335 `.sap` |

So every tune has an address — the zip entry's own path under `https://asma.atari.org/` — and the
list of them costs **0.85 MB instead of 20**.

**The page.** `archive.downloadAsma` asks for the zip's tail, finds the end record, asks for the
central directory, and stores what it names in Modland's shape under an `asma:` prefix: section
(Composers 5,305 · Unknown 386 · Games 380 · Misc 196 · Groups 68), author, tunes. A server that
ignores `Range` answers 200 with everything and the same reading works on that. Each tune is
fetched from its own address when it plays. Browse lists ASMA beside Modland, search looks in both
and says which it looked in, the dice draws from both as one pool, and a row's second line names the
archive: `ASMA/Composers/Aki`.

**The phone.** `Catalogue.fileUrlFor` — the address a *browser* can fetch — is new, and ASMA answers
it. The phone still plays from the zip, which is what makes it work offline; the address is for
everybody else: Share a link gives the file, a queue link and Send to Protracktor web carry it, and
the paired send no longer spends its byte budget on ASMA rows.

**One rule, two runtimes.** The address is escaped by Modland's rule (`URLEncoder`, space as
`%20`), and `[asmaUrl]` in `docs/rules/queue-cases.tsv` holds both sides to four cases.

Not built: ASMA's STIL (`docs/WISHLIST.md` B30), and the 20 MB archive in the browser for offline
play — declined for now as not worth the download.
