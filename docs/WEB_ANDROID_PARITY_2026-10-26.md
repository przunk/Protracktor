<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# WEB and Android feature parity

Date: **2026-10-26**.

The browser player has the core playback features, but trails Android most visibly in playback
context and manual queue handling, search, music discovery and audio processing. This inventory
records the identified gaps, including features introduced between releases 1.0.0 and 1.4.0 and
older Android functionality. It is an analysis, not an implementation plan or a decision to port
every platform-specific behaviour.

## Scope and evidence

- Compared the Android and WEB source on `develop` at
  **`67769b70d16bd32e3c5f5271888996f7d99fd343`**.
- Assigned release versions from the actual `v1.0.0` through `v1.4.0` tags and their changes,
  rather than historical test-APK version names. The 1.4.0 release commit is
  **`5b6936e5389d9e34bd6861e574907bb8becd6015`**.
- The agent performed source analysis. No new browser/device scenario, automated test run,
  application build or deployment was performed for this inventory. Runtime parity is therefore
  **partially checked** through source inspection; untested browser/device behaviour remains
  **unchecked**.
- This describes the inspected source, not the version currently active in an existing browser's
  PWA cache. See [the C121 investigation](WEB_DECODER_FILENAMES.md) for the separate cache report.
- [The earlier WEB parity plan](PLAN_WEB_PARITY.md) is historical. Its early descriptions of
  missing Polish, light theme and other subsequently implemented features are not current gaps.

## Scope selected after the inventory — 2026-10-26

Proceed with all twenty-five portable WEB scopes proposed after this comparison:

- Independent playback context, the complete manual queue and return to its source.
- Remembered Random, explicit dice reroll and platform/Modland Favourites filters.
- Played-before marks, saved places and learned list titles.
- Recent-search suggestions, live History search and catalogue/platform/playlist search scopes.
- Playlist reorder and import/export, and bulk actions in Browse/Search/History.
- Original/Narrow/Mono, optional volume normalization, compact dock, Browse/subsong position
  handling and playback-mode/settings restoration.
- Read-ahead controls, managed music cache and offline marks, complete offline ASMA, grouped
  downloads and recovery of retained Modland URLs.
- Language changes without interrupting playback and browser system playback controls,
  including investigation of C104. This selection reopens the previously deferred WEB-control
  work; it does not establish that the reported device problem is fixed.

Implement independently reviewable feature commits on one task branch from develop. For every
feature, inspect the current Android production code and tests, define corresponding scenarios
and compare WEB results against them. Match functional semantics 1:1; identify browser-specific
limits explicitly rather than silently narrowing the scope. Merge and deployment remain separate
decisions. The execution checklist is maintained in the local, ignored GOAL.md; implementation
has not started by recording this selection.

Keep the Android local-folder scanner, current UADE/process integration, external replay routines
and UADE-only durations outside the scope. Also exclude native Android service/permission/focus/
wallpaper/haptic mechanisms, overlays, a copied native notification with its custom heart, and
accounts/synchronization. A simple browser file picker or drag-and-drop remains undecided.
UnExoticA/The Mod Archive integration, AMP and public GitHub Pages pairing are deferred rather
than permanently rejected; existing individual links and self-hosted pairing remain supported.

## Features introduced between 1.0.0 and 1.4.0

| Android release | Android feature | Missing or partial WEB behaviour |
| --- | --- | --- |
| **1.1.0** | Played-before indication | No discreet mark in Browse or Random, no qualification after five seconds of listening or a natural end, and no corresponding reset through Clear History. A WEB History entry is not an implementation of listening qualification. |
| **1.1.0** | Author and game-folder bookmarks | No saved catalogue places, Saved places view, opening a saved location, bookmark removal or removal Undo. |
| **1.1.0** | Learned titles in Browse and Random | Titles retained in History are not applied to those lists across visits or restarts while preserving row order. WEB learns the title for the player and History, but does not use it to improve these list headings. |
| **1.1.0** | Correctly refreshed Random filters | The actual filters are absent, so WEB also lacks their availability refresh and scope changes that preserve heard history. The filters themselves predate 1.1.0; this release refined their behaviour. |
| **1.2.0** | Original, Narrow and Mono stereo modes | No stereo-mix setting or corresponding playback processing. The WEB volume slider changes level, not channel separation. |
| **1.2.0** | Playlist heart in system playback controls | No notification/media-session action adding the current track to the selected playlist, saving immediately and indicating existing membership. The page's own Add button is a different action. |
| **1.2.0** | Recent-search suggestions | No retained list of twenty unique queries, up to three case-insensitive prefix matches, or immediate scoped search when a suggestion is selected. |
| **1.2.0** | Live History search | No field filtering the entire History by title, author or filename as text changes, including matches outside the current page. |
| **1.3.0** | Independent playback context | The selected playlist editor is not independent of the playback source. WEB still uses older Random/Browse/History sessions; returning to the ordinary playlist or choosing another playlist can end the session and stop playback. |
| **1.3.0** | Manual playback queue | No one-shot manual insertions within the active context. The detailed missing behaviours are listed below. |
| **1.3.0** | Remembered Random view | No return to the previous Random list without rerolling or taking playback away from another source. Re-entering Random starts a new session. The older author-detour return is only a partial equivalent. |
| **1.3.0** | Double-tap dice reroll | No deliberate new-list gesture, dice animation or explanatory gesture hint. |
| **1.4.0** | Return from Queue to its source | No route back to the appropriate playlist, Random, History, search results or folder with the source view restored and playback undisturbed. |
| **1.4.0** | Optional volume normalization | No playback loudness matching, preparation and retention of measurements, or Settings switch. |
| **1.4.0** | Revised compact dock | No equivalent stable Add/subsong layout or current Tune 1 of N indication in the collapsed dock. WEB has subsong controls in Now Playing. |
| **1.4.0** | Recovery of retained Modland URLs | No 404 retry translating a retained unknown-author directory from `- unknown` to `_unknown`, including nested directories, while preserving saved track identity. |

