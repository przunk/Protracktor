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
- **`docs/PLAN_FORMATS.md` §1 (libsidplayfp).** It needs Commodore ROMs that cannot be shipped, and
  what to do about that is his call, not an implementation detail.
- **`master`.** See rule 1.
- **`docs/STATUS.md` C3.** Measuring R9 needs his phone and his network share.
- **`docs/BACKLOG.md` A3 (fast scrolling).** It was on this list and the owner pulled it: he wants to
  talk about it first. Note that item 2 builds part of the same machinery, so leave the scrolling
  helpers general enough not to prejudge A3 — but do not build A3.

---

## The list

- [x] **1. B1 + A1 — search: play a result, and see where it came from**
      The owner has asked for this three times, which is what puts it first.

      Two halves of one row and they are done together because they are the same row: a result must
      be playable **before** it is added, and it must say where it came from. The queue question is
      already answered in `docs/BACKLOG.md` A1 — **the results become the queue while you are in
      them**, the way Random has its own history — and playing from a search must not rewrite the
      playlist. Build that reading.

- [x] **2. B13 + B14 — finding the playing track**
      Tapping the dock's identity row scrolls the playlist to the playing track. Plus the owner's
      follow toggle: a floating button over the list, off by default; tapping it makes the list
      follow playback and hides the button; scrolling by hand turns it off and brings the button
      back. Both traps are written up in `docs/WISHLIST.md` B14 — distinguishing our scrolling from
      the user's, and not animating across three hundred rows.

- [x] **3. B3 — the catalogue path in Information** *(landed with item 1: one change to what a source is called served both)*
      A Modland track should read `Modland/Author/name.mod`. Today the dialog shows `subtitle`,
      which is `format · author` for a catalogue track — right for a row, wrong for an information
      panel. Same underlying inconsistency as item 1's second half, so if item 1 settles what a
      source is called, this should follow it rather than invent a second answer.

- [x] **4. A8 — haptics** *(three of the four gestures; the fourth belongs to A4, which does not exist yet)*
      Four gestures only, listed in the backlog entry, and nothing with a visible result. Last
      of the building items because it attaches to gestures items 1 and 2 create.

- [ ] **5. C2 — does `.sc68` play?**
      A defect line that says "unknown". Take a `.sc68` from Modland (1,775 of them), run it through
      the host probe the way `docs/PLAN_FORMATS.md` §0 describes, and turn the line into an answer
      either way. Cheap, and it removes an "I do not know" from the defect list.

## When the list is done

Stop. Do not start `docs/BACKLOG.md` A4 or A5 — both are large enough to deserve a conversation
first. Write what happened, what is unverified, and what you would do next.
