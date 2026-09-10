<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Random, as a view rather than a scrim

Agreed with the owner on 2026-09-10, in conversation, before any of it was built. **The APK first,
because the view has somewhere to stand there, and the web takes the same shape after** — he asked
for the two to be as alike as they can be, so the design is written once for both.

`docs/PLAN_WEB_LIBRARY.md` S5 holds the web-specific half: what the dice picks from, and the
measurements behind it.

---

## What Random is today, and why it is thin

There is no Random view. There is `AwayScrim` — a translucent sheet that **covers the playlist** and
shows a dice, the words "Playing at random", and a button back. Its own comment says what it is:
*"a mode you can enter but cannot obviously leave is a trap."* It was written as a defence, not as a
design. The playlist is hidden because it would lie — nothing in it is playing — and nothing is put
in its place.

The scope is chosen by **long-pressing** the Random row in Browse. Nothing on screen advertises
this. `a11y_choose_random_scope` exists, so a screen reader announces it and an eye does not.

What the owner already rates as working, and which none of this changes: press the dice and music
plays; the scope can be narrowed; forward and back work; the plus keeps a tune; the file's
information is there; the playlist gets out of the way.

## The view

**Browse → Random opens a list**, and the list is the record of what the dice has given this
session. One track played, one row. Five played, five rows.

- **A heading that says "Playing at random"** — the words already exist, they simply hang on a
  blank today instead of standing over a list.
- **A "Filter" button, icon and label**, opening the same scope bar as today ("Random picks from").
  Beside it, the current scope in words. This replaces the long press: a function nothing on screen
  advertises is a function most people do not have.
- **The scope can be changed mid-session, from this view.** Already supported underneath —
  `chooseRandomScope` discards the picks read ahead under the old scope, precisely so that choosing
  "Amiga" does not play three C64 tunes first.
- **A way back to the playlist in the corner.** The scrim put it in the middle with a label because
  there was nothing else on screen; with a list there, the corner is right.
- **Rows carry the ordinary track actions, including delete.** No reorder — the order is the dice's,
  not yours, so there is nothing to express. Delete stays because *"jak jest to spoko, nie trzeba
  używać; jak nie ma, to nie można używać, co jest gorsze"* — and pruning the record before adding
  the rest to a playlist is a real use.
- **Entering plays.** No second press. The list arrives empty and immediately has one row in it.

## What was decided, and what it cost to decide

**Entering afresh starts a new list.** Not resumed. The argument against — you go to check the
playlist, come back, and the record of the last quarter hour is gone — was heard and answered:
*"odtworzone są w historii, więc nic nie ginie."* Which is the same answer as the next point.

**The Random view is the session's history, not a second thing like it.** *"Widok random z danej
sesji pokazuje… historię sesji random, więc tak, to jest to samo."* One mechanism serves this and
`PLAN_WEB_LIBRARY.md` S6, and the only differences either of us could name are which actions a row
offers — no reorder here — and whatever a future one adds. **Building two would have been waste,
and the plan had two in it.**

**The list is not kept between runs.** It would grow without end and there is a history for that:
*"niech sobie z historii ludzie czytają, ja tego nie potrzebuję."*

**The scope is kept between runs — and only because this design makes it visible.** `RandomScope.kt`
refuses to persist it, and the reason is worth quoting because it is now spent: *"a scope that
outlives the session is an invisible mode, and a dice button that quietly remembers a setting has
stopped being a dice button."* The whole argument rests on invisibility. Put the scope on screen
next to a Filter button and it no longer applies. The doc comment has to change with the code, or it
will be defending a decision that was reversed.

**A second queue, explicitly transient.** The controller holds one queue and a transient track
beside it. A list you can pick from by hand is a second queue, and it goes in as one rather than as
a playlist fed by dice: a playlist drags in unsaved-changes state, saving, renaming, and the "From
the phone" rules the web grew on 2026-09-10. **Random must never be able to be dirty.**

**Read-ahead stays, and stays invisible.** `READ_AHEAD = 3` picks are drawn before they are heard,
because a tune that has not been fetched is a gap between tracks — and more so in a browser, where
every one comes over the network. The list shows what has *played*; the three ahead of it are a
fetching strategy, not a promise, and showing them would turn a record into a schedule.

## Two things to build carefully

**The browser needs the gesture.** Audio needs a user gesture, and Browse → Random is one — but the
tune is played after an asynchronous fetch, and the `AudioContext` has to be resumed **inside** the
click rather than after it. Free on the phone, deliberate on the web.

**There is no ceiling on the list, and one day there will have to be.** `randomHistory` has never had
one because nobody has ever looked at it. A session running for an hour is hundreds of rows held in
memory and unscrollable in practice. *"Na razie brak sufitu nie jest problemem, ale pewnie będzie
kiedyś"* — recorded here as important rather than urgent, and it is the same question for the
history in `PLAN_WEB_LIBRARY.md` S6, since they are the same list.
