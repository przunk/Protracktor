<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Round 9 — seven things the owner found, in the order they will be done

*Reported by the owner on 2026-09-14, after testing the web player and the phone. Written down and
ordered before any of it is built, at his request: "zapisz wszystkie problemy najpierw i posortuj
sobie do realizacji". Branch: `feature/round-9`, all seven on it.*

The defects live in `docs/STATUS.md` (C41–C45) and the two new pieces of work in `docs/BACKLOG.md`
(A35, A36); this file is the running order and the working notes.

## The order, and why it is this one

1. **C41 — APK: "Add to playlist…" looks like it moves the track.** First because it is the one that
   looks like losing music: the track is gone from the list he started in.
2. **C42 — WEB: a file the engine refuses by throwing kills every later tune.** Second because one
   bad file ends the session until the tab is reloaded.
3. **C44 — APK: the playlist list does not refresh its counts after an add.** Third because it is
   the same journey as C41 and the same code to read.
4. **A35 — WEB: "Add to playlist" in a track's three-dots menu, as the APK has it.** Fourth: the web
   half of the same subject, and small.
5. **C45 — APK: the seek bar's position dot is nearly invisible.** Fifth: small, certain, visual.
6. **C43 — APK: the transport sometimes missing from the notification.** Sixth because it is the
   only one with no reliable way to reproduce yet, so it needs time rather than a quick fix.
7. **A36 — WEB: a gear beside shuffle, opening a settings panel.** Last: nothing depends on it, and
   it is for later use.

## The notes

### 1. C41 — "Add to playlist…" takes the track out of the list it was in

*Owner: "add to playlist.. przenosi tracka … w liście źródłowej już tego tracka nie ma! a powinien
być; ta funkcja powinna dodawać do innej playlisty nie zabierając tracka z aktualnej."*

Read first: `PlaybackController.addToPlaylist(targetPlaylistId, tracks)` (around line 2879) — it
reads the **target's** tracks, appends what is not there and calls `store.replaceTracks(target)`.
**It does not touch the source**, so the disappearance comes from somewhere else on that journey:
the picker's caller in the UI, the selection path that collects the rows, or the source playlist
being written back from a stale copy afterwards. Find it before fixing anything.

Done when: adding a track from playlist A to playlist B leaves A exactly as it was, checked by a
test over the store rather than by eye.

### 2. C42 — one refused file and the engine is done for

*Owner, from the browser console:* a Startrekker AM file, libopenmpt warning that external
synthesiser instruments are not supported, then `uncaught exception: 1464664` out of
`___cxa_throw` in `engine.mjs`, and after that **no tune plays at all**.

A C++ exception escaping into the worklet leaves the engine's state and the worklet both unusable.
Two halves: `pt_open` in `native/engine/engine.cpp` must catch what a backend throws and answer with
a refusal like any other; and `web/src/processor.js` must survive an engine that has thrown — the
next `open` has to work. Check on the file itself (Modland has Startrekker AM) through the Node
route in `docs/PLAN_INSTRUMENT_NAMES.md`, then in the page.

Done when: that file is refused with a sentence, and the tune after it plays.

**Android has the same unguarded boundary** (`player_oboe.cpp`: `nativeOpen`, `nativeDescribe` and
the rest call straight into a backend). There an escaping C++ exception ends the process rather than
a session. Not part of this item -- the owner reported the browser -- but it is the same repair and
belongs on a branch of its own.

### 3. C44 — the playlist list keeps stale counts

*Owner: "dana lista w widoku playlist nie odświeża ilości tracków, dopóki się w nią nie wejdzie."*

`PlayerUiState.playlists` is filled where the playlists are read (around line 781) and nothing
re-reads it after `addToPlaylist(target, …)` writes. Refresh it there, on every path that changes
what a playlist holds.

### 4. A35 — the web's track menu has no "Add to playlist"

*Owner: "3 kropki (ustawienia) tracka nie mają opcji add to playlist jak w APK -> ma działać tak
samo jak w APK."* The page's row menu offers Select, Save the file, Copy a link, Share with
Protracktor, More from this author, Information — and adding is only reachable by ticking rows
first. The phone's row menu has "Add to playlist…" straight away, opening the picker for one track.
Add it in the same place and with the same picker (`openAddTo([entry])`).

### 5. C45 — the position dot on the seek bar

*Owner: "nieprzesuwalnego handla na pasku odtwarzania prawie nie widać (kropka, która wskazuje
aktualny czas) — jest ciemna na ciemnym tle."* `ui/SeekBar.kt` draws it by hand rather than with
`SliderDefaults.Thumb` (around line 110) and takes its colours around line 133. Make it read on a
dark background, both while playing and while stopped.

### 6. C43 — no transport in the notification, sometimes

*Owner: "czasem z jakiegoś powodu nie widzę paska odtwarzania w notification (słyszę jak muza gra
ale tego playera nie widać)."* `PlaybackService` builds it with the platform builder and MediaStyle
and calls `ServiceCompat.startForeground` (around line 212). **No reproduction yet**, so: read the
paths that build and cancel it, look for the ones where playback starts without the service in the
foreground, and add what is needed to tell the two apart in a log the owner can send. A guess with
no evidence is worth less here than a way to catch it.

### 7. A36 — a gear beside shuffle

*Owner: "możesz dodać koło zębate, które otworzy ustawienia WEB po lewej stronie od shuffle (w
przyszłości użyjemy)."* A gear to the **left of shuffle** in the dock, opening a settings panel of
the page's own. It may hold little at first — what is already there and worth showing is the
storage line and the engine's backends — but the way in is what he asked for.

## The rules for all of it

English in the repository, Polish in conversation. One branch, `feature/round-9`, a commit per
piece. Nothing merges until he has tested it. Handover is a release APK and a web bundle.
