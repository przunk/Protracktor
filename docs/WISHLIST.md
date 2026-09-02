# Wishlist

Ideas, not defects. A thing that does not work as intended belongs in `docs/STATUS.md`.

Numbered so a choice can be made unambiguously. The order is the order they were raised, newest
first — it is not a priority.

Each says who raised it and when. Stale reasoning is worse than no reasoning, so where a wish has
been overtaken by work already done, it says so.

---

## B13. ~~Tapping the player bar scrolls the list to that track~~ — DONE 2026-09-02

*owner, 2026-09-01.*

The dock says what is playing; the list does not say where it is. Tapping the identity row — or the
expanded player it opens — should take you to that row in the playlist.

Cheap: `LazyListState.animateScrollToItem` on the index the queue already knows. It shares its
mechanism with B14 and with `docs/BACKLOG.md` A3.

**Built** as the locate button in the expanded player. Not confirmed on a device.

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
expanded player — that opens the browser **at the place the track came from**: the author's folder
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

## B15. History should say when

*owner, 2026-09-02, after using B8.*

The list says what was played and in what order, and not **when**. "That tune two days ago" is the
question history exists to answer, and a date and time is most of the answer — the ordering alone
only says "before that other one".

Cheap: `played_at` is already stored, to the millisecond. What needs deciding is the *form* —
"yesterday, 21:14" reads better than a date for anything recent and worse for anything old, and
grouping the list under day headings may be better than putting a timestamp on every row.

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

