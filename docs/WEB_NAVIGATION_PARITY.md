# WEB navigation controls

Reported: **2026-10-30**, C125. Implementation branch: `fix/web-navigation-parity`, from develop
`4da35220cd676ed721d45486e4357202e8e7de0a`. Android production UI at that reference is the contract.

**Integration and publication preparation, 2026-10-30:** branch head `35e4a3c` is merged into
develop as `17794197b49c789f77bc4f5379087815ceab424a` after approval before the physical browser trial. All 830 inputs
match tested source `95ec29a` before staging; only the integration documentation changes.
The original archive retains its identity. Local gh-pages commit
`a8111b83718337f83ce41205f45adf4da3aa1fbd` contains **115 files** from the clean merge source. All **111** PWA
integrity entries and staged asset bytes match; the rebuilt engine is unchanged. Only generated
build identity and worker revision change. PWA revision:
`d0642b445d622899263dbffdd9866c97a9160ce658c93d21b4e416c04212d6de`.

**Push, new public deployment and browser acceptance remain pending.** This document records
local preparation, not live HTTP verification, and does not require another site build.

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

The complete gate on **`95ec29ae8612756a90e153e9a265044ea90d5d9a`** exits **0**: **1266 Native tests / 173 suites**,
zero failures/errors/skips, **1242 seconds**, **1368 ESM page assertions**, **43 navigation
assertions**, **57 engine checks** and every companion. Native unit tests execute; Gradle reports
9 tasks executed and 22 up to date. All three lint analyses execute: **0 errors / 79 warnings**,
**151.4 seconds**. All **830** frozen source/package inputs match after verification and packaging.

WEB trial archive: [protracktor-web-95ec29a-20261030-095926-413517787.tar.gz](../dist/protracktor-web-95ec29a-20261030-095926-413517787.tar.gz),
**1,610,278 bytes**, SHA-256
`eb6e2d5e237c62df412f469cc995e9434a67ecff2d5b9bfc3d9e65510ae37f1a`.
The extracted engine/API/PCM, original notices, launcher/HTTP and all **111** PWA asset fetches
pass host checks. The WASM is unchanged from the previous checked engine; no engine rebuild was
needed. A later documentation-only commit records these results without relabelling the archive.

Host CSS/event checks do not establish physical layout, touch feel, keyboard default activation
or screen-reader behaviour. Integration is approved; browser acceptance and publication remain
pending. No Android application source, schema, release version or engine is changed. Navigation
is the first delivery stage; Windows 11/Firefox Bluetooth evaluation follows publication.

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
