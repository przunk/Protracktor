# WEB parity browser trial

Prepared: **2026-10-30**. Use the candidate source, archive and checksums recorded in
[STATUS](STATUS.md#web-feature-implementation). This checklist does not
declare a browser trial complete. Record browser/version, OS/device, tab or installed PWA,
source SHA, language/theme and the outcome of each scenario.

Host evidence uses Linux x64, Node 20.20.0, jsdom 25.0.1, fake-indexeddb 6.2.5 and the actual
WASM engine, with browser output/transport adapters. Native comparisons use the accepted Android
reference `c8209b1ab79976ec28d27e1da447055756c7ca29` and existing JDK17. Automated run results are
recorded separately in STATUS; host execution does not establish appearance, audible quality,
touch, storage behaviour or OS routing in a physical browser.

The technical candidate is source **abd505a11f389509e92dfab6ad3f496d0364fa1a** and
[protracktor-web-abd505a-20261030-004338-244872936.tar.gz](../dist/protracktor-web-abd505a-20261030-004338-244872936.tar.gz) (**1,610,081 bytes**), SHA-256
`88cd7b0ab3068fc69cd2f830c08f1805a089d7dde76723a1bb948b3e19f36644`.
Later documentation commits do not identify another tested application.

## Environments to record

| Environment | State |
| --- | --- |
| Desktop Chromium/Firefox, mouse and keyboard | Unchecked |
| Android browser and installed PWA, touch and headset | Unchecked |
| Safari/iOS tab and installed PWA | Unchecked |
| Narrow/wide viewport, EN/PL, Light/Dark/System, large text/desktop zoom | Unchecked |

Use a separate browser profile/site for destructive Clear and upgrade scenarios. Serve the
extracted archive through its bundled `run.sh`/Node server. HTTPS or localhost is required for
PWA installation; plain HTTP on a LAN is not an equivalent installation environment.

## Playback, lists and discovery

- [ ] **W01:** Play playlist A, then view B, Browse, Search and History. Audio/position and
  Next/Previous stay with A. Editing/saving/discarding B affects only B; selecting a track in B
  deliberately transfers playback. Selecting an uncached track silences the preceding source
  while downloading; merely viewing a list keeps it playing.
- [ ] **W02:** Add several tracks from different views, including the same track twice. Queue
  inserts them FIFO after the current source row; each manual occurrence disappears after one
  play. Previous returns to the source anchor, not a consumed request. Test Repeat Off/All/One,
  shuffle, a failed file and short versus held Next with subsongs.
- [ ] **W02:** In Queue, tap to play, hold/right-click for Information/actions, swipe right to
  append and left to omit/remove. Source omission does not edit the saved playlist. Undo restores
  the intended occurrence; stale Undo explains refusal. Reorder only pending manual rows by drag,
  arrows or menu. Vertical scrolling must not enqueue; a swipe's trailing click must not play.
- [ ] **W02:** Clear retains the actual current track, empties pending/source rows and permits a
  fresh manual queue. The retained row disappears after consumption. Check Undo before and after
  playback changes, departure animation, reduced motion and current-row following without flashes.
- [ ] **W03:** From Queue return to a playlist, Random, filtered History, Search query/scope and
  nested author/game folder. Playback is unchanged. Missing sources and dirty destinations have
  explanations; old route snapshots must not restore obsolete cache/download state.
- [ ] **W04/W05:** First Random entry starts its first draw; later entries reveal the retained
  record without taking playback from another source. Double-tap the dice or use its accessible
  alternative to reroll. Change the single platform/Favourites filter while playing an older row:
  Next traverses heard history before newly filtered draws. Download/delete indexes while the
  filter is open and check loading, unavailable and refreshed states.
- [ ] **W06:** A new track qualifies after five seconds of progressing playback or a natural end
  under five seconds. Pause/loading/failed start/seeking to the end must not qualify it. Check
  subsong/restart boundaries, discreet Browse/Random marks, no mark in the playlist editor and
  Clear History while a write is pending. Heard tracks remain eligible for Random.
- [ ] **W07:** Bookmark authors/game folders from supported catalogues. Saved places opens their
  exact paths; remove offers one-shot Undo. Delete the relevant index, then restore it: bookmarks
  survive and unavailable actions explain preparation. Check equal icon sizes and both themes.
- [ ] **W08:** Learn a decoded title in playback, then inspect Browse/Random/Queue. Title updates
  preserve row order, scroll, duplicate occurrences and original filename/decoder identity.
  Restart the page, then Clear History: retained titles survive restart and Clear removes overlays.
- [ ] **W09:** Submit distinct/case-duplicate/space-padded searches. Reopen the field: at most
  three of twenty retained queries match case-insensitive startsWith. Typing alone is not saved;
  choosing a suggestion immediately searches the current scope without reopening the popup.
- [ ] **W10:** Search all History by title/author/filename, including a match beyond page one.
  Check debounce, literal punctuation, counts, paging, empty/error/retry, changing query while a
  read is pending, fresh entry at the top and Clear during a pending read. History replay does not
  reorder the retained occurrence.
- [ ] **W11:** Search held catalogues, platforms and all saved browser playlists. Check short/
  empty phrases, duplicate IDs, result order/cap and unavailable-scope explanations. Returning
  from Queue restores the producing query/scope, not an unsubmitted draft. No local-folder scope.
- [ ] **W12:** Reorder playlist rows by drag, arrows and menu; check live crossings, cancellation,
  scroll and Save/Discard. A legacy playlist with duplicate URLs must retain the correct playing
  occurrence when another row is deleted/restored. From the phone remains protected.
- [ ] **W12:** Export/import an M3U8 playlist, including an Android ASMA reference, duplicate URLs
  and unavailable local entries. Preserve first-offering order, report found/unresolved counts
  and handle cancel/read/storage/download refusal. Export uses the intended saved list or draft
  without selecting it or disturbing independent playback.
- [ ] **W13:** Select Browse/Search/History rows by long press/menu/checkbox. Select all/none,
  batch Add and Share use that result snapshot and source order. Back first exits selection;
  checkbox appearance and title/cache updates retain row geometry and focus. Tab to a main-list
  title and use Enter/Space to play it. Choose another saved playlist with Enter: audio keeps
  its old source, focus enters the viewed list and Space on its title transfers playback.

## Audio, presentation and storage

- [ ] **W14:** Original is default; Narrow and Mono apply immediately and survive restart. Check
  hard-panned stereo, pause/seek, subsongs and preserved source/manual requests. Export remains raw.
- [ ] **W15:** Normalization defaults Off and survives explicit changes. Compare quiet and loud
  tracks, first versus repeated play, short/silent files, live stereo changes, seeking, subsongs
  and cancellation while preparing. Expect the fixed -18 LUFS policy subject to boost/peak caps;
  do not expect every track to reach the target. Export remains raw and there is no target slider.
- [ ] **W16:** Try Battle of honour plus 1/5/256-tune examples. Title/Add stay above metadata/
  full Tune counter, Add never shifts horizontally, and Queue stays outside the card. Check large
  text, desktop zoom and narrow/wide layouts; counter/hidden Add must not squeeze or overlap rows.
- [ ] **W17:** Scroll through catalogue/author/game levels, descend and Back: return to the departed
  row. Fresh History opens at its top. Subsongs follow advancement only while the playing region
  is watched; browsing a distant region must not snap back.
- [ ] **W18:** Restore a saved playlist/position and shuffle/repeat/play-all settings without
  autoplay. Settings and Now Playing subsong controls stay synchronized. A later user choice wins
  over a slow startup read; denied persistence explains that the live choice was not saved.
- [ ] **W19:** Confirm ordinary upcoming-track preparation, stale-source cancellation and held
  bytes. Folder downloads default to Wi-Fi only and remain paused when metering authority is
  unknown. Explicit Always enables up to three ordered transfers within the folder budget; Off,
  Back and hiding Browse stop new work without cancelling another caller's shared transfer.
- [ ] **W20:** Play a fetched track, reload and go offline: its retained copy and marks work.
  Check storage accounting, LRU pressure, in-use/read-ahead protection, quota/refusal feedback
  and Clear cache. Playlists, History, bookmarks, indexes and PWA shell survive cache Clear.
  Phone-transferred bytes remain session-only and must not silently become persistent music.
- [ ] **W21:** Explicitly download complete ASMA, reload and play it offline. Index-only state is
  distinct from held music. Check Stop/failure/retry, deletion and another tab's archive replacement;
  playlist/History URLs survive. Startup/upgrade does not download the archive automatically.
- [ ] **W22:** Select several approved datasets. Check sizes, progress, Stop and retry; completed
  steps and an older valid dataset survive later failures. No disabled catalogue/replay routine
  should appear as a required download. Declining a picker changes no data.
- [ ] **W23:** Open a retained legacy Modland `- unknown` URL, including a nested format. Only a
  relevant 404 may try recovery; the saved ID/encoding stays unchanged. Distinguish missing files
  from connection failures and refuse ambiguous paths.
- [ ] **W24:** Switch EN/PL/System during playback, download, selection, Search and open Queue.
  Text/accessibility/status labels change in place; decoder, position, pending FIFO, selected rows
  and input focus remain. Theme changes retain live state. The established C92 pinch/viewport
  policy is retained; desktop zoom/text scaling still needs inspection.

## System controls, upgrades and deployment

- [ ] **W25/C104:** Record the exact browser/OS before testing hardware keys, lock-screen controls
  and background playback. Where the browser routes Media Session, check explicit repeated Play/
  Pause, Next/Previous, Stop, known/unknown duration, seeking, subsongs, duplicate requests and
  shared title/subtitle/artwork. A previous pointer hold must not swallow a later media action.
  Unsupported OS routing remains a recorded limitation, not a passed app scenario. No custom heart.
- [ ] **Persistence:** Upgrade a test profile containing version-4 data; playlists, History,
  indexes and settings survive coordinated v5/v6 upgrades. Check another open tab, blocked upgrade,
  unavailable/quota-limited storage and a newer-schema refusal without automatic deletion.
- [ ] **PWA:** Install from HTTPS/localhost, close all tabs and reopen offline. With playback
  running, deploy a second complete test version: it must wait for old tabs to close. Missing or
  HTTP-200 stale assets must fail installation while preserving the old offline shell. Check both
  `/` and `/Protracktor/`, and restore the consistent version before reopening.
- [ ] **Existing features/exclusions:** Check direct supported archive links, original/share-as-
  audio export, labels/licences/privacy and self-hosted phone pairing. GitHub Pages has no pairing
  relay. No AMP, UnExoticA/Mod Archive catalogue, UADE process, sync account, overlay, native service
  or Android permission UI has been introduced by this candidate.

Keep C104 open unless the relevant real-device/browser routing is observed. The
[implementation ledger](WEB_IMPLEMENTATION.md#w25-platform-boundary-and-c104) records the diagnosis
and retry; it does not claim a released-browser compatibility matrix.
