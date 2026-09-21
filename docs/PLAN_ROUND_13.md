<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Round 13 — titles, search, the dock, folders ahead

*Asked for on 2026-09-21, after 0.7.0 went to closed testing and eleven days before the app goes
public on 2026-10-02. Written down before any code; the decisions D1–D6 are the owner's, each with a
recommendation.*

| # | what | where it came from |
| --- | --- | --- |
| **A47** | accented letters in titles come out as `�` | `docs/BACKLOG.md`, noted 2026-09-17 |
| **A48** | two seconds before the playlist appears | `docs/BACKLOG.md`, one cause fixed 2026-09-18 |
| **A53** | search ignores accents: `michal` finds `Michał`, and the other way round | new |
| **A54** | the dock's text scrolls slowly left when it does not fit on one line | new |
| **A55** | opening a folder caches its tracks one after another, three at a time, a spinner on each | new |

**One item at a time** (the owner, 2026-09-21): each is its own `/goal` run, its own branch off
`develop`, its own PR and its own approval on the phone, so that a regression can be pinned on one
feature. `GOAL.md` round 13 has the rules of the runs.

**Database versions follow the merge order.** A47 takes **18** (AGENTS.md §10); A53 takes the next
free number when its run starts, because it is built on `develop` after A47 has merged.

## Order, and why

1. **A48, the instrumentation only.** Timing lines around every candidate, in the build the owner
   installs anyway. The fix waits for the phone's numbers; putting the measuring first means every
   later handover in this round also measures.
2. **A47** before A53. Until titles are decoded, `Åkes lekhörna` is stored as `�kes lekh�rna`, and
   there is nothing for accent-folding search to fold.
3. **A53**, on the titles A47 made right.
4. **A54**, independent and small.
5. **A55**, the largest and the only one with network, storage and privacy consequences.
6. ~~**A48, the fix**, from what step 1 measured on the phone.~~ **Not needed** (2026-09-21): the phone
   measured under 0.4 s from process start to the playlist, and the owner closed A48.

One branch and one PR per item, each off `develop` as the owner has left it. Nothing merges before
the phone.

---

## A48. Two seconds before the playlist appears

What is known is in `docs/BACKLOG.md` A48: the full-table scan at launch was found and removed;
what remains is unexplained. **The rule stays: do not guess.**

**Step 1 — measure.** One log tag, `PtLaunch`, one line per stage with its duration in
milliseconds: process start → `onCreate`, the database opening (and migrating, when it does),
`restore()` split into its queries, `tracksIn` with the row count, `enforceBudget()` with the number
of files walked, `summaries()`, `grantedFolders()`, the first frame with the playlist in it. Kept in
the release build, because the release build is what the owner installs; `Log.i` survives R8 as
configured today.

The owner then runs, after a cold start (swipe the app away first):

```
adb logcat -d -s PtLaunch
```

**Step 6 — fix** what the numbers name. If they name something not on the candidate list, that is
reported with the numbers before anything is changed.

## A47. Accented letters in titles

The analysis is in `docs/BACKLOG.md` A47 and stands: a module's title is raw bytes from a fixed
field, usually ISO-8859-1 on an Amiga, handed to Java through `NewStringUTF`, which expects UTF-8.

**The fix, in one place:** `engine.cpp` transcodes every string it hands out — title, author,
message, instrument and sample names — to UTF-8 before it leaves the engine. Both players read the
engine through that one exit, so the browser is fixed by the same change.

**D1 — how bytes become letters.**

**Decided 2026-09-21, after the premise below proved wrong.** Read from the file, the title is
CP437 (`0x86`, `0x94`), not ISO-8859-1, and the `�` is made by libopenmpt, not by `NewStringUTF`.
So neither option as written would have fixed the reported file. The owner chose the revised rule:
**valid UTF-8 stays; a byte from 0x80 to 0x9F means CP437; anything else is ISO-8859-1** — in the
engine for every decoder's text, and in libopenmpt through a patch. Built on
`feature/a47-title-encoding`; the reasoning is `docs/STATUS.md` C79 there.

