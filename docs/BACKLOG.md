# Backlog

Work that is agreed but not done, ordered so each item can be picked up cold. Written 2026-09-01
after a device session; the owner's words are quoted where the requirement came from him.

A defect that exists in shipped behaviour lives in `docs/STATUS.md`, not here. This file is for work
not yet started.

**Read `AGENTS.md` (workshop root and project root) before starting any of it.** Work happens on a
branch off `develop`, one stage per commit, and nothing merges without the owner saying so.

---

---

# A — open work

## A1. ~~Search results need the path too, and a way to hear a track first~~ — DONE 2026-09-02


Both reported 2026-09-01, both about the Search view specifically.

**The path is missing from search results.** Adding a folder now shows where a file lives, but a
search result still shows only the last part — "AMIGA", "SNDH". The owner wants both: the format or
archive it came from *and* the path within it, like `ASMA/Przunk/Bonio`. Local results have the path
in `subtitle` already; catalogue results put `format · author` there instead, so the two disagree
about what that field means. Decide what a result line owes the reader, and make both sources answer
it the same way.

**A track cannot be played from search before being added.** You can tick a row but not hear it,
which is backwards: the point of a search is to find out what something is. A play control on the
row is the obvious shape — the owner suggested the right-hand side — but there is a real question
behind it: **what do next and previous mean while listening from a search?** The answer that matches
everything else here is that the results become the queue for as long as you are in them, the way
Random has its own history. Playing from search must not quietly rewrite the playlist.

## A2. Subsongs — decided 2026-09-01, UI still open


game-music-emu reports track counts in the hundreds — one GBS in the sample said 99, an HES said 256
— and we play track 0 and nothing else. sc68 and ASAP report subsongs too. This is a defect now,
not a gap.

**The owner has decided what a subsong is: a property of a track, not a track of its own.**
Expanding a 99-track GBS into 99 rows was rejected outright — a folder of twenty such files would
become a playlist of two thousand rows.

**His constraints on the UI, in his words, to be designed against:**

- **(a)** It must be possible to play all subsongs in order.
- **(b)** It must be possible to play only subsong 0 and no others.
- **(c)** It must not cover the track's own view — the track is the *representative*, and it is what
  is on the list.
- **(d)** It must be easy to reach: no clicking through several places, and no complicating the way
  back to the playlist.

Nobody has a good design yet, his words included. **To be discussed before anything is built.**

Two observations to bring to that conversation:

- (a) and (b) together mean subsong playback is a **mode**, like Random — "play this one" and "play
  through them" are different intents and the transport has to know which. Random already
  established that shape and could be followed.
- (c) argues against a sheet or a dialog and in favour of something inline on the Now Playing
  screen, since that is the one surface where covering the track is not a problem.

Schema is not a constraint: the owner has confirmed the database can change freely and he can
reinstall, since nobody else uses the app yet.

## A3. Getting up and down a long list


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

## A4. Bulk operations on the playlist


Raised 2026-09-01. Long-press a row to start a selection, then **tap** further rows to add them —
tapping, not only dragging, because a selection of scattered tracks is the normal case and dragging
only reaches neighbours.

While a selection exists the per-row controls give way to actions in the top bar. Delete is the
obvious one and the owner named it; the others are worth thinking about rather than guessing —
plausible candidates are *move to another playlist*, *add to another playlist*, and *play these
only*. The last is interesting because it is close to what Random already does: a temporary queue
that is not the playlist.

**Two things it will collide with**, worth knowing before starting:

- **The drag handle.** Long-press-to-select and drag-to-reorder are different gestures on the same
  row, and the handle is what keeps them apart today. Selection must not make the handle ambiguous.
- **The draft model.** A bulk delete is one edit, not twenty, so undo has to restore the whole
  selection — the current single-track `lastRemoval` will not do.

## A9. The app icon

Raised 2026-09-02: the launcher icon is a placeholder — `@android:drawable/ic_media_play`, the
system's own. The owner wants to discuss the concept before anything is drawn, so this is a
placeholder for that conversation rather than a task.

