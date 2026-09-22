# Wishlist

Ideas, not defects. A thing that does not work as intended belongs in `docs/STATUS.md`.

Numbered so a choice can be made unambiguously. The order is the order they were raised, newest
first — it is not a priority.

Each says who raised it and when. Stale reasoning is worse than no reasoning, so where a wish has
been overtaken by work already done, it says so.

---

## B36. Protracktor's own length database, measured by its own engines — **planned 2026-09-22, not started**

Raised by the owner on 2026-09-22, after asking whether NSF could have lengths and a seek bar:
*"a project of our own metadata. We already have the engines; we only need to measure every tune
and store the results. It could run in the background for 72 hours from a script if it takes that
long."*

**Why it is needed.** NSF has nowhere to store a length, songdb does not cover it (checked on two
Modland files), and HVSC is the C64's only. What exists elsewhere is scattered: NSFe collections as
RAR files on MediaFire, MEGA and Dropbox, with no terms stated and different files from Modland's;
`nsf.joshw.info`'s 1,634 per-game `.7z` archives with hand-timed NEZplug M3U playlists, no CORS and
no single index (whether its NSFs are byte-identical to Modland's is **not yet checked** -- no 7z
tool here).

**The cost is not the problem.** game-music-emu renders an NSF about 2,500 times faster than it
plays (2026-09-22): Modland's 5,015 NSFs with their tracks are hours, not days.

