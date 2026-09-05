# Wishlist

Ideas, not defects. A thing that does not work as intended belongs in `docs/STATUS.md`.

Numbered so a choice can be made unambiguously. The order is the order they were raised, newest
first — it is not a priority.

Each says who raised it and when. Stale reasoning is worse than no reasoning, so where a wish has
been overtaken by work already done, it says so.

---

## B21. The player notification is grey, tall and belongs to nothing

*owner, 2026-09-05: the controls themselves are fine; the bar around them is the problem.*

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
the owner's Android version, and how a generated bitmap looks at the two sizes the shade and the
lock screen use. Both are questions a device answers in ten minutes and a document cannot.

**Related:** `docs/STATUS.md` C12, where the same notification lost its skip buttons because Android
13 takes them from `PlaybackState` rather than from the actions we add — this part of the platform
rewards checking over assuming.

## B22. Random, but within something

*owner, 2026-09-05: random for a particular platform, author or other domain.*

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

## B23. Search by platform, not by service

*owner, 2026-09-05: the online section should offer AMIGA, C64 and so on rather than Modland, ASMA
and The Mod Archive — searching every service available, but narrowed to a platform or format.*

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

## B20. The year a tune was released

*owner, 2026-09-04: "fajnie by było gdyby dało się zobaczyć rok wydania utworu (np. w info o
utworze, w liście albo wyszukiwaniu)".*

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

## B19. Server-hosted periodically updated catalogue indexes (Cloudflare / Google Cloud)

*owner, 2026-09-02.*

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

*owner, 2026-09-01.*

The dock says what is playing; the list does not say where it is. Tapping the identity row — or the
Now Playing it opens — should take you to that row in the playlist.

Cheap: `LazyListState.animateScrollToItem` on the index the queue already knows. It shares its
mechanism with B14 and with `docs/BACKLOG.md` A3.

**Built** as the locate button in Now Playing. Not confirmed on a device.

## B14. ~~A follow-the-playing-track toggle~~ — DONE 2026-09-02

*owner asked the question 2026-09-01, and settled it himself 2026-09-02.*

The wish: press next on shuffle and the playing track is somewhere off screen.

**The owner's design, and it is better than the chip I proposed.** A floating button, bottom-right
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
and a far target jumps rather than animating. The owner then asked for the button to be subtler,
which was done the same day. Not confirmed on a device.

## B1. ~~Play a track from the search results~~ — DONE 2026-09-02

*owner, raised twice, 2026-09-01.*

Already written up in `docs/BACKLOG.md` A1; repeated here because he has now asked for it twice, which is a signal about
priority rather than a duplicate. **Built** — the results become the queue while you are in them, so
next and previous walk what you found and the playlist is untouched.

## B2. ~~Jump to a tune's neighbours~~ — DONE 2026-09-02 for catalogue tracks

*owner, 2026-09-01.*

Something plays at random, it is good, and the question is "what else did they write". An action — from the row's overflow menu or from the
Now Playing — that opens the browser **at the place the track came from**: the author's folder
in Modland, the folder on disk.

His refinement is the good part: for a **local** file this should not open the browser at all, it
should just show the path. There is nothing to browse to that the user does not already have.

Depends on knowing where a track came from, which `TrackRef.subtitle` already carries for both
sources, though in two different shapes — see the note under A1 about what a result line owes the
reader.

## B3. ~~Show the catalogue path in Information~~ — DONE 2026-09-02

*owner, 2026-09-01.*

A Modland track's information should read `Modland/Przunk/name.mod`. Today the dialog shows what is in `subtitle`, which for a
catalogue track is `format · author` — right for a row, wrong for an information panel. Same
underlying inconsistency as the wish above.

## B15. ~~History should say when~~ — DONE 2026-09-03

*owner, 2026-09-02, after using B8.*

The list says what was played and in what order, and not **when**. "That tune two days ago" is the
question history exists to answer, and a date and time is most of the answer — the ordering alone
only says "before that other one".

Cheap: `played_at` is already stored, to the millisecond. What needs deciding is the *form* —
"yesterday, 21:14" reads better than a date for anything recent and worse for anything old, and
grouping the list under day headings may be better than putting a timestamp on every row.