Primary Android sources:
[History presentation](../app/src/main/kotlin/com/przunk/protracktor/player/HistoryPresentation.kt),
[listening qualification](../app/src/main/kotlin/com/przunk/protracktor/player/ListeningSession.kt),
[saved places](../app/src/main/kotlin/com/przunk/protracktor/player/SavedPlaces.kt),
[recent searches](../app/src/main/kotlin/com/przunk/protracktor/player/RecentSearches.kt),
[History filtering](../app/src/main/kotlin/com/przunk/protracktor/player/HistoryFilter.kt),
[Settings](../app/src/main/kotlin/com/przunk/protracktor/ui/SettingsScreen.kt),
[compact dock](../app/src/main/kotlin/com/przunk/protracktor/ui/PlayerDock.kt),
[Modland URL recovery](../app/src/main/kotlin/com/przunk/protracktor/net/Catalogue.kt).

WEB counterparts are in [app.js](../web/src/app.js),
[the controls and Settings markup](../web/src/index.html),
[the audio processor](../web/src/processor.js) and [stored data](../web/src/store.js).

## Playback context and manual queue

The HTML element named `queue` in WEB displays the current playlist or session. Its name does not
mean that it implements the current Android manual queue. WEB lacks:

1. A dedicated view combining source-context rows and manual insertions, accessible independently
   of the screen currently being browsed.
2. Add to queue from a playlist, Browse, Search, History or Random.
3. First-in-first-out manual requests independent of shuffle ordering.
4. Multiple independent requests for the same track.
5. Consumption of a manual insertion after playback and continuation of the source context.
6. Previous returning to the source-context track rather than a consumed manual insertion.
7. Preservation of pending manual requests when the playback context changes.
8. Removal of either manual or source-context occurrences from current playback without editing
   the saved playlist.
9. Clear queue while retaining the currently playing track.
10. Reordering of pending manual requests.
11. Manual-entry identification by a symbol and indentation where applicable.
12. Swipe actions with action backgrounds, long-press information and animated row departure.

These behaviours are distinct from editing a saved playlist. Porting the queue would require
separating the visible playlist from the active playback owner, rather than adding controls to
the existing session list alone.

Sources: [playback context](../app/src/main/kotlin/com/przunk/protracktor/player/ActiveQueue.kt),
[schedule](../app/src/main/kotlin/com/przunk/protracktor/player/PlaybackSchedule.kt),
[Queue view](../app/src/main/kotlin/com/przunk/protracktor/ui/QueueScreen.kt),
[source navigation](../app/src/main/kotlin/com/przunk/protracktor/player/QueueSourceNavigation.kt),
[WEB sessions and transport](../web/src/app.js).

## Older Android features and remaining differences