Worth having ready for it: an icon needs an adaptive foreground and background (API 26+), a
monochrome layer for themed icons (API 33+), and it is the one asset where a wrong choice is visible
on every home screen. Nothing about it is a detail to settle in code.

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

And deliberately **not**: play, pause, next, previous, shuffle, repeat, or anything with a visible
result. The screen already answered.

**Implementation notes.** Compose's `LocalHapticFeedback` offers only `LongPress` and
`TextHandleMove`, which is thin. The richer constants live on `View.performHapticFeedback` —
`GESTURE_START` and `GESTURE_END` (API 30+), `SEGMENT_TICK` (API 34), `CONFIRM` and `REJECT`. With
`minSdk 29` that means a small helper that degrades: the good constant where it exists, `LongPress`
where it does not, nothing where the device has no vibrator.

Android already honours the user's system haptics setting, so there is no need for our own — and
adding one would be inventing a preference the platform already owns.

## A10. ~~Sharing — the file, and a link to it~~ — DONE 2026-09-02

Raised 2026-09-02 by the owner. Two actions, and they are **not** the same feature:

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

**Decided by the owner 2026-09-02**: the link points at **the file** — exactly what is being played
— and what to do with it is the recipient's business. So Modland's track URL stays as it is, and
the reason ASMA cannot have one (it publishes an archive, not a file tree) is now the whole reason
its share names the collection and the path instead.

## A11. ~~Random should read ahead, the way the playlist does~~ — DONE 2026-09-02

Raised 2026-09-02 by the owner: waiting for each random track to download is the wait R9 exists to
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
`randomPlayed` is for. **Confirmed by the owner 2026-09-02** — the dice re-rolls, next walks
forward.

## A12. ~~Random should keep going when a track ends~~ — DONE 2026-09-02

Raised 2026-09-02 by the owner: Random stops at the end of each track and has to be pressed again.

Today that is deliberate and the comment in `handleTrackEnded()` says why — *"Rolling on into the
playlist would be answering a question the user did not ask by pressing Random."* **That reasoning
survives.** What the owner wants is not rolling into the playlist; it is rolling on to the next
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

## A13. Application settings

Raised 2026-09-02 by the owner. There is no settings screen at all today, and several decisions
that are currently hard-coded or implicit are exactly the kind a person wants to change once and
never think about again.

**What already exists and would move there** — this is the argument for the screen, and it is worth
gathering before designing it, because a settings screen designed before its contents is a screen of
somebody's guesses:

- **Storage.** Granted folders are managed from Browse today; a downloaded catalogue index, the ASMA
  archive (20 MB) and the HVSC song lengths (5.2 MB) have **no way to be deleted at all**, and the
  fetched-file cache has no budget (`docs/OPEN_QUESTIONS.md` Q5). This is the strongest candidate:
  the app takes disk and offers no way to give it back.
- **Language.** Polish and English are chosen by the system today. An override is cheap.
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

Raised 2026-09-02 by the owner. Nothing here is written down anywhere yet; `docs/BUILD.md` covers
building and signing an APK and explicitly stops short of a bundle.

**What is already true:** the release build goes through R8, is signed from `PRZUNK_UPLOAD_*`, and
`build-release.sh` prints the certificate subject so an accidentally debug-signed artifact is
visible. `targetSdk` is 36 and `minSdk` 29, both current enough.

**What is missing, roughly in the order it will bite:**

- **An App Bundle.** The store takes `.aab`, not `.apk`. `docs/BUILD.md` says the script is not
  written because the owner is adding a bundle signing key later — that key is the blocker.
- **The launcher icon** (A9) is the system's `ic_media_play`. The store will not take that, and it
  is the one asset visible on every home screen.
- **A privacy policy and a data-safety declaration.** Required for every listing. Ours is unusually
  easy and worth saying plainly: the app collects nothing, has no analytics, no accounts, no
  crash reporting, and the only network traffic is fetching music from archives the user chose.
