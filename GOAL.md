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

---

## The list

- [ ] **1. B13 + B14 — finding the playing track**
      Tapping the dock's identity row scrolls the playlist to the playing track. Plus the owner's
      follow toggle: a floating button over the list, off by default; tapping it makes the list
      follow playback and hides the button; scrolling by hand turns it off and brings the button
      back. Both traps are written up in `docs/WISHLIST.md` B14 — distinguishing our scrolling from
      the user's, and not animating across three hundred rows.

- [ ] **2. A3 — getting up and down a long list**
      Jump-to-top and jump-to-bottom, appearing when far from either end. The backlog entry
      recommends doing this before a draggable scrollbar; follow that unless building it proves
      otherwise.

- [ ] **3. B3 — the catalogue path in Information**
      A Modland track should read `Modland/Author/name.mod`. Today the dialog shows `subtitle`,
      which is `format · author` for a catalogue track — right for a row, wrong for an information
      panel.

- [ ] **4. A1 — search results: the path, and playing from them**
      Two halves of one row. Show where a result came from, and let it be played before it is added.
      The queue question is already answered in the backlog entry: **the results become the queue
      while you are in them**, the way Random has its own history, and playing from a search must
      not rewrite the playlist. Build that reading.

- [ ] **5. A8 — haptics**
      Four gestures only, listed in the backlog entry, and nothing with a visible result. This comes
      after items 1, 2 and 4 because it attaches to the gestures they build.

- [ ] **6. C2 — does `.sc68` play?**
      A defect line that says "unknown". Take a `.sc68` from Modland (1,775 of them), run it through
      the host probe the way `docs/PLAN_FORMATS.md` §0 describes, and turn the line into an answer
      either way. Cheap, and it removes an "I do not know" from the defect list.

---

## When the list is done

Stop. Do not start `docs/BACKLOG.md` A4 or A5 — both are large enough to deserve a conversation
first. Write what happened, what is unverified, and what you would do next.