| Area | Missing or partial WEB functionality |
| --- | --- |
| **Random platform selection** | No platform-scoped draws. Everything is the only implemented scope; Filter displays an explanation of this limitation. |
| **Random Modland favourites** | No download/use of Modland's curated favourites as a draw pool. These are the archive's favourites, not playlist membership or the notification heart. |
| **Search scope** | No Everywhere, Local, selected-catalogue or platform scope controls. WEB searches all catalogue indexes it holds. |
| **Search in the personal library** | No search across a scanned local library and tracks in all saved playlists. WEB searches catalogue indexes. |
| **Short or empty search** | WEB requires two characters. Android can query locally held data without that restriction; a live The Mod Archive query is a separate case requiring text. |
| **Local files and folders** | No independent file/folder picker and local hierarchy browsing. Paired handoff can supply file bytes, but is not a local-library entry point. Dragging local files into WEB remains unimplemented. |
| **Local-library indexing** | No persistent scanned library, decoder-based file identification, incremental/rescanning workflow, stale-index indication, scan progress or Stop scan. |
| **Playlist row ordering** | No manual drag reordering. WEB can add and remove rows, but shuffle ordering is not editable playlist order. |
| **Playlist file transfer** | No file import or `.m3u8` export, including resolution of local files by retained identity or filename/size. A shared WEB link is a different transfer format. |
| **Bulk actions in Browse** | No multiple selection, Select all, or batch addition/sharing of Browse, Search and History rows. WEB selection exists on the main list, not these Browse rows. |
| **UnExoticA** | No catalogue index, soundtrack/author browsing, LHA member playback or archive caching. The Android integration is enabled. |
| **The Mod Archive** | No native live search or inclusion in combined search results. WEB recognizes individual track links, which is not catalogue integration. |
| **Persistent music cache** | No application-managed fetched-track cache with a 512 MiB budget, least-recently-used eviction and protection for files in use. Browser HTTP caching is not the same policy. |
| **Offline ASMA playback** | Android retains the complete archive. WEB keeps an index and fetches individual tracks from ASMA. |
| **Playback read-ahead** | No equivalent preparation of upcoming tracks outside Random or prefetch of a browsed folder. Random read-ahead already exists in WEB. |
| **Prefetch network policy** | No Wi-Fi only, Always or Off selection for folder prefetch. |
| **Offline-availability marks** | No row indicator corresponding to the phone-with-a-tick mark for music already available locally. |
| **Grouped downloads** | No selection sheet for several datasets, combined size or Stop for the batch. WEB starts individual downloads. |
| **Playback-mode restoration** | Shuffle and repeat reset to their defaults after a reload. WEB restores a saved playlist and selected index, but not these modes. |
| **Browse position restoration** | No explicit per-level position retention and restoration after Back, equivalent to Android's BrowseScroll. Automatic following of the currently playing row is a different feature. |
| **Long subsong lists** | No equivalent conditional following of the active subsong that preserves the area manually browsed by the listener. WEB rebuilds the strip when it updates. |
| **Play-all-subsongs setting location** | Playback support exists in WEB, but the switch is available only in Now Playing for a multi-subsong file, rather than also in Settings. |

Sources:
[Random scope](../app/src/main/kotlin/com/przunk/protracktor/player/RandomScope.kt),
[search scope](../app/src/main/kotlin/com/przunk/protracktor/player/SearchScope.kt),
[library search](../app/src/main/kotlin/com/przunk/protracktor/player/LibrarySearch.kt),
[local scanning](../app/src/main/kotlin/com/przunk/protracktor/player/MediaScanner.kt),
[playlist transfer](../app/src/main/kotlin/com/przunk/protracktor/player/PlaylistTransfer.kt),
[Browse selection](../app/src/main/kotlin/com/przunk/protracktor/ui/BrowseScreen.kt),
[cache policy](../app/src/main/kotlin/com/przunk/protracktor/net/CacheBudget.kt),
[folder prefetch](../app/src/main/kotlin/com/przunk/protracktor/player/FolderPrefetch.kt),
[offline availability](../app/src/main/kotlin/com/przunk/protracktor/player/OnPhone.kt),
[download sheet](../app/src/main/kotlin/com/przunk/protracktor/ui/DownloadPicker.kt),
[Browse scrolling](../app/src/main/kotlin/com/przunk/protracktor/ui/BrowseScroll.kt),
[subsong strip](../app/src/main/kotlin/com/przunk/protracktor/ui/SubsongStrip.kt).
Compare [WEB catalogue sources](../web/src/catalogue.js),
[WEB persistence](../web/src/store.js), [WEB UI](../web/src/index.html) and
[WEB behaviour](../web/src/app.js).

UnExoticA and The Mod Archive need separate browser access/infrastructure decisions before a port.
The existing Android permission is not approval to introduce a server-side relay. The absence of
these integrations does not establish that every individual URL is inaccessible from a browser;
no new CORS measurement was made for this inventory.

## Decoder and platform integration differences

