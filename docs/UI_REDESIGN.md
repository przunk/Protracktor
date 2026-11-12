# Redesign: a collapsible dock, bottom navigation, one header row

Agreed 2026-11-11. Testers point at Spotify; the app keeps what it is for me: **the playlist is the
home screen**, music is found and added to it, and the queue carries listening along the way.
From Spotify it takes ways of handling (a dock that collapses, navigation at the bottom), not its
structure (a catalogue to discover in), which this app neither has nor needs.

Mock-ups, drawn from the real playlist and dock: `RedesignMockupsTest` on `ui/redesign`, pictures
in `dist/evidence/ui-redesign-mockups/` (`ui-1-collapsed`, `ui-2-expanded`, `ui-3-more`,
`ui-4-selecting`; `redmi-pl-light-playlist` is the screen today).

## Why

On a 360 × 800 dp screen today two rows at the top (the playlist chooser with Search; Browse, WEB
and Settings) take about 120 dp and the dock about 190 dp, which leaves the list about 60 % of the
screen. Most of those buttons go somewhere rather than act on the list.

Measured on the mock-ups (800 dp tall, without the system bars):

| | list today | after, dock collapsed | after, dock expanded |
| --- | --- | --- | --- |
| height | 476 dp | 561 dp (+85) | 463 dp (−13) |

The collapsed dock is 108 dp in the mock-up, not the 64 dp first estimated: the grip and a seek
band easy to hold cost about 44 dp. Putting the seek band on the dock's top edge, under the grip,
would bring it near 80 dp; that is for E2, on the phone.

## The layout, top to bottom

