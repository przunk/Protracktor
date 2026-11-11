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