| Area | Missing or partial WEB behaviour |
| --- | --- |
| **UADE** | The current WEB engine excludes UADE. Formats requiring that backend and their companion-file workflow are unavailable, including relevant TFMX files. This is not a claim that every Amiga custom format needs UADE or that all Amiga formats are missing. |
| **External replay routines** | No download/install path for UADE players or additional sc68 replay routines. SNDH files playable without those routines remain supported. |
| **UADE durations** | No corresponding songdb length lookup or retention of lengths learned during playback. HVSC SID lengths are already implemented. |
| **System playback controls** | WEB registers Play, Pause, Next and Previous. It does not supply the Android-equivalent heart, Media Session seek/stop handlers, current position publication or generated per-track artwork. |
| **Headset/Bluetooth buttons** | WEB has handlers, but C104 reports that external buttons do not control it. Treat this as implemented in part with unresolved device behaviour, not absent code. |
| **Audio-output changes** | No application-level counterpart to Android's Bluetooth output recovery and unplug pause handling. Actual behaviour depends on the browser and system and was not tested in this audit. |
| **Audio focus and background lifetime** | No counterpart to the Android foreground service, explicit audio-focus handling or system-requested ducking. This does not imply that WEB can never play in the background; its guarantees differ. |
| **Open with integration** | No equivalent Android intent handling for local music files and supported archive addresses. Links opening tracks in the WEB player already exist. |
| **Dynamic colours and haptics** | No Android wallpaper-derived palette or counterpart to the app's gesture/action haptic feedback. |
| **Language changes** | WEB reloads the page and stops playback. Android recreates the interface while retaining the playback service. |

These are not all straightforward frontend ports. In particular, the current UADE integration
uses a separate process; it is not included by merely rebuilding the existing WASM target.
Browser/system capabilities must be evaluated for the supported environments before promising
equivalent native integrations.

Sources: [WEB engine build](../scripts/build-web-engine.sh),
[WEB audio processor](../web/src/processor.js),
[WEB Media Session publication](../web/src/app.js),
[Android presentation](../app/src/main/kotlin/com/przunk/protracktor/player/PlaybackPresentation.kt),
[audio focus](../app/src/main/kotlin/com/przunk/protracktor/player/AudioFocus.kt),
[output recovery](../app/src/main/kotlin/com/przunk/protracktor/engine/OutputRecovery.kt),
[replay downloads](../app/src/main/kotlin/com/przunk/protracktor/player/ReplayDownloads.kt),
[activity language handling](../app/src/main/kotlin/com/przunk/protracktor/MainActivity.kt).

## Existing features that must not be counted as missing

- English and Polish, and System, Light and Dark themes.
- Multiple custom playlists, creation, renaming, deletion, adding/removing tracks, explicit Save
  and Discard, and Undo for removal. The protected From the phone playlist has separate ownership.
- Shuffle, repeat, subsong selection, playing all subsongs and holding Next/Previous to skip files.
- History pagination and leaving an entry in place when replayed from History.
- Songdb metadata, HVSC lengths, instrument/sample names and track information.
- Sharing, saving the original file and audio export. Actual browser API availability still
  governs the available sharing/export paths.
- PWA installation and offline application-shell startup. Offline startup does not establish
  offline availability of all music.
- ZXTune, PT3 and ASAP/SAP. The recent C121 decoder-filename correction is included in the inspected
  source; older descriptions of missing ZXTune are obsolete.
- Basic Media Session controls and Random read-ahead, subject to the limitations above.

## Excluded or unresolved on both sides

- **C118:** automatic appearance of new records while History stays open remains an Android
  defect. It is not an existing Android feature that WEB needs to catch up with.
- **AMP:** the separate integration branch is unmerged and awaits permission. It is outside the
  released 1.4.0 and enabled-develop comparison.
- **B54 floating controls:** retained as an experiment on develop with
  `FloatingControls.ENABLED = false`. It is not an enabled feature of the published 1.4.0.
- Android Auto, adding individual subsongs to playlists, the tutorial and other unimplemented
  wishlist items do not become WEB gaps merely because they have been discussed.
- GitHub Pages pairing remains separately deferred. Self-hosted pairing exists; lack of a public
  pairing service is a deployment/infrastructure choice rather than absence of the entire handoff.
- Account-based synchronization between Android and WEB remains a separate idea, not a feature
  already supplied by Android.

See [current status](STATUS.md) and [the implementation roadmap](IMPLEMENTATION_ROADMAP.md) for
defect ownership, deferred decisions and the distinction between implemented and proposed work.

## Suggested priorities

The most noticeable parity work would be:

1. Independent playback context and manual queue, because the current Android workflow depends
   on browsing without interrupting playback and inserting one-shot requests.
2. Stereo modes and volume normalization, because they change the listening experience directly.
3. Search improvements, including History search and recent-query suggestions.

These remain suggested priorities, not a fixed implementation sequence. The subsequent scope
selection above includes all twenty-five agreed areas; no feature was started as part of
recording this analysis or writing the execution checklist.