1. **One header row**: the playlist (name, count, ▾ to choose) and **Filter**, which narrows this
   playlist (today's playlist Search, renamed so the word Search means one thing). Save and
   Discard appear here while editing.
2. **The list**, unchanged.
3. **The dock, in two states**, with a grip at its top:
   - **expanded** (the default): today's dock, Now Playing from its card as now;
   - **collapsed**: the tune and author, a **seek bar that still seeks** but drawn subtly (a 3 dp
     track) with a thumb and a 32 dp band that are easy to hold, Play/Pause, Queue, and **Add** at
     the right edge when the tune is not in the playlist. Swiping the collapsed dock left or right
     moves to the next or previous tune;
   - tapping the grip or the collapsed dock, or dragging up, expands it with an animation; dragging
     down collapses it. The state is remembered.
4. **Bottom navigation**, small icons with small labels (the icon-and-label rule):
   **Playlist · Browse · Search · Random · More**.
   - *Search* searches everything (Browse → Search today, three taps deep);
   - *Random* stays on top because I use it often;
   - *More* is a sheet: History, Alarms, Saved places, WEB, Settings, Feedback.
   The top row's Browse, WEB and Settings go; so does the *Playlist* way back on those screens,
   which the navigation now is. Back steps within a tab, then returns to Playlist, then leaves.

## Details decided with it

- **Selecting rows**: the selection bar (count, Cancel, Select all, Add to, Share) takes the
  navigation's place while selecting, so the list keeps its room.
- **Gestures**: rows swipe to the queue and the collapsed dock swipes between tunes; they are
  separate areas. The navigation keeps the dock away from the system's swipe-up edge.
- **Accessibility**: a swipe is never the only way. The collapsed dock offers Next and Previous to
  TalkBack as actions; the expanded dock has the buttons.
- **Not now**: collapsing the dock by itself while the list scrolls (predictability first);
  floating controls (B54) are decided after the prototype, which may make them unnecessary.

## The risk that is not on screen

The main screen (`ProtracktorApp.kt`, about 1,000 lines) chooses what is shown with separate flags
(`showBrowse`, `showQueue`, `showRandom`, `showSettings`, …); Back's order between them has been a
source of defects (B60's Back while the queue covered Browse). Bottom navigation on top of those
flags would multiply them. So the first stage replaces them with one destination model, with no
visible change, under the existing shell tests.

## How the work runs

`develop` → integration branch `ui/redesign` → one branch per stage, each merged into
`ui/redesign` after a phone check of its APK → `ui/redesign` into `develop` at the end. `develop`
is merged into `ui/redesign` from time to time (a merge, not a rebase), so fixes released meanwhile
do not drift away from the redesign.

| stage | what | visible |
| --- | --- | --- |
| E0 | one destination model and Back, replacing the flags | no |
| E1 | bottom navigation and More; the top row goes | yes |
| E2 | the collapsible dock: collapsed form, animation, gestures, remembered state | yes |
| E3 | the header row with Filter; the selection bar in the navigation's place; editing | yes |
| E4 | finishing: B54's fate, haptics, screenshots, testers' notes | yes |
| later | the page (WEB) | — |

## Decided 2026-11-11

1. Navigation: Playlist · Browse · Search · Random · More.
2. The dock starts expanded; the state chosen is remembered.
3. The collapsed dock's seek bar seeks; subtle, with a handle easy to use.
4. *Filter* is the playlist's own search.
5. Mock-ups before code: drawn, above.

## Built, 2026-11-11 (unattended, to be checked on the phone)

All four stages on `ui/redesign` (E0 merged after its phone check; E1–E4 merged into it without
one, as agreed for this run; nothing merged into `develop`):

- **E0** `ShellState`: one state for where the shell is. **E1** turned it into a stack, so Back is
  the place before (Browse → Random → an author → Back ×3 → the playlist); a tab replaces the
  stack. The navigation bar (Playlist · Browse · Search · Random · More) under the dock, More as a
  sheet (History, Alarms, Saved places, WEB with hold to pair again, Settings). The top row's
  Browse, WEB and Settings and the Playlist way back are gone; Search, History and Saved places
  opened from the bar leave at their own top; Browse's bar says which of them it is.
- The playlist's search and Browse's narrowing are **Filter** (EN) / **Filtruj** (PL). Discard and
  Save sit in the header row while editing, sharing its width with Filter: a row of their own,
  appearing only then, pushed the list down under a finger dragging a row.
- **E2** the dock collapses: a grip (tap or drag), the collapsed form with the tune, author and
  time, a thinner seek bar held in the same 48 dp band, Play/Pause, Queue and + at the right edge;
  tap the tune or drag up to expand, swipe it for the next or previous tune (also TalkBack actions).
  Expanded by default, remembered. Its height animates.
- **E3** while rows are selected (playlist, Random, Browse) the navigation bar steps aside.

Measured on the screenshots (800 dp tall): the list is 476 dp in 1.9.0, about 546 dp with the
dock collapsed and about 462 dp expanded. The collapsed dock is about 130 dp: the grip and the
seek band easy to hold cost more than the 108 dp of the mock-up.

**Left for the phone check, not decided:**
- Random's own **Filter** (what to draw from) and the playlist's **Filter** (narrow the list) now
  share a word with two meanings.
- Browse's start still lists Random, Search and Alarms, which the bar and More now also reach.
- Floating controls (B54) are untouched (decided after the phone check: they stay).

## After the first phone check, 2026-11-11 (APK 1548)

Changed on `ui/e5-feedback`:
- **No grip.** Dragging the dock down to make it smaller is expected without being drawn, and the
  grip cost a row of room. Drag down on the full dock collapses it, drag up on the collapsed dock
  (or a tap on its tune) expands it; TalkBack has both as an action on the dock.
- **The collapsed dock after my sketch** (`user/Screenshot_20261111-212205.png`): the seek bar along
  its top, thin, with a 32 dp band to take; under it the tune and the author, **scrolling when they
  do not fit**, as on the full dock; + when the tune is not in the playlist; a smaller Play/Pause;
  Queue as its icon alone (my choice for this form, an exception to the icon-and-label rule, named
  for TalkBack). No times on it. About 88 dp: the list is about 588 dp with it, 482 dp with the full
  dock, against 476 dp in 1.9.0.
- **History and Saved places belong to Browse**, where they are found among the music; More holds
  Alarms, WEB and Settings.
- **The crash on a swipe back** was C140, a seek running while the tune was left; fixed on its own
  branch and merged into `ui/redesign`.

Decided: **floating controls (B54) stay.** They are how the player is reached outside the app, over
a map in picture-in-picture and another app in front; the collapsed dock does not replace that.

## Round 2, after the second phone check (APK 1554), 2026-11-11

On `ui/e6-round2`:
- **Search again, with the magnifier**, for the playlist and a level of Browse: the words are typed
  live, which is searching, and Filter suggested a chooser of filters. Random keeps Filter, since
  there it sets limits on what is drawn.
- **The collapsed dock's swipes take its whole row**, buttons included: a swipe that started on
  Play did nothing. A tap on a button is still a press; the seek bar stays out of it.
- **The collapsed seek bar lies on the dock's top edge**, after my sketch: a 3 dp line where the list
  ends, a ▲ under it whose tip marks the position, and a 24 dp band under the line that answers to
  a tap (seek there) or a sideways drag (seek on letting go). A drag up or down passes to the dock.
  Nothing sticks out over the list. The collapsed dock is 72 dp (88 dp before).
- **The full dock's dot sat 1 dp above its line** (the collapsed one's old dot, 2 dp). Measured on
  screenshots: the slider places its thumb in a 16 dp slot and a smaller dot drawn bare sat at that
  slot's top. The dot is now centred in the slot; measured again, line and dot share a centre.
- **Two places on the bar are mine to choose** (Settings → Navigation bar): Playlist · Browse · ○ · ○
  · More, Search and Random until chosen otherwise. Choices: Search, Random, On this phone, Saved
  places, Online catalogues, History, Alarms (only in a build with music alarms). Settings shows
  the bar as it will look, the two places outlined and marked *Change*; tapping one lists the
  choices with icons, the current one ticked, the other place's marked as swapping. *Restore
  defaults* puts Search and Random back. A stored choice the build cannot offer falls back to
  that place's default.
  - A place on the bar opens at its top, is marked while it shows (however reached), and Back from
    its top returns to the playlist; deeper, Back steps up first. A place not on the bar marks
    Browse.
  - Alarms on the bar opens the alarms screen and is not a marked tab. More stays Alarms, WEB,
    Settings whatever the bar holds.
  - Online catalogues has the database icon on the bar, since Browse already shows the cloud.

## Round 3, after the phone check of APK 1559, 2026-11-12

Round 2 held on the phone; the full-width Restore defaults under the bar's preview did not fit. On
`ui/e7-settings`:
- **Settings in titled sections, one style:** Playback (stereo mix, volume normalisation, all
  subsongs, the length when nothing knows it), Controls (the navigation bar, floating controls),
  Sharing (the length when shared as audio), Appearance and language (theme, dynamic colour,
  language), then Online catalogues, Storage, Web player, About. The language had been the last row
  of Playback and floating controls sat among the sound settings; Storage's title was drawn without
  the section colour.
- **Restore defaults** is a small action in the navigation bar's title row.
- **B65: the stereo mix is one three-part pill**, on the same control as the theme and the
  language, each part with its icon; the separate buttons and the "Current: …" line went.
- **The length when shared as audio is a slider**, like the length when nothing knows it, over the
  same four lengths (1, 3, 5, 10 minutes), so nothing stored changes.
- **The collapsed dock's content sits centred**: the seek band 19.5 dp (from 24), 8.5 dp under the
  row (from 4); the dock stays 72 dp. Measured on a screenshot: 8.5 dp from the marker to the
  content and from the content to the navigation bar.
- **Queue on the collapsed dock has the full dock's tonal background**, still its icon alone.
  Checked on the phone (APK 1561): Play and Queue sat too close and read as one shape; they are now
  12 dp apart.
- **WEB keeps the navigation bar** as well as the dock: hiding the bar alone took away the way out
  every other place has. More stays marked there; a tab leaves it.