*The options as first written:*

- **(a) Recommended: valid UTF-8 stays UTF-8; everything else is ISO-8859-1.** A byte sequence that
  parses as UTF-8 by accident is rare enough to ignore, and ISO-8859-1 maps every byte to a letter,
  so nothing comes out as `�` again.
- (b) Also guess CP437 for DOS-era files. Telling CP437 from Latin-1 by content is a guess, and a
  wrong guess turns correct Latin-1 into a different kind of wrong. Not recommended, unless the
  owner has files where Latin-1 reads visibly wrong.

**Already-stored titles.** Titles read before the fix sit in the database with `U+FFFD` in them and
would stay wrong forever. Migration 18 clears every stored title and author that contains `U+FFFD`,
so the background pass that fills them in reads them again. Only those rows; nothing else is
touched.

**Row identity.** Titles are shown, not keyed on. Before the change, this is checked table by table
rather than assumed (the BACKLOG entry says so): a changed title must not change what a row is.

**Tests.** The transcoding as a host test of the engine function: Latin-1 bytes, valid UTF-8,
mixed, a field that ends mid-sequence. The migration against real SQLite, with a stored `U+FFFD`
title before and the same row cleared afterwards, and a clean title left alone.

## A53. Search that ignores accents

`michal` finds `Michał`, `michał` finds `michal`, `akes lekhorna` finds `Åkes lekhörna`, `dzwiek`
finds `dźwięk`.

**Why it is not one line.** Search is SQL `LIKE` over half a million catalogue rows
(`SearchTerms.sqlFor`), and SQLite's `LIKE` folds case for ASCII only. Android's SQLite offers no
way to add a folding function. `ł` is not even a decomposable letter, so Unicode normalisation
alone does not turn it into `l`.

**The fold, in one place** — `SearchTerms.fold()`: lower-case, Unicode NFD, drop the combining
marks, then a short table for the letters that do not decompose: `ł→l`, `đ→d`, `ø→o`, `ß→ss`,
`æ→ae`, `œ→oe`, `þ→th`. Both the query and the stored side go through it.

**D2 — where the folded text lives.**

**Decided 2026-09-22: (a)**, approved by the owner as recommended.

- **(a) Recommended: a sparse `folded` column**, filled only for rows whose title or author is not
  plain ASCII, and `NULL` for the rest. The query is folded and matched against `title`, `author`
  *and* `folded`. An ASCII row is matched by its own columns as today; a row with accents is
  matched through `folded`. Nearly every catalogue row is ASCII, so the column costs almost nothing,
  and the query adds one `OR` to a scan it already does. Measure the worst case (a search matching
  nothing) on the 516,000-row table before and after, as the separator rule was measured.
- (b) A full folded column on every row: simpler query, about 12 MB more database, and a longer
  migration. Not recommended.
- (c) No column: loosen the SQL to `_` wildcards and filter in Kotlin. Needs no migration, but the
  limit then cuts before the filter, and a search that returns fewer rows than exist is a search
  that lies. Not recommended.

**Filling it.** A53's migration fills `folded` for existing rows that need it; the index import and
the library scan fill it for new ones. The migration is measured on the full index on the host
before it meets a phone.

**The browser follows the same rule.** `web/src/rules.js` gets the same fold, and the cases go into
`docs/rules/queue-cases.tsv` `[searchMatch]`, which both players are tested against. One rule, one
table of cases.

**Out of reach:** The Mod Archive's live search is their site's own search, and it receives the
query as typed.

## A54. The dock's text scrolls when it does not fit

Today both dock lines — the title, and author · machine · year — are cut with `…`.

**The change.** A line that does not fit scrolls slowly to the left, stops at the start for a
moment, and goes round again; a line that fits does not move. Compose's `basicMarquee` does this,
and it is in the Compose version already in use, so no new dependency.

**D3 — what scrolls, and how.**

**Decided 2026-09-21:** the owner asked for A54 next without choosing otherwise, so it is built to
the recommendation, (a).