- **The GPL and the store.** Distributing a GPL-3 app through Play is fine, and the obligation is
  that source is offered to recipients — the public repository does that. Worth writing down once
  so it is not re-litigated. The **`sc68` replay binaries** question (`docs/LICENSES.md`) is a real
  one to settle *before* publishing, not after.
- **Content rating, listing text, screenshots, a feature graphic.** Mechanical, but none exists.
- **`versionCode` discipline.** Every upload needs a higher one; the scheme is in `docs/BUILD.md`
  and has never been exercised against a store that rejects duplicates.

**Not started, and not to be started without the owner**: publishing is his account, his key and
his name on the listing.

## A15. ~~The Random icon does not look like a die~~ — DONE 2026-09-02

Reported by the owner: *"it does not look like a die, it looks like the Excel logo"*, which was
exact. It was Material's `casino` glyph, whose pips wind the same way as its outline, so under
non-zero winding they filled in and left a solid rounded square. Redrawn as an outline plus five
pips, rendered even-odd. `PlayerIcons.icon()` gained a `hollow` flag for the next icon with the
same symptom.

## A16. No way back to the playlist from a browse jump — to discuss

Raised 2026-09-02 by the owner, after using **B2**: *"more from this author"* opens the browser at
the author's folder, which is right; back then walks up the folder levels, which is also right; but
there is **no one-tap way back to the playlist** from wherever you have got to.

**Why it is a real gap and not a missing button.** The rule the app follows today is "back goes up
one level, and from the top it leaves Browse". That is correct and it is also slow: land three
levels deep from a jump and getting out is four taps. The jump made it easy to arrive somewhere
deep, and nothing made it easy to leave.

**Options, none chosen — this is the owner's call:**

1. **A close affordance on Browse itself**, separate from back. An X in the top bar next to the
   back arrow. Cheapest, and it makes the two gestures visibly different things.
2. **Back from a jump returns whence it came**, rather than walking up. The jump becomes one step
   in the history rather than a teleport, which is what a browser tab does.
3. **The player dock is already on every screen** — tapping the identity row could mean "take me
   back to the list", which is close to what B13 already does inside the playlist.

Option 1 is the least clever and the easiest to explain. Option 2 is the most correct and the most
likely to surprise someone who wanted to keep browsing where they landed.

**Related and unsettled**: `docs/OPEN_QUESTIONS.md` Q1, the navigation model, touches exactly this.

## A17. The actions in a track's details need sorting out — to discuss

Raised 2026-09-02 by the owner about two places at once:

- **The expanded player**, where every action is its own full-width row — *show in playlist*, *more
  from this author*, *share the file*, *share a link* — and the list grows every time one is added.
  Four now, and A10 and B2 added two of them in a day.
- **The history list**, where the owner says the actions are *"średnio"* — the row menu offers
  what a playlist row offers, and some of it does not belong on something that is not in a playlist.

Not a layout tweak: the question is what a track's actions *are*, once there are more than fit
comfortably. Candidates are an icon row rather than stacked text buttons, an overflow menu, or
splitting "about this track" from "do something with this track".

## A18. Should playing something reorder the list it came from? — to discuss

Raised 2026-09-02 by the owner: what should happen to the order of the displayed list after one of
its entries is played.

