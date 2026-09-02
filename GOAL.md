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

- [ ] **5. HVSC — at least its song lengths**
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
