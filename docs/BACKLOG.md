# Backlog

Work that is agreed but not done, ordered so each item can be picked up cold. Written 2026-09-01
after a device session. Entries state the requirement, not who asked for it or when.

A defect that exists in shipped behaviour lives in `docs/STATUS.md`, not here. This file is for work
not yet started.

**Read `AGENTS.md` (workshop root and project root) before starting any of it.** Work happens on a
branch off `develop`, one stage per commit, and nothing merges without the owner saying so.

---

---

# A — open work

## A51. One extension, two formats: a refusal that explains itself — **noted 2026-09-18**

**Both halves are working as designed, and the answer is a measurement.**

`.gtk` is two unrelated formats. Fetched both and put them through this engine:

| file | magic | what happens |
| --- | --- | --- |
| `Graoumf Tracker/Dumbo/demo.gtk` | `GTK\x01` | opens: "Graoumf Tracker v1", 88.9 s |
| `Beaver Sweeper/Steffo/nokia.gtk` | `+SNT` | refused: libopenmpt, "error loading file" |

Beaver Sweeper is an Amiga custom replayer — the ~29,000-file bucket `docs/ROADMAP_FORMATS.md`
step 2 assigns to UADE. It happens to share an extension with a tracker libopenmpt does play, and an
index built from filenames cannot tell them apart. Deciding by content would mean downloading half a
million files, which is why the index decides by name (`docs/BACKLOG.md` A6).

**The scale, counted in Modland's own index rather than guessed: four files.** 52 `.gtk` rows are
Graoumf Tracker and 4 are Beaver Sweeper, out of 516,118. Across the whole archive **18** of the 103
extensions this build claims appear under more than one format directory, and **5,079** rows sit
under a directory that is not their extension's main one — most of which play anyway, because
`.mod` under Fasttracker 2 is still a `.mod`.

**So: no blocklist, and no per-directory special case.** Four files do not earn machinery, and the
next archive would need its own.

**What is worth doing** is making the refusal say what the archive itself calls the file. The row
already carries its format directory — `catalogueFormatOf` reads it, and `OpenFailure.formatName`
already uses it for a format nothing here claims. A file that *is* claimed and still refused falls
into `FILE_REFUSED_WITH_REASON`, which repeats the decoder's own sentence and never mentions that
Modland files this under **Beaver Sweeper**. One clause, from data already in hand, turns a puzzling
refusal into an explanation — and it covers every one of those 5,079 rows, not just this extension.

## A50. A length learnt by playing it once — **agreed 2026-09-18; folded into A52 as its last step**

**The right answer, and bigger than the defect it comes from.** C67 stopped the app claiming a
length no file ever stated; C68 stopped the bar pinning itself at the end when there is none. What
neither does is give the bar something to draw — an unmeasured tune plays with an empty bar and a
counter, which is honest and unsatisfying.

But the app *does* learn the length, exactly once, every time such a tune plays to its end: the
engine returns a short render and `handleTrackEnded` runs. **Write it down and the second play has a
real total and a moving bar** — and it is a measurement of this file on this device, not a
library's default.

**The shape of it, as far as it is thought through:**

- A table keyed the way the song lengths are (`SongLengthStore` is the model), holding seconds and
  the date they were observed.
- Written **only on a natural end**, never on a skip, a stop, or an error: those measure the
  listener, not the tune.
- Read in `load()` when the backend reports nothing, and used exactly where an HVSC length is used
  today, so the fallback, the end-of-tune path and the bar all work already.
- **Not** written for a tune the fallback cut off, or the length recorded would be the setting
  rather than the music.
- Subsongs are separate tunes and need separate rows.

**What it costs**: a schema version, a store, one call at the end of a track and one at its start,
and its own tests. **What it buys**: every format with nowhere to write a length — NSF, AY, KSS,
GBS, SNDH without sc68's database, SID without HVSC — gets a real one after a single listen.

## A52. Song lengths from songdb for the Amiga formats — **DONE 2026-09-21, schema 17; confirmed on the phone**

`docs/PLAN_SONGDB_LENGTHS.md` has it in full. In one paragraph: songdb, from the same repository the
track metadata already comes from, has a length for **every one of the 3,792** Modland files the app
offers through UADE, per subsong, agreeing with our own measurement to a tenth of a second — and
lengths for the 1,172 that loop, which the measurement cannot give. It replaces the two-second wait
and the second emulator for every tune it knows, and gives looping tunes a slider. It is **not** a
source for consoles, SID or anything outside what audacious-uade plays: measured, zero such rows.
11.9 MB to download, 11.1 MB stored with an integer key (25.0 MB with the text key `track_metadata`
uses). The four decisions (D1–D4) were approved as recommended. **A50 is folded in** as the plan's last
step: a length learnt by playing becomes a row of the same shape.

## A49. Quotations from correspondence in the documentation — **DONE 2026-09-18**

A37 did this for the **code** and stopped at the door of `docs/`. The same sentences are still
there, and now the repository is public.

**How much:** about **111** lines across a dozen files carry an attribution like "Owner, 2026-09-14"
or a quoted request, and roughly **117** carry an italicised quotation. `docs/STATUS.md`,
`docs/BACKLOG.md`, `docs/WISHLIST.md`, the plans and `docs/review-round-*.md` all have them.

**The calibration A37 arrived at applies unchanged**: keep the finding, drop the provenance. A
defect is what the code did and what it should do; who noticed it on which evening is what git
history is for. Where a quote *is* the decision — a rule that was set — state the rule.

**What was done.** Every quotation and every attribution is out of `docs/`: 44 opening attributions
deleted where the entry below already said what they said, 31 more fused into prose and rewritten,
and about 250 sentences turned from "the owner saw" into what was seen. A dozen passages of Polish —
quoted requests, and one whole paragraph about the launcher icon — are English now, which is the
documentation rule anyway.

**What stays, and why.** Twenty-two mentions of the owner as a *role*: who decides, whose account
and key the release uses, that pushing is his action, that a merge waits for his word. Those are
rules, not provenance, and removing them would delete the very thing that tells a reader what is not
theirs to choose. `docs/letters/` stays untouched: it is correspondence with archive maintainers,
quoted deliberately as evidence of what was asked and answered. Dates on measurements stay too —
"measured 2026-09-11, 6,780 entries" is the provenance of a fact rather than of a person.

**One tension worth naming.** `/mnt/workspace/AGENTS.md` §13 says not to distort what was said,
because a paraphrase can land in the documentation as a false reason for a decision. Quoting was the
cheap defence against that. With the quotes gone the defence has to be discipline instead: **write
the requirement and the finding, never the motive.** "It plays for thirty seconds and shows 2:30" is
safe to paraphrase; "he wanted it because…" is not, and now has nowhere to live.

**Two things decided while doing it:**

- **`docs/letters/`** is correspondence with archive maintainers, quoted on purpose because it is
  evidence of what was asked and answered. That is not the same thing and should stay.
- The **dates on measurements** stay too: "measured 2026-09-11, 6,780 entries" is provenance of a
  fact, not of a person.

## A48. Two seconds pass before the playlist appears — **measured, one cause fixed 2026-09-18**

**The wrong screen was a defect and is fixed** (the empty playlist now says nothing until it knows
what it is talking about). **The two seconds are not fixed, and this is that.**

The five seconds on the first run are explained: that launch ran the migration from schema 14 to 16,
and `CATALOGUE_ARCHIVE_COUNT_V16` is a `COUNT(*)` per catalogue over `catalogue_tracks` — half a
million rows on a full index. One-off, and the price of the round's own change.

The two seconds after that are not explained, and are what a person meets every time. Candidates,
in the order worth measuring:

- **`restore()`** reads the playlist and every track in it. A few hundred rows, joined and turned
  into `TrackRef`s on the main dispatcher's turn.
- **`pruneUnknownCatalogues()`** at start-up: a delete against a table of half a million rows, and
  the only one of these that writes.
- **`enforceBudget()`** walks the fetched-file cache directory.
- **The database is 112 MB** and the first query after an upgrade reads cold pages.
- **`summaries()` and `grantedFolders()`**, added for the empty screen's question — small, but they
  are now on the path and should be ruled in or out rather than assumed innocent.

### What the measurement said

Built a `catalogue_tracks` of **516,000 rows** on this machine and timed the launch path against it.
Desktop SQLite, so these are a floor; a phone's storage is several times slower.

| | |
| --- | --- |
| `pruneUnknownCatalogues`, the delete over `catalogue_tracks` | **55.6 ms** |
| the same against the `catalogues` table | 0.0 ms |
| `summaries()` | 0.0 ms |
| asking *whether* anything is stale, from `catalogues` | **0.0 ms** |
| the v16 `archive_count` backfill, which ran once | 27.7 ms |

**`catalogue_id NOT IN (…)` cannot use an index.** A negation indexes nothing, and since v15 the two
browse indexes are partial (`WHERE playable = 1`), so they could not serve it even if it were
positive. `EXPLAIN QUERY PLAN` says `SCAN catalogue_tracks`: half a million rows, **inside a write
transaction**, on the one connection every other read at launch is queueing behind — `restore()`
and `summaries()` both wait for it.

**Fixed:** ask the `catalogues` table first, which is one row per catalogue and answers in no
measurable time, and do the delete only when there is something to delete — which is almost never.
`LaunchDoesNotScanTheIndexTest` holds it there as a query plan rather than a stopwatch, so it means
the same thing on any machine.

**Also fixed, while reading:** `restore()` asked `store.playlists()` twice.

### What is left

The rest of the two seconds is unaccounted for and the remaining candidates are unchanged:
`tracksIn` for a long playlist, `enforceBudget()` walking the cache directory, and cold pages in a
112 MB database. **Do not guess at those either** — the next step is a timing log around each, read
once on a phone.

## A47. Accented letters in a title come out as replacement characters — **noted 2026-09-17**

Opening `Zalza/akes lekhorna.mod` shows the title as **"�kes lekh�rna (za)"** — diamond
question marks where two letters should be.

**The tune is Swedish and the title is almost certainly "Åkes lekhörna".** Two bytes, `0xC5` and
`0xF6`, are Å and ö in ISO-8859-1 — the encoding an Amiga tracker wrote in 1993 — and neither is
valid UTF-8. Whatever decodes them replaces each with U+FFFD, which is the diamond.

**Where it goes wrong.** `native/engine/player_oboe.cpp` hands every string over with
`env->NewStringUTF`, which is documented to take *modified UTF-8*. A module's title is not UTF-8
and nobody said it was: it is raw bytes from a fixed-size field in the file. The engine already
scrubs control characters out of a title (`engine.cpp`, `title()`); it does not transcode.

**What it affects.** Titles, author names, and instrument and sample names (A34) — everywhere the
demoscene wrote in Swedish, German, Finnish or Polish, which is a great deal of Modland. The web
player reads the same strings through `UTF8ToString` and will show the same diamonds.

**The fix is transcoding, not guessing wildly.** Decode as ISO-8859-1 by default, which is right
for Amiga trackers, and take valid UTF-8 as UTF-8 where it is unambiguous — a byte sequence that
parses as UTF-8 almost never does so by accident. CP437 is the third candidate, for DOS trackers,
and telling it apart from Latin-1 is a guess; do not pretend otherwise. One function in the engine,
applied where the strings leave it, so both players get the same answer.

**Check the cache key before changing anything.** Titles reach `TrackRef`, the database and the
handoff to the browser; a title that changes shape must not change what a row is keyed on.

## A46. Nobody knew they had to index anything — **one-press download BUILT 2026-09-17, branch**

**The finding is real and it is the most serious of the three.** A player that opens empty, says
an empty screen and waits is a player most people close. The two screenshots from that round show
it exactly: an empty playlist, then Browse with three catalogues each saying "no index — tap
no index — tap the arrow to download it" — an instruction that only reads as one once you already know
what an index is.

**Downloading all of them in the background on first launch is the wrong shape of the right idea**,
for three reasons that are measurements rather than opinions:

- **It is 25 MB before anybody has heard a note**, and most of it is not wanted: Modland's index is
  5.76 MB, ASMA is a 20 MB archive, HVSC's song lengths are 5.2 MB, the songdb metadata is another
  download again. On a phone away from wi-fi that is somebody's data, spent by an app they have had
  for four seconds.
- **It is a decision taken on the user's behalf and invisible while it runs.** The one thing worse
  than an app that does nothing is an app that does something expensive without asking.
