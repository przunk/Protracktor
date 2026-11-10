# Music alarms: adding music, a floating New, and the screens around them

Analysis written 2026-11-10, from the code on develop `8d082187` (1.8.0) and the B57 screens
rendered at 360 dp. Asked for the same day: add tunes to an alarm from the alarm's own editor, a
floating button for a new alarm at the bottom right, and the alarm screens made better to use in
general. The work is B62 in `docs/WISHLIST.md`; this document is why it is shaped as it is.

## What is there now

The alarms live in their own activity (`AlarmsActivity`), reached from Browse. Two tabs:

- **Alarms**: one card per alarm with the time and a switch, then four labelled lines (*Name ·*,
  *Repeat ·*, *Selected tunes: n*, *Status ·*), then three full-size buttons: *Edit alarm*, *Test
  alarm*, *Delete*. A card is about 315 dp tall at 360 dp, so two alarms fill the screen.
- **Music**: one card per prepared tune: title, artist, platform, year, the excerpt length, *Test*
  and *Delete*.

The header holds *Back*, the title, *Info* and *New alarm*. The editor holds the time, an optional
name, seven day buttons and the list of prepared tunes to tick, with *Save* in its header.

Music reaches an alarm one way only: **Use for alarms** in a tune's menu in the player (playlist,
Browse, Random). That renders a 60-second excerpt (`AlarmMusicPreparer`) into the alarm library.
The editor then lists the library, as *Choose prepared music*.

## What does not work well

1. **Music cannot be added where it is needed.** Setting up an alarm means leaving the alarms,
   finding each tune in the player, choosing *Use for alarms*, waiting for the toast, and coming
   back to tick it. The editor says *Choose prepared music* and offers nothing when the library is
   empty except a sentence about the player. That is the request, and the largest problem.
2. **The main action is one of four in a crowded header.** *New alarm* sits beside *Info* and
   *Back* at the top, the farthest place from a thumb. It is the one action the list exists for.
3. **When the next alarm rings is hidden in Info.** The list shows each alarm's own status, but
   the one answer people open an alarm clock for, *when will it wake me*, is only in a dialog.
4. **The cards say every field's name.** *Name · Morning music*, *Repeat · Mon · Tue · Wed · Thu
   · Fri*, *Status · Armed · Wed · 7:30 AM*: the labels repeat on every card and push the values
   aside. Five weekdays read as a list, not as *Weekdays*. *Selected tunes: 1* says how many but
   not which.
5. **Three full-size buttons on every card** make the list long and the cards heavy. *Edit* is
   also what tapping the time already does.
6. **A disabled alarm looks like an enabled one** except for its switch and status line.
7. **The editor has the same title for a new alarm and an old one** (*Edit alarm*).
8. **Save is greyed out without saying why** when no playable tune is ticked. The project's rule
   is that a control that cannot be pressed says how to make it pressable (workspace §7, "Silence
   is a bug").
9. **Back from the editor throws away changes without asking**, even after the time, the days and
   the music were changed.
10. **The Music tab does not say what each tune is used by**, so deleting one is a guess about
    which alarms lose it.

## What changes

### Add music from the editor (and from the Music tab)

- The editor's music section becomes **Music**, with an **Add music** action (icon and label) at
  its top. The Music tab gets the same action as its floating button.
- **Add music** opens a picker inside the alarms activity, not the player: *Back*, the title, a
  search field, and two sources to browse, **Playlists** (each saved playlist, opened to its tunes)
  and **History** (most recent first). Typing searches the playlists and the catalogues held on the
  phone together, by the search's usual rule (every word, title or author).
- Each tune has **Add** (icon and label). Pressing it queues the tune; the queue is prepared one at
  a time, because preparation renders audio and the preparer runs one job at once. The row shows
  waiting, preparing, added or failed, in words. A tune already in the library shows *Added* and
  adding it again only refreshes its facts, as *Use for alarms* does now.
- **What is added from the editor is ticked in the alarm being edited.** Coming back from the
  picker, the new tunes are in the list and selected; the alarm still needs *Save*.
- The library's limits stay (64 tunes, 128 MB); reaching them is said, not silent.

**How, within the existing architecture.** The player must not reach into the alarm package (the
`AlarmBoundaryTest` rule); the alarm package may use the player's data. So the picker reads the
existing stores (`LibraryStore`, `HistoryStore`, `CatalogueStore`) through one small source class
inside `alarms/`, and preparation goes through the existing door: the player's loader (file bytes,
companions, metadata, which only the player can read) and `MusicAlarms.prepare`, which now hands
back the prepared tune so the editor can tick it. Nothing is added to the wake-up path: an alarm
still rings from its prepared excerpt with no decoder, network or music database.

Using the player's loader from the alarms screen starts the player's controller if it is not
running yet (it normally is: the alarms are opened from Browse). That only loads state; it plays
nothing.

### A floating button for the main action

- **Alarms tab:** an extended floating button at the bottom right, **New alarm**, icon and label.
  The header keeps *Back*, the title and *Info*.
- **Music tab:** the same place, **Add music**.
- The list keeps room at its end, so the button never covers the last card's actions.
- When no more alarms can be added (32), the button stays and says so when pressed, rather than
  disappearing.

### The list

- **The next alarm at the top**: *Next alarm Wednesday 7:30 AM, in 9 h 12 min*, or why nothing is
  armed (no alarm on, access missing). The same text stays in Info.
- **Cards say values, not field names**: the time and the switch; the name, if any, as a title;
  the days as *Weekdays*, *Every day*, *Weekends*, *Once* or the short day names; the music by name
  (*Lotus 3 · Space ninja*, *+2 more*); the status in words with its icon.
- **The whole card opens the editor.** The actions become *Test* and *Delete*, in the smaller
  row the top bar uses; *Edit* goes, because the card is it.
- **A disabled alarm is dimmed**, and says *Off*.

### The editor

- **New alarm** or **Edit alarm** as the title, by what it is.
- **When it will ring**, under the time: *Rings tomorrow at 7:30 AM*, recomputed as the time and
  days change.
- **Why Save cannot be pressed**, next to the music, while no playable tune is ticked.
- **Back with changes asks**: *Discard changes?* with *Discard* and *Keep editing* (a dialog's
  confirm and cancel, which the icon rule exempts).
- **Music** instead of *Choose prepared music*; the count and the *Add music* action at its top.

### The Music tab

- **Used by**: each tune says how many alarms use it, and deleting one that is in use says which
  alarms will lose it before it goes.

## Not changed

- The ringing screen and the challenge (B57's phone checklist is still open for them).
- Scheduling, storage, the ledger format (JSON v1) and the limits. No database schema change.
- *Use for alarms* in the player stays; the picker is a second door, not a replacement.
- The page (WEB) has no alarms; nothing to mirror there.

## Open, to be decided on the phone

- Whether *Edit* should stay on the card as a visible action as well as the card itself.
- Whether the day buttons fit one row at 360 dp in Polish (the short names reach six letters:
  *niedz.*); they stay in two rows until that is looked at.
- Whether searching the catalogues from the picker is wanted, or Playlists and History are enough.