**Raised again 2026-09-03**, and it is what the owner wanted from history all along: asked whether
replaying should reorder the list (`docs/BACKLOG.md` A18) he said no, and *"what I care about more
is the date and time in history."*

**Built** with `DateUtils.getRelativeDateTimeString`, so it reads "yesterday, 21:14" while that is
useful and becomes a date when it is not — and is translated by the system into languages this
project does not ship. It shares the source line rather than adding a third. **Day headings remain
the open half**: they may well read better than a stamp on every row, and nothing here forecloses
them.

## B16. ~~The share-file and share-link icons are the same~~ — DONE 2026-09-02

*owner, 2026-09-02.* Both used `PlayerIcons.Share`. The link has its own now — two actions that do
different things should not be told apart only by their labels.

## B17. A mis-tap in an online list now costs a download

*owner, 2026-09-02, raised as a consequence of `docs/ARCHITECTURE.md` §17 and deliberately not acted
on.*

Once tapping a row plays it, tapping the wrong row in a Modland folder fetches a file. These are
kilobytes so it is not a disaster, and before the change a mis-tap cost nothing at all.

Worth thinking about rather than fixing: the honest options are a short delay before the fetch
starts, cancelling the fetch when another row is tapped within a moment, or simply accepting it.
The middle one is probably right and is nearly free — `openJob` is already cancelled on a new load.

## B18. ~~Add a track to a *different* playlist, from its menu~~ — DONE 2026-09-02

*owner, 2026-09-02.*

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

*owner, 2026-09-03, raised while looking at the export that had just been built.*

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

## B4. Play MP3 too

*owner, 2026-09-01.*

Likely simple, and there is a neat route: `minimp3` is a
single public-domain header, which is a fourth backend of about fifty lines rather than a whole
library. (Android's own decoder is not the easy answer here — the engine is native from the file
to the speaker, and routing one format through the platform instead would mean two playback paths
to keep in step.)

Worth a thought before doing it: this app is a retro chiptune player, and MP3 is the format its
whole point is *not*. Handy for a rip of something, out of place in a browse tree. Probably belongs
behind "play this file" rather than in the library scan.

## B5. A web player, with favourites and history synced to an account

*owner, 2026-09-01.*

The same music in a browser, sharing state with the phone through a Google or Cloudflare account. Recorded
as a thought to return to, not a plan.

Worth noting now, because it would change decisions we are making today: the decoders are C and
would need WebAssembly builds (libopenmpt already ships one; sc68 does not), and syncing state to
an account means a server, accounts, and somebody's data in someone else's hands — which is a
different kind of project from an app that reads files off a phone. The parts that would carry
over unchanged are the ones already kept free of Android: `PlayQueue`, and the schema in
`SchemaSql`.

## B6. Fold hard-panned channels together

*owner, 2026-09-01.*

Amiga modules pan channels hard left and right by convention, and on his phone one "speaker" is the screen vibrator: half the music is
effectively inaudible. Wanted is a mix control — full stereo, narrowed, or mono — applied in the
render callback where the gain already is. Note that libopenmpt has a stereo-separation setting of
its own, so part of this may be a backend option rather than a mix of ours.

## B7. Full metadata for any supported file

*owner, 2026-09-01.*

An info button or a long press, showing everything the backend knows about a track rather than the handful of fields the player
screen has room for. Should work on any file the current version can open, not only on what is
playing.

## B8. ~~A history of what was played~~ — DONE 2026-09-02

*owner, 2026-09-01.*

Somewhere in settings. Note that `PlayQueue` already keeps a history, but only within a session and only for the active playlist;
a real one is a table of plays with timestamps, which is a small schema change and a screen.
There is no settings screen yet either.

## B9. Optional visualiser on the main screen

*owner, 2026-08-31.*

R4 makes metadata the default; the visualiser returns as something the user switches on. Worth doing properly (a real scope or
per-channel VU driven by the render callback) rather than the "stiff" one being replaced.

## B10. ~~Online catalogue browsing — the rest of the archives~~ — SAME AS A7, folded 2026-09-02

*Claude, 2026-08-31. Spotted as a duplicate by the owner 2026-09-02, and he was right.*

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