**The problem is loops.** Game music plays in a loop and never falls silent; played to its end, a
looping NSF stops at game-music-emu's default of 2:30 plus fade -- both files measured did, at
158.1 s. So measuring means **detecting the loop**: for the NES, the sequence of writes to the sound
chip starting to repeat. That needs a hook in game-music-emu (a patch, kept in `native/patches/` as
libopenmpt's is) and is a piece of research, which is why it is its own project. songdb did the same
for the Amiga -- its end codes `l` and `p` are detected loops.

**The shape, when it is taken up:**
1. loop detection in game-music-emu for NSF (then GBS, HES, KSS, which share the approach);
2. a host script that walks a catalogue index, fetches each file, measures every track, and writes
   a table keyed like songdb's (the first 48 bits of the MD5), resumable, safe to run for days;
3. the joshw playlists as a check: where the same file exists, the measured and the hand-timed
   lengths should agree;
4. the table committed and downloaded by both players with the song metadata, under the same tick;
5. its licence and provenance written down: our own measurements of files, not anybody's list.

Until then, variant (a) of 2026-09-22 (merged the same day, confirmed on the phone) covers the everyday case: a tune that falls silent gets its measured
length when it plays, and a looping one a seek bar up to the fallback length, shown as `~3:00`.

## B35. Old files' text as Central European, as a setting — **not planned, 2026-09-21**

Raised by the owner on 2026-09-21: `Fasttracker 2/Jakim/studium falszu.xm` shows "Studium Fa³szu".
The title holds `0xB3`, which is `ł` in Windows-1250 and ISO-8859-2 (Polish Windows) and `³` in
Windows-1252, the encoding libopenmpt reads a FastTracker 2 XM in. The file does not say which, and
the same byte is a different letter in a Polish title and a German one, so no rule can tell them
apart without guessing the language — and switching everyone to Central European would turn `ö`,
`ä` and `å` in western titles into other letters.

The option, if it is ever wanted: a setting, *Old file text: Western / Central European*, default
Western, applied where the engine and the libopenmpt patch choose ISO-8859-1 / Windows-1252 today
(`docs/STATUS.md` C79). **The owner's decision, 2026-09-21: not to be built; kept as an option.**

## B32. A row being fetched breathes — DONE 2026-09-14

Marking says "this one"; what a download has to add is "still working", and a network fetch is
seconds. Both runtimes now breathe the row at about a second a cycle — the page in CSS, the phone
with an animation on the row that is only allocated while one is being fetched, the same guard the
drag layer has.

**Browse's rows breathe too**, since 2026-09-14: the id of the tune being fetched follows the id of
the tune playing, which that list already took. It changes when a fetch starts and stops, not five
times a second, so the recomposition the screen is careful about is not disturbed.

## B34. Hiding the file extension in a name, as a setting — **shelved, 2026-09-18**

**Do not build it, and do not bring it up.** Not in a round's plan, not as a suggestion beside a
related change, not as "while we are in this file anyway". If it is ever wanted the owner will say
so; until then this entry exists to answer the question, not to keep asking it.

The reasoning below is kept because it is the useful part — it says what any future version would
have to survive, and two of the alternatives are worth doing on their own terms if the subject ever
comes back.

**Where the extension even comes from.** A row shows the tune's own title when the app knows one —
which it does for a local file after a scan, and for a catalogue row only once the file has been
opened. Until then the title *is* the filename, so `elysium.mod` is not decoration around a name, it
is the whole of what is known. Hiding part of it hides part of the only fact in hand.

**Three things the switch would have to survive:**

1. **A name without its extension is often not unique.** Modland holds the same tune in several
   formats under one author — `elysium.mod` beside `elysium.ahx`. Hide the suffix and the folder
   shows two identical rows, and the one thing that told them apart is the thing that was hidden.
2. **Half this archive marks the format at the front, not the back.** `mod.elysium`, `hip.something`
   — thirteen prefixes in `SupportedFormats`, and an Amiga listing is full of them. "Hide the
   extension" really means "hide the format marker wherever it is", and a setting that tidies
   suffixes while leaving prefixes is a setting that looks broken on the Amiga half.
3. **Search matches what is stored, not what is drawn.** Type `mod` with the setting on and rows
   come back with nothing visible to explain the match.

**What would be better than a setting**, in increasing order of effort:

- **Hide it where it is redundant and cannot collide**: inside one catalogue folder every row shares
  a format, the header already says which, and if no two names collide once stripped, the suffix
  earns nothing. That is a rule, not a preference, and it needs no screen in Settings.
- **Show the tune's real name instead.** The songdb metadata already downloaded for A34 answers
  "what is this called" by hash — for a file that has been fetched. Every row whose title is really
  a filename is a row waiting for that answer, and the extension question disappears with it.

**If it is wanted as a switch anyway**, it must strip prefixes as well as suffixes, must leave a
name alone when stripping would make it a duplicate of a sibling, and must not change what search
matches. Those three are the whole of the work; the checkbox is the easy part.

## B33. Modland's `/incoming/` is not in `allmods.zip`, and neither app can see it

**Not a search defect — a data one.** `allmods.zip` is the whole of what both apps know about
Modland, and it describes exactly one tree. Checked, rather than assumed:

- 516,107 lines, each `size ⇥ Format/Author/path`;
- **339 top-level names, every one of them a format** (AHX, Ace Tracker, Ad Lib…). None is
  `incoming`, none is `pub`;
- every path is relative to `https://modland.com/pub/modules/`;
- "incoming" appears 12 times in the index and "warehouse" 29 — all of them **tune titles**
  (`Fasttracker 2/Cons/warehouse.xm`), never path segments.

`/incoming/` is Modland's staging area: uploaded, not yet filed into the collection. The file is
real and reachable — 146,324 bytes, HTTP 200, `Access-Control-Allow-Origin: *`, so a browser may
read it directly — it is simply not in anything we download.

**What indexing it would take — measured, and the measurement is the argument.** There is no index
file for this tree, so it means walking Apache directory listings: `<a href>` per level, recursing
on the ones ending in `/`. A crawl capped at 600 directory requests was run once, breadth-first:

- **600 requests, 389.7 s** — about 0.65 s each, and it **hit its cap rather than the end**;
- it had found **23,880 files** and still had **4,997 directories queued**, having reached only
  depth 3 and 4. So it saw something like a tenth of the tree, and every number here is a floor;
- five areas, by files seen: `delivery bay` 11,825, `warehouse` 7,315, `laboratory` 3,306,
  `vault` 1,002, `workshop` 431;
- by extension: `.spc` 8,907, `.orc` 3,451, `.xm` 3,073, `.it` 2,818, **`.zip` 2,793**, `.s3m`
  1,386, `.rar` 113. The archives are a second problem — Modland's own tree has none, so nothing
  in the app unpacks one.

**Finishing that crawl would be roughly an hour of continuous requests against somebody else's
server, as a lower bound, with nothing to say when it needs doing again.** Against `allmods.zip`:
one request, 5.7 MB, 516,107 tracks, versioned. That is the whole comparison, and it is why this
stays a wish:

- the shape is not `Format/Author/file`, so it does not fit the catalogue's three-level browse —
  `warehouse/MOD/games/<Game>/<file>` is closer to how UnExoticA is filed than to Modland;
- a scrape has no version and no size column, so nothing tells us it went stale, and the only way
  to find out is to do the whole hour again;
- it is one request per directory against somebody else's server, which is the sort of thing the
  letter to ExoticA was written to avoid doing thoughtlessly. See `docs/PLAN_WEB_LIBRARY.md` S7.
  The bounded crawl above was run once, to answer the cost question, and should not be repeated
  casually.

**Cheaper first step, and possibly enough**: a way to type or paste an address into the APK. The
web page already has one (and now obeys the playlist rule when using it); the phone has no such
field at all, only the `http(s)` + `*.mod` intent filter in the manifest, which works but requires
starting from a browser. One field would make every unindexed corner of every archive reachable
without indexing any of them.

## B31. Drag a file onto the web player to add it

**The one way of getting a file into a page that everybody already knows.** The page takes music
from a phone link, a pasted address and Browse; a file sitting on the desk of the machine the page
is open on has no way in at all short of that machine being the phone.

What it would be: a drop target over the whole page (`dragover` / `drop`, `DataTransfer.files`),
each dropped file read once into an `ArrayBuffer` and appended to the playlist that is showing —
which means the same rule Browse just grew, that "From the phone" is not a list the page writes
into. A folder dropped from a desktop browser arrives as `webkitGetAsEntry`, so recursing into it
is a second, larger step and should not hold up the first.

Two things to settle before writing it:

- **Where the bytes live.** A dropped file is a local file with no address, so it is the same shape
  as the phone's `data:` entries — kept in the queue as bytes, gone on reload unless the playlist
  stores them. Storing them means playlists in IndexedDB stop being a few kilobytes of URLs.
- **MP3 stays info-only when it arrives by QR** (`docs/STATUS.md` C33), but a file dropped on the
  page by the person sitting at it is not the QR path, and there is no reason it should not play.

Cheap, obvious, and the sort of thing whose absence gets noticed on the first day somebody else
uses the page.

## B30. ASMA ships a STIL, and we do not read it

**266 KB, and it is exactly the thing this app keeps having to go elsewhere for.** 1,973 entries
keyed by the same path our ASMA index already holds — `/Composers/Aki/Robots.sap` — carrying what
the file itself cannot say:

```
/Composers/Aki/Robots.sap
  TITLE: Die Roboter (The Robots) [from The Man Machine (Die Mensch-Maschine)]
 ARTIST: Kraftwerk
```

Three shapes in it. **`TITLE`/`ARTIST` name what a tune is a cover *of*** — the SAP says "Robots" by
Aki, and the truth is that Aki arranged Kraftwerk. **`COMMENT`** is provenance a listener would
actually want: *"1st place at Forever 16 music compo"*. And some entries point at another file as
the same tune in another form, which is the beginnings of a duplicate map.

**Why it is cheap.** It joins on a path, not a hash, so it needs none of `SongDbMetadata`'s
machinery — the same shape as `modland_favourites.tsv`, which was a day's work. And ASMA is already
downloaded whole, so the file arrives with everything else rather than being a new thing to fetch.

**Why it is not obvious.** The fields do not mean what the same words mean everywhere else in this
app: `ARTIST` here is the *original* artist, not the person who made the file. Putting Kraftwerk in
the artist column of a tune by Aki would be wrong in the one place the app states who wrote
something. It probably wants a line of its own — "after Kraftwerk" — which is a UI decision before
it is a parsing one.

Counted 2026-09-10: 883 lines carry one of those field names across 1,973 entries.

## B29. The documents need a list a person can read at a glance

**Every list needs a human-readable state at the top**: what is to do and what is done, without
reading the whole file. A change to `AGENTS.md` as much as to the files, because the rule has to
hold for whoever writes the next entry.

`docs/STATUS.md` is now past a thousand lines and `docs/WISHLIST.md` and `docs/BACKLOG.md` are not
far behind. Everything in them is findable and nothing in them is **scannable**: to learn what is
open you read every heading, and the strikethrough that marks a finished entry only shows once you
are level with it.

**Clarified 2026-09-10: only what is open.** Ten open items in ten lines, not a search through two
thousand. Not a checklist with
`[x]` beside the finished ones — those should not be on the list at all. A done entry keeps its
struck-through heading where it is, because the reasoning in it is the point of keeping it; it simply
stops appearing at the top.

**The numbers say the idea works.** Counted 2026-09-10: **2 open defects, 7 open backlog items, 12
wishes** — twenty-one lines for the whole project, against three files totalling some three thousand.

**One list or three?** The request named the wishlist and the status file, but the same argument makes one list at the top of
`docs/STATUS.md` covering C, A and B better than three: the state of the project on one screen rather
than on three. Worth deciding before it is written.

Two things to decide, and neither is hard:

- **It has to be maintained by whoever adds an entry**, or it becomes the stalest thing in the repo
  — which is worse than not having it. That is an `AGENTS.md` rule, and `AGENTS.md` already carries
  rules of exactly this kind about how these files are written.
- **Generated or written?** A script that rebuilds the list from the headings cannot fall out of step
  and needs somewhere to run; a hand-written list is one more line per entry and no machinery. The
  headings already carry everything needed — the number, the title, and `~~struck through~~` for
  done — so generating is a dozen lines of Python and the honest answer is probably to generate it
  and check the result in.

**Not just a table of contents.** The point is the state, not the navigation: what is open, what is
done, and how many of each.

## B28. The Mod Archive gives us no artist

*Found 2026-09-08 while working out why "more from this author" did nothing for one of its tracks
(`docs/STATUS.md` C19). Not part of the request — a consequence somebody has to decide about.*

**Every tune from The Mod Archive has an empty author, everywhere in the app.** The parser sets
`author = ""` and it is not being lazy: a search result row carries a title, a format icon and a
module id, and the artist is simply not in the HTML it returns. So the playlist row, the information
panel and the Now Playing screen all show nothing for those tracks, and no amount of care elsewhere
changes that.

**The name is on the module's own page**, at `index.php?request=view_by_moduleid&query=<id>` — which
means a second request and a second HTML parse **per track**, against a page nobody publishes a
contract for. That is the whole cost, and it is worth stating plainly before anyone starts:

- **Not during search.** Twenty results would be twenty page fetches before the list could be drawn,
  on somebody's mobile connection. The list has to appear first.
- **On demand, then.** Either when a track is opened — the information panel is where an absent
  author is most visible — or when it is played, filling the field a moment late.
- **Cached, once fetched**, in the same table as the songdb metadata or beside it. The answer does
  not change, and asking twice for one module is asking their site to do our bookkeeping.

**The alternative is to leave it blank and say why.** "The Mod Archive does not publish the artist
in its search results" is a true sentence and costs nothing to show, and there is an argument that a
player has no business scraping a page per track to fill a field. Worth deciding before building,
because the scraping version is the kind of thing that works for a year and then breaks silently on
a redesign.

**A third option exists and is better if it holds:** the songdb metadata table is keyed by MD5 and
covers around 400 archives. If it names artists for modules The Mod Archive serves, the answer is
already on the device after one download the app already offers — no scraping, no second request.
That should be measured before either of the above is built.

---

## B27. ~~Random, but only tunes considered good~~ — DONE 2026-09-08

**The app has no opinion, and inventing one would be worse than having none.** It records no plays
that mean anything yet, no ratings, no skips-as-signal; a "good" score derived from what happens to
be indexed would be a number with nothing behind it, and it would look authoritative on screen. The
honest reading of the wish is *somebody else's judgement, published* — and Modland keeps a
favourites list, republished by `audacious-uade-tools` as one 142 KB tab-separated file, the same
repository and licence as the metadata table B20 already brought in.

### The numbers, measured rather than estimated

**991 favourites. 891 still at the path Modland publishes today. 835 that an index built by this
build keeps.**

The first figure written here on the morning of the 8th was "991 of which this build plays 924",
and it was an estimate from format names. Checking it properly against `allmods.txt` moved it twice:
a hundred of the paths have been renamed or removed since the list was compiled, and the extension
filter drops another fifty-six — `.aon`, `.dw`, `.dm2`, `.hip`, `.cus`, formats no backend here
claims. The difference is not large and it is the whole difference between a number and a guess.

### It is a join, not a table of tunes

The list is paths, and `catalogue_tracks.path` holds the identical string, so `modland_favourites`
is one column and the query is one `IN`. That is also what makes it self-correcting: **the join
silently drops the hundred that moved**, so the dice never hands out a download that 404s, and the
count the app shows is the playable one rather than the published one. Showing 991 beside a dice
that draws from 835 is the kind of small lie that costs somebody an evening.

**Keyed by path, unlike its two neighbours.** `song_lengths` and `track_metadata` answer questions
about a file the user already holds, so they key on its digest. This one answers "what should I
play", which is a question about the catalogue — and keying it by hash would mean downloading a
tune to find out whether it was worth downloading.

### One scope, not two filters

`Favourites` sits beside `Everything` rather than among the platform chips, because it is not a
platform: it cuts across all of them. It is deliberately not combinable with one either — the list
is Amiga tracker music almost entirely (562 ProTracker, 198 Fasttracker 2, 46 AHX of the 835), so
`Favourites ∩ C64` would be a chip that returns nothing.

### Found on the first run: the chip was dead

**The same mistake as the platform chips, two commits later.** `favouriteCount` was set by
`refreshCatalogues`, which the Browse *root* does not call — and the scope sheet opens from the
root. Anyone who had not visited the catalogue list in that session saw a chip that read "not
downloaded" whatever was on the device. The root now recounts it, unguarded: it is one `COUNT` over
a thousand rows rather than the grouped scan of half a million the platform counts need, and zero is
a real answer here, so a guard on emptiness could never tell "no favourites" from "not asked yet".

**And a disabled chip has to say why** — asked for once already, about the search
filter. Here it can do better than explain, because what it needs is a 142 KB download the sheet can
start: the line under the chips offers it, turns to "Downloading…", and disappears when the chip
comes alive. Zero has two causes, so there are two sentences — list not downloaded, or downloaded
and Modland not indexed, which is why `favouritesListed` is tracked beside the playable count.
Offering "Download" to somebody who has already downloaded it would send them round a loop.

### What the download says

The message after fetching reports the **playable** count, and says something different when it is
zero: "$n favourites downloaded. Index Modland to play them." A list that arrives and reaches
nothing otherwise reads as a download that worked and a chip that stayed dead.

---

## B21. ~~The player notification is grey, tall and belongs to nothing~~ — DONE 2026-09-06

**Both complaints have the same cause, and it is one we can fix.** Since Android 12 a `MediaStyle`
notification takes its colour from the artwork it is given — `setColorized` on its own does nothing
without one — and reserves the tall media layout whether artwork arrives or not. We give it none:
`PlaybackService` sets a title, an artist, an album and a duration, and no
`METADATA_KEY_ALBUM_ART`. So the platform draws the large player it always draws, in the default
surface grey, around a hole where the cover would be.

**These formats have no cover art and never will**, which is the interesting part rather than the
obstacle. What could fill it:

- **Something generated per track.** A deterministic image from the file's own identity — the format
  name over a colour derived from a hash of the title, say. Every tune gets a stable, distinct
  square, and it costs one bitmap per track change.
- **Something generated per *format*.** Twelve or so images, one per backend or format family, drawn
  once and reused. Cheaper, and arguably truer: what a `.sndh` looks like is a real fact about it,
  where a colour from its title is decoration.
- **The launcher icon.** Cheapest and worst — every tune identical, which tells the user nothing and
  makes the notification look like a mistake.

**The height is the platform's and cannot be argued with**, but a filled artwork slot is what that
layout is for, so filling it is the closest thing to a fix.

**Worth measuring before choosing:** whether `setColorized(true)` plus artwork actually recolours on
the Android version in use, and how a generated bitmap looks at the two sizes the shade and the
lock screen use. Both are questions a device answers in ten minutes and a document cannot.

**Related:** `docs/STATUS.md` C12, where the same notification lost its skip buttons because Android
13 takes them from `PlaybackState` rather than from the actions we add — this part of the platform
rewards checking over assuming.

### Built 2026-09-06 — the format over a colour taken from the file

**A hybrid of the first two options rather than either.** The glyph is the format — `AHX`, `SNDH`,
`SID` — because that is a fact about the file; the colour is per-tune, because it is what makes a
track change visible and what stops the bar being grey. Split that way each half answers one of the
two complaints, and neither option alone did.

`TrackArtwork` holds the arithmetic and is Android-free; `TrackArtworkBitmaps` draws the 512-pixel
square and caches one, which matters because the media session republishes five times a second.

**Two things the tests found that no amount of looking would have.**

*The colour was illegible on a sixth of the wheel.* At one fixed HSL lightness a yellow and a blue
have wildly different perceived brightness — 2.6:1 and 12:1 against the same label. Each hue is now
given the lightness that lands it at a fixed **luminance**, so all 360 read at 5.6:1. The test sweeps
every one rather than sampling, and asserts the range is narrow rather than merely above the bar:
uniformity is what tells a working construction from one that passes today.

*The hash put similar names next to each other.* `part 1`, `part 2`, `part 3` — the normal case in
these archives, and the covers a listener sees one after another. Java's `hashCode` gives them
consecutive hues, 344/345/346; plain FNV-1a fixed the neighbours and left every *second* one two
degrees apart, so the alternation was the hash's pattern rather than the music's. An avalanche step
on the end of FNV scatters them, and the hue distribution was checked over 20,000 names before that
was believed.

**Not proven, and it needs a phone:** whether `setColorized(true)` is honoured on that
Android version. Several manufacturers ignore it. The design does not depend on it — the square is
coloured either way — but the "grey" half of the complaint does.

## B26. ~~The cache belongs in Settings, not in Browse~~ — DONE, and the premise had gone stale

**We do, and that is the finding.** Browse has a **Storage on this phone** screen: it reports the
fetched-music cache against its 512 MB ceiling, each catalogue's index, any downloaded archive, the
sc68 replay routines and the database, and it can throw away everything that can be fetched again.
`CacheBudget` and `Q5` settled the policy behind it — oldest deleted first.

**So this is not a missing feature, it is a misplaced one.** Settings is where it was looked for, and where a
person looks for "how much room is this app taking", and found nothing. Storage is filed under
Browse because that is where downloading happens, which is our reasoning rather than his.

Three ways out, and the third is probably right:

- **Move it.** Settings gains a real second section; Browse loses a screen that arguably never
  belonged in a *browser*.
- **Duplicate the entry point.** Two doors to one screen. Cheap and slightly confusing.
- **A one-line summary in Settings that opens the existing screen.** "Storage — 210 MB" leading to
  what already exists. Settings answers the question, Browse keeps the tool, and nothing is built
  twice.

### Checked 2026-09-08 before starting: it had already moved

`StorageSection` is called from `SettingsScreen` and from nowhere else. The screen this entry says
lives in Browse lives in Settings, which is where it was looked for and where it belongs;
the entry describes a state that no longer existed when it was written down.

**What was actually missing was smaller and worse.** The two newest downloads — the songdb metadata
(15 MB, 380,282 rows) and Modland's favourites — had no row: no size, no delete, nothing on the one
screen that answers "what is this app keeping". And the song-lengths delete quietly cleared the
metadata table as well, while reporting only "Song lengths deleted": the metadata had been hung on
the nearest existing hook rather than given its own. Three separate clears now, each naming what it
removed, and deleting the favourites puts Random back to `Everything` — a scope pointing at a list
that has just gone would draw nothing, and the chip that set it is disabled the moment it goes.

## B25. A swipe on a track row — to discuss

**Two things make this a conversation rather than a task.**

*There is no queue to add to.* In Protracktor the queue **is** the playlist — `PlayQueue` holds what
the playlist holds, and there is no separate "up next" list. Adding a gesture would mean inventing
that concept first, which is doing it backwards: the gesture would be justifying the feature rather
than the other way round.

*The playlist row has no gesture budget left.* Tap plays, long press selects, drag reorders, and
three dots hold everything else. A fourth is where these systems start misfiring — an accidental
horizontal swipe during a vertical scroll is the common failure, and this row is one somebody uses
in a car.

**Where a swipe would earn its place is Browse and search results**, and for a different reason:
those rows do not reorder, so they carry two gestures rather than four, and "add to playlist" is
genuinely the dominant action there. The same gesture is right in one list and wrong in another
because the lists differ in both respects.

**Deferred on the day it was raised.** Recorded so the reasoning survives the
conversation.

## B22. ~~Random, but within something~~ — DONE 2026-09-08

**Most of this already exists in the query.** `CatalogueStore.randomSample` takes a set of catalogue
ids and appends `ORDER BY RANDOM() LIMIT n`; the rows it draws from carry `catalogue_id`, `format`
and `author` as columns, and `formats()` and `authors()` already group by them. Narrowing the sample
is another `WHERE` clause, not a new index.

**The question is the interface, and it is the same question as B23 below.** "Random within this"
needs somewhere to say what *this* is. Three shapes, and they are not equal:

- **From where you already are.** Random inside the folder, author or format currently open in
  Browse — no new screen, no new vocabulary, and it reads as "more of what I am looking at".
  Cheapest, and it covers the common case.
- **A scope the dice button remembers.** Set it once, and Random keeps meaning that. More powerful
  and it introduces a mode with no obvious place to display itself, which is how a dice button
  stops being a dice button.
- **Ask each time.** Rejected before it is built: Random exists to be pressed without thinking.

**Worth deciding with B23**, because "platform" as a way of narrowing Random and "platform" as a way
of narrowing search want the same list of platforms to exist.

### Built 2026-09-08 — the first of the three shapes, and the idiom was already here

**"From where you already are" won**, as this entry guessed it would, but the deciding argument
arrived later than this was written. By the time it was built the app had two idioms it did not have
when the question was asked: **press does the plain thing, hold does the qualified one** (the
transport gained it that morning), and **the control states its own scope in words** (the search
field's label, which is what made an empty selection safe again).

So: tap the dice to play something, **hold it to choose where from**, and the row says which —
`Random · Amiga`. Nothing new appears at rest, which matters in a screen already
called cluttered once; and the scope cannot become an invisible mode, because the row is where you
would go to press it anyway.

**The picker is the search filter's chips, whole** — same `Platforms` table, same counts, same rule
that a platform with nothing indexed is drawn disabled. One vocabulary for "which machine".

`RandomScope` is a type rather than a nullable platform id, because **the other wish that
day is another case of it**: "random, but only tunes considered good" wants Modland's own
favourites, and that arrives as a second scope rather than a second mechanism. Built the same day —
B27 above, which also corrects the figure this paragraph first carried: 835 playable, not 924.

**Not persisted, deliberately — and then reversed on 2026-09-10.** The rule was that after a restart
the dice meant anything again, because a scope outliving the session is an invisible mode and the
subtitle only defends against that while somebody is looking at it. The Random view took that
argument away: the scope stands on its own screen beside a Filter button, in words, whether or not
anybody is looking for it. So it is stored with the rest of the player state and survives a restart
(`docs/PLAN_RANDOM.md`, `docs/SPEC_RANDOM.md` §1).

### Two things found on the first run

**The chips were all disabled**, because the counts they read are fetched when the *search* screen
opens and this sheet opens from the Browse root. Every platform read as "nothing indexed" and the
whole picker was dead. Fetched on the root too now, but only when they are absent — this is a
grouped scan of every catalogue row and the root is returned to on every step back out of a folder
— and dropped whenever `refreshCatalogues` runs, which is what every path that changes an index
already ends in. Without that last part a chip would stay dead for the rest of a session after the
index that would have lit it.

**Nothing said the hold existed.** The subtitle only mentioned it once a scope was already set,
which is exactly backwards: the state you need telling about is the one you have not discovered.
The default row now says so.

**One thing that would have looked like a bug.** Random reads three picks ahead so a track can be
fetched before it is wanted. Choosing "Amiga" without discarding those would play three C64 tunes
first — a change that looks ignored. `setRandomScope` throws the read-ahead away.

## B23. ~~Search by platform, not by service~~ — DONE 2026-09-06

**This is the better model and it is worth saying why.** A person looking for C64 music does not
care which archive holds it; the service names are our plumbing showing through. The current filter
asks the user to know that ASMA is Atari 8-bit and that Modland has everything — which is knowledge
about *us*, not about music.

**What exists already:** every catalogue row carries a `format` string, and `formats()` groups by
it. What does not exist is the map from format to platform. Modland alone has 339 format
directories; the app's own backends already imply a grouping — sc68 is Atari ST, libsidplayfp is
C64, ASAP is Atari 8-bit, game-music-emu is consoles, libopenmpt is trackers across everything —
but that is a grouping by *decoder*, and decoder is not platform. AHX is Amiga and libopenmpt does
not play it (`docs/PLAN_FORMATS.md` §0b); `.mod` is Amiga by origin and plays everywhere.

**So the work is a table, and the table is the decision.** Which platforms exist as choices, and
which of Modland's 339 directories belongs to each. It can be wrong in two directions — a platform
nobody picks, or a format filed under the wrong one — and the second is worse because it hides
music rather than merely failing to offer it.

**Two things make it cheaper than it sounds.** The set of formats actually present is known from the
index rather than guessed, so the table only has to cover what is really there. And it does not have
to be complete to be useful: the six or seven platforms that cover most of the archive are worth
having even if a long tail stays filed under "other".

**Related:** B22 wants the same list to exist, and `docs/BACKLOG.md` A7 records that every catalogue
except The Mod Archive is blocked on a decoder — so the platform list is also a way of showing what
is *not* playable yet without pretending otherwise.

### Built 2026-09-06 — one scope, said out loud

**The design changed shape in conversation and came out better than either starting point.** The
objection was that the filter was cluttered — up to five chips in two rows — and the
answer was three large tiles: `LOCAL LIBRARY`, `ONLINE CATALOGUES`, `BY PLATFORM`. The design's real
move, though, was the second one: **the search field's own label states the scope** — `Everywhere`,
`Amiga`, `Online: Modland, Aminet`.

That is what made the rest fall out. The earlier sketches kept running into "what does ONLINE *and*
BY PLATFORM mean", because the tiles were two different axes wearing one shape. Once the scope is a
single value displayed in one place, there is no conjunction to resolve, and `SearchScope` is a
sealed type with four cases rather than three booleans and a set.

It also made an empty selection safe again. "No catalogue ticked means all of them" had to be
reversed once, because unticking Modland searched Modland anyway and the filter looked broken. It is
back — tapping `ONLINE CATALOGUES` with nothing ticked searches everything — for the one reason it
was not safe before: **the label says so out loud.**

**The tiles shrink rather than disappear.** At rest they are large and share the width; once a scope
is set they collapse to a row of icons with the active one lit and the chips appear below. An earlier
version replaced the tiles with the chip list and needed a small back arrow to escape; keeping the
icons means switching from platforms to catalogues is one tap, there is nothing to go back from, and
the system back button keeps a single meaning.

**Back.** Inside search there is one level and it is the scope: back widens it to `Everywhere`, and
a second press leaves. It must never destroy the typed query or the results, and `browseBack` used
to clear `tracks` on the way out of any domain — right for a folder you walked out of, wrong for a
search you are coming straight back to. Back always meaning "leave" is only safe because leaving
costs nothing.

### The table, and what measuring it found

`Platforms` maps thirteen machines to Modland's directory names *and* to file extensions, because a
catalogue row carries a `format` column and a local file carries only its name. One table, keyed by
platform, so B22 can use it unchanged.

`./scripts/probe-platforms.py` measures it against the archive: **98.2% of Modland's 515,509 files
land on a platform**, and the largest unmapped directory holds 486. That is the number the design
rested on — 339 directories sounds like a research project until you see that the top twenty are 84%
and 240 of them hold under a hundred files each.

Two things it caught that reading would not have:

- **FMP, PMD and S98 had been filed under Sharp X68000.** They are NEC PC-98 sound drivers — 22,513
  files under the wrong machine. Nothing on screen would have looked wrong, because neither platform
  plays anything yet.
- **`gtk` and `mms` belonged to no platform at all**, having been added to `SupportedFormats` a day
  earlier. A test now asserts that every name the app claims belongs to some platform, because the
  failure is silent: picking a platform would drop files the app can play and say nothing.

`.ftm` is the honest awkward case, and it is recorded rather than smoothed over: 1,779 of Modland's
1,874 are FamiTracker and 95 are Face The Music. The directories are certain and are mapped
separately; the *extension* has to pick one, and it goes with the majority — while libopenmpt is the
one that plays the minority.

### Three corrections from the first device run

**Results outlived their scope.** Switching to `Online` left the local files on screen, looking like
an answer — with the label already saying `Online`, so two things on one screen disagreed about what
you were looking at. A scope change now clears them. That is not a contradiction of "back keeps the
results": what has to survive is *leaving and returning*, not a list that no longer matches the
question.

**The chips are one scrolling row after all.** They were a wrapping grid on the argument that a
horizontal row hides its own contents. The argument lost to a phone: thirteen platforms wrap to four
rows, and with the field and the tiles above them **two results were left visible**. A filter that
costs you the answer is worse than one you have to drag. The fixed order earns its keep here — the
biggest platforms are the ones reachable without dragging.

**An empty query means "everything in scope".** It used to return silently. `%%` matches every row,
the per-source cap and the count beside it were already built for exactly this answer, and "show me
everything on the Amiga" is a scope with nothing typed — which is how anyone would ask it. The Mod
Archive is skipped for it, being a live search against somebody else's server with no index here to
list; and the playlist source, which had never needed a cap because a typed query is its own limit,
got one.

**Greyed for a measured reason.** A platform chip is disabled when the user's indexes hold nothing
for it. That is a real count, not a hard-coded support list: a catalogue index only ever contains
files this build claims, because `Catalogue.parseIndex` is handed a `keep` predicate, so a count of
rows is a count of tunes that will open. A hard-coded flag would have been wrong the day after AHX
landed — and one did land the day before.

## B24. ~~Open a chiptune link with Protracktor~~ — DONE 2026-09-08, the file half

*Raised 2026-09-05: somebody sends a link to `Nukes_Chiptune.sndh`, it is tapped, and this player opens
and plays it.*

**The playing half is nearly free.** `RemoteFiles.fetch` already takes any URL and `playTransient`
already plays something that is in no playlist — that is what Random and a search result do. A URL
arriving from outside is the same shape of thing.

**The recognising half is where the work and the disappointment live**, so both are worth knowing
before this is started.

`MainActivity` declares only `MAIN`/`LAUNCHER` today. Adding an `ACTION_VIEW` filter is the
mechanism, and it splits into two cases that behave nothing alike:

- **A file** — tapped in a file manager, a chat app, a download. Arrives as `content://` with a mime
  type, and the mime type is almost always `application/octet-stream`, so it identifies nothing.
  Matching has to be by extension.
- **A web link** — the case actually described. Arrives as `https://…/Nukes_Chiptune.sndh`.

**Android's `pathPattern` is not a regular expression**, and this is the trap: its `.*` does not
backtrack, so the natural `.*\.sndh` fails on any path containing an earlier dot — which a real
URL usually has. The workarounds are a pattern per dot-count, or `pathAdvancedPattern`, which is
API 31+ and `minSdk` here is 29.

**And the web-link case cannot do what it looks like it should, on any modern Android.** Since Android 12 an
unverified web link opens the browser without offering a chooser at all. Being offered requires
App Links — an `assetlinks.json` served from the domain — and we do not own `modland.com` or
anybody else's archive. What remains is the user going into *Open by default → Add link* for the
app once, per domain. That is a real feature for somebody who wants it and it is not "tap and it
plays".

**So the honest shape of this is probably the file case first**: a tune saved or received opens in
Protracktor from the share sheet and the file manager, which needs no domain, no verification and no
`assetlinks.json`. The web link is the same intent filter with a caveat attached, and the caveat
belongs in whatever text offers it.

**Worth checking on a device before committing to either**, because both claims above are about
platform behaviour that varies with version and manufacturer: whether a `content://` open reaches us
with a usable name, and what a tapped `.sndh` link actually does on a phone today.

### Built 2026-09-08 — and the entry's own advice was taken

**The file case, as this entry recommended.** `ACTION_VIEW` and `ACTION_SEND` for `content://` and
`file://`, matched by mime type — `audio/*` and `application/octet-stream`, because a document URI
usually carries no extension anywhere in it and octet-stream is what a chiptune is nearly always
labelled. That is as good as the platform allows, and it means Protracktor appears for some files it
cannot play. It says so plainly when it opens one, which is the honest half of that trade.

The link case is there too, with three `pathPattern`s per extension for the dot-counting trap this
entry described, and with no illusion attached: on Android 12 and later an unverified web link opens
the browser with no chooser, so what this buys is *Open by default → Add link*, once, per domain.

**The playing half was free, as predicted; the name was not.** `loadBytes` already reads a
`content://` through the resolver, so nothing new was needed to get the bytes. But four backends
choose a loader by the file's *name*, and a document URI often has none — so the app asks the
provider for `OpenableColumns.DISPLAY_NAME` first. Without that a perfectly good `.sndh` arrives as
a nameless blob and is declined by everything.

**What was not free: a transient track had meant one thing.** `randomMode` was literally
`transient != null`, so a file handed to us would have put the app in Random — next rolling the
dice, and a scrim over the playlist reading "Next picks another" when nothing follows at all. There
is an `externalOpen` flag beside the transient now, and next, previous, the read-ahead and the
end-of-track handler each ask it. One file arrived; that is the whole of it.

**Not done, and deliberately:** no `assetlinks.json`, because we own none of these domains, and no
uppercase `pathPattern`s, which would double the largest block in the manifest to catch `.MOD`.

## B20. ~~The year a tune was released~~ — DONE 2026-09-06 for Now Playing and the dock

Raised 2026-09-04: show a tune's release year — in its information, in a list, or in search.

**Half of this is nearly free and the other half is not, and the difference is worth stating before
anyone starts.**

`Sc68Backend::describe()` **already emits a `year` field**, and `NowPlaying`'s `FIELDS` list does
not include it — so for every SNDH and `.sc68` the year is read out of the file, carried across JNI
and then dropped on the floor. Showing it in Now Playing is one entry in that list and one string.

The other backends can supply one too, none of them do yet, and each needs a line:

| backend | where the year is |
| --- | --- |
| sc68 | `info_.year` — **already emitted**, just not shown |
| ASAP | `ASAPInfo_GetYear` / `ASAPInfo_GetDate` |
| libsidplayfp | the tune's "released" line, `infoString(2)` — usually `1987 Rob Hubbard` |
| game-music-emu | `gme_info_t::copyright`, which normally starts with the year |
| libopenmpt | `get_metadata("date")`, present in IT and MPTM and rare elsewhere |

Two of those are not a year but a string that usually contains one, so this ends in a small parser
and a decision about what to show when it says `1987-1989` or nothing at all.

**"In the list or in search" is the expensive half.** A row is drawn from the index, not from an
open file, so a year would have to be *stored* — a column in `library_index` and in
`catalogue_tracks`, a schema migration, and a re-index to fill it. Worth doing if the year is wanted
for sorting or filtering; not worth it to decorate a row.

**Suggested order:** show it in Now Playing first, from whatever the backend already knows. That is
an afternoon, it answers the question for the file you are listening to, and it says how often a
year is actually there before anything is stored.

### Built 2026-09-06 — Now Playing and the dock, nothing stored

**Four of the five backends were already sending something across.** The table above says sc68's
`year` was being dropped on the floor; in fact ASAP's `date`, game-music-emu's `copyright` and
libsidplayfp's `copyright` were all crossing JNI too and being ignored just as quietly. Only
libopenmpt's `date` was genuinely not sent, and that is one line of C++.

`ReleaseYear` is the parser this always needed. It reads `year`, then `date`, then `copyright` — in
that order because sc68 states a year, ASAP states a date, and a copyright line is prose that
happens to contain one. **The four-digit search is bounded to 1970..2099 rather than being `\d{4}`**,
because game-music-emu's copyright carries whatever the ripper typed and `KMCA-1234` must not become
1234.

A range is kept as a range: `1987-1989` is what the file says, and picking its first year would be
us deciding something the composer did not. Two years separated by anything but a dash are two
numbers in a sentence, not a span — `1987 Rob Hubbard, remastered 2019` is 1987.

**In the dock it joins the author line rather than adding one.** `4-Mat · Amiga · 1992`, ellipsised
from the right, so a long author pushes out the decoration instead of the title growing a third row
— the dock's height has been argued about once already. With no author the platform *replaces* the
format, because "MOD · Amiga" says one thing twice. The machine comes from `Platforms`, which the
search filter built the day before and which needs no backend to answer.

### Why most tunes have no year, and it is not our doing

The first observation on using it: plenty of tracks show nothing. That is the format, not the
reading, and it is worth writing down so nobody investigates it twice.

**The formats that can carry a date are the minority.** libopenmpt's `date` is the module's
last-saved stamp, and grepping the vendored loaders for the file-history structure that produces it
gives exactly five: **DMF, GT2, IT, PT36 and S3M**. Plain `.mod` and `.xm` have nowhere in the file
to put one — between them that is about 125,000 of Modland's files, the two biggest directories in
the archive. No parser can find what was never written.

Where a year does exist it is usually reliable: SID's released line, SAP's `DATE`, SNDH's `YEAR`,
and IT/S3M's save date. So the field is worth showing and worth leaving blank.

**Getting a year for a `.mod` means an external database**, keyed by something like the file's hash —
which is the same shape as HVSC's song lengths and would be its own piece of work, not an extension
of this one.

### The other half, 2026-09-07 — a year for the formats that cannot carry one

`mvtiaine/audacious-uade-tools` publishes author, publisher, album and
year for 380,282 hashes. **67,601 of the Modland files this app claims gain a release year** — and
the formats that gain most are exactly the ones with nowhere in the file to record it: 40,161
ProTracker, 11,733 Fasttracker 2, 5,920 Impulsetracker.

It is the shape this item predicted: *"an external database keyed by something like the file's hash
— the same shape as HVSC's song lengths"*. So it is built the same way: `SongDbMetadata` parses,
`TrackMetadataStore` keeps and asks, one download beside the song lengths, and the storage screen
can throw it away.

**The file wins every field it fills.** A lookup on a hash is a good guess about a tune; what the
tune says about itself is not a guess. The database fills gaps and never overwrites, which also
makes a wrong row harmless rather than authoritative.

**Still not in lists or search**, so the genuinely expensive half stands: the hash is known only
after the bytes arrive, and a list is drawn from an index that has none. Using it in Now
Playing is what will say whether that is worth it.

## B19. Server-hosted periodically updated catalogue indexes (Cloudflare / Google Cloud)

Instead of relying solely on archives providing their own monolithic index files or querying third-party
APIs directly from every device, maintain a server worker/job (e.g. on Google Cloud or Cloudflare Workers/R2)
that aggregates, scrapes, or indexes archives (like The Mod Archive, Aminet, etc.) and exposes clean,
pre-compressed index files that the phone downloads once and browses offline.

Benefits:
- Solves archives that lack one-file indexes (e.g. The Mod Archive, Aminet).
- Keeps API keys private on the server backend rather than embedding them in an open-source client.
- Fast, bandwidth-efficient downloads cached globally via Cloudflare.

**Observation (owner, 2026-09-02):** In apps like ZXTune that attempt live online hierarchy browsing
of The Mod Archive without a monolithic index, opening directories takes ages (each level requires
real-time HTTP fetches and remote scraping). Full offline index caching (Variant 3) is therefore
**necessary** rather than optional if directory-based browsing of The Mod Archive is to be usable and
fast. Live search provides immediate utility, but full browsing requires the server index pipeline.

## B13. ~~Tapping the player bar scrolls the list to that track~~ — DONE 2026-09-02

The dock says what is playing; the list does not say where it is. Tapping the identity row — or the
Now Playing it opens — should take you to that row in the playlist.

Cheap: `LazyListState.animateScrollToItem` on the index the queue already knows. It shares its
mechanism with B14 and with `docs/BACKLOG.md` A3.

**Built** as the locate button in Now Playing. Not confirmed on a device.

## B14. ~~A follow-the-playing-track toggle~~ — DONE 2026-09-02, **REPLACED 2026-09-10**

Asked 2026-09-01 and settled 2026-09-02.

**Replaced by `KeepRowInView`, and the button is gone**, withdrawn on 2026-09-10 as no longer
needed. It came out of the Random view: there the playing
row is kept on screen as next and previous move it — one row at a time, and only when the row would
otherwise leave — and that is wanted on every list. Once every list does it unasked, a button to
switch it on has nothing left to do.

**This reverses the principle the entry below was built on**, and that should be said rather than
left for the next reader to notice. The FAB's whole argument was *"nothing ever steals the view"*:
following was opt-in, and the first drag switched it off. The replacement does move the view when
the track changes. What keeps it from being the thing that argument feared is how little it moves:
never while the row is visible, one row when it is not, and **never on arrival** — coming back to the
playlist from Browse leaves you where you were reading, and "Show in playlist" in Now Playing stays
as the deliberate jump.

What follows is the entry as it was, kept because the two problems it names are real and the second
of them is exactly what `KeepRowInView` had to solve again.

The wish: press next on shuffle and the playing track is somewhere off screen.

**The agreed design, and it is better than the chip first proposed.** A floating button, bottom-right
over the list, labelled to say what it does — *follow track on list*. Off by default. Tap it and the
list starts following playback, and the button disappears because it has nothing left to offer.
Scroll the list by hand and following stops and the button comes back.

Why it beats the chip: mine was a one-shot jump, so a user who *wants* to follow along has to keep
asking. This is a mode you opt into, and the gesture that cancels it is exactly the gesture that
means "I want to look somewhere else". Nothing ever steals the view, and nobody has to keep pressing.
It composes with B13 (tap the player bar to jump once) rather than replacing it.

**Two things that will bite, worth knowing before starting:**

- **Telling our scrolling from the user's.** When following is on, *we* scroll the list — and that
  must not be mistaken for the user scrolling and switch following off. `isScrollInProgress` alone
  cannot tell them apart; the distinction is in `LazyListState.interactionSource`, where a real drag
  arrives as a `DragInteraction`.
- **How far to scroll.** `animateScrollToItem` across three hundred rows is a long, silly animation.
  Animate when the target is near, jump when it is far.

**Built**, both bites included: a real drag is told from our own scrolling through `DragInteraction`,
and a far target jumps rather than animating. The button was then asked to be subtler,
which was done the same day. Not confirmed on a device.

## B1. ~~Play a track from the search results~~ — DONE 2026-09-02

Already written up in `docs/BACKLOG.md` A1; repeated here because it has been asked for twice, which is a signal about
priority rather than a duplicate. **Built** — the results become the queue while you are in them, so
next and previous walk what you found and the playlist is untouched.

## B2. ~~Jump to a tune's neighbours~~ — DONE 2026-09-02 for catalogue tracks

Something plays at random, it is good, and the question is "what else did they write". An action — from the row's overflow menu or from the
Now Playing — that opens the browser **at the place the track came from**: the author's folder
in Modland, the folder on disk.

The refinement is the good part: for a **local** file this should not open the browser at all, it
should just show the path. There is nothing to browse to that the user does not already have.

Depends on knowing where a track came from, which `TrackRef.subtitle` already carries for both
sources, though in two different shapes — see the note under A1 about what a result line owes the
reader.

## B3. ~~Show the catalogue path in Information~~ — DONE 2026-09-02

A Modland track's information should read `Modland/Przunk/name.mod`. Today the dialog shows what is in `subtitle`, which for a
catalogue track is `format · author` — right for a row, wrong for an information panel. Same
underlying inconsistency as the wish above.

## B15. ~~History should say when~~ — DONE 2026-09-03

The list says what was played and in what order, and not **when**. "That tune two days ago" is the
question history exists to answer, and a date and time is most of the answer — the ordering alone
only says "before that other one".

Cheap: `played_at` is already stored, to the millisecond. What needs deciding is the *form* —
"yesterday, 21:14" reads better than a date for anything recent and worse for anything old, and
grouping the list under day headings may be better than putting a timestamp on every row.

**Raised again 2026-09-03**, and it is what history is for: asked whether
replaying should reorder the list (`docs/BACKLOG.md` A18) the answer was no, and *"what I care about more
is the date and time in history."*

**Built** with `DateUtils.getRelativeDateTimeString`, so it reads "yesterday, 21:14" while that is
useful and becomes a date when it is not — and is translated by the system into languages this
project does not ship. It shares the source line rather than adding a third. **Day headings remain
the open half**: they may well read better than a stamp on every row, and nothing here forecloses
them.

## B16. ~~The share-file and share-link icons are the same~~ — DONE 2026-09-02

Both used `PlayerIcons.Share`. The link has its own now — two actions that do
different things should not be told apart only by their labels.

## B17. A mis-tap in an online list now costs a download

Once tapping a row plays it, tapping the wrong row in a Modland folder fetches a file. These are
kilobytes so it is not a disaster, and before the change a mis-tap cost nothing at all.

Worth thinking about rather than fixing: the honest options are a short delay before the fetch
starts, cancelling the fetch when another row is tapped within a moment, or simply accepting it.
The middle one is probably right and is nearly free — `openJob` is already cancelled on a new load.

## B18. ~~Add a track to a *different* playlist, from its menu~~ — DONE 2026-09-02

Every row now has a three-dot menu whose **Add to the playlist** means the active one. This is the
other half: put this tune in one of the others without leaving what you are doing to switch
playlists and come back.

**Built** with a destination picker (`AddToPlaylistDialog`) accessible from the three-dot menu in both
the active playlist and Browse, in bulk selection mode, and in Now Playing. Adding to a different
playlist commits to disk immediately without affecting the active playlist's draft, and confirms with
a snackbar message naming the destination playlist (`docs/ARCHITECTURE.md` §17). Users can also create
a new playlist on the fly from the picker.

**Related:** `docs/BACKLOG.md` A4 candidate bulk actions share this picker machinery.

## B19. Should an export carry the music, not just the list?

Today an export is a list of references (`docs/BACKLOG.md` A25). A **zip holding the actual files**
would make a playlist self-contained: it would open on a phone that has never seen those tunes, and
survive the library being reorganised, both of which the reference form cannot do.

**The reason it is a wish and not a task** is that it stops being a file-format question. Exporting a
list of names is describing music; exporting a zip of the files **is distributing it**. Most of this
is demoscene and game music of uncertain ownership — the same uncertainty already recorded for
sc68's replay binaries in `docs/LICENSES.md` — and a button that quietly turns a personal library
into a shareable archive deserves to be a decision rather than a side effect.

Worth weighing when it is:

- **Size.** These formats are kilobytes, so a hundred-track playlist is a few megabytes. That is the
  argument *for*: unlike most music, this can actually travel.
- **Which files.** A catalogue track is a URL that anyone can fetch, so including its bytes adds
  weight for nothing. Local files are the ones that cannot travel otherwise — so a mixed export
  would sensibly carry the local files and reference the rest.
- **Import would need to unpack somewhere**, which means the app owns a copy of music the user
  already had. That is a storage question on top of a licensing one (`docs/BACKLOG.md` A13).

## B4. ~~Play MP3 too~~ — MOVED to `docs/BACKLOG.md` A29 on 2026-09-09

*Raised 2026-09-01 as a wish and promoted to work on 2026-09-09 — it is a requirement, not a wish.
The reasoning below moved with it and is kept here only so the number is not a dead link.*

Likely simple, and there is a neat route: `minimp3` is a
single public-domain header, which is a fourth backend of about fifty lines rather than a whole
library. (Android's own decoder is not the easy answer here — the engine is native from the file
to the speaker, and routing one format through the platform instead would mean two playback paths
to keep in step.)

Worth a thought before doing it: this app is a retro chiptune player, and MP3 is the format its
whole point is *not*. Handy for a rip of something, out of place in a browse tree. Probably belongs
behind "play this file" rather than in the library scan.

## B5. A web player, with favourites and history synced to an account

The same music in a browser, sharing state with the phone through a Google or Cloudflare account. Recorded
as a thought to return to, not a plan.

Worth noting now, because it would change decisions we are making today: the decoders are C and
would need WebAssembly builds (libopenmpt already ships one; sc68 does not), and syncing state to
an account means a server, accounts, and somebody's data in someone else's hands — which is a
different kind of project from an app that reads files off a phone. The parts that would carry
over unchanged are the ones already kept free of Android: `PlayQueue`, and the schema in
`SchemaSql`.

**Expanded 2026-09-08 into `docs/PLAN_WEB.md`**, and two of the sentences above
did not survive it. Left standing rather than edited, because the plan explains what was wrong:

- **sc68 does have a WebAssembly build** — and one based on 3.0.0b, the version we moved to two days
  after this was written.
- **"`PlayQueue` and `SchemaSql`" was far too modest.** `PlaybackController` is nearly three
  thousand lines and imports Android six times; roughly 5,000 lines would carry over, not a few
  hundred. (The first version of this correction said 5,400 and counted imports alone. Portability
  turned out to be a property of the *graph*: that file imports 26 project types, ten of them
  Android-bound, and holds a `Context` it uses 41 times. `PLAN_WEB.md` §3 has the revision.)
- The third sentence — that an account is a different kind of project — was right, and the plan's
  §8 is about the part of it nobody had looked at: **a playlist that mixes a local file with a
  catalogue track cannot arrive intact in a browser**, and the obvious implementation loses rows
  without saying so.

The plan also finds that five of the seven hosts we fetch from already permit a browser to read them
directly, which was expected to be the wall and is not. It remains a wish; nothing is decided.

**And the account may not be needed at all** — `docs/PLAN_HANDOFF.md`, 2026-09-08, written after the
owner said what the wish was actually for: *play it in a browser at work, and be able to send music
to it from the phone, ideally with shallow pairing and no accounts.* What has to cross is a
**pointer, not a sound**: a fifty-track playlist is 1,992 characters compressed into a URL fragment,
against a megabyte of audio, and the browser may fetch the audio itself. So the first useful step has
no server in it at all, and the account this entry is named after turns out to be a thing the phone
already is.

## B6. Fold hard-panned channels together

Amiga modules pan channels hard left and right by convention, and on some phones one "speaker" is the screen vibrator: half the music is
effectively inaudible. Wanted is a mix control — full stereo, narrowed, or mono — applied in the
render callback where the gain already is. Note that libopenmpt has a stereo-separation setting of
its own, so part of this may be a backend option rather than a mix of ours.

## B7. Full metadata for any supported file

An info button or a long press, showing everything the backend knows about a track rather than the handful of fields the player
screen has room for. Should work on any file the current version can open, not only on what is
playing.

## B8. ~~A history of what was played~~ — DONE 2026-09-02

Somewhere in settings. Note that `PlayQueue` already keeps a history, but only within a session and only for the active playlist;
a real one is a table of plays with timestamps, which is a small schema change and a screen.
There is no settings screen yet either.

## B9. Optional visualiser on the main screen

R4 makes metadata the default; the visualiser returns as something the user switches on. Worth doing properly (a real scope or
per-channel VU driven by the render callback) rather than the "stiff" one being replaced.

## B10. ~~Online catalogue browsing — the rest of the archives~~ — SAME AS A7, folded 2026-09-02

*Spotted as a duplicate on 2026-09-02, correctly.*

This and `docs/BACKLOG.md` A7 are one item under two names: I raised it here as a wish on 2026-08-31
and it became agreed work as A7 the next day, without anyone striking the wish. **A7 is the live
one**, and it carries what this entry said — that these archives are how people actually get this
music, and streaming straight from them removes the "download and unpack it yourself" step.

Kept rather than deleted so the number is not reused and the duplication stays visible: a wishlist
and a backlog that share a topic will do this again.

## B11. Per-format playback settings

*Claude, 2026-08-31.*

Interpolation and stereo separation for trackers, SID model (6581 vs 8580) and filter curve, Amiga LED filter. This audience cares, and
it is cheap once the backend facade exposes it.

## B12. Gapless / crossfade between subsongs

*Claude, 2026-08-31.*