- **`Data safety` says we fetch on demand.** Fetching four archives at startup is not that, and the
  declaration is a promise.

**The answer, 2026-09-17: one button, and it fetches the lot.** That is sound, and it
answers both objections above, because the objection was never to the downloading — it was to
*nobody having asked*. A press is the asking. What it needs is the size on the face of it:
`downloadEverything` runs the steps in sequence with the total (46 MB) on the button, each step
keeping its own row spinner, and partial failure reported as partial.

**The measured total, `curl -I` on 2026-09-17:** Modland 5.49 MB, ASMA 19.18 MB, UnExoticA 1.68 MB,
HVSC 4.96 MB, songdb metadata 14.11 MB, Modland favourites 0.14 MB — **45.56 MB**. `DownloadSizes`
rounds each part up and `DownloadSizesTest` keeps the advertised total equal to the sum, so editing
one line cannot make the button lie.

**What the button deliberately does not fetch** is sc68's replay routines. They are not an index and
not metadata; they are binaries for one niche format, and they are the one download with a licence
question attached (`docs/LICENSES.md`). They stay their own row.

**And a sheet rather than one button, 2026-09-17.** The choice is the
user's: checkboxes, a total that follows them, and a way to stop a run. `DownloadPicker` is that
sheet and `DownloadPlan` is the rule behind it, as a pure function so the rules can be tested
without a screen. The one grouping decision: **the SID song lengths and Modland's
favourites are not boxes of their own** — they come with Modland, because neither is any use
without the index it describes, and asking about them separately asks the user to know what HVSC
is.

**Stopping keeps what landed.** Each step writes its own table as it finishes, which is why the
steps are sequential and separate rather than one transaction: stopping after Modland leaves
Modland indexed. The clean-up runs under `NonCancellable`, because the spinners are state rather
than a side effect of the coroutine — cancelled without that, every row the run had reached would
spin until the app was restarted.

**Still open, cheapest first:**

1. **Offer it, don't hide it.** A first-run card on the playlist screen — "Get some music" — with
   one primary action that indexes **Modland alone** and says its size. One tap, one download, and
   the app is full of music. The rest stay where they are for whoever wants them.
2. **Say what an index is, once, in a sentence.** "A catalogue is downloaded once so that it can be
   browsed without a network" is the whole idea, and it is not in the app anywhere.
3. **Make the arrow look like a control.** `DownloadAction` is a bare arrow beside a red line of
   text; the red reads as an error, not as an invitation. A labelled button that says *Pobierz
   indeks (5,8 MB)* answers both the "what do I do" and the "what will it cost".
4. **A word about metered networks.** The size is on the button, which is most of the answer;
   saying "you are on mobile data" would be the rest of it.
5. **Progress within a step.** A row spins; it does not say how far 19 MB of ASMA has got.
6. **Doing it automatically** is still not the plan, and the reasons above are unchanged.

**Measure before and after.** The question this answers is "did they get to music", and the release
that answers it is the one where nobody has to be told what an index is.

## A45. A jump from the playlist does not name the author the way a jump from Random does — **noted 2026-09-17**

The same action from two places, and only one of them says where it landed. From Random it is a
**digression** — `showNeighboursOf` sets the session's author, the header under the bar reads
"Browsing <author>", and there is a way back to the dice. From the playlist it is a plain jump:
Browse opens on the folder and the bar says only "Browse".