- **(a) Recommended:** each of the two lines scrolls on its own, only when it overflows; about
  30 dp per second, a two-second pause at the start of every pass; **no scrolling at all when the
  system's "Remove animations" is on**, which then falls back to `…` as today. The loading line
  ("Loading…") never scrolls.
- (b) The title only.

Only the dock. Now Playing shows the full title on several lines already.

**Tested as far as a host can:** that the line which fits is not given a marquee, and that
animations off means no marquee. How it looks and reads is the phone's.

## A55. A folder's tracks cached ahead, three at a time

Opening an author's folder starts fetching its tracks into the cache, one after another, **three
at a time**. A row being fetched shows a turning spinner in the place on the left where the
checkbox appears while selecting.

**Where it applies.** Catalogues fetched a file at a time: **Modland** folders. ASMA is already
whole on the phone. An UnExoticA folder is one game's archive, fetched in one go when the first
tune is played, so there is nothing to spread over three. The Mod Archive has no folders.

**How it behaves.**

- The **track the user taps goes first**, outside the three, and a track already being fetched
  ahead is not fetched twice: the tap waits for that fetch.
- **Leaving the folder stops the rest.** A fetch already running finishes; nothing new starts.
- A **failed** fetch is not retried in the background. The row shows no error, because nobody asked
  for anything; tapping it fetches and reports as today.
- **While selecting**, the checkbox has the slot and the spinner is not shown; the fetch goes on.
- It goes through the existing cache, with its 512 MB ceiling and least-recently-used eviction.

**D4 — when it runs.** Nobody has pressed anything, so this spends data on the user's behalf —
the exact objection A46 made to downloading at first launch.

- **(a) Recommended: on Wi-Fi (an unmetered network) only**, with a setting: *Cache folders ahead*
  — **Wi-Fi only** (default) / **Always** / **Off**.
- (b) Always, with the setting to turn it off.
- (c) Only on a button in the folder ("Cache this folder"), never by itself.

**D5 — what a row shows once cached.**

- **(a) Recommended:** the spinner turns into a small "on this phone" mark, the same shape as the
  cloud-with-check that marks a downloaded catalogue, so a row says it will play without network.
  Rows cached earlier show it too. It carries a content description for a screen reader.
- (b) Nothing; the spinner simply goes.

**D6 — a large folder.**

- **(a) Recommended: stop after 100 MB per folder.** A folder that big would otherwise push a fifth
  of the cache out to make room for tracks nobody has heard, including tracks played often.
- (b) No limit; the cache ceiling is the only bound.

**The privacy policy changes.** It says Modland is asked for "its catalogue index and selected
tracks". Tracks cached ahead are tracks from a folder the user opened, not selected ones, so the
sentence becomes "tracks you play, and those in a folder you open when caching ahead is on", in
both languages. No new host and no new data; `data-safety.md` is re-read and changed only if it
states otherwise. The hosted policy is the `master` copy on GitHub, so the new text is live after
the next release reaches `master`.

**Tests.** The queue as plain Kotlin: three at a time, the tapped track first, no second fetch of a
track in flight, leaving stops the rest, the 100 MB stop, the network rule for each setting value.
The spinner and the mark are the phone's.

**Not in this round:** the browser. It has no cache that survives the tab, so caching ahead there is
a different question.

---

## What the owner checks on the phone

- **A48:** a cold start, then `adb logcat -d -s PtLaunch`, pasted back.
- **A47:** `Zalza/akes lekhorna.mod` reads `Åkes lekhörna`; a title that was already right still is.
- **A53:** `michal`, `michał`, `akes lekhorna` in search find what they should, in Browse and in the
  library.
- **A54:** a long title scrolls slowly and comes back; a short one stands still; with "Remove
  animations" on, nothing moves.
- **A55:** on Wi-Fi, an author's folder spins three rows at a time down the list; a tapped row plays
  at once; leaving the folder stops it; on mobile data with the default setting, nothing spins.
