# GOAL

The work to do when the owner is away. Written 2026-09-02.

**How to use it:** the owner runs `/goal` pointing here, and the agent works through the list in
order until it is done or genuinely blocked.

---

## The rules

These are not new — they are `AGENTS.md` and `/mnt/workspace/AGENTS.md`, restated because an agent
working unattended is the case they were written for.

1. **One item, one branch, one merge.** Branch off `develop`, merge back to `develop` when the item
   is finished and building. Do **not** touch `master`: merging there is the owner's decision.
2. **Every item ends with `./scripts/test-protracktor.sh` and `./scripts/build-debug.sh` green.**
   A stage that does not build is not a stage.
3. **Do not claim anything works on a device.** Nothing here can be confirmed without the owner's
   phone. Say what was verified and by what.
4. **If an item turns out to need a decision, stop that item, write the decision needed into
   `docs/BACKLOG.md`, and move to the next one.** Do not guess on the owner's behalf. Guessing on
   your own behalf about *implementation* is fine and expected — say so in the commit.
5. **Update `docs/STATUS.md` as you go.** A document that lies is worse than one that is missing.
6. Tick items off in this file as they land, so progress is visible at a glance.

## Explicitly out of scope

- **`docs/BACKLOG.md` A2 (subsongs).** The owner has decided what a subsong *is*; the UI is an open
  conversation he asked to have. Do not design it.
- **The Commodore ROM decision.** Integrating libsidplayfp is item 3; deciding whether to ship ROMs,
  ask the user for them, or live without them is not. Measure and report.
- **`master`.** See rule 1.
- **`docs/STATUS.md` C3.** Measuring R9 needs his phone and his network share.
- **`docs/BACKLOG.md` A3 (fast scrolling).** It was on this list and the owner pulled it: he wants to
  talk about it first. Note that item 2 builds part of the same machinery, so leave the scrolling
  helpers general enough not to prejudge A3 — but do not build A3.

---

## The list

- [x] **1. The follow-track button is too heavy**
      Reported 2026-09-02: it covers too much of the list. It is currently an
      `ExtendedFloatingActionButton` with a label — the label is what makes it large. Make it
      subtle: a small icon-only button, or something that fades to the edge when the list is idle.
      It still has to be discoverable enough that the owner finds it without being told, which is why
      the label was there in the first place — so shrinking it is a trade, not a free win. Keep the
      behaviour exactly as it is; this is about weight on screen, nothing else.

- [x] **2. Adding tracks should show them**
      Reported 2026-09-02. Adding from Browse appends to the end of the playlist and leaves the view
      where it was, so nothing visibly happens — which is worse now that the confirming snackbar has
      been removed on purpose. Scroll to the first newly added track.

      Note this is the same machinery as item 1 and as `docs/BACKLOG.md` A3, which the owner has
      reserved for a conversation. Use the scrolling helper that already exists; do not build A3.

- [x] **3. libsidplayfp — Commodore 64** *(30 of 30 played with no ROMs at all)*
      The format chosen for this round, and the reasoning is the point of writing it down: it is the
      largest single body of music left (**~72,000 files in Modland alone** — HVSC 60,572, Sidplayer
      5,032, RealSID 3,540), it is one library rather than several, and C64 is closer to the heart of
      this app than the `*SF` console dumps that rival it on volume.

      Follow `docs/PLAN_FORMATS.md` §1, and **prove it on the host before integrating**, the way
      sc68, ASAP and game-music-emu were. That has caught something every time.

      **The ROM question stays the owner's.** Some SID tunes need Commodore's KERNAL, BASIC and
      CHARGEN images, which cannot be shipped. Do **not** decide what to do about that. Do the thing
      that makes his decision easy: integrate, play what plays without them, and **measure how many
      of thirty random SIDs actually need them**. A number turns that question from abstract into a
      choice.

- [x] **4. ASMA — the Atari 8-bit archive** *(it publishes the whole collection as one zip, which is a second kind of catalogue)*
      Unblocked by ASAP landing: it is the reference collection for `.sap`, and we can now play what
      it holds. `docs/PLAN_CATALOGUES.md` records what is known — the site responds, `asma.zip`
      does not, and **the distribution URL and its index shape are unknown**. Finding them is the
      first half of the job.

      If it turns out ASMA publishes no machine-readable index, stop and write that down rather than
      scraping HTML. A catalogue that depends on the shape of somebody's web page is a catalogue that
      breaks silently.