**The heading is not decoration.** It is the one thing on screen that says *whose* folder this is,
and it is arrived at from a tune rather than by walking the archive — so without it the
screen answers a question nobody asked ("here is a folder") instead of the one that was ("what else
did this person write").

Two things to settle when it is picked up:

- **The heading, not the digression.** A jump from the playlist has no session to return to, so it
  should get the heading and the author's name without the dice's "waiting" state or its back path.
  `SESSION_HEADER_HEIGHT` is shared for exactly this reason, and `arrivedByJump` already
  distinguishes a place you were put from one you walked to.
- **What the web does**, since it offers the same jump from the same two places
  (`docs/SPEC_RANDOM.md` wants them alike). Check before building, and fix both together if they
  differ — A43 is in the same corner of the same screen and the two may as well be one branch.

## A44. UADE's process model — **decided 2026-09-19, fork+exec; merged 2026-09-21**

Round 12 item 2 stopped here, which is what the round's rules say to do with a decision rather than
guess it. The recommendation was taken as it stood. Everything else about UADE is settled: the measurement (~29,000 Modland files), the
licence (`players/` downloaded from the page upstream publishes for it, never shipped), and the
song database it also needs (`conf/song.conf`, GPL-2-or-later — **not** `conf/songdb` beside it,
which is CC BY-NC-SA and cannot ship in a store app).

**The recommendation is fork+exec**, with `uadecore` inside `lib/<abi>/`, and it is a recommendation
rather than a preference because it was measured. The alternative — running `uadecore` as a thread
in our own process, where "the seam is one function" — founders on `uade.c:476`:

```c
f = lookup_amiga_file_cache(nameptr);
if (f == NULL) {
        uadecore_send_debug("load: request error: %s", nameptr);
        exit(1);
}
```

That fires when the **emulated Amiga program asks for a file that is not there** — a replay routine
wanting a sample that a damaged module does not carry, which is the same population of files that
produced C42 and C55. `exit()` is not an exception, so the guard on the engine boundary catches
nothing and the app simply disappears. Forty of the 51 exits are in that one command loop.

**What confirming it costs, so the trade is visible:**

| | |
| --- | --- |
| APK | +1.8 MB — `uadecore` 1.6 MB, `libuade` 0.17 MB |
| the browser | **cannot have UADE at all**: `fork` and `exec` do not exist in WebAssembly |
| so | ~29,000 tunes the phone plays and the page does not, which Browse must say out loud |
| the alternative | patching 51 exits, which is forking a library `docs/ARCHITECTURE.md` §3 says we do not fork |

**Not blocked on anything else.** Say yes and the work is the integration; say no and it is the same
integration with a fork of UADE in front of it. `docs/PLAN_FORMATS.md` has the full reading.

### What the integration turned out to need, 2026-09-19

Measured while building it, because none of it was visible from the decision:

- **Five files upstream's `configure` writes are not in git**, and a cross-compiler cannot ask the
  questions they answer — they would describe the build machine, not the phone. They are answered
  for bionic in `native/backends/uade/config/` and copied in by `scripts/fetch-uade.py`. One of
  them matters: glibc has `canonicalize_file_name` and bionic does not, tested with the NDK's own
  clang rather than assumed.
- **The 68000 emulator does not exist as source.** `build68k` and `gencpu` write 80,293 lines of it
  from `table68k`; they are host programs, so the fetch script runs them and the NDK compiles what
  they produce. Compiled in eight pieces, as upstream does.
- **`uadecore` is 1.2 MB stripped on arm64**, 784 KB on armeabi-v7a, 1.2 MB on x86_64 — close to
  the +1.8 MB the recommendation quoted, and position-independent with 16 KB-aligned segments,
  both measured on the built file.
- **The native libraries now extract at install** (`useLegacyPackaging = true`), which is not the
  modern default and is not a preference: an executable has to be a file on disk, and with the
  default packaging `nativeLibraryDir` holds nothing. It applies to every library, not only this
  one, and there is no per-file form of it. The download gets smaller and the install gets bigger.
- **A song is not always a file.** `uade_play_from_buffer` cannot do multifile and TFMX is
  `mdat.name` beside `smpl.name`, so the engine writes the tune to a scratch directory and plays it
  by path. `openBackend` takes companions, and the caller finds them: the next URL in the Modland
  directory, the next member of the UnExoticA archive, the next document in a granted folder —
  that last one only where the provider's document ids are paths.
- **The format list is measured, not read from UADE's table.** `eagleplayer.conf` declares 371
  markers; taken as a list it would have indexed 3,856 PlayStation `.psf` files UADE cannot play.
  `probe-uade.py --formats 60 --play 12`: 413 of 720. 33 extensions and two prefixes whose Modland
  directory played 10 or more of 12 — 3,810 files. Left out and why is in `SupportedFormats`.
- **Size, measured on the release APK**: arm64 native libraries 6.0 → 7.4 MB installed, and the APK
  itself 20 → 11 MB, because extracted libraries are compressed inside it again.
- **Run on the host through our own engine, 2026-09-21**: `scripts/probe-uade-engine.py` drives
  `UadeBackend` in the unchanged `engine.cpp` through the real `openBackend`, with the data
  directory laid out from the same GitLab archive the app downloads. **140 of 140** — four files for
  each of the 35 offered names, TFMX with its `smpl.` fetched beside it — plus five pairs played at
  once, and the scratch directory empty afterwards. It found two things the phone would have shown
  as "plays wrong" with no reason:
  - UADE walks on to the next subsong inside the same stream unless told `one_subsong`, so the
    position restarted mid-tune and the player's own "play all subsongs" was bypassed.
  - With one subsong per stream, a file whose first subsong is half a second of silence that ends —
    `reach for the skies-german.avp` — would play nothing. It now opens at the first subsong with
    sound, as `GmeBackend` does for HES and KSS; a subsong that is only quiet at the start is kept.

  Both were confirmed to be caught: with `one_subsong` removed the driver fails
  `subsong-advanced-by-itself`.
- **The phone, first contact, 2026-09-21**: `uadecore` started — the process model works — and
  `mdat.coolbass` was refused with "score died" although its samples had been fetched beside it.
  UADE finds the files a replay routine asks for with `uade_find_amiga_file`, which matches names
  without regard to case by walking the path **from `/`, listing every directory**. An app may
  pass through `/data` but not list it, so the walk failed on its second step. Reproduced on the
  host with a parent directory of mode 111, and fixed with `uade_set_amiga_loader`: names are
  looked up in the instance's own scratch directory, which is where this class put them. The host
  run now keeps both directories under such a parent, so it cannot pass what a phone cannot.
- **Replay routines counted**: 176, not the 178 first written, plus eleven player configurations
  under `players/ENV/EaglePlayer/` that the first unpacker dropped.
- **Length and seeking, 2026-09-21.** Nothing in these formats states a length, so the app showed
  none and offered no slider. Measured first: UADE renders 120 to 150 times faster than real time
  on the host, and a tune's replay routine reports its own end. Now, once a tune starts *playing* —
  never for a scan or the metadata pass, through `Backend::startedPlaying` — a second emulator
  plays the subsong silently to its end and the length arrives a few seconds in; the host keeps
  asking while `durationArrivesLater` says it may. Seeking runs the emulator to the position, under
  the same lock as every other backend's.

  **The trap, found by measuring rather than by reading:** with UADE's own timeouts on, 19 of 140
  tunes measured exactly 512.0 seconds. That is UADE's subsong timeout, reported the same way — even
  as a happy ending — as a routine that finished. The measurement runs with timeouts off; a tune
  that loops reaches the ten-minute cap and stays unknown. Host run: 140 of 140 with seeks forward
  and back, 122 with a length, 18 without, none at 512.

  **And then the phone showed no length at all**, and two mistakes of mine were behind it. The
  backend said "a length may still come" only *while* measuring, which stops being true at the
  moment the length is known — so the host, which asks only while that is said, stopped asking
  just as there was something to read. The host check did not catch it because the driver asked
  the backend directly rather than the way the host asks, and because UADE had quietly remembered
  the lengths of tunes my earlier measurements had played to the end, in `~/.uade/contentdb`, so
  the host had a length from the first frame without measuring anything. Both are closed: the
  driver now asks as the host does and fails `length-not-announced` on the old code, and every
  host run gets an empty home directory.
- **A dead emulator took the app with it, 2026-09-21.** Found on the phone as a crash on the seventh
  subsong of `cust.paradroid`, with no message. The host could not reproduce the trigger — all
  seven subsongs play there — but it could reproduce the kind of death: kill uadecore mid-tune and
  the process talking to it dies of **SIGPIPE**, because libuade writes to a socket whose other end
  is gone and the signal's default action ends the process. That defeats the reason UADE runs
  apart at all (the 51 `exit()` calls above). SIGPIPE is ignored now, the write fails with EPIPE,
  and the tune ends; the host run kills uadecore at three moments on every run and fails if the
  driver dies of a signal. **Why uadecore died on the phone is still unknown** — the fix makes the
  app survive it, and the next phone test says whether subsong seven then plays or ends.


## A43. "More from this author" opens an empty folder when the archive is not indexed — **noted 2026-09-16**

Confirmed by reading it: `showAuthorFolder` asks `archive.tracksIn(format, author, source)`, which
answers out of the stored index. With no index that is an empty list, so Browse opens on the
author's folder with nothing in it and no explanation — and the digression, if the dice was
rolling, is handed an empty queue.

**The action should be unavailable rather than silently empty**, which is the rule Browse already
keeps for walking into a Modland folder by hand. Two things to settle while doing it:

- **Where the guard goes.** `authorFolderOf` returning null is how the action already hides itself
  for a local file, so the same answer probably serves: no index for that source, no folder. That
  keeps one place deciding whether the action exists.
- **Whether the phone has it too.** It offers the same jump and reads the same kind of index, and
  the two players are meant to behave alike (`docs/SPEC_RANDOM.md`). Check before assuming it is a
  web-only fault; if the phone guards it, copy what it does rather than inventing a second answer.

A tune reached by a shared link is the case that makes this worth doing rather than shrugging at:
somebody opening a `#play:` link has no index at all, and "more from this author" is exactly the
thing they would try next.

## A42. ~~Line the Random screen's buttons up~~ — DONE 2026-09-16

**Named on 2026-09-16, with a screenshot:** Filter, in the heading, and Playlist in the bar directly
above it are neither the same size nor in line.

Measured off the screenshot rather than judged by eye — 864px wide at 2.1x — and it was **three
faults, not one**:

| | Playlist | Filter |
| --- | --- | --- |
| height | 134px — a 72dp pill clipped by a 64dp `TopAppBar` | 97px — 46dp, slim |
| width | 107px — a full pill's 48dp floor, and the label needs 51 | 118px — `SLIM_MIN_WIDTH`, 56dp |
| right edge | 32px — 8dp + 3dp seam + the bar's own 4dp | 40px — 16dp + the same seam |

Both are slim pills now, so both stand 46dp tall and both sit at the 56dp floor — neither label
needs more than that, so they come out exactly the same width rather than merely similar. And the
bar's edge is **derived** from the heading's (`TOP_BAR_ACTION_EDGE = SESSION_HEADER_EDGE -
TOP_APP_BAR_ACTION_PADDING`) instead of written down beside it, so moving the heading moves the bar
with it. The 3dp seam every pill keeps inside its own bounds cancels, since both rows add it.

**Not verified by rendering**: there is no emulator here (`AGENTS.md` §3), so the arithmetic is
checked against the screenshot's pixels and the fix wants an eye on the phone.

## A41. Digression mode: the dice waits while you browse an author — DONE 2026-09-14

**Today a digression ends the session**: playing a tune from a Browse list moves playback to that
list, and the dice is finished (C49 made the screen agree with that instead of lying about it).
This makes the dice **wait** instead.

- **Entered** by "More from this author" from a Random session.
- **While digressing the heading says what is going on**, in the shape the dice's own heading has:
  "Browsing author — <name>" where Random says "Playing at random — everything".
- **Back returns to the dice**: the author's tune stops, the record comes back with the cursor where
  it was, **paused** — "bo inaczej operator dostanie szoku". Play resumes that pick; next rolls.
- **One level of digression.** Inside the author's folder, sub-folders may be opened and left again
  up to that folder, and no higher: the way out above it is the way back to the dice.
- **The page does the same and looks the same.** It ended the dice outright when a Browse tune
  played, so this changed both runtimes together.
- **It ends for good** when something else takes the player: a playlist chosen, a file handed over by
  another app, a link opened. A way back that leads nowhere is worse than none.

**Built both sides.** The phone kept the record and the cursor already; what it gained is
`diceWaiting`, `resumeDice()` — which puts the pick back **selected, not started**, so the next press
of play starts it through `pendingRetry` — and a Back out of the folder that returns there instead of
to the playlist. `browseBack` already treated "More from this author" as one step rather than a
descent, which is the one level of digression, so nothing new decides that.

On the page the dice now waits inside the Browse session (`away.dice`) instead of being thrown away,
the heading names the author, the heading's button offers the dice rather than the playlist, and
Back out of the author's folder resumes it. Eleven page checks walk the whole journey, including a
playlist chosen mid-digression, which ends both.

## A40. Protracktor links open in the app — **parked 2026-09-14, waiting for a fixed address**

A link to the page, opened on a phone that has the app, should open in the app.

The fragment survives the trip into an app intact, so that is not the obstacle. The address is:
Android binds an app to a **named** host, verified by a file served from it, and the page's tunnel
takes a new random name every time it starts. The two shapes that work are a scheme of our own
(`protracktor://…`), which an https link in a chat will never use, and App Links against one stable
host with `assetlinks.json` on it. Worth doing when the page has a permanent home; brittle before
that.

## A39. An MP3's cover as a thumbnail — **parked 2026-09-14**

## A38. Share several tunes as one link — DONE 2026-09-14

**The limit is arithmetic**: about 2,000 characters is what a link can be everywhere, which is some
fifty tracks; past that `pack` already drops the names of files that cannot travel and says it did.

## A37. Comments cut back to implementation facts — DONE 2026-09-17

**What to strip.** Quoted requests, dates, who asked for what, the argument a decision won, "this
used to be X and it was wrong", and anything restating what the line below already says.

**What to keep.** What the code cannot say about itself: a constraint that is invisible locally
(SQLite's REPLACE deletes and cascades, so the track row is never replaced), a platform rule (a
foreground service has about five seconds to post its notification), a measurement the choice rests
on (buckets: 43,715 writes against 515,509), and the licence headers. Keep them as plain statements
— the fact, not its provenance.

**Where the rest belongs, so nothing is lost by deleting it:** `docs/STATUS.md` for defects and what
caused them, `docs/BACKLOG.md` and the plans for decisions and their reasons, and git history for
who asked and when. Every quote now in the source already has a home there or can be given one in
the same commit that removes it.

**Names first.** Where a comment explains a name, rename instead: the comment goes and the reader
is helped everywhere the name appears, not only here.

**How it was done:** one area per commit — the engine, the store, the controller, the screens, the
page, the scripts and the tests — with the suite green after each and no behaviour changed in the
same commit as a comment change.

**What it found on the way.** Eight KDoc blocks had come loose from what they described and were
attached to the wrong declaration or to none: the in-flight-open and read-ahead notes in
`PlaybackController`, `postQueue`'s `@param`, a stale `deletePlaylist` doc still claiming the last
playlist cannot be deleted, the CX40 and link notes in `PlayerIcons`, the track-list and track-row
notes in `BrowseScreen`, and one in `WebRemoteTest` sitting on the wrong test. Each is now on the
declaration it belongs to. One comment was stale and contradicted the code: `StorageSection` said
it lived in Browse "rather than in a settings screen", and `SettingsScreen` draws it.

**Where the findings went.** The seek rule rescued from a deleted comment is `docs/ARCHITECTURE.md`
§5; the fallback length points at C56; the CORS-with-a-GET rule is in `web/src/catalogue.js` and
`docs/PLAN_CATALOGUES.md`. Everything else that was provenance is in the commits that removed it.

## A36. A gear beside shuffle, opening the page's settings — DONE 2026-09-14

## A35. "Add to playlist" in the web's track menu — DONE 2026-09-14

## A34. Instrument and sample names in Now Playing — DONE, tested 2026-09-15

## A33. Whether the page's Browse lists should stop writing into the playlist — DONE 2026-09-11, the phone's way

**Decided on 2026-09-11, by using it.** A search for "zool", a press on `zoolook.mod`, and the
playlist made earlier was replaced by every result on the screen, which is not how the phone
behaves. What is expected, in order: a search shows tunes, not folders; pressing one
plays it, with Add and the other actions beside it; and when browsing is done, the playlist holds
what it held before plus exactly the tunes that were added.

**Built on `feature/web-browse-transient`** (`docs/PLAN_WEB_LIBRARY.md` S4c): every Browse list plays
through the transient session History and Random use, Browse became a screen with the dock under it
rather than a dialog over it, Add appends to the playlist and waits for Save, and S4a's block went
with nothing left to block. The question as it was put is kept below.

*Raised by `GOAL.md` round 8 on 2026-09-11, while building item 4.*

On the phone, playing from any Browse list — a folder, a search, History — plays through a queue of
its own and leaves the playlist exactly as it was; the playlist goes behind glass until you come
back. **On the page, a folder or a search result replaces the playlist that is showing** — which is
why Browse is shut while "From the phone" is up (`docs/PLAN_WEB_LIBRARY.md` S4a), the rule
agreed when it was first used.

Round 8 asked for History to play "without writing into the playlist", and it does: it uses the
transient session Random built (item 3), with a heading and a way back. So the page now has both
behaviours at once — History and Random leave the playlist alone; a folder and a search result
replace it.

**Which of these the rest should follow is still to choose:**

- **Leave it.** Browse fills a playlist on the page, which is a way of *building* one; History and
  Random are listening, not building.
- **Make the page match the phone.** Every Browse list plays transiently, the way History now does,
  and filling a playlist becomes an explicit "add". The S4a block would then have nothing left to
  block.

The machinery for the second exists since item 3; the decision is about what Browse is *for*.

## A32. ~~ZXTune in the browser's engine~~ — DONE 2026-09-15

*Raised by `GOAL.md` round 8 on 2026-09-11, which was told to record it rather than decide it.*

The web engine was built with `-DPROTRACKTOR_WITH_ZXTUNE=OFF`, and since round 8 item 1 the page
indexes only what it can play, so the absence had become a number on the screen: **26,537 of
Modland's tunes played on the phone and not in the browser** — `pt3` 7,376, `pt2` 6,284, `ym` 4,977,
`stc` 3,639 and the rest of the Spectrum's formats.

*Everything from here to the answer below is what was written **before anybody tried the build**,
and it is kept because the lesson is in it.* The reason it was off, inherited from a comment in
`web/src/app.js`: ZXTune does not build under Emscripten as it stands, and making it build means
patching a library `docs/ARCHITECTURE.md` §3 says this project does not fork.

So the question was thought not to be "switch it on" but **one of**:

- keep it off, and the browser stays a player for everything but the Spectrum — said plainly in
  Browse, which is what item 1 delivers;
- carry a patch to ZXTune for the wasm build, against the no-fork rule, and measure what it adds to
  the engine's 2.6 MB;
- find the smaller piece — `ym` and `vtx` are register dumps rather than trackers, 5,856 tunes
  between them, and their decoder may be separable from the rest.

### The answer, 2026-09-15: the second option, and it cost eight lines

**Agreed, and the first thing to do was read a real log rather than
that comment.** ZXTune under Emscripten fails on **eight lines in five files**, every one a `const auto*` initialised from a
`std::string_view` iterator: a raw pointer in the NDK's libc++, a `__wrap_iter` in Emscripten's,
where ABI version 2 makes it one deliberately so that code cannot rely on the detail. Nothing was
reimplemented, no logic changed, and Android compiles the same sources.

The patch lives in `scripts/fetch-zxtune.py` rather than in the tree, because a fetch re-clones the
directory and a hand edit would vanish; each entry states how many times it expects to match, which
caught a real mistake — one pattern occurs **twice** in `encoding.cpp` and a blind replace had
quietly done both.

The page needed **no change at all**: `absentDecoders()` reads the engine's own fingerprint, so the
decoder turning up there is the integration. Measured cost: **2.65 MB → 3.21 MB** over the wire,
+21%, for **26,537 Modland tunes**. `.pt3`, `.asc` and `.stp` were played through the built engine,
so this is "plays" rather than "links". `docs/PLAN_WEB.md` §14 has the detail.

**The order of work it was done in**, written before any of it and worth keeping because three of
the four steps turned out to be unnecessary:

1. **Try the build first**, unpatched, and find out exactly what fails — rather than trusting the
   comment. *This step was the whole answer.*
2. **If it does not build, size the patch** before arguing about it: a portability fix is not a
   fork, a reimplemented backend is, and `docs/ARCHITECTURE.md` §3 forbids only the second. *Eight
   lines, none of them logic.*
3. **Measure what it costs the page**, because every byte is paid on first load. *+21%.*
4. **`ym`/`vtx` alone is the fallback**, not the goal — 5,856 of the 26,537. *Not needed.*

**The general lesson, which is the reason this entry keeps its old reasoning**: "it does not build"
sat in a comment for a week and was repeated into three documents without anybody running the
command. The cost of checking was one configure and one build.

## A31. Haptics in the seven places named — DONE 2026-09-10

**This overturns A8's closing rule**, which forbade haptics on anything with a visible result. What
survives of that rule is its reason: haptics everywhere is noise, and noise is what makes somebody
turn the setting off system-wide, at which point the useful ones go too. So the answer is not "no",
it is **weight** — the buzzes that carry information a finger cannot otherwise get keep the firm
effects, and the ones that are only an accent get the lightest the platform has.

The vocabulary in `ui/Haptics.kt` grew from three to eight, each with a fallback because `minSdk`
is 29 and the interesting constants arrived in API 30 and 34:

| | effect (API 34 / 30 / 29) | where |
|---|---|---|
| `press()` | `CONFIRM` / `CONFIRM` / `KEYBOARD_TAP` | indexing arrows, the replay row, the pairing camera, play-pause, next, previous |
| `toggle(on)` | `TOGGLE_ON`,`TOGGLE_OFF` / `CONTEXT_CLICK` | shuffle, repeat, the two Settings switches, the segmented pickers, the selection checkboxes |
| `grab()` | `DRAG_START` / `GESTURE_START` / `LONG_PRESS` | taking hold of the seek thumb |
| `scrub()` | `SEGMENT_FREQUENT_TICK` / `CLOCK_TICK` | each notch while dragging it |
| `transition()` | `CLOCK_TICK` | arriving at another view, another folder |
| `tick()` | `SEGMENT_TICK` / `CONTEXT_CLICK` | a row that actually changed places *(fallback strengthened from `CLOCK_TICK`: this says a thing happened, and on an older phone the old one was faint enough to be missed under a moving thumb)* |
| `pick()` | `SEGMENT_TICK` / `CLOCK_TICK` | a subsong chosen from the strip |

**Four more the same day, after the first seven were felt on a device.** *"Delikatnie na subsong, mocniej na
przyciski funkcyjne save revert…, track song"*, and a question about the list:

- **Function buttons** — `LabelledAction` is the shape every one of them wears, so the press lives
  there and reaches Save, Discard, the five track actions in Now Playing, and the two bulk actions
  in the playlist at once. **The three that navigate pass `haptic = null`**: arriving already
  buzzes, and two buzzes for one press reads as a stutter rather than as emphasis. Its long press
  gained `gestureEnd()`, which closes the loose end A8 left open — a long press with no answer
  feels like a press that missed.
- **Choosing a tune** — `press()`, the firm one, on the tap this whole screen exists for. The same
  tap while selecting is a tick in a box instead, so it feels like the checkbox beside it.
- **Long press that starts a selection** — `gestureEnd()`. A8 owed this since 2026-09-01.
- **Subsongs** — `pick()`, and nothing at all for the one already playing.

### A31a. ~~A notch per row while a list is dragged~~ — BUILT AND REMOVED, same day

*The question was whether scrolling a list should buzz faintly once per row. *The answer, after
feeling it:* **"wywal to przewijanie bo nie jest
fajne."**

Built as `HapticOnRowScroll`: one `scrub()` per row, and only while the finger was down, so a fling
across fifty rows would not fire fifty times. The care went into the right problem and the thing
was still wrong — a list is a surface you read, and giving it a texture makes reading it feel like
operating it.

**Kept here rather than deleted** because the idea is an obvious one to have twice. It was tried, it
worked as designed, and as designed it was not nice. The divisor this entry once proposed as the
fix would not have saved it; less of something unpleasant is not pleasant.

### A31b. The follow-track button had none

*Moot since the evening of 2026-09-10: the button itself was removed, replaced by lists that keep
the playing row in view on their own (`docs/WISHLIST.md` B14). Kept so the haptics table above
does not appear to have lost a row without a reason.*

`toggle(on = true)` on the press, and deliberately **nothing when following switches off** — that
happens because the user dragged the list, and a buzz mid-scroll would be answering a gesture
nobody aimed at the button. On is a press; off is a side effect of looking somewhere else.

## A1. ~~Search results need the path too, and a way to hear a track first~~ — DONE 2026-09-02


Both reported 2026-09-01, both about the Search view specifically.

**The path is missing from search results.** Adding a folder now shows where a file lives, but a
search result still shows only the last part — "AMIGA", "SNDH". Both are wanted: the format or
archive it came from *and* the path within it, like `ASMA/Przunk/Bonio`. Local results have the path
in `subtitle` already; catalogue results put `format · author` there instead, so the two disagree
about what that field means. Decide what a result line owes the reader, and make both sources answer
it the same way.

**A track cannot be played from search before being added.** You can tick a row but not hear it,
which is backwards: the point of a search is to find out what something is. A play control on the
row is the obvious shape — the right-hand side was suggested — but there is a real question
behind it: **what do next and previous mean while listening from a search?** The answer that matches
everything else here is that the results become the queue for as long as you are in them, the way
Random has its own history. Playing from search must not quietly rewrite the playlist.

## A2. ~~Subsongs~~ — DONE 2026-09-03


game-music-emu reports track counts in the hundreds — one GBS in the sample said 99, an HES said 256
— and we play track 0 and nothing else. sc68 and ASAP report subsongs too. This is a defect now,
not a gap.

**A subsong is a property of a track, not a track of its own.** That is decided.
Expanding a 99-track GBS into 99 rows was rejected outright — a folder of twenty such files would
become a playlist of two thousand rows.

**The constraints on the UI, to be designed against:**

- **(a)** It must be possible to play all subsongs in order.
- **(b)** It must be possible to play only subsong 0 and no others.
- **(c)** It must not cover the track's own view — the track is the *representative*, and it is what
  is on the list.
- **(d)** It must be easy to reach: no clicking through several places, and no complicating the way
  back to the playlist.

**Designed on 2026-09-03 and built.** What was agreed, and where each constraint
landed:

| | |
| --- | --- |
| **Dock — transport** | untouched. A control that does nothing for most files does not belong beside shuffle and repeat |
| **Dock — title row** | now visibly a control, and says *"tune 3 of 15"* when the mode is on |
| **Expanded player** | the strip of tunes and the mode toggle — **(c)**: that screen *is* the track, so nothing is covered |
| **Playlist row** | the count, in both modes |

**The mode decides what the transport means, and that was a correction to the first design.** The
first proposal had `next` walk tunes inside any multi-tune file. That makes a
button mean different things depending on the *file* — which is not something the user chose — and
tied it to the mode instead: **"all tunes"** and next walks them, **"first only"** and next is the
next file. That is exactly the principle behind the first complaint this app ever had, that next and
previous behaved unpredictably. I had it written down and proposed the opposite anyway.

**Default is "first only"**: a file reporting 256 subsongs should not take over a
listening session the first time one appears.

**Two things the design would have failed on:**

- The toggle belongs where the track's settings are, not in the transport bar. I read "in the dock"
  and "in the place of the track's settings" as the same place; they are not, and what was meant was the
  second.
- **The dock's title row did not look clickable**, and it is the way to all of this. Fixed with the
  same remedy the playlist name in the top bar needed: a visible surface and a chevron.

**One hole in the design, named and then closed properly.** We had agreed the count would appear
only in "all tunes" — which meant you had to already know a file held fifteen tunes in order to
switch to the mode that would tell you. A first patch put an indicator in the dock; the
condition was then removed altogether: *"we can always show 1 of n, even in play-only-one."* That is
right, and it is the simpler rule. A count is true whatever the mode does with it.

**Still not solved, deliberately:** console formats over-report. A GBS claims 99 and an HES 256, and
many are silence or sound effects rather than tunes. We already store per-subsong durations for SID
and GME knows its own, so greying out the empty ones is reachable — but it is a separate question
and is not tangled into this.

Two observations to bring to that conversation:

- (a) and (b) together mean subsong playback is a **mode**, like Random — "play this one" and "play
  through them" are different intents and the transport has to know which. Random already
  established that shape and could be followed.
- (c) argues against a sheet or a dialog and in favour of something inline on the Now Playing
  screen, since that is the one surface where covering the track is not a problem.

Schema is not a constraint: the database can change freely, and a tester can
reinstall, since nobody else uses the app yet.

## A3. ~~Getting up and down a long list~~ — DONE 2026-09-03

**Decided**, after reserving this for a conversation on 2026-09-02: *"I want a visible
scrollbar on the right that you can grab and drag down."* So it is a real thumb the user can take
hold of, not a fast-scroll gesture and not jump-to-letter.

Worth knowing before building: the playlist already has a drag handle per row for reordering, and a
draggable scrollbar is a second drag on the same screen. They must not be confusable — the handle is
inside the row, the scrollbar is at the edge, and that separation has to survive selection mode
(`docs/ARCHITECTURE.md` §17) where rows also respond to a long press.

**Built**, on the playlist and on Browse's track lists. Ten device-independent pixels at the very
edge: wide enough to take hold of, narrow enough that a thumb scrolling the list does not land on
it. It positions by **item counts rather than pixels** — rows here are one height, and asking a lazy
list for the pixel extent of half a million unmeasured items is not a question it can answer — and
it drags with `scrollToItem` rather than an animated scroll, because a list animating its way
towards a finger reads as lag. Absent when everything already fits, since a scrollbar for six rows
is furniture.


Raised 2026-09-01. Three hundred tracks is a lot of flicking.

This is the other half of the ordinal conversation. The number stayed on the row because it is the
only thing that says *where in the list you are* — but that is a read-out, not a control. What is
missing is the way to **go** somewhere.

Two shapes, and they answer different questions:

- **A scrollbar you can drag.** Appears while scrolling, grabbable, drags the list. Answers "take me
  roughly two thirds down". Standard, and Compose has no built-in one, so it is a custom
  `LazyListState`-driven overlay — not hard, but not free either.
- **Jump to top / bottom.** A button that appears once you are far from either end. Answers "take me
  back to the start", which on a playlist is the more common wish, and costs almost nothing.

Worth doing the second first: it is a fraction of the work and covers the case that comes up most.
The scrollbar can follow if flicking still annoys.

**If a draggable scrollbar lands, revisit the ordinal.** It was kept because nothing else reported
position; a scrollbar reports it better and takes no row space. That is not a reason to remove the
number now, but it is a reason to ask again then.

## A4. ~~Bulk operations on the playlist~~ — DONE 2026-09-03

**Agreed 2026-09-02 (evening):** *"long-press/bulk operations should be implemented the same way on
the playlist"*, and the actions were settled on 2026-09-03. So the gesture and its behaviour come straight from `docs/ARCHITECTURE.md` §17 —
long press to start, tap to tick, back to leave with nothing ticked, no layout shift when the
checkbox arrives.

**Decided 2026-09-03.** Three actions: **add to another playlist**, **remove
from this playlist**, and **make a new playlist from these**. *Play these only* was my guess and he
did not ask for it; it is not in.

**Built.** Long press starts selecting, exactly as in Browse, and the checkbox takes the ordinal's
slot — already reserved and already that size, so entering selection moves nothing.

**Corrected after use:** the actions are icons with labels like everywhere else rather than
bare text; the bar is one row, because the dock's height was being applied *inside* it and made it
three; and the follow-track button is hidden while selecting, since it floats over the corner where
the actions now are.

**Two buttons for three actions, and that is not a shortcut.** *Add to playlist…* opens the picker,
which offers an existing playlist **or a new one** — so "make a new playlist from these" is in there
rather than missing. *Remove* is the other. Say if you would rather see three.

**Undo restores the whole selection**, which was the thing that could lose data. `TrackEditing` is a
separate object with a round-trip test over two hundred generated selections, because the ordering
is the trick: putting the lowest index back first makes room for the next, and going the other way
returns a list that is subtly wrong rather than obviously broken. Reversing that order fails three
tests, which was checked by reversing it.

**Two things §17 does not cover, because Browse does not have them:**

- **The drag handle.** Long-press-to-select and drag-to-reorder are different gestures on the same
  row, and the handle is what keeps them apart. Selection must not make the handle ambiguous.
- **Undo.** A bulk delete is one edit, not twenty, so undo has to restore the whole selection. The
  current single-track `lastRemoval` will not do.



Raised 2026-09-01. Long-press a row to start a selection, then **tap** further rows to add them —
tapping, not only dragging, because a selection of scattered tracks is the normal case and dragging
only reaches neighbours.

While a selection exists the per-row controls give way to actions in the top bar. Delete is the
obvious one and it was named first; the others are worth thinking about rather than guessing —
plausible candidates are *move to another playlist*, *add to another playlist*, and *play these
only*. The last is interesting because it is close to what Random already does: a temporary queue
that is not the playlist.

**Two things it will collide with**, worth knowing before starting:

- **The drag handle.** Long-press-to-select and drag-to-reorder are different gestures on the same
  row, and the handle is what keeps them apart today. Selection must not make the handle ambiguous.
- **The draft model.** A bulk delete is one edit, not twenty, so undo has to restore the whole
  selection — the current single-track `lastRemoval` will not do.

## A9. ~~The app icon~~ — DONE 2026-09-02

Raised 2026-09-02: the launcher icon was a placeholder (`@android:drawable/ic_media_play`). Google Play
flagged this on upload.

**Built:** Created a proper adaptive icon (API 26+) featuring stylized tracker equalizer bars in cyan
on a dark indigo background (`res/drawable/ic_launcher_foreground.xml` and `ic_launcher_background.xml`),
along with pre-rendered fallback PNGs across all five screen densities (`mipmap-mdpi` through
`mipmap-xxxhdpi`) including round variants (`ic_launcher_round`). Updated `AndroidManifest.xml`.

**Redrawn 2026-09-03:** the mark chosen from the first sheet of concepts is **P + Play**. The letter
`P` is four separated tracker channels of regular cells in two shades -- two running to the bottom,
two building the letter's bowl. The Play triangle is a transparent cut in the third channel alone,
stopping just short of its right edge. A near-black field replaced the blue circle, which was not in
the concept that was picked.

`scripts/GenerateLauncherIcons.java` keeps the adaptive icon, Android 13's monochrome icon and the
PNG fallbacks for older launchers from one geometry, and writes the full 512 x 512 px file to
`artwork/protracktor-launcher-icon-512.png`. The mark occupies 82% of the source geometry about the
centre of `54 x 54`, so that a margin survives the launcher's round mask as well.

## A8. ~~Haptics for the gestures that deserve them~~ — MOSTLY DONE 2026-09-02

Three of the four landed: picking a row up, putting it down, and each position it crosses, plus the
snackbar's dismiss threshold. The fourth — long-press that starts a selection — belongs to A4 and
should be added there rather than left as a loose end here.

Raised 2026-09-01.

**The whole risk here is doing too much of it.** Haptics on every tap is noise, and noise is what
makes people turn the setting off system-wide — at which point the app loses the few buzzes that
would have been useful. So the list is short on purpose:

- **Drag start and drop** — picking a row up and putting it down. The gesture has no other
  confirmation that it began.
- **Crossing a reorder boundary** — one tick per position the row passes. This is the one that
  actually helps: it tells you a move happened without looking, which is exactly when you cannot
  look, because your thumb is over the row.
- **Long-press that starts a selection** (`A4`) — a long press with no feedback feels like a press
  that failed.
- **Swipe past the snackbar's dismiss threshold** — it already fades; a tick says "let go now".

~~And deliberately **not**: play, pause, next, previous, shuffle, repeat, or anything with a visible
result. The screen already answered.~~

**Reversed on 2026-09-10 — see A31.** Haptics were asked for on the transport, on
moving between views and folders, and on holding the seek bar: exactly the list this paragraph
ruled out. The phone is held by somebody; this was written from the armchair. The reasoning
behind the rule survives as *weight* rather than as a ban, which A31 sets out.

**Implementation notes.** Compose's `LocalHapticFeedback` offers only `LongPress` and
`TextHandleMove`, which is thin. The richer constants live on `View.performHapticFeedback` —
`GESTURE_START` and `GESTURE_END` (API 30+), `SEGMENT_TICK` (API 34), `CONFIRM` and `REJECT`. With
`minSdk 29` that means a small helper that degrades: the good constant where it exists, `LongPress`
where it does not, nothing where the device has no vibrator.

Android already honours the user's system haptics setting, so there is no need for our own — and
adding one would be inventing a preference the platform already owns.

## A10. ~~Sharing — the file, and a link to it~~ — DONE 2026-09-02

Raised 2026-09-02. Two actions, and they are **not** the same feature:

- **Share the file.** An `ACTION_SEND` with the bytes, so a tune can go into a chat. These are
  kilobytes, which is what makes this pleasant — the whole point of the formats.
- **Share a link** to a track in an online catalogue, so the other person gets the tune without
  receiving a file at all.

**What has to be settled before either is built:**

- **A local file has no shareable URI.** What the library holds is a SAF document URI, and a
  permission grant that belongs to this app. Handing it to another app hands over nothing it can
  read. The file has to be copied out through a `FileProvider` — which means a copy, a cache
  directory for it, and an eviction rule for that directory (`docs/OPEN_QUESTIONS.md` Q5 again).
- **Not every catalogue track has a link.** Modland tracks do: the id is an `https://` URL and can
  be shared as it stands. **ASMA tracks do not** — their id is `asma://<entry>`, an address inside a
  20 MB archive on this device (`docs/ARCHITECTURE.md` §13), and it means nothing anywhere else. So
  the action is either hidden for archive catalogues, or it shares the archive's own page and names
  the file, which is a worse thing that at least exists. Decide, do not let it fail quietly.
- **What a shared link should be.** A raw `modland.com` URL is honest and ugly, and it points at a
  file rather than at anything a person can look at. Worth a conversation before choosing.

Sharing the whole **playlist** is a third thing again, and nobody has asked for it — the formats
would be a list of URLs plus local files that cannot travel. Not in scope here.

**Built**, and both questions above were decided rather than dodged:

- The file is copied into a `FileProvider` directory in the cache and shared from there, because
  neither place our files live can be handed to another app — a SAF grant cannot be passed on, and
  app-private storage cannot be read from outside. Copies are swept an hour after they are made,
  not immediately, because the receiving app reads the file after the chooser closes.
- The link asks the catalogue what it publishes. Modland serves every file over HTTP, so the link
  is the file. ASMA publishes one archive and no per-file address, so the share names the
  collection and the path inside it — a real thing to act on, rather than an `asma://` reference
  that means nothing on anyone else's phone. `Catalogue.webUrlFor` returning null is what says so.
- The MIME type is `application/octet-stream`, not `audio/*`: no chat app can play a `.mod`, and
  claiming an audio type invites the receiving end to try and fail.

**Decided 2026-09-02**: the link points at **the file** — exactly what is being played
— and what to do with it is the recipient's business. So Modland's track URL stays as it is, and
the reason ASMA cannot have one (it publishes an archive, not a file tree) is now the whole reason
its share names the collection and the path instead.

**Corrected 2026-09-11: ASMA does have one.** Every file is served at its zip entry's own path under
`https://asma.atari.org/`, with CORS open — measured, not assumed, when the web player took ASMA on
(`docs/PLAN_WEB_LIBRARY.md` S7). So ASMA's link is now the file, exactly like Modland's, and the
"collection and path" fallback is left for a catalogue that really has no per-file address.

## A11. ~~Random should read ahead, the way the playlist does~~ — DONE 2026-09-02

Raised 2026-09-02: waiting for each random track to download is the wait R9 exists to
remove, and Random is the one place still paying it in full.

**Why the existing read-ahead does not cover it.** `prefetchUpcoming()` reads the next track while
the current one plays, and it works by asking the queue what comes next. Random has no next:
`playRandom()` calls `catalogues.random()` at the moment you press it, so the track that comes next
does not exist until it is already needed. Nothing can be read ahead because nothing has been
decided.

**So the change is to decide sooner, not to cache harder.** Pick two or three ahead, put them in
`randomHistory` past `randomCursor`, and let the existing read-ahead do its job. Pressing next then
takes the one already chosen and already fetched, and picks a new one at the far end to replace it.
The history machinery is already the right shape for this: `randomCursor` sitting behind
`randomHistory.lastIndex` is exactly what "there is a queue ahead" means, and `randomNext()` already
walks into it rather than picking fresh.

**Watch for:**

- **Read-ahead is single-slot today.** `prefetched` holds one track's bytes and `MAX_PREFETCH_BYTES`
  bounds it. Two or three ahead means several, which is a small cache with an eviction rule, not a
  variable.
- **Fetching things nobody hears.** Three ahead on a metered connection is three downloads for one
  listen if the user stops. These files are kilobytes, so the cost is small — but it is not nothing,
  and it should be a considered number rather than whatever felt right.
- **Going back must not re-pick.** `randomPrevious()` walks the history; nothing about a queue ahead
  may make the past re-roll.

**Built**, three ahead. One thing had to be decided that the write-up above did not foresee: with a
queue ahead, **the dice and next stop meaning the same thing.** Next means forward, and walks into
the queue. The dice means "surprise me", so it drops the picks that were read ahead and never heard
and re-rolls — but keeps everything actually played, which the old code did not: it truncated at the
cursor and so discarded real history along with the guesses. Telling the two apart is what
`randomPlayed` is for. **Confirmed on a device 2026-09-02** — the dice re-rolls, next walks
forward.

## A12. ~~Random should keep going when a track ends~~ — DONE 2026-09-02

Raised 2026-09-02: Random stops at the end of each track and has to be pressed again.

Today that is deliberate and the comment in `handleTrackEnded()` says why — *"Rolling on into the
playlist would be answering a question the user did not ask by pressing Random."* **That reasoning
survives.** What is wanted is not rolling into the playlist; it is rolling on to the next
random pick. The transient track ending should call `randomNext()`, and the comment should be
narrowed to say what it is really guarding against rather than deleted.

**One thing to get right:** a transient track is also how a **search result** is played, and those
already have their own queue (`resultsQueue`) handled earlier in the same function. The change must
reach the Random path only — a search result playing on into a random tune would be the same mistake
in the other direction.

Pairs naturally with **A11**: continuous play is where reading ahead stops being a nicety, because
the gap between tracks becomes the only thing the listener notices.

**Built.** Repeat-one is honoured — it says "keep playing this" on the dock while Random runs, and
skipping under it would be the app contradicting its own button. Not confirmed on a device.

## A13. ~~Application settings~~ — DONE 2026-09-04

Raised 2026-09-02. There is no settings screen at all today, and several decisions
that are currently hard-coded or implicit are exactly the kind a person wants to change once and
never think about again.

**What already exists and would move there** — this is the argument for the screen, and it is worth
gathering before designing it, because a settings screen designed before its contents is a screen of
somebody's guesses:

- ~~**Storage.**~~ **Done 2026-09-04**, as round 6 item 3, and deliberately without waiting for
  this screen to be designed. Every stored thing now has a delete beside it — the fetched cache,
  each downloaded archive, each catalogue index, the HVSC song lengths — in a `StorageSection` that
  lives on the Online catalogues screen because Browse already answers "what does this app have of
  mine" and needs no new navigation to do it. **That placement is the placeholder, not the
  feature**: when this screen is designed the section moves in one piece. See
  `docs/ARCHITECTURE.md` §19.
- ~~**Language.**~~ **Done 2026-09-04:** System, Polish and English are selected inside the app and
  work across its whole supported API range, rather than handing Android 13+ off to a system page.
- **Behaviour that is currently a decision we made for the user.** Whether Random keeps going
  (A12), how far it reads ahead (A11), whether the playlist follows the playing track by default
  (B14 is off by default and forgets when you leave the screen).
- **Per-format playback settings** — `docs/WISHLIST.md` B11 is a whole sub-tree of this, and the
  reason not to design the screen around a flat list of switches.

**What to decide before building:** whether this is one screen or a section per topic, and where it
is reached from. The app has no overflow menu at the top level today, and adding one to reach a
single screen is a navigation change — `docs/OPEN_QUESTIONS.md` Q1 is still open and touches the
same surface.

## A14. Getting ready for the Play Store

Raised 2026-09-02. Nothing here is written down anywhere yet; `docs/BUILD.md` covers
building and signing an APK and explicitly stops short of a bundle.

**What is already true:** the release build goes through R8, is signed from `PRZUNK_UPLOAD_*`, and
`build-release.sh` prints the certificate subject so an accidentally debug-signed artifact is
visible. `targetSdk` is 36 and `minSdk` 29, both current enough.

**What is missing, roughly in the order it will bite:**

- ~~**An App Bundle.**~~ `./scripts/build-bundle.sh`, written 2026-09-02. The key itself is still
  the owner's to create; the script asks for it and refuses to produce a debug-signed bundle.
- ~~**The launcher icon** (A9)~~ — done 2026-09-03: an adaptive icon with a monochrome layer for
  themed icons, at every density.
- ~~**A privacy policy and a data-safety declaration.**~~ **Written 2026-09-04** as round 6 item 5:
  `store/privacy-policy.md` is the policy — one copy, bilingual — and `docs/PLAY_STORE.md`
  answers the data-safety form row by row. Both claims were checked against the source rather than
  assumed — no analytics SDK, no identifier read anywhere, five network hosts and all of them
  archives. `ACCESS_NETWORK_STATE` was found declared and unused, and removed.
- ~~**The replay binaries.**~~ **Settled 2026-09-04**, which was the last thing genuinely blocking a
  listing: the APK ships only sc68's own replay and downloads the rest at the user's request. The
  letters to sc68 and UADE are in `docs/letters/`; the UADE one has been sent. Neither reply gates
  anything.
- ~~**The GPL and the store.**~~ **Written down 2026-09-04** in `docs/PLAY_STORE.md`, including why
  the Apple App Store problem people cite does not apply to Play. The **replay binaries** question
  (`docs/LICENSES.md`) is untouched by that and still the owner's to settle *before* publishing —
  it is now measured for both sc68 and UADE rather than argued.
- **Content rating, listing text, screenshots, a feature graphic.** Mechanical, but none exists.
- ~~**`versionCode` discipline.**~~ — done 2026-09-03, after it blocked an upload: it is the commit
  count now and nobody has to remember it.

- ~~**`docs/letters/` before the repository goes public.**~~ **Done.** The directory is gitignored
  as of 2026-09-05 and was filtered out of the history and force-pushed; no commit on any branch,
  local or on the remote, has ever named a file under it — checked, not assumed. The separate half
  — what the letters *established*, reported in `LICENSES.md` and `PLAN_FORMATS.md` — was settled in
  two passes: their sentences on 2026-09-05, their attribution and the personal address on
  2026-09-15. `docs/PLAY_STORE.md` has the rule that came out of it and what the history still
  holds.

**Not started, and not to be started alone**: publishing is the owner's account, key and name on
the listing.

## A15. ~~The Random icon does not look like a die~~ — DONE 2026-09-02

Reported as looking less like a die than like a spreadsheet logo, which was
exact. It was Material's `casino` glyph, whose pips wind the same way as its outline, so under
non-zero winding they filled in and left a solid rounded square. Redrawn as an outline plus five
pips, rendered even-odd. `PlayerIcons.icon()` gained a `hollow` flag for the next icon with the
same symptom.

## A16. ~~No way back to the playlist from a browse jump~~ — DONE 2026-09-02

Raised 2026-09-02, after using **B2**: "more from this author" opens the browser at
the author's folder, which is right; back then walks up the folder levels, which is also right; but
there is **no one-tap way back to the playlist** from wherever you have got to.

**Why it is a real gap and not a missing button.** The rule the app follows today is "back goes up
one level, and from the top it leaves Browse". That is correct and it is also slow: land three
levels deep from a jump and getting out is four taps. The jump made it easy to arrive somewhere
deep, and nothing made it easy to leave.

**Options, none chosen — this one is a decision, not a design:**

1. **A close affordance on Browse itself**, separate from back. An X in the top bar next to the
   back arrow. Cheapest, and it makes the two gestures visibly different things.
2. **Back from a jump returns whence it came**, rather than walking up. The jump becomes one step
   in the history rather than a teleport, which is what a browser tab does.
3. **The player dock is already on every screen** — tapping the identity row could mean "take me
   back to the list", which is close to what B13 already does inside the playlist.

**Both 1 and 2 were built**, because they turned out to answer different halves:

- The header carries a labelled **Playlist** action — icon and word — that
  leaves Browse from any depth in one press. That is the way *out*, as distinct from back.
- **Back after a jump returns to the playlist** rather than walking up. Reported as a defect, and
  rightly: a jump puts you three levels deep without your passing through any of
  them, so climbing out of a hierarchy you never climbed into is not "back". `arrivedByJump` marks
  it and is cleared by any ordinary navigation, so browsing down from the landing place behaves
  normally again.

**Related and unsettled**: `docs/OPEN_QUESTIONS.md` Q1, the navigation model, touches exactly this.

## A17. ~~The actions in a track's details need sorting out~~ — DONE 2026-09-03

Raised 2026-09-02 about two places at once:

- **Now Playing**, where every action is its own full-width row — *show in playlist*, *more
  from this author*, *share the file*, *share a link* — and the list grows every time one is added.
  Four now, and A10 and B2 added two of them in a day.
- **The history list**, where the actions were called weak — the row menu offers
  what a playlist row offers, and some of it does not belong on something that is not in a playlist.

Not a layout tweak: the question is what a track's actions *are*, once there are more than fit
comfortably.

**Agreed 2026-09-03:** an **icon with its label above or below it**, and all of them
**in one row**. The same shape as the *Playlist* action already in the Browse header, so the two
will not read as different kinds of control.

**And one action goes.** Since *add to another playlist* exists, there is no separate *add to
the current one*: one item, **"Add to playlist…"**, opening the picker. The picker should put the
**current playlist first**, so the common case is still two unthinking taps. The dock's **+** keeps
adding to the current playlist without asking — that is the reflex, and the menu is the decision.
The bulk button stays as it is, because it already names its target on its face.

**Built.** Now Playing's actions are one wrapping row of icons with their names underneath,
through a shared `LabelledAction` — the shape already asked for twice, now in one
place rather than copied. **Corrected after seeing it:** they are drawn in the accent colour,
because the full-width text buttons they replaced were accented by default and losing that made
them read as labels rather than controls; and every cell is one fixed width, because sized to their
own text they came out ragged and a row of different-sized things does not read as a set of equals. The two add actions became one **"Add to playlist…"** opening the picker,
with the current playlist sorted to the top.

**And it took some code out.** `addToPlaylistAndSay` and the `onAddStayingHere` wiring existed to
give the *current-playlist* menu item feedback while staying in Browse (`docs/STATUS.md` C7). That
menu item is gone, and the picker's own path already reports back **and names the target**, which is
strictly better. Removed rather than kept for a caller that no longer exists.

## A18. ~~Should playing something reorder the list it came from?~~ — DECIDED 2026-09-03: no

Raised 2026-09-02: what should happen to the order of the displayed list after one of
its entries is played.

Worth pinning down **which** list before designing anything, because the answer is probably not the
same for each: the playlist (where order is the user's and must not move by itself), history (where
order is recency and playing something arguably *should* move it to the top — it already does in
the database, the view just does not refresh under you), and a browse or search result (where order
is the archive's).

The reason it needed a conversation rather than a decision: a list that reorders under a finger is
the single most disorienting thing a list can do, and the ordinal numbers were defended on exactly
that ground: knowing where you are in three hundred rows matters.

**Decided: no.** *"I don't think I want to reorder a list just because I clicked something again.
The first play is what matters."* So history keeps the order it has while you are looking at it, and
a replay does not move a row. Current behaviour is now the chosen behaviour rather than an accident.

**What history is actually for is the date and time**, which is `docs/WISHLIST.md` B15 and
is now the live half of this conversation.

## A19. Should a jump also fetch the whole author's folder? — DEFERRED 2026-09-03

Raised 2026-09-02, about **B2**: having jumped to an author's folder, pre-fetch what is
in it so playing any of it is instant.

**Deferred**, for the reason the entry already gave: *"an author is effectively the
lowest-level folder and can have hundreds of tracks."* Not to be raised again unprompted.

**Why it is not simply "yes".** An author's folder in Modland can hold a handful of tunes or a
couple of hundred, and the app cannot tell before it fetches which it is. The read-ahead built for
Random (A11) is bounded at three because three is the cost of a feature nobody asked to pay for on
a metered connection; a folder is unbounded.

**What is worth knowing before deciding:**

- These files are kilobytes. A folder of thirty is perhaps a megabyte, which is nothing; a folder of
  three hundred is not.
- The cache has **no eviction budget at all** (`docs/OPEN_QUESTIONS.md` Q5). Fetching folders makes
  that question urgent rather than theoretical.
- A middle position exists and may be the right one: fetch the first *n* of the folder, in the order
  shown, on the same machinery A11 already uses.

## A20. ~~Browse forgets where you were when you go back~~ — DONE 2026-09-03

Raised 2026-09-02, with the use case that shows it:

> I go into Browse and look for an author's folder — say Przunk. I go in, listen to something, press
> back — and it puts me at the **start** of the folder list. I expect to be back where I was: at the
> folder I came out of.

**A defect in effect, and listed here because it is the same piece of work as A3 and A16 rather than
a separate fix.** It is not a wrong-behaviour bug so much as a missing one: nothing in Browse
remembers a position at all.

**What the code says** (read, not measured): `BrowseScreen` never creates a `LazyListState`. Every
level is a `LazyColumn` with no explicit state, and the levels are the *same* call site recomposed
with different data — catalogue list, then formats, then authors, then tracks. So the scroll state
is neither saved per level nor reset between them: it persists across a level change by accident and
is then clamped to whatever the new, usually shorter, list can hold. Going into a folder from row
900 of the authors and coming back out lands near the top, which is exactly what happens.

**What it should do**, and the second half is the part that is easy to leave out:

1. Each level keeps its own scroll position, restored on the way back up.
2. Coming back should put the row you **came from** on screen — not merely the offset you happened
   to have. Those differ whenever the list changed underneath, and the second is what "back where I
   was" means to a person.

**Built.** `BrowseScroll` keeps one scroll state per level for the life of a Browse session, and
records the row you descended by so that returning finds it again. Identity first and **no index
fallback**: an index is only "where I was" while the list is unchanged, and the case this exists for
is exactly the one where it changed. When the row has gone, the level's own saved offset is already
close enough, and jumping somewhere arbitrary because a number still parses would be worse. The
animate-when-near / jump-when-far helper is shared with B13 and B14 rather than reimplemented.

**Shares its machinery with:**

- **A3** (getting up and down a long list) — both need Browse to have a real, addressable list state
  rather than an anonymous one.
- **B13/B14**, which already solved "put this row on screen, animate when near and jump when far"
  for the playlist. That helper should be reused rather than written twice.
- **A16** (no way back to the playlist), which is about the same back button doing too many jobs.

## A21. ~~The playlist picker should say how big each playlist is~~ — DONE 2026-09-03

Raised 2026-09-03. Choosing where to add a track shows a list of names and nothing
else, so there is no way to tell a playlist you filled from one you made and forgot. The count is
already in the database — `LibraryStore.playlists()` reads the rows the picker shows.

**Built**, counted in the same query with a `LEFT JOIN` rather than by reading every playlist's
tracks: the picker shows all of them at once, and one statement is one statement.

**In both places, on the second try.** The first attempt changed only the *add-to* dialogue and
missed the **switcher** in the top bar, which is where anybody looks first. That row now shows
the count where a dot used to say "this is the current one" — the dot spent a slot saying something
the row can say by being highlighted, and left the question a list of bare names cannot answer.
Highlighting the whole row is the same convention the playlist already uses for the playing track,
so "which one am I in" is one idea rather than two.

## A22. ~~Online catalogue indexes go stale and nothing says so~~ — DONE 2026-09-03

Raised 2026-09-03, after a search for C64 music found none: **the Modland index predated
libsidplayfp**, so it contains no `.sid` entries at all. The index filters by filename *at index
time*, and the filter only admits formats a backend can play — so an index built before a backend
existed is permanently missing that backend's formats, and looks simply empty rather than stale.

**The asymmetry is the point.** Round 5 built exactly this mechanism for the *local* library: every
row records which decoder set produced it, and a folder scanned by a different one says so and
offers a rescan (`docs/ARCHITECTURE.md` §18). Catalogue indexes still rely on A7's note asking a
human to remember. The gap was hit the first time it mattered.

**The straightforward fix** is the same one: a `backends` column on `catalogues`, written at index
time and compared on open. The catalogue row already shows a track count and an indexed-at date;
"indexed with older decoders — re-index to see C64 music" belongs beside them.

**Built**, and the decision that came with it: **the row says so and the user presses the button
that was already there.** Not automatic. A Modland re-index is a 5.75 MB download and half a million
rows, and starting that because somebody opened a screen would be indefensible; the catalogue row
already carries a re-index button, so the notice only has to say *why* to press it. That also leaves
the choice open — making it automatic later is adding a trigger, not redoing the mechanism.

**And that question was closed on 2026-09-03, with a better reason.** Automatic
re-indexing is not deferred for taste: *"we can't do anything better until we have an online worker
that indexes this for us automatically."* Re-indexing on the device means every user downloading
5.75 MB and rebuilding half a million rows to learn what one server could have worked out once. So
the notice is the right answer **for as long as the index is built on the phone**, and the real
alternative is a different piece of infrastructure rather than a different trigger.

**An index with no recorded decoder set counts as stale**, which is not a detail: every index that
exists today predates the column, including the one that caused this. Treating "unknown" as current
would have left the reader exactly where they started.

**Also fixed here** (`docs/STATUS.md` C9): a catalogue track that no decoder can open now names the
format the archive files it under — *"SidMon 1 is a format Protracktor cannot play yet"* rather than
*"no backend recognised it"*. The archive knew; the app was not asking.

## A23. ~~The Browse button should be an icon with a label~~ — DONE 2026-09-03

Raised 2026-09-03: it is a bare text button, while the way *out* of Browse — added the
day before for **A16** — is an icon with its name underneath. Two controls that do opposite things
should not be told apart by one being drawn and the other written.

Same treatment as A17's action row, and small enough to go with it.

## A24. A baseline profile — NOT NEEDED FOR NOW, 2026-09-03

Raised 2026-09-03 out of `docs/STATUS.md` C10. The list is janky until it has been scrolled ten or
fifteen times, then it is fine — and adding **more** tracks made it faster. That is a warm-up curve,
not a cost per row or per second: ART interprets until the JIT compiles the paths, and Compose
composes each type once before it is cheap.

A baseline profile ships those traces AOT-compiled, so the first run is already warm. It is the
standard answer to exactly this shape of complaint.

**Not started, and it needs a device.** A real profile is generated by running the app under a
macrobenchmark; this workshop has no emulator, so it is a run on a phone rather than one that can be
done here. A hand-written profile is possible and worth less — it guesses at what a real one
measures.

**Ruled out, and it was the whole thing.** Everything had been measured on a **debug** build. The
release build stutters not at all — reported as "zero stuttering now" — so there is
nothing here to fix and this stays as a note rather than as work.

**Worth having if the release build is ever janky on a cold start**, which is what a baseline profile
is genuinely for. Not before.

## A30. ~~The button to the browser should say "WEB"~~ — DONE 2026-09-10

The action is called **WEB**, the same word in both languages.

Done, and the same word in both languages: it is the name of the other half of this project, not a
sentence about where something is going.

**The prose around it still says "browser", and that is deliberate.** *Scan a different browser*,
*Forget the paired browser*, and the sentence under the web player address all describe the machine
at the other end, which is a browser and is the right word for it. Only the button is a name.

## A29. ~~Play MP3 too~~ — DONE 2026-09-10

Raised as `docs/WISHLIST.md` B4, promoted to work on 2026-09-09 and built on 2026-09-10, with one
rule attached: **an MP3 sent by code carries its name and nothing playable.**

**minimp3**, CC0-1.0 — two headers, one object, and the smallest vendored decoder here. `mp3dec_ex`
rather than the plain frame loop, for the two things a player needs and a frame loop cannot give: a
length for a variable-bitrate file, and an index to seek with.

### Measured 2026-09-10, against the conformance suite minimp3 ships

83 MPEG conformance bitstreams through the engine:

| | |
|---|---|
| played | **70** |
| silent | 10 — every one an `l3-nonstandard-*`: a stream that is an ID3 tag and no audio, a corrupted VBR tag, a truncated side info |
| refused | 3 — a tag-only file, a file too small to hold a frame, and a deliberate over-allocation |

**So everything that contains audio played, and everything that did not is a stream designed not
to.** Layer 1, Layer 2, Layer 3, MPEG-2 LSF and free-format among them.

**And it decodes correctly, not merely audibly.** Eleven Layer 3 vectors compared sample by sample
against the reference PCM shipped beside them: **worst RMS 0.04 out of 32,768, worst single sample
off by one.** That is rounding, not decoding.

*The first run of that comparison showed two vectors "wrong" with an RMS of 6,000. The comparison was
wrong, not the decoder — it read a stereo reference as if it were mono. The same lesson as the
worklet stub in `docs/review-round-8.md` R1, twice in one day: a measurement that reads the wrong
thing is evidence of the wrong conclusion.*

### The two decisions, answered

**`.mp3` is not in `SupportedFormats.extensions`, and that was the whole question.** That list is
what a catalogue index is filtered through *and* what `fingerprint` is computed from, so a name
added there marks every stored index stale and costs everyone a 40 MB re-download. No archive here
holds an MP3. It lives in `localOnlyExtensions` instead, and a test asserts that it is in neither of
the two sets the fingerprint is made of — if that ever fails, somebody owes every device a download.

**A folder scan needed nothing at all.** It lists every file and lets a decoder answer
(`MediaScanner.listFiles`), so an MP3 in a scanned folder simply plays. The name only had to be
judged in one place: deciding whether a failure means "this app cannot play this format" or "this
file is broken", which is what `looksPlayable` now answers and `inCatalogueIndex` does not.

### And it never travels to the browser

The rule is arithmetic before it is a preference: this whole handoff rests
on a tracker module being kilobytes — Modland's median is 20 KB and the budget for a *whole queue*
is eight megabytes — and one four-minute MP3 is more than that budget by itself. So an MP3 goes as a
name in its own place, in the link and in the paired message alike, and the page draws it greyed and
unplayable exactly as A28 draws a file that stayed on the phone.

A rule rather than a size check, so a short MP3 cannot surprise anybody by behaving differently.

**Not a tag reader.** minimp3 decodes and does not read ID3, and inventing one for a single format
would be a second metadata path to keep in step with `SongDbMetadata`. An MP3 is named by its
filename, which is what this app already does for every format that says nothing about itself.
## A28. ~~The web player carries the local files it cannot play~~ — DONE 2026-09-10

An outside listener opened a shared link and the two lists did not agree.

A local file's id is a storage grant valid on one phone, so its music was never going to travel. Its
**place in the list** was, and that was the defect: track seven here was track five there.

**Built to that shape.** The file travels as a name under a `phone:` scheme in the link — and as
`"local":true` in the paired message, which covers the second case nobody had named: a file that
*could* have travelled as bytes and did not fit `WebRemote.LOCAL_BYTES_BUDGET` used to arrive as a
row that failed on the first touch. The page draws both greyed, in their own positions, unclickable,
and `next`, `previous` and shuffle step over them rather than stopping on a row that can never play.

**Grey, not red**: `.failed` means a decoder refused the music, and these never had any to refuse.

**What the measurement changed.** Deflate makes the names nearly free — a hundred tracks of ordinary
filenames pack well under the two-thousand-character limit whether their ghosts travel or not. So
the guard that drops them fires only for a long queue of *unlike* names, and the real tracks are
never what goes. `left` now means "the link was too long even for their names", which is a much
rarer and much more honest thing for that number to say.
## A27. ~~The web player has no actions~~ — DONE 2026-09-10

**The panel** has the phone's row of labelled squares, in the same place: *Show in playlist*, *Save
the file*, *Copy a link*. `Add to playlist…` is deliberately absent — the page has no playlists, and
`docs/PLAN_HANDOFF.md` §5a is why.

**Every row** has the phone's three dots, and the same three actions. They are **disabled together**
for a row that stayed on the phone: no file to save, no address to copy, nothing to read. A menu
offering three things and doing none of them is worse than one with three greyed.

**Information was the expensive one, as predicted.** It opens a **second** decoder handle in the
worklet, describes the file and closes it, leaving the one making sound untouched. The cost is
opening a decoder on the audio thread: the median module here is 20 KB and that is nothing, but a
very large file is what to blame if this ever glitches, and that is written where the code is.

*Copy a link* copies the **track's own address**, not the page's. A link to the page carries the
whole queue and the phone already sends that; what is useful from a row is the one file, at an
address anybody can open.
## A26. Two rough edges on the JNI boundary

*Found 2026-09-08, while answering an experienced C++ engineer's objection to JNI (`docs/OPEN_QUESTIONS.md`
Q9). Neither misbehaves, so neither is a defect — they are the two places where the criticism lands
on our code specifically rather than on the API in general.*

**A full copy of every file across the boundary.** `engine.cpp:1745` reads the module with
`GetByteArrayRegion` into a `std::vector`. At Modland's median of 20 KB that is nothing; the largest
file in the archive is **71.6 MB**, and there the copy is real and avoidable — a direct `ByteBuffer`
and `GetDirectBufferAddress` would hand the decoder the bytes where they already are. Worth doing
when something makes large files common; not before, because the change moves ownership of the
buffer's lifetime to the Kotlin side and that is a new thing to get wrong.

**Errors come back through a one-element array.** `nativeOpen` takes a `jobjectArray errorOut` and
writes the failure message into slot zero. It is the classic JNI idiom for "return two things" and it
is as ugly as it sounds. The alternatives are a small result object or throwing across the boundary,
and both are more code than this earns today — recorded so that the next person to touch the
signature knows it was seen rather than missed.

## A25. ~~Import and export a playlist~~ — DONE 2026-09-03

Raised 2026-09-03.

**The hard part is not the file format, it is what a playlist refers to.** Rows point at three
different kinds of thing and only some of them travel:

| what a row is | portable? |
| --- | --- |
| a catalogue track — `https://modland.com/...` or `asma://...` | **yes**, it means the same anywhere |
| a local file — a storage-access-framework document URI | **no**. The URI is issued by a provider on *this* device, and the framework hands out a different one for the same file reached a different way |

So an export that simply writes the ids produces a file that works perfectly on the phone it came
from and not at all anywhere else — which is the one case where somebody would want it.

**What might survive the trip**, and what to decide between:

- **Path and filename plus size**, and let import match against the local index on the other side.
  `TrackRef.sameFileAs` already settles identity by name and size for exactly this reason, and
  `library_index` is the thing that could answer the question. It would match a library that holds
  the same tunes in a different place, which is the realistic case.
- **A hash of the file's bytes.** Exact, survives renaming, and costs reading every file on both
  sides — which the scan already does once, so it could be stored.
- **Both**, with the hash as the authority and the name as the fallback.

**Format:** M3U is the obvious choice — every player reads it, and `#EXTINF` carries a title. Its
weakness is the same one above: it holds paths, and we hold URIs. An `#EXT` comment of our own could
carry what M3U cannot, in a way other players ignore.

**Worth deciding early:** whether export is *for another copy of this app* or *for other players*.
They pull in opposite directions — the first wants our identifiers, the second wants plain paths —
and trying to serve both is how a format ends up serving neither.

**Built 2026-09-03, and that question is answered by putting each thing where it belongs rather than
choosing between them.** The file is M3U: the location line is what another player reads — a real
URL for a catalogue track, a path for a local file — and our identifiers go in `#PROTRACKTOR:`
comments, which every other program ignores by the format's own rules.

**Import gives each line two chances.** Its recorded id, which is exact and makes a backup restore
perfectly on the device that wrote it; and failing that a match on filename **and size** against the
scanned library, which is how a list written on another phone finds the same tunes here. Size is not
decoration — `elysium.mod` is a filename several hundred people have used.

**Import always makes a new playlist**, never appends to the open one: an import that edited whatever
happened to be in front of you would be a change nobody asked for, and undoing it means working out
which rows were new. It says how many of the file's lines it could not place rather than quietly
arriving shorter.

## A5. Formats we do not play yet — planned in `docs/PLAN_FORMATS.md`


Agreed 2026-09-01: this comes back as its own goal. Recording what is known now so it does
not have to be rediscovered.

- **`.sap`** — Atari 8-bit. Needs ASAP (GPL-2.0-or-later, by Piotr Fusik, has an Android port).
- **`.sndh`** — should already work; see the defect in `docs/STATUS.md`. Fix before adding anything.
- **SID, NSF, SPC, GBS, VGM, AY** — `libsidplayfp` and game-music-emu, in that order of value.
- **AHX and HVL** — 1,433 Modland files, removed from `SupportedFormats` on 2026-09-04 because
  nothing here plays them. HivelyTracker is a small standalone library and would be the cheapest
  format win available (`docs/PLAN_FORMATS.md` §5).
- **Amiga custom (TFMX, Hippel, Future Composer, …)** — UADE, last, and its bundled replay binaries
  need a licence decision of their own. **Measured 2026-09-04** and the estimate in this item was
  wrong: the corrected current top-25 sample reaches successful format directories holding 5,799
  Modland files, not tens of thousands. The first 184/300 result was also wrong because the probe
  enabled UADE's strict content-only mode; the same historical corpus is 196/300, with Hippel COSO
  at 11 full plus 1 silent first buffer instead of 0/12. Numbers, the
  process-model problem and the licence trade are in `docs/PLAN_FORMATS.md` §4; nothing is
  integrated.

Each new backend also means **re-indexing the catalogues**: the index only keeps entries whose
filename a backend might handle.

## A6. ~~Probe content instead of trusting extensions~~ — DONE 2026-09-03


`SupportedFormats` filters by extension and says in its own documentation that this is provisional.
Extensions here are unreliable, absent, or shared between unrelated formats. Probing means reading
every candidate, which is why it belongs with the persistent index from item 1 rather than with a
foreground scan.

## A7. More online catalogues — planned in `docs/PLAN_CATALOGUES.md`

**`docs/WISHLIST.md` B10 is this item.** It was raised as a wish a day before this was agreed and
nobody struck it; the duplicate was spotted on 2026-09-02. This is the live one.

Modland and ASMA are wired up and verified end to end. On 2026-09-02, **The Mod Archive** was
integrated as the third online catalogue via live search (`isOnlineOnly`), providing direct access to
tens of thousands of tracker modules (MOD, XM, S3M, IT) playable through libopenmpt.

Each needs two things, and the second is usually the blocker:

- **Its index parser or search integration.** `Catalogue` is a sealed class; adding one is writing
  `parseIndex` or live search and `urlFor` against whatever that archive publishes. None of the others
  is a single tab-separated file the way Modland's is.
- **A backend that can play what it holds.** ASMA is Atari 8-bit SAP and needs ASAP; AMP is heavy on
  Amiga custom formats and needs UADE. Indexing an archive we cannot play produces a browsable list
  of tracks that fail to open.

**Adding one now also means writing `pathFrom`**, the inverse of `urlFor` — it is what "more from
this author" uses to get from a track back to where it came from (`docs/WISHLIST.md` B2). It is
abstract, so the compiler asks for it.

**Also**: re-index after adding any backend. The index deliberately keeps only entries whose
filename a backend might handle, so formats added later are simply absent until a re-index.

---

# Done

Kept rather than deleted: what was broken and why is worth more than a tidy list.

## ~~Persistence — the state survives a restart~~ — DONE 2026-09-01


The list emptied on a restart, and settings — repeat mode, the last thing played — were not kept.

This is **R2**, the requirement the whole project started from, and everything below is easier once
it exists. Do it first.

**What has to survive:** the track list, shuffle on/off, repeat mode, and which track was last
playing. **Not** the playback position — that is `docs/OPEN_QUESTIONS.md` Q6, still open, and
formats that cannot seek make it expensive.

**Where:** app-private storage — the database, which is where playlists and the catalogue indexes
already live (`docs/ARCHITECTURE.md` §9). Written before that was built, this said "Room"; there is
no Room here and a plain file plus `SharedPreferences` is no longer the cheaper first cut, because
the schema and its migrations are already in place and tested.

**Watch for:**
- SAF grants must be persisted too, or the saved URIs will be unreadable on the next launch.
  `MediaScanner.persistPermission` already takes them; confirm they actually survive a reboot rather
  than assuming.
- A track whose file has since disappeared must be visible and marked unavailable, not silently
  dropped (AGENTS.md §7).
- Saving on every state change will write on every poll tick. Debounce, or save on the changes that
  matter.

**Done when:** the app is force-stopped and reopened and shows the same list, the same scroll
position, the same shuffle and repeat, and the same track loaded in the dock.

## ~~Browse that adds a selection, not a whole folder~~ — DONE 2026-09-01


Browse should add one tune, or several ticked ones, to the playlist.

Today Browse is two buttons and a folder scan dumps everything it finds. It should show what was
found and let the user choose.

- A browsable list of the scanned folder with checkboxes, a select-all, and an explicit "add to
  playlist".
- Remembers granted folders between sessions so browsing does not begin with a file picker every
  time.
- This is also where **several playlists (R6)** becomes possible: adding needs a destination, and
  once there is a destination there is more than one. The top bar's playlist name is plain text
  today precisely because a switcher with nothing to switch to is a control that does nothing.

**Done when:** the user can open Browse, tick three files out of a folder of two hundred, add them
to a named playlist, and find them there.

## ~~Undo and confirmation on removal~~ — DONE 2026-09-01


> "usuwanie z potwierdzeniem oraz opcja undo w snackbarze"

Read as two different things, because one destructive action is not like the other:

- **Removing one track → undo, no dialog.** `Message` already carries an unused `actionLabel` for
  exactly this. Restore the track at the index it came from.
- **Clearing the whole playlist → a confirmation dialog.** Undoing many rows from a snackbar that
  vanishes in four seconds is not a real escape route.

A confirmation dialog *and* an undo for a single row would mean two interactions to delete one
line. If both are wanted, say so and it is done — this reading is an interpretation, not a decision
anybody stated.

**Built that way on 2026-09-01**: undo on a track, a dialog on deleting a playlist.

## ~~A snackbar that can be swiped away~~ — DONE 2026-09-01


A snackbar that can be swiped away.

Material 3's `Snackbar` has no swipe dismissal. Wrap it with a horizontal drag that dismisses past a
threshold, via a custom `snackbarHost`.

## ~~Media session and audio focus~~ — DONE 2026-09-01


The foreground service landed on 2026-09-01; playback survives the app leaving the screen and the
notification carries transport. What is left of the original item:

Playback did not survive long in the background.

The largest item and the one that makes the app usable rather than demonstrable.

- ~~Audio focus and becoming-noisy~~ — done 2026-09-01.
- Headphone and Bluetooth controls, lock-screen transport. Needs the `MediaSession`.
- `docs/ARCHITECTURE.md` §4 picked Media3's `SimpleBasePlayer`; **§12 records that the platform's own
  `android.media.session` was used instead**, and Media3 never became a dependency.
- Android 14+ requires a `foregroundServiceType` and its permission; `targetSdk` is 36, so this is
  not optional.

**Done when:** a headphone button pauses playback, the lock screen shows transport, and another app
starting audio pauses us instead of playing over us.

## ~~sc68 — SNDH, the format that started this~~ — BUILT 2026-09-01, UNPROVEN


It compiles and links for every ABI and the licence is verified. Nobody has heard a note: there is
no SNDH file on the build machine and no emulator. First job when one is to hand.

Also outstanding from this item:

- `.sc68` container files reference external replay binaries we do not ship, so they will not play.
- Subsong selection: sc68 reports `tracks`, and the UI has nowhere to show them.

`docs/OPEN_QUESTIONS.md` Q2 put it second after libopenmpt and it has not been done.

- Vendor `sc68` through `scripts/fetch-native-deps.sh`, pinned and checksummed like libopenmpt.
- **Verify its licence against the actual `COPYING` before anything else** and record it in
  `docs/LICENSES.md`. If it turns out to be GPL-2-only it invalidates the licence decision and must
  be raised, not worked around.
- It needs a second backend behind the engine facade, which is what will show whether that facade is
  the right shape.
- `sndh.net` did not resolve from this machine on 2026-08-31 — unverified whether the domain is gone
  or the network here blocks it. Find a source as part of the work.
- Backends differ in what they can do: sc68 emulates a CPU and **cannot seek**. The capability has
  to be declared and the UI has to reflect it (`docs/ARCHITECTURE.md` §5), which means item 5's
  transport and the seek slider both need a "can this backend do it" answer.

## ~~Reordering, and what a playlist row should say~~ — DONE 2026-09-01


Both raised 2026-09-01.

**Reordering.** Drag a track to a new position. Fits the draft model already in place: reordering is
an edit, so it waits for Save like the others. `withTracks` remaps history by identity, so a reorder
will not break the play history — that was designed for removal and happens to cover this.

**The row itself, as sketched:**

```
 12  ◤   Elysium                              SAP    ⋮   ≡
         4-Mat                                ATARI
```

- Ordinal on the left, replaced by a **play triangle** on the playing row rather than the dot used
  today. Better: a triangle says *what it is*, where a dot only says *this one*.
- Title and author stacked.
- The platform (`ATARI`) or the format (`SAP`). Worth having, and cheap — the backend already
  reports both.
- An overflow menu (`⋮`) for per-row actions: info, delete, and whatever comes later. This is where
  the wishlist's "full metadata" button belongs, and it removes the permanent delete icon that
  currently sits on every row.
- A drag handle (`≡`) on the far right, which is what makes reordering discoverable at all.

**One reservation, worth settling before building it.** That is five things across two lines on a
phone. The platform label and the overflow menu compete for the same right-hand space, and the
handle takes more of it. Suggestion: platform/format as a small label under the author or beside it,
with overflow and handle sharing the right edge.

**The ordinal stays.** Dropping it was proposed to buy space; the reason for keeping it is
better than my reason for removing it: with three hundred tracks the number tells you *where you
are in the list* at a glance, which nothing else on the row does. A scroll indicator would do the
same job and take no row space at all — worth offering as the alternative, but not worth removing
the number before one exists.

Also: **the delete icon leaving every row is a real improvement**, not just tidying. It currently
sits one thumb-width from the row you tap to play.

## ~~The snackbar covers the list~~ — DONE 2026-09-01


Raised 2026-09-01. Adding tracks confirms with a snackbar that sits over the rows you just added,
which is exactly what you want to look at. It is worse here than in most apps because the message
arrives at the moment the list changes.

It also carries undo, so it cannot simply be made quieter. Options worth weighing: put it above the
dock rather than over the content, shorten it to a line inside the top bar, or drop the message for
additions entirely and let the list itself be the confirmation — the rows appearing *is* the
feedback, and a notice that repeats what the screen already shows is noise.

## ~~Resolve metadata in the background~~ — DONE 2026-09-01


Raised 2026-09-01. Titles and authors currently improve only when a track is played, because reading
them means opening the file. On a list of three hundred that means the list stays full of filenames
until each has been heard.

A background pass after a scan — open each file, take title and author, write them, move on — fixes
that. It is the same work `adoptTitleFrom` already does, driven by a queue instead of by playback.

**Two things it has to respect.** It must not compete with playback for the network on a share, and
it must be interruptible: the user pressing play matters more than the pass finishing. It is also
half of the content-probing item (§7) — once every file is being opened anyway, identifying it
properly is nearly free.

## ~~Instant start (R9)~~ — DONE 2026-09-01, UNMEASURED


Read-ahead and a disk cache both landed. What is left is the measurement: whether a
five-to-thirty second wait actually became nothing. Also still open is the cache
eviction budget (`docs/OPEN_QUESTIONS.md` Q5) — nothing is ever deleted today.

Nothing is cached and nothing is prepared ahead. A local module is fast because it is small.

> **Correction, 2026-09-03.** This said the test library sits on an SMB share, inferred from a
> `hierynomus.smbj` trace in one log. **It does not** — that library is local on the phone, and the
> trace was incidental. The inference was recorded as a fact and then quietly
> shaped several decisions: R9's justification, C3's measurement, and how urgent the fetched-file
> read-ahead looked.
>
> **What survives the correction, and deliberately.** Hardening against awkward
> providers anyway: *"SMB is a nuisance and we should be robust to nuisances."* So the work stays —
> but as a choice made on purpose, not as a response to a setup nobody has. The difference
> matters, because the second kind of reasoning cannot be checked.

Two things make this real: reads can cross a network, and these formats are small enough that the
round trip dominates.

- A cache of fetched bytes, keyed by source identity, with the eviction budget from
  `docs/OPEN_QUESTIONS.md` Q5.
- Preparing the next track while the current one plays.

## Later, agreed but not scheduled

- Remote catalogues — Modland and HVSC (`docs/ARCHITECTURE.md` §8, endpoints already measured).
- The remaining backends: `libsidplayfp`, game-music-emu, UADE last.
- Everything in `docs/WISHLIST.md`.
