# WEB navigation controls

Reported: **2026-10-30**, C125. Implementation branch: `fix/web-navigation-parity`, from develop
`4da35220cd676ed721d45486e4357202e8e7de0a`. Android production UI at that reference is the contract.

## Common controls

| View | Android contract | WEB change |
| --- | --- | --- |
| Random | `ProtracktorApp.leaveRandom`: Back returns to Browse; `PlaylistReturnAction` returns directly to the selected playlist | Separate Back and Playlist actions in a destination bar; both preserve playback |
| Random heading | `RandomHeader`/`RandomDice`: one 56 dp dice target, double tap and one accessible activation, hint below, Filter at the right | One 56 px dice target with accessible name/hint; no separate New list pill or duplicate decorative dice |
| Browse root | `leaveBrowse`: Back exits to the underlying playlist; explicit Playlist exit remains available | Back is available at root, with a separate Playlist action |
| History/Search/Saved places/catalogues/author | Back follows navigation/selection/scope; Playlist leaves the entire browser for the selected editor | Shared Playlist exit replaces Close, including author detours |
| Queue | Back dismisses Queue; Help and Clear queue keep their distinct meanings | Back arrow/caption and Clear queue caption, in both languages |
| Playlist | `PlaylistTopBar` is visible on the playlist, not other full-screen destinations | Playlist-only toolbar is replaced by the destination's controls; WEB Pair/Paste remain available from the playlist |
| Dialogs/Now Playing | Modal dismissal is distinct from leaving Browse | Keep existing modal Close actions and dock expansion |

Android references: `ui/ProtracktorApp.kt`, `LabelledAction.kt` (`PlaylistReturnAction`),
`RandomScreen.kt`, `RandomDice.kt`, `QueueScreen.kt` and both `values*/strings.xml` resources.
`BrowseRootNavigationTest` exercises the real Back actions for playing/paused Random and
History/Search/folder contexts, and verifies playback ownership and position survive navigation.
WEB retains these distinctions rather than changing only the caption of an existing Browse action.

## Duplicate dice mechanism

`sessionicon` is an SVG element. Assigning `.hidden` creates a JavaScript property without
reflecting the HTML hidden attribute; the decorative SVG therefore remained rendered beside the
real dice action. The earlier check only inspected that property and could not detect the defect.
The presenter now toggles the actual attribute, and the regression guard checks both its presence
and computed display. The interactive dice retains the existing double-pointer gesture,
keyboard/accessibility activation, drag/cancel handling and reduced-motion policy. Its icon-only
shape and visible explanatory hint intentionally follow the accepted Android RandomDice control.

## Verification

Five new production-ESM assertions fail before correction. After correction, **43 focused page
assertions**, all **9 dice gesture cases** and **1368 full ESM page assertions** pass. The focused
page test uses actual buttons for Back/Playlist, verifies all common Browse destinations, compares
playback context/schedule/current-track/command ownership, checks focus restoration and changes
EN/PL in place. Existing Random/source-return guards now use Back for their navigation scenario;
Playlist has separate direct-editor assertions.

The complete project gate/lint is pending at this implementation checkpoint. Host CSS/event
checks do not establish physical layout, touch feel, keyboard default activation or screen-reader
behaviour. Browser acceptance and integration remain pending; no Android application source,
schema, release version, engine or public deployment is changed.

## Browser trial

- In Random, check one dice target, double-click/double-tap reroll and the hint; a single pointer
  click must not replace the list. Check the focus ring and accessible activation.
- While Random plays or is paused, try Playlist, then Browse → Random → Back → Back. Playback and
  the remembered list must survive; Playlist goes directly to the selected editor.
- From History, Search, Saved places, catalogues and an author detour, compare Back versus
  Playlist. Back keeps its existing scope/selection/hierarchy; Playlist exits to the editor.
- Open Queue over Browse and Random. Back restores the producing view; inspect Help and Clear
  queue labels. Try EN/PL, both themes, narrow/wide windows and large text.

All of these physical-browser scenarios remain **unchecked**.
