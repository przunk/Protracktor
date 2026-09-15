<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Random, as the phone does it, and what the page must match

*Written 2026-09-15 at the owner's request, after testing the digression in the browser: "spisz
funkcjonalność APK w zakresie random; spisz wymagania dla WEB (mają być takie same)".*

`docs/PLAN_RANDOM.md` holds the design and the decisions behind it. This is the behaviour as built,
so the two runtimes can be held to one list rather than to a memory of a conversation.

---

## 1. What the phone does

### Entering and leaving

- **Browse → Random enters and plays at once.** No second press.
- **Every entry starts a new record.** What played before is in History; the record itself is not
  kept between runs.
- **The scope is kept between runs**, as a setting (`random_scope` in the player state).
- **Leaving** — the Playlist button, or Back — ends the session: playback stops, the record goes,
  and the playlist is exactly as it was left, because nothing ever wrote to it.
- **The screen closes when something else takes the player**: a file from another app, or a tune
  played from a list (`docs/STATUS.md` C49). It never stands over music it is not playing.

### What it picks from

- **Three scopes**: everything indexed, one platform, or Modland's published favourites. Narrowing
  is a `format IN (…)` clause and nothing else; favourites is a join against the published list.
- **Changing the scope mid-session discards the picks read ahead**, so the change is heard on the
  next tune rather than three tunes later.

### How it draws

- **Uniform over tunes, not over authors**: `ORDER BY RANDOM() LIMIT n` over the whole scope, so an
  author with one tune is a quarter as likely as one with four.
- **Drawn wide and filtered**: four times what is needed (`OVERDRAW`), with anything already in this
  session's record dropped — the query cannot exclude, so the drawing does.
- **A pool too small to fill the session repeats rather than stopping.** Forty favourites cannot
  fill an evening otherwise.
- **Three picks stand past the cursor** (`READ_AHEAD`), their bytes fetched before they are wanted.
  They are **not shown**: the list is a record of what played, not a schedule.

### The record, and moving through it

- **One row per tune that has played**, in the order the dice gave them.
- **Next walks the record where there is more of it, and rolls only at its end** — with a list on
  screen, next means the next row.
- **Previous walks back** and stops at the start.
- **A row can be tapped**, but only backwards into what has been heard: the picks past the cursor
  are speculation nobody has been shown.
- **A row can be removed**, including the one playing, which keeps playing: tidying a list is not a
  request to stop the music.
- **A pick that will not open is walked past**, up to eight in a row, after which it stops and says
  so.
- **The dock offers "+"** for anything playing that is not the playlist, which appends that tune to
  the playlist as an edit waiting for Save.

### The digression (`docs/BACKLOG.md` A41)

- **"More from this author" from the record enters a digression**: the dice **waits** rather than
  ending, keeping its record and its cursor.
- **The folder becomes the transport at once**, before anything in it is played: next and previous
  walk the author's tunes, starting from the tune the jump was made from. The tune that was playing
  keeps playing; nothing restarts.
- **The folder opens with that tune marked and on screen**, scrolled to if it is below the fold.
- **The heading says "Browsing author" with the author's name under it**, in the shape the dice's
  own heading has, from the moment the jump lands.
- **Back returns to the dice**, at the pick it was on, **paused**. Play resumes that tune; next
  rolls.
- **One level**: inside the folder you may open what it contains and come back up to it; above it,
  Back is the way to the dice rather than into the archive.
- **It ends for good** when something else takes the player: a playlist chosen, a file from another
  app, a link opened.

## 2. What the page must match

**Everything in part 1**, with two differences that are the build's rather than the design's:

- **The scope is "everything this browser can play"**, because the page has no platform chips and no
  favourites list yet; the filter panel says so in as many words. When they arrive, part 1 applies.
- **The dice draws from the archives indexed in the browser** — Modland and ASMA — through the same
  uniform-over-tunes rule (`buildRandomTable`, `drawTrack`), with the same three read ahead, four
  drawn per pick and eight failures tolerated.

And two the page states in its own way, which is allowed because the shapes differ:

- the record, the heading and the way back live in the page's session heading over the list;
- Browse is a screen in the column rather than a dialog, so a digression's heading belongs **inside
  it**, above the list, where the phone puts it under the bar.

## 3. What was wrong on 2026-09-15, against that list

The owner's report, each line against the requirement it breaks:

| What he saw | Requirement |
|---|---|
| In the author's folder, next played another random pick | "The folder becomes the transport at once" |
| Back in the folder climbed to the format above it | "Above it, Back is the way to the dice" |
| The way back existed only as a hack through the playlist sheet | "Back returns to the dice, paused" |
| The Modland search box was still there | The phone offers no search in a digression |
| The heading did not read like Random's | "The heading says Browsing author … in the shape the dice's own heading has" |

The cause was one decision, not five: the page entered the digression when a tune in the folder was
**played**, while the phone enters it when the folder is **opened**. Everything above follows from
that, and is fixed by moving the entry to the jump.

*On the tune that came up twice:* with 300,000 tunes and some 150 heard, two sessions sharing one
tune is about a one-in-fourteen chance — the session drops repeats within itself, and nothing
remembers across sessions. Unremarkable rather than a fault, and the arithmetic is here so it need
not be done again.