- [x] **5. HVSC — at least its song lengths** *(done; full HVSC browsing deliberately not built)*
      `Songlengths.md5` fetches directly (5.2 MB, verified 2026-08-31) and gives SID tunes the
      durations they otherwise lack entirely. Worth having **even if the full HVSC catalogue is not
      built**, because without it every SID will show as unknown length and the scrubber will be
      disabled.

      Full HVSC browsing is a different shape from Modland — one large collection rather than an
      index plus per-track fetches — and `docs/PLAN_CATALOGUES.md` says so. If that shape turns out
      to need a second kind of `Catalogue`, build the song lengths and write up the rest.

## When the list is done

Stop. Do not start `docs/BACKLOG.md` A2 (subsongs), A3 (fast scrolling) or A4 (bulk operations) —
all three are reserved for a conversation.

Write what happened, what is unverified, and what you would do next. In particular, say plainly how
much of items 3, 4 and 5 was **measured** rather than merely built: the last round established that
a backend proven on the host before integration is worth several device round trips, and the same
goes for a catalogue whose index has actually been parsed.

---

## Round 2 — closed 2026-09-02

All five items done and merged into `develop`. What is worth knowing beyond that:

- **Item 3 (libsidplayfp)** needed no Commodore ROMs for any of thirty test files, which shrinks the
  ROM question rather than answering it — none of the thirty was BASIC-compatible, and those are the
  ones that would need them.
- **Item 4 (ASMA)** was not the shape the plan assumed. It has no index; it publishes the whole
  collection as one 20 MB zip. That produced a second kind of catalogue rather than another parser
  (`docs/ARCHITECTURE.md` §13).
- **Item 5 (HVSC song lengths)** is keyed on the plain MD5 of the file, not the header hash older
  HVSC releases used and that libsidplayfp still exposes. Written from the older documentation it
  would have found nothing and looked empty rather than wrong.

**Nothing in this round has been confirmed on a device.** Five formats and two online archives were
built and measured on the host; none has been seen working on a phone. That is the honest state and
the first thing worth doing next.

---

# Round 3 — set 2026-09-02

The owner picked these five by number and asked for them to be built, not planned. The rules at the
top of this file still apply, unchanged.

**Out of scope for this round**, in addition to the standing list above: `docs/BACKLOG.md` A13
(application settings) was added the same day and is deliberately **not** here — it needs a
navigation decision the owner has not made (`docs/OPEN_QUESTIONS.md` Q1 touches the same surface).

Order is chosen so each item stands on the one before it, not by the owner's numbering.

- [x] **1. A12 — Random keeps going when a track ends**
      Smallest of the five and the one the rest is felt through. A transient track ending should go
      to the next random pick. **The existing comment's reasoning survives** and must be narrowed
      rather than deleted: rolling on into the *playlist* is still the wrong answer. A search result
      is also a transient track and must not be touched — it has its own queue, handled earlier in
      the same function.

- [x] **2. A11 — Random reads ahead**
      Decide two or three picks in advance into `randomHistory` past `randomCursor`, so the
      read-ahead that already exists has something to read. `prefetched` holds one track today;
      several means a small cache with an eviction rule. Going back must never re-roll the past.

- [x] **3. B8 — A history of what was played**
      Persisted, not the in-memory `randomHistory`. What it is *for* is the thing to get right: not
      an audit log, but "that tune two days ago, what was it". Needs a decision on how much is kept
      and whether it is browsable as a queue — decide the implementation, and if the **UI** turns
      out to need the owner, write it down and move on (rule 4).

- [x] **4. B2 — Jump to a tune's neighbours** *(catalogue tracks; local files stated as out, with the reason)*
      From a playing or listed track, open Browse at the place it came from: the author's folder.
      The catalogue path is already in `TrackRef.subtitle` and B3 proved it survives the trip. A
      **local** file's neighbours are its folder, which is a different mechanism — do the catalogue
      case first and say plainly whether the local one is in or out.

- [x] **5. A10 — Sharing, the file and a link**
      Two actions, not one. A local file needs a `FileProvider` copy because a SAF URI cannot be
      handed to another app. **Not every track has a link**: Modland ids are `https://` URLs, ASMA
      ids are `asma://<entry>` and mean nothing off this device — decide and act, do not let it
      fail quietly. If choosing *what a shared link should say* needs the owner, that part stops
      and the rest ships.

## Round 3 — closed 2026-09-02

All five done and merged into `develop`. Two things came out of it that were not in the plan:

- **A latent null in `Catalogue.all`**, found by the first test that reached it. A companion's
  properties are static fields of the outer class, so touching a catalogue object directly built
  the list while that object was still initialising and put a `null` in it for the life of the
  process. Nothing in the app did that yet; the next person to write `Modland.something` would have.
- **With a queue read ahead, the dice and next stop meaning the same thing.** Next means forward;
  the dice means surprise me, so it drops unheard picks and re-rolls. Say if that is wrong.

Still true, and now longer: **nothing in rounds 2 or 3 has been confirmed on a device.**