Worth pinning down **which** list before designing anything, because the answer is probably not the
same for each: the playlist (where order is the user's and must not move by itself), history (where
order is recency and playing something arguably *should* move it to the top — it already does in
the database, the view just does not refresh under you), and a browse or search result (where order
is the archive's).

The reason it needs a conversation rather than a decision: a list that reorders under a finger is
the single most disorienting thing a list can do, and the owner has already said (defending the
ordinal numbers) that knowing where you are in three hundred rows matters to him.

## A19. Should a jump also fetch the whole author's folder? — to discuss

Raised 2026-09-02 by the owner, about **B2**: having jumped to an author's folder, pre-fetch what is
in it so playing any of it is instant.

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

## A5. Formats we do not play yet — planned in `docs/PLAN_FORMATS.md`


Agreed 2026-09-01: the owner will send this as its own goal. Recording what is known now so it does
not have to be rediscovered.

- **`.sap`** — Atari 8-bit. Needs ASAP (GPL-2.0-or-later, by Piotr Fusik, has an Android port).
- **`.sndh`** — should already work; see the defect in `docs/STATUS.md`. Fix before adding anything.
- **SID, NSF, SPC, GBS, VGM, AY** — `libsidplayfp` and game-music-emu, in that order of value.
- **Amiga custom (TFMX, Hippel, Future Composer, …)** — UADE, last, and its bundled replay binaries
  need a licence decision of their own.

Each new backend also means **re-indexing the catalogues**: the index only keeps entries whose
filename a backend might handle.

## A6. Probe content instead of trusting extensions


> "wczytuje dobrze pliki z folderu ale opiera się o rozszerzenie tylko (na razie ok)"

`SupportedFormats` filters by extension and says in its own documentation that this is provisional.
Extensions here are unreliable, absent, or shared between unrelated formats. Probing means reading
every candidate, which is why it belongs with the persistent index from item 1 rather than with a
foreground scan.

## A7. More online catalogues — planned in `docs/PLAN_CATALOGUES.md`

**`docs/WISHLIST.md` B10 is this item.** It was raised as a wish a day before this was agreed and
nobody struck it; the owner spotted the duplicate on 2026-09-02. This is the live one.

Modland and ASMA are wired up and verified end to end. The owner asked for several: ASMA, AMP
(amp.dascene.net), Aminet, ModArchive and others.

Each needs two things, and the second is usually the blocker:

- **Its index parser.** `Catalogue` is a sealed class; adding one is writing `parseIndex` and
  `urlFor` against whatever that archive publishes. None of the others is a single tab-separated
  file the way Modland's is.
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


> "po restarcie/aktualizacji aplikacji wszystkie utwory zniknęły z listy"
> "aplikacja powinna zapisywać stan ustawień po restarcie (np. loop, który ostatnio odtwarzaliśmy)"

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


> "chcę mieć widok browse który dodaje do playlisty pojedyncze (lub kilka zaznaczonych) utwory"

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
line. If the owner wants both, say so and it is done — this reading is an interpretation, not a
decision he made.

**Built that way on 2026-09-01**: undo on a track, a dialog on deleting a playlist.

## ~~A snackbar that can be swiped away~~ — DONE 2026-09-01


> "snackbar, który można 'przesunąć'"

Material 3's `Snackbar` has no swipe dismissal. Wrap it with a horizontal drag that dismisses past a
threshold, via a custom `snackbarHost`.

## ~~Media session and audio focus~~ — DONE 2026-09-01


The foreground service landed on 2026-09-01; playback survives the app leaving the screen and the
notification carries transport. What is left of the original item:

> "nie gra za długo w tle"

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
no SNDH file on the build machine and no emulator. First job when the owner has one to hand.

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

**The row itself — the owner's sketch, and it is a good one:**

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

**The ordinal stays.** I suggested dropping it to buy space; the owner's reason for keeping it is
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


Read-ahead and a disk cache both landed. What is left is the measurement: whether the owner's
five-to-thirty second wait on an SMB share actually became nothing. Also still open is the cache
eviction budget (`docs/OPEN_QUESTIONS.md` Q5) — nothing is ever deleted today.

Nothing is cached and nothing is prepared ahead. A local module is fast because it is small.

Two things make this real, and the owner's own setup shows why: his library sits on an **SMB share**
(the `hierynomus.smbj` traces in his log are the SAF provider for it), so every read crosses a
network before it reaches us.

- A cache of fetched bytes, keyed by source identity, with the eviction budget from
  `docs/OPEN_QUESTIONS.md` Q5.
- Preparing the next track while the current one plays.

## Later, agreed but not scheduled

- Remote catalogues — Modland and HVSC (`docs/ARCHITECTURE.md` §8, endpoints already measured).
- The remaining backends: `libsidplayfp`, game-music-emu, UADE last.
- Everything in `docs/WISHLIST.md`.

